package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the scalarSubquery { } method for embedding subqueries in SELECT clauses.
 *
 * A scalar subquery is a subquery that returns exactly one column and at most one row.
 * It can be used as an expression in SELECT, WHERE, or other contexts.
 *
 * Example output for Person+Order combination:
 * ```
 * inline fun <T> SelectContext_Person_Order.scalarSubquery(
 *     crossinline subquery: () -> Query
 * ): Expression<T> {
 *     val query = subquery()
 *     return Expression.ScalarSubquery(query)
 * }
 * ```
 *
 * Usage in queries:
 * ```
 * from(Person)
 *     .select { person.name }
 *     .select {
 *         scalarSubquery<Int> {
 *             from(Order)
 *                 .selectAs(OrderCount) { count(order.id) }
 *                 .where { order.userName eq person.name }
 *                 .build()
 *         }
 *     }
 * // SQL: SELECT person.name, (SELECT COUNT(order.id) FROM order WHERE order.user_name = person.name)
 * ```
 *
 * Requirements:
 * - Subquery must select exactly ONE column
 * - Subquery should return at most ONE row (or use an aggregate like COUNT)
 * - Type parameter T specifies the result type
 *
 * Common use cases:
 * - Correlated subqueries for per-row calculations
 * - Embedding aggregates without GROUP BY
 * - Conditional value lookups
 */
class ScalarSubqueryMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val contextClassName = "SelectContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Create a scalar subquery expression.")
        appendLine(" */")
        appendLine("inline fun $contextClassName.scalarSubquery(")
        appendLine("    crossinline subquery: () -> Query")
        appendLine("): Expression {")
        appendLine("    val query = subquery()")
        appendLine("    return ScalarSubqueryExpression(query)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.components.expression.Expression",
            "com.obabichev.kodama.components.expression.ScalarSubqueryExpression",
            "com.obabichev.kodama.query.Query"
        )
    }
}
