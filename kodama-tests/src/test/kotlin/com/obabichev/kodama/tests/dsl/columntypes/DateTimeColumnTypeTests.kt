package com.obabichev.kodama.tests.dsl.columntypes

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Events
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for DateTime column types.
 *
 * Verifies:
 * - DATE columns map to LocalDate and work correctly
 * - TIME columns map to LocalTime and work correctly
 * - TIMESTAMP columns map to LocalDateTime and work correctly
 * - TIMESTAMP WITH TIME ZONE columns map to OffsetDateTime and work correctly
 * - TIME WITH TIME ZONE columns map to OffsetTime and work correctly
 * - INTERVAL columns map to Duration and work correctly
 * - Nullable datetime columns support NULL values
 */
class DateTimeColumnTypeTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Events)

    @Test
    fun testDateColumn() {
        // Insert an event with a specific date
        val testDate = LocalDate.of(2024, 1, 15)

        testData {
            events(
                id = 1,
                eventDate = testDate,
                eventTime = LocalTime.of(10, 30, 0),
                createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                eventTimestamp = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC),
                reminderTime = OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC),
                duration = java.time.Duration.ofHours(2).plusMinutes(30)
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val eventDate = row.events.eventDate as LocalDate

            assertEquals(testDate, eventDate, "event_date should be 2024-01-15")
            assertEquals(2024, eventDate.year)
            assertEquals(1, eventDate.monthValue)
            assertEquals(15, eventDate.dayOfMonth)
        }
    }

    @Test
    fun testTimeColumn() {
        // Insert an event with a specific time
        val testTime = LocalTime.of(14, 45, 30)

        testData {
            events(
                id = 2,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = testTime,
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                eventTimestamp = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                reminderTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC),
                duration = java.time.Duration.ofHours(1)
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val eventTime = row.events.eventTime as LocalTime

            assertEquals(testTime, eventTime, "event_time should be 14:45:30")
            assertEquals(14, eventTime.hour)
            assertEquals(45, eventTime.minute)
            assertEquals(30, eventTime.second)
        }
    }

    @Test
    fun testTimestampColumn() {
        // Insert an event with a specific timestamp
        val testTimestamp = LocalDateTime.of(2024, 2, 20, 16, 30, 45)

        testData {
            events(
                id = 3,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = LocalTime.of(0, 0, 0),
                createdAt = testTimestamp,
                eventTimestamp = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                reminderTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC),
                duration = java.time.Duration.ofHours(1)
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val createdAt = row.events.createdAt as LocalDateTime

            assertEquals(testTimestamp, createdAt, "created_at should match")
            assertEquals(2024, createdAt.year)
            assertEquals(2, createdAt.monthValue)
            assertEquals(20, createdAt.dayOfMonth)
            assertEquals(16, createdAt.hour)
            assertEquals(30, createdAt.minute)
            assertEquals(45, createdAt.second)
        }
    }

    @Test
    fun testNullableTimestampWithNull() {
        // Insert an event with scheduled_for as null
        testData {
            events(
                id = 4,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = LocalTime.of(0, 0, 0),
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                scheduledFor = null,
                eventTimestamp = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                reminderTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC),
                duration = java.time.Duration.ofHours(1)
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val scheduledFor = row.events.scheduledFor

            assertNull(scheduledFor, "scheduled_for should be null")
        }
    }

    @Test
    fun testNullableTimestampWithValue() {
        // Insert an event with scheduled_for having a value
        val testTimestamp = LocalDateTime.of(2024, 3, 15, 9, 0, 0)

        testData {
            events(
                id = 5,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = LocalTime.of(0, 0, 0),
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                scheduledFor = testTimestamp,
                eventTimestamp = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                reminderTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC),
                duration = java.time.Duration.ofHours(1)
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val scheduledFor = row.events.scheduledFor as LocalDateTime?

            assertEquals(testTimestamp, scheduledFor, "scheduled_for should have the correct value")
        }
    }

    @Test
    fun testTimestampWithTimeZoneColumn() {
        // Insert an event with timestamp with time zone
        // PostgreSQL stores timestamptz in UTC, so we need to account for conversion
        val testTimestamp = OffsetDateTime.of(2024, 4, 10, 15, 30, 0, 0, ZoneOffset.ofHours(2))

        testData {
            events(
                id = 6,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = LocalTime.of(0, 0, 0),
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                eventTimestamp = testTimestamp,
                reminderTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC),
                duration = java.time.Duration.ofHours(1)
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val eventTimestamp = row.events.eventTimestamp as OffsetDateTime

            // PostgreSQL stores timestamptz in UTC, verify the UTC conversion
            assertEquals(2024, eventTimestamp.year)
            assertEquals(4, eventTimestamp.monthValue)
            assertEquals(10, eventTimestamp.dayOfMonth)
            // Time will be converted to UTC (15:30+02 = 13:30 UTC)
            assertEquals(13, eventTimestamp.hour)
            assertEquals(30, eventTimestamp.minute)
        }
    }

    @Test
    fun testTimeWithTimeZoneColumn() {
        // Insert an event with time with time zone
        val testTime = OffsetTime.of(18, 45, 0, 0, ZoneOffset.ofHours(3))

        testData {
            events(
                id = 7,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = LocalTime.of(0, 0, 0),
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                eventTimestamp = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                reminderTime = testTime,
                duration = java.time.Duration.ofHours(1)
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val reminderTime = row.events.reminderTime as OffsetTime

            // Verify the time component
            assertEquals(18, reminderTime.hour)
            assertEquals(45, reminderTime.minute)
            assertEquals(0, reminderTime.second)
        }
    }

    @Test
    fun testIntervalColumn() {
        // Insert an event with an interval (duration)
        val expectedDuration = java.time.Duration.ofDays(3).plusHours(2).plusMinutes(30)

        testData {
            events(
                id = 8,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = LocalTime.of(0, 0, 0),
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                eventTimestamp = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                reminderTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC),
                duration = expectedDuration
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val duration = row.events.duration as java.time.Duration

            // 3 days + 2 hours + 30 minutes = 74.5 hours = 268200 seconds
            assertEquals(expectedDuration, duration, "duration should be 3 days 2 hours 30 minutes")
            assertEquals(268200L, duration.seconds)
        }
    }

    @Test
    fun testNullableIntervalWithNull() {
        // Insert an event with optional_duration as null
        testData {
            events(
                id = 9,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = LocalTime.of(0, 0, 0),
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                eventTimestamp = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                reminderTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC),
                duration = java.time.Duration.ofHours(1),
                optionalDuration = null
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val optionalDuration = row.events.optionalDuration

            assertNull(optionalDuration, "optional_duration should be null")
        }
    }

    @Test
    fun testNullableIntervalWithValue() {
        // Insert an event with optional_duration having a value
        val expectedDuration = java.time.Duration.ofHours(5).plusMinutes(15)

        testData {
            events(
                id = 10,
                eventDate = LocalDate.of(2024, 1, 1),
                eventTime = LocalTime.of(0, 0, 0),
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                eventTimestamp = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                reminderTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC),
                duration = java.time.Duration.ofHours(1),
                optionalDuration = expectedDuration
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()
            val optionalDuration = row.events.optionalDuration as java.time.Duration?

            assertEquals(expectedDuration, optionalDuration, "optional_duration should be 5 hours 15 minutes")
        }
    }

    @Test
    fun testMultipleDateTimeColumnsInSingleQuery() {
        // Insert an event with all datetime fields populated
        val testDate = LocalDate.of(2024, 5, 20)
        val testTime = LocalTime.of(10, 0)
        val testCreatedAt = LocalDateTime.of(2024, 5, 19, 14, 30)
        val testScheduledFor = LocalDateTime.of(2024, 5, 20, 10, 0)
        val testTimestamp = OffsetDateTime.of(2024, 5, 20, 10, 0, 0, 0, ZoneOffset.UTC)
        val testReminderTime = OffsetTime.of(9, 30, 0, 0, ZoneOffset.UTC)
        val testDuration = java.time.Duration.ofHours(2)
        val testOptionalDuration = java.time.Duration.ofMinutes(30)

        testData {
            events(
                id = 11,
                eventDate = testDate,
                eventTime = testTime,
                createdAt = testCreatedAt,
                scheduledFor = testScheduledFor,
                eventTimestamp = testTimestamp,
                reminderTime = testReminderTime,
                duration = testDuration,
                optionalDuration = testOptionalDuration
            )
        }

        withConnection {
            val results = from(Events)
                .selectAll(Events)
                .execute(this)

            val row = results.first()

            // Verify all datetime columns are properly typed and accessible
            val eventDate = row.events.eventDate as LocalDate
            val eventTime = row.events.eventTime as LocalTime
            val createdAt = row.events.createdAt as LocalDateTime
            val scheduledFor = row.events.scheduledFor as LocalDateTime?
            val eventTimestamp = row.events.eventTimestamp as OffsetDateTime
            val reminderTime = row.events.reminderTime as OffsetTime
            val duration = row.events.duration as java.time.Duration
            val optionalDuration = row.events.optionalDuration as java.time.Duration?

            assertEquals(testDate, eventDate)
            assertEquals(testTime, eventTime)
            assertEquals(testCreatedAt, createdAt)
            assertEquals(testScheduledFor, scheduledFor)
            assertEquals(10, eventTimestamp.hour)
            assertEquals(9, reminderTime.hour)
            assertEquals(30, reminderTime.minute)
            assertEquals(testDuration, duration)
            assertEquals(testOptionalDuration, optionalDuration)
        }
    }
}
