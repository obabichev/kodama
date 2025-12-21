# Subquery Support: Clean Slate Design Proposal

## Executive Summary

This document proposes a redesign of Kodama's query builder from first principles to elegantly support subqueries while maintaining full compile-time type safety. The core insight is to use **direct parameters** instead of lambdas for selection, eliminating overload ambiguity while enabling inline subquery composition.

## Current Problems

1. **Overload ambiguity** - Lambda-based `.selectAll { table }` creates unresolvable overload conflicts
2. **Inline subqueries don't work** - Requires storing subqueries in variables
3. **Complex generic hierarchies** - Many type parameters to track selection state
4. **Inconsistent API** - Tables and subqueries handled differently

## Core Design Principles

1. **Tables and subqueries should be first-class objects** - passable, comparable, usable as parameters
2. **Selection should use direct parameters** - avoid lambda overload ambiguity
3. **Context receivers for operators** - lambdas only where needed (WHERE, JOIN conditions)
4. **State tracking through types** - compiler prevents invalid operations
5. **Minimal generics** - use concrete types and method chaining instead
6. **Inline composition** - no variables required for subqueries

---

## Proposed API

### Simple Query
```kotlin
from(Order)
    .select(Order.userName, Order.product)
    .where { Order.cost gt 100 }
    .execute(tx)
```

### Subquery in FROM
```kotlin
from(
    from(Order)
        .select(Order.userName)
        .selectAs(TotalCost) { sum(Order.cost) }
        .groupBy(Order.userName)
        .alias<UserTotals>()  // Returns Subquery<UserTotals>
)
    .select(UserTotals.userName, UserTotals.totalCost)
    .where { UserTotals.totalCost gt 1000 }
    .execute(tx)
```

### Subquery in JOIN
```kotlin
from(Person)
    .join(
        from(Order)
            .select(Order.userName)
            .alias<OrderUsers>()
    ) { Person.name eq OrderUsers.userName }
    .select(Person.name, Person.age)
    .execute(tx)
```

### Multiple Selections
```kotlin
from(Person)
    .join(Order) { Order.userName eq Person.name }
    .select(Person.all(), Order.product, Order.cost)
    .where { Person.age gt 25 and (Order.cost lt 1000) }
    .execute(tx)
```

### Inline Subquery - Complete Example
```kotlin
val results = from(Person)
    .join(
        from(Order)
            .select(Order.userName)
            .selectAs(TotalCost) { sum(Order.cost) }
            .groupBy(Order.userName)
            .alias<UserTotals>()  // Type-safe subquery
    ) { Person.name eq UserTotals.userName }
    .select(
        Person.name,
        Person.age,
        UserTotals.totalCost
    )
    .where {
        Person.age gt 25 and (UserTotals.totalCost gt 1000)
    }
    .execute(tx)

// Access results
results.forEach { row ->
    val name: String = row[Person.name]
    val age: Int = row[Person.age]
    val total: Number? = row[UserTotals.totalCost]
}
```

---

## Key Design Elements

### 1. Tables as Objects (Already Implemented)
```kotlin
object Person : Table("person") {
    val name = varchar("name", 255)
    val age = integer("age")

    // Convenience for selecting all columns
    fun all(): List<Column<*>> = listOf(name, age)
}
```

**Why**: Tables are singletons - there's only one Person table. Objects are perfect for this.

### 2. Subquery Types as Objects
```kotlin
// Generated for each marker interface
object UserTotals : SubqueryType {
    val userName: SubqueryColumn<String> = SubqueryColumn("user_totals", "user_name")
    val totalCost: SubqueryColumn<Number?> = SubqueryColumn("user_totals", "total_cost")

    override val alias: String = "user_totals"

    fun all(): List<SubqueryColumn<*>> = listOf(userName, totalCost)
}
```

**Why**: Treat subqueries like tables - they're also unique sources in a query.

### 3. Unified Column Interface
```kotlin
sealed interface ColumnRef<T> {
    val tableName: String
    val columnName: String
}

class TableColumn<T>(
    override val tableName: String,
    override val columnName: String
) : ColumnRef<T>

class SubqueryColumn<T>(
    override val tableName: String,  // subquery alias
    override val columnName: String
) : ColumnRef<T>
```

**Why**: Both table columns and subquery columns are just column references. Unified type eliminates special cases.

### 4. Selection with Varargs
```kotlin
class QueryBuilder {
    fun select(vararg columns: ColumnRef<*>): QueryBuilderWithSelection {
        // Add columns to selection list
        return QueryBuilderWithSelection(this)
    }

    fun select(vararg sources: TableSource): QueryBuilderWithSelection {
        // Select all columns from sources
        return QueryBuilderWithSelection(this)
    }
}

// TableSource is common interface for Table and Subquery
interface TableSource {
    val alias: String
    fun columns(): List<ColumnRef<*>>
}
```

**Why**:
- Varargs allow natural syntax: `.select(col1, col2, col3)`
- No overload ambiguity - different parameter types are clearly distinguishable
- Flexible - can mix columns: `.select(Person.name, Order.cost, UserTotals.total)`

### 5. Operator DSL with Context Receivers
```kotlin
class WhereContext(private val availableSources: List<TableSource>) {
    // Infix operators for building conditions
    infix fun <T> ColumnRef<T>.eq(other: ColumnRef<T>): Condition = ...
    infix fun <T : Comparable<T>> ColumnRef<T>.gt(value: T): Condition = ...
    infix fun Condition.and(other: Condition): Condition = ...
    infix fun Condition.or(other: Condition): Condition = ...
}

// Usage:
.where { Person.age gt 25 and (Order.cost lt 1000) }
```

**Why**:
- Lambdas are still needed for operator DSL (infix `gt`, `eq`, `and`, etc.)
- Context provides the operators without polluting global scope
- Type-safe - operators only work on compatible types

### 6. Subquery Builder with Type Safety
```kotlin
class SubqueryBuilder {
    fun <T : SubqueryType> alias(): Subquery<T> {
        // T provides the type information
        // Generated code creates instance
        return Subquery(this, T::class)
    }
}

class Subquery<T : SubqueryType>(
    internal val builder: SubqueryBuilder,
    internal val type: KClass<T>
) : TableSource {
    override val alias: String get() = type.objectInstance!!.alias
    override fun columns(): List<ColumnRef<*>> = type.objectInstance!!.all()
}
```

**Why**:
- `.alias<UserTotals>()` binds the query to a type
- Type parameter `T` carries type information through builder chain
- Enables inline composition without variables

### 7. Named Aggregates
```kotlin
// Define marker for aggregate alias
object TotalCost : AggregateMarker

from(Order)
    .select(Order.userName)
    .selectAs(TotalCost) { sum(Order.cost) }  // Binds name to aggregate
    .groupBy(Order.userName)
```

**Why**:
- Explicit naming is clearer than generated `select_totalCost` methods
- Separates concerns: selection vs naming
- Marker objects are type-safe identifiers

---

## Why This Design is Better

### 1. No Overload Ambiguity

**Current problem:**
```kotlin
.selectAll { person }         // Which overload?
.selectAll { usersWithOrders } // Ambiguous!
```

**Solution:**
```kotlin
.select(Person.name)              // ColumnRef<String>
.select(UserTotals.totalCost)     // ColumnRef<Number?>
.select(Person.all())             // List<ColumnRef<*>>
// Different parameter types - no ambiguity!
```

### 2. Inline Subqueries Work Naturally

**Current problem:**
```kotlin
// Doesn't work - must use variable
val subquery = from(Order).select(...).aliasAs<UserTotals>()
from(Person).join(subquery) { ... }
```

**Solution:**
```kotlin
// Fully inline - no variable needed
from(Person)
    .join(from(Order).select(...).alias<UserTotals>()) { ... }
```

### 3. Consistent API

**Tables:**
```kotlin
Person.name
Person.age
Person.all()
```

**Subqueries:**
```kotlin
UserTotals.userName
UserTotals.totalCost
UserTotals.all()
```

**Same pattern!** No mental model switch between tables and subqueries.

### 4. Type-Safe Results

**Option A: Column-based access (explicit)**
```kotlin
val name: String = row[Person.name]
val total: Number? = row[UserTotals.totalCost]
```

**Option B: Generated accessor classes**
```kotlin
val name: String = row.person.name
val total: Number? = row.userTotals.totalCost
```

Both options maintain full compile-time type safety.

### 5. Minimal Generated Code

**Only need to generate:**
1. SubqueryType objects with column properties
2. Result accessor classes (optional, for convenience)

**No longer need:**
- Complex generic builder hierarchies
- SelectAllContext classes per table combination
- Method overloads for each table
- Companion objects with delegation

### 6. Natural SQL Mapping

```kotlin
from(Person)              // FROM person
    .join(Order) { ... }       // JOIN order ON ...
    .select(Person.name)       // SELECT person.name
    .where { ... }             // WHERE ...
```

Code structure mirrors SQL structure directly.

### 7. Extensibility

Easy to add new features:

```kotlin
// Scalar subqueries
.where { Person.age gt scalarSubquery(avgAgeQuery) }

// EXISTS
.where { exists(hasOrdersQuery) }

// UNION
from(Person).select(Person.name)
    .union(from(Company).select(Company.name))

// CTEs (WITH clause)
with("user_totals", userTotalsQuery)
    .from(Person)
    .join(UserTotals) { ... }
```

---

## Implementation Strategy

### Phase 1: Core Infrastructure
1. Define `ColumnRef<T>` sealed interface
2. Define `TableSource` interface
3. Define `SubqueryType` interface
4. Implement unified `QueryBuilder` with varargs selection

### Phase 2: Operator DSL
1. Implement `WhereContext` with infix operators
2. Implement `JoinContext` for join conditions
3. Build `Condition` system

### Phase 3: Subquery Support
1. Implement `SubqueryBuilder`
2. Add `.alias<T>()` method
3. Create `Subquery<T>` wrapper

### Phase 4: Code Generation
1. Generate `SubqueryType` objects from marker interfaces
2. Generate result accessor classes
3. Scan for table/subquery usage patterns

### Phase 5: Advanced Features
1. Scalar subqueries (`scalarSubquery()`)
2. EXISTS/NOT EXISTS operators
3. UNION/INTERSECT operations
4. CTEs (WITH clause)
5. Correlated subqueries

---

## Comparison to Current Design

| Aspect | Current Design | Fresh Design |
|--------|----------------|--------------|
| **Selection** | `.selectAll { person }` (lambda) | `.select(Person.all())` (direct) |
| **Overload ambiguity** | ❌ Unresolvable | ✅ No ambiguity |
| **Inline subqueries** | ❌ Requires variables | ✅ Works perfectly |
| **API consistency** | Mixed (tables vs subqueries differ) | ✅ Uniform |
| **Generated code** | Complex (many contexts, generics) | ✅ Simple (objects + columns) |
| **Type parameters** | Many generic parameters | ✅ Minimal generics |
| **Boilerplate** | Lambda blocks | ✅ Direct parameters |
| **Single column** | `.select { person.name }` | `.select(Person.name)` |
| **Multiple columns** | Multiple `.select { }` calls | `.select(Person.name, Order.cost)` |
| **All columns** | `.selectAll { person }` | `.select(Person.all())` |

---

## Open Questions

### 1. Result Reading API

**Column-based access:**
```kotlin
val name = row[Person.name]      // Explicit column reference
val age = row[Order.cost]
```

**Pros:**
- Explicit and clear
- No generated accessor classes needed
- Flexible - can use any column

**Cons:**
- More verbose
- Requires keeping column references around

**Property-based access:**
```kotlin
val name = row.person.name       // Generated property accessors
val age = row.order.cost
```

**Pros:**
- Concise and natural
- IDE autocomplete works well
- Feels like working with objects

**Cons:**
- Requires generating accessor classes
- More code generation

**Recommendation:** Support both. Column-based as primary, property-based as convenience.

### 2. Type Safety with Varargs

**Challenge:** With `.select(Person.name, Order.cost)`, how do we ensure type-safe result reading?

**Option A:** Generate distinct builder classes for each selection pattern (2^N classes)
```kotlin
class Builder_Person_Order {
    fun select(col: Person.name): Builder_Person_Order_Selected_PersonName
    fun select(col: Order.cost): Builder_Person_Order_Selected_OrderCost
}
```

**Option B:** Use type-level tracking with phantom types
```kotlin
class Builder<Tables, Selections> {
    fun select(col: ColumnRef<*>): Builder<Tables, AddSelection<Selections, col>>
}
```

**Option C:** Runtime validation with reified generics
```kotlin
inline fun <reified R> execute(tx: Transaction): ResultIterable<R> {
    // Validate at runtime that R matches selections
}
```

**Recommendation:** Option A for full compile-time safety, Option C as fallback for dynamic queries.

### 3. Aggregate Selection Syntax

**Current approach:**
```kotlin
.select_totalCost { sum(order.cost) }  // Generated method per aggregate
```

**Proposed approach:**
```kotlin
.selectAs(TotalCost) { sum(Order.cost) }  // Explicit marker object
```

**Alternative:**
```kotlin
.select(sum(Order.cost).`as`(TotalCost))  // Fluent API
```

**Recommendation:** Start with `.selectAs(Marker) { aggregate }` for clarity.

---

## Migration Path

### Step 1: Add New API Alongside Old
- Implement new direct-parameter API
- Keep existing lambda-based API
- Mark old API as `@Deprecated`

### Step 2: Migrate Tests
- Rewrite tests using new API
- Verify functionality parity
- Document migration patterns

### Step 3: Update Documentation
- Update getting-started guide
- Add migration guide
- Explain benefits of new approach

### Step 4: Remove Old API
- Remove deprecated methods
- Clean up old code generation
- Simplify codebase

---

## Conclusion

This redesign solves the fundamental problems with subquery support while simplifying the codebase and providing a more intuitive API. The key insights are:

1. **Direct parameters eliminate overload ambiguity** - Different types are distinguishable
2. **First-class subquery objects enable inline composition** - No variables needed
3. **Uniform API for tables and subqueries** - Same mental model throughout
4. **Minimal generics reduce complexity** - Clearer code, better error messages
5. **Varargs enable flexible selection** - Natural syntax for multiple columns

The proposed design maintains full compile-time type safety while enabling the inline subquery composition that is critical for a fluent, composable query builder.

---

## Next Steps

1. **Prototype core infrastructure** - ColumnRef, TableSource, QueryBuilder
2. **Implement subquery aliasing** - `.alias<T>()` mechanism
3. **Test with real queries** - Verify API ergonomics
4. **Evaluate code generation requirements** - Determine minimal generated code
5. **Measure performance** - Ensure no runtime overhead
6. **Gather feedback** - Validate with users

---

## Appendix: Code Examples

### Example 1: Complex Multi-Join with Subquery
```kotlin
from(Person)
    .join(Order) { Order.userName eq Person.name }
    .join(
        from(Product)
            .select(Product.name, Product.category)
            .selectAs(AvgPrice) { avg(Product.price) }
            .groupBy(Product.category)
            .alias<CategoryStats>()
    ) { Order.product eq CategoryStats.name }
    .select(
        Person.name,
        Order.product,
        CategoryStats.avgPrice
    )
    .where {
        Person.age gt 25 and (CategoryStats.avgPrice lt 100)
    }
    .execute(tx)
```

### Example 2: Subquery in FROM with Filter
```kotlin
from(
    from(Order)
        .select(Order.userName, Order.product, Order.cost)
        .where { Order.cost gte 500 }
        .alias<ExpensiveOrders>()
)
    .join(Person) { Person.name eq ExpensiveOrders.userName }
    .select(Person.name, ExpensiveOrders.product)
    .execute(tx)
```

### Example 3: Nested Subqueries
```kotlin
from(
    from(
        from(Order)
            .select(Order.userName)
            .selectAs(TotalSpent) { sum(Order.cost) }
            .groupBy(Order.userName)
            .alias<UserSpending>()
    )
        .select(UserSpending.userName, UserSpending.totalSpent)
        .where { UserSpending.totalSpent gt 1000 }
        .alias<HighSpenders>()
)
    .join(Person) { Person.name eq HighSpenders.userName }
    .select(Person.name, Person.age, HighSpenders.totalSpent)
    .execute(tx)
```

---

*Document created: 2025-12-26*
*Author: Claude (via user request)*
*Status: Proposal - Not yet implemented*
