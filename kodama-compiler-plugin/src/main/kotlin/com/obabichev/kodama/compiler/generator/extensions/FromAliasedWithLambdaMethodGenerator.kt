package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the fromAliased() function overload that accepts a lambda for inline subquery definition.
 *
 * This allows defining subqueries inline instead of pre-registering them.
 *
 * Example output for UserTotals subquery:
 * ```
 * inline fun <reified T> fromAliased(
 *     marker: T,
 *     queryBuilder: () -> Query
 * ): AfterFromQueryBuilder_UserTotals<NoColumnsSelected, NoAggregates>
 *     where T : UserTotals {
 *     val query = queryBuilder()
 *     val subqueryTable = SubqueryRegistry.createSubquery(T::class, query) as SubqueryTable_UserTotals
 *     val state = QueryState()
 *     state._from = state.relations.relation(subqueryTable)
 *     return AfterFromQueryBuilder_UserTotals(state)
 * }
 * ```
 *
 * Usage:
 * ```
 * fromAliased(UserTotals) {
 *     from(Order)
 *         .selectAs(OrderUserName) { order.userName }
 *         .selectAs(TotalCost) { sum(order.cost) }
 *         .groupBy { order.userName }
 *         .build()
 * }
 *     .selectAll(UserTotals)
 * ```
 */
class FromAliasedWithLambdaMethodGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val builderClassName = subqueryInfo.builderClassName
        val subqueryTableClassName = subqueryInfo.subqueryTableClassName

        appendLine("/**")
        appendLine(" * Start a query from ${subqueryInfo.name} subquery with inline definition.")
        appendLine(" */")
        appendLine("inline fun <reified T> fromAliased(")
        appendLine("    marker: T,")
        appendLine("    queryBuilder: () -> Query")
        appendLine("): $builderClassName<NoColumnsSelected, NoAggregates>")
        appendLine("    where T : ${subqueryInfo.name} {")
        appendLine("    val query = queryBuilder()")
        appendLine("    val subqueryTable = SubqueryRegistry.createSubquery(T::class, query) as $subqueryTableClassName")
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
            "com.obabichev.kodama.query.Query"
        )
    }
}
