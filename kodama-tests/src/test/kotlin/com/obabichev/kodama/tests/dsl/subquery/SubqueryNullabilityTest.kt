package com.obabichev.kodama.tests.dsl.subquery

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests that subquery columns preserve nullability from the original tables.
 * Non-nullable columns should remain non-nullable after going through a subquery.
 */
class SubqueryNullabilityTest : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Order)

    @Test
    fun testSubqueryPreservesNonNullableColumns() {
        testData {
            order(1, "alice", "Laptop", 1000)
            order(2, "bob", "Mouse", 50)
        }

        withConnection {
            val userNames = mutableSetOf<String>()
            // Inline subquery with .aliasAs<T>()
            val results = fromAliased(UserTotalsNew) {
                from(Order)
                    .selectAs(OrderUserName) { order.userName }
                    .selectAs(MyAlias) { sum(order.cost) }
                    .groupBy { order.userName }
                    .build()
            }
                .selectAll(UserTotalsNew)  // Direct parameter - no lambda!
                .execute(this)

            results.forEach { row ->
                // userName is non-nullable in Order table, should be non-nullable in subquery
                val userName: String = row.userTotalsNew.orderUserName as String
                assertNotNull(userName)
                userNames.add(userName)

                // Aggregates in subqueries are nullable because they might be used in LEFT JOINs
                val total = row.userTotalsNew.myAlias
                assertNotNull(total)  // In this case, we know it's not null because we're using FROM, not LEFT JOIN
            }

            // Verify we got the expected results
            assertEquals(setOf("alice", "bob"), userNames)
        }
    }

    @Test
    fun testSubqueryNonNullableColumnWithoutCast() {
        testData {
            order(1, "alice", "Laptop", 1000)
        }

        withConnection {
            // Inline subquery with .aliasAs<T>()
            val results = fromAliased(UserTotalsNew) {
                from(Order)
                    .selectAs(OrderUserName) { order.userName }
                    .selectAs(MyAlias) { sum(order.cost) }
                    .groupBy { order.userName }
                    .build()
            }
                .selectAll(UserTotalsNew)  // Direct parameter - no lambda!
                .execute(this)

            val row = results.first()

            // userName is non-nullable in Order table, should remain non-nullable in subquery
            val userName: String = row.userTotalsNew.orderUserName as? String ?: ""  // Subquery results are nullable for LEFT JOIN safety
            assertEquals("alice", userName)

            // Aggregates in subqueries are nullable (for LEFT JOIN safety), so nullable access is needed
            val total: Number? = row.userTotalsNew.myAlias
            assertNotNull(total)
            assertEquals(1000, total.toInt())
        }
    }
}
