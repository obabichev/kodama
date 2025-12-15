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

/**
 * Infix eq operator for TypedColumn - used in typed where clauses
 * Unwraps the underlying Column and delegates to Column.eq
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.eq(value: Any?): Expression {
    return this.column.eq(value)
}

/**
 * Infix eq operator for TypedColumn to TypedColumn - used in typed join conditions
 * Unwraps the underlying Columns and returns a Pair for join condition
 */
infix fun <T1, TM1, CM1, T2, TM2, CM2> com.obabichev.kodama.components.TypedColumn<T1, TM1, CM1>.eq(
    other: com.obabichev.kodama.components.TypedColumn<T2, TM2, CM2>
): Pair<com.obabichev.kodama.components.Column<*>, com.obabichev.kodama.components.Column<*>> {
    return this.column to other.column
}
