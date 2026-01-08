package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for all join types: INNER, LEFT, RIGHT, FULL.
 *
 * Test data setup:
 * - Person: alice (has orders), bob (no orders)
 * - Order: order1 (alice's), order2 (alice's), order3 (charlie - person doesn't exist)
 */
class JoinTypesTests : DatabaseTest() {

    override fun requiredTables(): List<Table> = listOf(Person, Order)

    @Test
    fun testInnerJoin() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "charlie", "Keyboard", 100)  // charlie doesn't exist as person
        }

        val queryBuilder = from(Person)
            .innerJoin(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)

        withConnection {
            val results = queryBuilder.execute(this)
            val names = results.mapNotNull { it.person.name }.sorted()

            // INNER JOIN: Only rows where both tables match
            // alice appears twice (2 orders), bob doesn't appear (no orders), charlie doesn't appear (no person)
            assertEquals(2, names.size, "INNER JOIN should return 2 rows (alice's 2 orders)")
            assertEquals(listOf("alice", "alice"), names)
        }
    }

    @Test
    fun testJoinIsAliasForInnerJoin() {
        testData {
            person("alice", age = 25)
            order(1, "alice", "Laptop", 1000)
        }

        val queryWithJoin = from(Person)
            .join(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)

        val queryWithInnerJoin = from(Person)
            .innerJoin(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)

        withConnection {
            // Both .join() and .innerJoin() should produce identical results
            val resultsWithJoin = queryWithJoin.execute(this).toList()
            val resultsWithInnerJoin = queryWithInnerJoin.execute(this).toList()

            assertEquals(resultsWithJoin.size, resultsWithInnerJoin.size)
            assertEquals(1, resultsWithJoin.size)
        }
    }

    @Test
    fun testLeftJoin() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "charlie", "Keyboard", 100)  // charlie doesn't exist as person
        }

        val queryBuilder = from(Person)
            .leftJoin(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)

        withConnection {
            val results = queryBuilder.execute(this)

            // Collect ALL data in a single pass to avoid ResultSet exhaustion
            val data = results.map { row ->
                Triple(row.person.name, row.order.product, row.person.name to row.order.product)
            }.toList()

            // LEFT JOIN: All persons + their orders (if any)
            // alice appears twice (2 orders), bob appears once (no orders)
            val names = data.mapNotNull { it.first }.sorted()
            assertEquals(3, names.size, "LEFT JOIN should return 3 rows (alice twice, bob once)")
            assertEquals(listOf("alice", "alice", "bob"), names)

            // Bob's order fields should be null
            val bobData = data.first { it.first == "bob" }
            // Order columns are nullable for persons without orders
            assertEquals<String?>(null, bobData.second, "Bob should have no order product")
        }
    }

    @Test
    fun testRightJoin() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "charlie", "Keyboard", 100)  // charlie doesn't exist as person
        }

        val queryBuilder = from(Person)
            .rightJoin(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)

        withConnection {
            val results = queryBuilder.execute(this)

            // Collect ALL data in a single pass to avoid ResultSet exhaustion
            val data = results.map { row ->
                Triple(row.order.product, row.order.userName, row.person.name)
            }.toList()

            // RIGHT JOIN: All orders + their persons (if any)
            // alice's 2 orders (with person), charlie's order (person is null)
            val orderProducts = data.mapNotNull { it.first }.sorted()
            assertEquals(3, orderProducts.size, "RIGHT JOIN should return 3 rows (all orders)")
            assertEquals(listOf("Keyboard", "Laptop", "Mouse"), orderProducts)

            // Charlie's order should have null person fields
            val charlieData = data.first { it.second == "charlie" }
            // Person columns are nullable for orders without persons
            assertEquals<String?>(null, charlieData.third, "Charlie's order should have no person")
        }
    }

    @Test
    fun testFullJoin() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "charlie", "Keyboard", 100)  // charlie doesn't exist as person
        }

        val queryBuilder = from(Person)
            .fullJoin(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)

        withConnection {
            val results = queryBuilder.execute(this)

            // Collect data in a single pass to avoid ResultSet positioning issues
            val data = results.map { row ->
                row.person.name to row.order.product
            }.toList()

            // FULL OUTER JOIN: All persons + all orders
            // alice (2 orders), bob (no orders), charlie's order (no person)
            // = alice twice, bob once, charlie's order once = 4 rows
            assertEquals(4, data.size, "FULL JOIN should return 4 rows (all persons and all orders)")

            val personNames = data.mapNotNull { it.first }.sorted()
            val orderProducts = data.mapNotNull { it.second }.sorted()

            // Should have alice (2x), bob (1x) = 3 person rows
            assertEquals(listOf("alice", "alice", "bob"), personNames)

            // Should have all 3 orders
            assertEquals(listOf("Keyboard", "Laptop", "Mouse"), orderProducts)
        }
    }

    @Test
    fun testJoinWithWhereClause() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "bob", "Keyboard", 100)
        }

        // Test that all join types work with WHERE clause
        val queryBuilder = from(Person)
            .innerJoin(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)
            .where { order.cost gte 100 }

        withConnection {
            val results = queryBuilder.execute(this)
            val products = results.mapNotNull { it.order.product }.sorted()

            // Should only return expensive orders (Laptop, Keyboard)
            assertEquals(2, products.size)
            assertTrue(products.contains("Laptop"))
            assertTrue(products.contains("Keyboard"))
        }
    }

    @Test
    fun testJoinWithOrderBy() {
        testData {
            person("alice", age = 25)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "alice", "Keyboard", 100)
        }

        // Test that all join types work with ORDER BY
        val queryBuilder = from(Person)
            .innerJoin(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)
            .orderBy { order.cost.desc() }

        withConnection {
            val results = queryBuilder.execute(this)
            val products = results.mapNotNull { it.order.product }

            // Should be ordered by cost descending
            assertEquals(listOf("Laptop", "Keyboard", "Mouse"), products)
        }
    }
}
