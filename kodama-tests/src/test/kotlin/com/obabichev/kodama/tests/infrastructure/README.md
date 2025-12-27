# Test Infrastructure

This directory contains the core test infrastructure for Kodama tests.

## Components

### DatabaseTest.kt
Base class for all database tests. Provides:
- Per-class table management (only creates tables declared by each test class)
- Automatic data cleanup between tests using TRUNCATE
- Type-safe data insertion via `testData {}` DSL
- Fixture support via `useFixture()`

### TableRegistry.kt
Manages table metadata and SQL generation:
- Lists all available tables in dependency order
- Generates `CREATE TABLE IF NOT EXISTS` SQL from Table objects
- Handles SQL reserved keywords (like "order")
- Supports nullable columns

### TestDataBuilder.kt
Type-safe DSL for inserting test data:
- Prevents SQL injection (uses prepared statements)
- Named parameters for clarity
- Returns inserted data for chaining

### TestFixtures.kt
Predefined test data scenarios:
- `BASIC_ECOMMERCE` - Standard user/order/profile data
- `AGGREGATE_SCENARIO` - Multiple orders for testing aggregates
- `COMPANIES` - Company data
- `PRODUCT_CATALOG` - Products with nullable columns
- And more...

## Usage

### Basic Test

```kotlin
class MyTest : DatabaseTest() {
    // Declare only tables you need
    override fun requiredTables() = listOf(Person, Order)

    @Test
    fun testSomething() {
        // Insert test data
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
        }

        // Run query
        withConnection {
            val results = from(Person)
                .join(Order) { order.userName eq person.name }
                .execute(this)

            assertEquals(1, results.count())
        }
    }
}
```

### Using Fixtures

```kotlin
class MyTest : DatabaseTest() {
    override fun requiredTables() = listOf(Person, Order, Profile)

    @Test
    fun testWithFixture() {
        // Load predefined data
        useFixture(TestFixtures.BASIC_ECOMMERCE)

        withConnection {
            // Data already present
            val results = from(Person).execute(this)
            assertEquals(3, results.count()) // kodama, kokoro, pipiru
        }
    }
}
```

## Design Decisions

### CREATE TABLE IF NOT EXISTS

**Issue:** When multiple test classes use the same tables, the second class would encounter "table already exists" errors, causing transaction aborts.

**Solution:** Use `CREATE TABLE IF NOT EXISTS` instead of `CREATE TABLE`:
```sql
CREATE TABLE IF NOT EXISTS person (
    name TEXT NOT NULL PRIMARY KEY,
    age INTEGER NOT NULL
)
```

This is idempotent - it succeeds whether the table exists or not, preventing transaction errors.

### Per-Class Table Creation

Each test class declares only the tables it needs via `requiredTables()`. Benefits:
- **Faster**: Don't create unnecessary tables
- **Isolated**: Each test class is independent
- **Clear**: Explicit dependencies in code
- **Maintainable**: Easy to see which tests use which tables

### Lazy Initialization

Tables are created on first test run, not in a global setup. This allows:
- Multiple test classes to share the same database
- Tables to be created only when needed
- Test classes to run in any order

### TRUNCATE vs DELETE

Data cleanup uses `TRUNCATE TABLE ... CASCADE` instead of `DELETE FROM`:
- **Much faster**: O(1) instead of O(n)
- **Resets sequences**: Auto-increment IDs start from 1
- **Cascades**: Automatically handles foreign key dependencies

## Key Principles

1. **Explicit over implicit**: Test data is visible in each test
2. **Fast over slow**: Only create/clean what's needed
3. **Type-safe over strings**: Use DSL instead of raw SQL
4. **Isolated over shared**: Each test is independent

## Performance

**Before refactoring:**
- All 5 tables created for all 6 test classes = 30 table creations
- All tables truncated before each test

**After refactoring:**
- 13 total table creations (57% reduction)
- Only required tables truncated per test
- Estimated 20-40% faster test execution

## Migration

Tests can still use `PostgresBaseTest` for backward compatibility, but new tests should extend `DatabaseTest` directly for better performance and clarity.
