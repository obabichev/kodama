package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.SubqueryInfo

/**
 * Utility to determine the target file path for generated code.
 *
 * This centralizes the file organization logic, making it easy to understand
 * where each type of generated code will end up.
 *
 * File Organization Rules:
 * - Infrastructure: `_infrastructure/` - Shared markers, phantom types, registries
 * - Single tables: `single_table/` - One file per regular table
 * - Combinations: `combinations/` - One file per multi-table combination
 * - Subqueries: `subqueries/` - One file per subquery
 * - Synthetic: `synthetic/` - Auto-generated Table+Subquery combinations
 */
object TargetFileResolver {

    /**
     * Determine the target file for a query combination.
     *
     * Logic:
     * - Synthetic combinations → synthetic/{TableNames}Query.kt
     * - Single-table regular → single_table/{TableName}Query.kt
     * - Single-table subquery → subqueries/{SubqueryName}Query.kt
     * - Multi-table → combinations/{TableNames}Query.kt
     */
    fun forCombination(combination: QueryCombinationInfo): String {
        return when {
            combination.isSynthetic ->
                "synthetic/${combination.tableNamesSeparated}Query.kt"

            combination.tables.size == 1 && !combination.baseTable.isSubquery ->
                "single_table/${combination.baseTable.capitalizedName}Query.kt"

            combination.tables.size == 1 && combination.baseTable.isSubquery ->
                "subqueries/${combination.baseTable.capitalizedName}Query.kt"

            else ->
                "combinations/${combination.tableNamesSeparated}Query.kt"
        }
    }

    /**
     * Determine the target file for a subquery.
     */
    fun forSubquery(subquery: SubqueryInfo): String {
        return "subqueries/${subquery.name}Query.kt"
    }

    /**
     * Target file for marker interfaces (column, table markers).
     */
    fun forMarkers(): String = "_infrastructure/Markers.kt"

    /**
     * Target file for selection sets and result classes.
     */
    fun forSelectionSets(): String = "_infrastructure/SelectionSets.kt"

    /**
     * Target file for join pattern phantom types.
     */
    fun forJoinPatterns(): String = "_infrastructure/JoinPatterns.kt"

    /**
     * Target file for subquery infrastructure (registry, fromAliased methods).
     */
    fun forSubqueryInfrastructure(): String = "_infrastructure/SubqueryInfrastructure.kt"
}
