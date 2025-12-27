package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

/**
 * PostgreSQL SMALLSERIAL column type (auto-incrementing SMALLINT).
 *
 * SMALLSERIAL is a PostgreSQL-specific type that creates an auto-incrementing small integer column.
 * It is equivalent to: SMALLINT NOT NULL DEFAULT nextval('sequence_name')
 *
 * Use this for small ID ranges (up to 32,767) when storage space is a concern.
 *
 * Columns with this type should use GenerationStrategy.AlwaysGenerated and will be
 * excluded from INSERT statements.
 *
 * SQL Type: SMALLSERIAL (internally SMALLINT with sequence)
 * Kotlin Type: Short
 */
object SmallSerialColumnType : ColumnType<Short> {
    override fun readValue(resultSet: ResultSet, index: Int): Short {
        return resultSet.getShort(index)
    }

    override fun getSqlType(): Int = Types.SMALLINT
}
