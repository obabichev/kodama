package com.obabichev.kodama.tests.infrastructure

/**
 * Predefined test data fixtures for common scenarios.
 *
 * Usage:
 * ```kotlin
 * class MyTest : DatabaseTest() {
 *     @Test
 *     fun testSomething() {
 *         useFixture(TestFixtures.BASIC_ECOMMERCE)
 *         // tables now contain standard test data
 *     }
 * }
 * ```
 *
 * Benefits:
 * - Reusable: Same data across multiple tests
 * - Consistent: Known starting state
 * - Composable: Can combine multiple fixtures
 * - Documented: Fixture names describe the scenario
 */
object TestFixtures {

    /**
     * Basic e-commerce scenario with 3 users, their orders, and profiles.
     *
     * Data:
     * - Person: kodama (age 1), kokoro (age 2), pipiru (age 2)
     * - Order: 3 orders (1 laptop for kodama, 1 mouse for kodama, 1 keyboard for kokoro)
     * - Profile: 3 profiles with emails and photos
     *
     * Use for: Basic query tests, joins, simple aggregations
     */
    val BASIC_ECOMMERCE = TestFixture("BASIC_ECOMMERCE") {
        // Users
        person("kodama", age = 1)
        person("kokoro", age = 2)
        person("pipiru", age = 2)

        // Orders
        order(1, "kodama", "Laptop", 1000)
        order(2, "kodama", "Mouse", 50)
        order(3, "kokoro", "Keyboard", 100)

        // Profiles
        profile("kodama", "kodama@example.com", "photo1.jpg")
        profile("kokoro", "kokoro@example.com", "photo2.jpg")
        profile("pipiru", "pipiru@example.com", "photo3.jpg")
    }

    /**
     * Company data with 3 companies.
     *
     * Data:
     * - Company: Acme Corp, Tech Solutions, Global Industries
     *
     * Use for: Company-related queries, testing company table in isolation
     */
    val COMPANIES = TestFixture("COMPANIES") {
        company(1, "Acme Corp")
        company(2, "Tech Solutions")
        company(3, "Global Industries")
    }

    /**
     * Product catalog with various null value combinations.
     *
     * Data:
     * - Product 1: Full data (description + discount)
     * - Product 2: Minimal data (nulls for description and discount)
     * - Product 3: Partial data (null discount only)
     * - Product 4: Partial data (null description only)
     *
     * Use for: Testing nullable columns, NULL handling in queries
     */
    val PRODUCT_CATALOG = TestFixture("PRODUCT_CATALOG") {
        product(1, "Laptop", "High-performance laptop", 1500, 10)
        product(2, "Mouse", null, 50, null)  // NULL description and discount
        product(3, "Keyboard", "Mechanical keyboard", 120, null)  // NULL discount only
        product(4, "Monitor", null, 300, 15)  // NULL description only
    }

    /**
     * Aggregate testing scenario with multiple orders for same user.
     *
     * Data:
     * - Person: kodama (age 1)
     * - Order: 3 orders for kodama with different costs
     * - Total cost: 1150 (1000 + 50 + 100)
     *
     * Use for: SUM, COUNT, AVG aggregate tests, GROUP BY tests
     */
    val AGGREGATE_SCENARIO = TestFixture("AGGREGATE_SCENARIO") {
        person("kodama", age = 1)

        order(1, "kodama", "Laptop", 1000)
        order(2, "kodama", "Mouse", 50)
        order(3, "kodama", "Keyboard", 100)
    }

    /**
     * Single user for isolation tests.
     *
     * Data:
     * - Person: kodama (age 1)
     *
     * Use for: Tests that need exactly one record, isolation tests
     */
    val SINGLE_USER = TestFixture("SINGLE_USER") {
        person("kodama", age = 1)
    }

    /**
     * Empty fixture - no data inserted.
     * Useful for explicit "start with nothing" tests.
     *
     * Use for: Testing empty result sets, COUNT(0) scenarios
     */
    val EMPTY = TestFixture("EMPTY") {
        // Intentionally empty
    }

    /**
     * Multiple users with same age for testing duplicates and grouping.
     *
     * Data:
     * - Person: 3 users, 2 with age=2, 1 with age=1
     *
     * Use for: GROUP BY age, COUNT with duplicates, DISTINCT tests
     */
    val DUPLICATE_AGES = TestFixture("DUPLICATE_AGES") {
        person("kodama", age = 1)
        person("kokoro", age = 2)
        person("pipiru", age = 2)  // Duplicate age
    }

    /**
     * Full dataset combining all standard data.
     * Use sparingly - prefer more specific fixtures.
     *
     * Data: All of BASIC_ECOMMERCE + COMPANIES + PRODUCT_CATALOG
     *
     * Use for: Complex multi-table tests, integration tests
     */
    val FULL_DATASET = TestFixture("FULL_DATASET") {
        // Load all other fixtures
        BASIC_ECOMMERCE.load(this)
        COMPANIES.load(this)
        PRODUCT_CATALOG.load(this)
    }
}

/**
 * A test fixture encapsulates a reusable data scenario.
 *
 * @param name Descriptive name for logging/debugging
 * @param block DSL block that populates the database
 */
class TestFixture(
    val name: String,
    private val block: TestDataBuilder.() -> Unit
) {
    /**
     * Load this fixture's data using the provided builder.
     */
    fun load(builder: TestDataBuilder) {
        builder.apply(block)
    }

    override fun toString(): String = "TestFixture($name)"
}
