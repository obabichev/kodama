package com.obabichev.kodama.schema

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.components.types.BooleanColumnType
import com.obabichev.kodama.components.types.DecimalColumnType
import com.obabichev.kodama.components.types.DoubleColumnType
import com.obabichev.kodama.components.types.FloatColumnType
import com.obabichev.kodama.components.types.IntColumnType
import com.obabichev.kodama.components.types.LongColumnType
import com.obabichev.kodama.components.types.ShortColumnType
import com.obabichev.kodama.components.types.StringColumnType
import java.math.BigDecimal

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
     * Define a boolean column (non-nullable by default)
     */
    protected fun boolean(columnName: String): Column<Boolean> {
        val column = Column(columnName, relation, BooleanColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a smallint column (non-nullable by default)
     * Maps to Kotlin Short (-32,768 to 32,767)
     */
    protected fun smallint(columnName: String): Column<Short> {
        val column = Column(columnName, relation, ShortColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a bigint column (non-nullable by default)
     * Maps to Kotlin Long (-9,223,372,036,854,775,808 to 9,223,372,036,854,775,807)
     */
    protected fun bigint(columnName: String): Column<Long> {
        val column = Column(columnName, relation, LongColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a decimal/numeric column (non-nullable by default)
     * Maps to Kotlin BigDecimal (arbitrary precision)
     * @param precision Total number of digits
     * @param scale Number of digits after decimal point
     */
    protected fun decimal(columnName: String, precision: Int, scale: Int): Column<BigDecimal> {
        val column = Column(columnName, relation, DecimalColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Alias for decimal()
     */
    protected fun numeric(columnName: String, precision: Int, scale: Int): Column<BigDecimal> {
        return decimal(columnName, precision, scale)
    }

    /**
     * Define a real column (non-nullable by default)
     * Maps to Kotlin Float (single precision floating point)
     */
    protected fun real(columnName: String): Column<Float> {
        val column = Column(columnName, relation, FloatColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a double precision column (non-nullable by default)
     * Maps to Kotlin Double (double precision floating point)
     */
    protected fun doublePrecision(columnName: String): Column<Double> {
        val column = Column(columnName, relation, DoubleColumnType, nullable = false)
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
 * Entity table with ORM support.
 * Use this for tables that have corresponding domain entities.
 *
 * The type parameter E specifies the entity class this table maps to.
 *
 * Example:
 * ```kotlin
 * data class User(val id: Int, val name: String)
 *
 * object Users : EntityTable<User>("users") {
 *     val id = integer("id").primaryKey()
 *     val name = varchar("name", 255)
 * }
 * ```
 *
 * For tables without entities (views, staging tables, etc.), use regular Table:
 * ```kotlin
 * object AuditLog : Table("audit_log") {
 *     val timestamp = timestamp("timestamp")
 *     val message = text("message")
 * }
 * ```
 */
abstract class EntityTable<E : Any>(tableName: String) : Table(tableName) {

    /**
     * The entity type this table maps to.
     * Extracted from the generic type parameter.
     */
    @Suppress("UNCHECKED_CAST")
    val entityType: kotlin.reflect.KClass<E> by lazy {
        // Extract entity type from generic parameter
        this::class.supertypes
            .first { it.classifier == EntityTable::class }
            .arguments.first().type?.classifier as kotlin.reflect.KClass<E>
    }

    /**
     * Registry of relationships defined on this table.
     * Maps relationship name to relationship metadata.
     */
    private val _relationships = mutableMapOf<String, com.obabichev.kodama.entity.Relationship>()

    /**
     * All relationships defined on this table (read-only view).
     */
    val relationships: Map<String, com.obabichev.kodama.entity.Relationship>
        get() = _relationships.toMap()

    /**
     * Register a one-to-many relationship.
     * Internal - called by oneToMany() DSL function.
     */
    internal fun registerOneToMany(relationship: com.obabichev.kodama.entity.OneToManyRelationship<*, *>) {
        _relationships[relationship.name] = relationship
    }

    /**
     * Register a many-to-one relationship.
     * Internal - called by manyToOne() DSL function.
     */
    internal fun registerManyToOne(relationship: com.obabichev.kodama.entity.ManyToOneRelationship<*, *>) {
        _relationships[relationship.name] = relationship
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
