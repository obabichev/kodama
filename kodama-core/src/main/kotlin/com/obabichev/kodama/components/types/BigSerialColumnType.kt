package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

/**
 * PostgreSQL BIGSERIAL column type (auto-incrementing BIGINT).
 *
 * BIGSERIAL is a PostgreSQL-specific type that creates an auto-incrementing big integer column.
 * It is equivalent to: BIGINT NOT NULL DEFAULT nextval('sequence_name')
 *
 * Use this instead of SERIAL when you need larger ID values (up to 9,223,372,036,854,775,807).
 *
 * Columns with this type should use GenerationStrategy.AlwaysGenerated and will be
 * excluded from INSERT statements.
 *
 * SQL Type: BIGSERIAL (internally BIGINT with sequence)
 * Kotlin Type: Long
 */
object BigSerialColumnType : ColumnType<Long> {
    override fun readValue(resultSet: ResultSet, index: Int): Long {
        return resultSet.getLong(index)
    }

    override fun getSqlType(): Int = Types.BIGINT
}
