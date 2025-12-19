package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

/**
 * Column type for PostgreSQL SMALLINT type.
 * Maps to Kotlin Short.
 */
object ShortColumnType : ColumnType<Short> {
    override fun readValue(resultSet: ResultSet, index: Int): Short {
        return resultSet.getShort(index)
    }

    override fun getSqlType(): Int = Types.SMALLINT
}
