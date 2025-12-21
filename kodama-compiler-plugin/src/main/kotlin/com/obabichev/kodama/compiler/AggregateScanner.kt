package com.obabichev.kodama.compiler

/**
 * Scanner for aggregate function selections.
 * Matches patterns like: .selectAggregate { sum(order.cost) alias "totalRevenue" }
 */
class AggregateScanner : SelectionPatternScanner {
    override fun scanFile(content: String, tableNameMap: Map<String, String>): List<SelectionPattern> {
        val patterns = mutableListOf<SelectionPattern>()

        // Match complete query chains with selectAggregate calls
        val queryPattern = """query\s*\(\s*\)(?:(?!\.(?:build|execute)\()[\s\S])*?\.(?:execute|build)\(""".toRegex()

        queryPattern.findAll(content).forEach { queryMatch ->
            val queryChain = queryMatch.value

            // Extract table combination
            val tables = mutableListOf<String>()
            val tableRefPattern = """(?:from|fromAliased|join|joinAliased|leftJoin|leftJoinAliased)\s*\(\s*(\w+)""".toRegex()
            tableRefPattern.findAll(queryChain).forEach { match ->
                val tableRef = match.groupValues[1]
                val tableName = tableNameMap[tableRef.lowercase()] ?: tableRef  // Use original case
                if (!tables.contains(tableName)) {
                    tables.add(tableName)
                }
            }

            // Extract both column selections and aggregate selections
            val selections = mutableListOf<Selection>()
            val foundAliases = mutableSetOf<String>()
            val columnSelectionsByTable = mutableMapOf<String, MutableList<String>>()  // Track column selections by table

            // New unified API pattern: .select_xxx { ... } (alias inferred from method name)
            // This can be either aggregate or expression selection
            val selectUnifiedSimplePattern = """\.select_(\w+)\s*\{([^}]+)\}""".toRegex()
            selectUnifiedSimplePattern.findAll(queryChain).forEach { match ->
                val alias = match.groupValues[1]
                val blockContent = match.groupValues[2]

                // Only add if we haven't seen this alias yet (avoid duplicates)
                if (!foundAliases.contains(alias)) {
                    foundAliases.add(alias)

                    // Determine type based on block content
                    val isAggregate = blockContent.contains(Regex("""(sum|count|avg|min|max|countAll)\s*\("""))
                    val isExpression = blockContent.contains(Regex("""\s+(gt|lt|gte|lte|eq|neq|and|or|not)\s+"""))

                    val (selectionType, kotlinType) = when {
                        isAggregate -> SelectionType.AGGREGATE to "Number"
                        isExpression -> SelectionType.COMPUTED to "Any"  // Expressions can return various types
                        else -> SelectionType.AGGREGATE to "Number"  // Default to aggregate for backward compatibility
                    }

                    selections.add(
                        Selection(
                            alias = alias,
                            type = selectionType,
                            kotlinType = kotlinType
                        )
                    )
                }
            }

            // Old API pattern: .selectAggregate { aggregate() alias "xxx" } (for backward compatibility)
            val selectAggregatePattern = """\.selectAggregate\s*\{[^}]*alias\s+"(\w+)"[^}]*\}""".toRegex()
            selectAggregatePattern.findAll(queryChain).forEach { match ->
                val alias = match.groupValues[1]
                if (!foundAliases.contains(alias)) {
                    foundAliases.add(alias)
                    selections.add(
                        Selection(
                            alias = alias,
                            type = SelectionType.AGGREGATE,
                            kotlinType = "Number"
                        )
                    )
                }
            }

            // NEW: Also detect regular column selections in the same query
            // Pattern: .select { table.column }
            val regularSelectPattern = """\.select\s*\{\s*(\w+)\.(\w+)\s*\}""".toRegex()
            regularSelectPattern.findAll(queryChain).forEach { match ->
                val tableRef = match.groupValues[1]
                val tableName = tableNameMap[tableRef.lowercase()] ?: tableRef  // Use original case
                val columnName = match.groupValues[2]

                // Track this column selection
                columnSelectionsByTable.getOrPut(tableName) { mutableListOf() }.add(columnName)
            }

            // Pattern: .selectAll(Table)
            val selectAllPattern = """\.selectAll\s*\(\s*(\w+)\s*\)""".toRegex()
            selectAllPattern.findAll(queryChain).forEach { match ->
                val tableRef = match.groupValues[1]
                val tableName = tableNameMap[tableRef.lowercase()] ?: tableRef  // Use original case

                // Track this as "All" for the table
                columnSelectionsByTable.getOrPut(tableName) { mutableListOf() }.add("All")
            }

            // If we found both tables and selections, record the pattern
            if (tables.isNotEmpty() && selections.isNotEmpty()) {
                patterns.add(SelectionPattern(tables, selections, columnSelectionsByTable))
            }
        }

        return patterns
    }
}
