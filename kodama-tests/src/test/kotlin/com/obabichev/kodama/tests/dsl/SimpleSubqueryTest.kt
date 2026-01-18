package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SimpleSubqueryTest : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Order, Person)

    @Test
    fun testSimpleSubqueryWithAggregates() {
        testData {
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "bob", "Keyboard", 100)
        }

        withConnection {
            // Inline subquery with fromAliased API
            val results = fromAliased(UserTotalSubquery) {
                from(Order)
                    .selectAs(OrderUserName) { order.userName }
                    .selectAs(MyAlias) { sum(order.cost) }
                    .groupBy { order.userName }
                    .build()
            }
                .selectAll(UserTotalSubquery)
                .execute(this)

            val data = results.map {
                it.userTotalSubquery.orderUserName to it.userTotalSubquery.myAlias
            }.toList()

            val users = data.map { it.first as? String ?: "" }.toSet()
            assertTrue(users.contains("alice"), "Should have alice")
            assertTrue(users.contains("bob"), "Should have bob")
        }
    }

    @Test
    fun testNewSubqueryAliasingAPI() {
        testData {
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "bob", "Keyboard", 100)
        }

        withConnection {
            // Test fromAliased with selectAs for aggregates
            val query = fromAliased(UserTotalSubquery) {
                from(Order)
                    .selectAs(OrderUserName) { order.userName }
                    .selectAs(MyAlias) { sum(order.cost) }
                    .groupBy { order.userName }
                    .build()
            }
                .selectAll(UserTotalSubquery)

            val results = query.execute(this)

            val userTotals = results.map {
                (it.userTotalSubquery.orderUserName ?: "") to (it.userTotalSubquery.myAlias ?: 0L)
            }.toList().sortedBy { it.first }

            assertEquals(2, userTotals.size, "Should have 2 users")
            assertEquals("alice" to 1050L, userTotals[0])
            assertEquals("bob" to 100L, userTotals[1])
        }
    }

    @Test
    fun testSubqueryJoinWithTable() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 35)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "bob", "Keyboard", 100)
        }

        withConnection {
            // Test joining a table with an inline subquery
            val results = from(Person)
                .joinAliased(
                    from(Order)
                        .selectAs(OrderUserName) { order.userName }
                        .selectAs(MyAlias) { sum(order.cost) }
                        .groupBy { order.userName }
                        .build()
                        .aliasAs<UserTotalSubquery>()
                ) { person.name eq userTotalSubquery.orderUserName }
                .selectAll(Person)
                .selectAll(UserTotalSubquery)
                .execute(this)

            val resultList = results.toList()

            // Just check that we got results - don't access columns yet
            assertTrue(resultList.isNotEmpty(), "Should have at least some results")
            println("Got ${resultList.size} results from join")
        }
    }

    @Test
    fun testSubqueryLeftJoin() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 35)
            order(1, "alice", "Laptop", 1000)
            order(2, "bob", "Keyboard", 100)
        }

        withConnection {
            // LEFT JOIN Person with subquery (charlie has no orders)
            val results = from(Person)
                .leftJoinAliased(
                    from(Order)
                        .selectAs(OrderUserName) { order.userName }
                        .selectAs(MyAlias) { sum(order.cost) }
                        .groupBy { order.userName }
                        .build()
                        .aliasAs<UserTotalSubquery>()
                ) { person.name eq userTotalSubquery.orderUserName }
                .selectAll(Person)
                .selectAll(UserTotalSubquery)
                .execute(this)

            val resultList = results.toList()

            // Just check that we got results
            assertTrue(resultList.isNotEmpty(), "Should have at least some results")
            println("Got ${resultList.size} results from left join")
        }
    }
}
