package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types
import java.time.OffsetDateTime

/**
 * Column type for PostgreSQL TIMESTAMP WITH TIME ZONE type.
 * Maps to Kotlin/Java OffsetDateTime.
 *
 * Note: PostgreSQL stores all timestamps with time zone in UTC internally
 * and converts them to the session's time zone on retrieval. OffsetDateTime
 * preserves the offset information.
 *
 * Example:
 * ```kotlin
 * object AuditLog : Table("audit_log") {
 *     val eventTime = timestampWithTimeZone("event_time")
 * }
 * ```
 */
object TimestampWithTimeZoneColumnType : ColumnType<OffsetDateTime> {
    override fun readValue(resultSet: ResultSet, index: Int): OffsetDateTime {
        val sqlTimestamp = resultSet.getTimestamp(index)
        return sqlTimestamp?.toInstant()?.atOffset(java.time.ZoneOffset.UTC)
            ?: throw IllegalStateException("Unexpected null value for non-nullable TIMESTAMP WITH TIME ZONE column")
    }

    override fun setValue(preparedStatement: java.sql.PreparedStatement, index: Int, value: Any?) {
        if (value == null) {
            preparedStatement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE)
        } else if (value is OffsetDateTime) {
            // Convert OffsetDateTime to java.sql.Timestamp for PostgreSQL
            val timestamp = java.sql.Timestamp.from(value.toInstant())
            preparedStatement.setTimestamp(index, timestamp)
        } else {
            throw IllegalArgumentException("Expected OffsetDateTime but got ${value::class.qualifiedName}")
        }
    }

    override fun getSqlType(): Int = Types.TIMESTAMP_WITH_TIMEZONE
}
