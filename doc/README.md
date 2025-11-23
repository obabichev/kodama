# Kodama Documentation

Welcome to the Kodama documentation! Kodama is a type-safe SQL query builder for Kotlin and PostgreSQL.

## Documentation

- **[Getting Started](getting-started.md)** - Installation, basic concepts, and how to use Kodama
- **[Code Generation](code-generation.md)** - How Kodama generates type-safe code
- **[Roadmap](../ROADMAP.md)** - Planned features and development roadmap

## Quick Start

### 1. Installation

Add Kodama to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.1.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.1.0")
}
```

### 2. Define Tables

```kotlin
object Person : Table("person") {
    val name = varchar("name", 255).primaryKey()
    val age = integer("age")
}
```

### 3. Write Queries

```kotlin
query()
    .from(Person)
    .select { +person.name }
    .where { person.age eq 25 }
```

### 4. Execute

```kotlin
withConnection { transaction ->
    val results = queryBuilder.execute(transaction)
    results.forEach { row ->
        println(row.person.name)
    }
}
```

## Philosophy

Kodama provides **100% compile-time type safety** for SQL queries:

- ✅ If it compiles, it works
- ✅ No reflection - uses code generation
- ✅ Catch errors at compile time, not runtime
- ✅ Type-safe results - access only selected columns

## Learn More

- Read the [Getting Started Guide](getting-started.md) for detailed usage
- Understand [Code Generation](code-generation.md) to see how it works
- Check the [Roadmap](../ROADMAP.md) for upcoming features
