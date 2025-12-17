package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Numerics
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for comparison operators: eq, neq, lt, lte, gt, gte
 *
 * Verifies:
 * - All comparison operators work with integer values
 * - Operators generate correct SQL
 * - Operators work with different numeric types
 */
class ComparisonOperatorTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Numerics)

    @Test
    fun testEqualOperator() {
        testData {
            numerics(
                id = 1,
                smallIntValue = 10,
                intValue = 100,
                bigIntValue = 1000L,
                decimalValue = BigDecimal("99.99"),
                realValue = 1.5f,
                doubleValue = 2.5
            )
            numerics(
                id = 2,
                smallIntValue = 20,
                intValue = 200,
                bigIntValue = 2000L,
                decimalValue = BigDecimal("199.99"),
                realValue = 2.5f,
                doubleValue = 3.5
            )
        }

        withConnection {
            val queryBuilder = query()
                .from(Numerics)
                .selectAll(Numerics)
                .where {
                    numerics.intValue eq 100
                }

            val sql = queryBuilder.build().sql()
            assertTrue(sql.contains("="), "SQL should contain = operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                assertEquals(100, row.numerics.intValue)
            }
            assertEquals(1, count, "Should match exactly one row")
        }
    }

    @Test
    fun testNotEqualOperator() {
        testData {
            numerics(
                id = 1,
                smallIntValue = 10,
                intValue = 100,
                bigIntValue = 1000L,
                decimalValue = BigDecimal("99.99"),
                realValue = 1.5f,
                doubleValue = 2.5
            )
            numerics(
                id = 2,
                smallIntValue = 20,
                intValue = 200,
                bigIntValue = 2000L,
                decimalValue = BigDecimal("199.99"),
                realValue = 2.5f,
                doubleValue = 3.5
            )
        }

        withConnection {
            val queryBuilder = query()
                .from(Numerics)
                .selectAll(Numerics)
                .where {
                    numerics.intValue neq 100
                }

            val sql = queryBuilder.build().sql()
            assertTrue(sql.contains("<>"), "SQL should contain <> operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                assertTrue((row.numerics.intValue as Int) != 100)
            }
            assertEquals(1, count, "Should match one row (intValue != 100)")
        }
    }

    @Test
    fun testLessThanOperator() {
        testData {
            numerics(
                id = 1,
                smallIntValue = 10,
                intValue = 50,
                bigIntValue = 1000L,
                decimalValue = BigDecimal("99.99"),
                realValue = 1.5f,
                doubleValue = 2.5
            )
            numerics(
                id = 2,
                smallIntValue = 20,
                intValue = 100,
                bigIntValue = 2000L,
                decimalValue = BigDecimal("199.99"),
                realValue = 2.5f,
                doubleValue = 3.5
            )
            numerics(
                id = 3,
                smallIntValue = 30,
                intValue = 150,
                bigIntValue = 3000L,
                decimalValue = BigDecimal("299.99"),
                realValue = 3.5f,
                doubleValue = 4.5
            )
        }

        withConnection {
            val queryBuilder = query()
                .from(Numerics)
                .selectAll(Numerics)
                .where {
                    numerics.intValue lt 100
                }

            val sql = queryBuilder.build().sql()
            println("LT Test SQL: $sql")
            assertTrue(sql.contains("<"), "SQL should contain < operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val intValue = row.numerics.intValue as Int
                assertTrue(intValue < 100, "intValue should be < 100")
            }
            assertEquals(1, count, "Should match one row (intValue < 100)")
        }
    }

    @Test
    fun testLessThanOrEqualOperator() {
        testData {
            numerics(
                id = 1,
                smallIntValue = 10,
                intValue = 50,
                bigIntValue = 1000L,
                decimalValue = BigDecimal("99.99"),
                realValue = 1.5f,
                doubleValue = 2.5
            )
            numerics(
                id = 2,
                smallIntValue = 20,
                intValue = 100,
                bigIntValue = 2000L,
                decimalValue = BigDecimal("199.99"),
                realValue = 2.5f,
                doubleValue = 3.5
            )
            numerics(
                id = 3,
                smallIntValue = 30,
                intValue = 150,
                bigIntValue = 3000L,
                decimalValue = BigDecimal("299.99"),
                realValue = 3.5f,
                doubleValue = 4.5
            )
        }

        withConnection {
            val queryBuilder = query()
                .from(Numerics)
                .selectAll(Numerics)
                .where {
                    numerics.intValue lte 100
                }

            val sql = queryBuilder.build().sql()
            println("LTE Test SQL: $sql")
            assertTrue(sql.contains("<="), "SQL should contain <= operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val intValue = row.numerics.intValue as Int
                assertTrue(intValue <= 100, "intValue should be <= 100")
            }
            assertEquals(2, count, "Should match two rows (intValue <= 100)")
        }
    }

    @Test
    fun testGreaterThanOperator() {
        testData {
            numerics(
                id = 1,
                smallIntValue = 10,
                intValue = 50,
                bigIntValue = 1000L,
                decimalValue = BigDecimal("99.99"),
                realValue = 1.5f,
                doubleValue = 2.5
            )
            numerics(
                id = 2,
                smallIntValue = 20,
                intValue = 100,
                bigIntValue = 2000L,
                decimalValue = BigDecimal("199.99"),
                realValue = 2.5f,
                doubleValue = 3.5
            )
            numerics(
                id = 3,
                smallIntValue = 30,
                intValue = 150,
                bigIntValue = 3000L,
                decimalValue = BigDecimal("299.99"),
                realValue = 3.5f,
                doubleValue = 4.5
            )
        }

        withConnection {
            val queryBuilder = query()
                .from(Numerics)
                .selectAll(Numerics)
                .where {
                    numerics.intValue gt 100
                }

            val sql = queryBuilder.build().sql()
            println("GT Test SQL: $sql")
            assertTrue(sql.contains(">"), "SQL should contain > operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val intValue = row.numerics.intValue as Int
                assertTrue(intValue > 100, "intValue should be > 100")
            }
            assertEquals(1, count, "Should match one row (intValue > 100)")
        }
    }

    @Test
    fun testGreaterThanOrEqualOperator() {
        testData {
            numerics(
                id = 1,
                smallIntValue = 10,
                intValue = 50,
                bigIntValue = 1000L,
                decimalValue = BigDecimal("99.99"),
                realValue = 1.5f,
                doubleValue = 2.5
            )
            numerics(
                id = 2,
                smallIntValue = 20,
                intValue = 100,
                bigIntValue = 2000L,
                decimalValue = BigDecimal("199.99"),
                realValue = 2.5f,
                doubleValue = 3.5
            )
            numerics(
                id = 3,
                smallIntValue = 30,
                intValue = 150,
                bigIntValue = 3000L,
                decimalValue = BigDecimal("299.99"),
                realValue = 3.5f,
                doubleValue = 4.5
            )
        }

        withConnection {
            val queryBuilder = query()
                .from(Numerics)
                .selectAll(Numerics)
                .where {
                    numerics.intValue gte 100
                }

            val sql = queryBuilder.build().sql()
            println("GTE Test SQL: $sql")
            assertTrue(sql.contains(">="), "SQL should contain >= operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val intValue = row.numerics.intValue as Int
                assertTrue(intValue >= 100, "intValue should be >= 100")
            }
            assertEquals(2, count, "Should match two rows (intValue >= 100)")
        }
    }

    @Test
    fun testComparisonWithBigInt() {
        testData {
            numerics(
                id = 1,
                smallIntValue = 10,
                intValue = 100,
                bigIntValue = 5000000000L,  // 5 billion
                decimalValue = BigDecimal("99.99"),
                realValue = 1.5f,
                doubleValue = 2.5
            )
            numerics(
                id = 2,
                smallIntValue = 20,
                intValue = 200,
                bigIntValue = 10000000000L,  // 10 billion
                decimalValue = BigDecimal("199.99"),
                realValue = 2.5f,
                doubleValue = 3.5
            )
        }

        withConnection {
            val queryBuilder = query()
                .from(Numerics)
                .selectAll(Numerics)
                .where {
                    numerics.bigIntValue gt 6000000000L
                }

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val bigIntValue = row.numerics.bigIntValue as Long
                assertTrue(bigIntValue > 6000000000L, "bigIntValue should be > 6 billion")
            }
            assertEquals(1, count, "Should match one row")
        }
    }
}
