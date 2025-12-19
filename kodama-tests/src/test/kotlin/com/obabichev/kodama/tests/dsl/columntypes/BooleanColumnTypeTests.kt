package com.obabichev.kodama.tests.dsl.columntypes

import com.obabichev.kodama.query.query
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Boolean column type.
 *
 * Verifies:
 * - Boolean columns can be inserted with true/false values
 * - Boolean columns can be queried and read correctly
 * - Nullable boolean columns support NULL values
 * - Boolean values are properly typed in Kotlin
 */
class BooleanColumnTypeTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Settings)

    @Test
    fun testInsertAndQueryBooleanTrue() {
        // Insert a setting with enabled=true
        testData {
            settings(1, "feature_flag", enabled = true, verified = true)
        }

        withConnection {
            val results = query()
                .from(Settings)
                .selectAll(Settings)
                .execute(this)

            val row = results.first()
            val enabled = row.settings.enabled as Boolean
            val verified = row.settings.verified as Boolean?

            assertTrue(enabled, "enabled should be true")
            assertEquals(true, verified, "verified should be true")
        }
    }

    @Test
    fun testInsertAndQueryBooleanFalse() {
        // Insert a setting with enabled=false
        testData {
            settings(1, "debug_mode", enabled = false, verified = false)
        }

        withConnection {
            val results = query()
                .from(Settings)
                .selectAll(Settings)
                .execute(this)

            val row = results.first()
            val enabled = row.settings.enabled as Boolean
            val verified = row.settings.verified as Boolean?

            assertFalse(enabled, "enabled should be false")
            assertEquals(false, verified, "verified should be false")
        }
    }

    @Test
    fun testNullableBooleanWithNull() {
        // Insert a setting with verified=null
        testData {
            settings(1, "pending_feature", enabled = true, verified = null)
        }

        withConnection {
            val results = query()
                .from(Settings)
                .selectAll(Settings)
                .execute(this)

            val row = results.first()
            val enabled = row.settings.enabled as Boolean
            val verified = row.settings.verified as Boolean?

            assertTrue(enabled, "enabled should be true")
            assertNull(verified, "verified should be null")
        }
    }

    @Test
    fun testMultipleBooleanRows() {
        // Insert multiple settings with different boolean combinations
        testData {
            settings(1, "feature_a", enabled = true, verified = true)
            settings(2, "feature_b", enabled = false, verified = false)
            settings(3, "feature_c", enabled = true, verified = null)
            settings(4, "feature_d", enabled = false, verified = true)
        }

        withConnection {
            val results = query()
                .from(Settings)
                .selectAll(Settings)
                .execute(this)

            var rowCount = 0
            results.forEach { row ->
                rowCount++
                val id = row.settings.id as Int
                val enabled = row.settings.enabled as Boolean
                val verified = row.settings.verified as Boolean?

                when (id) {
                    1 -> {
                        // Feature A: enabled=true, verified=true
                        assertTrue(enabled, "Row 1: enabled should be true")
                        assertEquals(true, verified, "Row 1: verified should be true")
                    }
                    2 -> {
                        // Feature B: enabled=false, verified=false
                        assertFalse(enabled, "Row 2: enabled should be false")
                        assertEquals(false, verified, "Row 2: verified should be false")
                    }
                    3 -> {
                        // Feature C: enabled=true, verified=null
                        assertTrue(enabled, "Row 3: enabled should be true")
                        assertNull(verified, "Row 3: verified should be null")
                    }
                    4 -> {
                        // Feature D: enabled=false, verified=true
                        assertFalse(enabled, "Row 4: enabled should be false")
                        assertEquals(true, verified, "Row 4: verified should be true")
                    }
                }
            }
            assertEquals(4, rowCount, "Should have 4 rows")
        }
    }

    @Test
    fun testBooleanColumnDefinition() {
        // Verify boolean columns are defined correctly
        val enabledColumn = Settings.enabled
        val verifiedColumn = Settings.verified

        assertEquals("enabled", enabledColumn.name)
        assertFalse(enabledColumn.nullable, "enabled should be non-nullable")

        assertEquals("verified", verifiedColumn.name)
        assertTrue(verifiedColumn.nullable, "verified should be nullable")
    }
}
