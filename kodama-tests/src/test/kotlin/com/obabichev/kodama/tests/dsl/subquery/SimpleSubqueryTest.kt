package com.obabichev.kodama.tests.dsl.subquery

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.generated.fromAliased
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
            // Inline subquery with marker token parameter API
            val results = fromAliased(UserTotalsNew) {
                from(Order)
                    .selectAs(OrderUserName) { order.userName }
                    .selectAs(MyAlias) { sum(order.cost) }
                    .groupBy { order.userName }
            }
                .selectAll(UserTotalsNew)  // Direct parameter - no lambda!
                .execute(this)

            val users = results.map { it.userTotalsNew.orderUserName as? String ?: "" }.toSet()
            assertTrue(users.contains("alice"), "Should have alice")
            assertTrue(users.contains("bob"), "Should have bob")
        }
    }

    @Test
    fun testSubqueryJoinWithTable() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "bob", "Keyboard", 100)
        }

        withConnection {
            // Inline subquery in join with .aliasAs<T>() API
            val results = from(Person)
                .joinAliased(
                    from(Order)
                        .selectAs(OrderUserName) { order.userName }
                        .build()
                        .aliasAs<UsersWithOrders>()
                ) { person.name eq this.usersWithOrders.orderUserName }
                .selectAll(Person)
                .selectAll(UsersWithOrders)  // Direct parameter - no lambda!
                .execute(this)

            val names = results.map { it.person.name as String }.toList().sorted()

            // Alice appears twice because she has 2 orders (no DISTINCT in subquery)
            // Bob appears once
            assertEquals(3, names.size, "Should have 3 rows (alice twice, bob once)")
            assertEquals(listOf("alice", "alice", "bob"), names)
        }
    }

    @Test
    fun testSubqueryLeftJoin() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(1, "alice", "Laptop", 1000)
            order(2, "bob", "Keyboard", 100)
        }

        withConnection {
            // Inline subquery in left join with .aliasAs<T>() API
            val results = from(Person)
                .leftJoinAliased(
                    from(Order)
                        .selectAs(OrderUserName) { order.userName }
                        .selectAs(OrderCount) { count(order.id) }
                        .groupBy { order.userName }
                        .build()
                        .aliasAs<OrderCounts>()) { person.name eq this.orderCounts.orderUserName }
                .selectAll(Person)
                .execute(this)

            val names = results.map { it.person.name as String }.toList().sorted()

            // All 3 people should appear (even charlie who has no orders)
            assertEquals(3, names.size, "Should have all 3 people")
            assertEquals(listOf("alice", "bob", "charlie"), names)
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
            // Test the new inline .aliasAs<T>() API - provides compile-time type safety!
            val query = fromAliased(UserTotalSubquery) {
                from(Order)
                    .selectAs(OrderUserName) { order.userName }
                    .selectAs(MyAlias) { sum(order.cost) }
                    .groupBy { order.userName }
            }
                .selectAll(UserTotalSubquery)

            println(query.build().sql())

            val results = query  // Direct parameter - no lambda!
                .execute(this)

            val userTotals = results.map {
                (it.userTotalSubquery.orderUserName ?: "") to (it.userTotalSubquery.myAlias?.toInt() ?: 0)
            }.toList().sortedBy { it.first }

            assertEquals(2, userTotals.size, "Should have 2 users")
            assertEquals("alice" to 1050, userTotals[0])
            assertEquals("bob" to 100, userTotals[1])
        }
    }
}
