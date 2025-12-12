package com.obabichev.kodama.tests

import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryOrderByTests : PostgresBaseTest() {

    @Test
    fun testOrderByAsc() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.all())
            .orderBy {
                +person.age.asc()
            }

        println("Test ORDER BY ASC SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row ->
                Triple(row.person.name as String, row.person.age as Int, Unit)
            }.toList()
            assertEquals(3, list.size)
            assertEquals("kodama", list[0].first)
            assertEquals(1, list[0].second)
            assertEquals("kokoro", list[1].first)
            assertEquals(2, list[1].second)
            assertEquals("pipiru", list[2].first)
            assertEquals(2, list[2].second)
        }
    }

    @Test
    fun testOrderByDesc() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.all())
            .orderBy {
                +person.age.desc()
            }

        println("Test ORDER BY DESC SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row ->
                Pair(row.person.name as String, row.person.age as Int)
            }.toList()
            assertEquals(3, list.size)
            // First two have age 2, then one with age 1
            assertEquals(2, list[0].second)
            assertEquals(2, list[1].second)
            assertEquals(1, list[2].second)
            assertEquals("kodama", list[2].first)
        }
    }

    @Test
    fun testOrderByMultipleColumns() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.all())
            .orderBy {
                +person.age.desc()
                +person.name.asc()
            }

        println("Test ORDER BY Multiple Columns SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row ->
                Pair(row.person.name as String, row.person.age as Int)
            }.toList()
            assertEquals(3, list.size)
            // Age 2 desc, then name asc: kokoro, pipiru (both age 2), then kodama (age 1)
            assertEquals(2, list[0].second)
            assertEquals("kokoro", list[0].first)
            assertEquals(2, list[1].second)
            assertEquals("pipiru", list[1].first)
            assertEquals(1, list[2].second)
            assertEquals("kodama", list[2].first)
        }
    }

    @Test
    fun testOrderByWithWhere() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.all())
            .where {
                person.age eq 2
            }
            .orderBy {
                +person.name.asc()
            }

        println("Test ORDER BY with WHERE SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row -> row.person.name as String }.toList()
            assertEquals(2, list.size)
            assertEquals("kokoro", list[0])
            assertEquals("pipiru", list[1])
        }
    }

    @Test
    fun testOrderByWithJoin() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) {
                order.userName eq person.name
            }
            .select(Person.all())
            .select(Order.all())
            .orderBy {
                +order.cost.desc()
            }

        println("Test ORDER BY with JOIN SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row ->
                Triple(row.order.product as String, row.order.cost as Int, Unit)
            }.toList()
            assertEquals(3, list.size)
            // Ordered by cost desc: Laptop (1000), Keyboard (100), Mouse (50)
            assertEquals("Laptop", list[0].first)
            assertEquals(1000, list[0].second)
            assertEquals("Keyboard", list[1].first)
            assertEquals(100, list[1].second)
            assertEquals("Mouse", list[2].first)
            assertEquals(50, list[2].second)
        }
    }

    @Test
    fun testOrderByMultipleColumnsWithJoin() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) {
                order.userName eq person.name
            }
            .select(Person.all())
            .select(Order.all())
            .orderBy {
                +person.name.asc()
                +order.cost.asc()
            }

        println("Test ORDER BY Multiple Columns with JOIN SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val list = results.map { row ->
                Pair(row.person.name as String, row.order.cost as Int)
            }.toList()
            assertEquals(3, list.size)
            // First by name asc, then by cost asc
            // kodama: Mouse (50), Laptop (1000)
            // kokoro: Keyboard (100)
            assertEquals("kodama", list[0].first)
            assertEquals(50, list[0].second)
            assertEquals("kodama", list[1].first)
            assertEquals(1000, list[1].second)
            assertEquals("kokoro", list[2].first)
            assertEquals(100, list[2].second)
        }
    }
}
