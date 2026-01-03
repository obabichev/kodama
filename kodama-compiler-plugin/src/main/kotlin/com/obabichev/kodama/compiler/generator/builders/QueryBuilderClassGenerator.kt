package com.obabichev.kodama.compiler.generator.builders

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the main query builder class for a table combination.
 *
 * The query builder is the central class that provides the fluent API for building queries.
 * It tracks:
 * - Query state (tables, joins, where clauses, etc.)
 * - Type-level selection state (which columns/aggregates are selected)
 * - Type-level aggregate count
 *
 * Example output for Person + Order combination:
 * ```
 * class AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC : AggCount>(
 *     internal val state: com.obabichev.kodama.query.QueryState
 * ) {
 *     // Methods like select, where, join, etc. will be added by other generators
 * }
 * ```
 *
 * Generic parameters:
 * - `PersonSel`: Tracks which Person columns are selected (NoColumnsSelected, AllColumnsSelected, etc.)
 * - `OrderSel`: Tracks which Order columns are selected
 * - `AC : AggCount`: Tracks how many aggregates are selected (NoAggregates, Has1Aggregate, etc.)
 *
 * This enables compile-time verification:
 * ```
 * val builder: AfterFromQueryBuilder_Person_Order<AllColumnsSelected, NoColumnsSelected, NoAggregates>
 * // Compiler knows: Person columns selected, Order not selected, no aggregates
 * ```
 */
class QueryBuilderClassGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build generic type parameters for selection state
        val selectionTypeParams = combination.tables.joinToString(", ") { 
            "${it.capitalizedName}Sel" 
        }
        val allTypeParams = "$selectionTypeParams, AC : AggCount"

        appendLine("class ${combination.builderClassName}<$allTypeParams>(")
        appendLine("    val state: com.obabichev.kodama.query.QueryState")
        appendLine(") {")
        appendLine("    // Query building methods will be added via extension functions")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.QueryState",
            "com.obabichev.kodama.query.AggCount"
        )
    }
}
