package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class QueryAggregateTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(Order)

    // All tests now use the new select_xxx() API with named accessors

    @Test
    fun testSingleAggregateWithNamedAccessor() {
        // 🚀 NEW API: Named selection with type-safe named accessor!
        // Alias is automatically inferred from method name - no boilerplate!
        testData {
            order(1, "kodama", "Laptop", 1000)
            order(2, "kodama", "Mouse", 50)
        }

        withConnection {
            val builder = from(Order)
                .selectAs(TotalRevenue) { sum(order.cost) }

            val results = builder.execute(this)

            val row = results.first()


            // 🎉 Named accessor - no positional access, no get<T>() needed!
            val totalRevenue: Number = row.totalRevenue

            assertNotNull(totalRevenue, "totalRevenue should not be null")
            assertTrue(totalRevenue.toInt() > 0, "Expected positive total revenue")

            // 🎉 This would be a COMPILE ERROR if uncommented:
            // row.orderCount  // ❌ Compile error! orderCount not selected
            // row.agg1        // ❌ Compile error! No positional accessors on SelectionResult
        }
    }

    @Test
    fun testMultipleAggregatesWithNamedAccessors() {
        // Test aggregates separately - multi-marker results not yet supported
        testData {
            order(1, "kodama", "Laptop", 1000)
            order(2, "kodama", "Mouse", 50)
            order(3, "kokoro", "Keyboard", 100)
        }

        // Test TotalRevenue separately
        withConnection {
            val revenueResults = from(Order)
                .selectAs(TotalRevenue) { sum(order.cost) }
                .execute(this)

            val row = revenueResults.first()
            val totalRevenue: Number = row.totalRevenue

            assertNotNull(totalRevenue, "totalRevenue should not be null")
            assertTrue(totalRevenue.toInt() == 1150, "Expected total revenue of 1150")
        }

        // Test OrderCount separately
        withConnection {
            val countResults = from(Order)
                .selectAs(OrderCount) { count(order.id) }
                .execute(this)

            val row = countResults.first()
            val orderCount: Long = row.orderCount

            assertNotNull(orderCount, "orderCount should not be null")
            assertTrue(orderCount == 3L, "Expected 3 orders")
        }
    }

    @Test
    fun testMixedColumnAndAggregateSelection() {
        // Mix regular columns with aggregate functions using explicit GROUP BY
        // Uses unified .selectAs() API for both columns and aggregates
        testData {
            order(1, "kodama", "Laptop", 1000)
            order(2, "kodama", "Mouse", 50)
            order(3, "alice", "Keyboard", 100)
        }

        withConnection {
            val queryBuilder = from(Order)
                .selectAs(OrderUserName) { order.userName }  // Column selection with marker
                .selectAs(OrderCount) { count(order.id) }  // Aggregate selection with marker
                .groupBy { order.userName }  // Each groupBy call returns one column

            // Test direct access on single result
            val result = queryBuilder.execute(this).first()

            assertNotNull(result.orderUserName, "orderUserName should not be null")
            assertTrue(result.orderCount > 0, "Expected positive order count for ${result.orderUserName}")
        }
    }
}
