package com.obabichev.kodama.tests

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.Product
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests to verify that column types correctly reflect nullability in the type system
 */
class ColumnTypeTests {

    @Test
    fun testNonNullableColumnTypes() {
        // Person.name and Person.age should be Column<String> and Column<Int>
        val name: Column<String> = Person.name
        val age: Column<Int> = Person.age

        // Verify nullable property
        assertFalse(name.nullable, "Person.name should not be nullable")
        assertFalse(age.nullable, "Person.age should not be nullable")
    }

    @Test
    fun testNullableColumnTypes() {
        // Product.description and Product.discount should be Column<String?> and Column<Int?>
        val description: Column<String?> = Product.description
        val discount: Column<Int?> = Product.discount

        // Verify nullable property
        assertTrue(description.nullable, "Product.description should be nullable")
        assertTrue(discount.nullable, "Product.discount should be nullable")
    }

    @Test
    fun testMixedColumnTypes() {
        // Product has both nullable and non-nullable columns
        val id: Column<Int> = Product.id  // Non-nullable
        val name: Column<String> = Product.name  // Non-nullable
        val description: Column<String?> = Product.description  // Nullable
        val price: Column<Int> = Product.price  // Non-nullable
        val discount: Column<Int?> = Product.discount  // Nullable

        // Verify nullable properties
        assertFalse(id.nullable, "Product.id should not be nullable")
        assertFalse(name.nullable, "Product.name should not be nullable")
        assertTrue(description.nullable, "Product.description should be nullable")
        assertFalse(price.nullable, "Product.price should not be nullable")
        assertTrue(discount.nullable, "Product.discount should be nullable")
    }

    @Test
    fun testTypeSystemEnforcesNullability() {
        // This test demonstrates that the type system correctly enforces nullability

        // Non-nullable columns have non-null types
        val nonNullable: Column<Int> = Product.id
        // val shouldNotCompile: Column<Int?> = Product.id  // Would not compile!

        // Nullable columns have nullable types
        val nullable: Column<Int?> = Product.discount
        // val shouldNotCompile2: Column<Int> = Product.discount  // Would not compile!

        // The type parameter correctly reflects the nullability
        assertFalse(nonNullable.nullable)
        assertTrue(nullable.nullable)
    }
}
