# Package Configuration

This guide explains how Kodama handles package detection and configuration for code generation.

## Overview

Kodama needs to know two things to generate code correctly:
1. **Schema Package** - Where your `Table` definitions are located
2. **Generated Package** - Where to place the generated query extensions

## Auto-Detection (Default)

By default, Kodama automatically detects your package structure:

```kotlin
// No configuration needed!
plugins {
    id("com.obabichev.kodama") version "0.4.0"
}
```

### How Auto-Detection Works

1. Scans `src/main/kotlin` for Kotlin files
2. Finds files containing `object SomeName : Table(...)`
3. Extracts the package declaration from those files
4. Uses that package as the schema package
5. Generates code to `{schemaPackage}.generated`

### Example

If your tables are defined like this:

```kotlin
// File: src/main/kotlin/com/example/myapp/db/Tables.kt
package com.example.myapp.db

import com.obabichev.kodama.schema.Table

object Users : Table("users") {
    val id = integer("id")
    val name = varchar("name", 255)
}
```

Kodama will:
- Detect schema package: `com.example.myapp.db`
- Generate code to: `com.example.myapp.db.generated`
- Create file: `build/generated/kodama/com/example/myapp/db/generated/QueryExtensions.kt`

## Manual Configuration

You can explicitly configure packages in your `build.gradle.kts`:

```kotlin
kodama {
    // Package where your Table definitions are located
    schemaPackage.set("com.example.myapp.database.schema")

    // Package where generated code will be placed
    generatedPackage.set("com.example.myapp.database.generated")
}
```

### When to Use Manual Configuration

1. **Multiple Package Locations** - If tables are spread across packages, choose one as the base
2. **Custom Generated Location** - If you want generated code in a specific package
3. **Build Consistency** - To ensure package names don't change accidentally

## Package Structure Best Practices

### Recommended Structure

```
src/main/kotlin/
└── com/example/myapp/
    ├── schema/              # All Table definitions here
    │   └── Tables.kt
    ├── model/               # Your business models
    └── repository/          # Your data access code
```

With this structure, use:

```kotlin
kodama {
    schemaPackage.set("com.example.myapp.schema")
    generatedPackage.set("com.example.myapp.schema.generated")
}
```

### Alternative Structure

```
src/main/kotlin/
└── com/example/myapp/
    ├── database/
    │   ├── tables/          # Table definitions
    │   └── queries/         # Hand-written queries
    └── domain/              # Domain models
```

With this structure, use:

```kotlin
kodama {
    schemaPackage.set("com.example.myapp.database.tables")
    generatedPackage.set("com.example.myapp.database.queries.generated")
}
```

## Generated Code Location

Generated code is placed in:
```
build/generated/kodama/{package-path}/
```

For example, with `generatedPackage = "com.example.myapp.generated"`:
```
build/generated/kodama/com/example/myapp/generated/QueryExtensions.kt
```

## Imports in Your Code

After code generation, import the generated extensions:

```kotlin
package com.example.myapp.repository

import com.example.myapp.schema.*  // Your tables
import com.example.myapp.schema.generated.*  // Generated extensions
import com.obabichev.kodama.query.*

class UserRepository {
    fun findUserByName(name: String) = from(Users)
        .selectAll(Users)  // From schema package with extension from generated package
        .where { users.name eq name }
}
```

## Troubleshooting

### Issue: "Unresolved reference" errors

**Cause:** Generated code package doesn't match imports

**Solution:**
1. Check the Gradle build output for detected packages:
   ```
   Kodama: Using schema package: com.example.myapp.schema
   Kodama: Using generated package: com.example.myapp.schema.generated
   ```
2. Update your imports to match
3. Or configure packages explicitly

### Issue: Generated code in wrong location

**Cause:** Auto-detection found tables in unexpected package

**Solution:** Explicitly configure `schemaPackage` and `generatedPackage`

```kotlin
kodama {
    schemaPackage.set("com.yourcompany.yourproject.schema")
    generatedPackage.set("com.yourcompany.yourproject.generated")
}
```

### Issue: Multiple table packages

**Problem:** Tables spread across multiple packages

**Solution:** Choose one primary package as the schema package, or consolidate tables into one package

```kotlin
// Option 1: Choose primary package
kodama {
    schemaPackage.set("com.example.myapp.schema.core")
}

// Option 2: Consolidate (recommended)
// Move all tables to single package: com.example.myapp.schema
```

## Advanced: Multi-Module Projects

For multi-module projects, configure each module separately:

### Module 1: Core Tables

```kotlin
// core/build.gradle.kts
kodama {
    schemaPackage.set("com.example.core.schema")
    generatedPackage.set("com.example.core.generated")
}
```

### Module 2: Feature-Specific Tables

```kotlin
// feature-auth/build.gradle.kts
kodama {
    schemaPackage.set("com.example.auth.schema")
    generatedPackage.set("com.example.auth.generated")
}
```

## Configuration Reference

### schemaPackage

- **Type:** `Property<String>`
- **Default:** Auto-detected from source files
- **Purpose:** Specifies where `Table` definitions are located
- **Example:** `"com.example.myapp.schema"`

### generatedPackage

- **Type:** `Property<String>`
- **Default:** `{schemaPackage}.generated`
- **Purpose:** Specifies where generated code will be placed
- **Example:** `"com.example.myapp.generated"`

## Logging

Enable Gradle info logging to see package detection:

```bash
./gradlew generateKodamaExtensions --info | grep "Kodama:"
```

Output:
```
Kodama: Auto-detected schema package from Tables.kt: com.example.myapp.schema
Kodama: Using schema package: com.example.myapp.schema
Kodama: Using generated package: com.example.myapp.schema.generated
```

## See Also

- [Getting Started](getting-started.md) - Basic setup and usage
- [Code Generation](code-generation.md) - How code generation works
- [Troubleshooting](#troubleshooting) - Common issues and solutions
