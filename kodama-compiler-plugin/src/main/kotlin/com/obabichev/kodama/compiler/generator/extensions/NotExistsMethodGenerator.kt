package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the notExists { } method for checking subquery non-existence in WHERE contexts.
 *
 * The notExists() method creates a NOT EXISTS(subquery) condition that evaluates to true
 * if the subquery returns no rows.
 *
 * Example output for Person+Order combination:
 * ```
 * inline fun WhereContext_Person_Order.notExists(
 *     crossinline subquery: () -> Query
 * ): BooleanExpression {
 *     val query = subquery()
 *     return BooleanExpression.NotExists(query)
 * }
 * ```
 *
 * Usage in queries:
 * ```
 * from(Person)
 *     .selectAll(Person)
 *     .where {
 *         notExists {
 *             from(Order)
 *                 .select { order.id }
 *                 .where { order.userName eq person.name }
 *                 .build()
 *         }
 *     }
 * // SQL: WHERE NOT EXISTS (SELECT order.id FROM order WHERE order.user_name = person.name)
 * // Finds persons with no orders
 * ```
 *
 * Common use cases:
 * - Finding records without related data (anti-join)
 * - Checking for absence of conditions
 * - Complement of EXISTS checks
 */
class NotExistsMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val contextClassName = "WhereContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Create NOT EXISTS(subquery) condition.")
        appendLine(" */")
        appendLine("inline fun $contextClassName.notExists(")
        appendLine("    crossinline subquery: () -> Query")
        appendLine("): Expression {")
        appendLine("    val query = subquery()")
        appendLine("    return NotExistsExpression(query)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.components.expression.Expression",
            "com.obabichev.kodama.components.expression.NotExistsExpression",
            "com.obabichev.kodama.query.Query"
        )
    }
}
