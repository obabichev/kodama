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

/**
 * Tests for string pattern matching operators: like, ilike, startsWith, endsWith, contains
 *
 * Verifies:
 * - LIKE operator for case-sensitive pattern matching
 * - ILIKE operator for case-insensitive pattern matching (PostgreSQL-specific)
 * - startsWith() convenience method
 * - endsWith() convenience method
 * - contains() convenience method
 * - Operators generate correct SQL with proper % wildcards
 */
class StringOperatorTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Product)

    @Test
    fun testLikeOperator() {
        testData {
            product(
                id = 1,
                name = "Widget Alpha",
                description = "First widget",
                price = 100,
                discount = null
            )
            product(
                id = 2,
                name = "Widget Beta",
                description = "Second widget",
                price = 200,
                discount = null
            )
            product(
                id = 3,
                name = "Gadget Gamma",
                description = "A gadget",
                price = 150,
                discount = null
            )
        }

        withConnection {
            // Test LIKE with % wildcard
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.name like "Widget%"
                }

            val sql = queryBuilder.build().sql()
            println("LIKE Test SQL: $sql")
            assertTrue(sql.contains("LIKE"), "SQL should contain LIKE operator")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two products starting with 'Widget'")
            assertEquals(listOf(1, 2), ids)
        }
    }

    @Test
    fun testLikeCaseSensitive() {
        testData {
            product(
                id = 1,
                name = "Widget Alpha",
                description = "First widget",
                price = 100,
                discount = null
            )
            product(
                id = 2,
                name = "widget beta",  // lowercase
                description = "Second widget",
                price = 200,
                discount = null
            )
        }

        withConnection {
            // LIKE is case-sensitive - should only match uppercase Widget
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.name like "Widget%"
                }

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList()
            assertEquals(1, ids.size, "LIKE should be case-sensitive (only match uppercase)")
            assertEquals(1, ids[0])
        }
    }

    @Test
    fun testILikeOperator() {
        testData {
            product(
                id = 1,
                name = "Widget Alpha",
                description = "First widget",
                price = 100,
                discount = null
            )
            product(
                id = 2,
                name = "widget beta",  // lowercase
                description = "Second widget",
                price = 200,
                discount = null
            )
            product(
                id = 3,
                name = "WIDGET GAMMA",  // uppercase
                description = "Third widget",
                price = 150,
                discount = null
            )
        }

        withConnection {
            // Test ILIKE (case-insensitive)
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.name ilike "widget%"
                }

            val sql = queryBuilder.build().sql()
            println("ILIKE Test SQL: $sql")
            assertTrue(sql.contains("ILIKE"), "SQL should contain ILIKE operator")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(3, ids.size, "ILIKE should be case-insensitive (match all)")
            assertEquals(listOf(1, 2, 3), ids)
        }
    }

    @Test
    fun testStartsWithOperator() {
        testData {
            product(
                id = 1,
                name = "Premium Widget",
                description = "High-end widget",
                price = 500,
                discount = null
            )
            product(
                id = 2,
                name = "Premium Gadget",
                description = "High-end gadget",
                price = 600,
                discount = null
            )
            product(
                id = 3,
                name = "Basic Widget",
                description = "Entry-level widget",
                price = 100,
                discount = null
            )
        }

        withConnection {
            // Test startsWith convenience method
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.name startsWith "Premium"
                }

            val sql = queryBuilder.build().sql()
            println("startsWith Test SQL: $sql")
            assertTrue(sql.contains("LIKE"), "startsWith should use LIKE operator")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two products starting with 'Premium'")
            assertEquals(listOf(1, 2), ids)
        }
    }

    @Test
    fun testEndsWithOperator() {
        testData {
            product(
                id = 1,
                name = "Alpha Widget",
                description = "First widget",
                price = 100,
                discount = null
            )
            product(
                id = 2,
                name = "Beta Widget",
                description = "Second widget",
                price = 200,
                discount = null
            )
            product(
                id = 3,
                name = "Gamma Gadget",
                description = "A gadget",
                price = 150,
                discount = null
            )
        }

        withConnection {
            // Test endsWith convenience method
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.name endsWith "Widget"
                }

            val sql = queryBuilder.build().sql()
            println("endsWith Test SQL: $sql")
            assertTrue(sql.contains("LIKE"), "endsWith should use LIKE operator")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two products ending with 'Widget'")
            assertEquals(listOf(1, 2), ids)
        }
    }

    @Test
    fun testContainsOperator() {
        testData {
            product(
                id = 1,
                name = "Super Widget Pro",
                description = "Professional widget",
                price = 300,
                discount = null
            )
            product(
                id = 2,
                name = "Ultra Widget Max",
                description = "Maximum widget",
                price = 400,
                discount = null
            )
            product(
                id = 3,
                name = "Simple Gadget",
                description = "Basic gadget",
                price = 150,
                discount = null
            )
        }

        withConnection {
            // Test contains convenience method
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.name contains "Widget"
                }

            val sql = queryBuilder.build().sql()
            println("contains Test SQL: $sql")
            assertTrue(sql.contains("LIKE"), "contains should use LIKE operator")

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two products containing 'Widget'")
            assertEquals(listOf(1, 2), ids)
        }
    }

    @Test
    fun testLikeWithMiddleWildcard() {
        testData {
            product(
                id = 1,
                name = "Widget-Alpha-Pro",
                description = "First pro widget",
                price = 100,
                discount = null
            )
            product(
                id = 2,
                name = "Widget-Beta-Pro",
                description = "Second pro widget",
                price = 200,
                discount = null
            )
            product(
                id = 3,
                name = "Widget-Gamma-Basic",
                description = "Basic widget",
                price = 150,
                discount = null
            )
        }

        withConnection {
            // Test LIKE with wildcard in the middle
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.name like "Widget-%Pro"
                }

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match products with pattern 'Widget-*-Pro'")
            assertEquals(listOf(1, 2), ids)
        }
    }

    @Test
    fun testContainsOnNullableColumn() {
        testData {
            product(
                id = 1,
                name = "Widget A",
                description = "This is a great widget",
                price = 100,
                discount = null
            )
            product(
                id = 2,
                name = "Widget B",
                description = null,  // NULL description
                price = 200,
                discount = null
            )
            product(
                id = 3,
                name = "Widget C",
                description = "Another great product",
                price = 150,
                discount = null
            )
        }

        withConnection {
            // Test contains on nullable column - should only match non-NULL values
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.description contains "great"
                }

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match two products with 'great' in description")
            assertEquals(listOf(1, 3), ids)
        }
    }

    @Test
    fun testCombinedStringOperators() {
        testData {
            product(
                id = 1,
                name = "Premium Widget Pro",
                description = "Professional widget",
                price = 500,
                discount = null
            )
            product(
                id = 2,
                name = "Premium Gadget Pro",
                description = "Professional gadget",
                price = 600,
                discount = null
            )
            product(
                id = 3,
                name = "Premium Widget Basic",
                description = "Entry-level widget",
                price = 200,
                discount = null
            )
        }

        withConnection {
            // Combine startsWith and endsWith
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    (product.name startsWith "Premium") and (product.name endsWith "Pro")
                }

            val results = queryBuilder.execute(this)
            val ids = results.map { it.product.id as Int }.toList().sorted()
            assertEquals(2, ids.size, "Should match products starting with 'Premium' and ending with 'Pro'")
            assertEquals(listOf(1, 2), ids)
        }
    }

    @Test
    fun testLikeWithSpecialCharacters() {
        testData {
            product(
                id = 1,
                name = "Widget 100%",
                description = "Full power widget",
                price = 100,
                discount = null
            )
            product(
                id = 2,
                name = "Widget 50%",
                description = "Half power widget",
                price = 50,
                discount = null
            )
            product(
                id = 3,
                name = "Widget Regular",
                description = "Normal widget",
                price = 75,
                discount = null
            )
        }

        withConnection {
            // Test LIKE with special character (%)
            // Note: In real usage, special characters should be escaped, but this tests basic functionality
            val queryBuilder = from(Product)
                .selectAll(Product)
                .where {
                    product.name like "Widget%"
                }

            val results = queryBuilder.execute(this)
            // Should match all three since % is a wildcard in LIKE
            assertEquals(3, results.count(), "Should match all products starting with 'Widget'")
        }
    }
}
