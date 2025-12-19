package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.expression.Expression

/**
 * Base class for aggregate functions (SUM, COUNT, AVG, MIN, MAX)
 * Implements Expression to allow aggregates to be used in expressions
 */
sealed class AggregateFunction<T>(
    val functionName: String,
    val column: Column<*>?,
    private var _alias: String? = null
) : Expression {
    /**
     * Check if an explicit alias has been set
     */
    val hasExplicitAlias: Boolean
        get() = _alias != null

    /**
     * The accessor name to use in generated code.
     * Uses alias if provided, otherwise generates from function and column name.
     */
    val accessorName: String
        get() = _alias ?: generateDefaultAccessorName()

    private fun generateDefaultAccessorName(): String {
        return if (column != null) {
            "${functionName.lowercase()}_${column.name}"
        } else {
            // For COUNT(*) special case
            "${functionName.lowercase()}_all"
        }
    }

    /**
     * Provide an explicit alias for this aggregate function
     * Mutates this instance and returns it for chaining
     */
    infix fun alias(aliasName: String): AggregateFunction<T> {
        _alias = aliasName
        return this
    }

    /**
     * Generate SQL for this aggregate function
     * E.g., "SUM(column_name)" or "COUNT(*)"
     */
    override fun toSql(): String {
        return if (column != null) {
            "$functionName(${column.relation.name}.${column.name})"
        } else {
            "$functionName(*)"
        }
    }

    /**
     * Aggregate functions don't have query arguments (parameters)
     * The column reference is part of the SQL, not a parameter
     */
    override fun arguments(): List<QueryArgument<*>> = emptyList()
}

/**
 * SUM aggregate function
 */
class Sum<T : Number>(
    column: Column<T>,
    alias: String? = null
) : AggregateFunction<T>("SUM", column, alias)

/**
 * COUNT aggregate function
 * Column can be null for COUNT(*)
 */
class Count(
    column: Column<*>? = null,
    alias: String? = null
) : AggregateFunction<Long>("COUNT", column, alias)

/**
 * AVG aggregate function
 */
class Avg<T : Number>(
    column: Column<T>,
    alias: String? = null
) : AggregateFunction<Double>("AVG", column, alias)

/**
 * MIN aggregate function
 */
class Min<T : Comparable<T>>(
    column: Column<T>,
    alias: String? = null
) : AggregateFunction<T>("MIN", column, alias)

/**
 * MAX aggregate function
 */
class Max<T : Comparable<T>>(
    column: Column<T>,
    alias: String? = null
) : AggregateFunction<T>("MAX", column, alias)
