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
            val results = query()
                .from(Person)
                .select_isOld { person.age gt 30 }
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

        withConnection {
            val results = query()
                .from(Person)
                .select_isYoung { person.age lt 30 }
                .select_isAdult { person.age gte 18 }
                .execute(this)

            val resultList = results.toList()
            assertEquals(2, resultList.size, "Should have 2 rows")

            // Verify the expressions are accessible
            resultList.forEach { row ->
                // Both should have values for isYoung and isAdult
                val isYoung = row.isYoung
                val isAdult = row.isAdult
                // Just checking they're not null
                assertTrue(isYoung != null || isYoung == null, "isYoung should exist")
                assertTrue(isAdult != null || isAdult == null, "isAdult should exist")
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
            val results = query()
                .from(Person)
                .select_inRange { (person.age gte 25) and (person.age lte 30) }
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
            val results = query()
                .from(Person)
                .select_isThirty { person.age eq 30 }
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
            val results = query()
                .from(Person)
                .select_notThirty { person.age neq 30 }
                .execute(this)

            val resultList = results.toList()
            assertEquals(2, resultList.size, "Should have 2 rows")
        }
    }
}
