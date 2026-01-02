# Kodama - Type-Safe SQL Query Builder for Kotlin

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0+-blue.svg)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/version-0.3.0-orange.svg)]()

**Kodama** (Kotlin Data Mapper) is a type-safe SQL query builder for Kotlin and PostgreSQL. Unlike traditional ORMs,
Kodama provides 100% compile-time type safety through code generation, eliminating runtime errors and reflection
overhead.

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
    id("com.obabichev.kodama") version "0.3.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.3.0")

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
    id("com.obabichev.kodama") version "0.3.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.3.0")

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
import com.obabichev.kodama.query.from
import com.obabichev.kodama.query.eq

val queryBuilder = from(Users)
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

## Examples

### Simple Query

```kotlin
// Select all columns from a table
from(Users)
    .selectAll(Users)
    .where { users.age eq 25 }
    .execute(transaction)
    .forEach { row ->
        val email = row.users.email  // Table-scoped accessor
        val age = row.users.age
    }

// Select specific columns with named accessors (recommended)
from(Users)
    .selectAs(UserEmail) { users.email }
    .selectAs(UserAge) { users.age }
    .where { users.age eq 25 }
    .execute(transaction)
    .forEach { row ->
        val email = row.userEmail  // Direct named accessor - type-safe!
        val age = row.userAge
    }
```

**Generates:**

```sql
-- selectAll generates explicit column list (not SELECT *)
SELECT "users"."id", "users"."email", "users"."age"
FROM "users"
WHERE "age" = ?

-- selectAs with markers
SELECT "users"."email" AS "user_email", "users"."age" AS "user_age"
FROM "users"
WHERE "age" = ?
```

### Join Query

```kotlin
from(Users)
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
from(Users)
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
// Single column ORDER BY
from(Users)
    .selectAll(Users)
    .orderBy { users.age.desc() }

// Multiple columns - chain orderBy calls
from(Users)
    .selectAll(Users)
    .orderBy { users.age.desc() }
    .orderBy { users.email.asc() }
```

**Generates:**

```sql
SELECT "users"."id", "users"."email", "users"."age"
FROM "users"
ORDER BY "age" DESC, "email" ASC
```

### Pagination (LIMIT / OFFSET)

```kotlin
// Basic pagination
from(Users)
    .selectAll(Users)
    .orderBy { users.id.asc() }
    .limit(10)
    .offset(20)

// Typical pagination pattern
val page = 2
val pageSize = 10
from(Users)
    .selectAll(Users)
    .orderBy { users.id.asc() }
    .limit(pageSize)
    .offset(page * pageSize)
    .execute(transaction)
```

**Generates:**

```sql
SELECT "users"."id", "users"."email", "users"."age"
FROM "users"
ORDER BY "id" ASC
LIMIT 10 OFFSET 20
```

### Marker-Based Selections with `.selectAs()`

Kodama uses a unified `.selectAs()` API for type-safe selections with marker interfaces:

```kotlin
// Column selections - marker infers from column type
from(Users)
    .selectAs(UserName) { users.name }  // String
    .selectAs(UserAge) { users.age }    // Int
    .execute(transaction)

// Aggregate selections - marker infers from aggregate function
from(Orders)
    .selectAs(TotalRevenue) { sum(orders.cost) }  // Number
    .selectAs(OrderCount) { count(orders.id) }    // Long
    .execute(transaction)

// Expression selections - marker infers Boolean type
from(Users)
    .selectAs(IsAdult) { users.age gte 18 }  // Boolean
    .execute(transaction)

// Results have type-safe named accessors
results.forEach { row ->
    val total = row.totalRevenue  // Number - properly typed!
    val count = row.orderCount    // Long - properly typed!
    val isAdult = row.isAdult     // Boolean - properly typed!
}
```

**Generates:**

```sql
-- Aggregates
SELECT SUM(cost) AS "total_revenue", COUNT(id) AS "order_count"
FROM "orders"

-- Expressions
SELECT (age >= 18) AS "is_adult"
FROM "users"
```

**Note:** When selecting only aggregates (no regular columns), GROUP BY is not needed. GROUP BY is only required when mixing regular columns with aggregates.

### Subqueries

```kotlin
// Subquery in FROM clause
fromAliased(UserTotals) {
    from(Orders)
        .selectAs(UserName) { orders.userName }
        .selectAs(TotalCost) { sum(orders.cost) }
}
    .selectAll(UserTotals)
    .execute(transaction)
    .forEach { row ->
        val userName = row.userTotals.userName
        val totalCost = row.userTotals.totalCost
    }
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
INSERT INTO orders (id, user_id, product, cost)
VALUES (?, ?, ?, ?)
```

### Auto-Increment Columns (SERIAL and IDENTITY)

Database-generated IDs are automatically excluded from `insert()` parameters:

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
    // No id parameter needed!
)

// Access the generated ID
val generatedId = result.generatedKeys["id"] as Int
println("Created user with ID: $generatedId")
```

**Available auto-increment types:**
- `serial("id")` → SERIAL (Int)
- `bigserial("id")` → BIGSERIAL (Long, for large IDs)
- `smallserial("id")` → SMALLSERIAL (Short, for small IDs)
- `integer("id").identity()` → INTEGER GENERATED ALWAYS AS IDENTITY (SQL standard)
- `bigint("id").identity()` → BIGINT GENERATED ALWAYS AS IDENTITY
- `smallint("id").identity()` → SMALLINT GENERATED ALWAYS AS IDENTITY

**Generates:**

```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL
);

INSERT INTO users (name, email)  -- id excluded!
VALUES (?, ?)
RETURNING id;  -- Generated ID returned automatically
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

## Features

For a complete list of features, see the [CHANGELOG](CHANGELOG.md).

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
