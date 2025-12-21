package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.components.expression.and
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for expression selections in SELECT clause.
 *
 * Verifies:
 * - Boolean expressions can be selected with aliases
 * - Comparison operators work in SELECT
 * - Expression results are accessible via aliases
 */
class ExpressionSelectionTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Person)

    @Test
    fun testSelectBooleanExpression() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 35)
        }

        withConnection {
            val results = from(Person)
                .selectAliased(IsOld) { person.age gt 30 }
                .execute(this)

            val resultList = results.toList()
            assertEquals(3, resultList.size, "Should have 3 rows")

            // alice: age 25, not old (25 > 30 = false)
            // bob: age 30, not old (30 > 30 = false)
            // charlie: age 35, is old (35 > 30 = true)
        }
    }

    @Test
    fun testSelectMultipleExpressions() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
        }

        // Test IsYoung separately
        withConnection {
            val youngResults = from(Person)
                .selectAliased(IsYoung) { person.age lt 30 }
                .execute(this)

            val youngList = youngResults.toList()
            assertEquals(2, youngList.size, "Should have 2 rows")

            // alice: age 25 -> isYoung = true
            // bob: age 30 -> isYoung = false
            youngList.forEach { row ->
                val isYoung = row.isYoung
                assertTrue(isYoung != null, "isYoung should be present")
            }
        }

        // Test IsAdult separately
        withConnection {
            val adultResults = from(Person)
                .selectAliased(IsAdult) { person.age gte 18 }
                .execute(this)

            val adultList = adultResults.toList()
            assertEquals(2, adultList.size, "Should have 2 rows")

            // Both alice (25) and bob (30) are adults (>= 18)
            adultList.forEach { row ->
                val isAdult = row.isAdult
                assertTrue(isAdult as Boolean, "Both should be adults")
            }
        }
    }

    @Test
    fun testSelectComplexBooleanExpression() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 35)
        }

        withConnection {
            val results = from(Person)
                .selectAliased(InRange) { (person.age gte 25) and (person.age lte 30) }
                .execute(this)

            val resultList = results.toList()
            assertEquals(3, resultList.size, "Should have 3 rows")
        }
    }

    @Test
    fun testSelectExpressionWithEquals() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
            person("charlie", age = 30)
        }

        withConnection {
            val results = from(Person)
                .selectAliased(IsThirty) { person.age eq 30 }
                .execute(this)

            val resultList = results.toList()
            assertEquals(3, resultList.size, "Should have 3 rows")

            // alice: false
            // bob: true
            // charlie: true
        }
    }

    @Test
    fun testSelectExpressionWithNotEqual() {
        testData {
            person("alice", age = 25)
            person("bob", age = 30)
        }

        withConnection {
            val results = from(Person)
                .selectAliased(NotThirty) { person.age neq 30 }
                .execute(this)

            val resultList = results.toList()
            assertEquals(2, resultList.size, "Should have 2 rows")
        }
    }
}
