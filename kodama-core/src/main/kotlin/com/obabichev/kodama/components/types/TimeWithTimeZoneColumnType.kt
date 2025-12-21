package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types
import java.time.OffsetTime

/**
 * Column type for PostgreSQL TIME WITH TIME ZONE type.
 * Maps to Kotlin/Java OffsetTime.
 *
 * Example:
 * ```kotlin
 * object BusinessHours : Table("business_hours") {
 *     val openingTime = timeWithTimeZone("opening_time")
 *     val closingTime = timeWithTimeZone("closing_time")
 * }
 * ```
 */
object TimeWithTimeZoneColumnType : ColumnType<OffsetTime> {
    override fun readValue(resultSet: ResultSet, index: Int): OffsetTime {
        // PostgreSQL returns TIME WITH TIME ZONE as OffsetTime
        val value = resultSet.getObject(index, OffsetTime::class.java)
        return value ?: throw IllegalStateException("Unexpected null value for non-nullable TIME WITH TIME ZONE column")
    }

    override fun setValue(preparedStatement: java.sql.PreparedStatement, index: Int, value: Any?) {
        if (value == null) {
            preparedStatement.setNull(index, Types.OTHER)
        } else if (value is OffsetTime) {
            // PostgreSQL JDBC driver can handle OffsetTime directly
            preparedStatement.setObject(index, value)
        } else {
            throw IllegalArgumentException("Expected OffsetTime but got ${value::class.qualifiedName}")
        }
    }

    override fun getSqlType(): Int = Types.TIME_WITH_TIMEZONE
}
