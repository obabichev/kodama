package com.obabichev.kodama.tests.infrastructure

import com.obabichev.kodama.execute.JdbcTransaction
import com.obabichev.kodama.schema.Table
import kotlin.test.BeforeTest

/**
 * Base class for database tests with optimized, per-class table management.
 *
 * Each test class declares which tables it needs, and only those tables are created.
 * This makes tests faster and more isolated.
 *
 * Schema Lifecycle:
 * - First test in class: Create only required tables
 * - @BeforeTest: Truncate data in required tables
 *
 * Usage:
 * ```kotlin
 * class MyTest : DatabaseTest() {
 *     override fun requiredTables() = listOf(Person, Order)
 *
 *     @Test
 *     fun testSomething() {
 *         testData {
 *             person("kodama", age = 1)
 *             order(1, "kodama", "Laptop", 1000)
 *         }
 *
 *         withConnection {
 *             val results = from(Person).execute(this)
 *             // assertions...
 *         }
 *     }
 * }
 * ```
 */
abstract class DatabaseTest {

    companion object {
        private const val DB_URL = "jdbc:postgresql://localhost:5454/kodama"
        private const val DB_USER = "kodama"
        private const val DB_PASSWORD = "kodama"

        // Track which test classes have initialized their schemas
        private val initializedClasses = mutableSetOf<String>()

        /**
         * Initialize schema for a specific test class.
         */
        private fun ensureSchemaForClass(className: String, tables: List<Table>) {
            if (!initializedClasses.contains(className)) {
                synchronized(initializedClasses) {
                    if (!initializedClasses.contains(className)) {
                        println("[$className] Ensuring tables exist: ${tables.joinToString(", ") { it.tableName }}")
                        try {
                            withStaticConnection {
                                tables.forEach { table ->
                                    val sql = table.toCreateTableSQL()
                                    executeUpdate(sql)
                                    // CREATE TABLE IF NOT EXISTS won't error if table exists
                                }
                            }
                            initializedClasses.add(className)
                            println("[$className] Tables ready")
                        } catch (e: Exception) {
                            println("[$className] Failed to initialize tables: ${e.message}")
                            throw e
                        }
                    }
                }
            }
        }

        /**
         * Helper for static (companion) context operations.
         */
        private fun <T> withStaticConnection(block: JdbcTransaction.() -> T): T {
            val transaction = JdbcTransaction(DB_URL, DB_USER, DB_PASSWORD)
            try {
                val result = block(transaction)
                transaction.commit()
                return result
            } catch (e: Exception) {
                transaction.rollback()
                throw e
            } finally {
                transaction.close()
            }
        }
    }

    /**
     * Override this method to declare which tables your test class needs.
     * Only these tables will be created and cleaned.
     *
     * Example:
     * ```kotlin
     * override fun requiredTables() = listOf(Person, Order)
     * ```
     */
    protected abstract fun requiredTables(): List<Table>

    /**
     * Clean data before each test.
     * Also ensures schema is initialized on first test.
     */
    @BeforeTest
    fun setupTest() {
        val tables = requiredTables()
        val className = this::class.simpleName ?: "Unknown"

        // Ensure schema exists for required tables (lazy initialization)
        ensureSchemaForClass(className, tables)

        // Clean data in required tables only
        withConnection {
            // Truncate in reverse order to respect foreign keys
            tables.reversed().forEach { table ->
                try {
                    val quotedTableName = if (table.tableName == "order") {
                        "\"${table.tableName}\""
                    } else {
                        table.tableName
                    }
                    executeUpdate("TRUNCATE TABLE $quotedTableName CASCADE")
                } catch (e: Exception) {
                    // Table might not have data yet - that's OK
                }
            }
        }
    }

    /**
     * Execute database operations within a transaction.
     * Automatically commits on success, rolls back on exception.
     */
    protected fun <T> withConnection(block: JdbcTransaction.() -> T): T {
        val transaction = JdbcTransaction(DB_URL, DB_USER, DB_PASSWORD)
        try {
            val result = block(transaction)
            transaction.commit()
            return result
        } catch (e: Exception) {
            transaction.rollback()
            throw e
        } finally {
            transaction.close()
        }
    }

    /**
     * Insert test data using type-safe DSL.
     */
    protected fun testData(block: TestDataBuilder.() -> Unit) {
        withConnection {
            TestDataBuilder(this).apply(block)
        }
    }

    /**
     * Load one or more predefined fixtures.
     */
    protected fun useFixture(vararg fixtures: TestFixture) {
        withConnection {
            val builder = TestDataBuilder(this)
            fixtures.forEach { it.load(builder) }
        }
    }
}
