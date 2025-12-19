# Kodama Feature Roadmap

## Overview
This roadmap outlines planned features for both the DSL layer (query building) and Entity layer (ORM). Features are prioritized based on usage patterns and user needs.

---

## DSL Layer (Query Building)

### Completed Features ✅

### 0. Support Postgres operators

- https://www.postgresql.org/docs/9.0/functions.html

### 1. Nullable Column Support
**Status:** ✅ Implemented (v0.1.0)

Kodama supports nullable columns with full type safety:

```kotlin
object Product : Table("product") {
    val id = integer("id").primaryKey()           // Column<Int>
    val name = varchar("name", 255)               // Column<String>
    val description = varchar("description", 500).nullable()  // Column<String?>
}
```

**Key features:**
- `.nullable()` extension changes type from `Column<T>` to `Column<T?>`
- Result accessors respect nullability: `row.product.description` has type `String?`
- NULL values from database are properly handled
- Full compile-time type safety for both nullable and non-nullable columns

### 2. ORDER BY Clause
**Status:** ✅ Implemented (v0.1.0)

Sort query results with type-safe column references:

```kotlin
query()
    .from(Person)
    .select { +person.all() }
    .orderBy {
        person.age.desc()
        person.name.asc()
    }
```

**Key features:**
- `.asc()` and `.desc()` modifiers on columns
- Multiple column sorting
- Type-safe column references
- Automatic SQL ORDER BY generation

### 3. Aggregate Functions (COUNT, SUM, AVG, MIN, MAX)
**Status:** ✅ Implemented (v0.1.0)

Type-safe aggregate functions with named accessors:

```kotlin
query()
    .from(Order)
    .select_totalRevenue { sum(order.cost) }
    .select_orderCount { count(order.id) }
    .execute(transaction)

// Results have compile-time safe named accessors
results.forEach { row ->
    val revenue: Number = row.totalRevenue
    val count: Number = row.orderCount
}
```

**Key features:**
- All standard aggregate functions: `count()`, `sum()`, `avg()`, `min()`, `max()`
- Method-based selection with automatic alias inference
- Type-safe result accessors
- Compile-time safety - only access selected aggregates

### 4. GROUP BY (Automatic)
**Status:** ✅ Implemented (v0.1.0)

When mixing columns with aggregates, GROUP BY is automatically added:

```kotlin
query()
    .from(Order)
    .select { order.userName }
    .select_orderCount { count(order.id) }
    .execute(transaction)

// Automatically generates: SELECT user_name, COUNT(id) FROM orders GROUP BY user_name
```

**Key features:**
- Automatic GROUP BY generation when mixing columns + aggregates
- Type-safe mixed queries
- No manual GROUP BY specification needed

### 5. INSERT Statements
**Status:** ✅ Implemented (v0.1.0)

Type-safe INSERT operations with compile-time column validation:

```kotlin
// All columns are required parameters
val result = Order.insert(
    transaction = transaction,
    id = 100,
    userName = "user",
    product = "Laptop",
    cost = 1500
)

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

**Key features:**
- Generated extension methods on table objects
- All columns required as parameters (forces code review on schema changes)
- Nullable columns have `Type?` parameter
- Returns `InsertResult` with `rowsAffected` and `generatedKeys`
- Proper NULL handling with `PreparedStatement.setNull()`

---

### Top Priority Features (DSL)

### 1. LIMIT and OFFSET (Pagination) ⭐ Critical
Critical for performance and pagination. Used in virtually every application.

**Status:** Not implemented

**Example Usage:**
```kotlin
query()
    .from(Person)
    .select { +person.all() }
    .limit(10)
    .offset(20)

// Typical pagination pattern
val page = 2
val pageSize = 10
query()
    .from(Person)
    .select { +person.all() }
    .orderBy { person.id.asc() }
    .limit(pageSize)
    .offset(page * pageSize)
```

**Implementation Tasks:**
- Add `limit(n: Int)` method to query builders
- Add `offset(n: Int)` method to query builders
- Update Query class to track LIMIT/OFFSET values
- Update SQL generation to append LIMIT/OFFSET
- Add tests for pagination scenarios

---

### 2. HAVING Clause
Filter aggregate results (works with GROUP BY).

**Status:** Not implemented

**Example Usage:**
```kotlin
query()
    .from(Order)
    .select { order.userName }
    .select_orderCount { count(order.id) }
    .having { count(order.id) gt 5 }
```

**Implementation Tasks:**
- Add `having` method to query builders
- Support aggregate functions in HAVING clause
- Update SQL generation
- Add tests for HAVING with aggregates

---

### Secondary Priority Features (DSL)

### 3. LEFT/RIGHT/FULL OUTER JOIN
You already have INNER join, but outer joins are very common for optional relationships.

**Status:** Partially implemented (INNER JOIN only)

**Example Usage:**
```kotlin
query()
    .from(Person)
    .leftJoin(Order) { order.userName eq person.name }
    .select {
        +person.all()
        +order.product  // May be null
    }

// Explicit type parameter
query()
    .from(Person)
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

### 4. DISTINCT
Removing duplicates is frequently needed.

**Status:** Not implemented

**Example Usage:**
```kotlin
query()
    .from(Person)
    .distinct()
    .select { +person.name }

// DISTINCT ON (PostgreSQL specific)
query()
    .from(Order)
    .distinctOn { order.userName }
    .select {
        +order.userName
        +order.product
    }
```

**Implementation Tasks:**
- Add `distinct()` method to query builders
- Add `distinctOn()` for PostgreSQL-specific syntax
- Update SQL generation
- Add tests for DISTINCT queries

---

### 5. IN operator and subqueries
**Status:** Not implemented

**Example Usage:**
```kotlin
// IN with list
query()
    .from(Person)
    .select { +person.all() }
    .where { person.name inList listOf("kodama", "user2", "user3") }

// IN with subquery
query()
    .from(Person)
    .select { +person.all() }
    .where {
        person.name inQuery {
            query()
                .from(Order)
                .select { +order.userName }
                .where { order.cost gt 1000 }
        }
    }

// NOT IN
query()
    .from(Person)
    .where { person.name notInList listOf("banned1", "banned2") }
```

**Implementation Tasks:**
- Implement `inList()` operator for collections
- Implement `inQuery()` operator for subqueries
- Implement `notInList()` and `notInQuery()`
- Support subquery execution
- Update SQL generation with proper parameter binding
- Add tests for IN operations

---

### 6. More comparison operators
**Status:** Partially implemented (only `eq` exists)

**Example Usage:**
```kotlin
// Comparison operators
where { person.age gt 18 }      // >
where { person.age gte 18 }     // >=
where { person.age lt 65 }      // <
where { person.age lte 65 }     // <=
where { person.age neq 0 }      // !=

// String operators
where { person.name like "%kodama%" }
where { person.name ilike "%KODAMA%" }  // case-insensitive
where { person.name startsWith "kod" }
where { person.name endsWith "ama" }

// NULL checks
where { person.email.isNull() }
where { person.email.isNotNull() }

// BETWEEN
where { person.age.between(18, 65) }
```

**Implementation Tasks:**
- Add comparison operators: `gt`, `gte`, `lt`, `lte`, `neq`
- Add string operators: `like`, `ilike`, `startsWith`, `endsWith`
- Add null operators: `isNull()`, `isNotNull()`
- Add `between()` operator
- Update SQL generation
- Add comprehensive operator tests

---

### 7. UPDATE and DELETE statements
Complete CRUD support with UPDATE and DELETE operations.

**Status:** Not implemented (INSERT is complete)

**Example Usage:**
```kotlin
// UPDATE
update(Person)
    .set {
        person.age = 26
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

### 8. AND/OR boolean combinations in WHERE
**Status:** Not implemented

**Example Usage:**
```kotlin
// AND
query()
    .from(Person)
    .where {
        (person.age gt 18) and (person.name eq "kodama")
    }

// OR
query()
    .from(Person)
    .where {
        (person.age lt 18) or (person.age gt 65)
    }

// Complex combinations
query()
    .from(Person)
    .where {
        ((person.age gt 18) and (person.age lt 65)) or (person.name eq "admin")
    }
```

**Implementation Tasks:**
- Implement `and` infix operator for expressions
- Implement `or` infix operator for expressions
- Support expression grouping with parentheses
- Handle operator precedence correctly
- Update SQL generation
- Add tests for complex boolean logic

---

### Additional Features (DSL - Lower Priority)

### 11. UNION / UNION ALL
Combining multiple query results.

### 12. Window Functions
ROW_NUMBER(), RANK(), LAG(), LEAD(), etc.

### 13. Common Table Expressions (WITH clause)
For complex queries with CTEs.

### 14. CASE expressions
Conditional logic in SELECT.

### 15. JSON/JSONB operations
PostgreSQL-specific JSON support.

### 16. Array operations
PostgreSQL array types and operations.

### 17. Transactions and batch operations
Better transaction management and batch inserts/updates.

### 18. Connection pooling
Efficient connection management.

### 19. Migration support
Schema versioning and migration tools.

### 20. Query caching
Cache compiled queries for performance.

---

## Entity Layer (ORM)

### Completed Features ✅

#### 1. Interface-Based Entity Definitions
**Status:** ✅ Implemented (v0.2.0)

Define entities as interfaces with auto-generated implementations:

```kotlin
// User-defined interface
interface User {
    val id: Int
    val name: String
    val email: String
}

interface UserOrder {
    val id: Int
    val userId: Int
    val product: String
    val amount: Int
}

// Generated implementation (internal)
internal data class UserImpl(...) : User
fun User(...): User = UserImpl(...)

// EntityTable definition
object Users : EntityTable<User>("users", User::class) {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)
}
```

**Key features:**
- Interface-based entity definitions
- Auto-generated internal data class implementations
- Factory functions for entity construction
- Backward compatible with data class entities
- EntityBinding generation for ResultSet mapping
- Type-safe entity operations

#### 2. EntitySession - Basic CRUD
**Status:** ✅ Implemented (v0.2.0)

Type-safe entity operations with identity map:

```kotlin
EntitySession(connection).use { session ->
    // Find by ID
    val user = session.find<User>(1)

    // Save new entity
    val newUser = User(id = 2, name = "Alice", email = "alice@example.com")
    session.save<User, Int>(newUser)

    // Delete entity
    session.delete(user)

    // Flush changes to database
    session.flush()
}
```

**Key features:**
- Identity map / entity caching (ensures same instance for same ID)
- find() - load entity by primary key
- save() - insert new entities
- delete() - remove entities
- flush() - commit pending changes
- Transaction-scoped sessions
- Automatic ResultSet to entity mapping

#### 3. One-to-Many Relationships with Context Parameters
**Status:** ✅ Implemented (v0.2.0)

Type-safe relationship navigation with Kotlin context parameters:

```kotlin
// EntityTable with relationship declaration
object Users : EntityTable<User>("users", User::class) {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    val orders = oneToMany(UserOrders, UserOrders.userId)
}

// Generated extension function
context(session: EntitySession)
fun User.orders(): List<UserOrder> {
    return session.findByForeignKey<UserOrder, Int, Int>(
        UserOrders, UserOrders.userId, this.id
    )
}

// Usage with context parameters
EntitySession(connection).use { session ->
    with(session) {
        val user = find<User>(1)!!
        val orders = user.orders()  // Clean syntax!

        orders.forEach { order ->
            println("${order.product}: ${order.amount}")
        }
    }
}
```

**Key features:**
- Declarative relationship definitions in EntityTable
- Auto-generated extension functions with context parameters
- Uses Kotlin 2.2.0 context parameters (non-deprecated)
- Identity map integration (cached entities returned)
- Lazy loading (relationships loaded on-demand)
- Type-safe relationship navigation

#### 4. Many-to-One Relationships
**Status:** ✅ Implemented (v0.2.0)

Navigate from child entities to parent entities:

```kotlin
// Entity interface declaration
interface UserOrder {
    val id: Int
    val userId: Int
    val product: String
    val amount: Int

    // Relationship method declared in interface
    context(session: EntitySession)
    fun user(): User
}

// EntityTable declaration
object UserOrders : EntityTable<UserOrder>("user_orders") {
    val id = integer("id").primaryKey()
    val userId = integer("user_id")
    val product = varchar("product", 255)
    val amount = integer("amount")

    init {
        manyToOne("user", Users, this.userId, Users.id)
    }
}

// Generated implementation (internal)
internal data class UserOrderImpl(...) : UserOrder {
    context(session: EntitySession)
    override fun user(): User {
        return session.find<User>(this.userId)!!
    }
}

// Usage
EntitySession(connection).use { session ->
    with(session) {
        val order = find<UserOrder>(1)!!
        val user = order.user()  // Navigate to parent
        println("Order belongs to: ${user.name}")
    }
}
```

**Key features:**
- Declarative `manyToOne()` DSL in EntityTable init blocks
- Relationship methods declared in entity interfaces
- Auto-generated implementations in internal data classes
- Identity map integration (same parent instance returned)
- Type-safe parent navigation
- Context parameters for clean syntax

#### 5. Bidirectional Relationships
**Status:** ✅ Implemented (v0.2.0)

Both sides of relationships are navigable with consistent identity map:

```kotlin
// Forward relationship (one-to-many)
interface User {
    val id: Int
    val name: String
    val email: String

    context(session: EntitySession)
    fun orders(): List<UserOrder>
}

// Inverse relationship (many-to-one)
interface UserOrder {
    val id: Int
    val userId: Int
    val product: String
    val amount: Int

    context(session: EntitySession)
    fun user(): User
}

// Usage - navigate both directions
EntitySession(connection).use { session ->
    with(session) {
        val user = find<User>(1)!!
        val orders = user.orders()  // User → Orders

        val order = orders.first()
        val parentUser = order.user()  // Order → User

        assert(user === parentUser)  // Same instance from identity map!
    }
}
```

**Key features:**
- Both `oneToMany` and `manyToOne` work together seamlessly
- Identity map ensures referential consistency
- Navigate parent → children → parent returns same instance
- Lazy loading on both sides
- Type-safe bidirectional navigation

---

### Planned Entity Layer Features

---

#### 5. Many-to-Many Relationships
**Status:** Not implemented
**Priority:** High

Junction table support for many-to-many relationships:

```kotlin
// Entity definitions
interface User {
    val id: Int
    val name: String
}

interface Role {
    val id: Int
    val name: String
}

interface UserRole {  // Junction table
    val userId: Int
    val roleId: Int
}

// EntityTable with many-to-many
object Users : EntityTable<User>("users", User::class) {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    val roles = manyToMany(Roles, UserRoles, UserRoles.userId, UserRoles.roleId)
}

// Usage
context(session: EntitySession)
fun User.roles(): List<Role> {
    return session.findManyToMany<Role, Int>(...)
}

EntitySession(connection).use { session ->
    with(session) {
        val user = find<User>(1)!!
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

#### 6. Improved Entity CRUD API
**Status:** Not implemented
**Priority:** High

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

**Implementation Tasks:**
- Generate `Entity.save()` extension functions with `context(EntitySession)`
- Generate `Entity.delete()` extension functions with `context(EntitySession)`
- Keep existing `session.save()` and `session.delete()` for backward compatibility
- Update code generator to emit extension functions in entity impl files
- Add tests for new extension function API
- Update documentation examples

**Benefits:**
- More idiomatic Kotlin (object-oriented style)
- No explicit type parameters needed
- Better IDE discoverability (auto-complete on entity instances)
- Aligns with relationship navigation syntax (e.g., `user.orders()`)

---

#### 7. Cascade Operations
**Status:** Not implemented
**Priority:** Medium

Automatically propagate operations to related entities:

```kotlin
object Users : EntityTable<User>("users", User::class) {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    val orders = oneToMany(UserOrders, UserOrders.userId)
        .cascade(CascadeType.DELETE)  // Delete orders when user deleted
}

// Usage
EntitySession(connection).use { session ->
    with(session) {
        val user = find<User>(1)!!
        delete(user)  // Automatically deletes all user's orders!
        flush()
    }
}
```

**Cascade Types:**
- `CascadeType.SAVE` - Save related entities when parent saved
- `CascadeType.DELETE` - Delete related entities when parent deleted
- `CascadeType.ALL` - Cascade all operations

**Implementation Tasks:**
- Add cascade type configuration to relationships
- Implement cascade logic in save/delete operations
- Handle cascade ordering (delete children before parent)
- Add tests for cascade scenarios

---

#### 8. Lazy vs Eager Loading
**Status:** Not implemented (currently all relationships are lazy)
**Priority:** Low

Control when relationships are loaded:

```kotlin
object Users : EntityTable<User>("users", User::class) {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    val orders = oneToMany(UserOrders, UserOrders.userId)
        .lazy()  // Load on-demand (default)

    val profile = oneToOne(UserProfiles, UserProfiles.userId)
        .eager()  // Load immediately with parent
}

// Eager loading uses JOIN
// SELECT * FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id WHERE u.id = ?

// Lazy loading uses separate query
// SELECT * FROM user_orders WHERE user_id = ?
```

**Implementation Tasks:**
- Add eager loading configuration
- Generate JOIN queries for eager relationships
- Populate eager relationships in toEntity()
- Add tests comparing lazy vs eager performance

---

#### 9. Dirty Checking
**Status:** Not implemented
**Priority:** Medium

Track which entity properties have changed:

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        val user = find<User>(1)!!

        user.name = "Updated Name"  // Mark as dirty
        user.email = "new@example.com"  // Mark as dirty

        flush()  // Only UPDATE changed columns
        // UPDATE users SET name = ?, email = ? WHERE id = ?
    }
}
```

**Implementation Tasks:**
- Track original entity state in identity map
- Compare current vs original on flush()
- Generate UPDATE only for changed columns
- Add tests for partial updates

---

#### 10. Composite Primary Keys
**Status:** Not implemented
**Priority:** Low

Support entities with multi-column primary keys:

```kotlin
interface UserRole {
    val userId: Int
    val roleId: Int
    val assignedAt: Instant
}

object UserRoles : EntityTable<UserRole>("user_roles", UserRole::class) {
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

**Implementation Tasks:**
- Support multiple primaryKey() declarations
- Create composite key data classes
- Update find() to accept composite keys
- Update identity map to use composite keys
- Add tests for composite key operations

---

#### 11. Optimistic Locking
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

object Users : EntityTable<User>("users", User::class) {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)
    val version = integer("version").version()  // Mark as version column
}

// Usage
EntitySession(connection).use { session ->
    with(session) {
        val user = find<User>(1)!!  // version = 5
        user.name = "Updated"

        flush()
        // UPDATE users SET name = ?, version = 6 WHERE id = ? AND version = 5
        // Throws OptimisticLockException if version mismatch
    }
}
```

**Implementation Tasks:**
- Add `.version()` column modifier
- Increment version on every update
- Add version to UPDATE WHERE clause
- Throw exception on version mismatch
- Add tests for concurrent updates

---

#### 12. Entity Lifecycle Callbacks
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

**Lifecycle Events:**
- `@PrePersist` - Before INSERT
- `@PostPersist` - After INSERT
- `@PreUpdate` - Before UPDATE
- `@PostUpdate` - After UPDATE
- `@PreDelete` - Before DELETE
- `@PostDelete` - After DELETE
- `@PostLoad` - After entity loaded

**Implementation Tasks:**
- Define callback interfaces
- Call callbacks at appropriate lifecycle points
- Support both interface methods and annotations
- Add tests for callback execution

---

#### 13. Batch Loading (N+1 Prevention)
**Status:** Not implemented
**Priority:** Medium

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
        val allOrders = session.batchLoad(users) { it.orders() }
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

#### 14. Query Result Caching
**Status:** Not implemented
**Priority:** Low

Cache frequently accessed entities:

```kotlin
EntitySession(connection).use { session ->
    session.enableCache()

    val user1 = session.find<User>(1)  // Query database
    val user2 = session.find<User>(1)  // Return cached

    assert(user1 === user2)  // Same instance

    session.clearCache()
}
```

**Implementation Tasks:**
- Implement second-level cache (beyond identity map)
- Add cache eviction policies (LRU, time-based)
- Support cache invalidation on updates
- Add configuration for cache size limits

---

## Recommended Implementation Order

**✅ Phase 1: Core Query Features (COMPLETED)**
1. ✅ SELECT queries with type-safe column selection
2. ✅ INNER JOIN with multiple table support
3. ✅ WHERE clause with eq operator
4. ✅ ORDER BY clause with asc/desc
5. ✅ Nullable column support

**✅ Phase 2: Analytics Support (COMPLETED)**
6. ✅ Aggregate functions (COUNT, SUM, AVG, MIN, MAX)
7. ✅ Automatic GROUP BY for mixed column + aggregate queries
8. ✅ Type-safe named aggregate accessors

**✅ Phase 3: Data Manipulation - Part 1 (COMPLETED)**
9. ✅ INSERT statements with compile-time column validation

**✅ Phase 4: Entity Layer - Core (COMPLETED)**
10. ✅ Interface-based entity definitions
11. ✅ EntitySession with identity map
12. ✅ Basic CRUD operations (find, save, delete, flush)
13. ✅ One-to-many relationships with context parameters
14. ✅ Auto-generated relationship extensions

**🚧 Phase 5: Essential DSL Query Features (IN PROGRESS)**
15. LIMIT and OFFSET (pagination)
16. HAVING clause for aggregate filtering
17. AND/OR boolean combinations in WHERE
18. More comparison operators (gt, lt, gte, lte, neq, isNull, isNotNull)

**🚧 Phase 6: Entity Layer - Relationships (IN PROGRESS)**
19. ✅ Many-to-one relationships
20. Many-to-many relationships with junction tables
21. ✅ Bidirectional relationships
22. Batch loading (N+1 prevention)

**📋 Phase 7: Advanced DSL Queries (PLANNED)**
23. IN operator and subqueries
24. LEFT/RIGHT/FULL OUTER JOIN
25. DISTINCT
26. Additional string operators (LIKE, ILIKE)

**📋 Phase 8: Data Manipulation - Part 2 (PLANNED)**
27. UPDATE statements
28. DELETE statements
29. RETURNING clause support

**📋 Phase 9: Entity Layer - Advanced (PLANNED)**
30. Improved Entity CRUD API (extension functions: entity.save(), entity.delete())
31. Cascade operations (save, delete)
32. Dirty checking for partial updates
33. Composite primary keys
34. Optimistic locking with version field

**📋 Phase 10: Advanced Features (FUTURE)**
35. Window functions
36. CTEs (WITH clause)
37. CASE expressions
38. PostgreSQL-specific features (JSON, arrays)
39. Entity lifecycle callbacks
40. Query result caching

**📋 Phase 11: Async/Reactive Support (FUTURE)**
41. R2DBC driver support (reactive PostgreSQL)
42. Suspend functions for entity operations
43. Coroutine-based query execution
44. Reactive streams integration
45. Non-blocking connection pooling

---

## R2DBC and Async Driver Support

### Future: R2DBC Integration
**Status:** Not implemented
**Priority:** Future consideration

Support for reactive, non-blocking database operations using R2DBC (Reactive Relational Database Connectivity):

```kotlin
// Suspend function API
interface User {
    val id: Int
    val name: String
    val email: String

    // Suspend function for R2DBC
    suspend fun orders(): List<UserOrder>
}

// Usage with coroutines
suspend fun loadUserWithOrders(userId: Int): UserData = coroutineScope {
    val user = entitySession.findSuspend<User>(userId)
    val orders = user.orders()  // Suspend function
    UserData(user, orders)
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
- Connection pooling with R2DBC Pool
- Transaction management with suspend functions
- Compatible with Spring WebFlux

**Implementation Tasks:**
- Add R2DBC PostgreSQL driver dependency
- Implement `R2dbcEntitySession` with suspend functions
- Generate suspend relationship methods
- Add coroutine Flow support for queries
- Implement reactive transaction management
- Add connection pooling configuration
- Ensure compatibility with existing JDBC code
- Add comprehensive async tests

**Migration Path:**
- Keep JDBC support for blocking operations
- Add R2DBC as optional dependency
- Users choose driver based on use case:
  - JDBC for traditional applications
  - R2DBC for reactive/high-concurrency applications

---

## Contributing

If you'd like to contribute to any of these features, please:
1. Check if there's already an issue for the feature
2. Create a new issue describing your implementation approach
3. Submit a PR with tests demonstrating the feature works

---

## Notes

- All features should maintain **complete type safety** - the core philosophy of Kodama
- Code generation should automatically discover and support new query patterns
- Every feature needs comprehensive test coverage
- SQL generation must handle proper parameter binding to prevent SQL injection
