package com.obabichev.kodama.query

import com.obabichev.kodama.components.expression.Expression
import kotlin.reflect.KClass

/**
 * Wrapper for expressions with type-safe alias markers.
 * This class carries both the expression and its marker type information at compile-time,
 * enabling fully type-safe aliased selections.
 *
 * Usage:
 * ```kotlin
 * interface TotalRevenue
 *
 * query()
 *     .from(Order)
 *     .select { sum(order.cost).alias<TotalRevenue>() }
 *     .execute(transaction)
 *     .first()
 *     .totalRevenue  // Type-safe accessor!
 * ```
 *
 * @param T The marker interface type (e.g., TotalRevenue)
 * @property expression The underlying expression (aggregate, column, etc.)
 * @property markerClass The marker interface class for runtime introspection
 * @property aliasName The SQL alias name derived from the marker interface
 */
class AliasedExpression<T : Any>(
    val expression: Expression,
    val markerClass: KClass<T>,
    val aliasName: String
) : Expression {

    override fun toSql(): String = expression.toSql()

    override fun arguments(): List<QueryArgument<*>> = expression.arguments()
}
