package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalTime

/**
 * Column type for PostgreSQL TIME type (without time zone).
 * Maps to Kotlin/Java LocalTime.
 *
 * Example:
 * ```kotlin
 * object Schedule : Table("schedule") {
 *     val startTime = time("start_time")
 * }
 * ```
 */
object TimeColumnType : ColumnType<LocalTime> {
    override fun readValue(resultSet: ResultSet, index: Int): LocalTime {
        val sqlTime = resultSet.getTime(index)
        return sqlTime?.toLocalTime() ?: throw IllegalStateException("Unexpected null value for non-nullable TIME column")
    }

    override fun getSqlType(): Int = Types.TIME
}
