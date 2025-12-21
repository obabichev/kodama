package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.components.expression.and
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Tests for NULL check operators: isNull, isNotNull
 *
 * Verifies:
 * - isNull() correctly filters rows with NULL values
 * - isNotNull() correctly filters rows with non-NULL values
 * - Operators generate correct SQL (IS NULL, IS NOT NULL)
 * - Operators work with different column types (varchar, integer, etc.)
 */
class NullCheckOperatorTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Product)

    @Test
    fun testIsNullOperator() {
        testData {
            product(
                id = 1,
                name = "Widget A",
                description = "A useful widget",
                price = 100,
                discount = 10
            )
            product(
                id = 2,
                name = "Widget B",
                description = null,  // NULL description
                price = 200,
                discount = null      // NULL discount
            )
            product(
                id = 3,
                name = "Widget C",
                description = null,  // NULL description
                price = 150,
                discount = 20
            )
        }

        withConnection {
            // Test IS NULL on varchar column
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.description.isNull()
                }

            val sql = queryBuilder.build().sql()
            println("IS NULL Test SQL: $sql")
            assertTrue(sql.contains("IS NULL"), "SQL should contain IS NULL")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two rows with NULL description")
            assertEquals(listOf(2, 3), ids)

            // Verify all descriptions are actually null
            results.forEach { row ->
                assertNull(row.product.description, "Description should be NULL")
            }
        }
    }

    @Test
    fun testIsNullOnIntegerColumn() {
        testData {
            product(
                id = 1,
                name = "Widget A",
                description = "Description A",
                price = 100,
                discount = 10
            )
            product(
                id = 2,
                name = "Widget B",
                description = "Description B",
                price = 200,
                discount = null  // NULL discount
            )
            product(
                id = 3,
                name = "Widget C",
                description = "Description C",
                price = 150,
                discount = null  // NULL discount
            )
        }

        withConnection {
            // Test IS NULL on integer column
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.discount.isNull()
                }

            val sql = queryBuilder.build().sql()
            assertTrue(sql.contains("IS NULL"), "SQL should contain IS NULL")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two rows with NULL discount")
            assertEquals(listOf(2, 3), ids)

            // Verify all discounts are actually null
            results.forEach { row ->
                assertNull(row.product.discount, "Discount should be NULL")
            }
        }
    }

    @Test
    fun testIsNotNullOperator() {
        testData {
            product(
                id = 1,
                name = "Widget A",
                description = "A useful widget",
                price = 100,
                discount = 10
            )
            product(
                id = 2,
                name = "Widget B",
                description = null,  // NULL description
                price = 200,
                discount = null      // NULL discount
            )
            product(
                id = 3,
                name = "Widget C",
                description = "Another widget",
                price = 150,
                discount = 20
            )
        }

        withConnection {
            // Test IS NOT NULL on varchar column
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.description.isNotNull()
                }

            val sql = queryBuilder.build().sql()
            println("IS NOT NULL Test SQL: $sql")
            assertTrue(sql.contains("IS NOT NULL"), "SQL should contain IS NOT NULL")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two rows with non-NULL description")
            assertEquals(listOf(1, 3), ids)

            // Verify all descriptions are not null
            results.forEach { row ->
                assertNotNull(row.product.description, "Description should not be NULL")
            }
        }
    }

    @Test
    fun testIsNotNullOnIntegerColumn() {
        testData {
            product(
                id = 1,
                name = "Widget A",
                description = "Description A",
                price = 100,
                discount = 10
            )
            product(
                id = 2,
                name = "Widget B",
                description = "Description B",
                price = 200,
                discount = null  // NULL discount
            )
            product(
                id = 3,
                name = "Widget C",
                description = "Description C",
                price = 150,
                discount = 15
            )
        }

        withConnection {
            // Test IS NOT NULL on integer column
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.discount.isNotNull()
                }

            val sql = queryBuilder.build().sql()
            assertTrue(sql.contains("IS NOT NULL"), "SQL should contain IS NOT NULL")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two rows with non-NULL discount")
            assertEquals(listOf(1, 3), ids)

            // Verify all discounts are not null
            results.forEach { row ->
                assertNotNull(row.product.discount, "Discount should not be NULL")
                assertTrue((row.product.discount as Int) > 0, "Discount should have a value")
            }
        }
    }

    @Test
    fun testCombinedNullChecks() {
        testData {
            product(
                id = 1,
                name = "Widget A",
                description = "A useful widget",
                price = 100,
                discount = 10
            )
            product(
                id = 2,
                name = "Widget B",
                description = null,
                price = 200,
                discount = null
            )
            product(
                id = 3,
                name = "Widget C",
                description = "Another widget",
                price = 150,
                discount = null  // Has description but no discount
            )
            product(
                id = 4,
                name = "Widget D",
                description = null,  // No description but has discount
                price = 175,
                discount = 25
            )
        }

        withConnection {
            // Find products with description but no discount
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.description.isNotNull() and product.discount.isNull()
                }

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList()
            assertEquals(1, ids.size, "Should match one row with description but no discount")
            assertEquals(3, ids[0])

            // Verify the conditions
            results.forEach { row ->
                assertNotNull(row.product.description, "Should have description")
                assertNull(row.product.discount, "Should not have discount")
            }
        }
    }

    @Test
    fun testAllNullsVsAllNonNulls() {
        testData {
            product(
                id = 1,
                name = "Complete",
                description = "Full description",
                price = 100,
                discount = 10
            )
            product(
                id = 2,
                name = "Missing All",
                description = null,
                price = 200,
                discount = null
            )
        }

        withConnection {
            // Find products where both optional fields are NULL
            val allNullResults = from(Product)
                .selectAll(Product)
                .where {
                    product.description.isNull() and product.discount.isNull()
                }
                .execute(this)

            var allNullCount = 0
            var allNullId: Int? = null
            allNullResults.forEach { row ->
                allNullCount++
                allNullId = row.product.id as Int
            }
            assertEquals(1, allNullCount, "Should find one product with all NULLs")
            assertEquals(2, allNullId)

            // Find products where both optional fields are NOT NULL
            val allNotNullResults = from(Product)
                .selectAll(Product)
                .where {
                    product.description.isNotNull() and product.discount.isNotNull()
                }
                .execute(this)

            var allNotNullCount = 0
            var allNotNullId: Int? = null
            allNotNullResults.forEach { row ->
                allNotNullCount++
                allNotNullId = row.product.id as Int
            }
            assertEquals(1, allNotNullCount, "Should find one product with no NULLs")
            assertEquals(1, allNotNullId)
        }
    }
}
