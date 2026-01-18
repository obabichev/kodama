# Kodama - Type-Safe SQL Query Builder for Kotlin

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0+-blue.svg)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/version-0.5.1-orange.svg)]()

**Kodama** (Kotlin Data Mapper) is a type-safe SQL query builder for Kotlin and PostgreSQL. Unlike traditional ORMs,
Kodama provides 100% compile-time type safety through code generation, eliminating runtime errors and reflection
overhead.

## ✨ Key Features

- **🔒 100% Compile-Time Type Safety**
  - Catch all errors at compile time, not in production
  - **Selection Enforcement**: Can only access tables in results that were explicitly selected
  - Phantom types track query state: which tables are joined AND which are selected
  - Invalid queries simply won't compile

- **🚀 Zero Reflection** - Code generation for maximum performance
- **💎 Fluent DSL** - Natural, readable query syntax
- **🎯 PostgreSQL Optimized** - Designed specifically for PostgreSQL
- **📦 Lightweight** - Minimal dependencies, focused on core functionality
- **✨ Nullable Types** - Full nullability support with `Column<T?>` for optional columns
- **🗂️ Entity Layer** - Interface-based ORM with relationships and identity map
- **🔗 Relationships** - Type-safe one-to-many and many-to-one navigation

### Compile-Time Selection Enforcement

Kodama ensures you can only access tables that were explicitly selected:

```kotlin
// ✅ This compiles - Order was selected
from(Person)
    .join(Order) { order.userName eq person.name }
    .selectAll(Order)
    .execute(tx)
    .forEach { row ->
        val product = row.order.product  // ✅ OK
    }

// ❌ This does NOT compile - Person was not selected
from(Person)
    .join(Order) { order.userName eq person.name }
    .selectAll(Order)
    .execute(tx)
    .forEach { row ->
        val name = row.person.name  // ❌ Compile error!
        //         ^^^^^^^^^^
        // ERROR: No extension function 'person' (S1 : TableNotSelected)
    }
```

**How it works**: The type system tracks each table's selection status independently using phantom types:
- `QueryBuilder_2<PersonMarker, OrderMarker, TableNotSelected, TableSelected, NoSelections>`
- Result accessors require `where SN : TableSelected` constraint
- Invalid access = compile error, not runtime exception

## Quick Start

### Installation

#### Option 1: From Maven Central (Recommended - Coming Soon)

Once published to Maven Central, simply add to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.5.1"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.5.1")

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
    id("com.obabichev.kodama") version "0.5.1"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.5.1")

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

Kodama supports type-safe subqueries in FROM and JOIN clauses with all join types.

#### Subquery in FROM Clause

```kotlin
// Aggregate subquery as the main table
fromAliased(UserTotals) {
    from(Orders)
        .selectAs(UserName) { orders.userName }
        .selectAs(TotalCost) { sum(orders.cost) }
        .groupBy { orders.userName }
        .build()
}
    .selectAll(UserTotals)
    .execute(transaction)
    .forEach { row ->
        val userName = row.userTotals.userName
        val totalCost = row.userTotals.totalCost
    }
```

#### Subquery in JOIN Clause

All join types are supported for subqueries:

```kotlin
// LEFT JOIN with subquery - find all people and their order counts (if any)
from(Person)
    .leftJoinAliased(
        from(Order)
            .selectAs(OrderUserName) { order.userName }
            .selectAs(OrderCount) { count(order.id) }
            .groupBy { order.userName }
            .build()
            .aliasAs<OrderCounts>()
    ) { person.name eq orderCounts.orderUserName }
    .selectAll(Person)
    .selectAll(OrderCounts)
    .execute(transaction)
    .forEach { row ->
        val name = row.person.name                // Non-nullable (left side)
        val orderCount = row.orderCounts.orderCount  // Nullable (right side)
        println("$name has ${orderCount ?: 0} orders")
    }
```

**Available join types for subqueries:**
- `.joinAliased()` / `.innerJoinAliased()` - INNER JOIN (only matching rows)
- `.leftJoinAliased()` - LEFT OUTER JOIN (all left rows, matching right rows)
- `.rightJoinAliased()` - RIGHT OUTER JOIN (matching left rows, all right rows)
- `.fullJoinAliased()` - FULL OUTER JOIN (all rows from both sides)

**Join-type-aware nullability:**
- INNER JOIN: Both sides non-nullable
- LEFT JOIN: Left side non-nullable, right side nullable
- RIGHT JOIN: Left side nullable, right side non-nullable
- FULL OUTER JOIN: Both sides nullable

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

Kodama provides a complete ORM layer with interface-based entities, relationships, lifecycle management, and efficient data loading.

#### Define Entities and Tables

```kotlin
// Entity interfaces
interface User {
    val id: Int
    val name: String
    val email: String

    // Relationships
    fun orders(session: EntitySession): List<UserOrder>
    fun roles(session: EntitySession): List<Role>
}

interface UserOrder {
    val id: Int
    val userId: Int
    val product: String
    val amount: Int

    fun user(session: EntitySession): User
}

interface Role {
    val id: Int
    val name: String

    fun users(session: EntitySession): List<User>
}

// EntityTable definitions with relationships
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
        manyToMany("roles", Roles, UserRoles, UserRoles.userId, UserRoles.roleId, Roles.id)
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

object Roles : EntityTable<Role>("roles") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    init {
        manyToMany("users", Users, UserRoles, UserRoles.roleId, UserRoles.userId, Users.id)
    }
}

object UserRoles : EntityTable<UserRole>("user_roles") {
    val userId = integer("user_id")
    val roleId = integer("role_id")
}
```

#### CRUD Operations

```kotlin
EntitySession(connection).use { session ->
    // CREATE - smart persist (automatically chooses INSERT or UPDATE)
    val user = User(id = 1, name = "Alice", email = "alice@example.com")
    session.persist(user)  // INSERT

    // READ
    val loaded = session.find<User>(1)  // Returns User? (nullable)
    val required = session.get<User>(1)  // Returns User (throws if not found)

    // UPDATE
    val modified = loaded!!.copy(email = "alice.new@example.com")
    session.persist(modified)  // UPDATE (only changed fields)

    // DELETE
    session.remove(user)  // Immediate deletion

    // UPSERT (PostgreSQL)
    session.upsert(user, conflictColumns = listOf(Users.id))

    // Batch operations
    session.persistAll(listOf(user1, user2, user3))
    session.insertAll(listOf(user4, user5, user6))
    session.updateAll(listOf(user7, user8, user9))
    session.removeAll(listOf(user10, user11, user12))
}
```

#### Navigate Relationships

```kotlin
EntitySession(connection).use { session ->
    val user = session.get<User>(1)

    // One-to-many: parent → children
    val orders = user.orders(session)
    orders.forEach { order ->
        println("${order.product}: ${order.amount}")
    }

    // Many-to-one: child → parent
    val firstOrder = orders.first()
    val parentUser = firstOrder.user(session)
    assert(user === parentUser)  // Same instance from identity map!

    // Many-to-many: through junction table
    val roles = user.roles(session)
    println("User has roles: ${roles.map { it.name }}")
}
```

#### Lifecycle Hooks

Register callbacks for validation, audit logging, computed fields:

```kotlin
EntitySession(connection).use { session ->
    // Register listener
    session.registerListener(User::class, object : EntityListener<User> {
        override fun onPrePersist(entity: User, session: EntitySession) {
            require(entity.email.contains("@")) { "Invalid email" }
        }

        override fun onPreUpdate(entity: User, old: User, session: EntitySession) {
            auditLog.log("User ${entity.id} changed: ${old.email} → ${entity.email}")
        }

        override fun onPostLoad(entity: User, session: EntitySession) {
            println("Loaded user: ${entity.name}")
        }
    })

    // Operations trigger hooks
    session.persist(user)  // Validates email, logs changes
}
```

#### Eager Loading (N+1 Prevention)

Batch load relationships to prevent performance issues:

```kotlin
EntitySession(connection).use { session ->
    // Load users
    val users = listOf(
        session.get<User>(1),
        session.get<User>(2),
        session.get<User>(3)
    )

    // Batch load all orders in ONE query (prevents N+1)
    users.withOneToMany<User, UserOrder, Int, Int>(
        session, User::class, "orders",
        UserOrders, UserOrders.userId, { it.id }
    )

    // Access orders - returns cached results, NO additional queries!
    users.forEach { user ->
        val orders = user.orders(session)  // Already loaded ✅
        println("${user.name}: ${orders.size} orders")
    }
}
// Total: 2 queries instead of 1 + N queries
```

#### Key Entity Layer Features

**Core Features:**
- ✅ **Interface-based entities** - Define contract, get implementation for free
- ✅ **Identity map** - Same ID always returns same instance within session
- ✅ **Change tracking** - Automatic dirty detection with snapshots
- ✅ **Zero reflection** - All type safety via code generation

**CRUD Operations:**
- ✅ **Smart persist** - `persist()` automatically chooses INSERT or UPDATE
- ✅ **Explicit operations** - `insert()`, `update()`, `remove()` for explicit control
- ✅ **Batch operations** - `persistAll()`, `insertAll()`, `updateAll()`, `removeAll()`
- ✅ **Upsert support** - PostgreSQL `INSERT ... ON CONFLICT ... DO UPDATE`
- ✅ **Partial updates** - Only changed fields are sent to database

**Relationships:**
- ✅ **One-to-many** - Parent has many children (e.g., User → Orders)
- ✅ **Many-to-one** - Child belongs to parent (e.g., Order → User)
- ✅ **Many-to-many** - Junction table support (e.g., User ↔ Roles)
- ✅ **Lazy loading** - Relationships loaded on-demand
- ✅ **Eager loading** - Batch load to prevent N+1 queries

**Lifecycle Management:**
- ✅ **Entity lifecycle hooks** - Pre/post callbacks for persist, update, delete, load
- ✅ **Validation** - Validate entities before persistence
- ✅ **Audit logging** - Track who changed what and when
- ✅ **Computed fields** - Auto-update timestamps and derived values

**See [Entity Layer Documentation](doc/entities.md) for complete guide with examples.**

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
