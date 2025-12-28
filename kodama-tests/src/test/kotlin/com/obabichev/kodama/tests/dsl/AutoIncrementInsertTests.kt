package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.insert
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.SerialTest
import com.obabichev.kodama.tests.schema.IdentityTest
import com.obabichev.kodama.tests.schema.BigSerialTest
import com.obabichev.kodama.tests.schema.SmallSerialTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Tests for auto-increment INSERT functionality.
 *
 * Tests verify that:
 * - SERIAL columns are excluded from insert() parameters
 * - IDENTITY columns are excluded from insert() parameters
 * - Generated IDs are returned in InsertResult.generatedKeys
 * - Multiple inserts generate unique IDs
 */
class AutoIncrementInsertTests : DatabaseTest() {

    override fun requiredTables(): List<Table> = listOf(
        SerialTest,
        IdentityTest,
        BigSerialTest,
        SmallSerialTest
    )

    // ============================================================================
    // SERIAL Tests (PostgreSQL-specific auto-increment)
    // ============================================================================

    @Test
    fun testSerialInsert_excludesIdParameter() {
        withConnection {
            // INSERT with SERIAL - id parameter should NOT be required
            val result = SerialTest.insert(
                transaction = this,
                // No 'id' parameter - auto-generated!
                name = "Test Entry",
                value = 42
            )

            assertTrue(result.isSuccess, "Insert should be successful")
            assertEquals(1, result.rowsAffected, "Should insert exactly 1 row")
        }
    }

    @Test
    fun testSerialInsert_returnsGeneratedId() {
        withConnection {
            val result = SerialTest.insert(
                transaction = this,
                name = "Entry with ID",
                value = 100
            )

            assertTrue(result.isSuccess)

            // Verify generated ID is returned
            val generatedId = result.generatedKeys["id"]
            assertNotNull(generatedId, "Generated ID should be returned")
            assertTrue(generatedId is Int, "Generated ID should be Int")
            assertTrue((generatedId as Int) > 0, "Generated ID should be positive")
        }
    }

    @Test
    fun testSerialInsert_multipleInsertsGenerateUniqueIds() {
        withConnection {
            // Insert multiple rows
            val result1 = SerialTest.insert(
                transaction = this,
                name = "Entry 1",
                value = 10
            )

            val result2 = SerialTest.insert(
                transaction = this,
                name = "Entry 2",
                value = 20
            )

            val result3 = SerialTest.insert(
                transaction = this,
                name = "Entry 3",
                value = 30
            )

            // Get generated IDs
            val id1 = result1.generatedKeys["id"] as Int
            val id2 = result2.generatedKeys["id"] as Int
            val id3 = result3.generatedKeys["id"] as Int

            // Verify all IDs are different and increasing
            assertTrue(id1 > 0, "First ID should be positive")
            assertTrue(id2 > id1, "Second ID should be greater than first")
            assertTrue(id3 > id2, "Third ID should be greater than second")
        }
    }

    // ============================================================================
    // IDENTITY Tests (SQL standard auto-increment)
    // ============================================================================

    @Test
    fun testIdentityInsert_excludesIdParameter() {
        withConnection {
            // INSERT with IDENTITY - id parameter should NOT be required
            val result = IdentityTest.insert(
                transaction = this,
                // No 'id' parameter - auto-generated!
                name = "Identity Test",
                value = 99
            )

            assertTrue(result.isSuccess, "Insert should be successful")
            assertEquals(1, result.rowsAffected, "Should insert exactly 1 row")
        }
    }

    @Test
    fun testIdentityInsert_returnsGeneratedId() {
        withConnection {
            val result = IdentityTest.insert(
                transaction = this,
                name = "Identity Entry",
                value = 200
            )

            assertTrue(result.isSuccess)

            // Verify generated ID is returned
            val generatedId = result.generatedKeys["id"]
            assertNotNull(generatedId, "Generated ID should be returned")
            assertTrue(generatedId is Int, "Generated ID should be Int")
            assertTrue((generatedId as Int) > 0, "Generated ID should be positive")
        }
    }

    @Test
    fun testIdentityInsert_multipleInsertsGenerateUniqueIds() {
        withConnection {
            val result1 = IdentityTest.insert(
                transaction = this,
                name = "Identity 1",
                value = 11
            )

            val result2 = IdentityTest.insert(
                transaction = this,
                name = "Identity 2",
                value = 22
            )

            val result3 = IdentityTest.insert(
                transaction = this,
                name = "Identity 3",
                value = 33
            )

            // Get generated IDs
            val id1 = result1.generatedKeys["id"] as Int
            val id2 = result2.generatedKeys["id"] as Int
            val id3 = result3.generatedKeys["id"] as Int

            // Verify all IDs are different and increasing
            assertTrue(id1 > 0)
            assertTrue(id2 > id1)
            assertTrue(id3 > id2)
        }
    }

    // ============================================================================
    // BIGSERIAL Tests (large auto-increment)
    // ============================================================================

    @Test
    fun testBigSerialInsert_excludesIdParameter() {
        withConnection {
            // INSERT with BIGSERIAL - id parameter should NOT be required
            val result = BigSerialTest.insert(
                transaction = this,
                // No 'id' parameter - auto-generated!
                description = "BigSerial Test Entry"
            )

            assertTrue(result.isSuccess, "Insert should be successful")
            assertEquals(1, result.rowsAffected, "Should insert exactly 1 row")
        }
    }

    @Test
    fun testBigSerialInsert_returnsGeneratedId() {
        withConnection {
            val result = BigSerialTest.insert(
                transaction = this,
                description = "Entry with BIGSERIAL ID"
            )

            assertTrue(result.isSuccess)

            // Verify generated ID is returned as Long
            val generatedId = result.generatedKeys["id"]
            assertNotNull(generatedId, "Generated ID should be returned")
            assertTrue(generatedId is Long, "Generated ID should be Long for BIGSERIAL")
            assertTrue((generatedId as Long) > 0L, "Generated ID should be positive")
        }
    }

    // ============================================================================
    // SMALLSERIAL Tests (small auto-increment)
    // ============================================================================

    @Test
    fun testSmallSerialInsert_excludesIdParameter() {
        withConnection {
            // INSERT with SMALLSERIAL - id parameter should NOT be required
            val result = SmallSerialTest.insert(
                transaction = this,
                // No 'id' parameter - auto-generated!
                tag = "small-tag"
            )

            assertTrue(result.isSuccess, "Insert should be successful")
            assertEquals(1, result.rowsAffected, "Should insert exactly 1 row")
        }
    }

    @Test
    fun testSmallSerialInsert_returnsGeneratedId() {
        withConnection {
            val result = SmallSerialTest.insert(
                transaction = this,
                tag = "test-tag"
            )

            assertTrue(result.isSuccess)

            // Verify generated ID is returned
            // Note: PostgreSQL JDBC returns Int for all SERIAL types (including SMALLSERIAL)
            val generatedId = result.generatedKeys["id"]
            assertNotNull(generatedId, "Generated ID should be returned")
            assertTrue(generatedId is Int, "Generated ID is returned as Int by PostgreSQL JDBC")
            assertTrue((generatedId as Int) > 0, "Generated ID should be positive")
        }
    }

    // ============================================================================
    // Comparison Test: SERIAL vs IDENTITY behavior
    // ============================================================================

    @Test
    fun testSerialVsIdentity_bothWorkIdentically() {
        withConnection {
            // Insert using SERIAL
            val serialResult = SerialTest.insert(
                transaction = this,
                name = "SERIAL row",
                value = 123
            )

            // Insert using IDENTITY
            val identityResult = IdentityTest.insert(
                transaction = this,
                name = "IDENTITY row",
                value = 456
            )

            // Both should succeed
            assertTrue(serialResult.isSuccess)
            assertTrue(identityResult.isSuccess)

            // Both should return generated IDs
            assertNotNull(serialResult.generatedKeys["id"])
            assertNotNull(identityResult.generatedKeys["id"])

            // Both IDs should be positive integers
            assertTrue((serialResult.generatedKeys["id"] as Int) > 0)
            assertTrue((identityResult.generatedKeys["id"] as Int) > 0)
        }
    }
}
