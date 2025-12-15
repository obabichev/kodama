package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.Join
import com.obabichev.kodama.components.JoinType
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.components.TypedColumn
import com.obabichev.kodama.components.expression.Expression
import com.obabichev.kodama.schema.Table

/**
 * Marker for selection types
 */
sealed interface SelectionMarker {
    val columns: List<Column<*>>
    val table: Table?
    val isTableAll: Boolean
}

/**
 * Marker for selecting all columns from a table
 */
data class TableAllSelection(
    override val table: Table,
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
}

/**
 * Query state accessible to generated code
 */
class QueryState {
    var _from: Relation? = null
    val _joins: MutableList<Join> = mutableListOf()
    val _selectedColumns: MutableList<Column<*>> = mutableListOf()
    val _tableAllSelections: MutableSet<Table> = mutableSetOf()
    var whereExpression: Expression? = null
    val _orderBy: MutableList<OrderByClause> = mutableListOf()
    val relations = RelationsContainer()

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
    }

    /**
     * Check if a table had .all() selected
     */
    fun isTableAllSelected(table: Table): Boolean = table in _tableAllSelections
}

/**
 * Initial query builder - only allows from()
 * Note: from() methods are provided as extensions in test/generated code
 *
 * Generic parameter Sel tracks the selection state at type level:
 * - NoSelection initially (nothing selected yet)
 * - Updated with each select() call to encode what's been selected
 */
class InitialQueryBuilder<Sel> {
    val state = QueryState()
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
        if (state._selectedColumns.isEmpty()) {
            throw IllegalStateException("SELECT clause is required. Call select() at least once.")
        }
        val from = state._from ?: throw IllegalStateException("FROM clause is required.")
        return Query(state._selectedColumns.toList(), from, state._joins.toList(), state.whereExpression, state._orderBy.toList(), state.relations)
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

/**
 * Entry point for type-safe queries
 * Starts with NoSelection (no columns selected yet)
 */
fun query(): InitialQueryBuilder<NoSelection> = InitialQueryBuilder()
