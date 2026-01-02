# Getting Started with Kodama

## Overview

Kodama is a type-safe SQL query builder and ORM for Kotlin that provides compile-time safety for your database operations. Unlike traditional ORMs that use reflection and runtime validation, Kodama ensures your queries are correct at compile time.

## Key Features

- **100% Type Safety**: All queries and entities are validated at compile time
- **No Reflection**: Uses code generation instead of runtime reflection
- **Fluent DSL**: Natural, readable query syntax
- **Entity Layer (ORM)**: Interface-based entities with relationships and CRUD operations
- **PostgreSQL Support**: Optimized for PostgreSQL databases
- **Type-Safe Results**: Access only the columns you selected, with correct types

## Installation

### Option 1: From Maven Central (Recommended - Coming Soon)

Once published to Maven Central, simply add Kodama to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.4.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.4.0")

    // SLF4J logging implementation (required - choose one)
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    // Or use Logback instead:
    // implementation("ch.qos.logback:logback-classic:1.4.14")
}
```

**What's included:**
- `kodama-core` - The core library with query DSL and entity layer
- PostgreSQL JDBC driver (included automatically as a transitive dependency)
- SLF4J API (included automatically as a transitive dependency)

**What you need to add:**
- An SLF4J implementation (Log4j, Logback, or another) - required for logging

### Option 2: From Maven Local (For Testing)

To test Kodama before the Maven Central release:

#### Step 1: Build and Publish Locally

Clone and publish Kodama to your local Maven repository:

```bash
git clone https://github.com/obabichev/kodama.git
cd kodama
./gradlew publishAllToMavenLocal
```

This publishes both `kodama-core` and `kodama-compiler-plugin` to `~/.m2/repository/`.

#### Step 2: Configure Your Project

In your project's `settings.gradle.kts`, add `mavenLocal()` to the repositories:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()  // Add this to resolve the Kodama plugin
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()  // Add this to resolve kodama-core
        mavenCentral()
    }
}
```

#### Step 3: Use Kodama

Now you can use Kodama in your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.4.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.4.0")

    // SLF4J logging implementation (required)
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
}
```

**Note:** When Kodama is published to Maven Central, you can remove `mavenLocal()` from your repositories.

### Package Configuration (Optional)

By default, Kodama automatically detects your package structure. However, you can explicitly configure packages if needed:

```kotlin
kodama {
    // Package where your Table definitions are located
    schemaPackage.set("com.yourcompany.yourproject.schema")

    // Package where generated code will be placed
    generatedPackage.set("com.yourcompany.yourproject.generated")
}
```

**Auto-Detection:** If not configured, Kodama will:
1. Scan your `src/main/kotlin` directory for files containing `Table` definitions
2. Extract the package name from those files
3. Place generated code in `{schemaPackage}.generated`

**Example:** If your tables are in `com.example.myapp.schema`, generated code will be in `com.example.myapp.schema.generated`.

## Basic Concepts

### 1. Define Your Tables

Tables are defined as Kotlin objects that extend the `Table` base class:

```kotlin
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.schema.primaryKey
import com.obabichev.kodama.schema.nullable

object Person : Table("person") {
    val name = varchar("name", 255).primaryKey()
    val age = integer("age")
}

object Order : Table("order") {
    val id = integer("id").primaryKey()
    val userName = varchar("user_name", 255)
    val product = varchar("product", 255)
    val cost = integer("cost")
    val notes = varchar("notes", 1000).nullable()  // Optional notes - Column<String?>
}

// Datetime column types
object Event : Table("event") {
    val id = integer("id").primaryKey()
    val eventDate = date("event_date")                    // DATE → LocalDate
    val eventTime = time("event_time")                    // TIME → LocalTime
    val createdAt = timestamp("created_at")               // TIMESTAMP → LocalDateTime
    val scheduledFor = timestamp("scheduled_for").nullable()  // Optional timestamp
    val eventTimestamp = timestampWithTimeZone("event_timestamp")  // TIMESTAMPTZ → OffsetDateTime
    val reminderTime = timeWithTimeZone("reminder_time")  // TIMETZ → OffsetTime
    val duration = interval("duration")                   // INTERVAL → Duration
}
```

### 2. Build Queries

Use the fluent DSL to build type-safe queries:

```kotlin
import com.obabichev.kodama.query.from
import com.obabichev.kodama.query.eq

val queryBuilder = from(Person)
    .selectAll(Person)  // Select all columns from Person table
    .where {
        person.age eq 25
    }
```

### 3. Execute Queries

Execute queries within a transaction:

```kotlin
withConnection { transaction ->
    val results = queryBuilder.execute(transaction)

    results.forEach { row ->
        println("Name: ${row.person.name}, Age: ${row.person.age}")
    }
}
```

## Complete Setup Example

Here's a complete `build.gradle.kts` with all required dependencies:

```kotlin
plugins {
    kotlin("jvm") version "2.2.0"
    id("com.obabichev.kodama") version "0.4.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")  // Required for Entity Layer
    }
}

dependencies {
    // Kodama
    implementation("com.obabichev.kodama:kodama-core:0.4.0")

    // Logging (required - choose one)
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    // Or Logback:
    // implementation("ch.qos.logback:logback-classic:1.4.14")

    // PostgreSQL driver (already included transitively, but shown for clarity)
    // implementation("org.postgresql:postgresql:42.7.8")
}
```

## Your First Query

Here's a complete example:

```kotlin
// 1. Define your table
object User : Table("users") {
    val id = integer("id").primaryKey()
    val email = varchar("email", 255)
    val age = integer("age")
}

// 2. Build a query
val queryBuilder = from(User)
    .selectAll(User)
    .where {
        user.age eq 25
    }

// 3. Execute the query
withConnection { transaction ->
    val results = queryBuilder.execute(transaction)

    results.forEach { row ->
        println("Email: ${row.user.email}")
        println("Age: ${row.user.age}")

        // Compile error! id was not selected:
        // println(row.user.id)  // Won't compile!
    }
}
```

## Nullable Columns

Kodama supports nullable columns with full type safety:

### Defining Nullable Columns

Use the `.nullable()` extension to mark columns as optional:

```kotlin
object Product : Table("product") {
    val id = integer("id").primaryKey()        // Column<Int> - required
    val name = varchar("name", 255)            // Column<String> - required
    val description = varchar("description", 500).nullable()  // Column<String?> - optional
    val discount = integer("discount").nullable()  // Column<Int?> - optional
}
```

### Working with Nullable Values

Nullable columns have nullable types in results:

```kotlin
withConnection { transaction ->
    val results = from(Product)
        .selectAll(Product)
        .execute(transaction)

    results.forEach { row ->
        val name: String = row.product.name  // Non-nullable
        val description: String? = row.product.description  // Nullable

        // Handle null values safely
        if (description != null) {
            println("Description: $description")
        } else {
            println("No description")
        }

        // Or use safe-call operator
        println("Description length: ${description?.length ?: 0}")
    }
}
```

### Type Safety

The type system enforces correct nullability:

```kotlin
// ✅ Correct
val nonNull: Column<Int> = Product.id
val nullable: Column<Int?> = Product.discount

// ❌ Won't compile
val wrong1: Column<Int?> = Product.id  // Type mismatch
val wrong2: Column<Int> = Product.discount  // Type mismatch
```

## Date/Time Column Types

Kodama provides full support for PostgreSQL date and time types, mapping them to Java Time API types for type safety and timezone awareness.

### Available Date/Time Types

| SQL Type | Kodama Function | Kotlin Type | Description |
|----------|----------------|-------------|-------------|
| DATE | `date("column")` | `LocalDate` | Calendar date (year, month, day) |
| TIME | `time("column")` | `LocalTime` | Time of day without timezone |
| TIMESTAMP | `timestamp("column")` | `LocalDateTime` | Date + time without timezone |
| TIMESTAMPTZ | `timestampWithTimeZone("column")` | `OffsetDateTime` | Date + time with timezone |
| TIMETZ | `timeWithTimeZone("column")` | `OffsetTime` | Time of day with timezone |
| INTERVAL | `interval("column")` | `Duration` | Time duration/period |

### Example Table Definition

```kotlin
import java.time.*

object Event : Table("events") {
    val id = integer("id").primaryKey()
    val eventDate = date("event_date")                    // LocalDate
    val eventTime = time("event_time")                    // LocalTime
    val createdAt = timestamp("created_at")               // LocalDateTime
    val scheduledFor = timestamp("scheduled_for").nullable()  // LocalDateTime?
    val eventTimestamp = timestampWithTimeZone("event_timestamp")  // OffsetDateTime
    val reminderTime = timeWithTimeZone("reminder_time")  // OffsetTime
    val duration = interval("duration")                   // Duration
}
```

### Working with Date/Time Values

All datetime types integrate seamlessly with queries and inserts:

```kotlin
withConnection { transaction ->
    // Insert event with datetime values
    Event.insert(
        transaction = transaction,
        id = 1,
        eventDate = LocalDate.of(2025, 12, 25),
        eventTime = LocalTime.of(14, 30),
        createdAt = LocalDateTime.now(),
        scheduledFor = null,  // nullable
        eventTimestamp = OffsetDateTime.now(),
        reminderTime = OffsetTime.of(LocalTime.of(13, 0), ZoneOffset.UTC),
        duration = Duration.ofHours(2)
    )

    // Query events
    val results = from(Event)
        .selectAll(Event)
        .execute(transaction)

    results.forEach { row ->
        val date: LocalDate = row.event.eventDate
        val time: LocalTime = row.event.eventTime
        val timestamp: OffsetDateTime = row.event.eventTimestamp

        println("Event on $date at $time")
        println("Created: ${row.event.createdAt}")
        println("Duration: ${row.event.duration.toHours()} hours")
    }
}
```

### Timezone Handling

PostgreSQL stores timezone-aware types (TIMESTAMPTZ) in UTC internally and converts them based on the session timezone:

- **TIMESTAMPTZ (OffsetDateTime)**: Preserves the offset information. PostgreSQL stores in UTC.
- **TIMETZ (OffsetTime)**: Time with timezone offset.
- **TIMESTAMP (LocalDateTime)**: No timezone information - assumes local or session timezone.
- **DATE/TIME (LocalDate/LocalTime)**: No timezone information.

**Best Practice**: Use `TIMESTAMPTZ` for events that need timezone accuracy (user actions, scheduled tasks), and `TIMESTAMP` for timezone-independent times (relative durations, local schedules).

### Interval Type

PostgreSQL `INTERVAL` type maps to `java.time.Duration`:

```kotlin
// Create durations
val twoHours = Duration.ofHours(2)
val thirtyMinutes = Duration.ofMinutes(30)
val oneDay = Duration.ofDays(1)

// Insert with duration
Event.insert(
    transaction = transaction,
    id = 1,
    duration = Duration.ofHours(3).plusMinutes(30),  // 3 hours 30 minutes
    // ... other fields
)

// Query and use durations
results.forEach { row ->
    val duration: Duration = row.event.duration
    println("Duration: ${duration.toHours()}h ${duration.toMinutesPart()}m")
}
```

## Advanced Features

### INSERT Statements

Kodama generates type-safe INSERT methods for each table with all columns as required parameters:

```kotlin
// All columns must be provided
val result = Order.insert(
    transaction = transaction,
    id = 1,
    userName = "kodama",
    product = "Laptop",
    cost = 1500
)

println("Inserted ${result.rowsAffected} row(s)")
result.generatedKeys["id"]?.let { println("Generated ID: $it") }

// Nullable columns must be explicitly passed (null or value)
Order.insert(
    transaction = transaction,
    id = 2,
    userName = "user2",
    product = "Mouse",
    cost = 50,
    notes = null  // Explicit null for nullable column
)
```

**Key Features:**
- All columns are required parameters (compile-time safety on schema changes)
- Nullable columns have `Type?` parameter
- Returns `InsertResult` with `rowsAffected` and `generatedKeys`
- Proper NULL handling in prepared statements

### Auto-Increment Columns

Kodama supports PostgreSQL's SERIAL types and SQL standard IDENTITY columns for auto-generated IDs.
Auto-generated columns are **automatically excluded** from `insert()` method parameters:

```kotlin
// Define table with SERIAL primary key (PostgreSQL-specific)
object Users : Table("users") {
    val id = serial("id").primaryKey()  // Auto-generated!
    val name = varchar("name", 255)
    val email = varchar("email", 255)
}

// Or use SQL standard IDENTITY
object Products : Table("products") {
    val id = integer("id").identity().primaryKey()  // SQL standard
    val name = varchar("name", 255)
    val price = integer("price")
}

// INSERT - id parameter is automatically excluded!
val result = Users.insert(
    transaction = transaction,
    name = "Alice",
    email = "alice@example.com"
    // No id parameter - it's auto-generated!
)

// Access the generated ID
val generatedId = result.generatedKeys["id"] as Int
println("Created user with ID: $generatedId")
```

**Available auto-increment types:**

| Function | SQL Type | Kotlin Type | Range |
|----------|----------|-------------|-------|
| `serial("id")` | SERIAL | Int | 1 to 2,147,483,647 |
| `bigserial("id")` | BIGSERIAL | Long | 1 to 9,223,372,036,854,775,807 |
| `smallserial("id")` | SMALLSERIAL | Short | 1 to 32,767 |
| `integer("id").identity()` | INTEGER GENERATED ALWAYS AS IDENTITY | Int | SQL standard |
| `bigint("id").identity()` | BIGINT GENERATED ALWAYS AS IDENTITY | Long | SQL standard |
| `smallint("id").identity()` | SMALLINT GENERATED ALWAYS AS IDENTITY | Short | SQL standard |

**SERIAL vs IDENTITY:**
- **SERIAL**: PostgreSQL-specific, simpler syntax, widely used
- **IDENTITY**: SQL standard (SQL:2003), more portable to other databases

**Key Features:**
- Auto-generated columns are excluded from `insert()` parameters at compile-time
- Generated IDs are returned in `InsertResult.generatedKeys` map
- Type-safe: code won't compile if you try to provide a value for auto-generated columns
- Works with any primary key or unique column

### Marker-Based Selections

Kodama uses a unified `.selectAs()` API for type-safe selections with marker interfaces. This works for columns, aggregates, and expressions:

```kotlin
// Aggregates only - no GROUP BY needed
val results = from(Order)
    .selectAs(TotalRevenue) { sum(order.cost) }
    .selectAs(OrderCount) { count(order.id) }
    .execute(transaction)

// Access with type-safe named accessors
results.forEach { row ->
    val revenue: Number = row.totalRevenue  // sum() returns Number
    val count: Long = row.orderCount        // count() returns Long
    println("Total: $revenue from $count orders")
}

// Mix columns with aggregates - GROUP BY required
// Use .selectAs() for both columns and aggregates for consistent named accessors
val byUser = from(Order)
    .selectAs(OrderUserName) { order.userName }  // Column selection
    .selectAs(UserTotal) { sum(order.cost) }  // Aggregate selection
    .groupBy { order.userName }  // Must group by non-aggregate columns
    .execute(transaction)

byUser.forEach { row ->
    println("${row.orderUserName}: ${row.userTotal}")  // Both use named accessors!
}

// Expression selections (boolean, comparisons, etc.)
from(Person)
    .selectAs(IsAdult) { person.age gte 18 }
    .execute(transaction)
    .forEach { row ->
        val isAdult: Boolean = row.isAdult  // Properly typed!
    }
```

**Available Functions:**
- `count(column)` - Count rows
- `sum(column)` - Sum values
- `avg(column)` - Average value
- `min(column)` - Minimum value
- `max(column)` - Maximum value

**Type Inference:**

Kodama infers appropriate types for each aggregate function:
- `count()` → `Long` - Count of rows
- `sum()` → `Number` - Sum of numeric columns (returns Number for flexibility)
- `avg()` → `Double` - Average value
- `min()`/`max()` → Preserves source column type

### ORDER BY

Sort query results with type-safe column references:

```kotlin
// Single column ORDER BY
from(User)
    .selectAll(User)
    .orderBy { user.age.desc() }
    .execute(transaction)

// Multiple columns - chain orderBy calls
from(User)
    .selectAll(User)
    .orderBy { user.age.desc() }  // First: descending age
    .orderBy { user.name.asc() }  // Then: ascending name
    .execute(transaction)
```

**Important:**
- Each `.orderBy { }` call returns exactly one `OrderByClause` and can be chained
- Columns are sorted in the order orderBy calls are chained

### GROUP BY

When mixing regular columns with aggregate functions, use explicit GROUP BY:

```kotlin
// Single column GROUP BY
from(Order)
    .selectAs(OrderUserName) { order.userName }
    .selectAs(TotalCost) { sum(order.cost) }
    .groupBy { order.userName }  // Returns one column
    .execute(transaction)

// Multiple columns - chain groupBy calls
from(Order)
    .selectAs(OrderUserName) { order.userName }
    .selectAs(OrderProduct) { order.product }
    .selectAs(TotalCost) { sum(order.cost) }
    .groupBy { order.userName }   // First grouping column
    .groupBy { order.product }    // Second grouping column
    .execute(transaction)
```

**Important:**
- GROUP BY is **required** when mixing columns with aggregates
- All non-aggregate selected columns must be included in GROUP BY
- Each `.groupBy { }` call returns exactly one column and can be chained
- Type-safe: only columns from the query can be referenced

### LIMIT and OFFSET (Pagination)

Paginate query results with type-safe limit and offset:

```kotlin
// Basic pagination
from(User)
    .selectAll(User)
    .orderBy { user.id.asc() }
    .limit(10)
    .offset(20)
    .execute(transaction)

// Typical pagination pattern
val page = 2
val pageSize = 10
from(User)
    .selectAll(User)
    .orderBy { user.id.asc() }
    .limit(pageSize)
    .offset(page * pageSize)
    .execute(transaction)
```

**Key features:**
- Works with WHERE, ORDER BY, JOIN, aggregates
- Optional parameters (nullable)
- Type-safe method chaining

### Comparison Operators

Kodama provides a complete set of comparison operators for building WHERE clauses:

#### Basic Comparison Operators

```kotlin
// Equality
from(User)
    .selectAll(User)
    .where { user.age eq 25 }  // age = 25

// Not equal
from(User)
    .selectAll(User)
    .where { user.age neq 0 }  // age <> 0

// Greater than / Less than
from(User)
    .selectAll(User)
    .where { user.age gt 18 }  // age > 18

from(User)
    .selectAll(User)
    .where { user.age lt 65 }  // age < 65

// Greater than or equal / Less than or equal
from(User)
    .selectAll(User)
    .where { user.age gte 18 }  // age >= 18

from(User)
    .selectAll(User)
    .where { user.age lte 65 }  // age <= 65
```

#### Range Queries (BETWEEN)

The `between` operator provides inclusive range queries:

```kotlin
// Find users aged 18 to 65 (inclusive)
from(User)
    .selectAll(User)
    .where { user.age.between(18, 65) }
    .execute(transaction)

// BETWEEN includes both boundaries
// Generates: WHERE age BETWEEN 18 AND 65
```

#### NULL Checks

Check for NULL and non-NULL values:

```kotlin
// Find users with no email
from(User)
    .selectAll(User)
    .where { user.email.isNull() }
    .execute(transaction)

// Find users with email
from(User)
    .selectAll(User)
    .where { user.email.isNotNull() }
    .execute(transaction)

// Combine NULL checks
from(Product)
    .selectAll(Product)
    .where {
        product.description.isNotNull() and product.discount.isNull()
    }
    .execute(transaction)
```

#### String Pattern Matching

Pattern matching with LIKE, ILIKE, and convenience methods:

```kotlin
// Case-sensitive pattern matching
from(User)
    .selectAll(User)
    .where { user.name like "%john%" }
    .execute(transaction)

// Case-insensitive pattern matching (PostgreSQL-specific)
from(User)
    .selectAll(User)
    .where { user.name ilike "%JOHN%" }  // Matches "john", "John", "JOHN"
    .execute(transaction)

// Convenience methods for common patterns
from(User)
    .selectAll(User)
    .where { user.name startsWith "John" }  // name LIKE 'John%'
    .execute(transaction)

from(User)
    .selectAll(User)
    .where { user.name endsWith "Smith" }  // name LIKE '%Smith'
    .execute(transaction)

from(User)
    .selectAll(User)
    .where { user.name contains "doe" }  // name LIKE '%doe%'
    .execute(transaction)

// Combine string operators with boolean logic
from(Product)
    .selectAll(Product)
    .where {
        (product.name startsWith "Premium") and (product.name endsWith "Pro")
    }
    .execute(transaction)
```

**Available Operators:**
- **Comparison**: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`
- **Range**: `between(lower, upper)` - inclusive on both ends
- **NULL checks**: `isNull()`, `isNotNull()`
- **String matching**: `like`, `ilike` (case-insensitive), `startsWith`, `endsWith`, `contains`
- **List membership**: `inList(values)`, `notInList(values)`
- **Boolean logic**: `and`, `or`, `not` (for combining conditions)

#### List Membership (IN Operators)

Check if a column value is in or not in a list of values:

```kotlin
// IN - find users with specific names
from(User)
    .selectAll(User)
    .where { user.name.inList(listOf("alice", "bob", "charlie")) }
    .execute(transaction)

// NOT IN - exclude specific names
from(User)
    .selectAll(User)
    .where { user.name.notInList(listOf("banned1", "banned2")) }
    .execute(transaction)

// Works with integers and other types
from(User)
    .selectAll(User)
    .where { user.age.inList(listOf(25, 30, 35, 40)) }
    .execute(transaction)

// Empty list handling
from(User)
    .selectAll(User)
    .where { user.name.inList(emptyList()) }  // Generates FALSE - no matches
    .execute(transaction)

from(User)
    .selectAll(User)
    .where { user.name.notInList(emptyList()) }  // Generates TRUE - all rows match
    .execute(transaction)

// Combine with other conditions
from(User)
    .selectAll(User)
    .where {
        user.name.inList(listOf("alice", "bob", "charlie")) and (user.age gt 25)
    }
    .execute(transaction)
```

**Key features:**
- `inList(values)` - Returns TRUE if column value is in the list
- `notInList(values)` - Returns TRUE if column value is NOT in the list
- Works with all column types (strings, integers, etc.)
- Empty list handling: `inList(emptyList())` = FALSE (no matches), `notInList(emptyList())` = TRUE (all match)
- Type-safe parameter binding prevents SQL injection
- Can be combined with other operators using `and`/`or`

### Entity Layer

For ORM functionality with CRUD operations and relationships, see the [Entity Layer guide](entities.md):

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        // CRUD operations
        val user = get<User>(1)

        // Relationships
        val orders = user.orders()

        // Save/update
        save<User, Int>(user.copy(email = "new@example.com"))
        flush()
    }
}
```

## Next Steps

- **[Entity Layer (ORM)](entities.md)** - Learn about CRUD operations and relationships
- **[Code Generation](code-generation.md)** - How Kodama generates code
- **[Roadmap](../ROADMAP.md)** - See planned features

## Philosophy

Kodama's design is based on these principles:

1. **Catch Errors Early**: If it compiles, it works
2. **No Magic**: Everything is explicit and traceable
3. **Type Safety First**: Never sacrifice type safety for convenience
4. **Natural Kotlin**: Feels like native Kotlin code

## Getting Help

- Check the [documentation](.)
- Review the [examples](../examples)
- See the [roadmap](../ROADMAP.md) for planned features
- Report issues on GitHub
