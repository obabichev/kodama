package com.obabichev.kodama.compiler.generator.builders

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a query builder class specifically for subquery operations.
 *
 * Subquery builders are similar to regular query builders but are created
 * when using the inline subquery API with fromAliased { } syntax.
 *
 * Example output for ExpensiveOrders subquery:
 * ```
 * class AfterFromQueryBuilder_ExpensiveOrders<SelectionType, AC : AggCount>(
 *     internal val state: com.obabichev.kodama.query.QueryState
 * ) {
 *     // Subquery-specific methods will be added via other generators
 * }
 * ```
 *
 * Generic parameters:
 * - `SelectionType`: Tracks selection state (NoColumnsSelected, AllColumnsSelected, specific markers)
 * - `AC : AggCount`: Tracks aggregate count
 *
 * Usage:
 * ```
 * fromAliased(ExpensiveOrders) {
 *     from(Order)
 *         .selectAs(UserName) { order.userName }
 *         .where { order.cost gt 500 }
 * }
 * // Returns AfterFromQueryBuilder_ExpensiveOrders
 * ```
 */
class SubqueryBuilderClassGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("class ${subqueryInfo.builderClassName}<SelectionType, AC : AggCount>(")
        appendLine("    internal val state: com.obabichev.kodama.query.QueryState")
        appendLine(") {")
        appendLine("    // Subquery building methods will be added via extension functions")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.QueryState",
            "com.obabichev.kodama.query.AggCount"
        )
    }
}
