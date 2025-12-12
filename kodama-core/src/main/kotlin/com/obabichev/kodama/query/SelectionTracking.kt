package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.Table

/**
 * Tracks which columns were selected for a table
 */
sealed class TableSelection {
    /**
     * All columns from the table were selected (via .all())
     */
    data object AllColumns : TableSelection()

    /**
     * Specific columns from the table were selected
     */
    data class SpecificColumns(val columns: Set<Column<*>>) : TableSelection()
}

/**
 * Tracks selections for all tables in a query
 */
class SelectionTracker {
    private val selections = mutableMapOf<Table, TableSelection>()

    fun selectAll(table: Table) {
        selections[table] = TableSelection.AllColumns
    }

    fun selectColumn(column: Column<*>) {
        val table = com.obabichev.kodama.schema.Tables.findByRelation(column.relation)
            ?: throw IllegalStateException("Column ${column.name} doesn't have an associated table")

        val current = selections[table]
        when (current) {
            is TableSelection.AllColumns -> {
                // Already have all columns, nothing to do
            }
            is TableSelection.SpecificColumns -> {
                selections[table] = TableSelection.SpecificColumns(current.columns + column)
            }
            null -> {
                selections[table] = TableSelection.SpecificColumns(setOf(column))
            }
        }
    }

    fun getSelection(table: Table): TableSelection? = selections[table]

    fun hasAllColumns(table: Table): Boolean {
        return selections[table] is TableSelection.AllColumns
    }

    fun hasColumn(column: Column<*>): Boolean {
        val table = com.obabichev.kodama.schema.Tables.findByRelation(column.relation) ?: return false
        return when (val selection = selections[table]) {
            is TableSelection.AllColumns -> true
            is TableSelection.SpecificColumns -> column in selection.columns
            null -> false
        }
    }

    fun getSelectedColumns(table: Table): Set<Column<*>>? {
        return when (val selection = selections[table]) {
            is TableSelection.AllColumns -> table.allColumns().toSet()
            is TableSelection.SpecificColumns -> selection.columns
            null -> null
        }
    }
}
