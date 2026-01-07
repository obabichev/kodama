package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the fromAliased() function for starting a query from a pre-defined subquery.
 *
 * This function allows using a marker interface to reference a subquery table that
 * will be built inline or retrieved from a registry.
 *
 * Example output for ExpensiveOrders subquery:
 * ```
 * inline fun <reified T> fromAliased(
 *     marker: T
 * ): AfterFromQueryBuilder_ExpensiveOrders<NoColumnsSelected, NoAggregates>
 *     where T : ExpensiveOrders {
 *     val subqueryTable = SubqueryRegistry.getOrCreate<T>()
 *     val state = QueryState()
 *     state._from = state.relations.relation(subqueryTable)
 *     return AfterFromQueryBuilder_ExpensiveOrders(state)
 * }
 * ```
 *
 * Usage:
 * ```
 * // Define marker interface
 * interface ExpensiveOrders : SubqueryType
 *
 * // Use in query
 * fromAliased(ExpensiveOrders)
 *     .selectAll { expensiveOrders.userName }
 *     .where { expensiveOrders.cost gt 1000 }
 * ```
 *
 * The marker interface must:
 * - Extend SubqueryType
 * - Be registered in SubqueryRegistry with its definition
 * - Have a corresponding SubqueryTable implementation
 */
class FromAliasedMethodGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val builderClassName = subqueryInfo.builderClassName

        appendLine("/**")
        appendLine(" * Start a query from ${subqueryInfo.name} subquery.")
        appendLine(" */")
        appendLine("inline fun <reified T> fromAliased(")
        appendLine("    marker: T")
        appendLine("): $builderClassName<NoColumnsSelected, NoAggregates, JoinPattern_NONE>")
        appendLine("    where T : ${subqueryInfo.name} {")
        appendLine("    val subqueryTable = SubqueryRegistry.getOrCreate<T>()")
        appendLine("    val state = QueryState()")
        appendLine("    state._from = state.relations.relation(subqueryTable)")
        appendLine("    state._subqueryTables[subqueryTable.alias] = subqueryTable")
        appendLine("    return $builderClassName(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.QueryState",
            "com.obabichev.kodama.query.NoAggregates",
            "com.obabichev.kodama.query.NoColumnsSelected",
            "com.obabichev.kodama.query.SubqueryRegistry"
        )
    }
}
