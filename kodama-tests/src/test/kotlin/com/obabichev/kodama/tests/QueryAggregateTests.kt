package com.obabichev.kodama.tests

import com.obabichev.kodama.query.query
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Order
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class QueryAggregateTests : PostgresBaseTest() {
    // All tests now use the new select_xxx() API with named accessors

    @Test
    fun testSingleAggregateWithNamedAccessor() {
        // 🚀 NEW API: Named selection with type-safe named accessor!
        // Alias is automatically inferred from method name - no boilerplate!
        withConnection {
            val results = query()
                .from(Order)
                .select_totalRevenue { sum(order.cost) }
                .execute(this)

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
        // 🚀 NEW API: Chained named selections with type-safe named accessors!
        // Aliases are automatically inferred from method names!
        withConnection {
            val results = query()
                .from(Order)
                .select_totalRevenue { sum(order.cost) }
                .select_orderCount { count(order.id) }
                .execute(this)

            val row = results.first()

            // 🎉 Named accessors - stable to reordering, intuitive!
            val totalRevenue: Number = row.totalRevenue
            val orderCount: Number = row.orderCount

            assertNotNull(totalRevenue, "totalRevenue should not be null")
            assertNotNull(orderCount, "orderCount should not be null")
            assertTrue(totalRevenue.toInt() > 0, "Expected positive total revenue")
            assertTrue(orderCount.toLong() > 0, "Expected at least 1 order")

            // 🎉 This would be a COMPILE ERROR if uncommented:
            // row.averageOrderValue  // ❌ Compile error! Not selected
        }
    }

    @Test
    fun testMixedColumnAndAggregateSelection() {
        // 🚀 Mix regular columns with aggregate functions!
        // Automatically generates GROUP BY for selected columns
        withConnection {
            val builder = query()
                .from(Order)
                .select { order.cost }
                .select_orderCount { count(order.id) }

            val query = builder.build()

            // Verify SQL includes both column and aggregate with GROUP BY
            val sql = query.sql()
            println("Generated SQL: $sql")
            assertTrue(sql.contains("cost"), "SQL should select cost column")
            assertTrue(sql.contains("COUNT("), "SQL should have COUNT aggregate")
            assertTrue(sql.contains("GROUP BY"), "SQL should have GROUP BY clause")
            assertTrue(sql.contains("\"order\".cost"), "GROUP BY should include cost column")

            val results = builder.execute(this)
            val row = results.first()

            // Verify we can access both column and aggregate
            val cost = row.order.cost
            val orderCount = row.orderCount

            assertNotNull(cost, "cost should not be null")
            assertNotNull(orderCount, "orderCount should not be null")
            assertTrue(cost > 0, "Expected positive cost")
            assertTrue(orderCount.toLong() > 0, "Expected at least 1 order")
        }
    }
}
