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
 *
 * Note: Table properties are accessed through generated contexts in queries:
 * ```
 * query()
 *     .from(Person)
 *     .select { person.name }  // Access via context, not Person.name
 *     .where { person.age eq 25 }
 * ```
 */
abstract class Table(tableName: String) {
    val relation: Relation = Relation(tableName)

    init {
        // Register this table globally
        Tables.register(this)
    }

    /**
     * Returns the fully qualified table name (used for generated code)
     */
    val tableName: String = tableName

    /**
     * Define an integer column (non-nullable by default)
     */
    protected fun integer(columnName: String): Column<Int> {
        val column = Column(columnName, relation, IntColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a varchar column (non-nullable by default)
     */
    protected fun varchar(columnName: String, length: Int): Column<String> {
        val column = Column(columnName, relation, StringColumnType, nullable = false)
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

/**
 * Extension to mark a column as nullable.
 * Changes the column type from Column<T> to Column<T?> to reflect nullability in the type system.
 */
fun <T : Any> Column<T>.nullable(): Column<T?> {
    @Suppress("UNCHECKED_CAST")
    val newColumn = Column(this.name, this.relation, this.type as ColumnType<T?>, nullable = true)
    // Re-register the column with the relation to replace the old one
    this.relation.replaceColumn(this, newColumn)
    return newColumn
}
