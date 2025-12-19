package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.data.insert
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Profile
import com.obabichev.kodama.tests.schema.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class InsertTests : DatabaseTest() {

    override fun requiredTables(): List<Table> = listOf(Order, Profile, Product)

    @Test
    fun testBasicInsert() {
        withConnection {
            // Insert a new order
            val result = Order.insert(
                transaction = this,
                id = 100,
                userName = "testuser",
                product = "Tablet",
                cost = 500
            )

            // Verify insert was successful
            assertTrue(result.isSuccess, "Insert should be successful")
            assertEquals(1, result.rowsAffected, "Should insert exactly 1 row")
        }
    }

    @Test
    fun testInsertWithNullableColumns() {
        withConnection {
            // Insert profile with explicit null for nullable column
            val result = Profile.insert(
                transaction = this,
                userName = "nulltest",
                contact = "nulltest@example.com",
                photo = null  // Explicitly passing null for nullable column
            )

            assertTrue(result.isSuccess, "Insert should be successful")
            assertEquals(1, result.rowsAffected, "Should insert exactly 1 row")
        }
    }

    @Test
    fun testInsertWithSomeNullableColumns() {
        withConnection {
            // Insert product with some nullable columns as null, others with values
            val result = Product.insert(
                transaction = this,
                id = 200,
                name = "Headphones",
                description = "Wireless headphones",  // Non-null
                price = 80,
                discount = null  // Null
            )

            assertTrue(result.isSuccess)
            assertEquals(1, result.rowsAffected)
        }
    }

    @Test
    fun testInsertMultipleRows() {
        withConnection {
            // Insert multiple products
            val result1 = Product.insert(
                transaction = this,
                id = 301,
                name = "Product 1",
                description = null,
                price = 100,
                discount = null
            )

            val result2 = Product.insert(
                transaction = this,
                id = 302,
                name = "Product 2",
                description = "Description 2",
                price = 200,
                discount = 5
            )

            val result3 = Product.insert(
                transaction = this,
                id = 303,
                name = "Product 3",
                description = null,
                price = 300,
                discount = 10
            )

            assertTrue(result1.isSuccess)
            assertTrue(result2.isSuccess)
            assertTrue(result3.isSuccess)
        }
    }

    @Test
    fun testInsertResultProperties() {
        withConnection {
            val result = Order.insert(
                transaction = this,
                id = 400,
                userName = "resulttest",
                product = "Test Product",
                cost = 999
            )

            // Test InsertResult properties
            assertTrue(result.isSuccess, "isSuccess should be true")
            assertEquals(1, result.rowsAffected, "rowsAffected should be 1")
            assertNotNull(result.generatedKeys, "generatedKeys should not be null")
            // Note: generatedKeys map may be empty if no auto-increment columns exist
        }
    }
}
