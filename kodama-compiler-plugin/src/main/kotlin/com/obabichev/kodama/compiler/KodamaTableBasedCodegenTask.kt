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

        // NEW: Discover selection patterns using pluggable scanners
        val scanners = listOf<SelectionPatternScanner>(
            AggregateScanner()
            // Easy to add more: ConstantScanner(), SubqueryScanner(), WindowFunctionScanner()
        )

        // Maps table combination to set of selection patterns
        // Example: [order] -> [SelectionPattern([order], [Selection("totalRevenue", AGGREGATE), Selection("orderCount", AGGREGATE)])]
        val selectionPatternsByTable = mutableMapOf<List<String>, MutableSet<SelectionPattern>>()

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
            // Match lambda-based API: .select { person.name }
            // Match direct API: .selectAll(Person)
            // Match aggregate functions: .select { sum(order.cost) alias "total" }
            val selectLambdaColumnPattern = """\.select\s*\{\s*\+\s*(\w+)\.(\w+)\s*\}""".toRegex()
            val selectAllDirectPattern = """\.selectAll\s*\(\s*([A-Z]\w+)\s*\)""".toRegex()

            // Match aggregate patterns (with or without alias)
            val aggregatePattern = """(sum|count|avg|min|max|countAll)\s*\(\s*(?:(\w+)\.(\w+))?\s*\)(?:\s+alias\s+"(\w+)")?""".toRegex()

            // Find complete query chains with selections
            // Match both: query()...execute() and query()...build()
            val executeQueryPattern = """query\s*\(\s*\)(?:(?!\.(?:build|execute)\()[\s\S])*?\.execute\(""".toRegex()
            val buildQueryPattern = """query\s*\(\s*\)(?:(?!\.(?:build|execute)\()[\s\S])*?\.build\(""".toRegex()

            val executeMatches = executeQueryPattern.findAll(content).map { it to true }.toList()
            val buildMatches = buildQueryPattern.findAll(content).map { it to false }.toList()
            val allMatches = executeMatches + buildMatches

            allMatches.forEach { (queryMatch, usesExecute) ->
                val queryChain = queryMatch.value

                // Extract table combination (from/join) - use LinkedHashSet to maintain order and avoid duplicates
                val tablesInQuery = linkedSetOf<String>()
                val tableRefPattern = """(?:from|join)\s*\(\s*(\w+)""".toRegex()
                tableRefPattern.findAll(queryChain).forEach { typeMatch ->
                    tablesInQuery.add(typeMatch.groupValues[1].lowercase())
                }

                // Extract selection pattern - preserve order for HList type accumulation!
                val selections = mutableListOf<String>()

                // Find select blocks and extract their contents
                // Match both .select { } and .selectAggregates { }
                val selectBlockPattern = """\.(select|selectAggregates)\s*\{([^}]*)\}""".toRegex()
                selectBlockPattern.findAll(queryChain).forEach { blockMatch ->
                    val blockType = blockMatch.groupValues[1]  // "select" or "selectAggregates"
                    val blockContent = blockMatch.groupValues[2]
                    val isAggregateBlock = blockType == "selectAggregates"

                    // Check for aggregate functions in the block
                    aggregatePattern.findAll(blockContent).forEach { aggMatch ->
                        val funcName = aggMatch.groupValues[1]
                        val tableName = aggMatch.groupValues[2].ifEmpty { null }?.lowercase()
                        val columnName = aggMatch.groupValues[3].ifEmpty { null }
                        val alias = aggMatch.groupValues[4].ifEmpty { null }

                        // Generate selection marker for aggregate
                        val accessorName = alias ?: if (tableName != null && columnName != null) {
                            "${funcName.lowercase()}_$columnName"
                        } else {
                            // countAll() case
                            "${funcName.lowercase()}_all"
                        }

                        val selection = "agg:$accessorName"
                        if (selection !in selections) {
                            selections.add(selection)
                        }
                    }

                    // Check for regular column selections (with or without + operator)
                    // Matches both: +person.name and person.name
                    // Skip this entirely in selectAggregates blocks - only aggregates should be detected there
                    if (!isAggregateBlock) {
                        val columnInBlockPattern = """\+?\s*(\w+)\.(\w+)""".toRegex()
                        columnInBlockPattern.findAll(blockContent).forEach { colMatch ->
                        val tableName = colMatch.groupValues[1].lowercase()
                        val columnName = colMatch.groupValues[2]

                        // Skip if this is inside an aggregate function call or an operator expression
                        val matchStart = colMatch.range.first
                        val matchEnd = colMatch.range.last + 1
                        val before = if (matchStart > 0) blockContent.substring(0, matchStart) else ""
                        val remaining = if (matchEnd < blockContent.length) blockContent.substring(matchEnd).trim() else ""

                        // Check if inside function call (has unclosed paren before this match)
                        val openParens = before.count { it == '(' }
                        val closeParens = before.count { it == ')' }
                        if (openParens > closeParens) {
                            return@forEach  // Skip, inside function call
                        }

                        // Check if followed by operator
                        if (remaining.startsWith("eq ") || remaining.startsWith("alias ")) {
                            return@forEach  // Skip, this is part of an expression
                        }

                            val columnCapitalized = columnName.replaceFirstChar { it.uppercase() }
                            val selection = "$tableName:$columnCapitalized"
                            if (selection !in selections) {
                                selections.add(selection)
                            }
                        }
                    }

                    // Check for .all() selections
                    val allInBlockPattern = """\+\s*(\w+)\.all\s*\(\s*\)""".toRegex()
                    allInBlockPattern.findAll(blockContent).forEach { allMatch ->
                        val tableName = allMatch.groupValues[1].lowercase()
                        val selection = "$tableName:All"
                        if (selection !in selections) {
                            selections.add(selection)
                        }
                    }
                }

                // Also handle .selectAll() direct calls (outside blocks)
                selectAllDirectPattern.findAll(queryChain).forEach { match ->
                    val tableName = match.groupValues[1].lowercase()
                    val selection = "$tableName:All"
                    if (selection !in selections) {
                        selections.add(selection)
                    }
                }

                // Store this pattern
                // Skip aggregate-only patterns from .build() queries to avoid overload ambiguity
                val hasRegularSelections = selections.any { !it.startsWith("agg:") }
                val isAggregateOnly = selections.isNotEmpty() && !hasRegularSelections

                if (tablesInQuery.isNotEmpty() && selections.isNotEmpty()) {
                    // Skip if: build() query AND aggregate-only
                    if (!(isAggregateOnly && !usesExecute)) {
                        selectionPatterns.getOrPut(tablesInQuery.toList()) { mutableSetOf() }.add(selections.toList())
                    }
                }

            }

            // Use scanners to discover selection patterns
            scanners.forEach { scanner ->
                val patterns = scanner.scanFile(content)
                patterns.forEach { pattern ->
                    selectionPatternsByTable.getOrPut(pattern.tables) { mutableSetOf() }.add(pattern)
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

            // Type aliases for aggregate count markers (to avoid fully qualified names in generics)
            appendLine("// Type aliases for aggregate count markers")
            appendLine("typealias AggCount = com.obabichev.kodama.query.AggCount")
            appendLine("typealias NoAggregates = com.obabichev.kodama.query.NoAggregates")
            appendLine("typealias Has1Aggregate = com.obabichev.kodama.query.Has1Aggregate")
            appendLine("typealias Has2Aggregates = com.obabichev.kodama.query.Has2Aggregates")
            appendLine("typealias Has3Aggregates = com.obabichev.kodama.query.Has3Aggregates")
            appendLine("typealias Has4Aggregates = com.obabichev.kodama.query.Has4Aggregates")
            appendLine("typealias Has5Aggregates = com.obabichev.kodama.query.Has5Aggregates")
            appendLine("typealias Has6Aggregates = com.obabichev.kodama.query.Has6Aggregates")
            appendLine("typealias Has7Aggregates = com.obabichev.kodama.query.Has7Aggregates")
            appendLine("typealias Has8Aggregates = com.obabichev.kodama.query.Has8Aggregates")
            appendLine("typealias Has9Aggregates = com.obabichev.kodama.query.Has9Aggregates")
            appendLine("typealias Has10Aggregates = com.obabichev.kodama.query.Has10Aggregates")
            appendLine()

            // Generate typealiases for selection state markers (defined in core)
            appendLine("// Selection state markers (typealiases to core definitions)")
            appendLine("typealias SelectionState = com.obabichev.kodama.query.SelectionState")
            appendLine("typealias NoSelections = com.obabichev.kodama.query.NoSelections")
            for (i in 1..10) {
                appendLine("typealias Has${i}Selection${if (i > 1) "s" else ""} = com.obabichev.kodama.query.Has${i}Selection${if (i > 1) "s" else ""}")
            }
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

            // No longer generating extension properties on Table objects
            // Users will access columns through select/where/join contexts only

            // Generate AllColumnsMarker class to represent Table.all()
            appendLine("// Marker class for selecting all columns")
            appendLine("sealed class AllColumnsMarker(val table: com.obabichev.kodama.schema.Table) : com.obabichev.kodama.components.SelectionMarker {")
            appendLine("    fun asTableAllSelection(): com.obabichev.kodama.query.TableAllSelection {")
            appendLine("        return com.obabichev.kodama.query.TableAllSelection(table, table.allColumns())")
            appendLine("    }")
            appendLine("}")
            tables.forEach { tableName ->
                val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                appendLine("class ${capitalizedName}AllMarker(table: com.obabichev.kodama.schema.Table) : AllColumnsMarker(table)")
            }
            appendLine()

            // Generate extension functions on Table objects for .all()
            tables.forEach { tableName ->
                val capitalizedName = tableName.replaceFirstChar { it.uppercase() }
                appendLine("fun com.obabichev.kodama.tests.schema.$capitalizedName.all() = ${capitalizedName}AllMarker(this)")
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
                appendLine("    // Allow using accessor itself as AllMarker (for selectAll { person } instead of selectAll { person.all() })")
                appendLine("    operator fun invoke() = ${capitalizedName}AllMarker(tableAccessor.table)")
                appendLine()

                // Add property accessors that directly return typed columns
                val propertyTypes = tableToPropertyTypes[tableName] ?: emptyMap()
                val propertyNullability = tableToPropertyNullability[tableName] ?: emptyMap()
                properties.forEach { propName ->
                    val propCapitalized = propName.replaceFirstChar { it.uppercase() }
                    val kotlinType = propertyTypes[propName] ?: "Any"
                    val isNullable = propertyNullability[propName] ?: true
                    val nullabilityMarker = if (isNullable) "?" else ""
                    appendLine("    val $propName: com.obabichev.kodama.components.TypedColumn<$kotlinType$nullabilityMarker, ${capitalizedName}Table, $propCapitalized>")
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

                // Generate builder class - generic per-table selection state + aggregate count
                // For Person: <PersonSel, AC : AggCount>
                // For Person+Order: <PersonSel, OrderSel, AC : AggCount>
                // For Person+Order+Profile: <PersonSel, OrderSel, ProfileSel, AC : AggCount>
                // Note: No default value on class - defaults are specified in factory functions
                val genericParams = combination.map { "${it.replaceFirstChar { it.uppercase() }}Sel" }.joinToString(", ")
                val genericParamsWithAggCount = if (genericParams.isEmpty()) "AC : AggCount" else "$genericParams, AC : AggCount"
                appendLine("class $builderClassName<$genericParamsWithAggCount>(")
                appendLine("    override val state: QueryState")
                appendLine(") : AfterFromQueryBuilderBase<NoSelection> {")
                appendLine("    // Explicit build() method to avoid Kotlin compiler resolution issues with complex generics")
                appendLine("    override fun build(): Query {")
                appendLine("        if (state._selectedColumns.isEmpty() && state._aggregateSelections.isEmpty()) {")
                appendLine("            throw IllegalStateException(\"SELECT clause is required. Call select() at least once.\")")
                appendLine("        }")
                appendLine("        val from = state._from ?: throw IllegalStateException(\"FROM clause is required.\")")
                appendLine()
                appendLine("        // When mixing columns with aggregates, automatically add selected columns to GROUP BY")
                appendLine("        val groupBy = if (state._aggregateSelections.isNotEmpty() && state._selectedColumns.isNotEmpty()) {")
                appendLine("            // Auto-populate GROUP BY with selected columns")
                appendLine("            state._selectedColumns.toList()")
                appendLine("        } else {")
                appendLine("            // No aggregates, or aggregates-only query (no columns to group by)")
                appendLine("            state._groupBy.toList()")
                appendLine("        }")
                appendLine()
                appendLine("        return Query(")
                appendLine("            state._selectedColumns.toList(),")
                appendLine("            from,")
                appendLine("            state._joins.toList(),")
                appendLine("            state.whereExpression,")
                appendLine("            state._orderBy.toList(),")
                appendLine("            state.relations,")
                appendLine("            state._aggregateSelections.toList(),")
                appendLine("            groupBy")
                appendLine("        )")
                appendLine("    }")
                appendLine("}")
                appendLine()

                // Generate from() extension only for single-table combinations
                if (combination.size == 1) {
                    val tableName = combination[0]
                    val capitalizedName = tableName.replaceFirstChar { it.uppercase() }

                    // Start with com.obabichev.kodama.query.NoColumnsSelected for the table and NoSelections
                    appendLine("fun InitialQueryBuilder<NoSelection>.from(table: com.obabichev.kodama.tests.schema.$capitalizedName): $builderClassName<com.obabichev.kodama.query.NoColumnsSelected, NoSelections> {")
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

                            // Generate join method - preserves existing selection states, adds com.obabichev.kodama.query.NoColumnsSelected for new table, preserves AC
                            val existingGenericParams = combination.map { "${it.replaceFirstChar { it.uppercase() }}Sel" }.joinToString(", ")
                            val existingGenericParamsWithAC = if (existingGenericParams.isEmpty()) "AC : AggCount" else "$existingGenericParams, AC : AggCount"
                            val newGenericParams = (combination.map { "${it.replaceFirstChar { it.uppercase() }}Sel" } + "com.obabichev.kodama.query.NoColumnsSelected").joinToString(", ")
                            val newGenericParamsWithAC = "$newGenericParams, AC"

                            appendLine("@JvmName(\"$jvmName\")")
                            appendLine("fun <$existingGenericParamsWithAC> $builderClassName<$existingGenericParams, AC>.join(")
                            appendLine("    table: com.obabichev.kodama.tests.schema.$newTableCapitalized,")
                            appendLine("    type: JoinType = JoinType.INNER,")
                            appendLine("    condition: $joinContextClassName.() -> Pair<com.obabichev.kodama.components.Column<*>, com.obabichev.kodama.components.Column<*>>")
                            appendLine("): $newBuilderClassName<$newGenericParamsWithAC> {")
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

                // Generate per-table select methods with lambda-based API
                // New API: .select { person.name } instead of .select(Person.Name)
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

                    // Generate selectAll() method that accepts Table object directly
                    // This avoids overload ambiguity because parameter types are different (Person, Order, etc.)
                    // Preserves AC parameter
                    val beforeParamsWithAC = if (beforeParams.isEmpty()) "AC : AggCount" else "${beforeParams.joinToString(", ")}, AC : AggCount"
                    val beforeGenericWithAC = if (beforeGeneric.isEmpty()) "AC" else "$beforeGeneric, AC"
                    val afterGenericForAllWithAC = if (afterGenericForAll.isEmpty()) "AC" else "$afterGenericForAll, AC"

                    appendLine("@JvmName(\"select${capitalizedName}All\")")
                    appendLine("fun <$beforeParamsWithAC> $builderClassName<$beforeGenericWithAC>.selectAll(table: com.obabichev.kodama.tests.schema.$capitalizedName): $builderClassName<$afterGenericForAllWithAC> {")
                    appendLine("    val result = com.obabichev.kodama.query.TableAllSelection(table, table.allColumns())")
                    appendLine("    state.applySelection(result)")
                    appendLine("    return $builderClassName(state)")
                    appendLine("}")
                    appendLine()

                    // Generate select() method that accepts lambda returning TypedColumn
                    // The table marker disambiguates which table's column is being selected
                    val currentTableParam = beforeParams[tableIndex]

                    // Add constraint only to the type parameter being modified, and preserve AC
                    val beforeParamsWithConstraint = beforeParams.mapIndexed { index, param ->
                        if (index == tableIndex) "$param : com.obabichev.kodama.query.ColumnSelectionState"
                        else param
                    }.joinToString(", ")
                    val beforeParamsWithConstraintAndAC = if (beforeParamsWithConstraint.isEmpty()) "AC : AggCount, CM" else "$beforeParamsWithConstraint, AC : AggCount, CM"

                    val afterSelectParams = beforeParams.mapIndexed { index, param ->
                        if (index == tableIndex) "com.obabichev.kodama.query.SelectedColumns<CM, $currentTableParam>"
                        else param
                    }.joinToString(", ")
                    val afterSelectParamsWithAC = if (afterSelectParams.isEmpty()) "AC" else "$afterSelectParams, AC"

                    appendLine("@JvmName(\"select${capitalizedName}Column\")")
                    appendLine("fun <$beforeParamsWithConstraintAndAC> $builderClassName<$beforeGenericWithAC>.select(block: $contextClassName.() -> com.obabichev.kodama.components.TypedColumn<*, ${capitalizedName}Table, CM>): $builderClassName<$afterSelectParamsWithAC> {")
                    appendLine("    val context = $contextClassName(state)")
                    appendLine("    val column = context.block()")
                    appendLine("    state.applySelection(com.obabichev.kodama.query.ColumnSelection(column.column))")
                    appendLine("    return $builderClassName(state)")
                    appendLine("}")
                    appendLine()
                }

                // Generate chained selectAggregate() methods for type-safe aggregate selection
                appendLine("// ===== Type-safe aggregate selection methods =====")
                appendLine()
                appendLine("/**")
                appendLine(" * Select a single aggregate function with compile-time type safety.")
                appendLine(" * Each call advances the aggregate count type, ensuring result type matches selections.")
                appendLine(" * Example: .selectAggregate { sum(order.cost) alias \"totalRevenue\" }")
                appendLine(" */")
                appendLine("@JvmName(\"selectAggregate0\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, NoAggregates>.selectAggregate(")
                appendLine("    block: $contextClassName.() -> com.obabichev.kodama.query.AggregateFunction<*>")
                appendLine("): $builderClassName<$genericParams, Has1Aggregate> {")
                appendLine("    val context = $contextClassName(state)")
                appendLine("    val agg = context.block()")
                appendLine("    state._aggregateSelections.add(agg)")
                appendLine("    return $builderClassName(state)")
                appendLine("}")
                appendLine()

                // Generate Has1Aggregate -> Has2Aggregates
                appendLine("@JvmName(\"selectAggregate1\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has1Aggregate>.selectAggregate(")
                appendLine("    block: $contextClassName.() -> com.obabichev.kodama.query.AggregateFunction<*>")
                appendLine("): $builderClassName<$genericParams, Has2Aggregates> {")
                appendLine("    val context = $contextClassName(state)")
                appendLine("    val agg = context.block()")
                appendLine("    state._aggregateSelections.add(agg)")
                appendLine("    return $builderClassName(state)")
                appendLine("}")
                appendLine()

                // Generate Has2Aggregates -> Has3Aggregates
                appendLine("@JvmName(\"selectAggregate2\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has2Aggregates>.selectAggregate(")
                appendLine("    block: $contextClassName.() -> com.obabichev.kodama.query.AggregateFunction<*>")
                appendLine("): $builderClassName<$genericParams, Has3Aggregates> {")
                appendLine("    val context = $contextClassName(state)")
                appendLine("    val agg = context.block()")
                appendLine("    state._aggregateSelections.add(agg)")
                appendLine("    return $builderClassName(state)")
                appendLine("}")
                appendLine()

                // Generate Has3Aggregates -> Has4Aggregates
                appendLine("@JvmName(\"selectAggregate3\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has3Aggregates>.selectAggregate(")
                appendLine("    block: $contextClassName.() -> com.obabichev.kodama.query.AggregateFunction<*>")
                appendLine("): $builderClassName<$genericParams, Has4Aggregates> {")
                appendLine("    val context = $contextClassName(state)")
                appendLine("    val agg = context.block()")
                appendLine("    state._aggregateSelections.add(agg)")
                appendLine("    return $builderClassName(state)")
                appendLine("}")
                appendLine()

                // Generate Has4Aggregates -> Has5Aggregates
                appendLine("@JvmName(\"selectAggregate4\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has4Aggregates>.selectAggregate(")
                appendLine("    block: $contextClassName.() -> com.obabichev.kodama.query.AggregateFunction<*>")
                appendLine("): $builderClassName<$genericParams, Has5Aggregates> {")
                appendLine("    val context = $contextClassName(state)")
                appendLine("    val agg = context.block()")
                appendLine("    state._aggregateSelections.add(agg)")
                appendLine("    return $builderClassName(state)")
                appendLine("}")
                appendLine()

                // Generate unified selection methods for discovered patterns
                val patternsForThis = selectionPatternsByTable[combination] ?: emptySet()

                if (patternsForThis.isNotEmpty()) {
                    appendLine("// ===== Named selection methods for type safety =====")
                    appendLine("// Supports aggregates, constants, subqueries, window functions, etc.")
                    appendLine("// Each selection combination has a unique compile-time type")
                    appendLine()

                    // First, generate phantom type markers for all selection combinations
                    val generatedMarkers = mutableSetOf<String>()
                    patternsForThis.forEach { pattern ->
                        val selectionsSoFar = mutableListOf<String>()
                        pattern.selections.forEach { selection ->
                            selectionsSoFar.add(selection.alias)
                            val markerName = "SelectionSet_" + selectionsSoFar.joinToString("_")

                            if (!generatedMarkers.contains(markerName)) {
                                generatedMarkers.add(markerName)
                                appendLine("/**")
                                appendLine(" * Phantom type marker for selection: ${selectionsSoFar.joinToString(", ")}")
                                appendLine(" */")
                                appendLine("interface $markerName : SelectionState")
                            }
                        }
                    }
                    appendLine()

                    // Track generated methods to avoid duplicates
                    val generatedMethods = mutableSetOf<String>()
                    val generatedExecuteMethods = mutableSetOf<String>()

                    patternsForThis.forEach { pattern ->
                        // Generate chain of methods for this pattern
                        var currentState = "NoSelections"
                        val selectionsSoFar = mutableListOf<String>()

                        pattern.selections.forEach { selection ->
                            selectionsSoFar.add(selection.alias)
                            val nextState = "SelectionSet_" + selectionsSoFar.joinToString("_")
                            val methodKey = "$currentState->$nextState->${selection.alias}"

                            // Generate method only if not already generated
                            if (!generatedMethods.contains(methodKey)) {
                                generatedMethods.add(methodKey)

                                // Generate method based on selection type
                                when (selection.type) {
                                    SelectionType.AGGREGATE -> {
                                        // Use @JvmName to avoid conflicts when same method name used in different patterns
                                        appendLine("@JvmName(\"select_${selection.alias}_${currentState}_to_${nextState}\")")
                                        appendLine("fun <$genericParams> $builderClassName<$genericParams, $currentState>.select_${selection.alias}(")
                                        appendLine("    block: $contextClassName.() -> com.obabichev.kodama.query.AggregateFunction<*>")
                                        appendLine("): $builderClassName<$genericParams, $nextState> {")
                                        appendLine("    val context = $contextClassName(state)")
                                        appendLine("    val agg = context.block()")
                                        appendLine("    // Automatically set alias from method name if not already set")
                                        appendLine("    val finalAgg = if (!agg.hasExplicitAlias) {")
                                        appendLine("        agg alias \"${selection.alias}\"")
                                        appendLine("    } else {")
                                        appendLine("        agg")
                                        appendLine("    }")
                                        appendLine("    state._selectables.add(com.obabichev.kodama.query.AggregateSelectable(\"${selection.alias}\", finalAgg))")
                                        appendLine("    state._aggregateSelections.add(finalAgg)  // For backward compatibility")
                                        appendLine("    return $builderClassName(state)")
                                        appendLine("}")
                                        appendLine()
                                    }
                                    SelectionType.CONSTANT -> {
                                        appendLine("fun <$genericParams> $builderClassName<$genericParams, $currentState>.select_${selection.alias}(")
                                        appendLine("    value: ${selection.kotlinType}")
                                        appendLine("): $builderClassName<$genericParams, $nextState> {")
                                        appendLine("    state._selectables.add(com.obabichev.kodama.query.ConstantSelectable(\"${selection.alias}\", value))")
                                        appendLine("    return $builderClassName(state)")
                                        appendLine("}")
                                        appendLine()
                                    }
                                    SelectionType.SUBQUERY -> {
                                        appendLine("fun <$genericParams> $builderClassName<$genericParams, $currentState>.select_${selection.alias}(")
                                        appendLine("    block: () -> com.obabichev.kodama.query.Query")
                                        appendLine("): $builderClassName<$genericParams, $nextState> {")
                                        appendLine("    val query = block()")
                                        appendLine("    state._selectables.add(com.obabichev.kodama.query.SubquerySelectable(\"${selection.alias}\", query))")
                                        appendLine("    return $builderClassName(state)")
                                        appendLine("}")
                                        appendLine()
                                    }
                                    else -> {
                                        // Future: window functions, metadata, computed, etc.
                                        appendLine("// TODO: Support ${selection.type} selection type")
                                    }
                                }
                            }

                            currentState = nextState
                        }

                        // Generate execute method for the final state
                        val finalState = currentState
                        val resultClassName = "SelectionResult_" + pattern.selections.joinToString("_") { it.alias }

                        if (!generatedExecuteMethods.contains(resultClassName)) {
                            generatedExecuteMethods.add(resultClassName)

                            appendLine("// Execute method for pattern: ${pattern.selections.joinToString(", ") { it.alias }}")
                            appendLine("@JvmName(\"execute_$resultClassName\")")
                            appendLine("fun <$genericParams> $builderClassName<$genericParams, $finalState>.execute(")
                            appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
                            appendLine("): com.obabichev.kodama.query.QueryResultIterable<$resultClassName> {")
                            appendLine("    val query = this.build()")
                            appendLine("    val resultSet = transaction.execute(query)")
                            appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
                            appendLine("        $resultClassName(rs, relations, query.select, state._selectables)")
                            appendLine("    }")
                            appendLine("}")
                            appendLine()
                        }
                    }
                }

                appendLine()

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

                // Generate where() extension - preserves all selection types and aggregate count
                appendLine("fun <$genericParams, AC : AggCount> $builderClassName<$genericParams, AC>.where(block: $whereContextClassName.() -> com.obabichev.kodama.components.expression.Expression): $builderClassName<$genericParams, AC> {")
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

                // Generate orderBy() extension - preserves all selection types and aggregate count
                appendLine("fun <$genericParams, AC : AggCount> $builderClassName<$genericParams, AC>.orderBy(block: $orderByContextClassName.() -> Unit): $builderClassName<$genericParams, AC> {")
                appendLine("    val context = $orderByContextClassName(state)")
                appendLine("    context.block()")
                appendLine("    state._orderBy.addAll(context.orderByClauses)")
                appendLine("    return this")
                appendLine("}")
                appendLine()

                // Generate type-specific execute() methods for each aggregate count
                appendLine("// ===== Type-specific execute() methods for aggregates =====")
                appendLine()

                // Execute for Has1Aggregate
                appendLine("@JvmName(\"executeAggregate1\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has1Aggregate>.execute(")
                appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
                appendLine("): com.obabichev.kodama.query.QueryResultIterable<com.obabichev.kodama.query.AggregateResult1> {")
                appendLine("    val query = this.build()")
                appendLine("    val resultSet = transaction.execute(query)")
                appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
                appendLine("        com.obabichev.kodama.query.AggregateResult1(rs, relations, query.select, query.aggregates)")
                appendLine("    }")
                appendLine("}")
                appendLine()

                // Execute for Has2Aggregates
                appendLine("@JvmName(\"executeAggregate2\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has2Aggregates>.execute(")
                appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
                appendLine("): com.obabichev.kodama.query.QueryResultIterable<com.obabichev.kodama.query.AggregateResult2> {")
                appendLine("    val query = this.build()")
                appendLine("    val resultSet = transaction.execute(query)")
                appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
                appendLine("        com.obabichev.kodama.query.AggregateResult2(rs, relations, query.select, query.aggregates)")
                appendLine("    }")
                appendLine("}")
                appendLine()

                // Execute for Has3Aggregates
                appendLine("@JvmName(\"executeAggregate3\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has3Aggregates>.execute(")
                appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
                appendLine("): com.obabichev.kodama.query.QueryResultIterable<com.obabichev.kodama.query.AggregateResult3> {")
                appendLine("    val query = this.build()")
                appendLine("    val resultSet = transaction.execute(query)")
                appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
                appendLine("        com.obabichev.kodama.query.AggregateResult3(rs, relations, query.select, query.aggregates)")
                appendLine("    }")
                appendLine("}")
                appendLine()

                // Execute for Has4Aggregates
                appendLine("@JvmName(\"executeAggregate4\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has4Aggregates>.execute(")
                appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
                appendLine("): com.obabichev.kodama.query.QueryResultIterable<com.obabichev.kodama.query.AggregateResult4> {")
                appendLine("    val query = this.build()")
                appendLine("    val resultSet = transaction.execute(query)")
                appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
                appendLine("        com.obabichev.kodama.query.AggregateResult4(rs, relations, query.select, query.aggregates)")
                appendLine("    }")
                appendLine("}")
                appendLine()

                // Execute for Has5Aggregates
                appendLine("@JvmName(\"executeAggregate5\")")
                appendLine("fun <$genericParams> $builderClassName<$genericParams, Has5Aggregates>.execute(")
                appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
                appendLine("): com.obabichev.kodama.query.QueryResultIterable<com.obabichev.kodama.query.AggregateResult5> {")
                appendLine("    val query = this.build()")
                appendLine("    val resultSet = transaction.execute(query)")
                appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
                appendLine("        com.obabichev.kodama.query.AggregateResult5(rs, relations, query.select, query.aggregates)")
                appendLine("    }")
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

                // Generate accessors for selection patterns found in tests (3+ columns)
                // This is needed for patterns like Name_Description_Discount that are not pairs
                selectionPatterns.forEach { (tables, patterns) ->
                    patterns.forEach { selectionList ->
                        // Group by table
                        val selectionsByTable = selectionList.groupBy { it.split(":")[0] }

                        selectionsByTable.forEach { (table, selections) ->
                            val tableCapitalized = table.replaceFirstChar { it.uppercase() }
                            val types = selections.map { it.split(":")[1] }

                            // Skip All (already generated) and single columns (already generated) and pairs (already generated)
                            if (types.size >= 3 && !types.contains("All")) {
                                val accessorClass = "${tableCapitalized}ResultAccessor_" + types.joinToString("_")

                                if (!generatedResultAccessors.contains(accessorClass)) {
                                    generatedResultAccessors.add(accessorClass)

                                    val properties = tableToProperties[table] ?: emptyList()
                                    val propertyTypes = tableToPropertyTypes[table] ?: emptyMap()
                                    val propertyNullability = tableToPropertyNullability[table] ?: emptyMap()

                                    appendLine("class $accessorClass(")
                                    appendLine("    resultSet: java.sql.ResultSet,")
                                    appendLine("    relations: com.obabichev.kodama.query.RelationsContainer,")
                                    appendLine("    selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
                                    appendLine(") : com.obabichev.kodama.query.TableResultAccessor(resultSet, relations, selectedColumns) {")

                                    // Generate properties for each selected column
                                    types.forEach { type ->
                                        val propName = type.lowercase()
                                        if (propName in properties) {
                                            val kotlinType = propertyTypes[propName] ?: "Any"
                                            val isNullable = propertyNullability[propName] ?: true
                                            val nullabilityMarker = if (isNullable) "?" else ""

                                            appendLine("    val $propName: $kotlinType$nullabilityMarker")
                                            appendLine("        get() = readColumn(com.obabichev.kodama.tests.schema.$tableCapitalized.$propName) as $kotlinType$nullabilityMarker")
                                            appendLine()
                                        }
                                    }

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

            val generatedQueryResultClasses = mutableSetOf<String>()

            selectionPatterns.forEach { (tables, patterns) ->
                patterns.forEach { selectionList ->
                    // Build class name from selection pattern
                    // Example: person:All, order:Product -> QueryResult_Person_All_Order_Product
                    // Example: agg:sum_cost -> QueryResult_Agg_sum_cost
                    val classNameParts = mutableListOf<String>()
                    selectionList.forEach { selection ->
                        val (table, type) = selection.split(":")
                        classNameParts.add(table.replaceFirstChar { it.uppercase() })
                        classNameParts.add(type.replaceFirstChar { it.uppercase() })
                    }
                    val resultClassName = "QueryResult_" + classNameParts.joinToString("_")

                    // Skip if already generated
                    if (generatedQueryResultClasses.contains(resultClassName)) {
                        return@forEach
                    }
                    generatedQueryResultClasses.add(resultClassName)

                    appendLine("/**")
                    appendLine(" * Result class for selection pattern: ${selectionList.joinToString(", ")}")
                    appendLine(" * Only contains accessors for selected columns")
                    appendLine(" */")
                    appendLine("class $resultClassName(")
                    appendLine("    override val resultSet: java.sql.ResultSet,")
                    appendLine("    override val relations: com.obabichev.kodama.query.RelationsContainer,")
                    appendLine("    override val selectedColumns: List<com.obabichev.kodama.components.Column<*>>,")
                    appendLine("    val aggregates: List<com.obabichev.kodama.query.AggregateFunction<*>> = emptyList()")
                    appendLine(") : com.obabichev.kodama.query.QueryResult {")
                    appendLine()

                    // Separate regular selections from aggregate selections
                    val regularSelections = selectionList.filter { !it.startsWith("agg:") }
                    val aggregateSelections = selectionList.filter { it.startsWith("agg:") }

                    // Generate accessors - ONE per table (merging multiple selections)
                    // Group selections by table
                    val selectionsByTable = regularSelections.groupBy { it.split(":")[0] }

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

                    // Generate accessors for aggregate functions
                    aggregateSelections.forEach { aggSelection ->
                        val accessorName = aggSelection.split(":")[1]
                        appendLine("    val $accessorName: Any?")
                        appendLine("        get() {")
                        appendLine("            val agg = aggregates.find { it.accessorName == \"$accessorName\" }")
                        appendLine("            return if (agg != null) {")
                        appendLine("                val index = selectedColumns.size + aggregates.indexOf(agg) + 1")
                        appendLine("                resultSet.getObject(index)")
                        appendLine("            } else null")
                        appendLine("        }")
                        appendLine()
                    }

                    appendLine("}")
                    appendLine()
                }
            }

            // ============================================================================
            // Generate unified selection result classes with named accessors
            // ============================================================================
            appendLine()
            appendLine("// ========== Selection Result Classes with Named Accessors ==========")
            appendLine("// These classes provide compile-time safe access to selected values by alias")
            appendLine()

            val generatedSelectionResultClasses = mutableSetOf<String>()
            selectionPatternsByTable.forEach { (tables, patterns) ->
                patterns.forEach { pattern ->
                    // Build class name from selection aliases
                    val resultClassName = "SelectionResult_" + pattern.selections.joinToString("_") { it.alias }

                    // Skip if already generated
                    if (generatedSelectionResultClasses.contains(resultClassName)) {
                        return@forEach
                    }
                    generatedSelectionResultClasses.add(resultClassName)

                    // Generate class
                    appendLine("class $resultClassName(")
                    appendLine("    override val resultSet: java.sql.ResultSet,")
                    appendLine("    override val relations: com.obabichev.kodama.query.RelationsContainer,")
                    appendLine("    override val selectedColumns: List<com.obabichev.kodama.components.Column<*>>,")
                    appendLine("    private val selectables: List<com.obabichev.kodama.query.Selectable>")
                    appendLine(") : com.obabichev.kodama.query.QueryResult {")
                    appendLine("    init {")
                    appendLine("        require(selectables.size == ${pattern.selections.size}) { \"Expected exactly ${pattern.selections.size} selection(s), got \${selectables.size}\" }")
                    appendLine("        // Verify aliases match")
                    appendLine("        val expectedAliases = listOf(${pattern.selections.joinToString(", ") { "\"${it.alias}\"" }})")
                    appendLine("        val actualAliases = selectables.map { it.alias }")
                    appendLine("        require(actualAliases == expectedAliases) { \"Expected aliases \$expectedAliases, got \$actualAliases\" }")
                    appendLine("    }")
                    appendLine()

                    // Generate table accessors for column selections
                    pattern.columnSelections.forEach { (tableName, columns) ->
                        val tableCapitalized = tableName.replaceFirstChar { it.uppercase() }

                        // Determine which accessor class to use based on what was selected
                        val accessorClass = if (columns.contains("All")) {
                            "${tableCapitalized}ResultAccessor_All"
                        } else if (columns.size == 1) {
                            val col = columns[0].replaceFirstChar { it.uppercase() }
                            "${tableCapitalized}ResultAccessor_$col"
                        } else {
                            // Multiple columns - create combined name
                            val combinedCols = columns.map { it.replaceFirstChar { c -> c.uppercase() } }.joinToString("_")
                            "${tableCapitalized}ResultAccessor_$combinedCols"
                        }

                        appendLine("    val $tableName: $accessorClass")
                        appendLine("        get() = $accessorClass(resultSet, relations, selectedColumns)")
                        appendLine()
                    }

                    // Generate typed accessor for each selection
                    pattern.selections.forEachIndexed { index, selection ->
                        appendLine("    val ${selection.alias}: ${selection.kotlinType}")
                        appendLine("        get() {")
                        appendLine("            val selectable = selectables[$index]")
                        appendLine("            val position = selectedColumns.size + $index + 1")
                        appendLine("            return selectable.getValue(resultSet, position) as ${selection.kotlinType}")
                        appendLine("        }")
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
                        resultClassNameParts.add(type.replaceFirstChar { it.uppercase() })  // Must match result class generation
                    }
                    val specificResultClassName = "QueryResult_" + resultClassNameParts.joinToString("_")

                    // Skip if this wasn't generated (duplicate)
                    if (!generatedQueryResultClasses.contains(specificResultClassName)) {
                        return@forEach
                    }

                    // Only generate execute() if pattern selects from ALL tables in the combination
                    // This prevents ambiguity (e.g., don't generate Person_All execute() on Person_Order builder)
                    val regularSelections = selectionList.filter { !it.startsWith("agg:") }
                    val tablesInPattern = regularSelections.map { it.split(":")[0] }.toSet()
                    if (tablesInPattern.size != tables.size) {
                        return@forEach  // Pattern doesn't cover all tables - skip
                    }

                    // Build type parameters - one for EACH table in the query
                    // Must match the builder class's type parameter count
                    // Group selections by table to handle multiple selections per table
                    // Note: Aggregate selections (agg:xxx) don't belong to any specific table
                    val selectionsByTable = regularSelections.groupBy { it.split(":")[0] }

                    val typeParams = mutableListOf<String>()
                    tables.forEach { table ->
                        val tableSelections = selectionsByTable[table]

                        val typeParam = if (tableSelections == null) {
                            // No selections for this table (could be aggregate-only query)
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

                    // Generate generic type parameter names (PersonSel, OrderSel, etc.) - one per table
                    val genericParamNames = tables.map { "${it.replaceFirstChar { ch -> ch.uppercase() }}Sel" }
                    val genericParams = genericParamNames.joinToString(", ")
                    val genericParamsWithAC = "$genericParams, AC : com.obabichev.kodama.query.SelectionState"

                    // Builder receiver uses generic parameter names, NOT concrete types
                    val receiverTypeArgs = genericParamNames.joinToString(", ") + ", AC"

                    appendLine("/**")
                    appendLine(" * Execute overload for selection: ${selectionList.joinToString(", ")}")
                    appendLine(" * Returns: $specificResultClassName (only selected accessors available)")
                    appendLine(" */")
                    appendLine("@JvmName(\"execute_$specificResultClassName\")")
                    appendLine("fun <$genericParamsWithAC> $builderClassName<$receiverTypeArgs>.execute(transaction: com.obabichev.kodama.execute.JdbcTransaction): com.obabichev.kodama.query.QueryResultIterable<$specificResultClassName> {")
                    appendLine("    val query = this.build()")
                    appendLine("    val resultSet = transaction.execute(query)")
                    appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
                    appendLine("        $specificResultClassName(rs, relations, query.select, query.aggregates)")
                    appendLine("    }")
                    appendLine("}")
                    appendLine()
                }
            }

            // ============================================================================
            // Generate INSERT extension methods for each table
            // ============================================================================
            appendLine()
            appendLine("// ========== INSERT Methods ==========")
            appendLine("// Type-safe insert methods with all columns as required parameters")
            appendLine()

            tables.forEach { tableName ->
                val capitalizedTableName = tableName.replaceFirstChar { it.uppercase() }

                // Get column metadata for this table
                val properties = tableToProperties[tableName] ?: emptyList()
                val propertyTypes = tableToPropertyTypes[tableName] ?: emptyMap()
                val propertyNullability = tableToPropertyNullability[tableName] ?: emptyMap()

                if (properties.isEmpty()) {
                    return@forEach  // Skip tables with no columns
                }

                // Generate parameter list: "transaction: JdbcTransaction, colName1: Type1, colName2: Type2, ..."
                val parameters = buildString {
                    append("transaction: com.obabichev.kodama.execute.JdbcTransaction")
                    properties.forEach { propName ->
                        append(",\n    ")
                        append(propName)
                        append(": ")

                        // Get Kotlin type from property types
                        val kotlinType = propertyTypes[propName] ?: "Any"

                        // Add nullable marker if column is nullable
                        append(kotlinType)
                        val isNullable = propertyNullability[propName] ?: false
                        if (isNullable) {
                            append("?")
                        }
                    }
                }

                // Generate column list for InsertStatement
                val columnList = properties.joinToString(", ") { "table.$it" }

                // Generate values list
                val valuesList = properties.joinToString(", ") { it }

                appendLine("/**")
                appendLine(" * Insert a row into the $tableName table.")
                appendLine(" * All columns are required parameters for compile-time safety.")
                appendLine(" *")
                properties.forEach { propName ->
                    val isNullable = propertyNullability[propName] ?: false
                    appendLine(" * @param $propName ${if (isNullable) "(nullable) " else ""}Value for column '$propName'")
                }
                appendLine(" * @return InsertResult with rows affected and generated keys")
                appendLine(" */")
                appendLine("fun com.obabichev.kodama.tests.schema.$capitalizedTableName.insert(")
                appendLine("    $parameters")
                appendLine("): com.obabichev.kodama.insert.InsertResult {")
                appendLine("    val table = this")
                appendLine("    val insert = com.obabichev.kodama.insert.InsertStatement(")
                appendLine("        table = table,")
                appendLine("        columns = listOf($columnList),")
                appendLine("        values = listOf($valuesList)")
                appendLine("    )")
                appendLine("    return transaction.executeInsert(insert)")
                appendLine("}")
                appendLine()
            }

            appendLine()
        })

        val totalColumnPatterns = selectionPatterns.values.sumOf { it.size }
        val totalSelectionPatterns = selectionPatternsByTable.values.sumOf { it.size }
        logger.lifecycle("Kodama: Generated ${tables.size} tables, ${queryCombinations.size} query combinations, $totalColumnPatterns column patterns, $totalSelectionPatterns selection patterns")
    }
}
