package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types
import java.time.Duration

/**
 * Column type for PostgreSQL INTERVAL type.
 * Maps to Kotlin/Java Duration.
 *
 * Note: PostgreSQL INTERVAL can represent years and months, but java.time.Duration
 * cannot. This implementation works best for intervals in terms of days, hours,
 * minutes, and seconds. For intervals with year/month components, consider using
 * a different approach or storing as text.
 *
 * Example:
 * ```kotlin
 * object Tasks : Table("tasks") {
 *     val estimatedDuration = interval("estimated_duration")
 *     val actualDuration = interval("actual_duration").nullable()
 * }
 * ```
 */
object IntervalColumnType : ColumnType<Duration> {
    override fun readValue(resultSet: ResultSet, index: Int): Duration {
        // PostgreSQL INTERVAL is returned as PGInterval object or String
        val value = resultSet.getObject(index)

        return when (value) {
            null -> throw IllegalStateException("Unexpected null value for non-nullable INTERVAL column")
            is org.postgresql.util.PGInterval -> {
                // Convert PGInterval to Duration
                val seconds = value.seconds.toLong()
                val minutes = value.minutes.toLong()
                val hours = value.hours.toLong()
                val days = value.days.toLong()

                Duration.ofDays(days)
                    .plusHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds)
            }
            is String -> {
                // Parse ISO-8601 duration format as fallback
                Duration.parse(value)
            }
            else -> throw IllegalArgumentException("Unexpected type for INTERVAL: ${value::class.qualifiedName}")
        }
    }

    override fun setValue(preparedStatement: java.sql.PreparedStatement, index: Int, value: Any?) {
        if (value == null) {
            preparedStatement.setNull(index, Types.OTHER)
        } else if (value is Duration) {
            // Convert Duration to PGInterval
            val totalSeconds = value.seconds
            val days = totalSeconds / (24 * 3600)
            val hours = (totalSeconds % (24 * 3600)) / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = (totalSeconds % 60).toDouble()

            val pgInterval = org.postgresql.util.PGInterval(
                0, 0, days.toInt(), hours.toInt(), minutes.toInt(), seconds
            )
            preparedStatement.setObject(index, pgInterval)
        } else {
            throw IllegalArgumentException("Expected Duration but got ${value::class.qualifiedName}")
        }
    }

    override fun getSqlType(): Int = Types.OTHER // INTERVAL doesn't have a specific JDBC type
}
