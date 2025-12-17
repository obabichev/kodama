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

infix fun Expression.neq(other: Expression): Expression = BinaryOperand("<>", this, other)