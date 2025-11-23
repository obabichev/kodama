# Kodama - Type-Safe SQL Query Builder for Kotlin

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.0+-blue.svg)](https://kotlinlang.org/)

**Kodama** (Kotlin Data Mapper) is a type-safe SQL query builder for Kotlin and PostgreSQL. Unlike traditional ORMs, Kodama provides 100% compile-time type safety through code generation, eliminating runtime errors and reflection overhead.

## ✨ Key Features

- **🔒 Type Safety** - Catch all errors at compile time, not in production
- **🚀 Zero Reflection** - Code generation for maximum performance
- **💎 Fluent DSL** - Natural, readable query syntax
- **🎯 PostgreSQL Optimized** - Designed specifically for PostgreSQL
- **📦 Lightweight** - Minimal dependencies, focused on core functionality

## Quick Start

### Installation

Add to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.1.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.1.0")
}
```

### Define Your Schema

```kotlin
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.schema.primaryKey

object User : Table("users") {
    val id = integer("id").primaryKey()
    val email = varchar("email", 255)
    val age = integer("age")
}

object Order : Table("orders") {
    val id = integer("id").primaryKey()
    val userId = integer("user_id")
    val product = varchar("product", 255)
    val cost = integer("cost")
}
```

### Write Type-Safe Queries

```kotlin
import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq

val queryBuilder = query()
    .from(User)
    .join(Order) { order.userId eq user.id }
    .select {
        +user.email
        +order.product
        +order.cost
    }
    .where {
        user.age eq 25
    }
```

### Execute and Get Results

```kotlin
withConnection { transaction ->
    val results = queryBuilder.execute(transaction)

    results.forEach { row ->
        println("${row.user.email} ordered ${row.order.product} for ${row.order.cost}")

        // ❌ Won't compile - id wasn't selected!
        // println(row.user.id)
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
    .from(User)
    .select {
        +user.name
        +user.email
    }

withConnection { transaction ->
    val results = queryBuilder.execute(transaction)
    val row = results.first()

    println(row.user.name)   // ✅ Compiles - name was selected
    println(row.user.email)  // ✅ Compiles - email was selected
    println(row.user.id)     // ❌ Won't compile - id not selected
    println(row.user.age)    // ❌ Won't compile - age not selected
}
```

## Examples

### Simple Query

```kotlin
query()
    .from(User)
    .select { +user.email }
    .where { user.age eq 25 }
```

**Generates:**
```sql
SELECT email FROM users WHERE age = ?
```

### Join Query

```kotlin
query()
    .from(User)
    .join(Order) { order.userId eq user.id }
    .select {
        +user.email
        +order.product
    }
```

**Generates:**
```sql
SELECT email, product
FROM users
INNER JOIN orders ON orders.user_id = users.id
```

### Multiple Joins

```kotlin
query()
    .from(User)
    .join(Order) { order.userId eq user.id }
    .join(Payment) { payment.orderId eq order.id }
    .select {
        +user.email
        +order.product
        +payment.amount
    }
```

**Generates:**
```sql
SELECT email, product, amount
FROM users
INNER JOIN orders ON orders.user_id = users.id
INNER JOIN payments ON payments.order_id = orders.id
```

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
| PostgreSQL focused | ✅ Yes | ❌ No | ⚠️ Multi-DB | ❌ No |
| Fluent DSL | ✅ Yes | ✅ Yes | ✅ Yes | ⚠️ Limited |
| Code generation | ✅ Gradle | ❌ No | ✅ Maven/Gradle | ❌ No |

## Project Status

**Version**: 0.1.0 (Alpha)

Kodama is in active development. The API is stabilizing but may change as we add features.

- ✅ Core features - Stable
- ✅ Type system - Stable
- ✅ Code generation - Stable
- 🚧 Additional operators - In progress
- 📋 Write operations - Planned

## Requirements

- Kotlin 2.0+
- Gradle 8.0+
- PostgreSQL 12+
- JVM 17+

## Roadmap

See [ROADMAP.md](ROADMAP.md) for detailed plans.

**Upcoming releases:**

- **v0.2.0** - ORDER BY, LIMIT, OFFSET, more operators
- **v0.3.0** - Aggregates, GROUP BY, HAVING
- **v0.4.0** - INSERT, UPDATE, DELETE
- **v1.0.0** - Production-ready with full PostgreSQL support

## Contributing

Contributions are welcome! Please:

1. Check the [roadmap](ROADMAP.md) for planned features
2. Open an issue to discuss your idea
3. Submit a PR with tests and documentation

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
