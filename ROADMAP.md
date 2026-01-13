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

### ✅ Compile-Time Safety for selectAll()

**Status:** Complete
**Impact:** Enhanced type safety

Implemented compile-time validation for `selectAll()` method using phantom type constraints:

**What Changed:**
- ✅ **Removed generic selectAll(table)**: Eliminated runtime validation
- ✅ **Generated table-specific extensions**: ~285 type-safe selectAll methods
- ✅ **Phantom type constraints**: Only available tables can be selected
- ✅ **Compile-time errors**: Invalid table selections caught by compiler

**Example:**
```kotlin
from(Author)
    .join(Book) { book.authorId eq author.id }
    .selectAll(Author)  // ✅ Compiles - Author in query
    .selectAll(Book)    // ✅ Compiles - Book in query
    .selectAll(Order)   // ❌ Compile error - Order not in query!
```

**Technical Details:**
- Generated `selectAll` extensions with phantom type constraints
- Used `@JvmName` annotations to prevent signature clashes
- Growth remains linear: O(N × M) where M = max tables (5)

**Known Limitation:**
- Multi-table query result accessors use nullable types for safety
- This is conservative but handles all join types (INNER/LEFT/RIGHT/FULL)
- Future improvement: Encode join types in phantom types for full nullability precision

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

#### 3. ✅ LEFT/RIGHT/FULL OUTER JOIN

**Status:** ✅ Implemented
**Priority:** ⭐⭐ High

Outer joins for optional relationships:

```kotlin
from(Person)
    .leftJoin(Order) { order.userName eq person.name }
    .selectAll(Person)
    .selectAll(Order)  // Order columns are nullable

// Also available: rightJoin(), fullJoin(), innerJoin()
from(Person)
    .rightJoin(Order) { order.userName eq person.name }
    .selectAll(Person)  // Person columns are nullable
    .selectAll(Order)
```

**Completed:**

- ✅ Added `JoinType.LEFT`, `RIGHT`, `FULL`, `INNER` enum values
- ✅ Added convenience methods: `leftJoin()`, `rightJoin()`, `fullJoin()`, `innerJoin()`
- ✅ Nullable result types for all tables in multi-table queries (conservative)
- ✅ SQL generation for all join types
- ✅ Comprehensive tests for all join types

**Known Limitation:** Result accessors use nullable types for all multi-table queries to safely handle all join types. Future improvement: Encode join type in phantom types for precise nullability.

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

### ✅ Phase 1-5: Core Entity Layer (Complete)

**Status:** ✅ Complete (January 2026)
**Test Coverage:** 76+ tests passing (CRUD, relationships, lifecycle, eager loading)
**Documentation:** See `doc/entities.md` and `doc/internal/entity-layer-implementation-roadmap.md`

**Implemented features:**
- ✅ EntitySession with identity map (one instance per ID)
- ✅ Entity lifecycle states (NEW, MANAGED, PENDING_INSERT, PENDING_UPDATE, PENDING_DELETE)
- ✅ CRUD operations: `find()`, `get()`, `persist()`, `insert()`, `update()`, `remove()`, `flush()`
- ✅ Batch operations: `persistAll()`, `insertAll()`, `updateAll()`, `removeAll()`
- ✅ Change tracking with snapshots (automatic dirty detection)
- ✅ Interface-based entities with generated data class implementations
- ✅ Auto-binding registry with zero-boilerplate auto-discovery (no manual setup required)
- ✅ OneToMany, ManyToOne, and ManyToMany relationships
- ✅ Relationship queries (`findByForeignKey`, `findManyToMany`)
- ✅ Entity lifecycle hooks (pre/post persist, update, delete, load)
- ✅ Eager loading for N+1 query prevention
- ✅ Session statistics and cache management

**Core Files:**
- `EntitySession.kt` (746 lines)
- `EntityBinding.kt` (126 lines)
- `Relationship.kt` + `RelationshipDsl.kt` (171 lines)
- `IdentityMap.kt` (97 lines)
- `EntitySessionTests.kt` (1,119 lines, 49 tests)

**Example Usage:**
```kotlin
EntitySession(connection).use { session ->
    with(session) {
        // Find entity
        val user = get<User>(1)

        // Load relationships
        val orders = user.orders(session)

        // Modify entity
        val modified = user.copy(name = "Updated")
        save<User, Int>(modified)

        // Delete entity
        delete(user)

        // Persist changes
        flush()
    }
}
```

---

### ✅ Phase 5: Essential Missing Features (Complete)

**Status:** ✅ Complete (January 2026)
**Duration:** Completed
**Priority:** ⭐⭐⭐ Critical
**Test Coverage:** 27 additional tests (lifecycle hooks, many-to-many, eager loading)

#### 5.1 Entity Lifecycle Hooks ✅

**Status:** ✅ Complete (January 2026)
**Priority:** ⭐⭐⭐ Critical
**Test Coverage:** 11 new tests, 41 total tests passing

Register callbacks for entity lifecycle events (audit logging, validation, computed fields):

```kotlin
session.registerListener(User::class, object : EntityListener<User> {
    override fun onPreUpdate(entity: User, old: User, session: EntitySession) {
        auditLog.log("User ${entity.id} changed: ${old.email} → ${entity.email}")
    }

    override fun onPrePersist(entity: User, session: EntitySession) {
        require(entity.email.contains("@")) { "Invalid email" }
    }
})
```

**Implementation Tasks:**
- Add `EntityListener<E>` interface with lifecycle methods
- Add listener registry to EntitySession
- Call hooks in flush() operations (executeInsert, executeUpdate, executeDelete)
- Add onPostLoad hook in loadFromDatabase()
- Add comprehensive tests for all lifecycle events

**Use Cases:**
- Audit logging (track who changed what)
- Validation (enforce business rules)
- Computed fields (auto-update timestamps)
- Event sourcing (publish domain events)

#### 5.2 Many-to-Many Relationships ✅

**Status:** ✅ Complete (January 2026)
**Priority:** ⭐⭐⭐ Critical
**Test Coverage:** 12 tests in ManyToManyRelationshipTests.kt

Junction table support for many-to-many relationships:

```kotlin
// EntityTable with many-to-many
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    init {
        manyToMany("roles", Roles, UserRoles, UserRoles.userId, UserRoles.roleId)
    }
}

// Generated interface methods
interface User {
    val id: Int
    val name: String

    fun roles(session: EntitySession): List<Role>
    fun setRoles(session: EntitySession, roles: List<Role>)
    fun addRole(session: EntitySession, role: Role)
    fun removeRole(session: EntitySession, role: Role)
}

// Usage
session.with {
    val user = get<User>(1)
    val roles = user.roles(session)  // SELECT with JOIN
    user.addRole(session, adminRole)  // INSERT INTO user_roles
    flush()
}
```

**Implementation Tasks:**
- Add `ManyToManyRelationship` to Relationship.kt
- Add `manyToMany()` DSL function to RelationshipDsl.kt
- Implement `findManyToMany()` in EntitySession
- Generate relationship accessor methods
- Implement INSERT/DELETE for join table modifications
- Add tests for many-to-many operations

#### 5.3 Eager Loading (N+1 Prevention) ✅

**Status:** ✅ Complete (January 2026)
**Priority:** ⭐⭐ High
**Test Coverage:** 4 tests in EagerLoadingTests.kt

Batch load relationships to avoid N+1 queries:

```kotlin
// Without eager loading - N+1 queries
val users = session.findAll<User>()
users.forEach { user ->
    user.orders(session)  // SELECT per user - N queries!
}

// With eager loading - 2 queries total
val users = session.findAll<User>().with(Users.orders)
users.forEach { user ->
    user.orders(session)  // Already loaded, no query
}

// Multiple relationships
session.findAll<User>()
    .with(Users.orders)
    .with(Users.profile)

// Nested eager loading
session.findAll<User>()
    .with(Users.orders) {
        with(Orders.product)
    }
```

**Implementation Tasks:**
- Add `EagerLoadContext` class to track what to preload
- Add `.with()` extension method
- Implement batch loading (IN queries)
- Store preloaded relationships in session cache
- Modify relationship accessors to check cache first
- Add tests comparing N+1 vs batch loading

#### 5.4 Improved Entity CRUD API (Extension Functions)

**Status:** Not implemented
**Priority:** ⭐⭐ High
**Estimated Effort:** 3-4 days

Extension functions for cleaner syntax:

**Current API (verbose):**
```kotlin
with(session) {
    save<User, Int>(user)  // Type parameters required
    delete(user)
}
```

**Proposed API (clean):**
```kotlin
with(session) {
    user.save()   // Extension function, no type params
    user.delete() // Extension function
}
```

**Implementation Tasks:**
- Generate extension functions with `context(EntitySession)`
- Keep existing methods for backward compatibility
- Update documentation
- Add tests

---

### Phase 6: Advanced Features (Medium Priority)

**Status:** Not started
**Duration:** 3-4 weeks
**Priority:** ⭐ Medium

#### 6.1 Ordered Collections (Default ORDER BY)

**Priority:** ⭐ Medium
**Estimated Effort:** 3-4 days

Default ordering for relationships:

```kotlin
object Users : EntityTable<User>("users") {
    val orders = oneToMany(Orders, Orders.userId, this.id)
        .orderBy(Orders.createdAt.desc(), Orders.id.desc())
}

// Usage - automatically ordered
val orders = user.orders(session)
```

#### 6.2 Self-Referencing Entities

**Priority:** ⭐ Medium
**Estimated Effort:** 3-4 days

Tree structures and graphs:

```kotlin
object Nodes : EntityTable<Node>("nodes") {
    init {
        manyToOne("parent", this, this.parentId, this.id).nullable()
        oneToMany("children", this, this.parentId, this.id)
    }
}
```

#### 6.3 Cache Management Enhancements

**Priority:** ⭐ Medium
**Estimated Effort:** 4-5 days

LRU eviction, cache limits, detailed statistics:

```kotlin
session.setCacheLimit(1000)
session.evict(user)
val stats = session.stats()  // Hit rate, query count
```

#### 6.4 Database-Generated Values

**Priority:** ⭐ Medium
**Estimated Effort:** 4-5 days

Auto-increment IDs, timestamps:

```kotlin
object Users : EntityTable<User>("users") {
    val id = serial("id").primaryKey()
    val createdAt = timestamp("created_at").default(CURRENT_TIMESTAMP)
    val updatedAt = timestamp("updated_at").onUpdate(CURRENT_TIMESTAMP)
}
```

#### 6.5 Composite Primary Keys

**Priority:** ⭐ Medium
**Estimated Effort:** 1 week

Multi-column primary keys:

```kotlin
object UserRoles : EntityTable<UserRole>("user_roles") {
    val userId = integer("user_id").primaryKey()
    val roleId = integer("role_id").primaryKey()
}

// Usage
data class UserRoleId(val userId: Int, val roleId: Int)
session.find<UserRole>(UserRoleId(1, 5))
```

---

### Phase 7: Specialized Features (Lower Priority)

**Status:** Not started
**Duration:** 2-3 weeks
**Priority:** Low

#### 7.1 Field Transformations

**Priority:** Low
**Estimated Effort:** 4-5 days

Encryption, JSON serialization:

```kotlin
object Users : EntityTable<User>("users") {
    val password = varchar("password", 255)
        .transform(
            toDatabase = { encrypt(it) },
            fromDatabase = { decrypt(it) }
        )
}
```

#### 7.2 Entity Refresh

**Priority:** Low
**Estimated Effort:** 2-3 days

Reload entity from database:

```kotlin
session.refresh(user)  // Discard changes, reload from DB
```

#### 7.3 Bidirectional Relationship Validation

**Priority:** Low
**Estimated Effort:** 3-4 days

Validate relationships are properly bidirectional at compile time.

---

### Phase 8: Optimizations and Alternatives (Future)

**Status:** Future consideration

#### 8.1 Thin Entity Client Pattern (Opt-In)

Memory optimization via property delegation:

```kotlin
@ThinClient  // Opt-in annotation
interface User {
    val id: Int
    var name: String  // Delegated to session cache
}
```

**Benefits:** ~70% memory savings per entity
**Trade-offs:** Slower property access, no immutability

#### 8.2 Cascade Operations

Automatically propagate operations to related entities:

```kotlin
object Users : EntityTable<User>("users") {
    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
            .cascade(CascadeType.DELETE)
    }
}

// Usage
session.delete(user)
session.flush()  // Automatically deletes all user's orders
```

**Cascade Types:** SAVE, DELETE, ALL

#### 8.3 Optimistic Locking

Version field for concurrent update detection:

```kotlin
object Users : EntityTable<User>("users") {
    val version = integer("version").version()
}

// UPDATE users SET ..., version = version + 1 WHERE id = ? AND version = ?
// Throws OptimisticLockException if version mismatch
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

### Current Status

**✅ Entity Layer Phase 1-4: COMPLETE** (January 2026)
- EntitySession, identity map, CRUD operations
- 49 tests passing, production-ready foundation

**Next Priorities:**

**📋 DSL Phase 1: CRUD Completion** ⭐⭐⭐ Critical Priority (DSL Layer)

1. UPDATE statements
2. DELETE statements
3. HAVING clause for aggregate filtering

**📋 Entity Layer Phase 5: Essential Missing Features** ⭐⭐⭐ Critical Priority (Entity Layer)

4. Entity lifecycle hooks (audit logging, validation)
5. Many-to-many relationships
6. Eager loading (N+1 prevention)
7. Improved Entity CRUD API (extension functions)

**📋 DSL Phase 2: JOIN Enhancements** ⭐⭐ High Priority (DSL Layer)

8. ✅ LEFT/RIGHT/FULL OUTER JOIN (Complete)
9. DISTINCT / DISTINCT ON

**📋 DSL Phase 3: Conditional Logic** ⭐ High Priority (DSL Layer)

10. CASE expressions

**📋 Entity Layer Phase 6: Advanced Features** ⭐ Medium Priority (Entity Layer)

11. Ordered collections (default ORDER BY)
12. Self-referencing entities
13. Cache management enhancements
14. Database-generated values
15. Composite primary keys

**📋 DSL Phase 4: Advanced Queries** Medium Priority (DSL Layer)

16. Common Table Expressions (Non-Recursive WITH)
17. UNION/INTERSECT/EXCEPT with type coercion

**📋 Entity Layer Phase 7: Specialized Features** Lower Priority (Entity Layer)

18. Field transformations
19. Entity refresh
20. Bidirectional relationship validation

**📋 DSL Phase 5: Specialized PostgreSQL Features** Lower Priority (DSL Layer)

21. Window functions
22. LATERAL joins
23. Table inheritance hierarchies
24. JSON/JSONB operations
25. Array operations

**📋 Entity Layer Phase 8: Optimizations** Future (Entity Layer)

26. Thin entity client pattern (opt-in)
27. Cascade operations
28. Optimistic locking

**📋 Infrastructure: Performance and Infrastructure** Lower Priority

29. Query result caching
30. Connection pooling improvements
31. Migration support
32. Batch operations

**📋 Future: Async/Reactive Support**

33. R2DBC driver support
34. Suspend functions for entity operations
35. Coroutine-based query execution

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
