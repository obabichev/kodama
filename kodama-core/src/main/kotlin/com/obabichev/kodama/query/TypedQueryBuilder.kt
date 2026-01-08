package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.Join
import com.obabichev.kodama.components.JoinType
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.components.TypedColumn
import com.obabichev.kodama.components.expression.Expression
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.schema.TableSource

/**
 * Marker for selection types
 */
sealed interface SelectionMarker {
    val columns: List<Column<*>>
    val table: TableSource?
    val isTableAll: Boolean
    val isAggregate: Boolean
        get() = false
}

/**
 * Marker for selecting all columns from a table or subquery
 */
data class TableAllSelection(
    override val table: TableSource,
    override val columns: List<Column<*>>
) : SelectionMarker {
    override val isTableAll: Boolean = true
}

/**
 * Marker for selecting a specific column
 */
data class ColumnSelection(
    val column: Column<*>
) : SelectionMarker {
    override val columns: List<Column<*>> = listOf(column)
    override val table: Table? = null
    override val isTableAll: Boolean = false
}

/**
 * Marker for selecting an aggregate function
 */
data class AggregateSelection(
    val aggregateFunction: AggregateFunction<*>
) : SelectionMarker {
    override val columns: List<Column<*>> = if (aggregateFunction.column != null) {
        listOf(aggregateFunction.column)
    } else {
        emptyList() // For COUNT(*)
    }
    override val table: Table? = null
    override val isTableAll: Boolean = false
    override val isAggregate: Boolean = true
}

/**
 * Helper to expose all columns from a table
 * Now works directly with Table objects and their column properties
 */
class TableAccessor(
    val table: Table,
    val relationsContainer: RelationsContainer
) {
    /**
     * Get all columns from this table - returns a selection marker
     */
    fun all(): TableAllSelection {
        return TableAllSelection(table, table.allColumns())
    }

    /**
     * Get the relation for this table
     */
    val relation: Relation
        get() = table.relation
}

/**
 * Context for building SELECT clause with type-safe column access
 * Supports unary plus operator to add columns: +person.name
 * Also supports aggregate functions without unary plus: sum(order.cost)
 */
abstract class SelectContext {
    val selections = mutableListOf<SelectionMarker>()

    /**
     * Unary plus operator for TypedColumn - adds column to selection
     */
    operator fun <T, TM, CM> TypedColumn<T, TM, CM>.unaryPlus() {
        selections.add(ColumnSelection(this.column))
    }

    /**
     * Unary plus operator for Column - adds column to selection
     */
    operator fun <T> Column<T>.unaryPlus() {
        selections.add(ColumnSelection(this))
    }

    /**
     * Unary plus operator for TableAllSelection - adds all columns from table
     */
    operator fun TableAllSelection.unaryPlus() {
        selections.add(this)
    }

    /**
     * Add aggregate function directly (no unary plus needed)
     */
    fun <T> AggregateFunction<T>.also(block: AggregateFunction<T>.() -> Unit = {}): AggregateFunction<T> {
        selections.add(AggregateSelection(this))
        return this
    }

    // DSL functions for aggregate functions
    fun <T : Number> sum(column: Column<T>): AggregateFunction<T> {
        val agg = Sum(column)
        selections.add(AggregateSelection(agg))
        return agg
    }

    fun <T : Number, TM, CM> sum(typedColumn: TypedColumn<T, TM, CM>): AggregateFunction<T> {
        return sum(typedColumn.column)
    }

    fun count(column: Column<*>): AggregateFunction<Long> {
        val agg = Count(column)
        selections.add(AggregateSelection(agg))
        return agg
    }

    fun <TM, CM> count(typedColumn: TypedColumn<*, TM, CM>): AggregateFunction<Long> {
        return count(typedColumn.column)
    }

    fun countAll(): AggregateFunction<Long> {
        val agg = Count(null)
        selections.add(AggregateSelection(agg))
        return agg
    }

    fun <T : Number> avg(column: Column<T>): AggregateFunction<Double> {
        val agg = Avg(column)
        selections.add(AggregateSelection(agg))
        return agg
    }

    fun <T : Number, TM, CM> avg(typedColumn: TypedColumn<T, TM, CM>): AggregateFunction<Double> {
        return avg(typedColumn.column)
    }

    fun <T : Comparable<T>> min(column: Column<T>): AggregateFunction<T> {
        val agg = Min(column)
        selections.add(AggregateSelection(agg))
        return agg
    }

    fun <T : Comparable<T>, TM, CM> min(typedColumn: TypedColumn<T, TM, CM>): AggregateFunction<T> {
        return min(typedColumn.column)
    }

    fun <T : Comparable<T>> max(column: Column<T>): AggregateFunction<T> {
        val agg = Max(column)
        selections.add(AggregateSelection(agg))
        return agg
    }

    fun <T : Comparable<T>, TM, CM> max(typedColumn: TypedColumn<T, TM, CM>): AggregateFunction<T> {
        return max(typedColumn.column)
    }
}

/**
 * Query state accessible to generated code
 */
class QueryState {
    var _from: Relation? = null
    val _joins: MutableList<Join> = mutableListOf()
    val _selectedColumns: MutableList<Column<*>> = mutableListOf()
    val _tableAllSelections: MutableSet<TableSource> = mutableSetOf()
    val _aggregateSelections: MutableList<AggregateFunction<*>> = mutableListOf()
    val _selectables: MutableList<Selectable> = mutableListOf()  // NEW: Unified selection tracking
    val _groupBy: MutableList<Column<*>> = mutableListOf()  // GROUP BY columns
    var whereExpression: Expression? = null
    val _orderBy: MutableList<OrderByClause> = mutableListOf()
    var _limit: Int? = null
    var _offset: Int? = null
    val relations = RelationsContainer()
    val _subqueryTables: MutableMap<String, Table> = mutableMapOf()  // Track subquery tables by SQL name
    val _markerSelections: MutableMap<kotlin.reflect.KClass<*>, Expression> = mutableMapOf()  // Track marker selections

    /**
     * Apply a selection marker to the query state
     */
    fun applySelection(marker: SelectionMarker) {
        _selectedColumns.addAll(marker.columns)
        if (marker.isTableAll) {
            val table = marker.table
            if (table != null) {
                _tableAllSelections.add(table)
            }
        }
        if (marker.isAggregate && marker is AggregateSelection) {
            _aggregateSelections.add(marker.aggregateFunction)
        }
    }

    /**
     * Add a marker-based selection (e.g., .selectAs(TotalRevenue) { sum(order.cost) })
     */
    fun addMarkerSelection(markerClass: kotlin.reflect.KClass<*>, expression: Expression) {
        _markerSelections[markerClass] = expression

        // Set the SQL alias based on the marker name (PascalCase -> camelCase)
        // ColumnSelectable will convert camelCase to snake_case for SQL
        val markerName = markerClass.simpleName ?: throw IllegalStateException("Marker must have a name")
        val camelCaseAlias = markerName.replaceFirstChar { it.lowercase() }

        // Add to appropriate selection list based on expression type
        when (expression) {
            is AggregateFunction<*> -> {
                // For aggregates, use snake_case alias directly
                val sqlAlias = camelCaseAlias
                    .replace(Regex("([a-z])([A-Z])"), "$1_$2")
                    .lowercase()
                expression.alias(sqlAlias)
                _aggregateSelections.add(expression)
            }
            is TypedColumn<*, *, *> -> {
                // Add as ColumnSelectable with proper alias
                _selectables.add(ColumnSelectable(camelCaseAlias, expression.column))
            }
            is Column<*> -> {
                // Add as ColumnSelectable with proper alias
                _selectables.add(ColumnSelectable(camelCaseAlias, expression))
            }
            else -> {
                // For general expressions (comparisons, arithmetic, etc.)
                // Convert to snake_case for SQL alias consistency
                val sqlAlias = camelCaseAlias
                    .replace(Regex("([a-z])([A-Z])"), "$1_$2")
                    .lowercase()
                _selectables.add(ExpressionSelectable(sqlAlias, expression))
            }
        }
    }

    /**
     * Get the value for a selected marker from the result set.
     * Used by generated marker accessor extensions.
     */
    fun getMarkerValue(markerClass: kotlin.reflect.KClass<*>, resultSet: java.sql.ResultSet): Any? {
        // Find the expression that was selected with this marker
        val expression = _markerSelections[markerClass]

        // Get the marker name and convert to camelCase accessor name
        val markerName = markerClass.simpleName ?: throw IllegalStateException("Marker must have a name")
        val accessorName = markerName.replaceFirstChar { it.lowercase() }

        // Read the value from the result set
        // The SQL alias was set as snake_case version of the marker name
        val sqlAlias = markerName
            .replaceFirstChar { it.lowercase() }
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .lowercase()

        // Get the value from result set
        return resultSet.getObject(sqlAlias)
    }

    /**
     * Check if a table had .all() selected
     */
    fun isTableAllSelected(table: TableSource): Boolean = table in _tableAllSelections
}

/**
 * Base interface for all query builders after from() is called
 *
 * Generic parameter Sel encodes the selection state at compile time.
 * This allows us to track which columns/tables have been selected.
 */
interface AfterFromQueryBuilderBase<Sel> {
    val state: QueryState

    /**
     * WHERE clause - provided as extension in generated code
     * Note: where() methods are provided as extensions in test/generated code
     */

    /**
     * Build the query - requires calling select first through generated extension
     */
    fun build(): Query {
        if (state._selectedColumns.isEmpty() && state._aggregateSelections.isEmpty()) {
            throw IllegalStateException("SELECT clause is required. Call select() at least once.")
        }
        val from = state._from ?: throw IllegalStateException("FROM clause is required.")

        return Query(
            state._selectedColumns.toList(),
            from,
            state._joins.toList(),
            state.whereExpression,
            state._orderBy.toList(),
            state.relations,
            state._aggregateSelections.toList(),
            state._groupBy.toList(),
            state._selectables.toList(),
            state._limit,
            state._offset
        )
    }
}

/**
 * Builder after from() is called - allows join() or build()
 * This is the generic fallback that will be used when no specific typed builder matches
 * Note: join() methods are provided as extensions in test/generated code
 *
 * Sel parameter tracks selection state at compile time
 */
class AfterFromQueryBuilder<Sel>(
    override val state: QueryState
) : AfterFromQueryBuilderBase<Sel>

