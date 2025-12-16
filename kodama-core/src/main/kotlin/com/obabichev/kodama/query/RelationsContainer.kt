package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.components.TypedColumn
import com.obabichev.kodama.schema.Table

/**
 * Container for managing table relations.
 * Now works with Table objects instead of reflection-based KClass.
 */
class RelationsContainer {
    private val relationsCache = mutableMapOf<Table, Relation>()

    /**
     * Get the relation for a table object
     */
    fun relation(table: Table): Relation {
        return relationsCache.getOrPut(table) {
            table.relation
        }
    }

    /**
     * Get a column from a table
     */
    fun <T> column(column: Column<T>): Column<T> {
        return column
    }

    /**
     * Get a column from a TypedColumn (unwraps the underlying Column)
     */
    fun <T, TM, CM> column(typedColumn: TypedColumn<T, TM, CM>): Column<T> {
        return typedColumn.column
    }

    /**
     * Get all columns from a table
     */
    fun allColumns(table: Table): List<Column<*>> {
        return table.allColumns()
    }
}
