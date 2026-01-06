package com.obabichev.kodama.tests.dsl.subquery

import com.obabichev.kodama.query.*
import com.obabichev.kodama.components.expression.exists
import com.obabichev.kodama.components.expression.notExists
import com.obabichev.kodama.components.expression.scalarSubquery
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.Order
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for subquery support in Kodama
 *
 * Verifies:
 * - Subqueries in FROM clause (derived tables)
 * - Subqueries in JOIN clause
 * - Scalar subqueries in WHERE clause
 * - EXISTS and NOT EXISTS operators
 * - Proper SQL generation and parameter binding
 * - Type-safe column access for subqueries
 */
class SubqueryTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Person, Order)

    // ========== Subquery in FROM clause tests ==========

    @Test
    fun testSubqueryInFromClause() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(id = 1, userName = "alice", product = "Laptop", cost = 1000)
            order(id = 2, userName = "alice", product = "Mouse", cost = 50)
            order(id = 3, userName = "bob", product = "Keyboard", cost = 100)
            order(id = 4, userName = "charlie", product = "Monitor", cost = 500)
        }

        withConnection {
            // Inline subquery with .aliasAs<T>() in FROM clause
            val queryBuilder = fromAliased(UserTotals) {
                from(Order)
                    .selectAs(OrderUserName) { order.userName }
                    .selectAs(TotalCost) { sum(order.cost) }
                    .groupBy { order.userName }
                    .build()
            }
                .selectAll(UserTotals)

            val sql = queryBuilder.build().sql()
            println("Subquery in FROM SQL: $sql")
            assertTrue(sql.contains("SELECT"), "Should have SELECT")
            assertTrue(sql.contains("user_totals"), "Should use subquery alias")

            val results = queryBuilder.execute(this)
            val resultList = results.map {
                Pair(it.userTotals.orderUserName as? String ?: "", it.userTotals.totalCost?.toInt())
            }.toList().sortedBy { it.first }

            assertEquals(3, resultList.size, "Should have 3 users")
            assertEquals(Pair("alice", 1050), resultList[0])
            assertEquals(Pair("bob", 100), resultList[1])
            assertEquals(Pair("charlie", 500), resultList[2])
        }
    }

    @Test
    fun testSubqueryInFromWithFilter() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            order(id = 1, userName = "alice", product = "Laptop", cost = 1000)
            order(id = 2, userName = "alice", product = "Mouse", cost = 50)
            order(id = 3, userName = "bob", product = "Keyboard", cost = 100)
        }

        withConnection {
            // Inline subquery with filter and marker token parameter API
            val queryBuilder = fromAliased(ExpensiveOrders) {
                from(Order)
                    .selectAs(OrderUserName) { order.userName }
                    .selectAs(OrderProduct) { order.product }
                    .where { order.cost gte 500 }
                    .build()
            }
                .selectAll(ExpensiveOrders)  // Direct parameter - no lambda!

            val results = queryBuilder.execute(this)
            val products = results.map { it.expensiveOrders.orderProduct as String }.toList()

            assertEquals(1, products.size, "Should have 1 expensive order")
            assertEquals("Laptop", products[0])
        }
    }

    // ========== Subquery in JOIN clause tests ==========

    @Test
    fun testSubqueryInJoinClause() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(id = 1, userName = "alice", product = "Laptop", cost = 1000)
            order(id = 2, userName = "bob", product = "Keyboard", cost = 100)
        }

        withConnection {
            // Inline subquery with .aliasAs<T>() API in join
            val queryBuilder = from(Person)
                .joinAliased(
                    from(Order)
                        .selectAs(OrderUserName) { order.userName }
                        .build()
                        .aliasAs<UsersWithOrders>()
                ) { person.name eq usersWithOrders.orderUserName }
                .selectAll(Person)
                .selectAll(UsersWithOrders)  // Direct parameter - no lambda!

            val sql = queryBuilder.build().sql()
            println("Subquery in JOIN SQL: $sql")
            assertTrue(sql.contains("INNER JOIN"), "Should have INNER JOIN")

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()

            // Only alice and bob have orders
            assertEquals(2, names.size, "Should have 2 people with orders")
            assertEquals(listOf("alice", "bob"), names)
        }
    }

    @Test
    fun testSubqueryInLeftJoinClause() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(id = 1, userName = "alice", product = "Laptop", cost = 1000)
        }

        // Subquery for order counts
        withConnection {
            // Inline subquery with .aliasAs<T>() API in left join
            val queryBuilder = from(Person)
                .leftJoinAliased(
                    from(Order)
                        .selectAs(OrderUserName) { order.userName }
                        .selectAs(OrderCount) { count(order.id) }
                        .groupBy { order.userName }
                        .build()
                        .aliasAs<OrderCounts>()
                ) { person.name eq orderCounts.orderUserName }
                .selectAll(Person)
                .selectAll(OrderCounts)  // Direct parameter - no lambda!

            val results = queryBuilder.execute(this)
            val resultList: List<Pair<String, Int?>> = results.asSequence().map { row ->
                val name = row.person.name as String
                val count = row.orderCounts.orderCount as? Number
                Pair(name, count?.toInt())
            }.sortedBy { it.first }.toList()

            assertEquals(3, resultList.size, "Should have all 3 people")
            // alice has orders, others don't
            assertEquals("alice", resultList[0].first)
            val aliceCount: Int? = resultList[0].second
            assertTrue(aliceCount != null && aliceCount > 0)
        }
    }

    // ========== Scalar subquery tests ==========

    @Test
    fun testScalarSubqueryInWhere() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(id = 1, userName = "alice", product = "Laptop", cost = 1000)
            order(id = 2, userName = "bob", product = "Keyboard", cost = 100)
            order(id = 3, userName = "charlie", product = "Monitor", cost = 500)
        }

        withConnection {
            // Create scalar subquery for average cost
            val avgCostQuery = from(Order)
                .selectAs(AvgCost) { avg(order.cost) }
                .build()

            // Find orders more expensive than average
            val queryBuilder = from(Order)
                .selectAll(Order)
                .where { order.cost gt scalarSubquery(avgCostQuery) }

            val sql = queryBuilder.build().sql()
            println("Scalar subquery SQL: $sql")
            assertTrue(sql.contains("WHERE"), "Should have WHERE clause")
            assertTrue(sql.contains("SELECT"), "Should have nested SELECT")

            val results = queryBuilder.execute(this)
            val products = results.map { it.order.product as String }.toList().sorted()

            // Average is 533.33, so Laptop (1000) is above average
            assertEquals(1, products.size, "Should have 1 order above average")
            assertEquals("Laptop", products[0])
        }
    }

    @Test
    @Ignore("Requires correlated subquery support - referencing outer query's person.name from inner query")
    fun testScalarSubqueryComparingDifferentTables() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            order(id = 1, userName = "alice", product = "Laptop", cost = 25)  // Cost matches alice's age
            order(id = 2, userName = "bob", product = "Keyboard", cost = 30)  // Cost matches bob's age
            order(id = 3, userName = "charlie", product = "Mouse", cost = 50)
        }

        withConnection {
            // Find people whose age matches an order cost
            // REQUIRES CORRELATED SUBQUERY SUPPORT
            val orderCostQuery = from(Order)
                .selectAs(OrderCost) { order.cost }
                //.where { order.userName eq person.name }  // Correlated - references outer table
                .build()

            val queryBuilder = from(Person)
                .selectAll(Person)
                .where { person.age eq scalarSubquery(orderCostQuery) }

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()

            // alice and bob have matching ages
            assertEquals(2, names.size, "Should have 2 people with matching ages")
            assertEquals(listOf("alice", "bob"), names)
        }
    }

    // ========== EXISTS tests ==========

    @Test
    @Ignore("Requires correlated subquery support - referencing outer query's person.name from inner query")
    fun testExistsOperator() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(id = 1, userName = "alice", product = "Laptop", cost = 1000)
            order(id = 2, userName = "bob", product = "Keyboard", cost = 100)
        }

        withConnection {
            // Find people who have at least one order
            // REQUIRES CORRELATED SUBQUERY SUPPORT
            val hasOrdersQuery = from(Order)
                .selectAs(OrderProduct) { order.id }
                //.where { order.userName eq person.name }  // Correlated - references outer table
                .build()

            val queryBuilder = from(Person)
                .selectAll(Person)
                .where { exists(hasOrdersQuery) }

            val sql = queryBuilder.build().sql()
            println("EXISTS SQL: $sql")
            assertTrue(sql.contains("EXISTS"), "Should have EXISTS keyword")

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()

            assertEquals(2, names.size, "Should have 2 people with orders")
            assertEquals(listOf("alice", "bob"), names)
        }
    }

    @Test
    @Ignore("Requires correlated subquery support - referencing outer query's person.name from inner query")
    fun testNotExistsOperator() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(id = 1, userName = "alice", product = "Laptop", cost = 1000)
        }

        withConnection {
            // Find people who have NO orders
            // REQUIRES CORRELATED SUBQUERY SUPPORT
            val hasOrdersQuery = from(Order)
                .selectAs(OrderProduct) { order.id }
                //.where { order.userName eq person.name }  // Correlated - references outer table
                .build()

            val queryBuilder = from(Person)
                .selectAll(Person)
                .where { notExists(hasOrdersQuery) }

            val sql = queryBuilder.build().sql()
            println("NOT EXISTS SQL: $sql")
            assertTrue(sql.contains("NOT EXISTS"), "Should have NOT EXISTS keywords")

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()

            assertEquals(2, names.size, "Should have 2 people without orders")
            assertEquals(listOf("bob", "charlie"), names)
        }
    }

    @Test
    @Ignore("Requires correlated subquery support - referencing outer query's person.name from inner query")
    fun testExistsWithAdditionalConditions() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            order(id = 1, userName = "alice", product = "Laptop", cost = 1000)
            order(id = 2, userName = "bob", product = "Keyboard", cost = 100)
            order(id = 3, userName = "charlie", product = "Monitor", cost = 200)
        }

        withConnection {
            // Find people who have expensive orders (cost > 500)
            // REQUIRES CORRELATED SUBQUERY SUPPORT
            val hasExpensiveOrdersQuery = from(Order)
                .selectAs(OrderProduct) { order.id }
                //.where { (order.userName eq person.name) and (order.cost gt 500) }  // Correlated - references outer table
                .where { order.cost gt 500 }  // Non-correlated version for compilation
                .build()

            val results = from(Person)
                .selectAll(Person)
                .where { exists(hasExpensiveOrdersQuery) }
                .execute(this)

            val names = results.map { it.person.name }.toList()

            assertEquals(1, names.size, "Should have 1 person with expensive orders")
            assertEquals("alice", names[0])
        }
    }
}
