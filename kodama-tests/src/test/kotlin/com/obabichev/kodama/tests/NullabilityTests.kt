package com.obabichev.kodama.tests

import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Tests for nullable and non-nullable column types
 */
class NullabilityTests : PostgresBaseTest() {

    @Test
    fun testNonNullableColumnsHaveCorrectTypes() {
        // Test that non-nullable columns have non-nullable types (String, Int, not String?, Int?)
        withConnection {
            val results = query()
                .from(Product)
                .select(Product.all())
                .where { product.id eq 1 }
                .execute(this)

            val row = results.first()

            // Non-nullable columns should have non-nullable types
            val id: Int = row.product.id  // Should be Int, not Int?
            val name: String = row.product.name  // Should be String, not String?
            val price: Int = row.product.price  // Should be Int, not Int?

            assertEquals(1, id)
            assertEquals("Laptop", name)
            assertEquals(1500, price)
        }
    }

    @Test
    fun testNullableColumnsHaveCorrectTypes() {
        // Test that nullable columns have nullable types (String?, Int?)
        withConnection {
            val results = query()
                .from(Product)
                .select(Product.all())
                .where { product.id eq 1 }
                .execute(this)

            val row = results.first()

            // Nullable columns should have nullable types
            val description: String? = row.product.description  // Should be String?
            val discount: Int? = row.product.discount  // Should be Int?

            assertNotNull(description)
            assertEquals("High-performance laptop", description)
            assertNotNull(discount)
            assertEquals(10, discount)
        }
    }

    @Test
    fun testNullableColumnsCanBeNull() {
        // Test product with NULL description and discount
        withConnection {
            val results = query()
                .from(Product)
                .select(Product.all())
                .where { product.id eq 2 }  // Mouse has NULL description and discount
                .execute(this)

            val row = results.first()

            assertEquals(2, row.product.id)
            assertEquals("Mouse", row.product.name)
            assertEquals(50, row.product.price)

            // These should be null
            assertNull(row.product.description)
            assertNull(row.product.discount)
        }
    }

    @Test
    fun testMixedNullValues() {
        // Test product with NULL discount only
        withConnection {
            val results = query()
                .from(Product)
                .select(Product.all())
                .where { product.id eq 3 }  // Keyboard has NULL discount only
                .execute(this)

            val row = results.first()

            assertEquals(3, row.product.id)
            assertEquals("Keyboard", row.product.name)
            assertEquals(120, row.product.price)

            assertNotNull(row.product.description)
            assertEquals("Mechanical keyboard", row.product.description)

            assertNull(row.product.discount)
        }
    }

    @Test
    fun testSelectNullableColumnPair() {
        // Test selecting a pair of nullable columns
        withConnection {
            val results = query()
                .from(Product)
                .select(Product.Description)
                .select(Product.Discount)
                .where { product.id eq 4 }  // Monitor has NULL description only
                .execute(this)

            val row = results.first()

            assertNull(row.product.description)
            assertNotNull(row.product.discount)
            assertEquals(15, row.product.discount)
        }
    }

    @Test
    fun testSelectNonNullableColumnPair() {
        // Test selecting a pair of non-nullable columns
        withConnection {
            val results = query()
                .from(Product)
                .select(Product.Id)
                .select(Product.Name)
                .where { product.id eq 2 }
                .execute(this)

            val row = results.first()

            // All selected columns are non-nullable
            val id: Int = row.product.id
            val name: String = row.product.name

            assertEquals(2, id)
            assertEquals("Mouse", name)
        }
    }

    @Test
    fun testSelectMixedNullability() {
        // Test selecting non-nullable and nullable columns together
        withConnection {
            val results = query()
                .from(Product)
                .select(Product.Name)
                .select(Product.Discount)
                .where { product.id eq 3 }
                .execute(this)

            val row = results.first()

            // name is non-nullable, discount is nullable
            val name: String = row.product.name
            val discount: Int? = row.product.discount

            assertEquals("Keyboard", name)
            assertNull(discount)
        }
    }
}
