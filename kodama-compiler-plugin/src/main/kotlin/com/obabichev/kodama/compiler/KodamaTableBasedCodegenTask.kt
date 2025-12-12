package com.obabichev.kodama.compiler

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.io.File

/**
 * New table-based code generator.
 * Instead of scanning data classes, this reads from registered Table objects.
 *
 * The generation happens at runtime when tables are first accessed,
 * so we need to scan test files to discover which table combinations are used.
 */
@CacheableTask
abstract class KodamaTableBasedCodegenTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        testDir.convention(project.layout.projectDirectory.dir("src/test/kotlin"))
        schemaDir.convention(project.layout.projectDirectory.dir("src/main/kotlin"))
        outputDir.convention(project.layout.buildDirectory.dir("generated/kodama"))
    }

    @TaskAction
    fun generate() {
        val queryOutputFile = outputDir.get().asFile.resolve("com/obabichev/kodama/tests/data/QueryExtensions.kt")

        // Discover tables by scanning schema files
        val tables = mutableSetOf<String>()
        val tableToProperties = mutableMapOf<String, List<String>>()
        val tableToPropertyTypes = mutableMapOf<String, Map<String, String>>()
        val tableToPropertyNullability = mutableMapOf<String, Map<String, Boolean>>()

        // Scan for table definitions: object Xxx : Table(...)
        val schemaFiles = schemaDir.get().asFile.walkTopDown().filter { it.extension == "kt" }
        schemaFiles.forEach { file ->
            val content = file.readText()

            // Match: object TableName : Table("...") { ... }
            val tablePattern = """object\s+(\w+)\s*:\s*Table\s*\([^)]*\)\s*\{([^}]*)\}""".toRegex()
            tablePattern.findAll(content).forEach { match ->
                val tableName = match.groupValues[1].lowercase()
                val tableBody = match.groupValues[2]
                tables.add(tableName)

                // Extract properties from table body with their types and nullability
                // Match patterns like: val name = varchar(...).primaryKey() or val age = integer(...).nullable()
                val propertyPattern = """val\s+(\w+)\s*=\s*(varchar|integer|text|boolean|timestamp|date|double|float|long|bigDecimal)\s*\([^)]*\)([^\n]*)""".toRegex()
                val properties = propertyPattern.findAll(tableBody)
                    .map { it.groupValues[1] }
                    .toList()

                // Extract property types and nullability for result accessor generation
                val propertyTypes = mutableMapOf<String, String>()
                val propertyNullability = mutableMapOf<String, Boolean>()
                propertyPattern.findAll(tableBody).forEach { match ->
                    val propName = match.groupValues[1]
                    val typeMethod = match.groupValues[2]
                    val modifiers = match.groupValues[3]  // e.g., ".primaryKey()" or ".nullable()"

                    // Check if column is explicitly marked as nullable
                    val isNullable = modifiers.contains(".nullable()")

                    // Map column type methods to Kotlin types
                    val kotlinType = when (typeMethod) {
                        "varchar", "text" -> "String"
                        "integer" -> "Int"
                        "long" -> "Long"
                        "double" -> "Double"
                        "float" -> "Float"
                        "boolean" -> "Boolean"
                        "timestamp", "date" -> "java.time.LocalDateTime"
                        "bigDecimal" -> "java.math.BigDecimal"
                        else -> "Any"
                    }
                    propertyTypes[propName] = kotlinType
                    propertyNullability[propName] = isNullable
                }

                if (properties.isNotEmpty()) {
                    tableToProperties[tableName] = properties
                    tableToPropertyTypes[tableName] = propertyTypes
                    tableToPropertyNullability[tableName] = propertyNullability
                }
            }
        }

        // Discover query combinations from test files
        val queryCombinations = mutableSetOf<List<String>>()

        // NEW: Discover selection patterns (which columns are actually selected)
        // Maps table combination to set of selection patterns
        // Example: [person, order] -> [["person:All", "order:Product"], ["person:Name", "order:All"]]
        val selectionPatterns = mutableMapOf<List<String>, MutableSet<List<String>>>()

        val testFiles = testDir.get().asFile.walkTopDown().filter { it.extension == "kt" }
        testFiles.forEach { file ->
            val content = file.readText()

            // Match query chains: query().from(Table).join(Table)...
            // Look for query().from(...) and then capture everything until .select or .where or end
            val queryChainPattern = """query\s*\(\s*\)\s*\.from\s*\([^)]+\)(?:\s*\.join\s*\([^)]+\)(?:\s*\{[^}]*\})?)*""".toRegex()

            queryChainPattern.findAll(content).forEach { chainMatch ->
                val chain = chainMatch.value
                val typesInChain = mutableListOf<String>()

                // Extract table names from the chain
                val tableRefPattern = """(?:from|join)\s*\(\s*(\w+)""".toRegex()
                tableRefPattern.findAll(chain).forEach { typeMatch ->
                    val tableName = typeMatch.groupValues[1].lowercase()
                    typesInChain.add(tableName)
                }

                // Store all prefixes
                if (typesInChain.isNotEmpty()) {
                    for (i in 1..typesInChain.size) {
                        val subCombination = typesInChain.take(i)
                        queryCombinations.add(subCombination)
                    }
                }
            }

            // NEW: Extract selection patterns
            // Match both old and new API:
            // Old: .selectPersonAll(), .selectPersonName()
            // New: .select(col.Person.name), .select(Person.name), .select(Person.all())
            val selectOldPattern = """\.select(\w+?)(All|[A-Z]\w+)\(\)""".toRegex()
            val selectNewColumnPattern = """\.select\((?:col\.)?(\w+)\.(\w+)\)""".toRegex()  // matches both col.Person.name and Person.name
            val selectNewAllPattern = """\.select\((?:col\.)?(\w+)\.all\(\)\)""".toRegex()

            // Find complete query chains with selections
            val fullQueryPattern = """query\s*\(\s*\).*?\.execute\(""".toRegex(RegexOption.DOT_MATCHES_ALL)
            fullQueryPattern.findAll(content).forEach { queryMatch ->
                val queryChain = queryMatch.value

                // Extract table combination (from/join) - use LinkedHashSet to maintain order and avoid duplicates
                val tablesInQuery = linkedSetOf<String>()
                val tableRefPattern = """(?:from|join)\s*\(\s*(\w+)""".toRegex()
                tableRefPattern.findAll(queryChain).forEach { typeMatch ->
                    tablesInQuery.add(typeMatch.groupValues[1].lowercase())
                }

                // Extract selection pattern - preserve order for HList type accumulation!
                val selections = mutableListOf<String>()

                // Find all .select() calls in order (both old and new patterns)
                val allSelectMatches = mutableListOf<Pair<Int, MatchResult>>()
                selectOldPattern.findAll(queryChain).forEach { allSelectMatches.add(it.range.first to it) }
                selectNewColumnPattern.findAll(queryChain).forEach { allSelectMatches.add(it.range.first to it) }
                selectNewAllPattern.findAll(queryChain).forEach { allSelectMatches.add(it.range.first to it) }

                // Sort by position in query chain
                allSelectMatches.sortBy { it.first }

                allSelectMatches.forEach { (_, match) ->
                    when {
                        // Old pattern: .selectPersonName()
                        match.value.matches(selectOldPattern) -> {
                            val tableName = match.groupValues[1].lowercase()
                            val selectionType = match.groupValues[2]
                            val selection = "$tableName:$selectionType"
                            if (selection !in selections) {
                                selections.add(selection)
                            }
                        }
                        // New pattern: .select(Person.all())
                        match.value.matches(selectNewAllPattern) -> {
                            val tableName = match.groupValues[1].lowercase()
                            val selection = "$tableName:All"
                            if (selection !in selections) {
                                selections.add(selection)
                            }
                        }
                        // New pattern: .select(Person.name)
                        match.value.matches(selectNewColumnPattern) -> {
                            val tableName = match.groupValues[1].lowercase()
                            val columnName = match.groupValues[2]
                            val columnCapitalized = columnName.replaceFirstChar { it.uppercase() }
                            val selection = "$tableName:$columnCapitalized"
                            if (selection !in selections) {
                                selections.add(selection)
                            }
                        }
                    }
                }

                // Store this pattern
                if (tablesInQuery.isNotEmpty() && selections.isNotEmpty()) {
                    selectionPatterns.getOrPut(tablesInQuery.toList()) { mutableSetOf() }.add(selections.toList())
                }
            }
        }

        // Generate query extensions
        queryOutputFile.parentFile.mkdirs()
        queryOutputFile.writeText(buildString {
            appendLine("@file:Suppress(\"UNCHECKED_CAST\")")
            appendLine()
            appendLine("package com.obabichev.kodama.tests.data")
            appendLine()
            appendLine("import com.obabichev.kodama.components.JoinType")
            appendLine("import com.obabichev.kodama.query.*")
            appendLine("import com.obabichev.kodama.tests.schema.*")
            appendLine("import com.obabichev.kodama.schema.Table")
            appendLine()
            appendLine("// AUTO-GENERATED by Kodama Compiler Plugin")
            appendLine("// DO NOT EDIT MANUALLY")
            appendLine()

            // Generate column name marker interfaces (reusable across tables)
            val allColumnNames = mutableSetOf<String>()
            tables.forEach { tableName ->
                val properties = tableToProperties[tableName] ?: emptyList()
                properties.forEach { propName ->
                    allColumnNames.add(propName.replaceFirstChar { it.uppercase() })
                }
            }

            appendLine("// Column name markers for type accumulation")
            allColumnNames.sorted().forEach { columnName ->
                appendLine("interface $columnName")
            }
            appendLine()

            appendLine("// Table marker interfaces for disambiguation")
            tables.forEach { tableName ->
                val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                appendLine("interface ${capitalizedName}Table")
            }
            appendLine()

            // Generate extension properties and functions on schema table objects for typed column access
            // This allows users to write .select(Person.Name) with capitalized column names
            // The capitalized names won't conflict with the lowercase member properties
            appendLine("// Extension properties on schema tables for type-safe column selection")
            tables.forEach { tableName ->
                val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                val properties = tableToProperties[tableName] ?: emptyList()

                properties.forEach { propName ->
                    val propCapitalized = propName.replaceFirstChar { it.uppercase() }
                    // Extension property with capitalized name - no conflict with member property
                    appendLine("val com.obabichev.kodama.tests.schema.$capitalizedName.$propCapitalized: com.obabichev.kodama.components.TypedColumn<*, ${capitalizedName}Table, $propCapitalized>")
                    appendLine("    get() = com.obabichev.kodama.components.TypedColumn(this.$propName)")
                }

                // Extension function for .all() that returns table-specific AllMarker
                appendLine("fun com.obabichev.kodama.tests.schema.$capitalizedName.all(): ${capitalizedName}AllMarker = ${capitalizedName}AllMarker(this)")
                appendLine()
            }
            appendLine()

            // Generate AllColumnsMarker class to represent Table.all()
            appendLine("// Marker class for selecting all columns")
            appendLine("sealed class AllColumnsMarker(val table: com.obabichev.kodama.schema.Table) {")
            appendLine("    fun asTableAllSelection(): com.obabichev.kodama.query.TableAllSelection {")
            appendLine("        return com.obabichev.kodama.query.TableAllSelection(table, table.allColumns())")
            appendLine("    }")
            appendLine("}")
            tables.forEach { tableName ->
                val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                appendLine("class ${capitalizedName}AllMarker(table: com.obabichev.kodama.schema.Table) : AllColumnsMarker(table)")
            }
            appendLine()

            // Generate accessor classes for each table
            tables.forEach { tableName ->
                val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                val properties = tableToProperties[tableName] ?: emptyList()

                appendLine("/**")
                appendLine(" * Type-safe accessor for $capitalizedName table")
                appendLine(" */")
                appendLine("class ${capitalizedName}Accessor(")
                appendLine("    private val tableAccessor: TableAccessor")
                appendLine(") {")
                appendLine("    fun all() = ${capitalizedName}AllMarker(tableAccessor.table)")
                appendLine()

                // Add property accessors that directly return typed columns
                properties.forEach { propName ->
                    val propCapitalized = propName.replaceFirstChar { it.uppercase() }
                    appendLine("    val $propName: com.obabichev.kodama.components.TypedColumn<*, ${capitalizedName}Table, $propCapitalized>")
                    appendLine("        get() = com.obabichev.kodama.components.TypedColumn(com.obabichev.kodama.tests.schema.$capitalizedName.$propName)")
                    appendLine()
                }

                appendLine("}")
                appendLine()

                // Generate OrderByAccessor for this table
                appendLine("/**")
                appendLine(" * Type-safe accessor for $capitalizedName table in ORDER BY context")
                appendLine(" */")
                appendLine("class ${capitalizedName}OrderByAccessor(")
                appendLine("    private val tableAccessor: TableAccessor")
                appendLine(") {")

                properties.forEach { propName ->
                    appendLine("    val $propName")
                    appendLine("        get() = com.obabichev.kodama.query.OrderByColumn(com.obabichev.kodama.tests.schema.$capitalizedName.$propName)")
                    appendLine()
                }

                appendLine("}")
                appendLine()
            }

            // Track generated ResultAccessor classes
            val generatedResultAccessors = mutableSetOf<String>()

            // Generate typed builders for each query combination
            queryCombinations.forEach { combination ->
                val typeNames = combination.map { it.replaceFirstChar { c -> c.uppercase() } }
                val builderClassName = "AfterFromQueryBuilder_" + typeNames.joinToString("_")
                val contextClassName = "SelectContext_" + typeNames.joinToString("_")

                appendLine("// ========== ${typeNames.joinToString(" + ")} ==========")
                appendLine()

                // Generate builder class - generic per-table selection state
                // For Person: <PersonSel>
                // For Person+Order: <PersonSel, OrderSel>
                // For Person+Order+Profile: <PersonSel, OrderSel, ProfileSel>
                val genericParams = combination.map { "${it.replaceFirstChar { it.uppercase() }}Sel" }.joinToString(", ")
                appendLine("class $builderClassName<$genericParams>(")
                appendLine("    override val state: QueryState")
                appendLine(") : AfterFromQueryBuilderBase<NoSelection>")
                appendLine()

                // Generate from() extension only for single-table combinations
                if (combination.size == 1) {
                    val tableName = combination[0]
                    val capitalizedName = tableName.replaceFirstChar { it.uppercase() }

                    // Start with com.obabichev.kodama.query.NoColumnsSelected for the table
                    appendLine("fun InitialQueryBuilder<NoSelection>.from(table: com.obabichev.kodama.tests.schema.$capitalizedName): $builderClassName<com.obabichev.kodama.query.NoColumnsSelected> {")
                    appendLine("    state._from = state.relations.relation(table)")
                    appendLine("    return $builderClassName(state)")
                    appendLine("}")
                    appendLine()
                }

                // Generate join() extensions
                tables.forEach { newTable ->
                    if (!combination.contains(newTable)) {
                        val newCombination = combination + newTable

                        if (queryCombinations.contains(newCombination)) {
                            val newTableCapitalized = newTable.replaceFirstChar { it.uppercase() }
                            val newBuilderClassName = "AfterFromQueryBuilder_" + newCombination.map { it.replaceFirstChar { c -> c.uppercase() } }.joinToString("_")
                            val jvmName = "join" + typeNames.joinToString("") + newTableCapitalized
                            val joinContextClassName = "JoinContext_" + typeNames.joinToString("_") + "_" + newTableCapitalized

                            // Generate JoinContext
                            appendLine("class $joinContextClassName(")
                            appendLine("    private val state: QueryState,")
                            appendLine("    private val joiningTable: Table")
                            appendLine(") {")

                            combination.forEach { existingTable ->
                                val existingCapitalized = existingTable.replaceFirstChar { it.uppercase() }
                                appendLine("    val $existingTable = ${existingCapitalized}Accessor(TableAccessor(com.obabichev.kodama.tests.schema.$existingCapitalized, state.relations))")
                            }
                            appendLine("    val $newTable = ${newTableCapitalized}Accessor(TableAccessor(com.obabichev.kodama.tests.schema.$newTableCapitalized, state.relations))")

                            appendLine("}")
                            appendLine()

                            // Generate join method - preserves existing selection states, adds com.obabichev.kodama.query.NoColumnsSelected for new table
                            val existingGenericParams = combination.map { "${it.replaceFirstChar { it.uppercase() }}Sel" }.joinToString(", ")
                            val newGenericParams = (combination.map { "${it.replaceFirstChar { it.uppercase() }}Sel" } + "com.obabichev.kodama.query.NoColumnsSelected").joinToString(", ")

                            appendLine("@JvmName(\"$jvmName\")")
                            appendLine("fun <$existingGenericParams> $builderClassName<$existingGenericParams>.join(")
                            appendLine("    table: com.obabichev.kodama.tests.schema.$newTableCapitalized,")
                            appendLine("    type: JoinType = JoinType.INNER,")
                            appendLine("    condition: $joinContextClassName.() -> Pair<com.obabichev.kodama.components.Column<*>, com.obabichev.kodama.components.Column<*>>")
                            appendLine("): $newBuilderClassName<$newGenericParams> {")
                            appendLine("    val relation = state.relations.relation(table)")
                            appendLine("    val context = $joinContextClassName(state, table)")
                            appendLine("    val (leftColumn, rightColumn) = context.condition()")
                            appendLine("    state._joins.add(com.obabichev.kodama.components.Join(type, relation, leftColumn to rightColumn))")
                            appendLine("    return $newBuilderClassName(state)")
                            appendLine("}")
                            appendLine()
                        }
                    }
                }

                // Generate SelectContext with table accessors
                appendLine("class $contextClassName(")
                appendLine("    private val state: QueryState")
                appendLine(") : SelectContext() {")

                combination.forEach { tableName ->
                    val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                    appendLine("    val $tableName = ${capitalizedName}Accessor(TableAccessor(com.obabichev.kodama.tests.schema.$capitalizedName, state.relations))")
                }

                appendLine("}")
                appendLine()

                // Generate per-table select methods: selectPersonAll(), selectPersonName(), etc.
                combination.forEachIndexed { tableIndex, tableName ->
                    val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                    val properties = tableToProperties[tableName] ?: emptyList()

                    // Build generic parameter lists
                    val beforeParams = combination.map { "${it.replaceFirstChar { it.uppercase() }}Sel" }
                    val beforeGeneric = beforeParams.joinToString(", ")

                    // Type parameters for selecting all columns
                    val afterParamsForAll = beforeParams.toMutableList().apply {
                        set(tableIndex, "com.obabichev.kodama.query.AllColumnsSelected")
                    }
                    val afterGenericForAll = afterParamsForAll.joinToString(", ")

                    // Generate .select(Table.all()) method using table-specific AllMarker
                    appendLine("@JvmName(\"select${capitalizedName}AllMarker\")")
                    appendLine("fun <${beforeParams.joinToString(", ")}> $builderClassName<$beforeGeneric>.select(all: ${capitalizedName}AllMarker): $builderClassName<$afterGenericForAll> {")
                    appendLine("    val result = com.obabichev.kodama.query.TableAllSelection(all.table, all.table.allColumns())")
                    appendLine("    state.applySelection(result)")
                    appendLine("    return $builderClassName(state)")
                    appendLine("}")
                    appendLine()

                    // Generate specific select() overloads for each column in this table
                    // Each overload accepts Column<T> and returns the correct type
                    val currentTableParam = beforeParams[tableIndex]

                    // Add constraint only to the type parameter being modified
                    val beforeParamsWithConstraint = beforeParams.mapIndexed { index, param ->
                        if (index == tableIndex) "$param : com.obabichev.kodama.query.ColumnSelectionState"
                        else param
                    }.joinToString(", ")

                    // Generate select() method that accepts TypedColumn with table marker
                    // The table marker disambiguates which table's column is being selected
                    appendLine("@JvmName(\"select${capitalizedName}Column\")")
                    appendLine("fun <$beforeParamsWithConstraint, CM> $builderClassName<$beforeGeneric>.select(column: com.obabichev.kodama.components.TypedColumn<*, ${capitalizedName}Table, CM>): $builderClassName<${beforeParams.mapIndexed { index, param ->
                        if (index == tableIndex) "com.obabichev.kodama.query.SelectedColumns<CM, $currentTableParam>"
                        else param
                    }.joinToString(", ")}> {")
                    appendLine("    state.applySelection(com.obabichev.kodama.query.ColumnSelection(column.column))")
                    appendLine("    return $builderClassName(state)")
                    appendLine("}")
                    appendLine()
                }

                // Legacy .select{} method removed - only typed selectors are supported now

                // Generate WhereContext
                val whereContextClassName = "WhereContext_" + typeNames.joinToString("_")
                appendLine("class $whereContextClassName(")
                appendLine("    private val state: QueryState")
                appendLine(") {")

                combination.forEach { tableName ->
                    val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                    appendLine("    val $tableName = ${capitalizedName}Accessor(TableAccessor(com.obabichev.kodama.tests.schema.$capitalizedName, state.relations))")
                }

                appendLine("}")
                appendLine()

                // Generate where() extension - preserves all selection types
                appendLine("fun <$genericParams> $builderClassName<$genericParams>.where(block: $whereContextClassName.() -> com.obabichev.kodama.components.expression.Expression): $builderClassName<$genericParams> {")
                appendLine("    val context = $whereContextClassName(state)")
                appendLine("    state.whereExpression = context.block()")
                appendLine("    return this")
                appendLine("}")
                appendLine()

                // Generate OrderByContext
                val orderByContextClassName = "OrderByContext_" + typeNames.joinToString("_")
                appendLine("class $orderByContextClassName(")
                appendLine("    private val state: QueryState")
                appendLine(") : com.obabichev.kodama.query.OrderByContext() {")

                combination.forEach { tableName ->
                    val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                    appendLine("    val $tableName = ${capitalizedName}OrderByAccessor(TableAccessor(com.obabichev.kodama.tests.schema.$capitalizedName, state.relations))")
                }

                appendLine("}")
                appendLine()

                // Generate orderBy() extension - preserves all selection types
                appendLine("fun <$genericParams> $builderClassName<$genericParams>.orderBy(block: $orderByContextClassName.() -> Unit): $builderClassName<$genericParams> {")
                appendLine("    val context = $orderByContextClassName(state)")
                appendLine("    context.block()")
                appendLine("    state._orderBy.addAll(context.orderByClauses)")
                appendLine("    return this")
                appendLine("}")
                appendLine()

                // NOTE: Generic QueryResult classes removed
                // They had ALL accessors available which defeats compile-time safety
                // Only selection-specific result classes (generated below) are used now
                val resultClassName = "QueryResult_" + typeNames.joinToString("_")

                // Generate result accessor classes for each selection pattern
                combination.forEach { tableName ->
                    val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                    val properties = tableToProperties[tableName] ?: emptyList()

                    // Generate accessor for TableAllSelected (all columns)
                    val accessorClassAll = "${capitalizedName}ResultAccessor_All"
                    if (!generatedResultAccessors.contains(accessorClassAll)) {
                        generatedResultAccessors.add(accessorClassAll)

                        appendLine("class $accessorClassAll(")
                        appendLine("    resultSet: java.sql.ResultSet,")
                        appendLine("    relations: com.obabichev.kodama.query.RelationsContainer,")
                        appendLine("    selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
                        appendLine(") : com.obabichev.kodama.query.TableResultAccessor(resultSet, relations, selectedColumns) {")

                        val propertyTypes = tableToPropertyTypes[tableName] ?: emptyMap()
                        val propertyNullability = tableToPropertyNullability[tableName] ?: emptyMap()
                        properties.forEach { propName ->
                            val kotlinType = propertyTypes[propName] ?: "Any"
                            val isNullable = propertyNullability[propName] ?: true
                            val nullabilityMarker = if (isNullable) "?" else ""
                            appendLine("    val $propName: $kotlinType$nullabilityMarker")
                            appendLine("        get() = readColumn(com.obabichev.kodama.tests.schema.$capitalizedName.$propName) as $kotlinType$nullabilityMarker")
                            appendLine()
                        }

                        appendLine("}")
                        appendLine()
                    }

                    // Generate accessors for individual columns
                    val propertyTypes = tableToPropertyTypes[tableName] ?: emptyMap()
                    val propertyNullability = tableToPropertyNullability[tableName] ?: emptyMap()
                    properties.forEach { propName ->
                        val propCap = propName.replaceFirstChar { it.uppercase() }
                        val accessorClass = "${capitalizedName}ResultAccessor_${propCap}"

                        if (!generatedResultAccessors.contains(accessorClass)) {
                            generatedResultAccessors.add(accessorClass)

                            appendLine("class $accessorClass(")
                            appendLine("    resultSet: java.sql.ResultSet,")
                            appendLine("    relations: com.obabichev.kodama.query.RelationsContainer,")
                            appendLine("    selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
                            appendLine(") : com.obabichev.kodama.query.TableResultAccessor(resultSet, relations, selectedColumns) {")

                            val kotlinType = propertyTypes[propName] ?: "Any"
                            val isNullable = propertyNullability[propName] ?: true
                            val nullabilityMarker = if (isNullable) "?" else ""
                            appendLine("    val $propName: $kotlinType$nullabilityMarker")
                            appendLine("        get() = readColumn(com.obabichev.kodama.tests.schema.$capitalizedName.$propName) as $kotlinType$nullabilityMarker")
                            appendLine()

                            appendLine("}")
                            appendLine()
                        }
                    }

                    // Generate accessors for pairs of columns
                    if (properties.size >= 2) {
                        for (i in 0 until properties.size - 1) {
                            for (j in i + 1 until properties.size) {
                                val prop1 = properties[i]
                                val prop2 = properties[j]
                                val prop1Cap = prop1.replaceFirstChar { it.uppercase() }
                                val prop2Cap = prop2.replaceFirstChar { it.uppercase() }
                                val accessorClass = "${capitalizedName}ResultAccessor_${prop1Cap}_${prop2Cap}"

                                if (!generatedResultAccessors.contains(accessorClass)) {
                                    generatedResultAccessors.add(accessorClass)

                                    appendLine("class $accessorClass(")
                                    appendLine("    resultSet: java.sql.ResultSet,")
                                    appendLine("    relations: com.obabichev.kodama.query.RelationsContainer,")
                                    appendLine("    selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
                                    appendLine(") : com.obabichev.kodama.query.TableResultAccessor(resultSet, relations, selectedColumns) {")

                                    val type1 = propertyTypes[prop1] ?: "Any"
                                    val type2 = propertyTypes[prop2] ?: "Any"
                                    val nullable1 = propertyNullability[prop1] ?: true
                                    val nullable2 = propertyNullability[prop2] ?: true
                                    val nullMarker1 = if (nullable1) "?" else ""
                                    val nullMarker2 = if (nullable2) "?" else ""

                                    appendLine("    val $prop1: $type1$nullMarker1")
                                    appendLine("        get() = readColumn(com.obabichev.kodama.tests.schema.$capitalizedName.$prop1) as $type1$nullMarker1")
                                    appendLine()

                                    appendLine("    val $prop2: $type2$nullMarker2")
                                    appendLine("        get() = readColumn(com.obabichev.kodama.tests.schema.$capitalizedName.$prop2) as $type2$nullMarker2")
                                    appendLine()

                                    appendLine("}")
                                    appendLine()
                                }
                            }
                        }
                    }
                }

                // NOTE: Generic execute() removed - no generic QueryResult class exists anymore
                // All queries must use typed select methods (selectPersonName, selectPersonAll, etc.)
                // to get an execute() method that returns selection-specific result classes
            }

            // ============================================================================
            // Generate specific result classes for discovered selection patterns
            // ============================================================================
            appendLine()
            appendLine("// ========== Selection-Specific Result Classes ==========")
            appendLine("// These classes provide TRUE compile-time safety")
            appendLine("// Only the selected accessors exist on each class")
            appendLine()

            val generatedSelectionResultClasses = mutableSetOf<String>()

            selectionPatterns.forEach { (tables, patterns) ->
                patterns.forEach { selectionList ->
                    // Build class name from selection pattern
                    // Example: person:All, order:Product -> QueryResult_Person_All_Order_Product
                    val classNameParts = mutableListOf<String>()
                    selectionList.forEach { selection ->
                        val (table, type) = selection.split(":")
                        classNameParts.add(table.replaceFirstChar { it.uppercase() })
                        classNameParts.add(type)
                    }
                    val resultClassName = "QueryResult_" + classNameParts.joinToString("_")

                    // Skip if already generated
                    if (generatedSelectionResultClasses.contains(resultClassName)) {
                        return@forEach
                    }
                    generatedSelectionResultClasses.add(resultClassName)

                    appendLine("/**")
                    appendLine(" * Result class for selection pattern: ${selectionList.joinToString(", ")}")
                    appendLine(" * Only contains accessors for selected columns")
                    appendLine(" */")
                    appendLine("class $resultClassName(")
                    appendLine("    override val resultSet: java.sql.ResultSet,")
                    appendLine("    override val relations: com.obabichev.kodama.query.RelationsContainer,")
                    appendLine("    override val selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
                    appendLine(") : com.obabichev.kodama.query.QueryResult {")
                    appendLine()

                    // Generate accessors - ONE per table (merging multiple selections)
                    // Group selections by table
                    val selectionsByTable = selectionList.groupBy { it.split(":")[0] }

                    selectionsByTable.forEach { (table, selections) ->
                        val tableCapitalized = table.replaceFirstChar { it.uppercase() }
                        val accessorName = table  // Just use table name

                        // Determine accessor class based on what was selected
                        val types = selections.map { it.split(":")[1] }
                        val accessorClass = if (types.size == 1 && types[0] == "All") {
                            // Single selection of .all()
                            "${tableCapitalized}ResultAccessor_All"
                        } else if (types.size == 1) {
                            // Single column selection
                            val typeWithUnderscores = types[0].replace(Regex("(?<!^)(?=[A-Z])"), "_")
                            "${tableCapitalized}ResultAccessor_$typeWithUnderscores"
                        } else {
                            // Multiple column selections - create combined name
                            val combinedTypes = types.map { type ->
                                if (type == "All") "All"
                                else type.replace(Regex("(?<!^)(?=[A-Z])"), "_")
                            }.joinToString("_")
                            "${tableCapitalized}ResultAccessor_$combinedTypes"
                        }

                        appendLine("    val $accessorName: $accessorClass")
                        appendLine("        get() = $accessorClass(resultSet, relations, selectedColumns)")
                        appendLine()
                    }

                    appendLine("}")
                    appendLine()
                }
            }

            // ============================================================================
            // Generate execute() overloads for selection-specific result classes
            // ============================================================================
            appendLine()
            appendLine("// ========== Execute Overloads for Compile-Time Safety ==========")
            appendLine("// These overloads return selection-specific result classes")
            appendLine("// They provide TRUE compile-time safety by exposing only selected accessors")
            appendLine()

            // Group patterns by table combination
            selectionPatterns.forEach { (tables, patterns) ->
                patterns.forEach { selectionList ->
                    // Build result class name
                    val resultClassNameParts = mutableListOf<String>()
                    selectionList.forEach { selection ->
                        val (table, type) = selection.split(":")
                        resultClassNameParts.add(table.replaceFirstChar { it.uppercase() })
                        resultClassNameParts.add(type)
                    }
                    val specificResultClassName = "QueryResult_" + resultClassNameParts.joinToString("_")

                    // Skip if this wasn't generated (duplicate)
                    if (!generatedSelectionResultClasses.contains(specificResultClassName)) {
                        return@forEach
                    }

                    // Build type parameters - one for EACH table in the query
                    // Must match the builder class's type parameter count
                    // Group selections by table to handle multiple selections per table
                    val selectionsByTable = selectionList.groupBy { it.split(":")[0] }

                    val typeParams = mutableListOf<String>()
                    tables.forEach { table ->
                        val tableSelections = selectionsByTable[table]

                        val typeParam = if (tableSelections == null) {
                            // No selections for this table
                            "com.obabichev.kodama.query.NoColumnsSelected"
                        } else {
                            val types = tableSelections.map { it.split(":")[1] }

                            if (types.size == 1 && types[0] == "All") {
                                // Single .all() selection
                                "com.obabichev.kodama.query.AllColumnsSelected"
                            } else {
                                // Build nested SelectedColumns type for each column
                                // HList builds from inside out: last selection is outermost type
                                // Example: .selectPersonName().selectPersonAge()
                                //   produces: SelectedColumns<Age, SelectedColumns<Name, NoColumnsSelected>>
                                var accumulated = "com.obabichev.kodama.query.NoColumnsSelected"
                                types.forEach { columnType ->
                                    val columnName = columnType.replaceFirstChar { it.uppercase() }
                                    accumulated = "com.obabichev.kodama.query.SelectedColumns<$columnName, $accumulated>"
                                }
                                accumulated
                            }
                        }
                        typeParams.add(typeParam)
                    }

                    // Determine builder class name from table combination
                    val builderClassName = "AfterFromQueryBuilder_" + tables.joinToString("_") {
                        it.replaceFirstChar { ch -> ch.uppercase() }
                    }

                    // Generate execute() overload with unique JVM name to avoid signature clashes
                    appendLine("/**")
                    appendLine(" * Execute overload for selection: ${selectionList.joinToString(", ")}")
                    appendLine(" * Returns: $specificResultClassName (only selected accessors available)")
                    appendLine(" */")
                    appendLine("@JvmName(\"execute_$specificResultClassName\")")
                    appendLine("fun $builderClassName<${typeParams.joinToString(", ")}>.execute(transaction: com.obabichev.kodama.execute.JdbcTransaction): com.obabichev.kodama.query.QueryResultIterable<$specificResultClassName> {")
                    appendLine("    val query = this.build()")
                    appendLine("    val resultSet = transaction.execute(query)")
                    appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, state.relations) { rs, relations ->")
                    appendLine("        $specificResultClassName(rs, relations, query.select)")
                    appendLine("    }")
                    appendLine("}")
                    appendLine()
                }
            }

            appendLine()
        })

        val totalPatterns = selectionPatterns.values.sumOf { it.size }
        logger.lifecycle("Kodama: Generated ${tables.size} tables, ${queryCombinations.size} query combinations, $totalPatterns selection patterns")
    }
}
