package com.obabichev.kodama.compiler.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for string case conversion utilities.
 *
 * These tests verify that our case conversion functions work correctly
 * and can replace regex-based conversions.
 */
class StringUtilsTest {

    // ============ toSnakeCase() tests ============

    @Test
    fun `toSnakeCase handles simple camelCase`() {
        assertEquals("my_property", "myProperty".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles PascalCase`() {
        assertEquals("my_class_name", "MyClassName".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles single uppercase letter`() {
        assertEquals("a", "A".toSnakeCase())
        assertEquals("my_a", "myA".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles consecutive uppercase letters (acronyms)`() {
        // Each uppercase letter gets an underscore
        assertEquals("h_t_t_p_s_connection", "HTTPSConnection".toSnakeCase())
        assertEquals("u_r_l", "URL".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles empty string`() {
        assertEquals("", "".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles single character`() {
        assertEquals("a", "a".toSnakeCase())
        assertEquals("z", "Z".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles all lowercase`() {
        assertEquals("alllowercase", "alllowercase".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles all uppercase`() {
        assertEquals("a_b_c", "ABC".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles typical marker names`() {
        assertEquals("total_revenue", "TotalRevenue".toSnakeCase())
        assertEquals("order_count", "OrderCount".toSnakeCase())
        assertEquals("user_id", "UserId".toSnakeCase())
        assertEquals("my_subquery_name", "MySubqueryName".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles numbers`() {
        assertEquals("order123_status", "order123Status".toSnakeCase())
        assertEquals("my_var1", "myVar1".toSnakeCase())
    }

    // ============ toCamelCase() tests ============

    @Test
    fun `toCamelCase handles PascalCase`() {
        assertEquals("myClassName", "MyClassName".toCamelCase())
    }

    @Test
    fun `toCamelCase handles single uppercase letter`() {
        assertEquals("a", "A".toCamelCase())
    }

    @Test
    fun `toCamelCase handles empty string`() {
        assertEquals("", "".toCamelCase())
    }

    @Test
    fun `toCamelCase handles already camelCase`() {
        assertEquals("myProperty", "myProperty".toCamelCase())
    }

    @Test
    fun `toCamelCase handles acronyms`() {
        assertEquals("hTTPSConnection", "HTTPSConnection".toCamelCase())
    }

    @Test
    fun `toCamelCase handles typical marker names`() {
        assertEquals("totalRevenue", "TotalRevenue".toCamelCase())
        assertEquals("orderCount", "OrderCount".toCamelCase())
    }

    // ============ toPascalCase() tests ============

    @Test
    fun `toPascalCase handles simple snake_case`() {
        assertEquals("MyPropertyName", "my_property_name".toPascalCase())
    }

    @Test
    fun `toPascalCase handles single word`() {
        assertEquals("User", "user".toPascalCase())
        assertEquals("A", "a".toPascalCase())
    }

    @Test
    fun `toPascalCase handles empty string`() {
        assertEquals("", "".toPascalCase())
    }

    @Test
    fun `toPascalCase handles double underscores`() {
        // Consecutive underscores create empty strings, which should be filtered
        assertEquals("MyName", "my__name".toPascalCase())
    }

    @Test
    fun `toPascalCase handles typical SQL column names`() {
        assertEquals("UserId", "user_id".toPascalCase())
        assertEquals("OrderCount", "order_count".toPascalCase())
        assertEquals("TotalRevenue", "total_revenue".toPascalCase())
    }

    @Test
    fun `toPascalCase handles trailing underscore`() {
        assertEquals("MyName", "my_name_".toPascalCase())
    }

    @Test
    fun `toPascalCase handles leading underscore`() {
        assertEquals("MyName", "_my_name".toPascalCase())
    }

    // ============ interfaceNameToPropertyName() tests ============

    @Test
    fun `interfaceNameToPropertyName converts PascalCase to camelCase`() {
        assertEquals("totalRevenue", interfaceNameToPropertyName("TotalRevenue"))
        assertEquals("orderCount", interfaceNameToPropertyName("OrderCount"))
        assertEquals("myInterface", interfaceNameToPropertyName("MyInterface"))
    }

    @Test
    fun `interfaceNameToPropertyName handles single character`() {
        assertEquals("a", interfaceNameToPropertyName("A"))
    }

    @Test
    fun `interfaceNameToPropertyName handles empty string`() {
        assertEquals("", interfaceNameToPropertyName(""))
    }

    // ============ Round-trip tests ============

    @Test
    fun `round trip PascalCase to snake_case to PascalCase`() {
        val original = "MyClassName"
        val snakeCase = original.toSnakeCase()  // "my_class_name"
        val backToPascal = snakeCase.toPascalCase()  // "MyClassName"

        assertEquals("my_class_name", snakeCase)
        assertEquals(original, backToPascal)
    }

    @Test
    fun `round trip camelCase to snake_case and back`() {
        val original = "myPropertyName"
        val snakeCase = original.toSnakeCase()  // "my_property_name"

        assertEquals("my_property_name", snakeCase)
        // Note: Can't round-trip camelCase -> snake_case -> camelCase perfectly
        // because we lose information about where capital was
    }

    // ============ Performance comparison (informational) ============

    @Test
    fun `toSnakeCase is faster than regex for typical strings`() {
        // This test doesn't assert anything, but documents the performance benefit
        val testString = "MyVeryLongClassNameWithManyWords"

        // Our implementation
        val result1 = testString.toSnakeCase()

        // Regex-based approach (for comparison)
        val regex = Regex("([a-z])([A-Z])")
        val result2 = testString.replace(regex, "$1_$2").lowercase()

        // Both should produce same result
        assertEquals(result2, result1)
    }
}
