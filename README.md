# Kodama - Type-Safe SQL Query Builder for Kotlin

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0+-blue.svg)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/version-0.2.0-orange.svg)]()

**Kodama** (Kotlin Data Mapper) is a type-safe SQL query builder for Kotlin and PostgreSQL. Unlike traditional ORMs, Kodama provides 100% compile-time type safety through code generation, eliminating runtime errors and reflection overhead.

## ✨ Key Features

- **🔒 Type Safety** - Catch all errors at compile time, not in production
- **🚀 Zero Reflection** - Code generation for maximum performance
- **💎 Fluent DSL** - Natural, readable query syntax
- **🎯 PostgreSQL Optimized** - Designed specifically for PostgreSQL
- **📦 Lightweight** - Minimal dependencies, focused on core functionality
- **✨ Nullable Types** - Full nullability support with `Column<T?>` for optional columns
- **🗂️ Entity Layer** - Interface-based ORM with relationships and identity map
- **🔗 Relationships** - Type-safe one-to-many and many-to-one navigation

## Quick Start

### Installation

#### Option 1: From Maven Central (Recommended - Coming Soon)

Once published to Maven Central, simply add to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.2.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.2.0")

    // SLF4J logging implementation (choose one)
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    // Or use Logback instead:
    // implementation("ch.qos.logback:logback-classic:1.4.14")
}
```

**Note:** `kodama-core` automatically includes the PostgreSQL JDBC driver as a transitive dependency.

**Package Configuration (Optional):**
```kotlin
kodama {
    schemaPackage.set("com.yourcompany.yourproject.schema")  // Auto-detected if not specified
    generatedPackage.set("com.yourcompany.yourproject.generated")  // Defaults to {schema}.generated
}
```

#### Option 2: From Maven Local (For Testing)

To test Kodama before the Maven Central release, you can publish it locally:

1. Clone and build Kodama:
```bash
git clone https://github.com/obabichev/kodama.git
cd kodama
./gradlew publishAllToMavenLocal
```

2. In your project's `settings.gradle.kts`, add `mavenLocal()`:
```kotlin
pluginManagement {
    repositories {
        mavenLocal()  // Add this
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()  // Add this
        mavenCentral()
    }
}
```

3. Use Kodama in your `build.gradle.kts`:
```kotlin
plugins {
    id("com.obabichev.kodama") version "0.2.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.2.0")

    // SLF4J logging implementation (required)
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
}
```

### Define Your Schema

**For Query DSL only:**

```kotlin
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.schema.primaryKey
import com.obabichev.kodama.schema.nullable

object Users : Table("users") {
    val id = integer("id").primaryKey()
    val email = varchar("email", 255)
    val age = integer("age")
    val bio = varchar("bio", 500).nullable()  // Optional column - Column<String?>
}

object Orders : Table("orders") {
    val id = integer("id").primaryKey()
    val userId = integer("user_id")
    val product = varchar("product", 255)
    val cost = integer("cost")
}
```

**For Entity Layer (ORM):**

```kotlin
import com.obabichev.kodama.schema.EntityTable
import com.obabichev.kodama.entity.oneToMany

// Define entity interface
interface User {
    val id: Int
    val email: String
    val age: Int
}

// Define EntityTable with generic type
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val email = varchar("email", 255)
    val age = integer("age")
}
```

### Write Type-Safe Queries

```kotlin
import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq

val queryBuilder = query()
    .from(Users)
    .join(Orders) { orders.userId eq users.id }
    .selectAll(Users)  // Select all columns from Users table
    .where {
        users.age eq 25
    }
```

### Execute and Get Results

```kotlin
withConnection { transaction ->
    val results = queryBuilder.execute(transaction)

    results.forEach { row ->
        // Access all selected columns from Users table
        println("User ${row.users.id}: ${row.users.email}, age ${row.users.age}")
    }
}
```

## Why Kodama?

### The Problem with Traditional ORMs

```kotlin
// Traditional ORM - compiles but fails at runtime
val user = query<User>()
    .select("name", "email")
    .execute()

println(user.id)  // 💥 Runtime error - id wasn't selected!
println(user.age) // 💥 Runtime error - type mismatch!
```

### The Kodama Solution

```kotlin
// Kodama - errors caught at compile time
val queryBuilder = query()
    .from(Users)
    .selectAll(Users)  // Select all columns

withConnection { transaction ->
    val results = queryBuilder.execute(transaction)
    val row = results.first()

    println(row.users.name)   // ✅ Compiles - all columns selected
    println(row.users.email)  // ✅ Compiles - all columns selected
    println(row.users.id)     // ✅ Compiles - all columns selected
    println(row.users.age)    // ✅ Compiles - all columns selected
}
```

## Examples

### Simple Query

```kotlin
query()
    .from(Users)
    .selectAll(Users)
    .where { users.age eq 25 }
```

**Generates:**
```sql
SELECT * FROM users WHERE age = ?
```

### Join Query

```kotlin
query()
    .from(Users)
    .join(Orders) { orders.userId eq users.id }
    .selectAll(Users)
```

**Generates:**
```sql
SELECT users.id, users.email, users.age
FROM users
INNER JOIN orders ON orders.user_id = users.id
```

### Multiple Joins

```kotlin
query()
    .from(Users)
    .join(Orders) { orders.userId eq users.id }
    .join(Payments) { payments.orderId eq orders.id }
    .selectAll(Orders)
```

**Generates:**
```sql
SELECT orders.id, orders.user_id, orders.product, orders.cost
FROM users
INNER JOIN orders ON orders.user_id = users.id
INNER JOIN payments ON payments.order_id = orders.id
```

### ORDER BY

```kotlin
query()
    .from(Users)
    .selectAll(Users)
    .orderBy {
        +users.age.desc()
        +users.email.asc()
    }
```

**Generates:**
```sql
SELECT * FROM users ORDER BY age DESC, email ASC
```

### Aggregates

```kotlin
query()
    .from(Orders)
    .select_totalRevenue { sum(orders.cost) }
    .select_orderCount { count(orders.id) }
    .execute(transaction)

// Results have type-safe named accessors
results.forEach { row ->
    val total: Number = row.totalRevenue  // Named accessor!
    val count: Number = row.orderCount
}
```

**Generates:**
```sql
SELECT SUM(cost) AS totalRevenue, COUNT(id) AS orderCount FROM orders
```

### INSERT Statements

```kotlin
// All columns required as parameters for compile-time safety
val result = Orders.insert(
    transaction = transaction,
    id = 1,
    userId = 100,
    product = "Laptop",
    cost = 1500
)

println("Inserted ${result.rowsAffected} row(s)")
result.generatedKeys["id"]?.let { println("Generated ID: $it") }

// Nullable columns (requires Product table with nullable columns)
Products.insert(
    transaction = transaction,
    id = 1,
    name = "Widget",
    description = null,  // Must explicitly pass null
    price = 100
)
```

**Generates:**
```sql
INSERT INTO orders (id, user_id, product, cost) VALUES (?, ?, ?, ?)
```

### Entity Layer (ORM)

Define entities as interfaces with automatic implementation generation:

```kotlin
// Entity interface
interface User {
    val id: Int
    val name: String
    val email: String

    // Relationship method with context parameter
    context(session: EntitySession)
    fun orders(): List<UserOrder>
}

interface UserOrder {
    val id: Int
    val userId: Int
    val product: String
    val amount: Int

    context(session: EntitySession)
    fun user(): User
}

// EntityTable definitions with relationships
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
    }
}

object UserOrders : EntityTable<UserOrder>("user_orders") {
    val id = integer("id").primaryKey()
    val userId = integer("user_id")
    val product = varchar("product", 255)
    val amount = integer("amount")

    init {
        manyToOne("user", Users, this.userId, Users.id)
    }
}
```

**Usage with EntitySession:**

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        // Get entity by ID (throws if not found - no !! needed)
        val user = get<User>(1)
        println("${user.name} (${user.email})")

        // Or use find() if entity might not exist (returns nullable)
        val maybeUser = find<User>(999)
        if (maybeUser != null) {
            println(maybeUser.name)
        }

        // Navigate relationships (one-to-many)
        val orders = user.orders()
        orders.forEach { order ->
            println("  - ${order.product}: $${order.amount}")
        }

        // Navigate back (many-to-one)
        val firstOrder = orders.first()
        val parentUser = firstOrder.user()
        assert(user === parentUser)  // Same instance from identity map!

        // Create and save new entity
        val newOrder = UserOrder(
            id = 100,
            userId = user.id,
            product = "Headphones",
            amount = 80
        )
        save<UserOrder, Int>(newOrder)
        flush()
    }
}
```

**Key Entity Layer Features:**
- **Interface-based entities** - Define contract, get implementation for free
- **Identity map** - Same ID always returns same instance within session
- **Convenient API** - `get<Entity>(id)` returns non-null, `find<Entity>(id)` returns nullable
- **Type-safe relationships** - Navigate parent ↔ children with compile-time safety
- **Context parameters** - Clean syntax using Kotlin 2.2.0 context parameters
- **Lazy loading** - Relationships loaded on-demand
- **Bidirectional navigation** - Both one-to-many and many-to-one work seamlessly

## Documentation

📚 **[Full Documentation](doc/README.md)**

- [Getting Started](doc/getting-started.md) - Installation and basics
- [Table Definitions](doc/table-definitions.md) - Define your schema
- [Query Building](doc/query-building.md) - Build queries with the DSL
- [Joins](doc/joins.md) - Working with multiple tables
- [Type-Safe Results](doc/type-safe-results.md) - Understanding results
- [Query Execution](doc/query-execution.md) - Execute and handle transactions
- [Code Generation](doc/code-generation.md) - How code generation works

## Philosophy

Kodama is built on these core principles:

1. **Type Safety First** - If it compiles, it works
2. **No Magic** - Everything is explicit and traceable
3. **Compile-Time Validation** - Catch errors before they reach production
4. **Zero Reflection** - Use code generation for performance
5. **Natural Kotlin** - Feels like native Kotlin code

## Comparison

| Feature | Kodama | Exposed | jOOQ | Hibernate |
|---------|--------|---------|------|-----------|
| Compile-time safety | ✅ 100% | ⚠️ Partial | ✅ Yes | ❌ No |
| Reflection-free | ✅ Yes | ❌ No | ✅ Yes | ❌ No |
| Type-safe results | ✅ Yes | ❌ No | ✅ Yes | ⚠️ Partial |
| Entity Layer (ORM) | ✅ Yes | ✅ Yes | ❌ No | ✅ Yes |
| Type-safe relationships | ✅ Yes | ⚠️ Partial | ❌ No | ❌ No |
| Interface-based entities | ✅ Yes | ❌ No | ❌ No | ❌ No |
| PostgreSQL focused | ✅ Yes | ❌ No | ⚠️ Multi-DB | ❌ No |
| Fluent DSL | ✅ Yes | ✅ Yes | ✅ Yes | ⚠️ Limited |
| Code generation | ✅ Gradle | ❌ No | ✅ Maven/Gradle | ❌ No |

## Current Features

**Version**: 0.2.0 (Alpha)

### DSL Layer (Query Building) ✅
- **SELECT** - Type-safe column selection
- **FROM** - Single table queries
- **INNER JOIN** - Multiple table queries with type-safe conditions
- **WHERE** - Filter with `eq` operator
- **ORDER BY** - Sort results with `.asc()` and `.desc()`
- **Multiple Joins** - Chain joins across 3+ tables
- **Aggregate Functions** - `count()`, `sum()`, `avg()`, `min()`, `max()`
- **Named Aggregates** - Type-safe aggregate aliases with method-based selection
- **GROUP BY** - Automatic GROUP BY for mixed column + aggregate queries
- **INSERT** - Type-safe inserts with compile-time column validation

### Entity Layer (ORM) ✅
- **Interface-Based Entities** - Define entities as interfaces, get implementations for free
- **EntitySession** - Identity map for entity caching and session management
- **CRUD Operations** - `get()`, `find()`, `save()`, `delete()`, `flush()`
  - `get<Entity>(id)` - Returns non-null entity or throws exception
  - `find<Entity>(id)` - Returns nullable entity
- **One-to-Many Relationships** - Type-safe parent → children navigation
- **Many-to-One Relationships** - Type-safe child → parent navigation
- **Bidirectional Relationships** - Navigate both directions with identity map consistency
- **Context Parameters** - Clean syntax using Kotlin 2.2.0 context parameters
- **Lazy Loading** - Relationships loaded on-demand
- **Auto-Generated Implementations** - Internal data classes + factory functions

### Type System ✅
- **Nullable Columns** - Full support with `Column<T?>` type
- **Compile-Time Safety** - Only access what you selected
- **Type-Safe Results** - Result types match selections exactly
- **Type-Safe Relationships** - Relationship methods declared in interfaces

### In Progress 🚧
- Many-to-many relationships with junction tables
- Batch loading (N+1 prevention)
- Additional WHERE operators (gt, lt, like, isNull, etc.)
- AND/OR boolean combinations
- LIMIT and OFFSET

### Planned 📋
- UPDATE and DELETE statements (DSL layer)
- LEFT/RIGHT/FULL OUTER JOINs
- DISTINCT
- IN operator and subqueries
- Cascade operations (entity layer)
- Dirty checking for partial updates
- Window functions

## Requirements

- Kotlin 2.2.0+ (for context parameters in Entity Layer)
- Gradle 8.0+
- PostgreSQL 12+
- JVM 17+

### Dependencies

Kodama requires:
- **kodama-core** - The main library (includes PostgreSQL JDBC driver and SLF4J API)
- **SLF4J implementation** - A logging backend like Log4j or Logback (you must add this)

## Contributing

Contributions are welcome! Please:

1. Check the [roadmap](ROADMAP.md) for planned features
2. Open an issue to discuss your idea
3. Submit a PR with tests and documentation

**For Maintainers:**
- [Version Update Guide](VERSION_UPDATE.md) - How to update versions for releases
- [Publishing Guide](doc/publishing.md) - How to publish to Maven Central

## Building from Source

```bash
# Clone the repository
git clone https://github.com/obabichev/kodama.git
cd kodama

# Build
./gradlew build

# Run tests
./gradlew test
```

## Getting Help

- 📖 [Documentation](doc/README.md)
- 🐛 [Report Issues](https://github.com/obabichev/kodama/issues)
- 💡 [Feature Requests](https://github.com/obabichev/kodama/issues/new)

## License

[Add license information]

## Acknowledgments

Inspired by:
- [Exposed](https://github.com/JetBrains/Exposed) - JetBrains' SQL library for Kotlin
- [jOOQ](https://www.jooq.org/) - Type-safe SQL with code generation
- [Slick](https://scala-slick.org/) - Scala's functional-relational mapper

---

**Kodama** - _Type-safe SQL, the Kotlin way_
