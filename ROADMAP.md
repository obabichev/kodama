# Kodama Feature Roadmap

## Overview

This roadmap outlines planned features for both the DSL layer (query building) and Entity layer (ORM). Features are
prioritized based on usage patterns and user needs.

**For completed features**, see [CHANGELOG.md](CHANGELOG.md).

---

## Recent Improvements (v0.4.0 - January 2026)

### ✅ Compiler Plugin Architecture Refactoring

**Status:** Complete
**Impact:** Internal architecture improvement

Successfully refactored the code generation system from a monolithic 242 KB file to a modular architecture with 987 fine-grained generators:

**Key Achievements:**
- ✅ **Modular Design**: 80+ focused generator classes replacing single monolithic file
- ✅ **Two-Phase Architecture**: Structure-driven (Phase 1) + Pattern-driven (Phase 2) generation
- ✅ **Phantom Type Parameters**: Compile-time selection tracking for type safety
- ✅ **SqlAliasStyle System**: Configurable SQL naming conventions (camelCase vs snake_case)
- ✅ **Zero Breaking Changes**: 149/149 tests passing, 100% backward compatibility
- ✅ **Clean Codebase**: Removed ~470 KB of legacy code

**Benefits:**
- **Maintainability**: Each generator ~50-150 lines vs 4,000+ line monolith
- **Testability**: Independently testable components
- **Extensibility**: Easy to add new features by implementing `CodeGenerator`
- **Developer Experience**: Clear architecture, focused files, better code reviews

**See:** [Refactoring Summary](doc/REFACTORING_SUMMARY.md) for complete details

---

## DSL Layer (Query Building)

### High Priority Features

#### 1. HAVING Clause

**Status:** Not implemented
**Priority:** ⭐ High

Filter aggregate results (works with GROUP BY):

```kotlin
from(Order)
    .selectAs(OrderUserName) { order.userName }
    .selectAs(OrderCount) { count(order.id) }
    .groupBy { order.userName }
    .having { count(order.id) gt 5 }
    .execute(transaction)
```

**Implementation Tasks:**

- Add `having()` method to query builders
- Support aggregate functions in HAVING clause
- Update SQL generation
- Add tests for HAVING with aggregates

---

#### 2. UPDATE and DELETE Statements

**Status:** Not implemented
**Priority:** ⭐⭐⭐ Critical

Complete CRUD support with UPDATE and DELETE:

```kotlin
// UPDATE
update(Person)
    .set {
        person.age = 26
        person.email = "newemail@example.com"
    }
    .where { person.name eq "kodama" }
    .execute(transaction)

// DELETE
delete(Person)
    .where { person.age lt 18 }
    .execute(transaction)
```

**Implementation Tasks:**

- Create UPDATE builder with type-safe set operations
- Create DELETE builder
- Support RETURNING clause (PostgreSQL specific)
- Handle transaction management
- Add comprehensive tests

---

#### 3. LEFT/RIGHT/FULL OUTER JOIN

**Status:** Not implemented (INNER JOIN only)
**Priority:** ⭐⭐ High

Outer joins for optional relationships:

```kotlin
from(Person)
    .leftJoin(Order) { order.userName eq person.name }
    .selectAll(Person)
    .selectAll(Order)  // Order columns may be null

// Explicit type parameter
from(Person)
    .join(Order, type = JoinType.LEFT_OUTER) {
        order.userName eq person.name
    }
```

**Implementation Tasks:**

- Add `JoinType.LEFT_OUTER`, `RIGHT_OUTER`, `FULL_OUTER` to enum
- Add convenience methods: `leftJoin()`, `rightJoin()`, `fullJoin()`
- Handle nullable result types for outer joins
- Update SQL generation
- Add tests for all join types

---

### Medium Priority Features

#### 4. DISTINCT

**Status:** Not implemented
**Priority:** Medium

Remove duplicates from query results:

```kotlin
from(Person)
    .distinct()
    .selectAs(PersonName) { person.name }

// DISTINCT ON (PostgreSQL specific)
from(Order)
    .distinctOn { order.userName }
    .selectAs(UserName) { order.userName }
    .selectAs(Product) { order.product }
```

**Implementation Tasks:**

- Add `distinct()` method to query builders
- Add `distinctOn()` for PostgreSQL-specific syntax
- Update SQL generation
- Add tests for DISTINCT queries

---

#### 5. CASE Expressions

**Status:** Not implemented
**Priority:** Medium

Conditional logic in SELECT clauses:

```kotlin
from(Person)
    .selectAs(PersonName) { person.name }
    .selectAs(AgeGroup) {
        case()
            .when {
                person.age lt 18
            } then "Minor"
            .when {
                person.age.between(18, 65)
            } then "Adult"
            .else_ { "Senior" }
    }

// With numeric results
from(Order)
    .selectAs(Product) { order.product }
    .selectAs(DiscountedPrice) {
        case()
            .when {
                order.cost gt 1000
            } then (order.cost * 0.9)
            .when {
                order.cost gt 500
            } then (order.cost * 0.95)
            .else_ { order.cost }
    }
```

**Implementation Tasks:**

- Create `CaseBuilder` DSL with `when()`, `then()`, `else_()` methods
- Support both simple CASE and searched CASE syntax
- Type inference for THEN branches
- Generate proper SQL with CASE/WHEN/THEN/ELSE/END
- Support CASE in SELECT, WHERE, and ORDER BY
- Add comprehensive tests

---

### Lower Priority Features

#### 6. Common Table Expressions (Non-Recursive)

**Status:** Not implemented
**Priority:** Lower (Medium complexity)

WITH clause for named subqueries (CTEs):

```kotlin
// Define CTE
val highValueOrders = cte("high_value_orders") {
    from(Order)
        .selectAs(UserName) { order.userName }
        .selectAs(Cost) { order.cost }
        .where { order.cost gt 1000 }
}

// Use CTE in main query
with(highValueOrders)
    .from(Person)
    .join(highValueOrders) { highValueOrders.userName eq person.name }
    .selectAs(PersonName) { person.name }
    .selectAs(OrderCost) { highValueOrders.cost }

// Multiple CTEs
val recentOrders = cte("recent") { /* query */ }
val topCustomers = cte("top_customers") { /* query */ }

with(recentOrders, topCustomers)
    .from(recentOrders)
    .join(topCustomers) { /* join condition */ }
    .selectAs(ResultField) { /* field selection */ }
```

**Important Notes:**

- **Non-recursive only** - WITH RECURSIVE not supported (see ARCHITECTURAL_CHALLENGES.md)
- Recursive CTEs have difficulty 9/10 due to circular type dependencies
- Non-recursive CTEs have medium difficulty (6-7/10)

**Implementation Tasks:**

- Create `cte()` function to define named CTEs
- Add `with()` method to query builder
- Support multiple CTEs in single query
- Generate proper WITH clause SQL
- Handle CTE result types for type-safe joins
- Add tests for CTE queries
- Document non-recursive limitation

---

#### 7. UNION / INTERSECT / EXCEPT with Type Coercion

**Status:** Not implemented
**Priority:** Lower

Combine multiple query results with set operations:

```kotlin
// UNION - combine results, remove duplicates
val query1 = from(Person)
    .selectAs(Name) { person.name }

val query2 = from(Company)
    .selectAs(Name) { company.companyName }

val combined = query1.union(query2)

// UNION ALL - keep duplicates
query1.unionAll(query2)

// INTERSECT - common rows
query1.intersect(query2)

// EXCEPT - rows in first but not second
query1.except(query2)

// Type coercion for compatible types
val numericQuery1 = from(Order)
    .selectAs(Amount) { order.cost }  // Int

val numericQuery2 = from(Product)
    .selectAs(Amount) { product.price }  // BigDecimal

// Kodama coerces Int to BigDecimal
numericQuery1.union(numericQuery2)  // Result type: BigDecimal
```

**Type Coercion Rules:**

- Int → Long → BigDecimal → Double (numeric widening)
- VARCHAR → TEXT (string widening)
- LocalDateTime → OffsetDateTime (timezone widening)
- Incompatible types → compile error

**Implementation Tasks:**

- Add `union()`, `unionAll()`, `intersect()`, `except()` methods
- Implement type coercion system for compatible types
- Verify column count and types match
- Generate proper set operation SQL
- Support chaining multiple set operations
- Add comprehensive tests
- Document type coercion rules

---

#### 8. LATERAL Joins

**Status:** Not implemented
**Priority:** Lower (High complexity - Difficulty 8/10)

Correlated subqueries in FROM clause where right side can reference left side columns.

**Why Complex:** See ARCHITECTURAL_CHALLENGES.md - LATERAL joins are Tier 3 difficulty because:

- Right side query structure depends on left side row values
- Each left row can produce different right-side result structure
- Requires advanced type system to maintain compile-time safety

```kotlin
// For each person, get their top 3 most expensive orders
from(Person)
    .joinLateral(
        lateral {
            from(Order)
                .selectAll(Order)
                .where { order.userName eq person.name }  // Correlated!
                .orderBy { order.cost.desc() }
                .limit(3)
        }
    )
    .selectAs(PersonName) { person.name }
    .selectAs(Product) { order.product }
    .selectAs(Cost) { order.cost }
```

---

#### 9. Window Functions

**Status:** Not implemented
**Priority:** Lower (Difficulty 7/10)

ROW_NUMBER, RANK, LAG, LEAD, etc.:

```kotlin
from(Order)
    .selectAs(Product) { order.product }
    .selectAs(Cost) { order.cost }
    .selectAs(RowNum) {
        rowNumber().over {
            partitionBy { order.userName }
            orderBy { order.cost.desc() }
        }
    }
```

---

#### 10. Table Inheritance Hierarchies

**Status:** Not implemented
**Priority:** Lower (High complexity - Difficulty 7/10)

PostgreSQL table inheritance support with type-safe polymorphic queries.

**Why Complex:** See ARCHITECTURAL_CHALLENGES.md - Table Inheritance is Tier 3 difficulty because:

- Child tables add columns not in parent
- Query result type depends on which tables are included
- Need runtime type discrimination for polymorphic results

---

#### 11. JSON/JSONB Operations

**Status:** Not implemented
**Priority:** Lower

PostgreSQL-specific JSON support.

---

#### 12. Array Operations

**Status:** Not implemented
**Priority:** Lower

PostgreSQL array types and operations.

---

#### 13. Performance and Infrastructure

**Status:** Not implemented
**Priority:** Lower

- Transaction management improvements
- Batch operations
- Connection pooling
- Query caching
- Migration support

---

## Entity Layer (ORM)

### High Priority Features

#### 1. Many-to-Many Relationships

**Status:** Not implemented
**Priority:** ⭐⭐ High

Junction table support for many-to-many relationships:

```kotlin
// Entity definitions
interface User {
    val id: Int
    val name: String

    context(session: EntitySession)
    fun roles(): List<Role>
}

interface Role {
    val id: Int
    val name: String

    context(session: EntitySession)
    fun users(): List<User>
}

// EntityTable with many-to-many
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    init {
        manyToMany("roles", Roles, UserRoles, UserRoles.userId, UserRoles.roleId)
    }
}

// Usage
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)
        val roles = user.roles()
        roles.forEach { println(it.name) }
    }
}
```

**Implementation Tasks:**

- Add `manyToMany()` declaration to EntityTable
- Implement `findManyToMany()` in EntitySession
- Generate junction table queries (SELECT with JOIN)
- Support adding/removing relationships
- Add tests for many-to-many operations

---

#### 2. Improved Entity CRUD API

**Status:** Not implemented
**Priority:** ⭐⭐ High

Refactor CRUD operations to use extension functions for cleaner syntax:

**Current API:**

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)

        // Verbose - requires explicit type parameters
        save<UserOrder, Int>(newOrder)
        delete(user)
        flush()
    }
}
```

**Proposed API:**

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)

        // Clean extension function syntax
        newOrder.save()
        user.delete()

        flush()
    }
}
```

**Benefits:**

- More idiomatic Kotlin (object-oriented style)
- No explicit type parameters needed
- Better IDE discoverability
- Aligns with relationship navigation syntax

**Implementation Tasks:**

- Generate `Entity.save()` extension functions with `context(EntitySession)`
- Generate `Entity.delete()` extension functions with `context(EntitySession)`
- Keep existing `session.save()` and `session.delete()` for backward compatibility
- Update code generator
- Add tests
- Update documentation

---

#### 3. Batch Loading (N+1 Prevention)

**Status:** Not implemented
**Priority:** ⭐ High

Load multiple related entities in one query:

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        val users = findAll<User>()

        // Naive approach - N+1 queries
        users.forEach { user ->
            val orders = user.orders()  // SELECT per user
        }

        // Batch loading - 1 query for all orders
        val allOrders = batchLoad(users) { it.orders() }
        // SELECT * FROM user_orders WHERE user_id IN (?, ?, ?, ...)
    }
}
```

**Implementation Tasks:**

- Implement `batchLoad()` method
- Generate IN queries for batch loading
- Populate relationship collections
- Add performance tests comparing N+1 vs batch

---

### Medium Priority Features

#### 4. Cascade Operations

**Status:** Not implemented
**Priority:** Medium

Automatically propagate operations to related entities:

```kotlin
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
            .cascade(CascadeType.DELETE)  // Delete orders when user deleted
    }
}

// Usage
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)
        delete(user)  // Automatically deletes all user's orders!
        flush()
    }
}
```

**Cascade Types:**

- `CascadeType.SAVE` - Save related entities when parent saved
- `CascadeType.DELETE` - Delete related entities when parent deleted
- `CascadeType.ALL` - Cascade all operations

---

### Lower Priority Features

#### 5. Composite Primary Keys

**Status:** Not implemented
**Priority:** Low

Support entities with multi-column primary keys:

```kotlin
interface UserRole {
    val userId: Int
    val roleId: Int
    val assignedAt: Instant
}

object UserRoles : EntityTable<UserRole>("user_roles") {
    val userId = integer("user_id").primaryKey()
    val roleId = integer("role_id").primaryKey()
    val assignedAt = timestamp("assigned_at")
}

// Usage with composite key
data class UserRoleId(val userId: Int, val roleId: Int)

EntitySession(connection).use { session ->
    val userRole = session.find<UserRole>(UserRoleId(1, 5))
}
```

---

#### 6. Lazy vs Eager Loading

**Status:** Not implemented (currently all relationships are lazy)
**Priority:** Low

Control when relationships are loaded:

```kotlin
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
            .lazy()  // Load on-demand (default)

        oneToOne("profile", UserProfiles, UserProfiles.userId, this.id)
            .eager()  // Load immediately with parent
    }
}
```

---

#### 7. Optimistic Locking

**Status:** Not implemented
**Priority:** Low

Version field for concurrent update detection:

```kotlin
interface User {
    val id: Int
    val name: String
    val email: String
    val version: Int  // Optimistic lock version
}

object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)
    val version = integer("version").version()  // Mark as version column
}

// Usage
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)  // version = 5
        user.name = "Updated"

        flush()
        // UPDATE users SET name = ?, version = 6 WHERE id = ? AND version = 5
        // Throws OptimisticLockException if version mismatch
    }
}
```

---

#### 8. Entity Lifecycle Callbacks

**Status:** Not implemented
**Priority:** Low

Hooks for entity lifecycle events:

```kotlin
interface User {
    val id: Int
    val name: String
    val createdAt: Instant
    val updatedAt: Instant

    fun onPrePersist() {
        // Called before INSERT
    }

    fun onPostLoad() {
        // Called after entity loaded from database
    }

    fun onPreUpdate() {
        // Called before UPDATE
    }
}
```

---

## Future: R2DBC and Async Support

**Status:** Not implemented
**Priority:** Future consideration

Support for reactive, non-blocking database operations using R2DBC:

```kotlin
// Suspend function API
interface User {
    val id: Int
    val name: String
    val email: String

    // Suspend function for R2DBC
    suspend fun orders(): List<UserOrder>
}

// R2DBC EntitySession
class R2dbcEntitySession(private val connection: Connection) {
    suspend fun <E : Any> find(id: Any): E?
    suspend fun <E : Any> save(entity: E)
    suspend fun <E : Any> delete(entity: E)
    suspend fun flush()
}
```

**Key Features:**

- Non-blocking I/O with coroutines
- Reactive streams (Flow) support
- Backpressure handling
- Transaction management with suspend functions

**Migration Path:**

- Keep JDBC support for blocking operations
- Add R2DBC as optional dependency
- Users choose driver based on use case

---

## Recommended Implementation Order

**📋 Phase 1: CRUD Completion** ⭐⭐⭐ Critical Priority

1. UPDATE statements
2. DELETE statements
3. HAVING clause for aggregate filtering

**📋 Phase 2: JOIN Enhancements** ⭐⭐ High Priority

4. LEFT/RIGHT/FULL OUTER JOIN
5. DISTINCT / DISTINCT ON

**📋 Phase 3: Conditional Logic** ⭐ High Priority

6. CASE expressions

**📋 Phase 4: Entity Layer Enhancements** ⭐⭐ High Priority

7. Many-to-many relationships
8. Improved Entity CRUD API (extension functions)
9. Batch loading (N+1 prevention)

**📋 Phase 5: Advanced Queries** Medium Priority

10. Common Table Expressions (Non-Recursive WITH)
11. UNION/INTERSECT/EXCEPT with type coercion

**📋 Phase 6: Entity Layer Advanced** Medium Priority

12. Cascade operations
13. Composite primary keys
14. Lazy vs eager loading

**📋 Phase 7: Specialized PostgreSQL Features** Lower Priority

15. Window functions
16. LATERAL joins
17. Table inheritance hierarchies
18. JSON/JSONB operations
19. Array operations

**📋 Phase 8: Performance and Infrastructure** Lower Priority

20. Optimistic locking
21. Entity lifecycle callbacks
22. Query result caching
23. Connection pooling improvements
24. Migration support
25. Batch operations

**📋 Phase 9: Async/Reactive Support** Future

26. R2DBC driver support
27. Suspend functions for entity operations
28. Coroutine-based query execution

---

## Contributing

If you'd like to contribute to any of these features, please:

1. Check if there's already an issue for the feature
2. Create a new issue describing your implementation approach
3. Submit a PR with tests demonstrating the feature works

---

## Notes

- All features must maintain **complete type safety** - the core philosophy of Kodama
- Code generation should automatically discover and support new query patterns
- Every feature needs comprehensive test coverage
- SQL generation must handle proper parameter binding to prevent SQL injection
