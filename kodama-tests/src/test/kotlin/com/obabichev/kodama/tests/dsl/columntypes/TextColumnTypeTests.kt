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
 * Tests for Text/VARCHAR column type.
 *
 * Verifies:
 * - Text columns can be inserted with various string values
 * - Text columns can be queried and read correctly
 * - Nullable text columns support NULL values
 * - Text values are properly typed in Kotlin
 * - Empty strings and special characters are handled correctly
 */
class TextColumnTypeTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Product)

    @Test
    fun testInsertAndQueryText() {
        // Insert a product with text values
        testData {
            product(1, "Laptop", description = "High-performance gaming laptop", price = 1500)
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            val name = row.product.name as String
            val description = row.product.description as String?

            assertEquals("Laptop", name, "name should be 'Laptop'")
            assertEquals("High-performance gaming laptop", description, "description should match")
        }
    }

    @Test
    fun testInsertAndQueryEmptyString() {
        // Insert a product with empty string description
        testData {
            product(1, "Item", description = "", price = 100)
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            val name = row.product.name as String
            val description = row.product.description as String?

            assertEquals("Item", name, "name should be 'Item'")
            assertEquals("", description, "description should be empty string")
        }
    }

    @Test
    fun testNullableTextWithNull() {
        // Insert a product with description=null
        testData {
            product(1, "Mystery Product", description = null, price = 50)
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            val name = row.product.name as String
            val description = row.product.description as String?

            assertEquals("Mystery Product", name, "name should be 'Mystery Product'")
            assertNull(description, "description should be null")
        }
    }

    @Test
    fun testMultipleTextRows() {
        // Insert multiple products with different text values
        testData {
            product(1, "Laptop", description = "Gaming laptop with RGB", price = 1500)
            product(2, "Mouse", description = null, price = 50)
            product(3, "Keyboard", description = "", price = 100)
            product(4, "Monitor", description = "4K Ultra HD", price = 800)
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
                val name = row.product.name as String
                val description = row.product.description as String?

                when (id) {
                    1 -> {
                        // Product 1: with description
                        assertEquals("Laptop", name, "Row 1: name should be Laptop")
                        assertEquals("Gaming laptop with RGB", description, "Row 1: description should match")
                    }
                    2 -> {
                        // Product 2: description is null
                        assertEquals("Mouse", name, "Row 2: name should be Mouse")
                        assertNull(description, "Row 2: description should be null")
                    }
                    3 -> {
                        // Product 3: description is empty string
                        assertEquals("Keyboard", name, "Row 3: name should be Keyboard")
                        assertEquals("", description, "Row 3: description should be empty string")
                    }
                    4 -> {
                        // Product 4: with description
                        assertEquals("Monitor", name, "Row 4: name should be Monitor")
                        assertEquals("4K Ultra HD", description, "Row 4: description should match")
                    }
                }
            }
            assertEquals(4, rowCount, "Should have 4 rows")
        }
    }

    @Test
    fun testTextColumnDefinition() {
        // Verify text columns are defined correctly
        val nameColumn = Product.name
        val descriptionColumn = Product.description

        assertEquals("name", nameColumn.name)
        assertFalse(nameColumn.nullable, "name should be non-nullable")

        assertEquals("description", descriptionColumn.name)
        assertTrue(descriptionColumn.nullable, "description should be nullable")
    }

    @Test
    fun testTextWithSpecialCharacters() {
        // Test with special characters and Unicode
        testData {
            product(
                1,
                "Special Item",
                description = "Contains special chars: !@#\$%^&*() and unicode: こんにちは",
                price = 999
            )
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            val description = row.product.description as String?

            assertEquals(
                "Contains special chars: !@#\$%^&*() and unicode: こんにちは",
                description,
                "description should preserve special characters and unicode"
            )
        }
    }

    @Test
    fun testTextWithQuotes() {
        // Test with single and double quotes (SQL injection prevention)
        testData {
            product(
                1,
                "Quote Test",
                description = "It's a product with \"quotes\" and 'apostrophes'",
                price = 100
            )
        }

        withConnection {
            val results = query()
                .from(Product)
                .selectAll(Product)
                .execute(this)

            val row = results.first()
            val description = row.product.description as String?

            assertEquals(
                "It's a product with \"quotes\" and 'apostrophes'",
                description,
                "description should preserve quotes safely"
            )
        }
    }
}
