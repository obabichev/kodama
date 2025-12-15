# Claude Context for Kodama Project

This file contains essential context for Claude AI sessions working on the Kodama project.

## Project Overview

**Kodama** is a type-safe SQL query builder for Kotlin and PostgreSQL that uses compile-time code generation instead of runtime reflection.

**Core Philosophy**: 100% compile-time type safety - if it compiles, it works.

## Key Architecture Decisions

### 1. Object-Based Table Definitions (NOT Data Classes)

Tables are defined as Kotlin **objects** (singletons), NOT data classes:

```kotlin
// ✅ Correct
object Person : Table("person") {
    val name = varchar("name", 255).primaryKey()
    val age = integer("age")
}

// ❌ OLD approach (removed)
@Table("person")
data class Person(...)
```

### 2. Code Generation Approach

- **Generator scans test files** for query patterns to discover table combinations
- **Generates type-safe builders** for each combination (Person, Person+Order, Person+Order+Profile)
- Generated code is in: `build/generated/kodama/com/obabichev/kodama/tests/data/QueryExtensions.kt`
- Generator task: `generateKodamaExtensions`

### 3. No Reflection

All type safety comes from generated code, not runtime reflection. The `RelationsContainer` was simplified to work directly with Table objects.

## Project Structure

```
kodama/
├── kodama-core/              # Core library (Table, Column, Query, etc.)
├── kodama-compiler-plugin/   # Gradle plugin with code generator
├── kodama-tests/            # Tests and example usage
│   ├── src/main/kotlin/
│   │   └── schema/Tables.kt  # Table definitions (Person, Order, Profile, Company)
│   └── src/test/kotlin/
│       └── QuerySimpleDataClassTests.kt  # Query examples
├── doc/                     # Documentation
│   ├── README.md
│   ├── getting-started.md
│   └── code-generation.md
├── README.md               # Main project README
└── ROADMAP.md             # Feature roadmap
```

## Important Files

### Table Definitions
**Location**: `kodama-tests/src/main/kotlin/com/obabichev/kodama/tests/schema/Tables.kt`

Contains: Person, Order, Profile, Company table objects

### Code Generator
**Location**: `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/KodamaTableBasedCodegenTask.kt`

Key logic:
- Scans schema files for `object X : Table(...)` patterns
- Scans test files for `query().from(...).join(...)` patterns
- Generates all prefix combinations for multi-join support
- Outputs to `build/generated/kodama/`

### Core Query Building
**Location**: `kodama-core/src/main/kotlin/com/obabichev/kodama/query/`

Key files:
- `TypedQueryBuilder.kt` - Query builder base classes
- `RelationsContainer.kt` - Simplified (no reflection)
- `Operators.kt` - Only Column-based operators (no KProperty1)
- `QueryResult.kt` - Type-safe result access

### Table Base Class
**Location**: `kodama-core/src/main/kotlin/com/obabichev/kodama/schema/Table.kt`

Provides DSL: `integer()`, `varchar()`, `primaryKey()`

## Current Features (Implemented)

- ✅ SELECT queries with type-safe column selection
- ✅ INNER JOIN with type-safe conditions
- ✅ Multiple joins (A → B → C)
- ✅ WHERE clause with `eq` operator
- ✅ Type-safe results (access only selected columns)
- ✅ Code generation via Gradle plugin
- ✅ SQL injection prevention (prepared statements)
- ✅ Column types: `integer()`, `varchar(length)`

## Current Limitations

- Only `eq` operator (no gt, lt, like, etc.)
- No AND/OR boolean combinations in WHERE
- No ORDER BY, LIMIT, OFFSET
- No aggregate functions (COUNT, SUM, etc.)
- No GROUP BY, HAVING
- No INSERT, UPDATE, DELETE
- Only INNER JOIN (no LEFT/RIGHT/FULL OUTER)

See `ROADMAP.md` for planned features.

## Common Commands

```bash
# Regenerate code (after schema changes or new queries)
./gradlew generateKodamaExtensions

# Force regeneration
./gradlew clean generateKodamaExtensions --rerun-tasks

# Run tests
./gradlew :kodama-tests:test

# Run specific test
./gradlew :kodama-tests:test --tests "QuerySimpleDataClassTests"

# Build everything
./gradlew build
```

## Query Syntax Examples

### Simple Query
```kotlin
query()
    .from(Person)
    .select { +person.name }
    .where { person.age eq 25 }
```

### Join Query
```kotlin
query()
    .from(Person)
    .join(Order) { order.userName eq person.name }
    .select {
        +person.name
        +order.product
    }
    .where { person.name eq "kodama" }
```

### Multiple Joins
```kotlin
query()
    .from(Person)
    .join(Order) { order.userName eq person.name }
    .join(Profile) { profile.userName eq person.name }
    .select {
        +person.all()
        +order.product
        +profile.contact
    }
```

### Execution
```kotlin
withConnection { transaction ->
    val results = queryBuilder.execute(transaction)
    results.forEach { row ->
        val name = row.person.name as String
        val age = row.person.age as Int
        println("$name is $age years old")
    }
}
```

## Important Design Patterns

### 1. Unary Plus Operator for SELECT
Use `+` to add columns to SELECT:
```kotlin
.select {
    +person.name    // Single column
    +person.all()   // All columns from table
}
```

### 2. Lambda Contexts for Type Safety
Each clause has a type-safe context:
- **Join context**: Access to all previously joined tables + new table
- **Select context**: Access to all tables in query
- **Where context**: Access to all tables in query

### 3. Result Access Pattern
Results provide table-scoped access:
```kotlin
row.person.name   // Access person's name
row.order.cost    // Access order's cost
```

## Major Refactoring History

### Last Major Change: Data Classes → Object-Based Tables

**What changed**:
- Removed `@Table` and `@Column` annotations
- Changed from `data class` to `object : Table(...)`
- Removed reflection from `RelationsContainer`
- Changed from `.from(Person::class)` to `.from(Person)`
- Removed old `execute(query, klass)` method
- Updated all tests to use new syntax

**Files affected**:
- Deleted old data class files (Person.kt, Order.kt, etc. in tests/data/)
- Created new Tables.kt in tests/schema/
- Updated RelationsContainer.kt
- Updated KodamaTableBasedCodegenTask.kt
- Changed column types to objects (IntColumnType, StringColumnType)

## Code Generation Process

1. **Scan**: Generator finds table definitions and query patterns using regex
2. **Extract**: Extracts table combinations from queries
3. **Generate Combinations**: Creates all prefixes (Person, Person+Order, Person+Order+Profile)
4. **Generate Code**: Creates accessors, builders, contexts, and result classes
5. **Output**: Writes to `build/generated/kodama/`

## Testing Database

Tests use PostgreSQL with test data:
- **Person**: ("kodama", age=1)
- **Order**: (id=1, userName="kodama", product="Laptop", cost=1000)
- **Profile**: (userName="kodama", contact="kodama@example.com", photo="photo1.jpg")
- **Company**: (id=1, companyName="Tech Corp")

## When Making Changes

### Adding New Features

1. Update core classes in `kodama-core/`
2. Add tests in `kodama-tests/` (generator will scan them)
3. Run `./gradlew generateKodamaExtensions`
4. Update documentation in `doc/` if it's a user-facing feature
5. Update `ROADMAP.md` if completing a planned feature

### Adding New Query Patterns

1. Write the query in test files (`src/test/kotlin/`)
2. Run generator: `./gradlew generateKodamaExtensions --rerun-tasks`
3. Generator will detect new patterns and create builders

### Troubleshooting

**"0 query combinations"**:
- Generator can't find queries in test files
- Ensure queries use `query().from(...).join(...)` pattern
- Check that queries aren't split across variables

**"Generated code doesn't compile"**:
- Missing table definition
- Check all tables used in queries are defined in schema/Tables.kt

**Tests fail after refactoring**:
- Run `./gradlew clean generateKodamaExtensions --rerun-tasks`
- Check that imports are correct (schema package, not data package)

## Key Principles for Claude

1. **Never use reflection** - All type safety comes from generated code
2. **Tables are objects** - Not data classes, not classes
3. **Query patterns drive generation** - Generator scans test files
4. **Maintain type safety** - Every operation must be type-checked at compile time
5. **Follow existing patterns** - Look at QuerySimpleDataClassTests.kt for examples
6. **Keep documentation minimal** - Only getting-started.md and code-generation.md
7. **Reference ROADMAP.md** - For planned features and priorities

## Quick Reference Links

- Main test file: `kodama-tests/src/test/kotlin/com/obabichev/kodama/tests/QuerySimpleDataClassTests.kt`
- Table definitions: `kodama-tests/src/main/kotlin/com/obabichev/kodama/tests/schema/Tables.kt`
- Code generator: `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/KodamaTableBasedCodegenTask.kt`
- Core query builder: `kodama-core/src/main/kotlin/com/obabichev/kodama/query/TypedQueryBuilder.kt`

## Development Workflow

1. Make changes to core/schema/tests
2. Run `./gradlew generateKodamaExtensions`
3. Run `./gradlew :kodama-tests:test`
4. Update documentation if needed
5. Commit changes

## Current State

- Version: 0.1.0 (Alpha)
- All tests passing (8 tests in QuerySimpleDataClassTests)
- Documentation complete and simplified
- Multiple joins working correctly
- Ready for next feature: ORDER BY + LIMIT/OFFSET (see ROADMAP.md Phase 1)
