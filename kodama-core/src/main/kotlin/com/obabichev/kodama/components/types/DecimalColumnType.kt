package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Types

/**
 * Column type for PostgreSQL DECIMAL/NUMERIC type.
 * Maps to Kotlin BigDecimal.
 */
object DecimalColumnType : ColumnType<BigDecimal> {
    override fun readValue(resultSet: ResultSet, index: Int): BigDecimal {
        return resultSet.getBigDecimal(index)
    }

    override fun getSqlType(): Int = Types.NUMERIC
}
