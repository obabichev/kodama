package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDateTime

/**
 * Column type for PostgreSQL TIMESTAMP type (without time zone).
 * Maps to Kotlin/Java LocalDateTime.
 *
 * Example:
 * ```kotlin
 * object Events : Table("events") {
 *     val createdAt = timestamp("created_at")
 *     val updatedAt = timestamp("updated_at").nullable()
 * }
 * ```
 */
object TimestampColumnType : ColumnType<LocalDateTime> {
    override fun readValue(resultSet: ResultSet, index: Int): LocalDateTime {
        val sqlTimestamp = resultSet.getTimestamp(index)
        return sqlTimestamp?.toLocalDateTime() ?: throw IllegalStateException("Unexpected null value for non-nullable TIMESTAMP column")
    }

    override fun getSqlType(): Int = Types.TIMESTAMP
}
