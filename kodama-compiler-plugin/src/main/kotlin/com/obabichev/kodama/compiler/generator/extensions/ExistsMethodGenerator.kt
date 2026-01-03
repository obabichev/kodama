package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the exists { } method for checking subquery existence in WHERE contexts.
 *
 * The exists() method creates an EXISTS(subquery) condition that evaluates to true
 * if the subquery returns at least one row.
 *
 * Example output for Person+Order combination:
 * ```
 * inline fun WhereContext_Person_Order.exists(
 *     crossinline subquery: () -> Query
 * ): BooleanExpression {
 *     val query = subquery()
 *     return BooleanExpression.Exists(query)
 * }
 * ```
 *
 * Usage in queries:
 * ```
 * from(Person)
 *     .selectAll(Person)
 *     .where {
 *         exists {
 *             from(Order)
 *                 .select { order.userName }
 *                 .where { order.userName eq person.name }
 *                 .build()
 *         }
 *     }
 * // SQL: WHERE EXISTS (SELECT order.user_name FROM order WHERE order.user_name = person.name)
 * ```
 *
 * The subquery lambda should return a fully built Query object.
 * The outer query's tables are accessible via the WhereContext.
 */
class ExistsMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val contextClassName = "WhereContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Create EXISTS(subquery) condition.")
        appendLine(" */")
        appendLine("inline fun $contextClassName.exists(")
        appendLine("    crossinline subquery: () -> Query")
        appendLine("): Expression {")
        appendLine("    val query = subquery()")
        appendLine("    return ExistsExpression(query)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.components.expression.Expression",
            "com.obabichev.kodama.components.expression.ExistsExpression",
            "com.obabichev.kodama.query.Query"
        )
    }
}
