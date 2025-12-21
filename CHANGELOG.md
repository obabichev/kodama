# Changelog

All notable changes to Kodama will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - TBD (First Release)

### Added

#### DSL Layer (Query Building)
- **SELECT queries** - Type-safe column selection with compile-time validation
  - Select individual columns: `.select { person.name }`
  - Select all columns from table: `.selectAll(Person)`
  - Each `.select { }` block returns exactly one column or expression
  - Chain multiple `.select { }` calls to select multiple columns
- **FROM clause** - Single table and subquery support
  - Basic table queries: `from(Person)`
  - Inline subqueries: `fromAliased(UserTotals) { from(Order)... }`
- **INNER JOIN** - Type-safe multi-table queries
  - Join conditions with type-safe operators
  - Multiple join support (chain 3+ tables)
  - Join with subqueries: `.joinAliased(...)`, `.leftJoinAliased(...)`
- **WHERE clause** - Rich filtering capabilities
  - **Comparison operators**: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`
  - **String pattern matching**: `like`, `ilike`, `startsWith`, `endsWith`, `contains`
  - **NULL checks**: `isNull()`, `isNotNull()`
  - **Range operator**: `between(lower, upper)`
  - **List membership**: `inList(values)`, `notInList(values)`
- **ORDER BY** - Sort query results
  - Ascending/descending: `.asc()`, `.desc()`
  - Multiple column sorting
- **LIMIT and OFFSET** - Pagination support
  - `.limit(count)` - Limit number of rows returned
  - `.offset(count)` - Skip rows for pagination
- **Subqueries** - Type-safe subquery support
  - Subqueries in FROM clause with marker tokens
  - Subqueries in JOIN clause
  - Scalar subqueries in WHERE clause
  - Type-safe subquery column access
  - Nullability preservation in results
- **Aggregate functions** - Statistical operations with type inference
  - Functions: `count()`, `sum()`, `avg()`, `min()`, `max()`
  - Named selections: `.selectAliased(TotalRevenue) { sum(order.cost) }`
  - Proper type inference: count → Long?, sum → Long?, avg → Double?
  - Aggregates are nullable for LEFT JOIN safety
  - Type-safe result accessors
- **Expression selections** - Computed columns and boolean expressions
  - Boolean expressions: `.selectAliased(IsOld) { person.age gt 30 }`
  - Type inference for expression results
  - Auto-generated marker interfaces
- **GROUP BY** - Explicit grouping for aggregates
  - `.groupBy { column }` method returns one column
  - Chainable: `.groupBy { col1 }.groupBy { col2 }`
  - Type-safe column selection in GROUP BY clause
  - Required when mixing columns with aggregates
- **INSERT statements** - Type-safe data insertion
  - Generated extension methods on table objects
  - All columns required as parameters (compile-time validation)
  - Nullable columns with `Type?` parameter
  - Returns `InsertResult` with `rowsAffected` and `generatedKeys`
  - Proper NULL handling with PreparedStatement

#### Entity Layer (ORM)
- **Interface-based entities** - Define entities as interfaces
  - Auto-generated internal data class implementations
  - Factory functions for entity construction
  - No manual implementation needed
- **EntityTable** - Table definitions with entity types
  - Generic `EntityTable<T>` for type-safe table mapping
  - Column definitions with type mapping
  - Relationship declarations
- **EntitySession** - Session management with identity map
  - Identity map ensures same ID returns same instance
  - Transaction-scoped sessions
  - Automatic ResultSet to entity mapping
- **CRUD operations** - Type-safe entity operations
  - `get<Entity>(id)` - Get entity by ID (throws if not found)
  - `find<Entity>(id)` - Find entity by ID (returns nullable)
  - `save<Entity, ID>(entity)` - Insert new entities
  - `delete(entity)` - Delete entities
  - `flush()` - Commit pending changes to database
- **Relationships** - Type-safe entity relationships
  - **One-to-many**: Navigate from parent to children
  - **Many-to-one**: Navigate from child to parent
  - **Bidirectional**: Navigate both directions seamlessly
  - Relationship methods declared in entity interfaces
  - Auto-generated relationship implementations
  - Context parameters (Kotlin 2.2.0) for clean syntax
  - Identity map integration for referential consistency
  - Lazy loading (relationships loaded on-demand)

#### Type System
- **Nullable columns** - Full nullability support
  - `.nullable()` modifier changes `Column<T>` to `Column<T?>`
  - Result accessors respect nullability
  - NULL values properly handled from database
  - Compile-time type safety for nullable/non-nullable columns
- **Column types** - Comprehensive PostgreSQL type support
  - **Numeric**: INTEGER, BIGINT, SMALLINT, DECIMAL, REAL, DOUBLE PRECISION
  - **String**: VARCHAR, TEXT
  - **Boolean**: BOOLEAN
  - **Date/Time**: DATE, TIME, TIMESTAMP, TIMESTAMPTZ, TIMETZ, INTERVAL
    - Maps to Java Time API (LocalDate, LocalTime, LocalDateTime, OffsetDateTime, OffsetTime, Duration)
    - Timezone-aware types
    - Full integration with queries and INSERT
- **Type-safe results** - Only access what you selected
  - Result types match selections exactly
  - Compile-time errors for accessing unselected columns
  - Proper types for all columns and expressions

#### Code Generation
- **Gradle plugin** - Automatic code generation
  - Scans schema files for table definitions
  - Scans test files for query patterns
  - Generates type-safe query builders
  - Generates result classes
  - Generates entity implementations
  - Task: `generateKodamaExtensions`
- **Zero reflection** - All type safety at compile time
  - No runtime reflection overhead
  - Maximum performance
  - Complete type information available to IDE

### Technical Details

- **Kotlin 2.2.0+** - Uses context parameters for entity relationships
- **PostgreSQL 12+** - Optimized for PostgreSQL
- **Gradle 8.0+** - Build system integration
- **JVM 17+** - Target platform

## [0.1.0] - 2024-10

### Added
- Project initialized
- Basic project structure and build configuration
- Initial implementation of core concepts

---

## Version History

- **0.2.0** (Upcoming) - First public release with full feature set
- **0.1.0** - Project initialization

---

## Roadmap

See [ROADMAP.md](ROADMAP.md) for planned features in future releases.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
