package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate

/**
 * Column type for PostgreSQL DATE type.
 * Maps to Kotlin/Java LocalDate.
 *
 * Example:
 * ```kotlin
 * object Orders : Table("orders") {
 *     val orderDate = date("order_date")
 * }
 * ```
 */
object DateColumnType : ColumnType<LocalDate> {
    override fun readValue(resultSet: ResultSet, index: Int): LocalDate {
        val sqlDate = resultSet.getDate(index)
        return sqlDate?.toLocalDate() ?: throw IllegalStateException("Unexpected null value for non-nullable DATE column")
    }

    override fun getSqlType(): Int = Types.DATE
}
