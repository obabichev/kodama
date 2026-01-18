# Entity Layer (ORM)

The entity layer provides type-safe ORM functionality on top of Kodama's query builder, enabling CRUD operations, relationships, lifecycle management, and efficient data loading.

## Table of Contents

- [Key Features](#key-features)
- [Quick Start](#quick-start)
- [CRUD Operations](#crud-operations)
- [Relationships](#relationships)
- [Lifecycle Hooks](#lifecycle-hooks)
- [Eager Loading (N+1 Prevention)](#eager-loading-n1-prevention)
- [Advanced Features](#advanced-features)
- [API Reference](#api-reference)
- [Best Practices](#best-practices)
- [Code Generation](#code-generation)

---

## Key Features

### Core Features ✅
- **Interface-Based Entities** - Define entities as interfaces, implementations are auto-generated
- **Identity Map** - Same ID always returns same instance within a session
- **Type-Safe Relationships** - Navigate parent ↔ children with compile-time safety
- **Zero Reflection** - All type safety via code generation
- **Change Tracking** - Automatic dirty detection with snapshots

### CRUD Operations ✅
- **Smart Persist** - `persist()` automatically chooses INSERT or UPDATE
- **Explicit Operations** - `insert()`, `update()`, `remove()` for explicit control
- **Batch Operations** - `persistAll()`, `insertAll()`, `updateAll()`, `removeAll()`
- **Upsert Support** - PostgreSQL `INSERT ... ON CONFLICT ... DO UPDATE`
- **Partial Updates** - Only changed fields are updated

### Relationships ✅
- **One-to-Many** - Parent has many children (e.g., User → Orders)
- **Many-to-One** - Child belongs to parent (e.g., Order → User)
- **Many-to-Many** - Junction table support (e.g., User ↔ Roles)
- **Lazy Loading** - Relationships loaded on-demand
- **Eager Loading** - Batch load to prevent N+1 queries

### Lifecycle Management ✅
- **Entity Lifecycle Hooks** - Pre/post callbacks for persist, update, delete, load
- **Validation** - Validate entities before persistence
- **Audit Logging** - Track who changed what and when
- **Computed Fields** - Auto-update timestamps and derived values

---

## Quick Start

### 1. Define Entities and Tables

```kotlin
// Entity interfaces
interface User {
    val id: Int
    val name: String
    val email: String

    // Relationships
    fun orders(session: EntitySession): List<UserOrder>
    fun roles(session: EntitySession): List<Role>
}

interface UserOrder {
    val id: Int
    val userId: Int
    val product: String
    val amount: Int

    fun user(session: EntitySession): User
}

interface Role {
    val id: Int
    val name: String

    fun users(session: EntitySession): List<User>
}

// EntityTable definitions with relationships
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
        manyToMany("roles", Roles, UserRoles, UserRoles.userId, UserRoles.roleId, Roles.id)
    }
}

object UserOrders : EntityTable<UserOrder>("user_orders") {
    val id = integer("id").primaryKey()
    val userId = integer("user_id")
    val product = varchar("product", 255)
    val amount = integer("amount")

    init {
        manyToOne("user", Users, this.userId, Users.id)
    }
}

object Roles : EntityTable<Role>("roles") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    init {
        manyToMany("users", Users, UserRoles, UserRoles.roleId, UserRoles.userId, Users.id)
    }
}

object UserRoles : EntityTable<UserRole>("user_roles") {
    val userId = integer("user_id")
    val roleId = integer("role_id")
}
```

### 2. Basic CRUD Operations

```kotlin
EntitySession(connection).use { session ->
    // CREATE - smart persist
    val user = User(id = 1, name = "Alice", email = "alice@example.com")
    session.persist(user)  // Automatically inserts (entity not in session)

    // READ
    val loaded = session.find<User>(1)  // Returns User?
    val required = session.get<User>(1)  // Returns User (throws if not found)

    // UPDATE
    val modified = loaded!!.copy(email = "alice.new@example.com")
    session.persist(modified)  // Automatically updates (entity already in session)

    // DELETE
    session.remove(user)
}
```

---

## CRUD Operations

### Smart Persist (Recommended)

`persist()` automatically chooses INSERT or UPDATE based on whether the entity is in the identity map:

```kotlin
EntitySession(connection).use { session ->
    val user = User(id = 1, name = "Alice", email = "alice@example.com")

    // First call - INSERT (entity not in session)
    session.persist(user)

    // Modify entity
    val modified = user.copy(email = "alice.updated@example.com")

    // Second call - UPDATE (entity already in session)
    session.persist(modified)
}
```

### Explicit Operations

For explicit control over database operations:

```kotlin
EntitySession(connection).use { session ->
    // Explicit INSERT
    val user = User(id = 1, name = "Alice", email = "alice@example.com")
    session.insert(user)  // Fails if entity already exists

    // Load entity
    val loaded = session.find<User>(1)!!

    // Explicit UPDATE
    val modified = loaded.copy(email = "new@example.com")
    session.update(modified)  // Fails if entity not in session

    // Explicit DELETE
    session.remove(user)
}
```

### Batch Operations

Efficiently process multiple entities:

```kotlin
EntitySession(connection).use { session ->
    val users = listOf(
        User(id = 1, name = "Alice", email = "alice@example.com"),
        User(id = 2, name = "Bob", email = "bob@example.com"),
        User(id = 3, name = "Charlie", email = "charlie@example.com")
    )

    // Batch persist (smart save)
    session.persistAll(users)

    // Batch insert
    session.insertAll(users)

    // Batch update
    val modified = users.map { it.copy(email = "updated@example.com") }
    session.updateAll(modified)

    // Batch delete
    session.removeAll(users)
}
```

### Upsert (PostgreSQL)

Insert or update based on conflict:

```kotlin
EntitySession(connection).use { session ->
    val user = User(id = 1, name = "Alice", email = "alice@example.com")

    // If user with id=1 exists: UPDATE
    // If user with id=1 doesn't exist: INSERT
    session.upsert(user, conflictColumns = listOf(Users.id))

    // Batch upsert
    val users = listOf(user1, user2, user3)
    session.upsertAll(users, conflictColumns = listOf(Users.id))
}
```

**SQL Generated:**
```sql
INSERT INTO "users" (id, name, email)
VALUES (?, ?, ?)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name, email = EXCLUDED.email
```

---

## Relationships

### One-to-Many Relationships

**Parent has many children:**

```kotlin
// Table definition
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
    }
}

// Usage
EntitySession(connection).use { session ->
    val user = session.get<User>(1)

    // Load orders (lazy)
    val orders = user.orders(session)
    orders.forEach { order ->
        println("${order.product}: ${order.amount}")
    }
}
```

### Many-to-One Relationships

**Child belongs to parent:**

```kotlin
// Table definition
object UserOrders : EntityTable<UserOrder>("user_orders") {
    val id = integer("id").primaryKey()
    val userId = integer("user_id")

    init {
        manyToOne("user", Users, this.userId, Users.id)
    }
}

// Usage
EntitySession(connection).use { session ->
    val order = session.get<UserOrder>(1)

    // Load parent user
    val user = order.user(session)
    println("Order placed by: ${user.name}")
}
```

### Many-to-Many Relationships

**Entities related through junction table:**

```kotlin
// Table definitions
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()

    init {
        manyToMany("roles", Roles, UserRoles,
            UserRoles.userId, UserRoles.roleId, Roles.id)
    }
}

object Roles : EntityTable<Role>("roles") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)

    init {
        manyToMany("users", Users, UserRoles,
            UserRoles.roleId, UserRoles.userId, Users.id)
    }
}

object UserRoles : EntityTable<UserRole>("user_roles") {
    val userId = integer("user_id")
    val roleId = integer("role_id")
}

// Usage
EntitySession(connection).use { session ->
    val user = session.get<User>(1)

    // Load roles through junction table
    val roles = user.roles(session)
    println("User has roles: ${roles.map { it.name }}")

    // Navigate from role to users
    val adminRole = session.get<Role>(1)
    val admins = adminRole.users(session)
    println("Admin users: ${admins.map { it.name }}")
}
```

**SQL Generated (Many-to-Many):**
```sql
SELECT target.*, junction.user_id as __source_fk__
FROM "roles" target
INNER JOIN "user_roles" junction
ON target.id = junction.role_id
WHERE junction.user_id IN (?, ?, ?)
```

---

## Lifecycle Hooks

Register callbacks for entity lifecycle events:

### Available Hooks

- `onPrePersist` - Before INSERT
- `onPostPersist` - After INSERT
- `onPreUpdate` - Before UPDATE
- `onPostUpdate` - After UPDATE
- `onPreDelete` - Before DELETE
- `onPostDelete` - After DELETE
- `onPostLoad` - After entity loaded from database

### Basic Usage

```kotlin
EntitySession(connection).use { session ->
    // Register listener for User entities
    session.registerListener(User::class, object : EntityListener<User> {
        override fun onPrePersist(entity: User, session: EntitySession) {
            println("About to insert user: ${entity.name}")
            // Validate entity
            require(entity.email.contains("@")) { "Invalid email" }
        }

        override fun onPostPersist(entity: User, session: EntitySession) {
            println("Inserted user with ID: ${entity.id}")
        }

        override fun onPreUpdate(entity: User, old: User, session: EntitySession) {
            println("Updating user ${entity.id}: ${old.email} → ${entity.email}")
        }

        override fun onPostUpdate(entity: User, old: User, session: EntitySession) {
            println("Updated user: ${entity.id} (was: ${old.name}, now: ${entity.name})")
        }

        override fun onPreDelete(entity: User, session: EntitySession) {
            println("About to delete user: ${entity.name}")
        }

        override fun onPostDelete(entity: User, session: EntitySession) {
            println("Deleted user")
        }

        override fun onPostLoad(entity: User, session: EntitySession) {
            println("Loaded user: ${entity.name}")
        }
    })

    // Operations trigger hooks
    val user = User(id = 1, name = "Alice", email = "alice@example.com")
    session.insert(user)  // Triggers onPrePersist, onPostPersist
}
```

### Audit Logging Example

```kotlin
class AuditLogger<E : Any> : EntityListener<E> {
    override fun onPreUpdate(entity: E, old: E, session: EntitySession) {
        val changes = findChanges(entity, old)
        auditLog.log(AuditEvent(
            entityType = entity::class.simpleName!!,
            entityId = extractId(entity),
            changes = changes,
            timestamp = Instant.now(),
            user = currentUser()
        ))
    }

    private fun findChanges(new: E, old: E): List<Change> {
        // Compare fields using reflection or generated comparison
        return listOf()
    }
}

// Register
session.registerListener(User::class, AuditLogger<User>())
```

### Validation Example

```kotlin
class UserValidator : EntityListener<User> {
    override fun onPrePersist(entity: User, session: EntitySession) {
        validate(entity)
    }

    override fun onPreUpdate(entity: User, old: User, session: EntitySession) {
        validate(entity)
    }

    private fun validate(user: User) {
        require(user.name.isNotBlank()) { "Name cannot be blank" }
        require(user.email.contains("@")) { "Invalid email format" }
        require(user.email.length <= 255) { "Email too long" }
    }
}

session.registerListener(User::class, UserValidator())
```

### Computed Fields Example

```kotlin
// Entity with timestamps
interface User {
    val id: Int
    val name: String
    val email: String
    val createdAt: Instant?
    val updatedAt: Instant?
}

class TimestampListener : EntityListener<User> {
    override fun onPrePersist(entity: User, session: EntitySession) {
        // Set createdAt on insert
        val now = Instant.now()
        val withTimestamps = entity.copy(
            createdAt = now,
            updatedAt = now
        )
        // Update entity in session
    }

    override fun onPreUpdate(entity: User, old: User, session: EntitySession) {
        // Update updatedAt on modification
        val withUpdatedAt = entity.copy(updatedAt = Instant.now())
        // Update entity in session
    }
}
```

---

## Eager Loading (N+1 Prevention)

Prevent N+1 query problems by batch-loading relationships:

### The N+1 Problem

**Without eager loading (N+1 queries):**

```kotlin
EntitySession(connection).use { session ->
    // 1 query to load users
    val users = listOf(
        session.find<User>(1)!!,
        session.find<User>(2)!!,
        session.find<User>(3)!!
    )

    // N queries - one per user!
    users.forEach { user ->
        val orders = user.orders(session)  // SELECT per user ❌
        println("${user.name} has ${orders.size} orders")
    }
}
// Total: 1 + 3 = 4 queries
```

### Solution: Eager Loading

**With eager loading (2 queries):**

```kotlin
EntitySession(connection).use { session ->
    // Load users
    val users = listOf(
        session.find<User>(1)!!,
        session.find<User>(2)!!,
        session.find<User>(3)!!
    )

    // Batch load all orders in one query ✅
    users.withOneToMany<User, UserOrder, Int, Int>(
        session = session,
        sourceEntityType = User::class,
        relationshipName = "orders",
        targetTable = UserOrders,
        foreignKeyColumn = UserOrders.userId,
        sourceIdExtractor = { it.id }
    )

    // Access orders - returns cached results, NO queries!
    users.forEach { user ->
        val orders = user.orders(session)  // Already loaded ✅
        println("${user.name} has ${orders.size} orders")
    }
}
// Total: 1 + 1 = 2 queries (saves 67% queries!)
```

**SQL Generated (Batch Loading):**
```sql
-- Instead of N queries:
-- SELECT * FROM user_orders WHERE user_id = 1
-- SELECT * FROM user_orders WHERE user_id = 2
-- SELECT * FROM user_orders WHERE user_id = 3

-- One batch query:
SELECT * FROM "user_orders" WHERE user_id IN (?, ?, ?)
-- params: [1, 2, 3]
```

### Eager Loading Many-to-Many

```kotlin
EntitySession(connection).use { session ->
    val users = listOf(
        session.find<User>(1)!!,
        session.find<User>(2)!!,
        session.find<User>(3)!!
    )

    // Batch load roles through junction table
    users.withManyToMany<User, Role, Int, Int, Int>(
        session = session,
        sourceEntityType = User::class,
        relationshipName = "roles",
        targetTable = Roles,
        junctionTable = UserRoles,
        sourceForeignKeyColumn = UserRoles.userId,
        targetForeignKeyColumn = UserRoles.roleId,
        targetPrimaryKeyColumn = Roles.id,
        sourceIdExtractor = { it.id }
    )

    // Access roles - cached!
    users.forEach { user ->
        val roles = user.roles(session)
        println("${user.name}: ${roles.map { it.name }}")
    }
}
```

### Chaining Multiple Relationships

Load multiple relationships efficiently:

```kotlin
EntitySession(connection).use { session ->
    val users = loadUsers(session)

    // Chain multiple eager loads
    users
        .withOneToMany<User, UserOrder, Int, Int>(
            session = session,
            sourceEntityType = User::class,
            relationshipName = "orders",
            targetTable = UserOrders,
            foreignKeyColumn = UserOrders.userId,
            sourceIdExtractor = { it.id }
        )
        .withManyToMany<User, Role, Int, Int, Int>(
            session = session,
            sourceEntityType = User::class,
            relationshipName = "roles",
            targetTable = Roles,
            junctionTable = UserRoles,
            sourceForeignKeyColumn = UserRoles.userId,
            targetForeignKeyColumn = UserRoles.roleId,
            targetPrimaryKeyColumn = Roles.id,
            sourceIdExtractor = { it.id }
        )

    // Both relationships cached
    users.forEach { user ->
        val orders = user.orders(session)  // Cached
        val roles = user.roles(session)    // Cached
        println("${user.name}: ${orders.size} orders, ${roles.size} roles")
    }
}
```

---

## Advanced Features

### Identity Map

Ensures referential consistency within a session:

```kotlin
EntitySession(connection).use { session ->
    val user1 = session.get<User>(1)
    val user2 = session.get<User>(1)

    assert(user1 === user2)  // Same instance ✅

    val order = session.get<UserOrder>(1)
    val parent = order.user(session)

    assert(user1 === parent)  // Also same instance ✅
}
```

**Benefits:**
- Prevents duplicate queries for same entity
- Ensures consistency when navigating relationships
- Enables efficient change tracking
- Memory efficient (one instance per unique ID)

### Change Tracking

Automatic dirty detection with snapshots:

```kotlin
EntitySession(connection).use { session ->
    // Load entity (snapshot created)
    val user = session.find<User>(1)!!

    // Modify entity
    val modified = user.copy(
        email = "new@example.com",
        name = "Updated Name"
    )

    // Update detects changes automatically
    session.update(modified)
    // Only changed fields are updated:
    // UPDATE users SET email = ?, name = ? WHERE id = ?
}
```

### Partial Updates

Only modified fields are sent to database:

```kotlin
EntitySession(connection).use { session ->
    val user = session.find<User>(1)!!  // Load: name="Alice", email="alice@example.com"

    // Change only email
    val modified = user.copy(email = "alice.new@example.com")
    session.update(modified)

    // SQL: UPDATE users SET email = ? WHERE id = ?
    // Name is NOT included (unchanged)
}
```

---

## API Reference

### EntitySession Methods

#### Finding Entities

- **`find<E>(id: Any): E?`** - Find entity by ID (nullable)
  - Returns null if not found
  - Uses identity map cache

- **`get<E>(id: Any): E`** - Get entity by ID (non-null)
  - Throws `EntityNotFoundException` if not found
  - Recommended when entity must exist

#### CRUD Operations (Immediate Execution)

- **`persist(entity: E): E`** - Smart save (INSERT if new, UPDATE if exists)
  - Executes immediately
  - Returns saved entity

- **`insert(entity: E): E`** - Explicit INSERT
  - Executes immediately
  - Fails if entity already in session

- **`update(entity: E): E`** - Explicit UPDATE
  - Executes immediately
  - Only updates changed fields
  - Fails if entity not in session

- **`remove(entity: E)`** - Immediate DELETE
  - Executes immediately
  - Removes from identity map

#### Batch Operations

- **`persistAll(entities: List<E>): List<E>`** - Batch persist
- **`insertAll(entities: List<E>): List<E>`** - Batch insert
- **`updateAll(entities: List<E>): List<E>`** - Batch update
- **`removeAll(entities: List<E>)`** - Batch delete

#### Upsert Operations (PostgreSQL)

- **`upsert(entity: E, conflictColumns: List<Column<*>>): E`** - INSERT ON CONFLICT UPDATE
- **`upsertAll(entities: List<E>, conflictColumns: List<Column<*>>): List<E>`** - Batch upsert

#### Legacy API (Staged Execution)

- **`save<E, ID>(entity: E)`** - Stage entity for INSERT or UPDATE
- **`delete(entity: E)`** - Stage entity for DELETE
- **`flush()`** - Execute all pending operations

#### Session Management

- **`clear()`** - Clear identity map and pending operations
- **`close()`** - Close session (called automatically by `use`)
- **`stats(): SessionStats`** - Get session statistics

#### Lifecycle Hooks

- **`registerListener<E>(entityClass: KClass<E>, listener: EntityListener<E>)`**
- **`removeListener<E>(entityClass: KClass<E>, listener: EntityListener<E>)`**

### Relationship Declarations

#### One-to-Many (Parent has many children)

```kotlin
init {
    oneToMany(
        relationshipName = "orders",
        targetTable = UserOrders,
        foreignKeyColumn = UserOrders.userId,
        primaryKeyColumn = this.id
    )
}
```

#### Many-to-One (Child belongs to parent)

```kotlin
init {
    manyToOne(
        relationshipName = "user",
        targetTable = Users,
        foreignKeyColumn = this.userId,
        primaryKeyColumn = Users.id
    )
}
```

#### Many-to-Many (Through junction table)

```kotlin
init {
    manyToMany(
        relationshipName = "roles",
        targetTable = Roles,
        junctionTable = UserRoles,
        sourceForeignKeyColumn = UserRoles.userId,
        targetForeignKeyColumn = UserRoles.roleId,
        targetPrimaryKeyColumn = Roles.id
    )
}
```

### Eager Loading Extensions

#### One-to-Many Eager Loading

```kotlin
fun <E : Any, R : Any, ID : Any, FK : Any> List<E>.withOneToMany(
    session: EntitySession,
    sourceEntityType: KClass<E>,
    relationshipName: String,
    targetTable: EntityTable<R>,
    foreignKeyColumn: Column<FK>,
    sourceIdExtractor: (E) -> FK
): List<E>
```

#### Many-to-Many Eager Loading

```kotlin
fun <E : Any, R : Any, ID : Any, SourceFK : Any, TargetFK : Any> List<E>.withManyToMany(
    session: EntitySession,
    sourceEntityType: KClass<E>,
    relationshipName: String,
    targetTable: EntityTable<R>,
    junctionTable: EntityTable<*>,
    sourceForeignKeyColumn: Column<SourceFK>,
    targetForeignKeyColumn: Column<TargetFK>,
    targetPrimaryKeyColumn: Column<TargetFK>,
    sourceIdExtractor: (E) -> SourceFK
): List<E>
```

---

## Best Practices

### 1. Use `persist()` for Most Cases

The smart `persist()` method is recommended for most use cases:

```kotlin
// Good - smart and flexible
session.persist(user)  // Automatically chooses INSERT or UPDATE

// Less flexible - must know if entity exists
session.insert(user)   // Fails if already exists
session.update(user)   // Fails if doesn't exist
```

### 2. Choose `get()` vs `find()` Appropriately

```kotlin
// Use get() when entity MUST exist
val user = session.get<User>(1)  // Throws if not found
println(user.name)  // No null check needed

// Use find() when entity MIGHT NOT exist
val maybeUser = session.find<User>(999)
if (maybeUser != null) {
    println(maybeUser.name)
}
```

### 3. Use Eager Loading for Collections

Prevent N+1 queries when loading collections:

```kotlin
// Bad - N+1 queries
val users = loadUsers(session)
users.forEach { user ->
    user.orders(session)  // Query per user ❌
}

// Good - 2 queries total
val users = loadUsers(session)
users.withOneToMany(/* ... */)
users.forEach { user ->
    user.orders(session)  // Cached ✅
}
```

### 4. Register Lifecycle Hooks Early

Register hooks before performing operations:

```kotlin
EntitySession(connection).use { session ->
    // Register hooks first
    session.registerListener(User::class, AuditLogger())
    session.registerListener(User::class, UserValidator())

    // Then perform operations
    session.persist(user)  // Hooks fire
}
```

### 5. Use Batch Operations

Process multiple entities efficiently:

```kotlin
// Good - batch operation
session.insertAll(users)  // One flush for all

// Less efficient - multiple operations
users.forEach { user ->
    session.insert(user)  // Flush per user
}
```

### 6. Leverage Identity Map

Take advantage of the identity map for consistency:

```kotlin
EntitySession(connection).use { session ->
    val user = session.get<User>(1)
    val order = session.get<UserOrder>(1)

    // Navigate relationship - returns cached user
    val parent = order.user(session)

    // Modify once, affects all references
    val modified = user.copy(email = "new@example.com")
    session.persist(modified)

    assert(parent.email == "new@example.com")  // ✅ Same instance
}
```

### 7. Use Transaction Boundaries

Always use transactions for data integrity:

```kotlin
connection.autoCommit = false
try {
    EntitySession(connection).use { session ->
        // Multiple operations
        session.persist(entity1)
        session.persist(entity2)
        session.persist(entity3)
    }
    connection.commit()
} catch (e: Exception) {
    connection.rollback()
    throw e
}
```

### 8. Validate with Lifecycle Hooks

Use hooks for centralized validation:

```kotlin
class UserValidator : EntityListener<User> {
    override fun onPrePersist(entity: User, session: EntitySession) {
        validateUser(entity)
    }

    override fun onPreUpdate(entity: User, old: User, session: EntitySession) {
        validateUser(entity)
    }

    private fun validateUser(user: User) {
        require(user.email.contains("@")) { "Invalid email" }
        // ... more validation
    }
}
```

---

## Code Generation

The Kodama compiler plugin (KSP) generates entity implementations automatically.

### What Gets Generated

For each entity interface, Kodama generates:

1. **Internal Implementation** - `internal data class UserImpl(...) : User`
2. **Factory Function** - `fun User(...): User = UserImpl(...)`
3. **Copy Function** - `fun User.copy(...): User`
4. **Entity Binding** - `object UserEntityBinding : EntityBinding<User, Int>`
5. **Relationship Methods** - Implementations in generated classes

### Generated Files Location

```
build/generated/kodama/
└── com/yourpackage/entity/impl/
    ├── UserEntityImpl.kt
    ├── UserOrderEntityImpl.kt
    ├── RoleEntityImpl.kt
    └── UserRoleEntityImpl.kt
```

### Example Generated Code

**For this entity:**
```kotlin
interface User {
    val id: Int
    val name: String
    val email: String

    fun orders(session: EntitySession): List<UserOrder>
}
```

**Kodama generates:**

```kotlin
// UserEntityImpl.kt
private data class UserImpl(
    override val id: Int,
    override val name: String,
    override val email: String
) : User {
    override fun orders(session: EntitySession): List<UserOrder> {
        // Check eager loading cache first
        val cached = session.getCachedRelationship<UserOrder>(
            sourceEntityType = User::class,
            sourceEntityId = id,
            relationshipName = "orders"
        )
        if (cached != null) {
            return cached
        }

        // Load from database
        return session.findByForeignKey<UserOrder, Int, Int>(
            UserOrders, UserOrders.userId, id
        )
    }
}

// Factory function
fun User(id: Int, name: String, email: String): User =
    UserImpl(id, name, email)

// Copy function
fun User.copy(
    id: Int = this.id,
    name: String = this.name,
    email: String = this.email
): User = UserImpl(id, name, email)

// Entity binding
object UserEntityBinding : EntityBinding<User, Int> {
    override val table = Users

    override fun entityId(entity: User): Int = entity.id

    override fun toEntity(resultSet: ResultSet): User {
        return UserImpl(
            id = resultSet.getInt("id"),
            name = resultSet.getString("name"),
            email = resultSet.getString("email")
        )
    }

    override fun toInsertValues(entity: User): Map<Column<*>, Any?> {
        return mapOf(
            Users.id to entity.id,
            Users.name to entity.name,
            Users.email to entity.email
        )
    }

    override fun toUpdateValues(entity: User, original: User): Map<Column<*>, Any?> {
        val changes = mutableMapOf<Column<*>, Any?>()
        if (entity.name != original.name) {
            changes[Users.name] = entity.name
        }
        if (entity.email != original.email) {
            changes[Users.email] = entity.email
        }
        return changes
    }

    override fun primaryKeyColumns(): List<Column<*>> {
        return listOf(Users.id)
    }
}
```

### Auto-Binding Registry

Kodama automatically generates a binding registry that self-initializes with **zero boilerplate**:

```kotlin
// Generated code - automatically discovered and initialized!
object KodamaBindingRegistry {
    val bindings: List<EntityBinding<*, *>> = listOf(
        UserEntityBinding,
        UserOrderEntityBinding,
        RoleEntityBinding
    )

    init {
        // Auto-registers all bindings on first EntitySession use
        val bindingMap = bindings.associateBy { it.entityClass }
        if (EntitySession.autoBindingProvider == null) {
            EntitySession.autoBindingProvider = { entityClass ->
                bindingMap[entityClass]
                    // Handle implementation classes (UserImpl -> User)
                    ?: bindingMap.entries.firstOrNull { (key, _) ->
                        key.simpleName == entityClass.simpleName?.removeSuffix("Impl")
                    }?.value
            }
        }
    }
}
```

**How Auto-Discovery Works:**

1. **KSP generates** a resource file at `META-INF/kodama/binding-registry.txt` containing the registry class name
2. **EntitySession automatically** reads this file and initializes the registry when first loaded
3. **No manual setup required** - just use `EntitySession` and entities work!

**Usage:**

```kotlin
// ✅ NO setup needed - just use EntitySession!
EntitySession(connection).use { session ->
    val user = session.find<User>(1)  // Works automatically!
    val order = session.get<UserOrder>(42)  // No boilerplate required!
}

// ✅ NO companion object needed
// ✅ NO manual imports needed
// ✅ NO EntitySession.autoBindingProvider setup needed
```

**Optional: Explicit Reference**

If you prefer explicit initialization for documentation purposes, you can optionally reference the registry:

```kotlin
class MyTest {
    companion object {
        // Optional: explicitly trigger initialization
        private val initRegistry = KodamaBindingRegistry
    }
}
```

Both approaches work identically - the registry auto-initializes either way.

---

## Current Limitations

- **Single-column primary keys only** - Composite keys not yet supported (Phase 6)
- **No cascade operations** - Must manually manage dependent entities
- **No optimistic locking** - No automatic version checking (Phase 8)
- **No field transformations** - No automatic encryption/decryption (Phase 7)
- **PostgreSQL-specific upsert** - ON CONFLICT syntax requires PostgreSQL

See [ROADMAP.md](../ROADMAP.md) for planned features in Phase 6-8.

---

## Migration from Query DSL

The entity layer is **completely optional** - existing query DSL code continues to work:

```kotlin
// Query DSL - still works!
from(Users)
    .join(UserOrders) { userOrders.userId eq users.id }
    .selectAll(Users)
    .selectAll(UserOrders)
    .execute(transaction)

// Entity layer - new option
EntitySession(connection).use { session ->
    val user = session.get<User>(1)
    val orders = user.orders(session)
}
```

Both approaches can coexist in the same project. Choose based on your use case:

- **Query DSL**: Complex queries, reporting, ad-hoc queries
- **Entity Layer**: CRUD operations, relationship navigation, domain modeling

---

## Complete Example

Putting it all together:

```kotlin
// Define entities and tables
interface User {
    val id: Int
    val name: String
    val email: String
    fun orders(session: EntitySession): List<UserOrder>
    fun roles(session: EntitySession): List<Role>
}

object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
        manyToMany("roles", Roles, UserRoles, UserRoles.userId, UserRoles.roleId, Roles.id)
    }
}

// Usage
fun example(connection: Connection) {
    EntitySession(connection).use { session ->
        // Register lifecycle hooks
        session.registerListener(User::class, object : EntityListener<User> {
            override fun onPrePersist(entity: User, session: EntitySession) {
                require(entity.email.contains("@")) { "Invalid email" }
            }
        })

        // Create user
        val user = User(id = 1, name = "Alice", email = "alice@example.com")
        session.persist(user)

        // Load with relationships (prevent N+1)
        val users = listOf(session.get<User>(1), session.get<User>(2))
        users.withOneToMany<User, UserOrder, Int, Int>(
            session, User::class, "orders",
            UserOrders, UserOrders.userId, { it.id }
        )

        // Access relationships
        users.forEach { u ->
            val orders = u.orders(session)  // Cached!
            println("${u.name}: ${orders.size} orders")
        }

        // Update
        val modified = user.copy(email = "alice.new@example.com")
        session.persist(modified)

        // Delete
        session.remove(user)
    }
}
```

---

## Next Steps

- Review [API Reference](#api-reference) for complete method signatures
- See [ROADMAP.md](../ROADMAP.md) for upcoming features
- Check out test files for more examples:
  - `EntitySessionTests.kt` - Basic CRUD operations
  - `RelationshipTests.kt` - Relationship navigation
  - `ManyToManyRelationshipTests.kt` - Many-to-many relationships
  - `EntityLifecycleHooksTests.kt` - Lifecycle hook examples
  - `EagerLoadingTests.kt` - N+1 prevention examples
  - `EntityCRUDTests.kt` - Improved CRUD API examples
