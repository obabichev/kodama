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
- Scans test files for `from(...).join(...)` patterns

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
- Scans test files for `from(...).join(...)` patterns
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

### Query Building
- ✅ SELECT queries with type-safe column selection
- ✅ INNER JOIN with type-safe conditions
- ✅ Multiple joins (A → B → C)
- ✅ WHERE clause with `eq` operator
- ✅ ORDER BY clause with `.asc()` and `.desc()`
- ✅ LIMIT and OFFSET for pagination
- ✅ Type-safe results (access only selected columns)
- ✅ Code generation via Gradle plugin
- ✅ SQL injection prevention (prepared statements)
- ✅ Column types: `integer()`, `varchar(length)`
- ✅ Nullable columns with `.nullable()` marker

### Aggregates
- ✅ Aggregate functions: `count()`, `sum()`, `avg()`, `min()`, `max()`
- ✅ Named aggregate selections: `selectAliased(TotalRevenue) { sum(order.cost) }`
- ✅ Explicit GROUP BY with chainable `.groupBy { column }` syntax
- ✅ Type-safe aggregate result accessors

### Data Manipulation
- ✅ INSERT statements with compile-time column validation
  - All columns required as parameters
  - Nullable columns with `Type?` parameter
  - Returns `InsertResult` with `rowsAffected` and `generatedKeys`

## Current Limitations

- Only `eq` operator (no gt, lt, like, etc.)
- No AND/OR boolean combinations in WHERE
- No HAVING clause for aggregate filtering
- No UPDATE, DELETE statements
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
from(Person)
    .select { person.name }
    .where { person.age eq 25 }
```

### Join Query
```kotlin
from(Person)
    .join(Order) { order.userName eq person.name }
    .selectAll(Person)
    .selectAll(Order)
    .where { person.name eq "kodama" }
```

### Multiple Joins
```kotlin
from(Person)
    .join(Order) { order.userName eq person.name }
    .join(Profile) { profile.userName eq person.name }
    .selectAll(Person)
    .selectAll(Order)
    .selectAll(Profile)
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

### ORDER BY Query
```kotlin
from(Person)
    .selectAll(Person)
    .orderBy {
        person.age.desc()
        person.name.asc()
    }
```

### Aggregate Query
```kotlin
from(Order)
    .selectAliased(TotalRevenue) { sum(order.cost) }
    .selectAliased(OrderCount) { count(order.id) }
    .execute(transaction)
    .forEach { row ->
        val revenue = row.totalRevenue  // Named accessor!
        val count = row.orderCount
        println("Total: $revenue from $count orders")
    }
```

### INSERT Statement
```kotlin
// All columns required as parameters
val result = Order.insert(
    transaction = transaction,
    id = 1,
    userName = "kodama",
    product = "Laptop",
    cost = 1500
)

println("Inserted ${result.rowsAffected} row(s)")
result.generatedKeys["id"]?.let { println("Generated ID: $it") }

// Nullable columns must be explicitly passed
Product.insert(
    transaction = transaction,
    id = 1,
    name = "Widget",
    description = null,  // Explicit null required
    price = 100,
    discount = null
)
```

## Important Design Patterns

### 1. Select Methods
Use method chaining for selections:
```kotlin
.selectAll(Person)  // Select all columns from a table
.select { person.name }  // Select specific column - each .select{} returns exactly one column/expression
.selectAliased(TotalRevenue) { sum(order.cost) }  // Named selection with marker token
```

### 2. Lambda Contexts for Type Safety
Each clause has a type-safe context:
- **Join context**: Access to all previously joined tables + new table
- **Select context**: Access to all tables in query - each `.select { }` returns exactly one column/expression
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
- Ensure queries use `from(...).join(...)` pattern
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

## Important Changes

### Package Auto-Detection (v0.2.1+)

Kodama now automatically detects your package structure! The compiler plugin:
- Scans source files for `Table` definitions
- Extracts the package name
- Generates code to `{detectedPackage}.generated`

**No configuration needed for most projects!** But you can override if needed:

```kotlin
kodama {
    schemaPackage.set("com.yourcompany.yourproject.schema")
    generatedPackage.set("com.yourcompany.yourproject.generated")
}
```

### Breaking Change from Earlier Versions

Generated code is now placed in `{schemaPackage}.generated` instead of hardcoded `com.obabichev.kodama.tests.data`. This allows Kodama to work in external projects with any package structure.

## Current State

- Version: 0.3.0 (Alpha)
- **All tests passing** including:
  - QuerySimpleDataClassTests (8 tests)
  - QueryAggregateTests (3 tests)
  - InsertTests (5 tests)
  - QueryOrderByTests (6 tests)
  - QueryLimitOffsetTests (10 tests) ✅ NEW
  - Entity Layer tests
- **Completed Features**:
  - ✅ SELECT with type-safe column selection
  - ✅ INNER JOIN with multiple tables
  - ✅ WHERE with eq operator
  - ✅ ORDER BY with asc/desc
  - ✅ LIMIT and OFFSET for pagination ✅ NEW (v0.3.0)
  - ✅ Aggregate functions (COUNT, SUM, AVG, MIN, MAX)
  - ✅ Explicit GROUP BY with chainable `.groupBy { column }` syntax
  - ✅ INSERT statements with compile-time validation
  - ✅ Nullable column support
  - ✅ Date/Time column types (DATE, TIME, TIMESTAMP, TIMESTAMPTZ, TIMETZ, INTERVAL)
  - ✅ Entity Layer with interface-based entities
  - ✅ One-to-many and many-to-one relationships
- **Documentation updated**: README, ROADMAP, CLAUDE.md with LIMIT/OFFSET examples
- **Ready for next features**: AND/OR combinations, comparison operators, HAVING (see ROADMAP.md Phase 5)
