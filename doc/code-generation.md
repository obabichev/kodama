# Code Generation

## Overview

Kodama uses Gradle-based code generation to create type-safe query builders, result accessors, and entity implementations. This eliminates runtime reflection and ensures complete type safety at compile time.

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
from(Person)
    .join(Order) { order.userName eq person.name }
    .selectAll(Person)
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
    id("com.obabichev.kodama") version "0.2.0"
}
```

## What Gets Generated

Kodama generates several types of code based on your schema and queries:

### 1. Query Builders

When you write this query:

```kotlin
from(Person)
    .join(Order) { order.userName eq person.name }
    .selectAll(Person)
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

### 2. INSERT Methods

For each table, Kodama generates a type-safe `insert()` extension method:

**Your Schema:**
```kotlin
object Order : Table("order") {
    val id = integer("id").primaryKey()
    val userName = varchar("user_name", 255)
    val product = varchar("product", 255)
    val cost = integer("cost")
}
```

**Generated INSERT Method:**
```kotlin
fun Order.insert(
    transaction: JdbcTransaction,
    id: Int,
    userName: String,
    product: String,
    cost: Int
): InsertResult {
    val table = this
    val insert = InsertStatement(
        table = table,
        columns = listOf(table.id, table.userName, table.product, table.cost),
        values = listOf(id, userName, product, cost)
    )
    return transaction.executeInsert(insert)
}
```

**Key Features:**
- All columns are required parameters (compile-time safety)
- Nullable columns have `Type?` parameter
- Returns `InsertResult` with `rowsAffected` and `generatedKeys`
- Parameter names match column names exactly

### 3. Aggregate Methods with Marker Interfaces

Kodama uses a marker interface pattern for type-safe aggregate selections:

**Your Query:**
```kotlin
from(Order)
    .selectAliased(TotalRevenue) { sum(order.cost) }
    .selectAliased(OrderCount) { count(order.id) }
```

**How Markers Work:**
1. Generator scans test files for `.selectAliased(MarkerName)` patterns
2. Extracts marker names and tracks which tables they're used with
3. Generates marker interfaces automatically
4. Only generates `selectAliased()` methods for table+marker combinations that are actually used

**Generated Marker Interfaces:**
```kotlin
// Marker for TotalRevenue - infers Number type from sum()
interface TotalRevenue<out T> {
    companion object : TotalRevenue<Number>
}

// Marker for OrderCount - infers Long type from count()
interface OrderCount<out T> {
    companion object : OrderCount<Long>
}
```

**Generated selectAliased() Methods:**
```kotlin
// Only generated for Order+TotalRevenue since that's what tests use
fun <OrderSel> AfterFromQueryBuilder_Order<OrderSel>.selectAliased(
    marker: TotalRevenue<Number>,
    block: SelectContext_Order.() -> AggregateFunction<*>
): SelectionResult_Order_TotalRevenue

// Only generated for Order+OrderCount since that's what tests use
fun <OrderSel> AfterFromQueryBuilder_Order<OrderSel>.selectAliased(
    marker: OrderCount<Long>,
    block: SelectContext_Order.() -> AggregateFunction<*>
): SelectionResult_OrderCount
```

**Generated Result Classes:**
```kotlin
// Result class for TotalRevenue selection
class SelectionResult_Order_TotalRevenue(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    private val selectables: List<Selectable>
) : QueryResult {
    val totalRevenue: Number  // Type inferred from sum() aggregate

    init {
        // Validates alias and caches value from result set
        totalRevenue = selectables[0].getValue(resultSet, selectedColumns.size + 1) as Number
    }
}

// Result class for OrderCount selection
class SelectionResult_OrderCount(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    private val selectables: List<Selectable>
) : QueryResult {
    val orderCount: Long  // Type inferred from count() aggregate

    init {
        // Validates alias and caches value from result set
        orderCount = selectables[0].getValue(resultSet, selectedColumns.size + 1) as Long
    }
}
```

**Type Safety:**
- Marker names become result property names (TotalRevenue → totalRevenue)
- Each aggregate type is inferred: count() → Long, sum() → Number, avg() → Double
- Only selected aggregates have accessors on result
- Compile-time error if you access non-selected aggregates
- Optimized generation: only creates methods for actually-used combinations

### 4. Entity Layer Code

For interface-based entities, Kodama generates:

**Your Entity Interface:**
```kotlin
interface User {
    val id: Int
    val name: String
    val email: String

    context(session: EntitySession)
    fun orders(): List<UserOrder>
}

object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
    }
}
```

**Generated Implementation:**
```kotlin
// 1. Internal data class implementation
internal data class UserImpl(
    override val id: Int,
    override val name: String,
    override val email: String
) : User {
    context(session: EntitySession)
    override fun orders(): List<UserOrder> {
        return session.findByForeignKey<UserOrder, Int, Int>(
            UserOrders, UserOrders.userId, this.id
        )
    }
}

// 2. Factory function
fun User(id: Int, name: String, email: String): User =
    UserImpl(id, name, email)

// 3. Copy extension function
fun User.copy(
    id: Int = this.id,
    name: String = this.name,
    email: String = this.email
): User = (this as UserImpl).copy(id, name, email)
```

**Generated EntityBinding:**
```kotlin
object UserEntityBinding : EntityBinding<User, Int> {
    override val table: EntityTable<User> = Users

    override fun toEntity(resultSet: ResultSet): User {
        return User(
            id = resultSet.getInt("id"),
            name = resultSet.getString("name"),
            email = resultSet.getString("email")
        )
    }

    override fun entityId(entity: User): Int = entity.id

    override fun primaryKeyColumns(): List<Column<*>> = listOf(Users.id)

    // ... INSERT/UPDATE methods
}
```

**Benefits:**
- Interface-based entities stay clean (no implementation details)
- Factory functions for easy construction
- Copy functions preserve interface type
- EntityBindings handle database mapping
- Relationship methods auto-generated

### 5. ORDER BY Methods

Kodama generates chainable `orderBy()` methods for type-safe sorting:

**Your Query:**
```kotlin
from(Person)
    .selectAll(Person)
    .orderBy { person.age.desc() }
    .orderBy { person.name.asc() }
```

**Generated OrderByAccessor:**
```kotlin
class PersonOrderByAccessor(private val tableAccessor: TableAccessor) {
    val name get() = Person.name
    val age get() = Person.age
}
```

**Generated OrderByContext:**
```kotlin
class OrderByContext_Person(state: QueryState) : OrderByContext() {
    val person = PersonOrderByAccessor(state.relations.relation(Person))
}
```

**Generated orderBy() Method:**
```kotlin
fun <PersonSel, AC : AggCount> AfterFromQueryBuilder_Person<PersonSel, AC>.orderBy(
    block: OrderByContext_Person.() -> OrderByClause
): AfterFromQueryBuilder_Person<PersonSel, AC> {
    val context = OrderByContext_Person(state)
    val clause = context.block()
    state._orderBy.add(clause)
    return this
}
```

**Key Features:**
- Chainable API - each call adds one ORDER BY clause
- Type-safe column access through generated accessor
- Returns `OrderByClause` from `.asc()` and `.desc()` extension functions
- Maintains builder type for further chaining

### 6. GROUP BY Methods

Kodama generates chainable `groupBy()` methods for explicit grouping:

**Your Query:**
```kotlin
from(Order)
    .select { order.userName }
    .selectAliased(OrderCount) { count(order.id) }
    .groupBy { order.userName }
```

**Generated GroupByAccessor:**
```kotlin
class OrderGroupByAccessor(private val tableAccessor: TableAccessor) {
    val id get() = Order.id
    val userName get() = Order.userName
    val product get() = Order.product
    val cost get() = Order.cost
}
```

**Generated GroupByContext:**
```kotlin
class GroupByContext_Order(state: QueryState) : GroupByContext() {
    val order = OrderGroupByAccessor(state.relations.relation(Order))
}
```

**Generated groupBy() Method:**
```kotlin
fun <OrderSel, AC : AggCount> AfterFromQueryBuilder_Order<OrderSel, AC>.groupBy(
    block: GroupByContext_Order.() -> Column<*>
): AfterFromQueryBuilder_Order<OrderSel, AC> {
    val context = GroupByContext_Order(state)
    val column = context.block()
    state._groupBy.add(column)
    return this
}
```

**Key Features:**
- Chainable API - each call adds one GROUP BY column
- Type-safe column access through generated accessor
- Returns `Column<*>` directly
- Required when mixing regular columns with aggregates
- Not needed for aggregates-only queries

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
// Finds: from(Person).join(Order)...
val queryChainPattern = """from\s*\([^)]+\)(?:\s*\.join\s*\([^)]+\)(?:\s*\{[^}]*\})?)*""".toRegex()
```

### Marker Discovery

Generator scans test files for aggregate marker usage:

```kotlin
// Finds: .selectAliased(MarkerName) { ... }
val markerPattern = """\.selectAliased\s*\(\s*([A-Z]\w+)\s*\)""".toRegex()

// Also scans inside subquery blocks
val subqueryPattern = """(?:fromAliased|joinAliased|leftJoinAliased)\s*\([A-Z]\w+\)\s*\{...\}""".toRegex()
```

**Marker Tracking:**
- Tracks which markers are used with which table combinations
- Only generates `selectAliased()` methods for actually-used combinations
- Example: If TotalRevenue is only used with Order table, only generates `AfterFromQueryBuilder_Order.selectAliased(TotalRevenue)`
- Optimizes generated code size by avoiding unnecessary methods

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

### Query DSL Code
```
build/generated/kodama/com/obabichev/kodama/tests/data/QueryExtensions.kt
```

### Entity Layer Code
```
build/generated/kodama/com/obabichev/kodama/tests/
├── entity/
│   ├── impl/
│   │   ├── UserImpl.kt              # Generated implementations
│   │   └── UserOrderImpl.kt
│   └── bindings/
│       ├── UserEntityBinding.kt     # Database mappings
│       └── UserOrderEntityBinding.kt
└── KodamaBindingRegistry.kt         # Auto-registration
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
val queryBuilder = from(Person)
    .selectAll(Person)

// ❌ Won't be detected (split across variables)
val fromBuilder = from(Person)
val selectBuilder = fromBuilder.selectAll(Person)
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
from(Person).join(Order) { ... }

// ❌ Avoid splitting
val fromBuilder = from(Person)
val builder = fromBuilder.join(Order) { ... }
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
2. Scan test files for query patterns and marker usage
3. Extract table combinations and marker-table associations
4. Generate type-safe extension functions (from, join, select, selectAliased, orderBy, groupBy)
5. Generate marker interfaces and result classes
6. Output to `build/generated/kodama/`
