# Getting Started with Kodama

## Overview

Kodama is a type-safe SQL query builder for Kotlin that provides compile-time safety for your database queries. Unlike traditional ORMs that use reflection and runtime validation, Kodama ensures your queries are correct at compile time.

## Key Features

- **100% Type Safety**: All queries are validated at compile time
- **No Reflection**: Uses code generation instead of runtime reflection
- **Fluent DSL**: Natural, readable query syntax
- **PostgreSQL Support**: Optimized for PostgreSQL databases
- **Type-Safe Results**: Access only the columns you selected, with correct types

## Installation

Add Kodama to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.1.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.1.0")
}
```

## Basic Concepts

### 1. Define Your Tables

Tables are defined as Kotlin objects that extend the `Table` base class:

```kotlin
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.schema.primaryKey

object Person : Table("person") {
    val name = varchar("name", 255).primaryKey()
    val age = integer("age")
}

object Order : Table("order") {
    val id = integer("id").primaryKey()
    val userName = varchar("user_name", 255)
    val product = varchar("product", 255)
    val cost = integer("cost")
}
```

### 2. Build Queries

Use the fluent DSL to build type-safe queries:

```kotlin
import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq

val queryBuilder = query()
    .from(Person)
    .select {
        +person.name
        +person.age
    }
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
    .select {
        +user.email
        +user.age
    }
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

## Next Steps

- [Table Definitions](table-definitions.md) - Learn how to define tables and columns
- [Query Building](query-building.md) - Master the query DSL
- [Joins](joins.md) - Work with multiple tables
- [Type-Safe Results](type-safe-results.md) - Understand result handling
- [Code Generation](code-generation.md) - How Kodama generates code

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
