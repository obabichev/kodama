# Kodama Documentation

Welcome to the Kodama documentation! Kodama is a type-safe SQL query builder and ORM for Kotlin and PostgreSQL.

## Documentation

- **[Getting Started](getting-started.md)** - Installation, basic concepts, and query building
- **[Package Configuration](package-configuration.md)** - Configure packages for your project structure
- **[Entity Layer (ORM)](entities.md)** - CRUD operations, relationships, and entity management
- **[Code Generation](code-generation.md)** - How Kodama generates type-safe code

## Quick Start

### 1. Installation

Add Kodama to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.5.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.5.0")

    // SLF4J logging implementation (required)
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
}
```

### 2. Define Tables

```kotlin
object Users : Table("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val age = integer("age")
}
```

### 3. Write Queries

```kotlin
from(Users)
    .selectAll(Users)
    .where { users.age eq 25 }
```

### 4. Execute

```kotlin
withConnection { transaction ->
    val results = queryBuilder.execute(transaction)
    results.forEach { row ->
        println("${row.users.name} is ${row.users.age} years old")
    }
}
```

### 5. Or Use Entity Layer

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)
        println("${user.name} (${user.email})")
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
