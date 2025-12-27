package com.obabichev.kodama.tests

import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.infrastructure.TestFixtures
import com.obabichev.kodama.tests.schema.*
import kotlin.test.BeforeTest

/**
 * Base class for PostgreSQL tests using the standard test data.
 *
 * Migration Note: This class now extends DatabaseTest which provides:
 * - Fast schema management (create once per test class)
 * - Data cleanup between tests
 * - Type-safe data insertion DSL
 *
 * Tests extending this class automatically get the FULL_DATASET fixture loaded before each test.
 * This maintains backward compatibility with existing tests.
 *
 * For new tests, consider:
 * 1. Extending DatabaseTest directly
 * 2. Using specific fixtures via useFixture(TestFixtures.BASIC_ECOMMERCE)
 * 3. Using testData {} for custom data
 */
open class PostgresBaseTest : DatabaseTest() {

    /**
     * PostgresBaseTest uses FULL_DATASET which includes all tables.
     * Subclasses can override this to specify a subset if they don't need all tables.
     */
    override fun requiredTables(): List<Table> = listOf(Person, Order, Profile, Company, Product)

    /**
     * Load standard test data before each test.
     * This ensures backward compatibility with existing tests that expect this data.
     *
     * Note: Individual tests can override this behavior by:
     * - Not calling super.loadStandardTestData() in their own @BeforeTest
     * - Or by extending DatabaseTest directly instead of PostgresBaseTest
     */
    @BeforeTest
    fun loadStandardTestData() {
        super.setupTest()  // Call parent's setup first
        useFixture(TestFixtures.FULL_DATASET)
    }
}
