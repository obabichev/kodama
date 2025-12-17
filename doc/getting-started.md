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

Add Kodama to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.2.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.2.0")
}
```

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
```

### 2. Build Queries

Use the fluent DSL to build type-safe queries:

```kotlin
import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq

val queryBuilder = query()
    .from(Person)
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
val queryBuilder = query()
    .from(User)
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
    val results = query()
        .from(Product)
        .select(Product.all())
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

### Aggregate Functions

Kodama supports type-safe aggregate functions with named accessors:

```kotlin
// Simple aggregate query
val results = query()
    .from(Order)
    .select_totalRevenue { sum(order.cost) }
    .select_orderCount { count(order.id) }
    .execute(transaction)

// Access with compile-time safe named accessors
results.forEach { row ->
    val revenue: Number = row.totalRevenue
    val count: Number = row.orderCount
    println("Total: $revenue from $count orders")
}

// Mix columns with aggregates (GROUP BY is automatic)
val byUser = query()
    .from(Order)
    .select { order.userName }  // Regular column selection
    .select_userTotal { sum(order.cost) }  // Named aggregate
    .execute(transaction)

byUser.forEach { row ->
    println("${row.order.userName}: ${row.userTotal}")
}
```

**Available Functions:**
- `count(column)` - Count rows
- `sum(column)` - Sum values
- `avg(column)` - Average value
- `min(column)` - Minimum value
- `max(column)` - Maximum value

### ORDER BY

Sort query results with type-safe column references:

```kotlin
query()
    .from(User)
    .selectAll(User)
    .orderBy {
        +user.age.desc()  // Descending
        +user.name.asc()  // Ascending
    }
    .execute(transaction)
```

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
