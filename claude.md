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

### 2. Code Generation Approach (Hybrid KSP + Runtime Reflection)

Kodama uses a **hybrid approach** for code generation:

1. **KSP (Kotlin Symbol Processing)** - Discovers table definitions at compile-time
   - Finds `object X : Table(...)` declarations
   - Outputs: `build/generated/ksp/main/resources/kodama-ksp-metadata.json`
   - Type-safe, handles custom Table subclasses automatically

2. **Runtime Reflection** - Extracts column metadata after compilation
   - Loads compiled Table classes via URLClassLoader
   - Accesses `Table.relation.columns` to get DSL results
   - Extracts: SQL names, types, nullability, auto-generation flags
   - Files: `RuntimeMetadataExtractor.kt`, `KspMetadataLoader.kt`

3. **Regex Pattern Scanning** - Discovers query usage patterns in test files
   - Scans test files for `from(...).join(...)` patterns
   - Finds `.selectAs(Marker)` patterns
   - Finds `fromAliased()` subquery definitions
   - Pattern-driven: Only generates code for table combinations actually used

**Key Files:**
- KSP Processor: `kodama-ksp-processor/src/main/kotlin/com/obabichev/kodama/ksp/KodamaSymbolProcessor.kt`
- Runtime Extractor: `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/metadata/RuntimeMetadataExtractor.kt`
- Main Generator: `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/KodamaTableBasedCodegenTask.kt`
- Generated Output: `build/generated/kodama/{package}/QueryExtensions.kt`
- Generator Task: `generateKodamaExtensions`

**Task Dependency Chain:**
```
kspKotlin → compileKotlin → generateKodamaExtensions → compileTestKotlin
```

### 3. No Reflection at Runtime

All type safety comes from generated code, not runtime reflection. Runtime reflection is only used **during code generation itself** to extract column metadata from compiled Table classes. The `RelationsContainer` was simplified to work directly with Table objects.

## Project Structure

```
kodama/
├── kodama-core/              # Core library (Table, Column, Query, etc.)
├── kodama-ksp-processor/     # KSP processor for table discovery
│   └── src/main/kotlin/
│       └── com/obabichev/kodama/ksp/
│           ├── KodamaSymbolProcessor.kt      # KSP table discovery
│           ├── KodamaSymbolProcessorProvider.kt
│           └── model/KspTableModel.kt        # KSP metadata models
├── kodama-compiler-plugin/   # Gradle plugin with code generator
│   └── src/main/kotlin/
│       └── com/obabichev/kodama/compiler/
│           ├── KodamaGradlePlugin.kt         # Gradle plugin entry point
│           ├── KodamaTableBasedCodegenTask.kt # Main code generation task
│           └── metadata/
│               ├── MetadataModels.kt          # Metadata data classes
│               ├── KspMetadataLoader.kt       # JSON loader
│               └── RuntimeMetadataExtractor.kt # Runtime reflection
├── kodama-tests/            # Tests and example usage
│   ├── src/main/kotlin/
│   │   └── schema/Tables.kt  # Table definitions (Person, Order, Profile, Company)
│   └── src/test/kotlin/
│       └── QuerySimpleDataClassTests.kt  # Query examples
├── doc/                     # Documentation
│   ├── README.md
│   ├── getting-started.md
│   └── code-generation.md
├── CODE_GENERATION.md       # Detailed code generation architecture
├── README.md               # Main project README
└── ROADMAP.md             # Feature roadmap
```

## Important Files

### Table Definitions
**Location**: `kodama-tests/src/main/kotlin/com/obabichev/kodama/tests/schema/Tables.kt`

Contains: Person, Order, Profile, Company table objects

### Code Generator (Hybrid Approach)

**Main Task**: `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/KodamaTableBasedCodegenTask.kt`

Key logic:
1. **Loads KSP metadata** - Reads JSON file produced by KodamaSymbolProcessor
2. **Extracts runtime metadata** - Uses RuntimeMetadataExtractor to load compiled Table classes and access column metadata via reflection
3. **Scans test files** - Uses regex to find query patterns (`from(...).join(...)`, `.selectAs(Marker)`, subqueries)
4. **Generates code** - Creates type-safe query builders, result classes, and INSERT methods
5. **Outputs to** `build/generated/kodama/`

**KSP Processor**: `kodama-ksp-processor/src/main/kotlin/com/obabichev/kodama/ksp/KodamaSymbolProcessor.kt`
- Discovers `object X : Table(...)` and `object Y : EntityTable<T>(...)` declarations
- Outputs `kodama-ksp-metadata.json` with table names and qualified class names

**Runtime Metadata**: `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/metadata/RuntimeMetadataExtractor.kt`
- Loads compiled Table classes via URLClassLoader
- Accesses `Table.relation.columns` via reflection
- Extracts SQL names, Kotlin types, nullability, and auto-generation flags

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
- ✅ All SQL JOIN types: `join()` / `innerJoin()`, `leftJoin()`, `rightJoin()`, `fullJoin()`
- ✅ Multiple joins (A → B → C)
- ✅ WHERE clause with `eq` operator
- ✅ ORDER BY clause with `.asc()` and `.desc()`
- ✅ LIMIT and OFFSET for pagination
- ✅ Type-safe results (access only selected columns)
- ✅ Code generation via Gradle plugin
- ✅ SQL injection prevention (prepared statements)
- ✅ Column types: `integer()`, `varchar(length)`
- ✅ Nullable columns with `.nullable()` marker

### Marker-Based Selections
- ✅ Unified `.selectAs()` API for columns, aggregates, and expressions
- ✅ Aggregate functions: `count()`, `sum()`, `avg()`, `min()`, `max()`
- ✅ Column selections: `selectAs(PersonName) { person.name }`
- ✅ Expression selections: `selectAs(IsAdult) { person.age gte 18 }`
- ✅ Explicit GROUP BY with chainable `.groupBy { column }` syntax
- ✅ Type-safe result accessors with proper type inference

### Subqueries
- ✅ Subqueries in FROM clause with `fromAliased(Marker) { ... }`
- ✅ Subqueries in JOIN clause with `.joinAliased()` methods
- ✅ All join types for subqueries: `joinAliased()`, `leftJoinAliased()`, `rightJoinAliased()`, `fullJoinAliased()`
- ✅ Type-safe marker-based subquery definitions: `.aliasAs<Marker>()`
- ✅ Automatic nullable properties for subquery columns (to handle outer joins)
- ✅ Join-type-aware nullability:
  - INNER JOIN: Both sides non-nullable
  - LEFT JOIN: Left non-nullable, right nullable
  - RIGHT JOIN: Left nullable, right non-nullable
  - FULL OUTER JOIN: Both sides nullable

### Data Manipulation
- ✅ INSERT statements with compile-time column validation
  - All columns required as parameters
  - Nullable columns with `Type?` parameter
  - Returns `InsertResult` with `rowsAffected` and `generatedKeys`

## Current Limitations

- No HAVING clause for aggregate filtering
- No UPDATE, DELETE statements

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
    .selectAs(PersonName) { person.name }
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

### Join Types

#### INNER JOIN (or join)
```kotlin
// Returns only matching rows from both tables
from(Person)
    .innerJoin(Order) { order.userName eq person.name }
    .selectAll(Person)
    .selectAll(Order)
```

**Note:** `.join()` and `.innerJoin()` are equivalent - use whichever you prefer.

#### LEFT JOIN
```kotlin
// Returns all persons, including those without orders
from(Person)
    .leftJoin(Order) { order.userName eq person.name }
    .selectAll(Person)
    .selectAll(Order)
    .execute(transaction)
    .forEach { row ->
        val name = row.person.name as String
        val product = row.order.product as? String  // Nullable for persons without orders
    }
```

#### RIGHT JOIN
```kotlin
// Returns all orders, including orphaned orders (no matching person)
from(Person)
    .rightJoin(Order) { order.userName eq person.name }
    .selectAll(Person)
    .selectAll(Order)
```

#### FULL OUTER JOIN
```kotlin
// Returns all rows from both tables, with NULLs where there's no match
from(Person)
    .fullJoin(Order) { order.userName eq person.name }
    .selectAll(Person)
    .selectAll(Order)
    .execute(transaction)
    .forEach { row ->
        val personName = row.person.name as? String  // Nullable
        val orderProduct = row.order.product as? String  // Nullable
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
    .selectAs(TotalRevenue) { sum(order.cost) }
    .selectAs(OrderCount) { count(order.id) }
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
.selectAs(PersonName) { person.name }  // Select individual column with named accessor
.selectAs(TotalRevenue) { sum(order.cost) }  // Select aggregate with named accessor
.selectAs(IsAdult) { person.age gte 18 }  // Select expression with named accessor
```

**Unified `.selectAs()` API:**
- Works for columns: `.selectAs(PersonName) { person.name }` → `row.personName`
- Works for aggregates: `.selectAs(TotalRevenue) { sum(order.cost) }` → `row.totalRevenue`
- Works for expressions: `.selectAs(IsAdult) { person.age gte 18 }` → `row.isAdult`
- Type inference from marker interface and expression type
- Generates consistent named accessors for all selections
- **Note**: The old `.select { }` API for individual columns has been removed

### 2. Lambda Contexts for Type Safety
Each clause has a type-safe context:
- **Join context**: Access to all previously joined tables + new table
- **Select context**: Access to all tables in query - each `.selectAs { }` returns exactly one column/expression
- **Where context**: Access to all tables in query

### 3. Result Access Pattern
Results provide table-scoped access:
```kotlin
row.person.name   // Access person's name
row.order.cost    // Access order's cost
```

## Major Refactoring History

### Latest: Modular Generator Architecture (v0.4.0 - January 2026)

**What changed**:
- Refactored from single 242 KB monolithic file to 987 fine-grained generators
- Implemented two-phase code generation (structure-driven + pattern-driven)
- Added phantom type parameters for compile-time selection tracking
- Introduced SqlAliasStyle for configurable SQL naming conventions
- Removed ~470 KB of legacy code

**Key improvements**:
- ✅ 80+ focused generator classes (vs 1 monolithic file)
- ✅ Each generator ~50-150 lines (vs 4,000+ line monolith)
- ✅ Independently testable components
- ✅ Clear separation: data, transform, generate layers
- ✅ 100% test compatibility maintained (149 tests passing)

**Architecture**:
```
kodama-compiler-plugin/
├── GenerateTableMetadataTask.kt      # Phase 1: Structure-driven
├── GenerateQueryExtensionsTask.kt    # Phase 2: Pattern-driven
├── data/                              # Structured data (TableInfo, etc.)
├── transform/                         # DataTransformer
└── generator/
    ├── CodeGenerator.kt               # Interface
    ├── FileGenerator.kt               # Orchestrator
    ├── GeneratorFactory.kt            # Creates 987 generators
    ├── markers/                       # 12 generators
    ├── accessors/                     # 18 generators
    ├── contexts/                      # 6 generators
    ├── builders/                      # 10 generators
    ├── extensions/                    # 24 generators
    └── results/                       # 8 generators
```

**See**: [Refactoring Summary](doc/REFACTORING_SUMMARY.md) for complete details

### Previous: Data Classes → Object-Based Tables

**What changed**:
- Removed `@Table` and `@Column` annotations
- Changed from `data class` to `object : Table(...)`
- Removed reflection from `RelationsContainer`
- Changed from `.from(Person::class)` to `.from(Person)`

## Code Generation Process

### Two-Phase Architecture

#### Phase 1: Structure-Driven (`GenerateTableMetadataTask`)
1. **Load KSP Metadata**: Read table definitions from JSON
2. **Extract Runtime Metadata**: Compile-time reflection on Table objects
3. **Generate TableMetadata.kt**: Metadata classes for all tables
4. **Output**: `build/generated/kodama/TableMetadata.kt`

#### Phase 2: Pattern-Driven (`GenerateQueryExtensionsTask`)
1. **Scan Test Files**: Find query patterns using regex
2. **Discover Combinations**: Extract table combinations (Person, Person+Order, etc.)
3. **Discover Markers**: Find selection markers (TotalRevenue, PersonName, etc.)
4. **Transform to Structured Data**: Convert raw maps to type-safe data classes
5. **Create Generators**: Factory creates 987 fine-grained generators
6. **Generate Code**: Each generator creates its specific construct
7. **Orchestrate Output**: FileGenerator combines all with imports
8. **Output**: `build/generated/kodama/QueryExtensions.kt`

**Key Components**:
- **Data Layer**: TableInfo, ColumnInfo, MarkerInfo (type-safe metadata)
- **Transform Layer**: DataTransformer (raw maps → structured data)
- **Generator Layer**: 80+ generator classes implementing CodeGenerator interface
- **Factory**: GeneratorFactory creates all generators in correct order
- **Orchestrator**: FileGenerator combines generators with imports and package

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

1. **🚫 NO GIT MANIPULATIONS** - Claude is **STRICTLY FORBIDDEN** to:
   - Make commits (`git commit`)
   - Change branches (`git checkout`, `git switch`)
   - Create branches (`git branch`)
   - Push to remote (`git push`)
   - Modify git state in ANY way
   - **ONLY ALLOWED**: Read git history (`git log`, `git status`, `git diff` - read-only operations)
   - **ALWAYS** stage changes with `git add` and let the USER make commits manually
2. **Never use reflection** - All type safety comes from generated code
3. **Tables are objects** - Not data classes, not classes
4. **Query patterns drive generation** - Generator scans test files
5. **Maintain type safety** - Every operation must be type-checked at compile time
6. **Follow existing patterns** - Look at QuerySimpleDataClassTests.kt for examples
7. **Keep documentation minimal** - Only getting-started.md and code-generation.md
8. **Reference ROADMAP.md** - For planned features and priorities

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

- Version: 0.4.0 (Alpha)
- **All tests passing** including:
  - QuerySimpleDataClassTests (8 tests)
  - QueryAggregateTests (3 tests)
  - InsertTests (5 tests)
  - QueryOrderByTests (6 tests)
  - QueryLimitOffsetTests (10 tests)
  - JoinTypesTests (7 tests) ✅ Tests all 4 join types
  - SubqueryTests (10 tests) ✅ Tests subqueries with all join types
  - Entity Layer tests
- **Completed Features**:
  - ✅ SELECT with type-safe column selection
  - ✅ All SQL join types: INNER, LEFT, RIGHT, FULL (for both tables and subqueries) ✅ COMPLETE
  - ✅ Join-type-aware nullability - INNER returns non-nullable, outer joins return appropriate nullability
  - ✅ Subqueries in FROM and JOIN clauses with all join types ✅ NEW
  - ✅ WHERE with eq operator
  - ✅ ORDER BY with asc/desc
  - ✅ LIMIT and OFFSET for pagination
  - ✅ Aggregate functions (COUNT, SUM, AVG, MIN, MAX)
  - ✅ Explicit GROUP BY with chainable `.groupBy { column }` syntax
  - ✅ INSERT statements with compile-time validation
  - ✅ Nullable column support
  - ✅ Date/Time column types (DATE, TIME, TIMESTAMP, TIMESTAMPTZ, TIMETZ, INTERVAL)
  - ✅ Entity Layer with interface-based entities
  - ✅ One-to-many and many-to-one relationships
- **Documentation updated**: README, CHANGELOG, CLAUDE.md with outer join and subquery examples
- **Code Quality**: Removed 13 redundant casts/mapNotNull calls - generated accessors have correct types
- **Ready for next features**: AND/OR combinations, comparison operators, HAVING (see ROADMAP.md Phase 5)
