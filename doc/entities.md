# Entity Layer (ORM)

The entity layer provides type-safe ORM functionality on top of Kodama's query builder, enabling CRUD operations, relationships, and entity lifecycle management.

## Key Features

- **Interface-Based Entities** - Define entities as interfaces, implementations are auto-generated
- **Identity Map** - Same ID always returns same instance within a session
- **Type-Safe Relationships** - Navigate parent ↔ children with compile-time safety
- **Context Parameters** - Clean syntax using Kotlin 2.2.0 context parameters
- **Zero Reflection** - All type safety via code generation
- **Lazy Loading** - Relationships loaded on-demand

## Quick Start

### Define Entity and Table

```kotlin
// Entity interface
interface User {
    val id: Int
    val name: String
    val email: String

    // Relationship method
    context(session: EntitySession)
    fun orders(): List<UserOrder>
}

interface UserOrder {
    val id: Int
    val userId: Int
    val product: String
    val amount: Int

    context(session: EntitySession)
    fun user(): User
}

// EntityTable definitions with relationships
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
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
```

### CRUD Operations

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        // CREATE - save and flush
        val newUser = User(id = 0, name = "Alice", email = "alice@example.com")
        save<User, Int>(newUser)
        flush()  // Executes INSERT

        // READ - get (non-null) or find (nullable)
        val user = get<User>(1)  // Throws if not found
        val maybeUser = find<User>(999)  // Returns null if not found

        // UPDATE - modify and save
        val updatedUser = user.copy(email = "newemail@example.com")
        save<User, Int>(updatedUser)
        flush()  // Executes UPDATE (only changed columns)

        // DELETE
        delete(user)
        flush()  // Executes DELETE
    }
}
```

### Relationships

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)

        // One-to-many: parent → children
        val orders = user.orders()
        orders.forEach { order ->
            println("${order.product}: ${order.amount}")
        }

        // Many-to-one: child → parent
        val firstOrder = orders.first()
        val parentUser = firstOrder.user()

        // Same instance from identity map
        assert(user === parentUser)
    }
}
```

## Code Generation

The Kodama compiler plugin generates:

1. **Internal implementations** - `internal data class UserImpl(...) : User`
2. **Factory functions** - `fun User(...): User = UserImpl(...)`
3. **EntityBindings** - Map database rows to entities
4. **Relationship methods** - Implementations in generated classes

### Generated Example

```kotlin
// Generated in build/generated/kodama/.../impl/UserImpl.kt
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

fun User(id: Int, name: String, email: String): User =
    UserImpl(id, name, email)
```

## API Reference

### EntitySession Methods

- `get<E>(id)` - Get entity by ID (throws EntityNotFoundException if not found)
- `find<E>(id)` - Find entity by ID (returns null if not found)
- `save<E, ID>(entity)` - Stage entity for INSERT or UPDATE
- `delete(entity)` - Stage entity for DELETE
- `flush()` - Execute all pending operations (INSERT/UPDATE/DELETE)
- `clear()` - Clear identity map and pending operations
- `close()` - Close session (automatically called by `use`)

### Relationship Declarations

**One-to-Many** (parent has many children):
```kotlin
init {
    oneToMany("relationshipName", TargetTable, TargetTable.foreignKey, this.primaryKey)
}
```

**Many-to-One** (child belongs to parent):
```kotlin
init {
    manyToOne("relationshipName", TargetTable, this.foreignKey, TargetTable.primaryKey)
}
```

## Identity Map

The identity map ensures referential consistency within a session:

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        val user1 = get<User>(1)
        val user2 = get<User>(1)

        assert(user1 === user2)  // Same instance

        val order = get<UserOrder>(1)
        val parent = order.user()

        assert(user1 === parent)  // Also same instance
    }
}
```

**Benefits:**
- Prevents duplicate database queries for same entity
- Ensures consistency when navigating relationships
- Enables efficient change tracking

## Best Practices

### 1. Use `with(session)` for clean syntax

```kotlin
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)
        val orders = user.orders()
        // Clean - no session.get(), session.orders() prefixes
    }
}
```

### 2. Choose `get()` vs `find()` appropriately

- Use `get<E>(id)` when entity **must exist** (throws exception otherwise)
- Use `find<E>(id)` when entity **might not exist** (returns nullable)

### 3. Flush strategically

```kotlin
// Good - batch multiple operations
session.save(user1)
session.save(user2)
session.save(user3)
session.flush()  // 3 INSERTs in one batch

// Less efficient - flush after each
session.save(user1)
session.flush()
session.save(user2)
session.flush()
```

### 4. Use transaction boundaries

```kotlin
connection.autoCommit = false
try {
    EntitySession(connection).use { session ->
        with(session) {
            // Multiple operations
            save(entity1)
            save(entity2)
            flush()
        }
    }
    connection.commit()
} catch (e: Exception) {
    connection.rollback()
    throw e
}
```

## Limitations (Current Version)

- Single-column primary keys only (composite keys not yet supported)
- No cascade operations (must manually delete children before parent)
- No lazy/eager loading configuration (all relationships are lazy)
- No automatic dirty checking (use explicit `save()` after modifications)
- No many-to-many relationships yet (planned)
- No batch loading (N+1 queries for relationships)

See [ROADMAP.md](../ROADMAP.md) for planned features.

## Migration from Query DSL

Existing query DSL code continues to work. Entity layer is optional:

```kotlin
// Query DSL - still works!
query()
    .from(Users)
    .join(Orders) { orders.userId eq users.id }
    .selectAll(Users)
    .execute(transaction)

// Entity layer - new option
EntitySession(connection).use { session ->
    with(session) {
        val user = get<User>(1)
        val orders = user.orders()
    }
}
```

Both approaches can coexist in the same project.
