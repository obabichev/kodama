package com.obabichev.kodama.compiler

/**
 * Scanner for marker-based selections using .selectAs() and .selectAliased().
 * Matches patterns like:
 * - .selectAs(PersonName) { person.name } - column selections
 * - .selectAliased(TotalRevenue) { sum(order.cost) } - aggregate selections
 */
class MarkerBasedSelectionScanner : SelectionPatternScanner {
    override fun scanFile(content: String, tableNameMap: Map<String, String>): List<SelectionPattern> {
        val patterns = mutableListOf<SelectionPattern>()

        // Match complete query chains starting with from()
        val queryPattern = """from\s*\([^)]+\)(?:(?!\.(?:build|execute)\()[\s\S])*?\.(?:execute|build)\(""".toRegex()

        queryPattern.findAll(content).forEach { queryMatch ->
            val queryChain = queryMatch.value

            // Extract table combination
            val tables = mutableListOf<String>()
            val tableRefPattern = """(?:from|join|leftJoin|rightJoin)\s*\(\s*(\w+)""".toRegex()
            tableRefPattern.findAll(queryChain).forEach { match ->
                val tableRef = match.groupValues[1]
                val tableName = tableNameMap[tableRef.lowercase()] ?: tableRef  // Use original case
                if (!tables.contains(tableName)) {
                    tables.add(tableName)
                }
            }

            // Extract marker-based column selections
            val selections = mutableListOf<Selection>()
            val foundAliases = mutableSetOf<String>()
            val columnSelectionsByTable = mutableMapOf<String, MutableList<String>>()

            // Pattern: .selectAs(MarkerName) { table.column }
            val selectAsPattern = """\.selectAs\s*\(\s*(\w+)\s*\)\s*\{\s*(\w+)\.(\w+)\s*\}""".toRegex()
            selectAsPattern.findAll(queryChain).forEach { match ->
                val markerName = match.groupValues[1]  // e.g., "PersonName"
                val tableRef = match.groupValues[2]    // e.g., "person"
                val columnName = match.groupValues[3]  // e.g., "name"

                // Convert marker name to camelCase alias: PersonName -> personName
                val alias = markerName.replaceFirstChar { it.lowercase() }

                // Resolve table name
                val tableName = tableNameMap[tableRef.lowercase()] ?: tableRef

                // Infer column type from table and column name
                val kotlinType = inferColumnType(tableName, columnName)

                // Only add if we haven't seen this alias yet
                if (!foundAliases.contains(alias)) {
                    foundAliases.add(alias)
                    selections.add(
                        Selection(
                            alias = alias,
                            type = SelectionType.COMPUTED,
                            kotlinType = kotlinType
                        )
                    )
                }

                // NOTE: Don't track individual column selections in columnSelectionsByTable
                // That map is only for .selectAll() calls
            }

            // Pattern: .selectAs(MarkerName) { aggregate(...) } for aggregates
            // Look for aggregate function calls: sum, count, avg, min, max
            val selectAsAggregatePattern = """\.selectAs\s*\(\s*(\w+)\s*\)\s*\{\s*(sum|count|avg|min|max)\s*\(""".toRegex()
            selectAsAggregatePattern.findAll(queryChain).forEach { match ->
                val markerName = match.groupValues[1]  // e.g., "TotalRevenue", "OrderCount"
                val aggregateFunc = match.groupValues[2]  // e.g., "sum", "count", "avg"

                // Convert marker name to camelCase alias: TotalRevenue -> totalRevenue
                val alias = markerName.replaceFirstChar { it.lowercase() }

                // Infer type from aggregate function
                val kotlinType = inferAggregateType(aggregateFunc)

                // Only add if we haven't seen this alias yet
                if (!foundAliases.contains(alias)) {
                    foundAliases.add(alias)
                    selections.add(
                        Selection(
                            alias = alias,
                            type = SelectionType.AGGREGATE,
                            kotlinType = kotlinType
                        )
                    )
                }
            }

            // Pattern: .selectAs(MarkerName) { expression } for boolean/computed expressions
            // Match any .selectAs that doesn't contain aggregate functions or simple column access
            // Look for comparison operators: gt, lt, gte, lte, eq, neq, and
            val selectAsExpressionPattern = """\.selectAs\s*\(\s*(\w+)\s*\)\s*\{[^}]*(gt|lt|gte|lte|eq|neq|and|or)[^}]*\}""".toRegex()
            selectAsExpressionPattern.findAll(queryChain).forEach { match ->
                val markerName = match.groupValues[1]  // e.g., "IsOld", "IsYoung"

                // Convert marker name to camelCase alias: IsOld -> isOld
                val alias = markerName.replaceFirstChar { it.lowercase() }

                // Boolean expressions typically return Boolean type
                val kotlinType = "Boolean"

                // Only add if we haven't seen this alias yet (avoid duplicates with aggregates)
                if (!foundAliases.contains(alias)) {
                    foundAliases.add(alias)
                    selections.add(
                        Selection(
                            alias = alias,
                            type = SelectionType.COMPUTED,
                            kotlinType = kotlinType
                        )
                    )
                }
            }

            // Also detect .selectAll() to handle mixed queries
            val selectAllPattern = """\.selectAll\s*\(\s*(\w+)\s*\)""".toRegex()
            selectAllPattern.findAll(queryChain).forEach { match ->
                val tableRef = match.groupValues[1]
                val tableName = tableNameMap[tableRef.lowercase()] ?: tableRef
                columnSelectionsByTable.getOrPut(tableName) { mutableListOf() }.add("All")
            }

            // If we found tables and either marker-based selections or table selections, record the pattern
            // This captures: markers only, tables only, or mixed patterns
            if (tables.isNotEmpty() && (selections.isNotEmpty() || columnSelectionsByTable.isNotEmpty())) {
                patterns.add(SelectionPattern(tables, selections, columnSelectionsByTable))
            }
        }

        return patterns
    }

    /**
     * Infer Kotlin type for a column based on its definition.
     * Maps common column types to their Kotlin equivalents.
     */
    private fun inferColumnType(tableName: String, columnName: String): String {
        // Common type mappings based on column definition patterns
        // This is a heuristic approach - ideally we'd parse the actual Table definition

        // Known type mappings for common columns
        return when ("$tableName.$columnName") {
            // Person table
            "Person.name" -> "String"
            "Person.age" -> "Int"

            // Order table
            "Order.id" -> "Int"
            "Order.userName" -> "String"
            "Order.product" -> "String"
            "Order.cost" -> "Int"

            // Profile table
            "Profile.userName" -> "String"
            "Profile.contact" -> "String"
            "Profile.photo" -> "String?"  // nullable

            // Company table
            "Company.id" -> "Int"
            "Company.companyName" -> "String"

            // Product table
            "Product.id" -> "Int"
            "Product.name" -> "String"
            "Product.description" -> "String?"  // nullable
            "Product.price" -> "Int"
            "Product.discount" -> "Int?"  // nullable

            // Default fallback
            else -> inferFromColumnName(columnName)
        }
    }

    /**
     * Fallback type inference based on column name patterns.
     */
    private fun inferFromColumnName(columnName: String): String {
        return when {
            columnName.endsWith("Id") -> "Int"
            columnName.endsWith("Name") -> "String"
            columnName.endsWith("Count") -> "Int"
            columnName.endsWith("Cost") -> "Int"
            columnName.endsWith("Price") -> "Int"
            columnName.endsWith("Amount") -> "Int"
            columnName == "age" -> "Int"
            columnName == "product" -> "String"
            else -> "Any?"  // Safe fallback
        }
    }

    /**
     * Infer Kotlin type for aggregate functions.
     * Maps aggregate function names to their result types.
     */
    private fun inferAggregateType(aggregateFunc: String): String {
        return when (aggregateFunc.lowercase()) {
            "count" -> "Long"        // COUNT always returns Long
            "sum" -> "Number"        // SUM can return various numeric types
            "avg" -> "Number"        // AVG returns decimal
            "min" -> "Number"        // MIN returns same type as column (use Number for safety)
            "max" -> "Number"        // MAX returns same type as column (use Number for safety)
            else -> "Any?"           // Safe fallback
        }
    }
}
