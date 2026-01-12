package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.expression.Expression
import com.obabichev.kodama.util.toSnakeCase

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

/**
 * Type-safe aliasing using marker interfaces.
 * Usage: sum(order.cost).aliasAs<TotalRevenue>()
 *
 * Returns an AliasedExpression<T> that carries the marker type at compile-time,
 * enabling fully type-safe result accessors.
 *
 * The marker interface name is converted to:
 * - snake_case for SQL: TotalRevenue -> total_revenue
 * - camelCase for accessor: TotalRevenue -> totalRevenue
 *
 * @param T The marker interface type
 * @return AliasedExpression carrying both the aggregate and marker information
 */
inline fun <reified T : Any> AggregateFunction<*>.aliasAs(): AliasedExpression<T> {
    val markerClass = T::class
    val interfaceName = markerClass.simpleName
        ?: throw IllegalArgumentException("Marker must be a named interface")

    // Convert PascalCase to snake_case for SQL alias
    val sqlAlias = interfaceName.replaceFirstChar { it.lowercase() }.toSnakeCase()

    // Set the alias on the aggregate function itself
    this.alias(sqlAlias)

    return AliasedExpression(this, markerClass, sqlAlias)
}

// ============================================================================
// Aggregate Function DSL Helpers
// ============================================================================

/**
 * Create a SUM aggregate function from a TypedColumn.
 * Usage: sum(order.cost)
 */
fun <T : Number, TableMarker, ColumnMarker> sum(typedColumn: com.obabichev.kodama.components.TypedColumn<T, TableMarker, ColumnMarker>): Sum<T> =
    Sum(typedColumn.column)

/**
 * Create a COUNT aggregate function from a TypedColumn.
 * Usage: count(order.id)
 */
fun <TableMarker, ColumnMarker> count(typedColumn: com.obabichev.kodama.components.TypedColumn<*, TableMarker, ColumnMarker>): Count =
    Count(typedColumn.column)

/**
 * Create a COUNT(*) aggregate function.
 * Usage: countAll()
 */
fun countAll(): Count = Count(null)

/**
 * Create an AVG aggregate function from a TypedColumn.
 * Usage: avg(order.cost)
 */
fun <T : Number, TableMarker, ColumnMarker> avg(typedColumn: com.obabichev.kodama.components.TypedColumn<T, TableMarker, ColumnMarker>): Avg<T> =
    Avg(typedColumn.column)

/**
 * Create a MIN aggregate function from a TypedColumn.
 * Usage: min(order.cost)
 */
fun <T : Comparable<T>, TableMarker, ColumnMarker> min(typedColumn: com.obabichev.kodama.components.TypedColumn<T, TableMarker, ColumnMarker>): Min<T> =
    Min(typedColumn.column)

/**
 * Create a MAX aggregate function from a TypedColumn.
 * Usage: max(order.cost)
 */
fun <T : Comparable<T>, TableMarker, ColumnMarker> max(typedColumn: com.obabichev.kodama.components.TypedColumn<T, TableMarker, ColumnMarker>): Max<T> =
    Max(typedColumn.column)
