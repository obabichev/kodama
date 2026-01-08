package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.TradingStrategy
import com.obabichev.kodama.tests.schema.MarketData
import com.obabichev.kodama.tests.schema.generated.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for PascalCase table naming convention.
 * Validates that table names like TradingStrategy and MarketData:
 * 1. Are preserved in PascalCase in generated class names
 * 2. Use camelCase for property names (tradingStrategy, marketData)
 * 3. Work correctly in joins, selects, where clauses, etc.
 */
class PascalCaseNamingTests : DatabaseTest() {
    override fun requiredTables(): List<Table> = listOf(TradingStrategy, MarketData)

    @Test
    fun `test PascalCase table name - simple select`() {
        testData {
            tradingStrategy(1, "Momentum Strategy", "Buy on momentum signals")
        }

        withConnection {
            val results = from(TradingStrategy)
                .selectAll(TradingStrategy)
                .where {
                    tradingStrategy.id eq 1
                }
                .execute(this)

            val row = results.first()

            // Verify property name is camelCase
            assertEquals(1, row.tradingStrategy.id)
            assertEquals("Momentum Strategy", row.tradingStrategy.strategyName)
            assertEquals("Buy on momentum signals", row.tradingStrategy.description)
        }
    }

    @Test
    fun `test PascalCase table name - nullable column`() {
        testData {
            tradingStrategy(2, "Mean Reversion", null)
        }

        withConnection {
            val results = from(TradingStrategy)
                .selectAll(TradingStrategy)
                .where {
                    tradingStrategy.id eq 2
                }
                .execute(this)

            val row = results.first()

            assertEquals(2, row.tradingStrategy.id)
            assertEquals("Mean Reversion", row.tradingStrategy.strategyName)
            assertEquals(null, row.tradingStrategy.description)
        }
    }

    @Test
    fun `test PascalCase table names - join query`() {
        testData {
            tradingStrategy(1, "Momentum Strategy", "Buy on momentum signals")
            marketData(1, 1, "2025-01-01T10:00:00", 15000)
            marketData(2, 1, "2025-01-01T11:00:00", 15200)
        }

        withConnection {
            val results = from(TradingStrategy)
                .join(MarketData) {
                    marketData.strategyId eq tradingStrategy.id
                }
                .selectAll(TradingStrategy)
                .selectAll(MarketData)
                .where {
                    tradingStrategy.id eq 1
                }
                .execute(this)

            var count = 0
            for (row in results) {
                if (count == 0) {
                    // Verify both table accessors use camelCase
                    assertEquals("Momentum Strategy", row.tradingStrategy.strategyName)
                    assertEquals(1, row.marketData.strategyId)
                    assertEquals(15000, row.marketData.price)
                }
                count++
            }

            assertEquals(2, count)
        }
    }

    @Test
    fun `test PascalCase table names - column selection`() {
        testData {
            tradingStrategy(1, "Momentum Strategy", "Buy on momentum signals")
        }

        withConnection {
            val results = from(TradingStrategy)
                .selectAll(TradingStrategy)
                .where {
                    tradingStrategy.id eq 1
                }
                .execute(this)

            val row = results.first()

            // Verify property name is camelCase
            assertEquals("Momentum Strategy", row.tradingStrategy.strategyName)
        }
    }

    @Test
    fun `test PascalCase table names - where clause`() {
        testData {
            tradingStrategy(1, "Momentum Strategy", "Buy on momentum signals")
            tradingStrategy(3, "Different Strategy", "Something else")
        }

        withConnection {
            val results = from(TradingStrategy)
                .selectAll(TradingStrategy)
                .where {
                    tradingStrategy.strategyName eq "Momentum Strategy"
                }
                .execute(this)

            var count = 0
            var firstRow: Any? = null
            for (row in results) {
                if (count == 0) {
                    firstRow = row
                    assertEquals("Momentum Strategy", row.tradingStrategy.strategyName)
                }
                count++
            }
            assertEquals(1, count)
        }
    }

    @Test
    fun `test PascalCase table names - order by`() {
        testData {
            tradingStrategy(1, "Momentum Strategy", "Buy on momentum signals")
            tradingStrategy(2, "Mean Reversion", null)
        }

        withConnection {
            val results = from(TradingStrategy)
                .selectAll(TradingStrategy)
                .orderBy { tradingStrategy.id.desc() }
                .execute(this)

            var count = 0
            for (row in results) {
                if (count == 0) {
                    assertEquals(2, row.tradingStrategy.id)
                } else if (count == 1) {
                    assertEquals(1, row.tradingStrategy.id)
                }
                count++
            }

            assertEquals(2, count)
        }
    }

    @Test
    fun `test PascalCase - insert statement`() {
        withConnection {
            val result = TradingStrategy.insert(
                transaction = this,
                id = 999,
                strategyName = "Test Strategy",
                description = "Test description"
            )

            assertEquals(1, result.rowsAffected)

            // Verify it was inserted
            val results = from(TradingStrategy)
                .selectAll(TradingStrategy)
                .where {
                    tradingStrategy.id eq 999
                }
                .execute(this)

            val row = results.first()
            assertEquals("Test Strategy", row.tradingStrategy.strategyName)
        }
    }

    @Test
    fun `test PascalCase - generated accessor classes exist`() {
        testData {
            tradingStrategy(1, "Momentum Strategy", "Buy on momentum signals")
        }

        withConnection {
            val results = from(TradingStrategy)
                .selectAll(TradingStrategy)
                .execute(this)

            val row = results.first()

            // Verify the accessor class name is PascalCase
            val accessor = row.tradingStrategy
            assertNotNull(accessor)

            // The class should be named TradingStrategyResultAccessor_All_NonNull (not tradingstrategyResultAccessor_All_NonNull)
            // Note: Single-table queries (no joins) use _NonNull variant for non-nullable accessors
            val className = accessor::class.simpleName
            assertEquals("TradingStrategyResultAccessor_All_NonNull", className)
        }
    }
}
