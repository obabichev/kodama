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
 * Infix gt (greater than) operator for Column with value
 */
infix fun com.obabichev.kodama.components.Column<*>.gt(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand(">", ColumnExpression(this), argument)
}

/**
 * Infix gt (greater than) operator for Column with Expression (e.g., scalar subquery)
 */
infix fun com.obabichev.kodama.components.Column<*>.gt(expression: com.obabichev.kodama.components.expression.Expression): Expression {
    return BinaryOperand(">", ColumnExpression(this), expression)
}

/**
 * Infix gte (greater than or equal) operator for Column with value
 */
infix fun com.obabichev.kodama.components.Column<*>.gte(value: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val argument = QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand(">=", ColumnExpression(this), argument)
}

/**
 * Infix gte (greater than or equal) operator for Column with Expression
 */
infix fun com.obabichev.kodama.components.Column<*>.gte(expression: com.obabichev.kodama.components.expression.Expression): Expression {
    return BinaryOperand(">=", ColumnExpression(this), expression)
}

/**
 * Infix eq operator for Column to Column - used in both join conditions and WHERE clauses
 * Returns an Expression that can be used in both JOIN ON and WHERE conditions
 */
infix fun com.obabichev.kodama.components.Column<*>.eq(other: com.obabichev.kodama.components.Column<*>): Expression {
    return BinaryOperand("=", ColumnExpression(this), ColumnExpression(other))
}

/**
 * Infix eq operator for Column to Expression - allows comparing columns with scalar subqueries, etc.
 * Returns an Expression that can be used in WHERE conditions
 */
infix fun com.obabichev.kodama.components.Column<*>.eq(expression: com.obabichev.kodama.components.expression.Expression): Expression {
    return BinaryOperand("=", ColumnExpression(this), expression)
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
 * Infix gt operator for TypedColumn with value
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.gt(value: Any?): Expression {
    return this.column.gt(value)
}

/**
 * Infix gt operator for TypedColumn with Expression
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.gt(expression: com.obabichev.kodama.components.expression.Expression): Expression {
    return this.column.gt(expression)
}

/**
 * Infix gte operator for TypedColumn with value
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.gte(value: Any?): Expression {
    return this.column.gte(value)
}

/**
 * Infix gte operator for TypedColumn with Expression
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.gte(expression: com.obabichev.kodama.components.expression.Expression): Expression {
    return this.column.gte(expression)
}

/**
 * Infix eq operator for TypedColumn to TypedColumn - used in both join conditions and WHERE clauses
 * Unwraps the underlying Columns and returns an Expression
 */
infix fun <T1, TM1, CM1, T2, TM2, CM2> com.obabichev.kodama.components.TypedColumn<T1, TM1, CM1>.eq(
    other: com.obabichev.kodama.components.TypedColumn<T2, TM2, CM2>
): Expression {
    return this.column.eq(other.column)
}

/**
 * Infix eq operator for TypedColumn to Expression - allows comparing columns with scalar subqueries, etc.
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.eq(expression: com.obabichev.kodama.components.expression.Expression): Expression {
    return this.column.eq(expression)
}

// ========== Range Operators ==========

/**
 * BETWEEN operator for Column - checks if value is within range (inclusive)
 */
fun com.obabichev.kodama.components.Column<*>.between(lower: Any?, upper: Any?): Expression {
    @Suppress("UNCHECKED_CAST")
    val lowerArg = QueryArgumentExpression(lower, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    @Suppress("UNCHECKED_CAST")
    val upperArg = QueryArgumentExpression(upper, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return com.obabichev.kodama.components.expression.BetweenExpression(ColumnExpression(this), lowerArg, upperArg)
}

/**
 * BETWEEN operator for TypedColumn
 */
fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.between(lower: Any?, upper: Any?): Expression {
    return this.column.between(lower, upper)
}

// ========== NULL Check Operators ==========

/**
 * IS NULL operator for Column
 */
fun com.obabichev.kodama.components.Column<*>.isNull(): Expression {
    return com.obabichev.kodama.components.expression.IsNullExpression(ColumnExpression(this))
}

/**
 * IS NOT NULL operator for Column
 */
fun com.obabichev.kodama.components.Column<*>.isNotNull(): Expression {
    return com.obabichev.kodama.components.expression.IsNotNullExpression(ColumnExpression(this))
}

/**
 * IS NULL operator for TypedColumn
 */
fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.isNull(): Expression {
    return this.column.isNull()
}

/**
 * IS NOT NULL operator for TypedColumn
 */
fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.isNotNull(): Expression {
    return this.column.isNotNull()
}

// ========== String Pattern Matching Operators ==========

/**
 * LIKE operator for pattern matching (case-sensitive)
 */
infix fun com.obabichev.kodama.components.Column<*>.like(pattern: String): Expression {
    @Suppress("UNCHECKED_CAST")
    val patternArg = QueryArgumentExpression(pattern, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand("LIKE", ColumnExpression(this), patternArg)
}

/**
 * ILIKE operator for pattern matching (case-insensitive, PostgreSQL-specific)
 */
infix fun com.obabichev.kodama.components.Column<*>.ilike(pattern: String): Expression {
    @Suppress("UNCHECKED_CAST")
    val patternArg = QueryArgumentExpression(pattern, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    return BinaryOperand("ILIKE", ColumnExpression(this), patternArg)
}

/**
 * Convenience method for startsWith pattern matching
 */
infix fun com.obabichev.kodama.components.Column<*>.startsWith(prefix: String): Expression {
    return this.like("$prefix%")
}

/**
 * Convenience method for endsWith pattern matching
 */
infix fun com.obabichev.kodama.components.Column<*>.endsWith(suffix: String): Expression {
    return this.like("%$suffix")
}

/**
 * Convenience method for contains pattern matching
 */
infix fun com.obabichev.kodama.components.Column<*>.contains(substring: String): Expression {
    return this.like("%$substring%")
}

/**
 * LIKE operator for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.like(pattern: String): Expression {
    return this.column.like(pattern)
}

/**
 * ILIKE operator for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.ilike(pattern: String): Expression {
    return this.column.ilike(pattern)
}

/**
 * startsWith for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.startsWith(prefix: String): Expression {
    return this.column.startsWith(prefix)
}

/**
 * endsWith for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.endsWith(suffix: String): Expression {
    return this.column.endsWith(suffix)
}

/**
 * contains for TypedColumn
 */
infix fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.contains(substring: String): Expression {
    return this.column.contains(substring)
}

// ========== IN Operators ==========

/**
 * IN operator for Column - checks if column value is in the list
 */
fun <T> com.obabichev.kodama.components.Column<T>.inList(values: List<T>): Expression {
    @Suppress("UNCHECKED_CAST")
    val valueExpressions = values.map { value ->
        QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    }
    return com.obabichev.kodama.components.expression.InListExpression(ColumnExpression(this), valueExpressions)
}

/**
 * NOT IN operator for Column - checks if column value is not in the list
 */
fun <T> com.obabichev.kodama.components.Column<T>.notInList(values: List<T>): Expression {
    @Suppress("UNCHECKED_CAST")
    val valueExpressions = values.map { value ->
        QueryArgumentExpression(value, this.type as com.obabichev.kodama.components.ColumnType<Any?>)
    }
    return com.obabichev.kodama.components.expression.NotInListExpression(ColumnExpression(this), valueExpressions)
}

/**
 * IN operator for Column with subquery - checks if column value is in the subquery results
 */
fun <T> com.obabichev.kodama.components.Column<T>.inQuery(subquery: com.obabichev.kodama.query.Query): Expression {
    return com.obabichev.kodama.components.expression.InQueryExpression(ColumnExpression(this), subquery)
}

/**
 * NOT IN operator for Column with subquery - checks if column value is not in the subquery results
 */
fun <T> com.obabichev.kodama.components.Column<T>.notInQuery(subquery: com.obabichev.kodama.query.Query): Expression {
    return com.obabichev.kodama.components.expression.NotInQueryExpression(ColumnExpression(this), subquery)
}

/**
 * IN operator for TypedColumn
 */
fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.inList(values: List<Any?>): Expression {
    @Suppress("UNCHECKED_CAST")
    return this.column.inList(values as List<T>)
}

/**
 * NOT IN operator for TypedColumn
 */
fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.notInList(values: List<Any?>): Expression {
    @Suppress("UNCHECKED_CAST")
    return this.column.notInList(values as List<T>)
}

/**
 * IN operator for TypedColumn with subquery
 */
fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.inQuery(subquery: com.obabichev.kodama.query.Query): Expression {
    return this.column.inQuery(subquery)
}

/**
 * NOT IN operator for TypedColumn with subquery
 */
fun <T, TM, CM> com.obabichev.kodama.components.TypedColumn<T, TM, CM>.notInQuery(subquery: com.obabichev.kodama.query.Query): Expression {
    return this.column.notInQuery(subquery)
}
