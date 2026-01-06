package com.obabichev.kodama.components.expression

import com.obabichev.kodama.query.QueryArgument

class BinaryOperand(val operation: String, val left: Expression, val right: Expression) : Expression {
    override fun toSql(): String {
        return "${left.toSql()} $operation ${right.toSql()}"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return left.arguments() + right.arguments()
    }
}

/**
 * Unary operator for NOT expression
 */
class UnaryOperand(val operation: String, val operand: Expression) : Expression {
    override fun toSql(): String {
        return "$operation (${operand.toSql()})"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return operand.arguments()
    }
}

// Boolean operators for combining expressions

infix fun Expression.and(other: Expression): Expression = BinaryOperand("AND", this, other)

infix fun Expression.or(other: Expression): Expression = BinaryOperand("OR", this, other)

fun not(expression: Expression): Expression = UnaryOperand("NOT", expression)

// Comparison operators

infix fun Expression.eq(other: Expression): Expression = BinaryOperand("=", this, other)

// Allow Expression to be compared with Column (e.g., scalar subquery eq column)
infix fun Expression.eq(column: com.obabichev.kodama.components.Column<*>): Expression =
    BinaryOperand("=", this, ColumnExpression(column))

// Allow Expression to be compared with TypedColumn
infix fun <T, TM, CM> Expression.eq(column: com.obabichev.kodama.components.TypedColumn<T, TM, CM>): Expression =
    BinaryOperand("=", this, ColumnExpression(column.column))

infix fun Expression.neq(other: Expression): Expression = BinaryOperand("<>", this, other)

// Range operators

/**
 * BETWEEN expression for range checks
 */
class BetweenExpression(val column: Expression, val lower: Expression, val upper: Expression) : Expression {
    override fun toSql(): String {
        return "${column.toSql()} BETWEEN ${lower.toSql()} AND ${upper.toSql()}"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return column.arguments() + lower.arguments() + upper.arguments()
    }
}

// NULL check operators

/**
 * IS NULL expression
 */
class IsNullExpression(val column: Expression) : Expression {
    override fun toSql(): String {
        return "${column.toSql()} IS NULL"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return column.arguments()
    }
}

/**
 * IS NOT NULL expression
 */
class IsNotNullExpression(val column: Expression) : Expression {
    override fun toSql(): String {
        return "${column.toSql()} IS NOT NULL"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return column.arguments()
    }
}

// IN operators

/**
 * IN expression for list of values
 */
class InListExpression(val column: Expression, val values: List<Expression>) : Expression {
    override fun toSql(): String {
        if (values.isEmpty()) {
            // Empty IN list is always false in SQL
            return "FALSE"
        }
        val placeholders = values.joinToString(", ") { it.toSql() }
        return "${column.toSql()} IN ($placeholders)"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return column.arguments() + values.flatMap { it.arguments() }
    }
}

/**
 * NOT IN expression for list of values
 */
class NotInListExpression(val column: Expression, val values: List<Expression>) : Expression {
    override fun toSql(): String {
        if (values.isEmpty()) {
            // Empty NOT IN list is always true in SQL
            return "TRUE"
        }
        val placeholders = values.joinToString(", ") { it.toSql() }
        return "${column.toSql()} NOT IN ($placeholders)"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return column.arguments() + values.flatMap { it.arguments() }
    }
}

/**
 * IN expression for subquery
 */
class InQueryExpression(val column: Expression, val subquery: com.obabichev.kodama.query.Query) : Expression {
    override fun toSql(): String {
        val subquerySql = subquery.sql()
        return "${column.toSql()} IN ($subquerySql)"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return column.arguments() + subquery.arguments()
    }
}

/**
 * NOT IN expression for subquery
 */
class NotInQueryExpression(val column: Expression, val subquery: com.obabichev.kodama.query.Query) : Expression {
    override fun toSql(): String {
        val subquerySql = subquery.sql()
        return "${column.toSql()} NOT IN ($subquerySql)"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return column.arguments() + subquery.arguments()
    }
}

// Scalar Subquery Support

/**
 * Scalar subquery expression - returns a single value
 * Can be used in WHERE clauses for comparisons or in SELECT clauses
 *
 * Example:
 * ```kotlin
 * where { order.cost gt scalarSubquery { avgCostQuery } }
 * ```
 */
class ScalarSubqueryExpression(
    val subquery: com.obabichev.kodama.query.Query
) : Expression {
    override fun toSql(): String {
        return "(${subquery.sql()})"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return subquery.arguments()
    }
}

/**
 * Helper function to create a scalar subquery expression
 */
fun scalarSubquery(subquery: com.obabichev.kodama.query.Query): Expression {
    return ScalarSubqueryExpression(subquery)
}

// EXISTS Support

/**
 * EXISTS expression - checks if subquery returns any rows
 *
 * Example:
 * ```kotlin
 * where {
 *     exists {
 *         query()
 *             .from(Order)
 *             .where { order.userName eq person.name }
 *             .build()
 *     }
 * }
 * ```
 */
class ExistsExpression(val subquery: com.obabichev.kodama.query.Query) : Expression {
    override fun toSql(): String {
        return "EXISTS (${subquery.sql()})"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return subquery.arguments()
    }
}

/**
 * NOT EXISTS expression - checks if subquery returns no rows
 */
class NotExistsExpression(val subquery: com.obabichev.kodama.query.Query) : Expression {
    override fun toSql(): String {
        return "NOT EXISTS (${subquery.sql()})"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return subquery.arguments()
    }
}

/**
 * Helper function to create an EXISTS expression
 */
fun exists(subquery: com.obabichev.kodama.query.Query): Expression {
    return ExistsExpression(subquery)
}

/**
 * Helper function to create a NOT EXISTS expression
 */
fun notExists(subquery: com.obabichev.kodama.query.Query): Expression {
    return NotExistsExpression(subquery)
}