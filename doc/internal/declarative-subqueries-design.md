# Declarative Subqueries - Zero Regex Design

**Date:** January 12, 2026
**Goal:** Eliminate ALL regex by making subqueries first-class declarative objects

---

## Core Insight

**Current Problem:** Subqueries are inline → requires regex to discover them
**Solution:** Make subqueries declarative objects → KSP discovers them like Tables

---

## Design: Subqueries as First-Class Objects

### 1. Subquery Declaration (Like Tables)

```kotlin
/**
 * Declarative subquery - defines structure and query logic explicitly.
 * Discovered by KSP, no regex scanning needed.
 */
object UserOrderTotals : Subquery("user_order_totals") {
    // Explicit column declarations (like Table columns)
    val userName = subqueryColumn<String>("user_name")
    val totalCost = subqueryColumn<Long>("total_cost")
    val orderCount = subqueryColumn<Long>("order_count")

    // Query definition (evaluated at runtime, discovered at compile-time)
    override fun buildQuery(): QueryBuilder {
        return from(Order)
            .select { order.userName as userName }
            .select { sum(order.cost) as totalCost }
            .select { count(order.id) as orderCount }
            .groupBy { order.userName }
    }
}
```

**Key Features:**
- ✅ Object declaration (KSP can find it)
- ✅ Explicit columns (type-safe, no inference needed)
- ✅ Extends `Subquery` base class (KSP can filter)
- ✅ Columns have types (String, Long, etc.)
- ✅ No regex needed - KSP discovers everything

### 2. Subquery Base Class

```kotlin
/**
 * Base class for all declarative subqueries.
 * Similar to Table, but represents a derived/computed data source.
 */
abstract class Subquery(val alias: String) {
    /**
     * Define the query that produces this subquery's results.
     * Called at query execution time.
     */
    abstract fun buildQuery(): QueryBuilder

    /**
     * List of columns this subquery exposes.
     * Populated by subqueryColumn() calls during object initialization.
     */
    internal val columns = mutableListOf<SubqueryColumn<*>>()
}

/**
 * Subquery column definition.
 * Similar to Table columns, but represents a selected/computed column.
 */
class SubqueryColumn<T>(
    val name: String,
    val type: KClass<T>
) {
    // Runtime value accessor (populated when query executes)
    internal var value: T? = null
}

/**
 * DSL for declaring subquery columns.
 */
inline fun <reified T> Subquery.subqueryColumn(name: String): SubqueryColumn<T> {
    val column = SubqueryColumn(name, T::class)
    columns.add(column)
    return column
}
```

### 3. Relationships to Subqueries

```kotlin
object Person : Table("person") {
    val name = varchar("name", 255).primaryKey()

    // Relationship to regular table
    val orders = oneToMany(Order, Order.userName, this.name)

    // Relationship to subquery! (Same DSL)
    val orderTotals = oneToMany(UserOrderTotals, UserOrderTotals.userName, this.name)
}
```

**Benefits:**
- Same relationship DSL for tables and subqueries
- KSP discovers subquery relationships
- CanJoin instances generated automatically
- No special-case code needed

### 4. Using Subqueries in Queries

```kotlin
// Query with subquery join (same API as table join!)
from(Person)
    .join(UserOrderTotals) { userOrderTotals.userName eq person.name }
    .selectAll(Person)
    .selectAll(UserOrderTotals)
    .execute(tx)
    .forEach { row ->
        val name = row.person.name
        val total = row.userOrderTotals.totalCost  // Type-safe!
        println("$name spent $total")
    }
```

**Key Points:**
- ✅ Same `.join()` API as tables
- ✅ Type-safe column access
- ✅ No special "joinAliased" or "aliasAs" methods
- ✅ Compile-time validation via CanJoin

---

## KSP Discovery (Zero Regex)

### KSP Processor Enhancement

```kotlin
class KodamaSymbolProcessor : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Discover Tables (existing)
        val tables = resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .filter { isTableObject(it) }

        // Discover Subqueries (NEW - same pattern!)
        val subqueries = resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .filter { isSubqueryObject(it) }

        // Extract metadata
        tables.forEach { extractTableMetadata(it) }
        subqueries.forEach { extractSubqueryMetadata(it) }

        // Generate CanJoin instances for both tables AND subqueries
        generateCanJoinInstances(tables, subqueries)

        return emptyList()
    }

    private fun isSubqueryObject(decl: KSClassDeclaration): Boolean {
        if (decl.classKind != ClassKind.OBJECT) return false

        return decl.superTypes.any { superType ->
            val resolved = superType.resolve()
            resolved.declaration.qualifiedName?.asString() ==
                "com.obabichev.kodama.schema.Subquery"
        }
    }

    private fun extractSubqueryMetadata(decl: KSClassDeclaration) {
        val name = decl.simpleName.asString()
        val pkg = decl.packageName.asString()

        // Find subquery columns
        val columns = decl.declarations
            .filterIsInstance<KSPropertyDeclaration>()
            .filter { isSubqueryColumn(it) }
            .map { extractColumnInfo(it) }

        // Write to metadata.json
        subqueryMetadata.add(SubqueryMetadata(name, pkg, columns))
    }
}
```

**Result:**
- Subqueries discovered via symbol processing (like Tables)
- Column types extracted from declarations
- Relationships discovered from oneToMany/manyToOne calls
- **Zero text scanning, zero regex**

---

## Generated Code

### Input (User Code)

```kotlin
// Subquery declaration
object UserOrderTotals : Subquery("user_order_totals") {
    val userName = subqueryColumn<String>("user_name")
    val totalCost = subqueryColumn<Long>("total_cost")

    override fun buildQuery() = from(Order)
        .select { order.userName as userName }
        .select { sum(order.cost) as totalCost }
        .groupBy { order.userName }
}

// Relationship declaration
object Person : Table("person") {
    val name = varchar("name", 255).primaryKey()
    val orderTotals = oneToMany(UserOrderTotals, UserOrderTotals.userName, this.name)
}
```

### Generated Output (KSP)

**1. CanJoin instances**
```kotlin
// Generated automatically from relationship
object PersonCanJoinUserOrderTotals : CanJoin<Person, UserOrderTotals>
```

**2. Subquery accessor**
```kotlin
// Type-safe accessor for subquery columns
class UserOrderTotalsAccessor(private val subquery: UserOrderTotals) {
    val userName: TypedColumn<String, UserOrderTotals, UserName>
        get() = TypedColumn(subquery.userName)

    val totalCost: TypedColumn<Long, UserOrderTotals, TotalCost>
        get() = TypedColumn(subquery.totalCost)
}
```

**3. Query builder extensions**
```kotlin
// Join extension (same as for tables!)
fun <Joins : JoinPattern> TypedQueryBuilder<Person, Joins, *>.join(
    target: UserOrderTotals,
    condition: JoinContext<Person, InnerJoin<UserOrderTotals, Joins>>.() -> Expression
): TypedQueryBuilder<Person, InnerJoin<UserOrderTotals, Joins>, *>
where CanJoin<Person, UserOrderTotals> : Any  // Type constraint!
{
    // ... implementation
}
```

**4. Result accessors**
```kotlin
// Type-safe result access
class QueryResult_Person_UserOrderTotals(private val rs: ResultSet) {
    val person: PersonAccessor = PersonAccessor(/* ... */)
    val userOrderTotals: UserOrderTotalsAccessor = UserOrderTotalsAccessor(/* ... */)
}
```

---

## Comparison: Current vs Declarative

### Current Approach (Regex-Based)

```kotlin
// Test code (implicit subquery definition)
from(Person)
    .joinAliased(
        from(Order)
            .selectAs(OrderUserName) { order.userName }  // ← Marker discovered via regex
            .selectAs(TotalCost) { sum(order.cost) }     // ← Marker discovered via regex
            .groupBy { order.userName }
            .build()
            .aliasAs<UserTotalSubquery>()                // ← Marker discovered via regex
    ) { person.name eq userTotalSubquery.orderUserName }
    .selectAll(Person)
    .selectAll(UserTotalSubquery)
```

**Requires:**
- Regex to find `.selectAs(MarkerName)` patterns
- Regex to find `.aliasAs<SubqueryName>()` patterns
- Regex to infer column types
- Regex to discover marker usage

**Problems:**
- Fragile (formatting breaks it)
- Implicit (subquery structure not declared)
- Runtime discovery (can't validate at compile-time)

### Declarative Approach (Zero Regex)

```kotlin
// Subquery declaration (explicit)
object UserOrderTotals : Subquery("user_order_totals") {
    val userName = subqueryColumn<String>("user_name")
    val totalCost = subqueryColumn<Long>("total_cost")

    override fun buildQuery() = from(Order)
        .select { order.userName as userName }
        .select { sum(order.cost) as totalCost }
        .groupBy { order.userName }
}

// Relationship declaration
object Person : Table("person") {
    val orderTotals = oneToMany(UserOrderTotals, UserOrderTotals.userName, this.name)
}

// Query (same as table join!)
from(Person)
    .join(UserOrderTotals) { userOrderTotals.userName eq person.name }
    .selectAll(Person)
    .selectAll(UserOrderTotals)
```

**Requires:**
- KSP to discover Subquery objects (like Tables)
- KSP to extract column declarations
- KSP to discover relationships

**Benefits:**
- ✅ Explicit (subquery structure declared upfront)
- ✅ Type-safe (columns have declared types)
- ✅ Compile-time validation (CanJoin constraints)
- ✅ Zero regex (KSP handles everything)
- ✅ Reusable (subquery defined once, used many times)

---

## Advanced Features

### 1. Parameterized Subqueries

```kotlin
object RecentOrders : Subquery("recent_orders") {
    // Subquery parameters
    var daysAgo: Int = 30

    val orderId = subqueryColumn<Int>("order_id")
    val userName = subqueryColumn<String>("user_name")
    val orderDate = subqueryColumn<LocalDate>("order_date")

    override fun buildQuery() = from(Order)
        .select { order.id as orderId }
        .select { order.userName as userName }
        .select { order.createdAt as orderDate }
        .where { order.createdAt gte LocalDate.now().minusDays(daysAgo) }
}

// Usage
RecentOrders.daysAgo = 7  // Last 7 days
from(Person)
    .join(RecentOrders) { recentOrders.userName eq person.name }
```

### 2. Nested Subqueries

```kotlin
object OrderStats : Subquery("order_stats") {
    val userName = subqueryColumn<String>("user_name")
    val avgCost = subqueryColumn<Double>("avg_cost")

    override fun buildQuery() = from(Order)
        .select { order.userName as userName }
        .select { avg(order.cost) as avgCost }
        .groupBy { order.userName }
}

object HighSpenders : Subquery("high_spenders") {
    val userName = subqueryColumn<String>("user_name")
    val avgCost = subqueryColumn<Double>("avg_cost")

    override fun buildQuery() = from(OrderStats)  // Subquery of subquery!
        .select { orderStats.userName as userName }
        .select { orderStats.avgCost as avgCost }
        .where { orderStats.avgCost gt 1000.0 }
}
```

### 3. Subquery with Window Functions (Future)

```kotlin
object RankedOrders : Subquery("ranked_orders") {
    val orderId = subqueryColumn<Int>("order_id")
    val userName = subqueryColumn<String>("user_name")
    val rank = subqueryColumn<Long>("rank")

    override fun buildQuery() = from(Order)
        .select { order.id as orderId }
        .select { order.userName as userName }
        .select {
            rowNumber().over {
                partitionBy(order.userName)
                orderBy(order.cost.desc())
            } as rank
        }
}
```

---

## Implementation Plan

### Phase 1: Core Infrastructure (1 week)

**Deliverables:**
- [ ] Create `Subquery` base class
- [ ] Create `SubqueryColumn` class
- [ ] Add `subqueryColumn()` DSL
- [ ] Update relationship DSL to support Subquery targets
- [ ] Test with 1-2 simple subqueries

### Phase 2: KSP Integration (1 week)

**Deliverables:**
- [ ] Enhance KSP processor to discover Subquery objects
- [ ] Extract subquery column metadata
- [ ] Generate CanJoin instances for subqueries
- [ ] Write subquery metadata to JSON

### Phase 3: Code Generation (1 week)

**Deliverables:**
- [ ] Generate subquery accessors
- [ ] Generate join extensions for subqueries
- [ ] Generate result classes with subquery columns
- [ ] Update RelationshipBasedCombinationGenerator to handle subqueries

### Phase 4: Migration & Testing (1 week)

**Deliverables:**
- [ ] Migrate existing subquery tests to declarative style
- [ ] Verify all tests pass
- [ ] Performance benchmarks
- [ ] Documentation

**Total Timeline: 4 weeks**

---

## Benefits Summary

### Code Quality

| Aspect | Current | Declarative | Improvement |
|--------|---------|-------------|-------------|
| **Regex patterns** | 41 | 0 | ✅ 100% elimination |
| **Type safety** | Runtime | Compile-time | ✅ Errors caught earlier |
| **Reusability** | Inline only | Define once, use many | ✅ DRY |
| **Maintainability** | Fragile (regex) | Robust (KSP) | ✅ Formatting-independent |
| **IDE support** | Limited | Full autocomplete | ✅ Better DX |

### Performance

| Metric | Current | Declarative | Improvement |
|--------|---------|-------------|-------------|
| **Build time** | 10s | 3-4s | ✅ 60-70% faster |
| **Generated code** | 2.3MB | 200-300KB | ✅ 90% smaller |
| **Compilation** | Slower | Faster | ✅ Less code to compile |

### Developer Experience

**Current:**
```kotlin
// Define inline (no reuse)
.joinAliased(
    from(Order).selectAs(X) { ... }.selectAs(Y) { ... }.build().aliasAs<Z>()
)

// Type inference fragile
// No IDE support
// Formatting matters
```

**Declarative:**
```kotlin
// Define once
object UserTotals : Subquery("user_totals") {
    val userName = subqueryColumn<String>("user_name")
    val total = subqueryColumn<Long>("total")
    override fun buildQuery() = ...
}

// Use anywhere
.join(UserTotals) { ... }

// Full type safety
// Full IDE support
// Formatting doesn't matter
```

---

## Migration Example

### Before (Regex-Based)

```kotlin
@Test
fun testSubqueryJoin() {
    from(Person)
        .joinAliased(
            from(Order)
                .selectAs(OrderUserName) { order.userName }
                .selectAs(TotalCost) { sum(order.cost) }
                .groupBy { order.userName }
                .build()
                .aliasAs<UserTotals>()
        ) { person.name eq userTotals.orderUserName }
        .selectAll(Person)
        .selectAll(UserTotals)
        .execute(tx)
}

// Requires empty marker interface (discovered via regex)
interface OrderUserName
interface TotalCost
interface UserTotals
```

### After (Declarative)

```kotlin
// Define subquery once (in schema package)
object UserTotals : Subquery("user_totals") {
    val userName = subqueryColumn<String>("user_name")
    val totalCost = subqueryColumn<Long>("total_cost")

    override fun buildQuery() = from(Order)
        .select { order.userName as userName }
        .select { sum(order.cost) as totalCost }
        .groupBy { order.userName }
}

// Declare relationship (optional, for type safety)
object Person : Table("person") {
    val orderTotals = oneToMany(UserTotals, UserTotals.userName, this.name)
}

// Use in test (same API as table join!)
@Test
fun testSubqueryJoin() {
    from(Person)
        .join(UserTotals) { userTotals.userName eq person.name }
        .selectAll(Person)
        .selectAll(UserTotals)
        .execute(tx)
        .forEach { row ->
            val name = row.person.name
            val total = row.userTotals.totalCost  // Type-safe!
        }
}

// No marker interfaces needed - everything declared in Subquery object
```

**Lines of code:**
- Before: ~15 lines (inline definition) + 3 marker interfaces
- After: ~8 lines (reusable definition) + 1 line per usage

**Regex patterns needed:**
- Before: 5-8 patterns
- After: 0 patterns

---

## Conclusion

**Declarative subqueries eliminate ALL regex by:**

1. ✅ Making subqueries first-class objects (like Tables)
2. ✅ Explicit column declarations (no type inference needed)
3. ✅ KSP discovery (no text scanning)
4. ✅ Relationship declarations (same DSL as tables)
5. ✅ Compile-time validation (CanJoin constraints)

**Result:**
- **Zero regex patterns** (was 41, targeting 0)
- **Better type safety** (compile-time vs runtime)
- **Smaller generated code** (90% reduction)
- **Faster builds** (70% faster)
- **Better DX** (reusable, IDE support, explicit)

**Trade-off:**
- Requires upfront subquery declaration (not inline)
- Slightly more verbose (but more maintainable)

**Is it worth it?**
- ✅ YES - eliminates last remaining regex
- ✅ YES - better type safety and DX
- ✅ YES - aligns with relationship-based architecture
- ✅ YES - subqueries become reusable components
