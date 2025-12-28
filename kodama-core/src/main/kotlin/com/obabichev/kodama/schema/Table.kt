package com.obabichev.kodama.schema

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.components.types.BigSerialColumnType
import com.obabichev.kodama.components.types.BooleanColumnType
import com.obabichev.kodama.components.types.DateColumnType
import com.obabichev.kodama.components.types.DecimalColumnType
import com.obabichev.kodama.components.types.DoubleColumnType
import com.obabichev.kodama.components.types.FloatColumnType
import com.obabichev.kodama.components.types.IntColumnType
import com.obabichev.kodama.components.types.IntervalColumnType
import com.obabichev.kodama.components.types.LongColumnType
import com.obabichev.kodama.components.types.SerialColumnType
import com.obabichev.kodama.components.types.ShortColumnType
import com.obabichev.kodama.components.types.SmallSerialColumnType
import com.obabichev.kodama.components.types.StringColumnType
import com.obabichev.kodama.components.types.TimeColumnType
import com.obabichev.kodama.components.types.TimeWithTimeZoneColumnType
import com.obabichev.kodama.components.types.TimestampColumnType
import com.obabichev.kodama.components.types.TimestampWithTimeZoneColumnType
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime

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
 * Tables implement TableSource, allowing them to be used uniformly with subqueries
 * in FROM and JOIN clauses.
 */
abstract class Table(tableName: String) : TableSource {
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
     * The alias used for this table in SQL queries.
     * Defaults to lowercase table name.
     */
    override val alias: String get() = tableName.lowercase()

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
     * Define a SERIAL column (PostgreSQL auto-incrementing INTEGER).
     *
     * SERIAL is a PostgreSQL-specific type that creates an auto-incrementing integer column.
     * Values are generated automatically by the database and should NOT be provided during INSERT.
     *
     * Columns defined with serial() are excluded from generated insert() method parameters.
     *
     * SQL Type: SERIAL (internally INTEGER with sequence)
     * Kotlin Type: Int
     * Range: -2,147,483,648 to 2,147,483,647
     *
     * Example:
     * ```kotlin
     * object Users : Table("users") {
     *     val id = serial("id").primaryKey()
     *     val name = varchar("name", 255)
     * }
     * ```
     */
    protected fun serial(columnName: String): Column<Int> {
        val column = Column(
            columnName,
            relation,
            SerialColumnType,
            nullable = false,
            generationStrategy = GenerationStrategy.AlwaysGenerated
        )
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a BIGSERIAL column (PostgreSQL auto-incrementing BIGINT).
     *
     * BIGSERIAL is a PostgreSQL-specific type that creates an auto-incrementing big integer column.
     * Use this instead of SERIAL when you need larger ID ranges.
     *
     * Columns defined with bigserial() are excluded from generated insert() method parameters.
     *
     * SQL Type: BIGSERIAL (internally BIGINT with sequence)
     * Kotlin Type: Long
     * Range: -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807
     *
     * Example:
     * ```kotlin
     * object Orders : Table("orders") {
     *     val id = bigserial("id").primaryKey()
     *     val total = decimal("total", 10, 2)
     * }
     * ```
     */
    protected fun bigserial(columnName: String): Column<Long> {
        val column = Column(
            columnName,
            relation,
            BigSerialColumnType,
            nullable = false,
            generationStrategy = GenerationStrategy.AlwaysGenerated
        )
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a SMALLSERIAL column (PostgreSQL auto-incrementing SMALLINT).
     *
     * SMALLSERIAL is a PostgreSQL-specific type that creates an auto-incrementing small integer column.
     * Use this for small ID ranges when storage space is a concern.
     *
     * Columns defined with smallserial() are excluded from generated insert() method parameters.
     *
     * SQL Type: SMALLSERIAL (internally SMALLINT with sequence)
     * Kotlin Type: Short
     * Range: -32,768 to 32,767
     *
     * Example:
     * ```kotlin
     * object Tags : Table("tags") {
     *     val id = smallserial("id").primaryKey()
     *     val name = varchar("name", 50)
     * }
     * ```
     */
    protected fun smallserial(columnName: String): Column<Short> {
        val column = Column(
            columnName,
            relation,
            SmallSerialColumnType,
            nullable = false,
            generationStrategy = GenerationStrategy.AlwaysGenerated
        )
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
     * Define a date column (non-nullable by default)
     * Maps to Kotlin/Java LocalDate
     *
     * Example:
     * ```kotlin
     * val birthDate = date("birth_date")
     * val startDate = date("start_date").nullable()
     * ```
     */
    protected fun date(columnName: String): Column<LocalDate> {
        val column = Column(columnName, relation, DateColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a time column (non-nullable by default)
     * Maps to Kotlin/Java LocalTime (time without time zone)
     *
     * Example:
     * ```kotlin
     * val openingTime = time("opening_time")
     * val closingTime = time("closing_time")
     * ```
     */
    protected fun time(columnName: String): Column<LocalTime> {
        val column = Column(columnName, relation, TimeColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a timestamp column (non-nullable by default)
     * Maps to Kotlin/Java LocalDateTime (timestamp without time zone)
     *
     * Example:
     * ```kotlin
     * val createdAt = timestamp("created_at")
     * val updatedAt = timestamp("updated_at").nullable()
     * ```
     */
    protected fun timestamp(columnName: String): Column<LocalDateTime> {
        val column = Column(columnName, relation, TimestampColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Define a timestamp with time zone column (non-nullable by default)
     * Maps to Kotlin/Java OffsetDateTime
     *
     * Example:
     * ```kotlin
     * val eventTime = timestampWithTimeZone("event_time")
     * val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
     * ```
     */
    protected fun timestampWithTimeZone(columnName: String): Column<OffsetDateTime> {
        val column = Column(columnName, relation, TimestampWithTimeZoneColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Alias for timestampWithTimeZone() for convenience
     */
    protected fun timestamptz(columnName: String): Column<OffsetDateTime> {
        return timestampWithTimeZone(columnName)
    }

    /**
     * Define a time with time zone column (non-nullable by default)
     * Maps to Kotlin/Java OffsetTime
     *
     * Example:
     * ```kotlin
     * val scheduledTime = timeWithTimeZone("scheduled_time")
     * ```
     */
    protected fun timeWithTimeZone(columnName: String): Column<OffsetTime> {
        val column = Column(columnName, relation, TimeWithTimeZoneColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Alias for timeWithTimeZone() for convenience
     */
    protected fun timetz(columnName: String): Column<OffsetTime> {
        return timeWithTimeZone(columnName)
    }

    /**
     * Define an interval column (non-nullable by default)
     * Maps to Kotlin/Java Duration
     *
     * Note: Works best for intervals in terms of days, hours, minutes, seconds.
     * PostgreSQL intervals with year/month components may not round-trip perfectly.
     *
     * Example:
     * ```kotlin
     * val duration = interval("duration")
     * val estimatedTime = interval("estimated_time").nullable()
     * ```
     */
    protected fun interval(columnName: String): Column<Duration> {
        val column = Column(columnName, relation, IntervalColumnType, nullable = false)
        relation.registerColumn(column)
        return column
    }

    /**
     * Get all columns from this table (implements TableSource)
     */
    override fun allColumns(): List<Column<*>> = relation.columns
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
