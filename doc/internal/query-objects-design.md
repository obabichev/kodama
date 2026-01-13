# Query Objects - Zero Regex, Inline-Friendly Design

**Date:** January 12, 2026
**Goal:** Design query/subquery system that's discoverable (zero regex) AND has great inline UX

---

## Core Problem

**Conflicting requirements:**
1. ✅ **Zero regex** → KSP discovery → Need declarations (objects/classes)
2. ✅ **Inline UX** → See whole query in one place → Need code in function bodies
3. ✅ **Parameters** → Dynamic WHERE clauses, limits, etc. → Need runtime values
4. ✅ **Nested subqueries** → Subqueries within subqueries → Need composability

**Key insight:** KSP can only discover **declarations**, not code in function bodies.

---

## Solution: Hybrid Query Objects

**Query objects define STRUCTURE** (KSP discovers) + **Inline usage adds PARAMETERS** (runtime)

### 1. Query Object (Structure Declaration)

```kotlin
/**
 * Reusable query object - defines structure, not parameters.
 * KSP discovers the structure, columns, and types.
 */
object UserTotalsQuery : Query<UserTotals> {
    // Columns this query exposes (KSP discovers these)
    val userName = column<String>("user_name")
    val totalCost = column<Long>("total_cost")
    val orderCount = column<Long>("order_count")

    // Base query structure (evaluated at runtime, but KSP sees the structure)
    override fun buildBase(): QueryBuilder {
        return from(Order)
            .select { order.userName as userName }
            .select { sum(order.cost) as totalCost }
            .select { count(order.id) as orderCount }
            .groupBy { order.userName }
    }
}

/**
 * Result type (marker interface - KSP discovers this too)
 * Columns from query object are automatically mapped to properties.
 */
interface UserTotals {
    // Generated automatically from query columns by KSP:
    // val userName: String
    // val totalCost: Long
    // val orderCount: Long
}
```

**What KSP discovers:**
- ✅ UserTotalsQuery is a Query object
- ✅ Columns: userName (String), totalCost (Long), orderCount (Long)
- ✅ Result type: UserTotals interface
- ✅ Base tables used: Order
- ✅ Relationship graph: can join to Order

**No regex needed** - all discovered via symbol processing!

### 2. Inline Usage with Parameters

```kotlin
// Use the query with custom parameters (inline!)
from(Person)
    .join(
        UserTotalsQuery
            .where { totalCost gt 1000 }  // Filter parameter
            .orderBy { totalCost.desc() }  // Ordering parameter
    ) { userTotals.userName eq person.name }
    .selectAll(Person)
    .selectAll(UserTotals)
    .execute(tx)
```

**Key insight:**
- Query **structure** is in the object (KSP discovers)
- Query **parameters** are inline (runtime customization)
- You see the full query in one place! ✅

### 3. Inline Anonymous Subqueries

```kotlin
// For truly one-off subqueries, use inline syntax
from(Person)
    .join(
        query {  // Anonymous query (not reusable)
            from(Order)
                .select { order.userName as "userName" }
                .select { sum(order.cost) as "totalCost" }
                .groupBy { order.userName }
        }.aliasAs(UserTotals)  // Must provide result type
    ) { userTotals.userName eq person.name }
```

**How this works:**
- `query { }` is a builder function
- `.aliasAs(UserTotals)` connects to KSP-discovered UserTotals interface
- UserTotals interface must still be declared (KSP discovers it)
- No regex needed - UserTotals is a declared interface

---

## Complete Design

### Query Base Classes

```kotlin
/**
 * Base class for all query objects.
 * Reusable queries extend this.
 */
abstract class Query<R> {
    /**
     * Define the base query structure.
     * This is the "template" that can be customized with parameters.
     */
    abstract fun buildBase(): QueryBuilder

    /**
     * Columns exposed by this query.
     * Populated by column() calls during object initialization.
     */
    internal val columns = mutableListOf<QueryColumn<*>>()

    /**
     * Apply WHERE clause parameter (inline customization).
     */
    fun where(condition: QueryContext.() -> Expression): ParameterizedQuery<R> {
        return ParameterizedQuery(this, whereClause = condition)
    }

    /**
     * Apply ORDER BY parameter (inline customization).
     */
    fun orderBy(ordering: QueryContext.() -> OrderSpec): ParameterizedQuery<R> {
        return ParameterizedQuery(this, orderByClause = ordering)
    }

    /**
     * Apply LIMIT parameter.
     */
    fun limit(n: Int): ParameterizedQuery<R> {
        return ParameterizedQuery(this, limit = n)
    }
}

/**
 * A query with runtime parameters applied.
 * Used when you customize a base query with WHERE/ORDER/LIMIT.
 */
class ParameterizedQuery<R>(
    val baseQuery: Query<R>,
    val whereClause: (QueryContext.() -> Expression)? = null,
    val orderByClause: (QueryContext.() -> OrderSpec)? = null,
    val limit: Int? = null,
    val offset: Int? = null
) {
    // Can chain more parameters
    fun where(condition: QueryContext.() -> Expression) =
        copy(whereClause = condition)

    fun orderBy(ordering: QueryContext.() -> OrderSpec) =
        copy(orderByClause = ordering)

    fun limit(n: Int) = copy(limit = n)
    fun offset(n: Int) = copy(offset = n)
}

/**
 * Column in a query result.
 */
class QueryColumn<T>(
    val name: String,
    val type: KClass<T>
)

/**
 * DSL for declaring query columns.
 */
inline fun <reified T> Query<*>.column(name: String): QueryColumn<T> {
    val column = QueryColumn(name, T::class)
    columns.add(column)
    return column
}
```

### Example: Reusable Query with Parameters

```kotlin
// 1. Define query object (KSP discovers this)
object UserTotalsQuery : Query<UserTotals> {
    val userName = column<String>("user_name")
    val totalCost = column<Long>("total_cost")
    val orderCount = column<Long>("order_count")

    override fun buildBase() = from(Order)
        .select { order.userName as userName }
        .select { sum(order.cost) as totalCost }
        .select { count(order.id) as orderCount }
        .groupBy { order.userName }
}

// 2. Result interface (KSP discovers this)
interface UserTotals {
    // Properties generated by KSP from query columns
}

// 3. Use with different parameters (inline!)
fun findHighSpenders() {
    from(Person)
        .join(
            UserTotalsQuery
                .where { totalCost gt 1000 }  // High spenders
                .orderBy { totalCost.desc() }
                .limit(10)  // Top 10
        ) { userTotals.userName eq person.name }
        .selectAll(Person)
        .selectAll(UserTotals)
        .execute(tx)
}

fun findLowSpenders() {
    from(Person)
        .join(
            UserTotalsQuery
                .where { totalCost lt 100 }  // Low spenders
                .orderBy { totalCost.asc() }
        ) { userTotals.userName eq person.name }
        .selectAll(Person)
        .selectAll(UserTotals)
        .execute(tx)
}
```

**Benefits:**
- ✅ Query defined once (DRY)
- ✅ Parameters inline (good UX)
- ✅ KSP discovers structure (zero regex)
- ✅ See full query at call site

---

## Nested Subqueries

### Example: Subquery of Subquery

```kotlin
// 1. Base query (order aggregates)
object OrderAggregates : Query<OrderAggregatesResult> {
    val userName = column<String>("user_name")
    val totalCost = column<Long>("total_cost")
    val avgCost = column<Double>("avg_cost")

    override fun buildBase() = from(Order)
        .select { order.userName as userName }
        .select { sum(order.cost) as totalCost }
        .select { avg(order.cost) as avgCost }
        .groupBy { order.userName }
}

// 2. Nested query (high spenders - filters OrderAggregates)
object HighSpendersQuery : Query<HighSpendersResult> {
    val userName = column<String>("user_name")
    val totalCost = column<Long>("total_cost")

    override fun buildBase() = from(OrderAggregates)  // Query of query!
        .select { orderAggregates.userName as userName }
        .select { orderAggregates.totalCost as totalCost }
        .where { orderAggregates.avgCost gt 500.0 }
}

// 3. Use nested query
from(Person)
    .join(HighSpendersQuery) { highSpenders.userName eq person.name }
    .selectAll(Person)
    .selectAll(HighSpenders)
    .execute(tx)
```

**How it works:**
- HighSpendersQuery references OrderAggregates
- KSP sees the dependency
- Generates: `from(OrderAggregates)` compiles to nested SQL
- No regex needed - all static structure

### SQL Generation

```sql
-- HighSpendersQuery generates:
SELECT
    person.*,
    high_spenders.*
FROM person
INNER JOIN (
    -- OrderAggregates (nested subquery)
    SELECT
        user_name,
        SUM(cost) as total_cost
    FROM (
        SELECT * FROM "order"
    ) order_aggregates
    WHERE avg_cost > 500.0
) high_spenders ON high_spenders.user_name = person.name
```

---

## Anonymous Inline Subqueries

**For one-off subqueries that won't be reused:**

```kotlin
from(Person)
    .join(
        query {
            from(Order)
                .select { order.userName as "userName" }
                .select { sum(order.cost) as "totalCost" }
                .groupBy { order.userName }
                .where { order.cost gt 100 }  // Inline filter
        }.aliasAs(UserTotals)  // Must provide result type
    ) { userTotals.userName eq person.name }
```

**Requirements:**
- `UserTotals` interface must be declared (KSP discovers)
- Column names in `as "userName"` must match interface properties
- Type inference from expressions

**Why this works:**
- The result type `UserTotals` is declared (KSP discovers it)
- The query structure is inline (no discovery needed)
- At runtime, the builder validates columns match interface

**Trade-off:**
- ✅ Inline UX (see query in one place)
- ⚠️ Not reusable (defined at use site)
- ⚠️ Must declare result interface separately

---

## Parameter Passing Patterns

### 1. Simple Parameters (WHERE clause)

```kotlin
object OrdersQuery : Query<OrdersResult> {
    val id = column<Int>("id")
    val userName = column<String>("user_name")
    val cost = column<Int>("cost")

    override fun buildBase() = from(Order)
        .select { order.id as id }
        .select { order.userName as userName }
        .select { order.cost as cost }
}

// Use with different WHERE clauses
OrdersQuery.where { cost gt 1000 }  // Expensive orders
OrdersQuery.where { userName eq "alice" }  // Alice's orders
OrdersQuery.where { (cost gt 100) and (userName like "%@example.com") }  // Combined
```

### 2. Parameterized Functions

```kotlin
object OrdersQuery : Query<OrdersResult> {
    // ... columns ...

    override fun buildBase() = from(Order)
        .select { order.id as id }
        .select { order.userName as userName }
        .select { order.cost as cost }

    // Helper methods for common filters
    fun byUser(name: String) = where { userName eq name }
    fun byMinCost(minCost: Int) = where { cost gte minCost }
    fun recent(days: Int) = where {
        order.createdAt gte LocalDate.now().minusDays(days)
    }
}

// Usage
OrdersQuery.byUser("alice")
OrdersQuery.byMinCost(1000)
OrdersQuery.recent(7).orderBy { cost.desc() }
```

### 3. Complex Parameters (Configuration Object)

```kotlin
data class QueryParams(
    val minCost: Int? = null,
    val maxCost: Int? = null,
    val users: List<String>? = null,
    val limit: Int = 100
)

object OrdersQuery : Query<OrdersResult> {
    // ... columns ...

    fun withParams(params: QueryParams): ParameterizedQuery<OrdersResult> {
        var query: ParameterizedQuery<OrdersResult> = this.limit(params.limit)

        if (params.minCost != null) {
            query = query.where { cost gte params.minCost }
        }
        if (params.maxCost != null) {
            query = query.where { cost lte params.maxCost }
        }
        if (params.users != null) {
            query = query.where { userName inList params.users }
        }

        return query
    }
}

// Usage
val params = QueryParams(minCost = 100, users = listOf("alice", "bob"))
OrdersQuery.withParams(params)
```

---

## KSP Discovery Strategy

### What KSP Discovers

```kotlin
class KodamaSymbolProcessor : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // 1. Discover all Query objects
        val queries = resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.OBJECT }
            .filter { extendsQuery(it) }

        queries.forEach { queryDecl ->
            // Extract metadata
            val queryName = queryDecl.simpleName.asString()
            val resultType = extractResultType(queryDecl)  // UserTotals
            val columns = extractColumns(queryDecl)  // userName, totalCost, etc.
            val baseTables = extractBaseTables(queryDecl)  // Order

            // Write to metadata.json
            writeQueryMetadata(queryName, resultType, columns, baseTables)
        }

        // 2. Discover all result type interfaces
        val resultTypes = queries.map { extractResultType(it) }
        resultTypes.forEach { resultTypeDecl ->
            // Generate properties from query columns
            generateResultTypeProperties(resultTypeDecl)
        }

        // 3. Generate CanJoin instances
        queries.forEach { query ->
            val baseTables = extractBaseTables(query)
            baseTables.forEach { table ->
                // Generate: object PersonCanJoinUserTotalsQuery : CanJoin<Person, UserTotalsQuery>
                generateCanJoin(table, query)
            }
        }

        return emptyList()
    }
}
```

### Generated Metadata (queries.json)

```json
{
  "queries": [
    {
      "name": "UserTotalsQuery",
      "resultType": "UserTotals",
      "columns": [
        {"name": "userName", "type": "String", "sqlName": "user_name"},
        {"name": "totalCost", "type": "Long", "sqlName": "total_cost"},
        {"name": "orderCount", "type": "Long", "sqlName": "order_count"}
      ],
      "baseTables": ["Order"],
      "dependencies": []
    },
    {
      "name": "HighSpendersQuery",
      "resultType": "HighSpenders",
      "columns": [
        {"name": "userName", "type": "String"},
        {"name": "totalCost", "type": "Long"}
      ],
      "baseTables": [],
      "dependencies": ["OrderAggregatesQuery"]
    }
  ]
}
```

**Zero regex** - all discovered via KSP symbol processing!

---

## Code Generation

### Input: Query Object

```kotlin
object UserTotalsQuery : Query<UserTotals> {
    val userName = column<String>("user_name")
    val totalCost = column<Long>("total_cost")

    override fun buildBase() = from(Order)
        .select { order.userName as userName }
        .select { sum(order.cost) as totalCost }
        .groupBy { order.userName }
}

interface UserTotals
```

### Generated: Result Type Properties

```kotlin
// Auto-generated by KSP
interface UserTotals {
    val userName: String
    val totalCost: Long
}
```

### Generated: Query Accessor

```kotlin
// Auto-generated accessor for type-safe column access
class UserTotalsAccessor(private val query: UserTotalsQuery) {
    val userName: TypedColumn<String, UserTotals, UserName>
        get() = TypedColumn(query.userName)

    val totalCost: TypedColumn<Long, UserTotals, TotalCost>
        get() = TypedColumn(query.totalCost)
}
```

### Generated: Join Extensions

```kotlin
// Join extension with CanJoin constraint
fun <Joins : JoinPattern> TypedQueryBuilder<Person, Joins, *>.join(
    query: ParameterizedQuery<UserTotals>,
    condition: JoinContext<Person, InnerJoin<UserTotals, Joins>>.() -> Expression
): TypedQueryBuilder<Person, InnerJoin<UserTotals, Joins>, *>
where CanJoin<Person, UserTotalsQuery> : Any
{
    // Implementation
}

// CanJoin instance (auto-generated from baseTables)
object PersonCanJoinUserTotalsQuery : CanJoin<Person, UserTotalsQuery>
```

---

## Comparison: Before vs After

### Current (Regex-Based Inline)

```kotlin
// Inline subquery - discovered via regex
from(Person)
    .joinAliased(
        from(Order)
            .selectAs(OrderUserName) { order.userName }  // ← Regex discovers marker
            .selectAs(TotalCost) { sum(order.cost) }     // ← Regex discovers marker
            .groupBy { order.userName }
            .build()
            .aliasAs<UserTotals>()  // ← Regex discovers type
    ) { person.name eq userTotals.orderUserName }
    .selectAll(Person)
    .selectAll(UserTotals)

// Must declare marker interfaces (discovered via regex)
interface OrderUserName
interface TotalCost
interface UserTotals
```

**Pros:** Inline (see query in one place)
**Cons:** Regex fragile, not reusable, poor type safety

### New (Query Objects)

```kotlin
// Define once (KSP discovers)
object UserTotalsQuery : Query<UserTotals> {
    val userName = column<String>("user_name")
    val totalCost = column<Long>("total_cost")

    override fun buildBase() = from(Order)
        .select { order.userName as userName }
        .select { sum(order.cost) as totalCost }
        .groupBy { order.userName }
}

interface UserTotals  // KSP generates properties

// Use with parameters (inline customization!)
from(Person)
    .join(
        UserTotalsQuery.where { totalCost gt 1000 }  // Inline parameter
    ) { userTotals.userName eq person.name }
    .selectAll(Person)
    .selectAll(UserTotals)
```

**Pros:** Zero regex, reusable, type-safe, inline parameters
**Cons:** Query structure in separate object (but used inline with parameters)

---

## Addressing UX Concerns

### "I want to see the whole query in one place"

**Solution:** Query objects are **templates** that you **customize inline**:

```kotlin
from(Person)
    .join(
        UserTotalsQuery  // Template (defined elsewhere)
            .where { totalCost gt 1000 }  // Inline customization
            .orderBy { totalCost.desc() }  // Inline customization
            .limit(10)  // Inline customization
    ) { userTotals.userName eq person.name }  // Join condition inline
    .selectAll(Person)
    .selectAll(UserTotals)
```

**You see:**
- Which query template (UserTotalsQuery)
- All parameters (WHERE, ORDER BY, LIMIT)
- Join condition
- Selected columns

**All in one place!** ✅

The only thing not inline is the query's SELECT/FROM/GROUP BY (the "structure"), which is intentionally extracted for reusability.

---

## Final Architecture

```
┌─────────────────────────────────────────┐
│ Query Objects (Declarative)             │
│ - Discovered by KSP                     │
│ - Define structure (SELECT/FROM/GROUP)  │
│ - Declare columns and types             │
│ - Zero regex                            │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│ KSP Metadata Generation                 │
│ - queries.json (structure)              │
│ - CanJoin instances                     │
│ - Result type properties                │
│ - Query accessors                       │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│ Inline Usage (Runtime)                  │
│ - Reference query objects               │
│ - Add parameters (WHERE/ORDER/LIMIT)    │
│ - Customize at call site               │
│ - Good UX - see query + params inline   │
└─────────────────────────────────────────┘
```

**Result:**
- ✅ Zero regex (KSP discovers everything)
- ✅ Good UX (parameters inline, see full query at call site)
- ✅ Reusable (query defined once, used many times)
- ✅ Type-safe (compile-time validation)
- ✅ Composable (queries can reference queries)
- ✅ Parameterizable (WHERE/ORDER/LIMIT inline)

---

## Next Steps

1. Implement `Query<R>` base class and `column()` DSL
2. Implement `ParameterizedQuery<R>` for inline parameters
3. Update KSP processor to discover Query objects
4. Generate query metadata (queries.json)
5. Generate CanJoin instances for queries
6. Test with nested queries

**Timeline:** 2-3 weeks for full implementation

Should I start implementing the Query base classes?
