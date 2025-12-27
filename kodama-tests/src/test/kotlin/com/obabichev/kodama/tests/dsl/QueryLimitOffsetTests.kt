package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryLimitOffsetTests : DatabaseTest() {

    override fun requiredTables(): List<Table> = listOf(Person, Order)

    @Test
    fun testLimitOnly() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
            person("charlie", age = 3)
            person("david", age = 4)
            person("eve", age = 5)
        }

        val queryBuilder = from(Person)
            .selectAll(Person)
            .orderBy { person.age.asc() }
            .limit(3)

        println("Test LIMIT only SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(3, list.size)
            assertEquals("alice", list[0])
            assertEquals("bob", list[1])
            assertEquals("charlie", list[2])
        }
    }

    @Test
    fun testOffsetOnly() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
            person("charlie", age = 3)
            person("david", age = 4)
            person("eve", age = 5)
        }

        val queryBuilder = from(Person)
            .selectAll(Person)
            .orderBy { person.age.asc() }
            .offset(2)

        println("Test OFFSET only SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(3, list.size)
            assertEquals("charlie", list[0])
            assertEquals("david", list[1])
            assertEquals("eve", list[2])
        }
    }

    @Test
    fun testLimitAndOffset() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
            person("charlie", age = 3)
            person("david", age = 4)
            person("eve", age = 5)
        }

        val queryBuilder = from(Person)
            .selectAll(Person)
            .orderBy { person.age.asc() }
            .limit(2)
            .offset(1)

        println("Test LIMIT and OFFSET SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(2, list.size)
            assertEquals("bob", list[0])
            assertEquals("charlie", list[1])
        }
    }

    @Test
    fun testPaginationPattern() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
            person("charlie", age = 3)
            person("david", age = 4)
            person("eve", age = 5)
            person("frank", age = 6)
            person("grace", age = 7)
        }

        val pageSize = 3

        // Page 0
        val page0 = from(Person)
            .selectAll(Person)
            .orderBy { person.age.asc() }
            .limit(pageSize)
            .offset(0 * pageSize)

        println("Test Pagination Page 0 SQL: ${page0.build().sql()}")

        withConnection {
            val results = page0.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(3, list.size)
            assertEquals("alice", list[0])
            assertEquals("bob", list[1])
            assertEquals("charlie", list[2])
        }

        // Page 1
        val page1 = from(Person)
            .selectAll(Person)
            .orderBy { person.age.asc() }
            .limit(pageSize)
            .offset(1 * pageSize)

        println("Test Pagination Page 1 SQL: ${page1.build().sql()}")

        withConnection {
            val results = page1.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(3, list.size)
            assertEquals("david", list[0])
            assertEquals("eve", list[1])
            assertEquals("frank", list[2])
        }

        // Page 2
        val page2 = from(Person)
            .selectAll(Person)
            .orderBy { person.age.asc() }
            .limit(pageSize)
            .offset(2 * pageSize)

        println("Test Pagination Page 2 SQL: ${page2.build().sql()}")

        withConnection {
            val results = page2.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(1, list.size)  // Last page has only 1 item
            assertEquals("grace", list[0])
        }
    }

    @Test
    fun testLimitWithWhere() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
            person("charlie", age = 2)
            person("david", age = 2)
            person("eve", age = 3)
        }

        val queryBuilder = from(Person)
            .selectAll(Person)
            .where { person.age eq 2 }
            .orderBy { person.name.asc() }
            .limit(2)

        println("Test LIMIT with WHERE SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(2, list.size)
            assertEquals("bob", list[0])
            assertEquals("charlie", list[1])
        }
    }

    @Test
    fun testLimitOffsetWithJoin() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
            person("charlie", age = 3)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "bob", "Keyboard", 100)
            order(4, "bob", "Monitor", 300)
            order(5, "charlie", "Headphones", 80)
        }

        val queryBuilder = from(Person)
            .join(Order) { order.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)
            .orderBy { order.cost.desc() }
            .limit(3)
            .offset(1)

        println("Test LIMIT/OFFSET with JOIN SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row ->
                Pair(row.order.product as String, row.order.cost as Int)
            }.toList()
            assertEquals(3, list.size)
            // Ordered by cost desc: Laptop (1000) [skipped], Monitor (300), Keyboard (100), Headphones (80)
            assertEquals("Monitor", list[0].first)
            assertEquals(300, list[0].second)
            assertEquals("Keyboard", list[1].first)
            assertEquals(100, list[1].second)
            assertEquals("Headphones", list[2].first)
            assertEquals(80, list[2].second)
        }
    }

    @Test
    fun testLimitZero() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
        }

        val queryBuilder = from(Person)
            .selectAll(Person)
            .limit(0)

        println("Test LIMIT 0 SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.toList()
            assertEquals(0, list.size)
        }
    }

    @Test
    fun testLimitGreaterThanResultCount() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
        }

        val queryBuilder = from(Person)
            .selectAll(Person)
            .orderBy { person.age.asc() }
            .limit(100)

        println("Test LIMIT > result count SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(2, list.size)  // Should return all 2 rows
            assertEquals("alice", list[0])
            assertEquals("bob", list[1])
        }
    }

    @Test
    fun testOffsetGreaterThanResultCount() {
        testData {
            person("alice", age = 1)
            person("bob", age = 2)
        }

        val queryBuilder = from(Person)
            .selectAll(Person)
            .offset(100)

        println("Test OFFSET > result count SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.toList()
            assertEquals(0, list.size)  // Should return empty list
        }
    }
}
