# Code Generation

## Overview

Kodama uses Gradle-based code generation to create type-safe query builders and result accessors. This eliminates runtime reflection and ensures complete type safety at compile time.

## How It Works

### 1. You Define Tables

```kotlin
object Person : Table("person") {
    val name = varchar("name", 255)           // Column<String>
    val age = integer("age")                  // Column<Int>
    val bio = varchar("bio", 500).nullable()  // Column<String?>
}
```

### 2. You Write Queries

```kotlin
query()
    .from(Person)
    .join(Order) { order.userName eq person.name }
    .select {
        +person.name
        +order.product
    }
```

### 3. Generator Scans Your Code

During the Gradle build, Kodama scans:
- **Schema files** → Discovers table definitions
- **Test files** → Discovers query patterns (which tables are joined together)

### 4. Generator Creates Type-Safe Code

For each table combination found, Kodama generates:
- **Table Accessors** - `person.name`, `person.age`, `person.all()`
- **Query Builders** - Type-safe builder for each table combination
- **Join Extensions** - Type-safe join methods
- **Result Classes** - Type-safe access to query results

## Running Code Generation

### Automatic

Code generation runs automatically during builds:

```bash
./gradlew build  # Includes generation
```

### Manual

Force regeneration:

```bash
./gradlew generateKodamaExtensions

# Clean and regenerate
./gradlew clean generateKodamaExtensions --rerun-tasks
```

### Setup

Add the plugin to `build.gradle.kts`:

```kotlin
plugins {
    id("com.obabichev.kodama") version "0.1.0"
}
```

## What Gets Generated

### Example: Person + Order Query

When you write this query:

```kotlin
query()
    .from(Person)
    .join(Order) { order.userName eq person.name }
    .select {
        +person.name
        +order.product
    }
```

Kodama generates:

**1. Table Accessors**
```kotlin
class PersonAccessor(private val tableAccessor: TableAccessor) {
    fun all() = tableAccessor.all()
    val name get() = Person.name
    val age get() = Person.age
}
```

**2. Query Builder**
```kotlin
class AfterFromQueryBuilder_Person_Order(
    override val state: QueryState
) : AfterFromQueryBuilderBase
```

**3. Join Extension**
```kotlin
fun AfterFromQueryBuilder_Person.join(
    table: Order,
    type: JoinType = JoinType.INNER,
    condition: JoinContext_Person_Order.() -> Pair<Column<*>, Column<*>>
): AfterFromQueryBuilder_Person_Order
```

**4. Select Context**
```kotlin
class SelectContext_Person_Order(private val state: QueryState) {
    val person = PersonAccessor(...)
    val order = OrderAccessor(...)
}
```

**5. Result Class**
```kotlin
class QueryResult_Person_Order(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    private val selectedColumns: List<Column<*>>
) {
    val person = PersonResultAccessor(...)
    val order = OrderResultAccessor(...)
}
```

## How Scanning Works

### Table Discovery

Generator uses regex to find table definitions:

```kotlin
// Finds: object Person : Table("person") { ... }
val tablePattern = """object\s+(\w+)\s*:\s*Table\s*\([^)]*\)\s*\{([^}]*)\}""".toRegex()
```

### Query Pattern Discovery

Generator scans test files for query chains:

```kotlin
// Finds: query().from(Person).join(Order)...
val queryChainPattern = """query\s*\(\s*\)\s*\.from\s*\([^)]+\)(?:\s*\.join\s*\([^)]+\)(?:\s*\{[^}]*\})?)*""".toRegex()
```

### Combination Generation

For each query, generates all prefixes:

```kotlin
// Query: Person → Order → Profile
// Generates:
// 1. Person
// 2. Person + Order
// 3. Person + Order + Profile
```

This ensures all intermediate builders exist for type-safe chaining.

### Nullability Tracking

Generator extracts nullability information from table definitions:

```kotlin
// Scans for .nullable() calls
val propertyPattern = """val\s+(\w+)\s*=\s*(varchar|integer|...)\s*\([^)]*\)([^\n]*)""".toRegex()

// Checks if column is marked nullable
val isNullable = modifiers.contains(".nullable()")
```

Generated result accessors respect nullability:

```kotlin
// For: val description = varchar("description", 500).nullable()
// Generates:
class ProductResultAccessor_All(...) {
    val id: Int             // Non-nullable
    val description: String?  // Nullable - matches Column<String?> type
}
```

## Generated File Location

```
build/generated/kodama/com/obabichev/kodama/tests/data/QueryExtensions.kt
```

## Build Integration

### Compilation Order

```
1. Compile core library
2. Compile your table definitions
3. Run code generation (scans tables and queries)
4. Compile generated code
5. Compile your test code (uses generated builders)
```

### Caching

The generation task is cached:

```kotlin
@CacheableTask
abstract class KodamaTableBasedCodegenTask : DefaultTask()
```

**Benefits:**
- Skipped if inputs unchanged
- Fast incremental builds
- Cached across builds

## Configuration

### Source Directories

```kotlin
tasks.named<KodamaTableBasedCodegenTask>("generateKodamaExtensions") {
    testDir.set(project.file("src/test/kotlin"))
    schemaDir.set(project.file("src/main/kotlin"))
    outputDir.set(project.file("build/generated/kodama"))
}
```

## Debugging

### View What Was Generated

```bash
./gradlew generateKodamaExtensions

# Output shows:
# Kodama: Generated 4 tables, 4 query combinations
```

### Inspect Generated Code

```bash
cat build/generated/kodama/com/obabichev/kodama/tests/data/QueryExtensions.kt
```

## Common Issues

### "0 query combinations"

**Problem**: Generator can't find queries.

**Solution**: Ensure queries are in test files and use correct syntax:

```kotlin
// ✅ Will be detected
val queryBuilder = query()
    .from(Person)
    .select { +person.all() }

// ❌ Won't be detected (split across variables)
val q = query()
val fromBuilder = q.from(Person)
```

### "Generated code doesn't compile"

**Problem**: Missing table definition.

**Solution**: Ensure all referenced tables exist:

```kotlin
object Order : Table("order") {
    val id = integer("id")
    val userName = varchar("user_name", 255)
}
```

## Best Practices

### 1. Keep Queries in Test Files

Generator only scans test directories:

```
src/test/kotlin/     ✓ Scanned
src/main/kotlin/     ✗ Not scanned for queries
```

### 2. Use Consistent Query Style

```kotlin
// ✅ Good
query().from(Person).join(Order) { ... }

// ❌ Avoid splitting
val q = query()
val builder = q.from(Person)
```

### 3. Regenerate After Schema Changes

```bash
./gradlew generateKodamaExtensions
```

## Implementation

Generator location:
```
kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/KodamaTableBasedCodegenTask.kt
```

Key steps:
1. Scan schema files for table definitions
2. Scan test files for query patterns
3. Extract table combinations
4. Generate type-safe extension functions
5. Output to `build/generated/kodama/`
