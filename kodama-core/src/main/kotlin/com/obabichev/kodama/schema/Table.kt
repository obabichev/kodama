package com.obabichev.kodama.schema

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.components.types.IntColumnType
import com.obabichev.kodama.components.types.StringColumnType

/**
 * Base class for table definitions.
 * Tables are defined as objects that extend this class.
 *
 * Example:
 * ```
 * object Person : Table("person") {
 *     val name = varchar("name", 255)
 *     val age = integer("age")
 * }
 * ```
 */
abstract class Table(tableName: String) {
    val relation: Relation = Relation(tableName)

    init {
        // Register this table globally
        Tables.register(this)
    }

    /**
     * Define an integer column
     */
    protected fun integer(columnName: String): Column<Int> {
        val column = Column(columnName, relation, IntColumnType)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a varchar column
     */
    protected fun varchar(columnName: String, length: Int): Column<String> {
        val column = Column(columnName, relation, StringColumnType)
        relation.registerColumn(column)
        return column
    }

    /**
     * Get all columns from this table
     */
    fun allColumns(): List<Column<*>> = relation.columns
}

/**
 * Global registry of all tables
 */
object Tables {
    private val tables = mutableListOf<Table>()

    fun register(table: Table) {
        if (!tables.contains(table)) {
            tables.add(table)
        }
    }

    fun all(): List<Table> = tables.toList()

    fun findByRelation(relation: Relation): Table? {
        return tables.find { it.relation == relation }
    }
}

/**
 * Extension to mark a column as primary key
 */
fun <T> Column<T>.primaryKey(): Column<T> {
    // TODO: Store primary key metadata
    return this
}
