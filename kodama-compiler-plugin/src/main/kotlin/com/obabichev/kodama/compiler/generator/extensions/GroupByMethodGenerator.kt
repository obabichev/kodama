package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .groupBy { column } extension method for grouping query results.
 *
 * The groupBy() method adds a GROUP BY clause to the query. Each invocation adds
 * one column to group by. Multiple columns require multiple .groupBy{} calls.
 *
 * Example output for Person+Order combination:
 * ```
 * inline fun <PersonSel, OrderSel, AC : AggCount>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.groupBy(
 *     crossinline selector: GroupByContext_Person_Order.() -> Column<*>
 * ): AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC> {
 *     val context = GroupByContext_Person_Order(state)
 *     val column = context.selector()
 *     state._groupBy.add(column)
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * Type state:
 * - All type parameters are preserved
 * - Returns the same builder type for chainable calls
 *
 * The GroupByContext provides typed accessors for all tables, allowing
 * type-safe column selection for grouping.
 *
 * Usage:
 * ```
 * from(Order)
 *     .selectAs(TotalRevenue) { sum(order.cost) }
 *     .groupBy { order.userName }  // Group by user
 * ```
 *
 * When using GROUP BY:
 * - Non-aggregated selected columns should appear in GROUP BY
 * - Aggregated expressions (sum, count, etc.) don't need GROUP BY
 */
class GroupByMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters
        val selectionParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val allParams = "$selectionParams, AC : AggCount, JP : JoinPattern"

        val contextClassName = "GroupByContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Add GROUP BY clause for aggregating results.")
        appendLine(" */")
        appendLine("inline fun <$allParams>")
        appendLine("${combination.builderClassName}<$selectionParams, AC, JP>.groupBy(")
        appendLine("    crossinline selector: $contextClassName.() -> Column<*>")
        appendLine("): ${combination.builderClassName}<$selectionParams, AC, JP> {")
        appendLine("    val context = $contextClassName(state)")
        appendLine("    val column = context.selector()")
        appendLine("    state._groupBy.add(column)")
        appendLine("    return ${combination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount",
            "com.obabichev.kodama.query.JoinPattern",
            "com.obabichev.kodama.schema.Column"
        )
    }
}
