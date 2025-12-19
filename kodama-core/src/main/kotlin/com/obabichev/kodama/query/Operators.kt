package com.obabichev.kodama.query

import com.obabichev.kodama.components.expression.BinaryOperand
import com.obabichev.kodama.components.expression.ColumnExpression
import com.obabichev.kodama.components.expression.Expression
import com.obabichev.kodama.components.expression.QueryArgumentExpression
import com.obabichev.kodama.components.expression.and
import com.obabichev.kodama.components.expression.or
import com.obabichev.kodama.components.expression.not

// ========== Comparison Operators ==========

/**
 * Infix eq operator for Column - used in typed where clauses
 */
infix fun com.obabichev.kodama.components.Column<*>.eq(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand("=", ColumnExpression(this), argument)
}

/**
 * Infix neq (not equal) operator for Column
 */
infix fun com.obabichev.kodama.components.Column<*>.neq(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand("<>", ColumnExpression(this), argument)
}

/**
 * Infix lt (less than) operator for Column
 */
infix fun com.obabichev.kodama.components.Column<*>.lt(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand("<", ColumnExpression(this), argument)
}

/**
 * Infix lte (less than or equal) operator for Column
 */
infix fun com.obabichev.kodama.components.Column<*>.lte(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand("<=", ColumnExpression(this), argument)
}

/**
 * Infix gt (greater than) operator for Column
 */
infix fun com.obabichev.kodama.components.Column<*>.gt(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand(">", ColumnExpression(this), argument)
}

/**
 * Infix gte (greater than or equal) operator for Column
 */
infix fun com.obabichev.kodama.components.Column<*>.gte(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand(">=", ColumnExpression(this), argument)
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
 * Infix neq operator for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.neq(value: Any?): Expression {
    return this.column.neq(value)
}

/**
 * Infix lt operator for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.lt(value: Any?): Expression {
    return this.column.lt(value)
}

/**
 * Infix lte operator for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.lte(value: Any?): Expression {
    return this.column.lte(value)
}

/**
 * Infix gt operator for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.gt(value: Any?): Expression {
    return this.column.gt(value)
}

/**
 * Infix gte operator for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.gte(value: Any?): Expression {
    return this.column.gte(value)
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
