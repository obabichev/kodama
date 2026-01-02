package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.components.expression.and
import com.obabichev.kodama.components.expression.not
import com.obabichev.kodama.components.expression.or
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for boolean expression operators: and, or, not
 *
 * Verifies:
 * - AND operator combines conditions correctly
 * - OR operator creates alternative conditions
 * - NOT operator negates conditions
 * - Operators generate correct SQL
 * - Complex combinations work properly
 */
class BooleanExpressionTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Person, Order)

    @Test
    fun testAndOperatorCombinesTwoConditions() {
        // Test that AND combines two conditions
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 25)
            order(1, "alice", "Laptop", 1000)
            order(2, "bob", "Mouse", 50)
            order(3, "charlie", "Keyboard", 100)
        }

        withConnection {
            val queryBuilder = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
                .where {
                    (person.age eq 25) and (order.cost eq 1000)
                }

            val sql = queryBuilder.build().sql()
            println("AND Test SQL: $sql")
            assertTrue(sql.contains("AND"), "SQL should contain AND operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                assertEquals("alice", row.person.name)
                assertEquals(25, row.person.age)
                assertEquals(1000, row.order.cost)
            }
            assertEquals(1, count, "Should match exactly one row (alice with age 25 AND cost 1000)")
        }
    }

    @Test
    fun testOrOperatorCreatesAlternatives() {
        // Test that OR creates alternative conditions
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "bob", "Mouse", 50)
        }

        withConnection {
            val queryBuilder = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
                .where {
                    (person.name eq "alice") or (person.name eq "bob")
                }

            val sql = queryBuilder.build().sql()
            println("OR Test SQL: $sql")
            assertTrue(sql.contains("OR"), "SQL should contain OR operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val name = row.person.name as String
                assertTrue(name == "alice" || name == "bob", "Name should be alice or bob")
            }
            assertEquals(2, count, "Should match two rows (alice OR bob)")
        }
    }

    @Test
    fun testNotOperatorNegatesCondition() {
        // Test that NOT negates a condition
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 35)
            order(1, "alice", "Laptop", 1000)
            order(2, "bob", "Mouse", 50)
            order(3, "charlie", "Keyboard", 100)
        }

        withConnection {
            val queryBuilder = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
                .where {
                    not(person.age eq 30)
                }

            val sql = queryBuilder.build().sql()
            println("NOT Test SQL: $sql")
            assertTrue(sql.contains("NOT"), "SQL should contain NOT operator")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val age = row.person.age as Int
                assertTrue(age != 30, "Age should not be 30")
            }
            assertEquals(2, count, "Should match two rows (NOT age 30)")
        }
    }

    @Test
    fun testComplexAndOrCombination() {
        // Test complex combinations: (A AND B) OR (C AND D)
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Mouse", 50)
            order(3, "bob", "Keyboard", 100)
            order(4, "bob", "Monitor", 500)
        }

        withConnection {
            val queryBuilder = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
                .where {
                    ((person.name eq "alice") and (order.cost eq 1000)) or
                    ((person.name eq "bob") and (order.cost eq 500))
                }

            val sql = queryBuilder.build().sql()
            println("Complex AND/OR Test SQL: $sql")
            assertTrue(sql.contains("AND"), "SQL should contain AND")
            assertTrue(sql.contains("OR"), "SQL should contain OR")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val userName = row.person.name as String
                val cost = row.order.cost as Int
                val matches = (userName == "alice" && cost == 1000) || (userName == "bob" && cost == 500)
                assertTrue(matches, "Should match expected combinations")
            }
            assertEquals(2, count, "Should match two rows")
        }
    }

    @Test
    fun testNotWithAndOperator() {
        // Test NOT combined with AND
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 25)
            order(1, "alice", "Laptop", 1000)
            order(2, "bob", "Mouse", 50)
            order(3, "charlie", "Keyboard", 100)
        }

        withConnection {
            val queryBuilder = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
                .where {
                    not((person.name eq "alice") and (person.age eq 25))
                }

            val sql = queryBuilder.build().sql()
            println("NOT with AND Test SQL: $sql")
            assertTrue(sql.contains("NOT"), "SQL should contain NOT")
            assertTrue(sql.contains("AND"), "SQL should contain AND")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val name = row.person.name as String
                val age = row.person.age as Int
                // Should match everything except alice with age 25
                val shouldNotMatch = (name == "alice" && age == 25)
                assertTrue(!shouldNotMatch, "Should not match alice with age 25")
            }
            assertEquals(2, count, "Should match two rows (bob and charlie)")
        }
    }

    @Test
    fun testMultipleAndConditions() {
        // Test multiple AND conditions: A AND B AND C
        testData {
            person("alice", age = 25)
            person("bob", age = 25)
            person("charlie", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "alice", "Laptop", 900)
            order(3, "alice", "Mouse", 1000)
            order(4, "bob", "Laptop", 1000)
        }

        withConnection {
            val queryBuilder = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
                .where {
                    (person.name eq "alice") and
                            (person.age eq 25) and
                            (order.product eq "Laptop")
                }

            val sql = queryBuilder.build().sql()
            println("Multiple AND Test SQL: $sql")
            assertTrue(sql.contains("AND"), "SQL should contain AND")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                assertEquals("alice", row.person.name)
                assertEquals(25, row.person.age)
                assertEquals("Laptop", row.order.product)
            }
            // Should match alice(25) with both Laptop orders
            assertEquals(2, count, "Should match alice's laptop orders with age 25")
        }
    }

    @Test
    fun testOperatorPrecedence() {
        // Test that parentheses control precedence: A OR (B AND C)
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 30)
            order(1, "alice", "Laptop", 1000)
            order(2, "bob", "Mouse", 50)
            order(3, "charlie", "Keyboard", 100)
        }

        withConnection {
            // Should match: alice OR (age=30 AND product="Mouse")
            val queryBuilder = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
                .where {
                    (person.name eq "alice") or
                            ((person.age eq 30) and (order.product eq "Mouse"))
                }

            val sql = queryBuilder.build().sql()
            println("Precedence Test SQL: $sql")

            val results = queryBuilder.execute(this)
            var count = 0
            results.forEach { row ->
                count++
                val name = row.person.name as String
                val age = row.person.age as Int
                val product = row.order.product as String
                // Should match alice or (age 30 with Mouse product)
                val matches = (name == "alice") || (age == 30 && product == "Mouse")
                assertTrue(matches, "Should match alice or (age 30 with Mouse)")
            }
            assertEquals(2, count, "Should match alice's order and bob's mouse")
        }
    }

    @Test
    fun testSelectBooleanExpression() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 30)
        }

        withConnection {
            from(Person)
                .selectAs(IsOld) { person.age gt 100 }
                .execute(this)
        }
    }
}
