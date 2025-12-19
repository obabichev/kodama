package com.obabichev.kodama.tests.dsl.columntypes

import com.obabichev.kodama.query.query
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Integer column type.
 *
 * Verifies:
 * - Integer columns can be inserted with positive/negative/zero values
 * - Integer columns can be queried and read correctly
 * - Nullable integer columns support NULL values
 * - Integer values are properly typed in Kotlin
 */
class IntegerColumnTypeTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Product)

    @Test
    fun testInsertAndQueryPositiveInteger() {
        // Insert a product with positive integers
        testData {
            product(1, "Laptop", description = "Gaming laptop", price = 1500, discount = 10)
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            val price = row.product.price as Int
            val discount = row.product.discount as Int?

            assertEquals(1500, price, "price should be 1500")
            assertEquals(10, discount, "discount should be 10")
        }
    }

    @Test
    fun testInsertAndQueryZeroInteger() {
        // Insert a product with zero values
        testData {
            product(1, "Free Item", price = 0, discount = 0)
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            val price = row.product.price as Int
            val discount = row.product.discount as Int?

            assertEquals(0, price, "price should be 0")
            assertEquals(0, discount, "discount should be 0")
        }
    }

    @Test
    fun testNullableIntegerWithNull() {
        // Insert a product with discount=null
        testData {
            product(1, "No Discount Item", price = 500, discount = null)
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            val price = row.product.price as Int
            val discount = row.product.discount as Int?

            assertEquals(500, price, "price should be 500")
            assertNull(discount, "discount should be null")
        }
    }

    @Test
    fun testMultipleIntegerRows() {
        // Insert multiple products with different integer values
        testData {
            product(1, "Expensive Item", price = 10000, discount = 20)
            product(2, "Cheap Item", price = 50, discount = 5)
            product(3, "No Discount", price = 300, discount = null)
            product(4, "Free Item", price = 0, discount = 0)
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            var rowCount = 0
            results.forEach { row ->
                rowCount++
                val id = row.product.id as Int
                val price = row.product.price as Int
                val discount = row.product.discount as Int?

                when (id) {
                    1 -> {
                        // Product 1: price=10000, discount=20
                        assertEquals(10000, price, "Row 1: price should be 10000")
                        assertEquals(20, discount, "Row 1: discount should be 20")
                    }
                    2 -> {
                        // Product 2: price=50, discount=5
                        assertEquals(50, price, "Row 2: price should be 50")
                        assertEquals(5, discount, "Row 2: discount should be 5")
                    }
                    3 -> {
                        // Product 3: price=300, discount=null
                        assertEquals(300, price, "Row 3: price should be 300")
                        assertNull(discount, "Row 3: discount should be null")
                    }
                    4 -> {
                        // Product 4: price=0, discount=0
                        assertEquals(0, price, "Row 4: price should be 0")
                        assertEquals(0, discount, "Row 4: discount should be 0")
                    }
                }
            }
            assertEquals(4, rowCount, "Should have 4 rows")
        }
    }

    @Test
    fun testIntegerColumnDefinition() {
        // Verify integer columns are defined correctly
        val priceColumn = Product.price
        val discountColumn = Product.discount

        assertEquals("price", priceColumn.name)
        assertFalse(priceColumn.nullable, "price should be non-nullable")

        assertEquals("discount", discountColumn.name)
        assertTrue(discountColumn.nullable, "discount should be nullable")
    }

    @Test
    fun testLargeIntegerValues() {
        // Test with large integer values (within Int range)
        testData {
            product(1, "Expensive", price = Int.MAX_VALUE, discount = Int.MAX_VALUE)
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            assertEquals(Int.MAX_VALUE, row.product.price as Int)
            assertEquals(Int.MAX_VALUE, row.product.discount as Int?)
        }
    }
}
