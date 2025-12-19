package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.query
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Numerics
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for all numeric column types.
 *
 * Verifies:
 * - SmallInt (Short) columns work correctly
 * - BigInt (Long) columns work correctly
 * - Decimal/Numeric (BigDecimal) columns work correctly
 * - Real (Float) columns work correctly
 * - Double Precision (Double) columns work correctly
 * - Nullable numeric columns support NULL values
 * - Type conversions work as expected
 */
class NumericColumnTypeTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Numerics)

    @Test
    fun testSmallIntColumnType() {
        // Test SmallInt (Short) - range: -32,768 to 32,767
        testData {
            numerics(
                id = 1,
                smallIntValue = 100,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 0.0
            )
            numerics(
                id = 2,
                smallIntValue = Short.MAX_VALUE,  // 32,767
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 0.0
            )
            numerics(
                id = 3,
                smallIntValue = Short.MIN_VALUE,  // -32,768
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 0.0
            )
        }

        withConnection {
            val results = query()
                .from(Numerics)
                .selectAll(Numerics)
                .execute(this)

            var count = 0
            results.forEach { row ->
                count++
                val id = row.numerics.id as Int
                val smallIntValue = row.numerics.smallIntValue as Short

                when (id) {
                    1 -> assertEquals(100.toShort(), smallIntValue)
                    2 -> assertEquals(Short.MAX_VALUE, smallIntValue)
                    3 -> assertEquals(Short.MIN_VALUE, smallIntValue)
                }
            }
            assertEquals(3, count, "Should have 3 rows")
        }
    }

    @Test
    fun testBigIntColumnType() {
        // Test BigInt (Long) - range: -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807
        testData {
            numerics(
                id = 1,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 5000000000L,  // 5 billion
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 0.0
            )
            numerics(
                id = 2,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = Long.MAX_VALUE,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 0.0
            )
            numerics(
                id = 3,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = Long.MIN_VALUE,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 0.0
            )
        }

        withConnection {
            val results = query()
                .from(Numerics)
                .selectAll(Numerics)
                .execute(this)

            var count = 0
            results.forEach { row ->
                count++
                val id = row.numerics.id as Int
                val bigIntValue = row.numerics.bigIntValue as Long

                when (id) {
                    1 -> assertEquals(5000000000L, bigIntValue)
                    2 -> assertEquals(Long.MAX_VALUE, bigIntValue)
                    3 -> assertEquals(Long.MIN_VALUE, bigIntValue)
                }
            }
            assertEquals(3, count, "Should have 3 rows")
        }
    }

    @Test
    fun testDecimalColumnType() {
        // Test Decimal/Numeric (BigDecimal) - arbitrary precision
        testData {
            numerics(
                id = 1,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal("123.45"),
                realValue = 0f,
                doubleValue = 0.0
            )
            numerics(
                id = 2,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal("99999999.99"),
                realValue = 0f,
                doubleValue = 0.0
            )
            numerics(
                id = 3,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal("0.01"),
                realValue = 0f,
                doubleValue = 0.0
            )
        }

        withConnection {
            val results = query()
                .from(Numerics)
                .selectAll(Numerics)
                .execute(this)

            var count = 0
            results.forEach { row ->
                count++
                val id = row.numerics.id as Int
                val decimalValue = row.numerics.decimalValue as BigDecimal

                when (id) {
                    1 -> assertEquals(BigDecimal("123.45"), decimalValue)
                    2 -> assertEquals(BigDecimal("99999999.99"), decimalValue)
                    3 -> assertEquals(BigDecimal("0.01"), decimalValue)
                }
            }
            assertEquals(3, count, "Should have 3 rows")
        }
    }

    @Test
    fun testRealColumnType() {
        // Test Real (Float) - single precision floating point
        testData {
            numerics(
                id = 1,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 1.5f,
                doubleValue = 0.0
            )
            numerics(
                id = 2,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 3.14159f,
                doubleValue = 0.0
            )
            numerics(
                id = 3,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = -2.5f,
                doubleValue = 0.0
            )
        }

        withConnection {
            val results = query()
                .from(Numerics)
                .selectAll(Numerics)
                .execute(this)

            var count = 0
            results.forEach { row ->
                count++
                val id = row.numerics.id as Int
                val realValue = row.numerics.realValue as Float

                when (id) {
                    1 -> assertEquals(1.5f, realValue, 0.001f)
                    2 -> assertEquals(3.14159f, realValue, 0.001f)
                    3 -> assertEquals(-2.5f, realValue, 0.001f)
                }
            }
            assertEquals(3, count, "Should have 3 rows")
        }
    }

    @Test
    fun testDoublePrecisionColumnType() {
        // Test Double Precision (Double) - double precision floating point
        testData {
            numerics(
                id = 1,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 2.718281828459045
            )
            numerics(
                id = 2,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 3.141592653589793
            )
            numerics(
                id = 3,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = -1.414213562373095
            )
        }

        withConnection {
            val results = query()
                .from(Numerics)
                .selectAll(Numerics)
                .execute(this)

            var count = 0
            results.forEach { row ->
                count++
                val id = row.numerics.id as Int
                val doubleValue = row.numerics.doubleValue as Double

                when (id) {
                    1 -> assertEquals(2.718281828459045, doubleValue, 0.000001)
                    2 -> assertEquals(3.141592653589793, doubleValue, 0.000001)
                    3 -> assertEquals(-1.414213562373095, doubleValue, 0.000001)
                }
            }
            assertEquals(3, count, "Should have 3 rows")
        }
    }

    @Test
    fun testNullableNumericColumns() {
        // Test that nullable numeric columns support NULL
        testData {
            numerics(
                id = 1,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 0.0,
                nullableSmallInt = null,
                nullableBigInt = null,
                nullableDecimal = null,
                nullableReal = null,
                nullableDouble = null
            )
            numerics(
                id = 2,
                smallIntValue = 0,
                intValue = 0,
                bigIntValue = 0L,
                decimalValue = BigDecimal.ZERO,
                realValue = 0f,
                doubleValue = 0.0,
                nullableSmallInt = 100,
                nullableBigInt = 1000000L,
                nullableDecimal = BigDecimal("50.25"),
                nullableReal = 1.5f,
                nullableDouble = 2.5
            )
        }

        withConnection {
            val results = query()
                .from(Numerics)
                .selectAll(Numerics)
                .execute(this)

            var count = 0
            results.forEach { row ->
                count++
                val id = row.numerics.id as Int

                when (id) {
                    1 -> {
                        // All nullable columns should be null
                        assertNull(row.numerics.nullableSmallInt)
                        assertNull(row.numerics.nullableBigInt)
                        assertNull(row.numerics.nullableDecimal)
                        assertNull(row.numerics.nullableReal)
                        assertNull(row.numerics.nullableDouble)
                    }
                    2 -> {
                        // All nullable columns should have values
                        assertEquals(100.toShort(), row.numerics.nullableSmallInt as Short?)
                        assertEquals(1000000L, row.numerics.nullableBigInt as Long?)
                        assertEquals(BigDecimal("50.25"), row.numerics.nullableDecimal as BigDecimal?)
                        assertEquals(1.5f, (row.numerics.nullableReal as Float?)!!, 0.001f)
                        assertEquals(2.5, (row.numerics.nullableDouble as Double?)!!, 0.001)
                    }
                }
            }
            assertEquals(2, count, "Should have 2 rows")
        }
    }

    @Test
    fun testAllNumericsInSingleRow() {
        // Test all numeric types work together in a single row
        testData {
            numerics(
                id = 1,
                smallIntValue = 123,
                intValue = 456789,
                bigIntValue = 9876543210L,
                decimalValue = BigDecimal("12345.67"),
                realValue = 3.14f,
                doubleValue = 2.71828,
                nullableSmallInt = 99,
                nullableBigInt = 88888888L,
                nullableDecimal = BigDecimal("111.11"),
                nullableReal = 9.99f,
                nullableDouble = 8.88
            )
        }

        withConnection {
            val results = query()
                .from(Numerics)
                .selectAll(Numerics)
                .execute(this)

            val row = results.first()

            // Verify non-nullable columns
            assertEquals(123.toShort(), row.numerics.smallIntValue as Short)
            assertEquals(456789, row.numerics.intValue as Int)
            assertEquals(9876543210L, row.numerics.bigIntValue as Long)
            assertEquals(BigDecimal("12345.67"), row.numerics.decimalValue as BigDecimal)
            assertEquals(3.14f, row.numerics.realValue as Float, 0.001f)
            assertEquals(2.71828, row.numerics.doubleValue as Double, 0.00001)

            // Verify nullable columns
            assertEquals(99.toShort(), row.numerics.nullableSmallInt as Short?)
            assertEquals(88888888L, row.numerics.nullableBigInt as Long?)
            assertEquals(BigDecimal("111.11"), row.numerics.nullableDecimal as BigDecimal?)
            assertEquals(9.99f, (row.numerics.nullableReal as Float?)!!, 0.001f)
            assertEquals(8.88, (row.numerics.nullableDouble as Double?)!!, 0.001)
        }
    }
}
