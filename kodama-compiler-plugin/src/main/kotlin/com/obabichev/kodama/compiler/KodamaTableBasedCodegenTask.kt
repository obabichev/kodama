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
                val propertyPattern = """val\s+(\w+)\s*=\s*(varchar|integer|smallint|bigint|text|boolean|timestamp|date|double|doublePrecision|float|real|long|bigDecimal|decimal)\s*\([^)]*\)([^\n]*)""".toRegex()
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
                        "smallint" -> "Short"
                        "bigint" -> "Long"
                        "long" -> "Long"
                        "decimal" -> "java.math.BigDecimal"
                        "bigDecimal" -> "java.math.BigDecimal"
                        "real" -> "Float"
                        "float" -> "Float"
                        "doublePrecision" -> "Double"
                        "double" -> "Double"
                        "boolean" -> "Boolean"
                        "timestamp", "date" -> "java.time.LocalDateTime"
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

                // First, detect named aggregate selection methods: .select_xxx { aggregate(...) }
                // The accessor name comes from the method name, not an alias
                val namedSelectPattern = """\.select_(\w+)\s*\{([^}]*)\}""".toRegex()
                namedSelectPattern.findAll(queryChain).forEach { namedMatch ->
                    val accessorName = namedMatch.groupValues[1]
                    val blockContent = namedMatch.groupValues[2]

                    // Check if block contains an aggregate function
                    if (aggregatePattern.containsMatchIn(blockContent)) {
                        val selection = "agg:$accessorName"
                        if (selection !in selections) {
                            selections.add(selection)
                        }
                    }
                }

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
                appendLine("        if (state._selectedColumns.isEmpty() && state._aggregateSelections.isEmpty() && state._selectables.isEmpty()) {")
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
                appendLine("            groupBy,")
                appendLine("            state._selectables.toList()")
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
                                    SelectionType.COMPUTED -> {
                                        appendLine("@JvmName(\"select_${selection.alias}_${currentState}_to_${nextState}\")")
                                        appendLine("fun <$genericParams> $builderClassName<$genericParams, $currentState>.select_${selection.alias}(")
                                        appendLine("    block: $contextClassName.() -> com.obabichev.kodama.components.expression.Expression")
                                        appendLine("): $builderClassName<$genericParams, $nextState> {")
                                        appendLine("    val context = $contextClassName(state)")
                                        appendLine("    val expr = context.block()")
                                        appendLine("    state._selectables.add(com.obabichev.kodama.query.ExpressionSelectable(\"${selection.alias}\", expr))")
                                        appendLine("    return $builderClassName(state)")
                                        appendLine("}")
                                        appendLine()
                                    }
                                    else -> {
                                        // Future: window functions, metadata, etc.
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
                    appendLine()

                    // Generate cached value properties
                    pattern.selections.forEachIndexed { index, selection ->
                        appendLine("    val ${selection.alias}: ${selection.kotlinType}")
                    }
                    appendLine()

                    appendLine("    init {")
                    appendLine("        require(selectables.size == ${pattern.selections.size}) { \"Expected exactly ${pattern.selections.size} selection(s), got \${selectables.size}\" }")
                    appendLine("        // Verify aliases match")
                    appendLine("        val expectedAliases = listOf(${pattern.selections.joinToString(", ") { "\"${it.alias}\"" }})")
                    appendLine("        val actualAliases = selectables.map { it.alias }")
                    appendLine("        require(actualAliases == expectedAliases) { \"Expected aliases \$expectedAliases, got \$actualAliases\" }")
                    appendLine()
                    appendLine("        // Cache values from result set")
                    pattern.selections.forEachIndexed { index, selection ->
                        appendLine("        ${selection.alias} = run {")
                        appendLine("            val selectable = selectables[$index]")
                        appendLine("            val position = selectedColumns.size + $index + 1")
                        appendLine("            selectable.getValue(resultSet, position) as ${selection.kotlinType}")
                        appendLine("        }")
                    }
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

                    // Build type parameters - one for EACH table in the query
                    // Must match the builder class's type parameter count
                    // Group selections by table to handle multiple selections per table
                    // Note: Aggregate selections (agg:xxx) don't belong to any specific table
                    val regularSelections = selectionList.filter { !it.startsWith("agg:") }
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

                    // Determine AC (aggregate count) type parameter from aggregate selections
                    val aggregateSelections = selectionList.filter { it.startsWith("agg:") }
                    val acType = when (aggregateSelections.size) {
                        0 -> "AC"  // No aggregates, keep generic
                        1 -> "com.obabichev.kodama.query.Has1Aggregate"
                        2 -> "com.obabichev.kodama.query.Has2Aggregates"
                        3 -> "com.obabichev.kodama.query.Has3Aggregates"
                        4 -> "com.obabichev.kodama.query.Has4Aggregates"
                        5 -> "com.obabichev.kodama.query.Has5Aggregates"
                        else -> "AC"  // Fallback for >5 aggregates
                    }

                    // Use CONCRETE types from typeParams and acType, not generic parameters
                    // This ensures type safety: execute() only accepts the exact selection state
                    val receiverTypeArgs = typeParams.joinToString(", ") + ", $acType"
                    val genericParamsWithAC = if (acType == "AC") {
                        "AC : com.obabichev.kodama.query.SelectionState"
                    } else {
                        ""  // No generic parameters needed
                    }

                    appendLine("/**")
                    appendLine(" * Execute overload for selection: ${selectionList.joinToString(", ")}")
                    appendLine(" * Returns: $specificResultClassName (only selected accessors available)")
                    appendLine(" */")
                    appendLine("@JvmName(\"execute_$specificResultClassName\")")
                    val genericBrackets = if (genericParamsWithAC.isEmpty()) "" else "<$genericParamsWithAC> "
                    appendLine("fun $genericBrackets$builderClassName<$receiverTypeArgs>.execute(transaction: com.obabichev.kodama.execute.JdbcTransaction): com.obabichev.kodama.query.QueryResultIterable<$specificResultClassName> {")
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

        // Phase 5: Generate Entity Bindings
        val generatedBindings = generateEntityBindings()

        logger.lifecycle("Kodama: Generated ${tables.size} tables, ${queryCombinations.size} query combinations, $totalColumnPatterns column patterns, $totalSelectionPatterns selection patterns, $generatedBindings entity bindings")
    }

    // Phase 5: Data classes for entity binding generation
    private data class EntityTableInfo(
        val tableName: String,  // e.g., "Users"
        val entityType: String,  // e.g., "User"
        val properties: List<String>,  // Property names (e.g., "userId")
        val propertyTypes: Map<String, String>,  // Property name -> Kotlin type
        val columnNames: Map<String, String>,  // Property name -> DB column name (e.g., "userId" -> "user_id")
        val primaryKey: String?,  // Primary key property name
        val packageName: String  // Package where EntityTable is defined
    )

    private data class InterfaceInfo(
        val name: String,  // e.g., "User"
        val properties: List<Pair<String, String>>,  // Property name -> type (with nullability)
        val packageName: String,
        val relationshipMethods: List<RelationshipMethodInfo> = emptyList()
    )

    private data class RelationshipMethodInfo(
        val name: String,  // e.g., "orders"
        val returnType: String,  // e.g., "List<UserOrder>" or "User"
        val hasContextReceiver: Boolean  // true if context(EntitySession) is present
    )

    /**
     * Phase 5: Scan for EntityTable<E> objects and interface entities, match them, and generate EntityBindings.
     */
    private fun generateEntityBindings(): Int {
        // Step 1: Scan for EntityTable<E> definitions

        val entityTables = mutableListOf<EntityTableInfo>()

        val schemaFiles = schemaDir.get().asFile.walkTopDown().filter { it.extension == "kt" }
        schemaFiles.forEach { file ->
            val content = file.readText()

            // Extract package
            val packagePattern = """package\s+([\w.]+)""".toRegex()
            val packageMatch = packagePattern.find(content)
            val tablePackageName = packageMatch?.groupValues?.get(1) ?: ""

            // Match: object TableName : EntityTable<EntityType>("table_name") { ... }
            val entityTablePattern = """object\s+(\w+)\s*:\s*EntityTable<(\w+)>\s*\([^)]*\)\s*\{([^}]*)\}""".toRegex()
            entityTablePattern.findAll(content).forEach { match ->
                val tableName = match.groupValues[1]  // e.g., "Users"
                val entityType = match.groupValues[2]  // e.g., "User"
                val tableBody = match.groupValues[3]

                // Extract properties (columns)
                // Pattern matches: val propName = typeMethod("column_name", ...)
                val propertyPattern = """val\s+(\w+)\s*=\s*(\w+)\s*\(\s*"([^"]+)"[^)]*\)([^\n]*)""".toRegex()
                val properties = mutableListOf<String>()
                val propertyTypes = mutableMapOf<String, String>()
                val columnNames = mutableMapOf<String, String>()
                var primaryKey: String? = null

                propertyPattern.findAll(tableBody).forEach { propMatch ->
                    val propName = propMatch.groupValues[1]       // e.g., "userId"
                    val typeMethod = propMatch.groupValues[2]     // e.g., "integer"
                    val columnName = propMatch.groupValues[3]     // e.g., "user_id"
                    val modifiers = propMatch.groupValues[4]

                    properties.add(propName)
                    columnNames[propName] = columnName

                    // Map to Kotlin type
                    val kotlinType = when (typeMethod) {
                        "varchar", "text" -> "String"
                        "integer" -> "Int"
                        "smallint" -> "Short"
                        "bigint" -> "Long"
                        "long" -> "Long"
                        "decimal" -> "java.math.BigDecimal"
                        "bigDecimal" -> "java.math.BigDecimal"
                        "real", "float" -> "Float"
                        "doublePrecision", "double" -> "Double"
                        "boolean" -> "Boolean"
                        "timestamp", "date" -> "java.time.LocalDateTime"
                        else -> "Any"
                    }
                    propertyTypes[propName] = kotlinType

                    // Check if primary key
                    if (modifiers.contains(".primaryKey()")) {
                        primaryKey = propName
                    }
                }

                entityTables.add(EntityTableInfo(tableName, entityType, properties, propertyTypes, columnNames, primaryKey, tablePackageName))
            }
        }

        // Step 2: Scan for interface definitions
        val interfaces = mutableListOf<InterfaceInfo>()

        val entityFiles = schemaDir.get().asFile.walkTopDown().filter { it.extension == "kt" }
        entityFiles.forEach { file ->
            val content = file.readText()

            // Extract package
            val packagePattern = """package\s+([\w.]+)""".toRegex()
            val packageMatch = packagePattern.find(content)
            val packageName = packageMatch?.groupValues?.get(1) ?: ""

            // Pattern: interface EntityName { ... }
            // Use a more robust pattern that handles multi-line interfaces
            val interfacePattern = """interface\s+(\w+)\s*\{""".toRegex()
            val interfaceMatches = interfacePattern.findAll(content).toList()

            interfaceMatches.forEach { match ->
                val interfaceName = match.groupValues[1]
                val startIndex = match.range.last + 1

                // Find matching closing brace
                var braceCount = 1
                var endIndex = startIndex
                while (endIndex < content.length && braceCount > 0) {
                    when (content[endIndex]) {
                        '{' -> braceCount++
                        '}' -> braceCount--
                    }
                    endIndex++
                }

                val interfaceBody = content.substring(startIndex, endIndex - 1)

                // Extract properties
                val propPattern = """val\s+(\w+)\s*:\s*([^=\n]+)""".toRegex()
                val properties = propPattern.findAll(interfaceBody)
                    .map {
                        val propName = it.groupValues[1]
                        // Remove inline comments and trim
                        val propType = it.groupValues[2]
                            .substringBefore("//")  // Remove inline comments
                            .trim()
                        propName to propType
                    }
                    .toList()

                // Extract relationship methods with context parameters
                // Pattern: context(session: EntitySession) fun methodName(): ReturnType
                val methodPattern = """context\s*\(\s*session\s*:\s*EntitySession\s*\)\s*fun\s+(\w+)\s*\(\s*\)\s*:\s*([^\n{]+)""".toRegex()
                val relationshipMethods = methodPattern.findAll(interfaceBody)
                    .map {
                        val methodName = it.groupValues[1]
                        val returnType = it.groupValues[2].trim()
                        RelationshipMethodInfo(methodName, returnType, hasContextReceiver = true)
                    }
                    .toList()

                interfaces.add(InterfaceInfo(interfaceName, properties, packageName, relationshipMethods))
            }
        }

        // Step 3: Match EntityTables with interfaces
        val matches = mutableListOf<Pair<EntityTableInfo, InterfaceInfo>>()
        entityTables.forEach { table ->
            val interfaceEntity = interfaces.find { it.name == table.entityType }
            if (interfaceEntity != null) {
                matches.add(table to interfaceEntity)
            }
        }

        // Step 4: Scan for relationship declarations
        val relationships = scanRelationshipDeclarations(matches, schemaFiles)

        // Step 5: Generate code for each match
        matches.forEach { (table, interface_) ->
            // Get relationships for this entity
            val entityRelationships = relationships.filter { it.sourceEntity == table.entityType }

            // Generate implementation + factory for interface
            generateInterfaceImplementation(
                entityType = table.entityType,
                properties = interface_.properties,
                entityPackageName = interface_.packageName,
                relationshipMethods = interface_.relationshipMethods,
                tableName = table.tableName,
                relationships = entityRelationships
            )

            // Generate EntityBinding
            generateEntityBinding(
                tableName = table.tableName,
                entityType = table.entityType,
                properties = table.properties,
                propertyTypes = table.propertyTypes,
                columnNames = table.columnNames,
                primaryKey = table.primaryKey,
                entityPackageName = interface_.packageName,
                tablePackageName = table.packageName
            )
        }

        // Step 5: Generate entity companion extensions
        // DISABLED: Using reified generics instead (session.find<User>(id))
        // matches.forEach { (table, dataClass) ->
        //     generateEntityExtensions(
        //         tableName = table.tableName,
        //         entityType = table.entityType,
        //         primaryKey = table.primaryKey,
        //         primaryKeyType = table.propertyTypes[table.primaryKey] ?: "Int",
        //         entityPackageName = dataClass.packageName,
        //         tablePackageName = table.packageName
        //     )
        // }

        // Step 6: Generate binding registry
        if (matches.isNotEmpty()) {
            generateBindingRegistry(matches)
        }

        // Step 7: Generate relationship extension functions
        // DISABLED: Relationship methods are now generated in *Impl classes
        // if (matches.isNotEmpty()) {
        //     generateRelationshipExtensions(matches, schemaFiles)
        // }

        return matches.size
    }

    /**
     * Generate implementation class and factory function for interface-based entities.
     */
    private fun generateInterfaceImplementation(
        entityType: String,
        properties: List<Pair<String, String>>,
        entityPackageName: String,
        relationshipMethods: List<RelationshipMethodInfo> = emptyList(),
        tableName: String,
        relationships: List<RelationshipDeclaration> = emptyList()
    ) {
        val implName = "${entityType}Impl"
        val outputFile = outputDir.get().asFile.resolve(
            "${entityPackageName.replace('.', '/')}/impl/$implName.kt"
        )
        outputFile.parentFile.mkdirs()

        outputFile.writeText(buildString {
            appendLine("// Generated by Kodama Code Generator - Interface Implementation")
            appendLine("// DO NOT EDIT: This file is automatically generated")
            appendLine()
            appendLine("package $entityPackageName.impl")
            appendLine()
            appendLine("import $entityPackageName.$entityType")
            if (relationshipMethods.isNotEmpty()) {
                appendLine("import com.obabichev.kodama.entity.EntitySession")

                // Import target entity types
                relationships.forEach { rel ->
                    appendLine("import $entityPackageName.${rel.targetEntity}")
                }

                // Import table objects
                relationships.forEach { rel ->
                    appendLine("import ${rel.tablePackage}.${rel.targetTable}")
                }
            }
            appendLine()

            // Internal data class implementation
            appendLine("/**")
            appendLine(" * Internal implementation of $entityType interface.")
            appendLine(" * Provides data class features (copy, equals, hashCode) and relationship methods.")
            appendLine(" */")
            appendLine("internal data class $implName(")
            properties.forEachIndexed { index, (propName, propType) ->
                val comma = if (index < properties.size - 1) "," else ""
                appendLine("    override val $propName: $propType$comma")
            }

            if (relationshipMethods.isNotEmpty()) {
                appendLine(") : $entityType {")
                appendLine()

                // Generate relationship method implementations
                relationshipMethods.forEach { method ->
                    // Find matching relationship declaration
                    val relationship = relationships.find { it.relationshipName == method.name }
                    if (relationship != null) {
                        appendLine("    /**")
                        appendLine("     * ${method.name} relationship - loads related ${relationship.targetEntity} ${if (relationship.type == RelationshipType.ONE_TO_MANY) "entities" else "entity"}.")
                        appendLine("     */")
                        appendLine("    context(session: EntitySession)")
                        appendLine("    override fun ${method.name}(): ${method.returnType} {")

                        when (relationship.type) {
                            RelationshipType.ONE_TO_MANY -> {
                                // One-to-many: find all children by foreign key
                                appendLine("        return session.findByForeignKey<${relationship.targetEntity}, Int, Int>(")
                                appendLine("            ${relationship.targetTable}, ${relationship.targetTable}.${relationship.foreignKeyColumn}, this.id")
                                appendLine("        )")
                            }
                            RelationshipType.MANY_TO_ONE -> {
                                // Many-to-one: find parent by ID
                                appendLine("        return session.find<${relationship.targetEntity}>(this.${relationship.foreignKeyColumn})!!")
                            }
                        }

                        appendLine("    }")
                        appendLine()
                    }
                }
                appendLine("}")
            } else {
                appendLine(") : $entityType")
            }
            appendLine()

            // Factory function
            appendLine("/**")
            appendLine(" * Factory function to create $entityType instances.")
            appendLine(" */")
            appendLine("fun $entityType(")
            properties.forEachIndexed { index, (propName, propType) ->
                val comma = if (index < properties.size - 1) "," else ""
                appendLine("    $propName: $propType$comma")
            }
            appendLine("): $entityType = $implName(")
            properties.forEachIndexed { index, (propName, _) ->
                val comma = if (index < properties.size - 1) "," else ""
                appendLine("    $propName = $propName$comma")
            }
            appendLine(")")
            appendLine()

            // Copy extension function
            appendLine("/**")
            appendLine(" * Copy extension function for $entityType interface.")
            appendLine(" * Delegates to the underlying data class copy method.")
            appendLine(" */")
            appendLine("fun $entityType.copy(")
            properties.forEachIndexed { index, (propName, propType) ->
                val comma = if (index < properties.size - 1) "," else ""
                appendLine("    $propName: $propType = this.$propName$comma")
            }
            appendLine("): $entityType {")
            appendLine("    return (this as $implName).copy(")
            properties.forEachIndexed { index, (propName, _) ->
                val comma = if (index < properties.size - 1) "," else ""
                appendLine("        $propName = $propName$comma")
            }
            appendLine("    )")
            appendLine("}")
        })

        logger.lifecycle("Kodama: Generated interface implementation: $implName.kt")
    }

    /**
     * Generate EntityBinding implementation for a matched EntityTable and data class.
     */
    private fun generateEntityBinding(
        tableName: String,
        entityType: String,
        properties: List<String>,
        propertyTypes: Map<String, String>,
        columnNames: Map<String, String>,
        primaryKey: String?,
        entityPackageName: String,
        tablePackageName: String
    ) {
        val bindingName = "${entityType}EntityBinding"
        val outputFile = outputDir.get().asFile.resolve("${entityPackageName.replace('.', '/')}/bindings/$bindingName.kt")

        outputFile.parentFile.mkdirs()

        val pkName = primaryKey ?: properties.firstOrNull() ?: "id"
        val pkType = propertyTypes[pkName] ?: "Int"

        // Determine the registry package (parent of entity package)
        val registryPackage = entityPackageName.substringBeforeLast(".", entityPackageName)

        outputFile.writeText(buildString {
            appendLine("// Generated by Kodama Code Generator - Phase 5")
            appendLine("// DO NOT EDIT: This file is automatically generated")
            appendLine()
            appendLine("package $entityPackageName.bindings")
            appendLine()
            appendLine("import com.obabichev.kodama.components.Column")
            appendLine("import com.obabichev.kodama.entity.EntityBinding")
            appendLine("import $entityPackageName.$entityType")
            appendLine("import $entityPackageName.impl.$entityType")  // Factory function
            appendLine("import $tablePackageName.$tableName")
            appendLine("import java.sql.ResultSet")
            appendLine("import $registryPackage.KodamaBindingRegistry")
            appendLine()
            appendLine("// Ensure the registry is loaded to enable auto-registration")
            appendLine("private val _initRegistry = KodamaBindingRegistry")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated EntityBinding for $entityType ↔ $tableName.")
            appendLine(" *")
            appendLine(" * Maps between:")
            appendLine(" * - Entity: $entityType (interface)")
            appendLine(" * - Table: $tableName (EntityTable)")
            appendLine(" *")
            appendLine(" * Generated methods:")
            appendLine(" * - toEntity: ResultSet → $entityType")
            appendLine(" * - toInsertValues: $entityType → Map<Column<*>, Any?>")
            appendLine(" * - toUpdateValues: Detect changes and return only modified fields")
            appendLine(" * - entityId: Extract primary key ($pkName)")
            appendLine(" * - primaryKeyColumns: Return primary key columns")
            appendLine(" */")
            appendLine("object $bindingName : EntityBinding<$entityType, $pkType> {")
            appendLine()
            appendLine("    override val table = $tableName")
            appendLine()
            appendLine("    override fun entityId(entity: $entityType): $pkType {")
            appendLine("        return entity.$pkName")
            appendLine("    }")
            appendLine()
            appendLine("    override fun toEntity(resultSet: ResultSet): $entityType {")
            appendLine("        return $entityType(")

            // Generate property mapping from ResultSet
            properties.forEachIndexed { index, propName ->
                val propType = propertyTypes[propName] ?: "Any"
                val columnName = columnNames[propName] ?: propName  // Use DB column name
                val getter = when (propType) {
                    "String" -> "resultSet.getString(\"$columnName\")"
                    "Int" -> "resultSet.getInt(\"$columnName\")"
                    "Long" -> "resultSet.getLong(\"$columnName\")"
                    "Short" -> "resultSet.getShort(\"$columnName\")"
                    "Float" -> "resultSet.getFloat(\"$columnName\")"
                    "Double" -> "resultSet.getDouble(\"$columnName\")"
                    "Boolean" -> "resultSet.getBoolean(\"$columnName\")"
                    "java.math.BigDecimal" -> "resultSet.getBigDecimal(\"$columnName\")"
                    else -> "resultSet.getObject(\"$columnName\") as $propType"
                }
                val comma = if (index < properties.size - 1) "," else ""
                appendLine("            $propName = $getter$comma")
            }

            appendLine("        )")
            appendLine("    }")
            appendLine()
            appendLine("    override fun toInsertValues(entity: $entityType): Map<Column<*>, Any?> {")
            appendLine("        return mapOf(")

            // Generate column mappings
            val insertMappings = properties.mapIndexed { index, propName ->
                val comma = if (index < properties.size - 1) "," else ""
                "            $tableName.$propName to entity.$propName$comma"
            }
            insertMappings.forEach { appendLine(it) }

            appendLine("        )")
            appendLine("    }")
            appendLine()
            appendLine("    override fun toUpdateValues(entity: $entityType, original: $entityType): Map<Column<*>, Any?> {")
            appendLine("        val changes = mutableMapOf<Column<*>, Any?>()")
            appendLine()

            // Generate field-by-field comparison (exclude primary key)
            val nonPkProperties = properties.filter { it != pkName }
            nonPkProperties.forEach { propName ->
                appendLine("        if (entity.$propName != original.$propName) {")
                appendLine("            changes[$tableName.$propName] = entity.$propName")
                appendLine("        }")
                appendLine()
            }

            appendLine("        return changes")
            appendLine("    }")
            appendLine()
            appendLine("    override fun primaryKeyColumns(): List<Column<*>> {")
            appendLine("        return listOf($tableName.$pkName)")
            appendLine("    }")
            appendLine("}")
            appendLine()
        })
    }

    /**
     * Generate companion object extension functions for entity operations.
     * Allows usage like: User.find(session, id) instead of session.find(Users, id)
     */
    private fun generateEntityExtensions(
        tableName: String,
        entityType: String,
        primaryKey: String?,
        primaryKeyType: String,
        entityPackageName: String,
        tablePackageName: String
    ) {
        val pkName = primaryKey ?: "id"
        val extensionsFile = outputDir.get().asFile.resolve("${entityPackageName.replace('.', '/')}/extensions/${entityType}Extensions.kt")
        extensionsFile.parentFile.mkdirs()

        extensionsFile.writeText(buildString {
            appendLine("// Generated by Kodama Code Generator - Phase 5")
            appendLine("// DO NOT EDIT: This file is automatically generated")
            appendLine()
            appendLine("package $entityPackageName.extensions")
            appendLine()
            appendLine("import com.obabichev.kodama.entity.EntitySession")
            appendLine("import $entityPackageName.$entityType")
            appendLine("import $tablePackageName.$tableName")
            appendLine()
            appendLine("/**")
            appendLine(" * Companion object extensions for $entityType entity.")
            appendLine(" * Provides convenient static-like methods for database operations.")
            appendLine(" */")
            appendLine()
            appendLine("/**")
            appendLine(" * Find an entity by its primary key.")
            appendLine(" * ")
            appendLine(" * Usage: val user = User.find(session, 1)")
            appendLine(" */")
            appendLine("fun $entityType.Companion.find(session: EntitySession, id: $primaryKeyType): $entityType? {")
            appendLine("    return session.find($tableName, id)")
            appendLine("}")
            appendLine()
            appendLine("/**")
            appendLine(" * Save a new entity (stages for INSERT on next flush).")
            appendLine(" * ")
            appendLine(" * Usage: User.save(session, user)")
            appendLine(" */")
            appendLine("fun $entityType.Companion.save(session: EntitySession, entity: $entityType) {")
            appendLine("    session.save<$entityType, $primaryKeyType>(entity)")
            appendLine("}")
            appendLine()
            appendLine("/**")
            appendLine(" * Delete an entity (stages for DELETE on next flush).")
            appendLine(" * ")
            appendLine(" * Usage: User.delete(session, user)")
            appendLine(" */")
            appendLine("fun $entityType.Companion.delete(session: EntitySession, entity: $entityType) {")
            appendLine("    session.delete<$entityType, $primaryKeyType>(entity)")
            appendLine("}")
            appendLine()
        })
    }

    /**
     * Generate a binding registry that EntitySession can use to auto-register bindings.
     */
    private fun generateBindingRegistry(matches: List<Pair<EntityTableInfo, InterfaceInfo>>) {
        val typedMatches = matches

        // Use a common parent package for the registry (e.g., first entity's parent package)
        val firstEntityPackage = typedMatches.first().second.packageName
        val registryPackage = firstEntityPackage.substringBeforeLast(".", firstEntityPackage)

        val registryFile = outputDir.get().asFile.resolve("${registryPackage.replace('.', '/')}/KodamaBindingRegistry.kt")
        registryFile.parentFile.mkdirs()

        registryFile.writeText(buildString {
            appendLine("// Generated by Kodama Code Generator - Phase 5")
            appendLine("// DO NOT EDIT: This file is automatically generated")
            appendLine()
            appendLine("package $registryPackage")
            appendLine()
            appendLine("import com.obabichev.kodama.entity.EntityBinding")
            appendLine("import com.obabichev.kodama.entity.EntitySession")
            appendLine("import com.obabichev.kodama.schema.EntityTable")
            appendLine("import kotlin.reflect.KClass")
            appendLine()

            // Import all bindings and entity types
            typedMatches.forEach { (table, interface_) ->
                appendLine("import ${interface_.packageName}.${interface_.name}")
                appendLine("import ${interface_.packageName}.impl.${interface_.name}")  // Factory
                appendLine("import ${interface_.packageName}.impl.${interface_.name}Impl")  // Implementation class
                appendLine("import ${interface_.packageName}.bindings.${interface_.name}EntityBinding")
                appendLine("import ${table.packageName}.${table.tableName}")
            }
            appendLine()

            appendLine("/**")
            appendLine(" * Auto-generated registry of all EntityBindings.")
            appendLine(" *")
            appendLine(" * This registry is automatically consulted by EntitySession to")
            appendLine(" * eliminate the need for manual binding registration.")
            appendLine(" *")
            appendLine(" * Generated bindings:")
            typedMatches.forEach { (table, interface_) ->
                appendLine(" * - ${interface_.name} ↔ ${table.tableName}")
            }
            appendLine(" */")
            appendLine("object KodamaBindingRegistry {")
            appendLine()
            appendLine("    init {")
            appendLine("        // Register this registry as the auto-binding provider for EntitySession")
            appendLine("        EntitySession.autoBindingProvider = { entityClass ->")
            appendLine("            @Suppress(\"UNCHECKED_CAST\")")
            appendLine("            getBinding<Any, Any>(entityClass as KClass<Any>)")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    /**")
            appendLine("     * Get binding for an entity type, or null if not found.")
            appendLine("     */")
            appendLine("    fun <E : Any, ID : Any> getBinding(entityClass: KClass<E>): EntityBinding<E, ID>? {")
            appendLine("        @Suppress(\"UNCHECKED_CAST\")")
            appendLine("        return when (entityClass) {")

            typedMatches.forEach { (table, interface_) ->
                appendLine("            ${interface_.name}::class -> ${interface_.name}EntityBinding as EntityBinding<E, ID>")
                appendLine("            ${interface_.name}Impl::class -> ${interface_.name}EntityBinding as EntityBinding<E, ID>")
            }

            appendLine("            else -> null")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    /**")
            appendLine("     * Auto-register all bindings in an EntitySession.")
            appendLine("     */")
            appendLine("    fun registerAll(session: EntitySession) {")

            typedMatches.forEach { (table, interface_) ->
                appendLine("        session.registerBinding(${table.tableName}, ${interface_.name}EntityBinding)")
            }

            appendLine("    }")
            appendLine("}")
            appendLine()
        })
    }

    /**
     * Type of relationship.
     */
    private enum class RelationshipType {
        ONE_TO_MANY,
        MANY_TO_ONE
    }

    /**
     * Data class for relationship information from oneToMany and manyToOne declarations.
     */
    private data class RelationshipDeclaration(
        val type: RelationshipType,     // ONE_TO_MANY or MANY_TO_ONE
        val sourceEntity: String,       // e.g., "User" or "UserOrder"
        val relationshipName: String,   // e.g., "orders" or "user"
        val targetEntity: String,       // e.g., "UserOrder" or "User"
        val targetTable: String,        // e.g., "UserOrders" or "Users"
        val foreignKeyColumn: String,   // e.g., "userId" (column in source or target table)
        val tablePackage: String        // e.g., "com.obabichev.kodama.tests.schema"
    )

    /**
     * Scan schema files for relationship declarations.
     */
    private fun scanRelationshipDeclarations(
        matches: List<Pair<EntityTableInfo, InterfaceInfo>>,
        schemaFiles: Sequence<java.io.File>
    ): List<RelationshipDeclaration> {
        val entityToTableMap = mutableMapOf<String, String>()
        val relationships = mutableListOf<RelationshipDeclaration>()

        // Build table name to entity name map and table package map for lookup
        val tableToEntityName = matches.associate { (table, interface_) ->
            table.tableName.lowercase() to interface_.name
        }
        val tableToPackage = matches.associate { (table, _) ->
            table.tableName to table.packageName
        }

        // Scan schema files for EntityTable definitions and oneToMany declarations
        schemaFiles.forEach { file ->
            val content = file.readText()

            // Extract package from file
            val packagePattern = """package\s+([\w.]+)""".toRegex()
            val packageMatch = packagePattern.find(content)
            val filePackage = packageMatch?.groupValues?.get(1) ?: ""

            // Pattern: object TableName : EntityTable<EntityType>(...)
            val entityTablePattern = """object\s+(\w+)\s*:\s*EntityTable<(\w+)>""".toRegex()
            entityTablePattern.findAll(content).forEach { match ->
                val tableName = match.groupValues[1]
                val entityType = match.groupValues[2]
                entityToTableMap[entityType] = tableName
            }

            // Pattern: oneToMany("relationshipName", TargetTable, TargetTable.fkColumn, this.pkColumn)
            val oneToManyPattern = """oneToMany\s*\(\s*"([^"]+)"\s*,\s*(\w+)\s*,\s*(\w+)\.(\w+)\s*,\s*this\.(\w+)\s*\)""".toRegex()
            oneToManyPattern.findAll(content).forEach { match ->
                val relationshipName = match.groupValues[1]
                val targetTableName = match.groupValues[2]
                val fkColumnName = match.groupValues[4]

                // Find source entity - look for the EntityTable that contains this oneToMany call
                val sourceTablePattern = """object\s+(\w+)\s*:\s*EntityTable<(\w+)>\s*\([^)]*\)\s*\{[^}]*init\s*\{[^}]*oneToMany\s*\(\s*"$relationshipName"""".toRegex()
                sourceTablePattern.find(content)?.let { tableMatch ->
                    val sourceEntityName = tableMatch.groupValues[2]
                    val targetEntity = tableToEntityName[targetTableName.lowercase()] ?: "Unknown"
                    val targetPackage = tableToPackage[targetTableName] ?: filePackage

                    relationships.add(
                        RelationshipDeclaration(
                            type = RelationshipType.ONE_TO_MANY,
                            sourceEntity = sourceEntityName,
                            relationshipName = relationshipName,
                            targetEntity = targetEntity,
                            targetTable = targetTableName,
                            foreignKeyColumn = fkColumnName,
                            tablePackage = targetPackage
                        )
                    )
                }
            }

            // Pattern: manyToOne("relationshipName", TargetTable, this.fkColumn, TargetTable.pkColumn)
            val manyToOnePattern = """manyToOne\s*\(\s*"([^"]+)"\s*,\s*(\w+)\s*,\s*this\.(\w+)\s*,\s*(\w+)\.(\w+)\s*\)""".toRegex()
            manyToOnePattern.findAll(content).forEach { match ->
                val relationshipName = match.groupValues[1]
                val targetTableName = match.groupValues[2]
                val fkColumnName = match.groupValues[3]

                // Find source entity - look for the EntityTable that contains this manyToOne call
                val sourceTablePattern = """object\s+(\w+)\s*:\s*EntityTable<(\w+)>\s*\([^)]*\)\s*\{[^}]*init\s*\{[^}]*manyToOne\s*\(\s*"$relationshipName"""".toRegex()
                sourceTablePattern.find(content)?.let { tableMatch ->
                    val sourceEntityName = tableMatch.groupValues[2]
                    val targetEntity = tableToEntityName[targetTableName.lowercase()] ?: "Unknown"
                    val targetPackage = tableToPackage[targetTableName] ?: filePackage

                    relationships.add(
                        RelationshipDeclaration(
                            type = RelationshipType.MANY_TO_ONE,
                            sourceEntity = sourceEntityName,
                            relationshipName = relationshipName,
                            targetEntity = targetEntity,
                            targetTable = targetTableName,
                            foreignKeyColumn = fkColumnName,
                            tablePackage = targetPackage
                        )
                    )
                }
            }
        }

        return relationships
    }

    /**
     * Generate relationship extension functions.
     * This scans EntityTable oneToMany declarations to generate relationship accessors.
     */
    private fun generateRelationshipExtensions(
        matches: List<Pair<EntityTableInfo, InterfaceInfo>>,
        schemaFiles: Sequence<java.io.File>
    ) {
        // Build a map of entity name to EntityTable name
        val entityToTableMap = mutableMapOf<String, String>()
        val relationships = mutableListOf<RelationshipDeclaration>()

        // Build table name to entity name map and table package map for lookup
        val tableToEntityName = matches.associate { (table, interface_) ->
            table.tableName.lowercase() to interface_.name
        }
        val tableToPackage = matches.associate { (table, _) ->
            table.tableName to table.packageName
        }

        // Scan schema files for EntityTable definitions and oneToMany declarations
        schemaFiles.forEach { file ->
            val content = file.readText()

            // Extract package from file
            val packagePattern = """package\s+([\w.]+)""".toRegex()
            val packageMatch = packagePattern.find(content)
            val filePackage = packageMatch?.groupValues?.get(1) ?: ""

            // Pattern: object TableName : EntityTable<EntityType>(...)
            val entityTablePattern = """object\s+(\w+)\s*:\s*EntityTable<(\w+)>""".toRegex()
            entityTablePattern.findAll(content).forEach { match ->
                val tableName = match.groupValues[1]
                val entityType = match.groupValues[2]
                entityToTableMap[entityType] = tableName
            }

            // Pattern: oneToMany("relationshipName", TargetTable, TargetTable.fkColumn, this.pkColumn)
            val oneToManyPattern = """oneToMany\s*\(\s*"([^"]+)"\s*,\s*(\w+)\s*,\s*(\w+)\.(\w+)\s*,\s*this\.(\w+)\s*\)""".toRegex()
            oneToManyPattern.findAll(content).forEach { match ->
                val relationshipName = match.groupValues[1]
                val targetTableName = match.groupValues[2]
                val fkColumnName = match.groupValues[4]

                // Find source entity - look for the EntityTable that contains this oneToMany call
                val sourceTablePattern = """object\s+(\w+)\s*:\s*EntityTable<(\w+)>\s*\([^)]*\)\s*\{[^}]*init\s*\{[^}]*oneToMany\s*\(\s*"$relationshipName"""".toRegex()
                sourceTablePattern.find(content)?.let { tableMatch ->
                    val sourceEntityName = tableMatch.groupValues[2]
                    val targetEntity = tableToEntityName[targetTableName.lowercase()] ?: "Unknown"
                    val targetPackage = tableToPackage[targetTableName] ?: filePackage

                    relationships.add(
                        RelationshipDeclaration(
                            type = RelationshipType.ONE_TO_MANY,
                            sourceEntity = sourceEntityName,
                            relationshipName = relationshipName,
                            targetEntity = targetEntity,
                            targetTable = targetTableName,
                            foreignKeyColumn = fkColumnName,
                            tablePackage = targetPackage
                        )
                    )
                }
            }
        }

        if (relationships.isEmpty()) {
            return
        }

        // Generate a single file with all relationship extensions
        val firstEntityPackage = matches.first().second.packageName
        val relationshipFile = outputDir.get().asFile.resolve(
            "${firstEntityPackage.replace('.', '/')}/RelationshipExtensions.kt"
        )
        relationshipFile.parentFile.mkdirs()

        relationshipFile.writeText(buildString {
            appendLine("// Generated by Kodama Code Generator")
            appendLine("// DO NOT EDIT: Relationship extensions with context parameters")
            appendLine()
            appendLine("package $firstEntityPackage")
            appendLine()
            appendLine("import com.obabichev.kodama.entity.EntitySession")

            // Import all entity types
            val allEntityTypes = relationships.map { it.sourceEntity }.toSet() + relationships.map { it.targetEntity }.toSet()
            allEntityTypes.forEach { entityType ->
                appendLine("import $firstEntityPackage.$entityType")
            }

            // Import all EntityTables
            matches.forEach { (table, _) ->
                appendLine("import ${table.packageName}.${table.tableName}")
            }

            appendLine()

            // Generate extension functions for each one-to-many relationship
            relationships.forEach { rel ->
                appendLine("/**")
                appendLine(" * ${rel.relationshipName} relationship for ${rel.sourceEntity}.")
                appendLine(" * Uses EntitySession from context parameter to load related entities.")
                appendLine(" *")
                appendLine(" * @return List of related ${rel.targetEntity} entities (may be empty)")
                appendLine(" */")
                appendLine("context(session: EntitySession)")
                appendLine("fun ${rel.sourceEntity}.${rel.relationshipName}(): List<${rel.targetEntity}> {")
                // Provide explicit type arguments to help with type inference
                // Assuming Int for ID and FK types for now
                appendLine("    return session.findByForeignKey<${rel.targetEntity}, Int, Int>(${rel.targetTable}, ${rel.targetTable}.${rel.foreignKeyColumn}, this.id)")
                appendLine("}")
                appendLine()
            }
        })

        logger.lifecycle("Kodama: Generated relationship extensions with context parameters")
    }

}
