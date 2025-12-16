package com.obabichev.kodama.query

/**
 * Core abstraction for anything that can be selected in a query with an alias.
 * This includes aggregates, constants, subqueries, window functions, computed columns, etc.
 */
interface Selectable {
    val alias: String
    val type: SelectableType

    /**
     * Get the value from the result set at the given position.
     * Position is typically: selectedColumns.size + selectables.indexOf(this) + 1
     */
    fun getValue(resultSet: java.sql.ResultSet, position: Int): Any?
}

/**
 * Type of selectable - used for grouping and validation
 */
enum class SelectableType {
    AGGREGATE,
    CONSTANT,
    SUBQUERY,
    WINDOW_FUNCTION,
    METADATA,
    COMPUTED
}

/**
 * Aggregate function selection (COUNT, SUM, AVG, etc.)
 */
class AggregateSelectable(
    override val alias: String,
    val function: AggregateFunction<*>
) : Selectable {
    override val type: SelectableType = SelectableType.AGGREGATE

    override fun getValue(resultSet: java.sql.ResultSet, position: Int): Any? {
        return resultSet.getObject(position)
    }
}

/**
 * Constant value selection
 */
class ConstantSelectable(
    override val alias: String,
    val value: Any
) : Selectable {
    override val type: SelectableType = SelectableType.CONSTANT

    override fun getValue(resultSet: java.sql.ResultSet, position: Int): Any? {
        // Constants are evaluated locally, not from DB
        return value
    }
}

/**
 * Subquery selection
 */
class SubquerySelectable(
    override val alias: String,
    val query: Query
) : Selectable {
    override val type: SelectableType = SelectableType.SUBQUERY

    override fun getValue(resultSet: java.sql.ResultSet, position: Int): Any? {
        return resultSet.getObject(position)
    }
}
