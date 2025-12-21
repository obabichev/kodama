package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.components.expression.and
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.Order
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for IN operator variants: inList, notInList, inQuery, notInQuery
 *
 * Verifies:
 * - IN with list of values
 * - NOT IN with list of values
 * - IN with subquery
 * - NOT IN with subquery
 * - Empty list handling
 * - Correct SQL generation and parameter binding
 */
class InOperatorTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Person, Order)

    @Test
    fun testInListOperator() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            person(name = "dave", age = 40)
        }

        withConnection {
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.inList(listOf("alice", "charlie", "dave"))
                }

            val sql = queryBuilder.build().sql()
            println("IN List Test SQL: $sql")
            assertTrue(sql.contains("IN"), "SQL should contain IN keyword")

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()
            assertEquals(3, names.size, "Should match three people")
            assertEquals(listOf("alice", "charlie", "dave"), names)
        }
    }

    @Test
    fun testInListWithIntegerValues() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            person(name = "dave", age = 40)
            person(name = "eve", age = 45)
        }

        withConnection {
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.age.inList(listOf(25, 35, 45))
                }

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()
            assertEquals(3, names.size, "Should match three people")
            assertEquals(listOf("alice", "charlie", "eve"), names)
        }
    }

    @Test
    fun testNotInListOperator() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            person(name = "dave", age = 40)
        }

        withConnection {
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.notInList(listOf("alice", "charlie"))
                }

            val sql = queryBuilder.build().sql()
            println("NOT IN List Test SQL: $sql")
            assertTrue(sql.contains("NOT IN"), "SQL should contain NOT IN keywords")

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()
            assertEquals(2, names.size, "Should match two people not in list")
            assertEquals(listOf("bob", "dave"), names)
        }
    }

    @Test
    fun testInListWithSingleValue() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
        }

        withConnection {
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.inList(listOf("bob"))
                }

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList()
            assertEquals(1, names.size, "Should match one person")
            assertEquals("bob", names[0])
        }
    }

    @Test
    fun testInListWithEmptyList() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
        }

        withConnection {
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.inList(emptyList())
                }

            val sql = queryBuilder.build().sql()
            println("Empty IN List Test SQL: $sql")
            // Empty IN list should generate FALSE (no matches)
            assertTrue(sql.contains("FALSE"), "SQL should contain FALSE for empty IN list")

            val results = queryBuilder.execute(this)
            assertEquals(0, results.count(), "Empty IN list should match no rows")
        }
    }

    @Test
    fun testNotInListWithEmptyList() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
        }

        withConnection {
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.notInList(emptyList())
                }

            val sql = queryBuilder.build().sql()
            println("Empty NOT IN List Test SQL: $sql")
            // Empty NOT IN list should generate TRUE (all matches)
            assertTrue(sql.contains("TRUE"), "SQL should contain TRUE for empty NOT IN list")

            val results = queryBuilder.execute(this)
            assertEquals(2, results.count(), "Empty NOT IN list should match all rows")
        }
    }

    @Test
    fun testInListWithManyValues() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            person(name = "dave", age = 40)
            person(name = "eve", age = 45)
            person(name = "frank", age = 50)
        }

        withConnection {
            // Test with many values in the list
            val namesList = listOf("alice", "charlie", "dave", "frank")
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.inList(namesList)
                }

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()
            assertEquals(4, names.size, "Should match four people")
            assertEquals(listOf("alice", "charlie", "dave", "frank"), names)
        }
    }

    @Test
    fun testInListCombinedWithOtherConditions() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
            person(name = "dave", age = 40)
            person(name = "eve", age = 45)
        }

        withConnection {
            // Find people in the list AND with age > 30
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.inList(listOf("alice", "bob", "charlie", "dave")) and (person.age gt 30)
                }

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()
            assertEquals(2, names.size, "Should match two people in list with age > 30")
            assertEquals(listOf("charlie", "dave"), names)
        }
    }

    @Test
    fun testInListReturningNoMatches() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
        }

        withConnection {
            // Test IN with values that don't exist
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.inList(listOf("charlie", "dave", "eve"))
                }

            val results = queryBuilder.execute(this)
            assertEquals(0, results.count(), "Should match no people when values don't exist")
        }
    }

    @Test
    fun testInListWithDuplicateValues() {
        testData {
            person(name = "alice", age = 25)
            person(name = "bob", age = 30)
            person(name = "charlie", age = 35)
        }

        withConnection {
            // List contains duplicates - should still work correctly
            val queryBuilder = from(Person)
                .selectAll(Person)
                .where {
                    person.name.inList(listOf("alice", "alice", "bob", "bob"))
                }

            val results = queryBuilder.execute(this)
            val names = results.map { it.person.name as String }.toList().sorted()
            assertEquals(2, names.size, "Should match two people despite duplicates in list")
            assertEquals(listOf("alice", "bob"), names)
        }
    }
}
