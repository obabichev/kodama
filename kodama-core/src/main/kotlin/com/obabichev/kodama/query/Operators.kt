package com.obabichev.kodama.query

import com.obabichev.kodama.components.expression.BinaryOperand
import com.obabichev.kodama.components.expression.ColumnExpression
import com.obabichev.kodama.components.expression.Expression
import com.obabichev.kodama.components.expression.QueryArgumentExpression

/**
 * Infix eq operator for Column - used in typed where clauses
 */
infix fun com.obabichev.kodama.components.Column<*>.eq(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand("=", ColumnExpression(this), argument)
}

/**
 * Infix eq operator for Column to Column - used in typed join conditions
 * Returns a Pair of columns for join condition
 */
infix fun com.obabichev.kodama.components.Column<*>.eq(other: com.obabichev.kodama.components.Column<*>): Pair<com.obabichev.kodama.components.Column<*>, com.obabichev.kodama.components.Column<*>> {
    return this to other
}
