package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

/**
 * Column type for PostgreSQL BIGINT type.
 * Maps to Kotlin Long.
 */
object LongColumnType : ColumnType<Long> {
    override fun readValue(resultSet: ResultSet, index: Int): Long {
        return resultSet.getLong(index)
    }

    override fun getSqlType(): Int = Types.BIGINT
}
