package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

/**
 * PostgreSQL SERIAL column type (auto-incrementing INTEGER).
 *
 * SERIAL is a PostgreSQL-specific type that creates an auto-incrementing integer column.
 * It is equivalent to: INTEGER NOT NULL DEFAULT nextval('sequence_name')
 *
 * Columns with this type should use GenerationStrategy.AlwaysGenerated and will be
 * excluded from INSERT statements.
 *
 * SQL Type: SERIAL (internally INTEGER with sequence)
 * Kotlin Type: Int
 */
object SerialColumnType : ColumnType<Int> {
    override fun readValue(resultSet: ResultSet, index: Int): Int {
        return resultSet.getInt(index)
    }

    override fun getSqlType(): Int = Types.INTEGER
}
