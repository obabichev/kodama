package com.obabichev.kodama.tests.infrastructure

import com.obabichev.kodama.components.Column
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
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.*

/**
 * Registry of all tables used in tests.
 * Provides ordered list for creation/deletion respecting dependencies.
 */
object TableRegistry {
    /**
     * All tables in dependency order (create order).
     * Tables without foreign keys first, dependent tables last.
     */
    val allTables: List<Table> = listOf(
        Company,
        Person,
        Order,
        Profile,
        Product,
        Settings,
        Numerics,
        TradingStrategy,
        MarketData,
        Events,
        SerialTest,
        IdentityTest,
        BigSerialTest,
        SmallSerialTest
    )

    /**
     * All tables in reverse dependency order (drop order).
     */
    val allTablesReversed: List<Table> = allTables.reversed()
}

/**
 * Generate CREATE TABLE SQL from Table metadata.
 */
fun Table.toCreateTableSQL(): String {
    val columns = relation.columns
    if (columns.isEmpty()) {
        throw IllegalStateException("Table $tableName has no columns defined")
    }

    val columnDefinitions = columns.joinToString(",\n    ") { column ->
        val sqlType = column.toSQLType()
        val nullable = if (column.nullable) "" else " NOT NULL"
        val identity = column.toIdentityModifier()
        val pk = if (column.isPrimaryKey()) " PRIMARY KEY" else ""
        "${column.sqlName()}$sqlType$identity$nullable$pk"
    }

    // Handle "order" as a reserved keyword
    val quotedTableName = if (tableName == "order") "\"$tableName\"" else tableName

    return """
        CREATE TABLE IF NOT EXISTS $quotedTableName (
            $columnDefinitions
        )
    """.trimIndent()
}

/**
 * Convert column to SQL type string.
 */
private fun Column<*>.toSQLType(): String {
    return when (type) {
        is SmallSerialColumnType -> " SMALLSERIAL"
        is SerialColumnType -> " SERIAL"
        is BigSerialColumnType -> " BIGSERIAL"
        is ShortColumnType -> " SMALLINT"
        is IntColumnType -> " INTEGER"
        is LongColumnType -> " BIGINT"
        is DecimalColumnType -> " NUMERIC"
        is FloatColumnType -> " REAL"
        is DoubleColumnType -> " DOUBLE PRECISION"
        is StringColumnType -> " TEXT"
        is BooleanColumnType -> " BOOLEAN"
        is DateColumnType -> " DATE"
        is TimeColumnType -> " TIME"
        is TimestampColumnType -> " TIMESTAMP"
        is TimestampWithTimeZoneColumnType -> " TIMESTAMPTZ"
        is TimeWithTimeZoneColumnType -> " TIMETZ"
        is IntervalColumnType -> " INTERVAL"
        else -> throw IllegalArgumentException("Unsupported column type: ${type::class.simpleName}")
    }
}

/**
 * Get IDENTITY modifier for columns with GENERATED ALWAYS AS IDENTITY.
 * Only returns the modifier for non-SERIAL columns with AlwaysGenerated strategy.
 */
private fun Column<*>.toIdentityModifier(): String {
    // SERIAL types already include auto-increment, don't add IDENTITY
    if (type is SerialColumnType || type is BigSerialColumnType || type is SmallSerialColumnType) {
        return ""
    }

    // For other types, check if it's marked as AlwaysGenerated (via .identity())
    return if (generationStrategy == com.obabichev.kodama.schema.GenerationStrategy.AlwaysGenerated) {
        " GENERATED ALWAYS AS IDENTITY"
    } else {
        ""
    }
}

/**
 * Get SQL-safe column name (handle reserved keywords).
 */
private fun Column<*>.sqlName(): String {
    // PostgreSQL reserved keywords that need quoting
    val reservedKeywords = setOf("order", "user", "group", "table")
    return if (name in reservedKeywords) "\"$name\"" else name
}

/**
 * Check if column is a primary key.
 * TODO: Once primary key metadata is stored in Column, use that instead.
 */
private fun Column<*>.isPrimaryKey(): Boolean {
    // Heuristic: columns named "id" or ending with table name are likely PKs
    // This will be replaced once Column stores PK metadata
    return name == "id" || name == relation.name || name == "${relation.name}_id"
}
