# Kodama Entity Layer Design

**Version:** 1.0
**Date:** 2026-01-13
**Status:** Proposal

---

## Executive Summary

This document proposes an entity layer for Kodama that maintains its core philosophy of **100% compile-time type safety** while providing a higher-level abstraction for object-relational mapping. Based on comprehensive research of 10 major Java/Kotlin ORM libraries, we propose a **dual-API approach** combining:

1. **Existing DSL API** - For complex queries and SQL control (already implemented)
2. **New Entity Layer** - For simple CRUD operations and relationship navigation

**Key Design Principles:**
- ✅ **Zero Runtime Reflection** - All code generated at compile time
- ✅ **Kotlin-First** - Leverage Kotlin's language features (sealed types, data classes, delegation)
- ✅ **Session-Based Architecture** - Entity session with identity map and caching
- ✅ **Thin Entity Clients** - Entities delegate to session for data access
- ✅ **Type-Safe Relationships** - Compile-time validated joins and navigation
- ✅ **Centralized Change Tracking** - Session tracks all modifications
- ✅ **Minimal Boilerplate** - Generate repetitive code via KSP

**Inspiration:**
- **Hibernate/JPA** - Session pattern with identity map and change tracking
- **Exposed** - Dual API pattern (DSL + DAO)
- **Ktorm** - Entity Sequence API (collection-like operations)
- **Requery** - Zero reflection, compile-time generation
- **jOOQ** - Type-safe code generation

---

## Table of Contents

1. [Research Insights](#research-insights)
2. [Design Goals](#design-goals)
3. [Architecture Overview](#architecture-overview)
4. [Entity Session](#entity-session)
5. [Entity Definition](#entity-definition)
6. [Relationship System](#relationship-system)
7. [CRUD Operations](#crud-operations)
8. [Query API](#query-api)
9. [Change Tracking](#change-tracking)
10. [Code Generation Strategy](#code-generation-strategy)
11. [Implementation Phases](#implementation-phases)
12. [Trade-offs & Decisions](#trade-offs--decisions)
13. [Examples](#examples)

---

## Research Insights

### What We Learned from 10 ORMs

**From Exposed (9,100 stars, Kotlin-first):**
- ✅ **Dual API works well** - DSL for complex queries, DAO for simple CRUD
- ✅ **JetBrains backing** ensures stability and quality
- ✅ **No code generation** is possible but limits type safety
- ⚠️ **Manual table definitions** can drift from schema

**From Ktorm (1,800 stars, Kotlin-only):**
- ✅ **Entity Sequence API** - Most Kotlin-idiomatic (filter, map, sortedBy on entities)
- ✅ **Interface-based entities** - Flexible, proxy-tracked changes
- ✅ **No session management** - Simpler mental model
- ⚠️ **Smaller community** - Less battle-tested

**From Hibernate/JPA (Most popular, enterprise standard):**
- ✅ **Session pattern** - Well-understood, battle-tested architecture
- ✅ **Identity Map** - Single instance per entity ID prevents inconsistencies
- ✅ **First-level cache** - Session caches loaded entities
- ✅ **Lazy loading** - Solves N+1 problems when used correctly
- ✅ **Centralized change tracking** - Session tracks all modifications
- ✅ **Automatic updates** - session.flush() writes all changes
- ❌ **Runtime overhead** - Reflection and dynamic proxies
- ❌ **Detached entities** - Complex attach/reattach semantics
- ⚠️ **LazyInitializationException** - Accessing relations outside session scope

**From Spring Data JPA (Spring ecosystem):**
- ✅ **Repository pattern** - Clean separation of concerns
- ✅ **Query methods from names** - Minimal boilerplate
- ❌ **Tied to Spring** - Framework coupling
- ❌ **Query method names** become unwieldy for complex queries

**From jOOQ (19,300 stars, type-safe SQL):**
- ✅ **Database-first generation** - Schema is source of truth
- ✅ **Type-safe SQL** - Every query checked at compile time
- ✅ **No ORM magic** - Predictable SQL
- ⚠️ **Requires build step** - Must regenerate from DB

**From MyBatis (19,000 stars, SQL mapper):**
- ✅ **SQL control** - Write exact SQL you want
- ✅ **XML separation** - Keep SQL separate from code
- ❌ **Not type-safe** - String-based SQL
- ❌ **Manual relationship handling**

**From JDBI (2,100 stars, lightweight):**
- ✅ **Kotlin data class mapping** - Automatic and clean
- ✅ **SQL Object pattern** - Interface-based
- ✅ **No code generation** - No build step
- ❌ **Not an ORM** - No relationships or change tracking

**From Requery (3,100 stars, Android/JVM):**
- ✅ **Zero reflection** - All compile-time generation
- ✅ **Compile-time validation** - Catches errors at build
- ✅ **Interface or abstract class** entities - Flexible
- ⚠️ **Declining** - Superseded by Room on Android

**From Ebean (1,400 stars, session-less ORM):**
- ✅ **Session-less** - No attach/detach complexity
- ✅ **Active Record pattern** - Intuitive save()/delete()
- ✅ **Query beans** - Type-safe generated queries
- ⚠️ **Bytecode enhancement** - Build-time requirement

**From QueryDSL (5,000 stars, query builder):**
- ✅ **Type-safe queries** - No string-based JPQL
- ✅ **Q-types generation** - Compile-time safety
- ⚠️ **Not standalone ORM** - Enhancement for JPA

### Key Takeaways for Kodama

1. **Dual API is the way** - DSL for power, entity layer for simplicity
2. **Session pattern provides critical benefits** - Identity map, caching, centralized change tracking
3. **But avoid Hibernate's complexity** - No detached entities, no LazyInitializationException
4. **Code generation enables type safety** - Zero runtime reflection, compile-time validation
5. **Interface-based entities are flexible** - Thin clients that delegate to session
6. **Kotlin collections API is intuitive** - Sequence-like entity operations
7. **Identity map prevents bugs** - Single instance per entity ID ensures consistency
8. **Centralized change tracking is cleaner** - Session knows all modifications

---

## Design Goals

### Primary Goals

1. **Maintain Kodama's Core Philosophy**
   - 100% compile-time type safety
   - If it compiles, it works
   - Zero reflection at runtime
   - Kotlin-first design

2. **Simplify Common Operations**
   - CRUD operations should be trivial: `person.save()`, `Person.findById(1)`
   - Relationship navigation: `person.orders.forEach { }`
   - Change tracking: `person.name = "New"; person.save()` (automatic UPDATE)

3. **Preserve Existing Strengths**
   - Keep DSL for complex queries
   - Maintain SQL control
   - Type-safe joins and selections

4. **Battle-Tested Patterns**
   - Learn from 10+ years of ORM evolution
   - Avoid known pitfalls (detached entities, lazy init exceptions)
   - Use proven patterns (Repository, Active Record, Sequence API)

### Non-Goals

- ❌ **Not trying to replace DSL** - Complementary, not replacement
- ❌ **Not supporting Java** - Kotlin-only entity layer (DSL remains Java-compatible)
- ❌ **Not Hibernate's complexity** - No detached entities, simpler session lifecycle
- ❌ **Not runtime reflection** - All compile-time, zero reflection

---

## Architecture Overview

### Session-Based Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        Application Code                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ┌──────────────────────┐            ┌──────────────────────┐    │
│  │    Entity Layer      │            │      DSL Layer       │    │
│  │   (CRUD, simple)     │            │  (Complex queries)   │    │
│  │                      │            │                      │    │
│  │  session.findById()  │            │  from(Person)        │    │
│  │  person.name = "..."│            │    .join(Order)      │    │
│  │  session.flush()     │            │    .where { ... }    │    │
│  └──────────┬───────────┘            └──────────────────────┘    │
│             │                                    │                 │
│             ▼                                    │                 │
│  ┌─────────────────────────────────────────┐    │                 │
│  │        EntitySession                     │    │                 │
│  │  ┌─────────────────────────────────┐   │    │                 │
│  │  │ Identity Map (entity cache)      │   │    │                 │
│  │  │  Person(1) → PersonEntity        │   │    │                 │
│  │  │  Order(42) → OrderEntity         │   │    │                 │
│  │  └─────────────────────────────────┘   │    │                 │
│  │  ┌─────────────────────────────────┐   │    │                 │
│  │  │ Change Tracker                   │   │    │                 │
│  │  │  Person(1).name: "old" → "new"  │   │    │                 │
│  │  │  Order(42).cost: 100 → 150      │   │    │                 │
│  │  └─────────────────────────────────┘   │    │                 │
│  └──────────────────┬──────────────────────┘    │                 │
│                     │                            │                 │
│                     └──────────────┬─────────────┘                 │
│                                    ▼                               │
│                        ┌─────────────────────┐                     │
│                        │   Core Query Layer  │                     │
│                        │   (SQL generation)  │                     │
│                        └─────────────────────┘                     │
├──────────────────────────────────────────────────────────────────┤
│                           PostgreSQL                              │
└──────────────────────────────────────────────────────────────────┘
```

### Entity Layer Components

1. **EntitySession** - Manages entity lifecycle, caching, and change tracking
2. **Identity Map** - Ensures single instance per entity ID
3. **Entity Interfaces** - Define entity structure (user-written)
4. **Entity Implementations** - Thin clients that delegate to session (KSP-generated)
5. **Change Tracker** - Tracks property modifications within session
6. **Entity Sequences** - Collection-like query API (inspired by Ktorm)
7. **Relationship Loaders** - Lazy load relationships through session

---

## Entity Session

### Overview

The **EntitySession** is the core component of the entity layer, inspired by Hibernate's Session but simplified for Kodama's compile-time approach. It provides:

1. **Identity Map** - Ensures single instance per entity ID
2. **First-level cache** - Caches all loaded entities
3. **Change tracking** - Tracks all property modifications
4. **Transaction boundary** - Clear lifecycle (open → flush → close)

### Session Lifecycle

```kotlin
// Open a session (tied to database transaction)
database.withSession { session ->
    // Load entities
    val person = session.findById(Persons, 1)

    // Modify entities
    person.name = "New Name"
    person.age = 26

    // Load relationships
    person.orders.forEach { order ->
        order.cost = order.cost + 10
    }

    // Flush writes all changes to database
    session.flush()  // UPDATE person SET ...; UPDATE order SET ...

} // Session automatically closed, entities become detached
```

### Identity Map Pattern

The session maintains an **identity map** that ensures a single instance per entity ID:

```kotlin
database.withSession { session ->
    val person1 = session.findById(Persons, 1)
    val person2 = session.findById(Persons, 1)

    // Same instance!
    assert(person1 === person2)

    // Modifications visible immediately
    person1.name = "New Name"
    assert(person2.name == "New Name")  // ✅
}
```

**Benefits:**
- ✅ **Prevents inconsistencies** - No two different objects representing same row
- ✅ **Reduces database queries** - Second load returns cached instance
- ✅ **Simplifies change tracking** - Single point of modification

### EntitySession Implementation

```kotlin
class EntitySession(
    private val database: Database,
    private val transaction: Transaction
) : AutoCloseable {

    // Identity map: Table + ID → Entity instance
    private val identityMap = mutableMapOf<EntityKey, Entity<*>>()

    // Change tracker: Entity → Property changes
    private val changeTracker = ChangeTracker()

    // Find by ID (uses identity map)
    fun <T : Entity<T>> findById(table: EntityTable<T>, id: Long): T? {
        val key = EntityKey(table, id)

        // Check identity map first
        @Suppress("UNCHECKED_CAST")
        identityMap[key]?.let { return it as T }

        // Not cached, load from database
        val entity = loadFromDatabase(table, id) ?: return null

        // Store in identity map
        identityMap[key] = entity

        return entity
    }

    // Track property change
    internal fun <T : Any> trackChange(
        entity: Entity<*>,
        column: Column<T>,
        oldValue: T?,
        newValue: T
    ) {
        changeTracker.recordChange(entity, column, oldValue, newValue)
    }

    // Flush all changes to database
    fun flush() {
        val changes = changeTracker.getChanges()

        changes.forEach { (entity, propertyChanges) ->
            // Generate UPDATE statement
            val table = entity.table()
            database.update(table) {
                propertyChanges.forEach { (column, newValue) ->
                    set(column, newValue)
                }
                where { table.primaryKey() eq entity.id }
            }
        }

        // Mark all changes as flushed
        changeTracker.clear()
    }

    // Clear session (detach all entities)
    fun clear() {
        identityMap.clear()
        changeTracker.clear()
    }

    override fun close() {
        clear()
    }
}

// Entity key for identity map
private data class EntityKey(
    val table: EntityTable<*>,
    val id: Long
)
```

### Thin Entity Client Pattern

Entities are **thin clients** that only hold an ID and delegate all data access to the session:

```kotlin
// Generated by KSP
class PersonEntity(
    override val id: Long,
    private val session: EntitySession
) : Person {

    override var name: String
        get() = session.getProperty(Persons, id, Persons.name)
        set(value) {
            val old = session.getProperty(Persons, id, Persons.name)
            session.setProperty(Persons, id, Persons.name, value)
            session.trackChange(this, Persons.name, old, value)
        }

    override var age: Int
        get() = session.getProperty(Persons, id, Persons.age)
        set(value) {
            val old = session.getProperty(Persons, id, Persons.age)
            session.setProperty(Persons, id, Persons.age, value)
            session.trackChange(this, Persons.age, old, value)
        }

    // Lazy-loaded relationships
    override val orders: List<Order> by lazy {
        session.findWhere(Orders) {
            Orders.personId eq this@PersonEntity.id
        }
    }
}
```

**Benefits of Thin Client Pattern:**
- ✅ **Minimal memory footprint** - Entity only stores ID and session reference
- ✅ **Session controls all data** - Centralized caching and change tracking
- ✅ **Identity map enforcement** - Session ensures single instance per ID
- ✅ **Lazy loading through session** - Relationships load on access

### Session Property Storage

The session stores entity properties in a cache:

```kotlin
class EntitySession {
    // Property cache: (Table, ID, Column) → Value
    private val propertyCache = mutableMapMap<PropertyKey, Any?>()

    fun <T : Any> getProperty(
        table: EntityTable<*>,
        id: Long,
        column: Column<T>
    ): T {
        val key = PropertyKey(table, id, column)
        return propertyCache[key] as T
    }

    fun <T : Any> setProperty(
        table: EntityTable<*>,
        id: Long,
        column: Column<T>,
        value: T
    ) {
        val key = PropertyKey(table, id, column)
        propertyCache[key] = value
    }

    private data class PropertyKey(
        val table: EntityTable<*>,
        val id: Long,
        val column: Column<*>
    )
}
```

### Change Tracking

The session tracks all property modifications:

```kotlin
class ChangeTracker {
    // Entity → (Column → New Value)
    private val changes = mutableMapOf<Entity<*>, MutableMap<Column<*>, Any?>>()

    fun <T : Any> recordChange(
        entity: Entity<*>,
        column: Column<T>,
        oldValue: T?,
        newValue: T
    ) {
        if (oldValue == newValue) return  // No actual change

        changes
            .getOrPut(entity) { mutableMapOf() }
            [column] = newValue
    }

    fun getChanges(): Map<Entity<*>, Map<Column<*>, Any?>> {
        return changes
    }

    fun hasChanges(): Boolean {
        return changes.isNotEmpty()
    }

    fun clear() {
        changes.clear()
    }
}
```

### Session Scope and Transactions

Sessions are tied to database transactions:

```kotlin
// Session automatically created with transaction
database.withTransaction { transaction ->
    val session = EntitySession(database, transaction)

    session.use {  // AutoCloseable
        val person = session.findById(Persons, 1)!!
        person.name = "New Name"

        session.flush()  // Write changes

        // On success, transaction commits
        // On exception, transaction rolls back
    }
}

// Convenience method
database.withSession { session ->
    // session is tied to transaction
    val person = session.findById(Persons, 1)!!
    person.name = "New Name"
    session.flush()
}
```

### Preventing LazyInitializationException

Unlike Hibernate, Kodama's sessions don't throw `LazyInitializationException`:

```kotlin
val person: Person = database.withSession { session ->
    session.findById(Persons, 1)!!
    // Session closes here
}

// ❌ Hibernate would throw LazyInitializationException here
person.orders.forEach { ... }  // ERROR in Hibernate

// ✅ Kodama solution: Entities know they're detached
val person: Person = database.withSession { session ->
    val p = session.findById(Persons, 1)!!
    // Eagerly load if needed
    p.orders.size  // Force load before session closes
    p
}

// Now safe to use outside session
person.orders.forEach { ... }  // ✅
```

**Or use explicit eager loading:**

```kotlin
val person = session.findById(Persons, 1, eager = listOf(Persons.orders))
// orders loaded immediately, safe to use outside session
```

### Benefits of Session Pattern

1. **Identity Map** - Single instance per entity prevents bugs
2. **Efficient caching** - Reduces database queries
3. **Centralized change tracking** - Session knows all modifications
4. **Transaction boundary** - Clear lifecycle
5. **Consistent state** - All entities in session see same data

### Avoiding Hibernate's Pitfalls

1. ❌ **No detached entities** - Session lifecycle is clear, entities know when detached
2. ❌ **No LazyInitializationException** - Explicit eager loading or clear error messages
3. ❌ **No complex merge/reattach** - Sessions are short-lived, use new session for new transaction
4. ✅ **Predictable behavior** - Compile-time generated code, no runtime proxies

---

## Entity Definition

### User-Written Entity Interface

Entities are defined as **Kotlin interfaces** (inspired by Ktorm and Requery):

```kotlin
// User writes this
interface Person : Entity<Person> {
    var id: Long
    var name: String
    var age: Int

    // Relationships (read-only, lazy-loaded)
    val orders: List<Order>
    val profile: Profile?
}
```

### Table Definition with Entity Binding

Tables now bind to entity types:

```kotlin
// User writes this
object Persons : EntityTable<Person>("person") {
    val id = long("id").primaryKey().bindTo { it.id }
    val name = varchar("name", 255).bindTo { it.name }
    val age = integer("age").bindTo { it.age }

    // Relationship declarations
    val orders = oneToMany(Orders, Orders.personId, this.id)
    val profile = oneToOne(Profiles, Profiles.personId, this.id)
}

object Orders : EntityTable<Order>("order") {
    val id = long("id").primaryKey().bindTo { it.id }
    val personId = long("person_id").bindTo { it.personId }
    val product = varchar("product", 255).bindTo { it.product }
    val cost = integer("cost").bindTo { it.cost }

    val person = manyToOne(Persons, this.personId, Persons.id)
}
```

### Generated Entity Implementation (Thin Client)

KSP generates a thin client implementation that delegates all data access to the session:

```kotlin
// Generated by KSP
class PersonEntity(
    override val id: Long,
    private val session: EntitySession
) : Person {

    // Properties delegate to session
    override var name: String
        get() = session.getProperty(Persons, id, Persons.name)
        set(value) {
            val oldValue = session.getProperty(Persons, id, Persons.name)
            session.setProperty(Persons, id, Persons.name, value)
            session.trackChange(this, Persons.name, oldValue, value)
        }

    override var age: Int
        get() = session.getProperty(Persons, id, Persons.age)
        set(value) {
            val oldValue = session.getProperty(Persons, id, Persons.age)
            session.setProperty(Persons, id, Persons.age, value)
            session.trackChange(this, Persons.age, oldValue, value)
        }

    // Lazy-loaded relationships (load through session)
    override val orders: List<Order> by lazy {
        session.findWhere(Orders) {
            Orders.personId eq this@PersonEntity.id
        }
    }

    override val profile: Profile? by lazy {
        session.findWhere(Profiles) {
            Profiles.personId eq this@PersonEntity.id
        }.firstOrNull()
    }

    // Entity metadata
    internal fun table(): EntityTable<Person> = Persons
}
```

**Key Characteristics:**
- ✅ **Thin client** - Only stores ID and session reference (~16 bytes per entity)
- ✅ **No state duplication** - All data stored in session's property cache
- ✅ **Session-managed** - Session enforces identity map and tracks changes
- ✅ **Lazy relationships** - Load on first access through session

### Why Interfaces?

**Advantages:**
- ✅ **Flexible** - Can add default methods, extensions
- ✅ **Clean** - User writes minimal code
- ✅ **Type-safe** - Compiler validates structure
- ✅ **Mockable** - Easy to mock for tests

**Used by:** Ktorm, Requery (both successful)

---

## Relationship System

### Relationship Declaration in Tables

Relationships are declared in table objects (aligned with Kodama 2.0 plan):

```kotlin
object Persons : EntityTable<Person>("person") {
    val id = long("id").primaryKey().bindTo { it.id }
    val name = varchar("name", 255).bindTo { it.name }

    // One-to-Many: Person has many Orders
    val orders = oneToMany(
        targetTable = Orders,
        foreignKey = Orders.personId,
        primaryKey = this.id
    )

    // One-to-One: Person has one Profile
    val profile = oneToOne(
        targetTable = Profiles,
        foreignKey = Profiles.personId,
        primaryKey = this.id
    )
}

object Orders : EntityTable<Order>("order") {
    val id = long("id").primaryKey().bindTo { it.id }
    val personId = long("person_id").bindTo { it.personId }

    // Many-to-One: Order belongs to Person
    val person = manyToOne(
        targetTable = Persons,
        foreignKey = this.personId,
        primaryKey = Persons.id
    )
}
```

### Relationship Types

```kotlin
// One-to-Many
fun <Target : Entity<Target>, FK, PK> EntityTable<*>.oneToMany(
    targetTable: EntityTable<Target>,
    foreignKey: Column<FK>,
    primaryKey: Column<PK>
): OneToManyRelationship<Target, FK, PK>

// Many-to-One
fun <Target : Entity<Target>, FK, PK> EntityTable<*>.manyToOne(
    targetTable: EntityTable<Target>,
    foreignKey: Column<FK>,
    primaryKey: Column<PK>
): ManyToOneRelationship<Target, FK, PK>

// One-to-One
fun <Target : Entity<Target>, FK, PK> EntityTable<*>.oneToOne(
    targetTable: EntityTable<Target>,
    foreignKey: Column<FK>,
    primaryKey: Column<PK>
): OneToOneRelationship<Target, FK, PK>

// Many-to-Many (through join table)
fun <Target : Entity<Target>> EntityTable<*>.manyToMany(
    targetTable: EntityTable<Target>,
    joinTable: Table,
    sourceForeignKey: Column<*>,
    targetForeignKey: Column<*>
): ManyToManyRelationship<Target>
```

### Lazy Loading Strategy

Relationships are **lazy-loaded by default** to avoid N+1 problems:

```kotlin
interface Person : Entity<Person> {
    var id: Long
    var name: String

    // Lazy-loaded: not fetched until accessed
    val orders: List<Order>  // Loads when first accessed
}

// Usage
val person = Persons.findById(1)
println(person.name)  // No extra query

// First access triggers query
person.orders.forEach { order ->  // SELECT * FROM order WHERE person_id = 1
    println(order.product)
}
```

### Eager Loading (Explicit)

Eager loading must be explicit to avoid surprises:

```kotlin
// Option 1: Eager load in query
val personsWithOrders = database.sequenceOf(Persons)
    .eager(Persons.orders)  // Explicit eager loading
    .toList()

// Option 2: Use DSL for complex joins
val results = from(Person)
    .join(Order) { order.personId eq person.id }
    .selectAll(Person)
    .selectAll(Order)
    .execute(transaction)
```

---

## CRUD Operations

### Create (Insert)

```kotlin
database.withSession { session ->
    // Option 1: Create entity through session
    val person = session.create(Persons) {
        set(Persons.name, "kodama")
        set(Persons.age, 25)
    }
    // Entity is now in session, changes tracked

    session.flush()  // INSERT INTO person (name, age) VALUES (?, ?)
}

// Option 2: Use DSL for INSERT (outside session)
val personId = Persons.insert {
    set(Persons.name, "kodama")
    set(Persons.age, 25)
}.execute()  // Returns generated ID
```

### Read (Select)

```kotlin
database.withSession { session ->
    // Find by ID (uses identity map)
    val person = session.findById(Persons, 1)  // Person?

    // Find with condition
    val adults = session.findWhere(Persons) {
        Persons.age greater 18
    }

    // Entity Sequence API
    val results = session.sequenceOf(Persons)
        .filter { it.age > 18 }
        .sortedBy { it.name }
        .toList()

    // First or null
    val person = session.sequenceOf(Persons)
        .firstOrNull { it.name eq "kodama" }
}
```

### Update

```kotlin
database.withSession { session ->
    // Automatic change tracking
    val person = session.findById(Persons, 1)!!

    person.name = "New Name"  // Tracked by session
    person.age = 26           // Tracked by session

    session.flush()  // UPDATE person SET name = ?, age = ? WHERE id = ?
}

// Bulk update (DSL - outside session)
database.update(Persons) {
    set(Persons.age, Persons.age + 1)
    where { Persons.age greaterThan 18 }
}
```

### Delete

```kotlin
database.withSession { session ->
    val person = session.findById(Persons, 1)!!

    session.delete(person)
    session.flush()  // DELETE FROM person WHERE id = ?
}

// Bulk delete (DSL - outside session)
database.delete(Persons) {
    where { Persons.age lessThan 18 }
}
```

---

## Query API

### Entity Sequence API (Ktorm-Inspired)

The **Entity Sequence API** provides a collection-like interface for entities:

```kotlin
// Get sequence from database
val personSeq = database.sequenceOf(Persons)

// Filter
val adults = personSeq.filter { it.age > 18 }

// Map
val names = personSeq.map { it.name }

// Sort
val sorted = personSeq.sortedBy { it.age }

// Take/drop
val firstTen = personSeq.take(10)

// Combine operations
val result = database.sequenceOf(Persons)
    .filter { it.age > 18 }
    .sortedByDescending { it.age }
    .map { it.name }
    .take(5)
    .toList()

// Aggregations
val totalAge = personSeq.sumOf { it.age }
val avgAge = personSeq.averageBy { it.age }
val maxAge = personSeq.maxByOrNull { it.age }

// Count
val adultCount = personSeq.count { it.age > 18 }

// Exists
val hasAdults = personSeq.any { it.age > 18 }
```

### Repository Pattern (Alternative)

For teams preferring repository pattern:

```kotlin
// Generated repository interface
interface PersonRepository {
    fun findById(id: Long): Person?
    fun findAll(): List<Person>
    fun save(person: Person): Person
    fun delete(person: Person): Boolean
    fun deleteById(id: Long): Boolean

    // Custom query methods
    fun findByName(name: String): List<Person>
    fun findByAgeGreaterThan(age: Int): List<Person>
}

// Usage
val repo = Persons.repository(database)
val person = repo.findById(1)
val adults = repo.findByAgeGreaterThan(18)
```

### Complex Queries (Fall Back to DSL)

For complex queries, fall back to existing DSL:

```kotlin
// Entity Sequence is great for simple queries
val adults = database.sequenceOf(Persons)
    .filter { it.age > 18 }
    .toList()

// But complex joins? Use DSL
val results = from(Person)
    .join(Order) { order.personId eq person.id }
    .join(Product) { product.id eq order.productId }
    .where {
        person.age greater 18 and
        (product.category eq "Electronics")
    }
    .selectAll(Person)
    .selectAll(Order)
    .execute(transaction)
```

---

## Change Tracking

### Session-Based Change Tracking

All changes are tracked centrally by the EntitySession:

```kotlin
database.withSession { session ->
    val person = session.findById(Persons, 1)!!

    // Initially clean
    assert(!session.hasChanges())

    // Modify properties
    person.name = "New Name"  // Tracked by session
    person.age = 26           // Tracked by session

    // Session knows what changed
    assert(session.hasChanges())

    // Flush writes only changed columns
    session.flush()  // UPDATE person SET name = ?, age = ? WHERE id = ?

    // Clean after flush
    assert(!session.hasChanges())
}
```

### How It Works

When an entity property is modified:

```kotlin
// Generated property setter
override var name: String
    get() = session.getProperty(Persons, id, Persons.name)
    set(value) {
        val oldValue = session.getProperty(Persons, id, Persons.name)  // Get current value
        session.setProperty(Persons, id, Persons.name, value)         // Update in cache
        session.trackChange(this, Persons.name, oldValue, value)       // Track change
    }
```

The session's `ChangeTracker` stores modifications:

```kotlin
class ChangeTracker {
    // Entity → (Column → New Value)
    private val changes = mutableMapOf<Entity<*>, MutableMap<Column<*>, Any?>>()

    fun recordChange(entity: Entity<*>, column: Column<*>, oldValue: Any?, newValue: Any?) {
        if (oldValue == newValue) return  // No change

        changes
            .getOrPut(entity) { mutableMapOf() }
            [column] = newValue
    }

    fun getChanges(): Map<Entity<*>, Map<Column<*>, Any?>> = changes
}
```

### Benefits of Session-Based Tracking

- ✅ **Centralized** - One place to check all changes
- ✅ **Efficient flush** - Write all changes in one transaction
- ✅ **Identity map integration** - Ensures consistency
- ✅ **No state duplication** - Changes tracked separately from data
- ✅ **Predictable** - No bytecode manipulation, all compile-time generated

---

## Code Generation Strategy

### What Gets Generated (KSP)

For each entity interface:

1. **Entity Implementation Class** (~150 lines)
   - Thin client with ID and session reference
   - Property delegationsto session
   - Lazy relationship loaders
   - Metadata methods

2. **Entity Sequence Extensions** (~100 lines)
   - Filter, map, sort operations
   - Aggregation functions

3. **Session Extensions** (~50 lines)
   - `session.findById(Table, id)`
   - `session.findWhere(Table) { }`
   - Table-specific helpers

**Once per project:**

4. **EntitySession** (~300 lines)
   - Identity map implementation
   - Change tracker
   - Property cache
   - Flush logic

### KSP Processor Flow

```
┌─────────────────────────────────────────────────────────┐
│ 1. Scan for EntityTable<T> declarations                 │
│    - Extract entity interface type T                    │
│    - Extract table columns and bindings                 │
│    - Extract relationship declarations                  │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 2. Analyze entity interface                             │
│    - Extract properties (var/val)                       │
│    - Validate bindings exist for all vars               │
│    - Identify relationship properties (val)             │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 3. Generate entity implementation                       │
│    - PropertyState wrappers for vars                    │
│    - Lazy loaders for relationship vals                 │
│    - Change tracking logic                              │
│    - save(), delete(), hasChanges() methods             │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 4. Generate repository                                  │
│    - findById, findAll, save, delete                    │
│    - Custom query methods from table definition         │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 5. Generate entity sequence extensions                  │
│    - filter, map, sortedBy, etc.                        │
│    - Aggregation: count, sum, avg, max, min             │
└─────────────────────────────────────────────────────────┘
```

### Generated Code Size Estimate

Per entity table with 5 columns:
- Entity implementation: ~200 lines
- Repository: ~50 lines
- Entity sequence: ~100 lines
- **Total: ~350 lines per entity**

For 10 entity tables: ~3,500 lines (vs 5MB+ in current DSL generation!)

---

## Advanced Features (From Exposed DAO Analysis)

Based on comprehensive analysis of Exposed's entity layer tests, here are advanced features to incorporate into Kodama's design:

### 1. Entity Lifecycle Hooks

**Feature:** Subscribe to entity lifecycle events (create, update, delete)

**Exposed API:**
```kotlin
EntityHook.subscribe { event ->
    when (event.changeType) {
        EntityChangeType.Created -> println("Entity created: ${event.entityId}")
        EntityChangeType.Updated -> println("Entity updated: ${event.entityId}")
        EntityChangeType.Removed -> println("Entity deleted: ${event.entityId}")
    }
}

// Scoped hooks
withHook({ println("Change detected") }) {
    val user = User.new { name = "John" }
    user.flush()  // Triggers hook
}
```

**Kodama Design:**
```kotlin
// Subscribe to lifecycle events
session.subscribeToChanges { event ->
    when (event) {
        is EntityCreated -> auditLog.log("Created ${event.entity}")
        is EntityUpdated -> auditLog.log("Updated ${event.entity}")
        is EntityDeleted -> auditLog.log("Deleted ${event.entity}")
    }
}

// Scoped hooks
session.withHook({ println("Entity changed") }) {
    val person = session.create(Persons) { /* ... */ }
    person.name = "New"
    session.flush()  // Triggers hook
}
```

**Use Cases:**
- Audit logging
- Cache invalidation
- Search index updates
- Event sourcing
- Real-time notifications

### 2. Many-to-Many Relationships

**Feature:** Manage many-to-many relationships through join tables

**Exposed API:**
```kotlin
object Users : IntIdTable()
object Cities : IntIdTable()
object UsersToCities : Table() {
    val user = reference("user", Users)
    val city = reference("city", Cities)
}

class User(id: EntityID<Int>) : IntEntity(id) {
    var cities by City via UsersToCities
}

class City(id: EntityID<Int>) : IntEntity(id) {
    var users by User via UsersToCities
}

// Usage
val user = User.new { name = "John" }
val nyc = City.new { name = "NYC" }
val la = City.new { name = "LA" }

user.cities = SizedCollection(listOf(nyc, la))
```

**Kodama Design:**
```kotlin
object Persons : EntityTable<Person>("person") {
    val id = long("id").primaryKey()
    val name = varchar("name", 255)

    // Many-to-many relationship
    val cities = manyToMany(
        targetTable = Cities,
        joinTable = PersonsToCities,
        sourceColumn = PersonsToCities.personId,
        targetColumn = PersonsToCities.cityId
    )
}

object Cities : EntityTable<City>("city") {
    val id = long("id").primaryKey()
    val name = varchar("name", 255)

    val persons = manyToMany(
        targetTable = Persons,
        joinTable = PersonsToCities,
        sourceColumn = PersonsToCities.cityId,
        targetColumn = PersonsToCities.personId
    )
}

object PersonsToCities : Table("persons_to_cities") {
    val personId = long("person_id") references Persons.id
    val cityId = long("city_id") references Cities.id

    override val primaryKey = PrimaryKey(personId, cityId)
}

// Entity interfaces
interface Person : Entity<Person> {
    var id: Long
    var name: String
    val cities: List<City>  // Many-to-many
}

interface City : Entity<City> {
    var id: Long
    var name: String
    val persons: List<Person>  // Many-to-many
}

// Usage
session.withSession { session ->
    val person = session.findById(Persons, 1)!!
    val nyc = session.findById(Cities, 1)!!
    val la = session.findById(Cities, 2)!!

    // Set many-to-many relationship
    person.setCities(listOf(nyc, la))  // Manages join table automatically

    session.flush()  // INSERT INTO persons_to_cities ...
}
```

### 3. Ordered Collections

**Feature:** Relationships with default ordering

**Exposed API:**
```kotlin
object UserRatings : IntIdTable() {
    val value = integer("value")
    val user = reference("user", Users)
}

class User(id: EntityID<Int>) : IntEntity(id) {
    // Ratings ordered by value ascending
    val ratings by UserRating referrersOn UserRatings.user orderBy UserRatings.value
}
```

**Kodama Design:**
```kotlin
object Persons : EntityTable<Person>("person") {
    val id = long("id").primaryKey()

    val orders = oneToMany(
        targetTable = Orders,
        foreignKey = Orders.personId,
        primaryKey = this.id
    ).orderBy(Orders.createdAt.desc())  // Default ordering
}

interface Person : Entity<Person> {
    var id: Long
    val orders: List<Order>  // Always returned in createdAt DESC order
}
```

### 4. Back References

**Feature:** Navigate relationships in both directions

**Exposed API:**
```kotlin
class Order(id: EntityID<Int>) : IntEntity(id) {
    var person by Person referencedOn Orders.personId
}

class Person(id: EntityID<Int>) : IntEntity(id) {
    // Back reference - inverse of Order.person
    val orders by Order referrersOn Orders.personId

    // Optional back reference
    val profile by Profile optionalBackReferencedOn Profiles.personId
}
```

**Kodama Design:**
```kotlin
// Automatic back references
object Persons : EntityTable<Person>("person") {
    val id = long("id").primaryKey()

    // Forward reference automatically creates back reference
    val orders = oneToMany(Orders, Orders.personId, this.id)
}

object Orders : EntityTable<Order>("order") {
    val id = long("id").primaryKey()
    val personId = long("person_id")

    // Back reference automatically inferred
    val person = manyToOne(Persons, this.personId, Persons.id)
}

// Both work automatically
val person = session.findById(Persons, 1)!!
person.orders.forEach { /* ... */ }  // Forward

val order = session.findById(Orders, 1)!!
println(order.person.name)  // Back reference
```

### 5. Different ID Types

**Feature:** Support various primary key types

**Exposed Examples:**
- `IntIdTable` - Int IDs with auto-increment
- `LongIdTable` - Long IDs with auto-increment
- `UUIDTable` - UUID primary keys
- `UIntIdTable`, `ULongIdTable` - Unsigned integers
- `CompositeIdTable` - Composite primary keys
- Custom `IdTable<T>` - Any type

**Kodama Design:**
```kotlin
// Int ID (auto-increment)
object Persons : EntityTable<Person, Int>("person") {
    override val id = integer("id").autoIncrement().primaryKey()
    val name = varchar("name", 255)
}

// UUID ID
object Sessions : EntityTable<Session, UUID>("sessions") {
    override val id = uuid("id").clientDefault { UUID.randomUUID() }.primaryKey()
    val token = varchar("token", 255)
}

// String ID
object Countries : EntityTable<Country, String>("countries") {
    override val id = varchar("code", 2).primaryKey()  // "US", "GB", etc.
    val name = varchar("name", 255)
}

// Composite ID
object UserPermissions : EntityTable<UserPermission, CompositeID>("user_permissions") {
    val userId = long("user_id")
    val resourceId = long("resource_id")

    override val id = compositeId(userId, resourceId)
}

interface UserPermission : Entity<UserPermission, CompositeID> {
    override val id: CompositeID
    var userId: Long
    var resourceId: Long
    var permission: String
}
```

### 6. Entity Refresh & Reload

**Feature:** Reload entity data from database

**Exposed API:**
```kotlin
val user = User.findById(1)!!
user.refresh(flush = true)  // Reload from DB, flush changes first
```

**Kodama Design:**
```kotlin
session.withSession { session ->
    val person = session.findById(Persons, 1)!!
    person.name = "Modified"

    // Reload from database (discards local changes)
    session.refresh(person)

    // Or reload and flush changes first
    session.refresh(person, flush = true)
}
```

### 7. Eager Loading & Preloading

**Feature:** Load related entities eagerly to avoid N+1 queries

**Exposed API:**
```kotlin
// Preload related entities
val users = User.all().with(User::orders)
users.forEach { user ->
    user.orders.forEach { order ->  // No additional query
        println(order.product)
    }
}

// Multiple relationships
User.all().with(User::orders, User::profile)
```

**Kodama Design:**
```kotlin
session.withSession { session ->
    // Eager load orders with persons
    val persons = session.findAll(Persons).with(Persons.orders)

    persons.forEach { person ->
        person.orders.forEach { order ->  // Already loaded, no query
            println(order.product)
        }
    }

    // Multiple relationships
    val persons = session.findAll(Persons)
        .with(Persons.orders, Persons.profile)
}
```

### 8. Field Transformations

**Feature:** Transform values on read/write

**Exposed API:**
```kotlin
class User(id: EntityID<Int>) : IntEntity(id) {
    var email by Users.email.transform(
        toColumn = { it.lowercase() },
        toReal = { it }
    )

    // Chained transformations
    var encrypted by Users.password
        .transform({ encrypt(it) }, { decrypt(it) })
        .memoize()  // Cache transformation result
}
```

**Kodama Design:**
```kotlin
object Persons : EntityTable<Person>("person") {
    val email = varchar("email", 255)
        .transform(
            toDatabase = { it.lowercase() },
            fromDatabase = { it }
        )

    val password = varchar("password", 255)
        .transform(
            toDatabase = { hashPassword(it) },
            fromDatabase = { it }  // Store hashed, return as-is
        )
}
```

### 9. Immutable Entities

**Feature:** Entities that cannot be modified after creation

**Exposed API:**
```kotlin
class AuditLog(id: EntityID<Int>) : IntEntity(id) {
    val timestamp by AuditLogs.timestamp
    val action by AuditLogs.action
    val userId by AuditLogs.userId

    // All properties are val (immutable)
}
```

**Kodama Design:**
```kotlin
interface AuditLog : Entity<AuditLog> {
    val id: Long
    val timestamp: Instant
    val action: String
    val userId: Long

    // No var properties - immutable
}

// Attempting to modify throws compile error
val log = session.findById(AuditLogs, 1)!!
// log.action = "Modified"  // ❌ Compile error: val cannot be reassigned
```

### 10. Cache Management

**Feature:** Control entity caching behavior

**Exposed API:**
```kotlin
// Global cache limit
EntityCache.maxCacheSize = 1000

// Per-transaction cache limit
transaction {
    entityCache.maxCacheSize = 100

    // Clear cache
    entityCache.clear()
}

// Cache invalidation on DSL operations
Users.deleteWhere { Users.age less 18 }  // Invalidates cached users
```

**Kodama Design:**
```kotlin
// Configure session cache
session.withSession { session ->
    session.cacheLimit = 1000  // Max entities to cache

    val person = session.findById(Persons, 1)  // Cached

    // Clear cache
    session.clearCache()

    // Cache statistics
    println("Cached entities: ${session.cacheSize}")
    println("Cache hit rate: ${session.cacheHitRate}")
}
```

### 11. Database-Generated Values

**Feature:** Handle database-generated default values

**Exposed API:**
```kotlin
object Users : IntIdTable() {
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

val user = User.new { name = "John" }
// createdAt and updatedAt automatically set by database
```

**Kodama Design:**
```kotlin
object Persons : EntityTable<Person>("person") {
    val id = long("id").autoIncrement().primaryKey()
    val name = varchar("name", 255)
    val createdAt = timestamp("created_at").default("CURRENT_TIMESTAMP")
    val updatedAt = timestamp("updated_at").default("CURRENT_TIMESTAMP")
        .onUpdate("CURRENT_TIMESTAMP")
}

// Values populated automatically
val person = session.create(Persons) {
    set(Persons.name, "John")
}
session.flush()
// person.createdAt is now set by database
```

### 12. Self-Referencing Entities

**Feature:** Entities that reference themselves

**Exposed API:**
```kotlin
object Nodes : IntIdTable() {
    val name = varchar("name", 50)
    val parent = reference("parent_id", Nodes).nullable()
}

class Node(id: EntityID<Int>) : IntEntity(id) {
    var name by Nodes.name
    var parent by Node optionalReferencedOn Nodes.parent
    val children by Node referrersOn Nodes.parent
}

// Usage
val root = Node.new { name = "Root" }
val child1 = Node.new {
    name = "Child 1"
    parent = root
}
val child2 = Node.new {
    name = "Child 2"
    parent = root
}

root.children.forEach { println(it.name) }  // Child 1, Child 2
```

**Kodama Design:**
```kotlin
object Nodes : EntityTable<Node>("nodes") {
    val id = long("id").primaryKey()
    val name = varchar("name", 255)
    val parentId = long("parent_id").nullable()

    val parent = manyToOne(this, this.parentId, this.id).nullable()
    val children = oneToMany(this, this.parentId, this.id)
}

interface Node : Entity<Node> {
    var id: Long
    var name: String
    var parent: Node?  // Self-reference
    val children: List<Node>  // Self-collection
}
```

### Feature Priority for Implementation

**Phase 4 (Essential):**
- ✅ Many-to-many relationships
- ✅ Back references (automatic)
- ✅ Eager loading
- ✅ Entity refresh

**Phase 5 (Important):**
- ✅ Lifecycle hooks
- ✅ Ordered collections
- ✅ Different ID types (UUID, composite)
- ✅ Cache management

**Phase 6 (Nice to have):**
- ✅ Field transformations
- ✅ Immutable entities
- ✅ Self-referencing entities
- ✅ Database-generated values

---

## Implementation Phases

### Phase 1: Foundation (2 weeks)

**Deliverables:**
- [ ] `Entity<T>` base interface
- [ ] `EntityTable<T>` base class with `bindTo { }` DSL
- [ ] Relationship declaration DSL (`oneToMany`, `manyToOne`, `oneToOne`)
- [ ] `PropertyState<T>` change tracking implementation
- [ ] Basic entity interface → implementation generation (KSP)

**Example:**
```kotlin
// User writes
interface Person : Entity<Person> {
    var id: Long
    var name: String
}

object Persons : EntityTable<Person>("person") {
    val id = long("id").primaryKey().bindTo { it.id }
    val name = varchar("name", 255).bindTo { it.name }
}

// Generated
class PersonEntity : Person { ... }
```

### Phase 2: CRUD Operations (1 week)

**Deliverables:**
- [ ] `save()` method (INSERT or UPDATE based on ID)
- [ ] `delete()` method
- [ ] `findById()`, `findAll()` static methods
- [ ] Repository pattern implementation
- [ ] Change tracking integration

**Example:**
```kotlin
val person = Person { name = "kodama" }
person.save()  // INSERT

person.name = "kodama2"
person.save()  // UPDATE (change tracking)

Persons.findById(1)  // SELECT
```

### Phase 3: Entity Sequence API (2 weeks)

**Deliverables:**
- [ ] `database.sequenceOf(Table)` method
- [ ] `filter`, `map`, `sortedBy`, `take`, `drop` operations
- [ ] `toList()`, `toSet()`, `firstOrNull()` terminals
- [ ] `count`, `sum`, `avg`, `max`, `min` aggregations
- [ ] Lazy evaluation (don't execute until terminal operation)

**Example:**
```kotlin
val adults = database.sequenceOf(Persons)
    .filter { it.age > 18 }
    .sortedBy { it.name }
    .take(10)
    .toList()
```

### Phase 4: Relationships (2 weeks)

**Deliverables:**
- [ ] Lazy loading for relationships
- [ ] `oneToMany` navigation: `person.orders`
- [ ] `manyToOne` navigation: `order.person`
- [ ] `oneToOne` navigation: `person.profile`
- [ ] Eager loading API: `.eager(relation)`
- [ ] N+1 detection (logging/warnings)

**Example:**
```kotlin
val person = Persons.findById(1)!!
person.orders.forEach { order ->  // Lazy loads orders
    println(order.product)
}
```

### Phase 5: Advanced Features (2 weeks)

**Deliverables:**
- [ ] Many-to-many relationships
- [ ] Cascading operations (cascade save, delete)
- [ ] Entity lifecycle callbacks (`@BeforeSave`, `@AfterLoad`)
- [ ] Optimistic locking (version column)
- [ ] Batch operations (save/delete multiple entities)

**Example:**
```kotlin
// Many-to-many
interface Student : Entity<Student> {
    val courses: List<Course>  // Many-to-many through enrollments
}

// Optimistic locking
interface Person : Entity<Person> {
    var version: Long  // Auto-incremented on each update
}
```

### Phase 6: Documentation & Polish (1 week)

**Deliverables:**
- [ ] User guide: "Entity Layer Quick Start"
- [ ] Migration guide: "When to Use DSL vs Entity Layer"
- [ ] API documentation
- [ ] Performance benchmarks
- [ ] Real-world examples

**Total Timeline: 10 weeks**

---

## Trade-offs & Decisions

### Decision 1: Interface vs Abstract Class vs Data Class

**Options:**
1. **Interface** (Ktorm, Requery)
2. **Abstract class** (Requery alternative)
3. **Data class** (Exposed DAO)

**Decision: Interface** ✅

**Rationale:**
- ✅ Most flexible (can add default methods, extensions)
- ✅ Clean user code (minimal boilerplate)
- ✅ Easy to generate implementation
- ✅ Mockable for testing
- ❌ Requires code generation (but we're already doing that)

### Decision 2: Session vs Session-Less

**Options:**
1. **Session-based** (Hibernate, JPA)
2. **Session-less** (Ebean, Ktorm)

**Decision: Session-based** ✅

**Rationale:**
- ✅ **Identity map pattern** - Ensures single instance per entity ID (prevents bugs)
- ✅ **Efficient caching** - Session caches all loaded entities
- ✅ **Centralized change tracking** - Session knows all modifications
- ✅ **Thin entity clients** - Minimal memory footprint (~16 bytes per entity)
- ✅ **Transaction boundary** - Clear lifecycle tied to database transaction
- ✅ **Avoids Hibernate complexity** - No detached entities, no LazyInitializationException
- ✅ **Compile-time generated** - Zero runtime reflection, predictable behavior

**Why not session-less?**
- ❌ **No identity map** - Multiple instances of same entity can exist (inconsistencies)
- ❌ **Distributed change tracking** - Each entity tracks own changes (memory overhead)
- ❌ **No caching** - Every load hits database (unless external cache added)
- ❌ **Manual consistency** - Developer must ensure same entity not loaded twice

### Decision 3: Lazy vs Eager Loading Default

**Options:**
1. **Lazy by default** (Hibernate)
2. **Eager by default** (some libraries)
3. **Explicit always** (JDBI, MyBatis)

**Decision: Lazy by default, explicit eager** ✅

**Rationale:**
- ✅ Avoids N+1 problems by default
- ✅ Explicit eager loading when needed
- ✅ Predictable performance (user controls loading)
- ⚠️ Requires database context (but we track that)

### Decision 4: Repository Pattern vs Active Record

**Options:**
1. **Repository pattern** (Spring Data JPA)
2. **Active Record** (Ebean, Rails)
3. **Both** (Exposed)

**Decision: Both (primary: Entity Sequence, optional: Repository)** ✅

**Rationale:**
- ✅ Entity Sequence is most Kotlin-idiomatic
- ✅ Repository available for teams who prefer it
- ✅ Flexibility like Exposed's dual API

### Decision 5: Code Generation Trigger

**Options:**
1. **KSP** (Kotlin Symbol Processing)
2. **KAPT** (slower)
3. **Runtime reflection** (against Kodama philosophy)

**Decision: KSP** ✅

**Rationale:**
- ✅ Faster than KAPT
- ✅ Kotlin-first
- ✅ Official Kotlin tool
- ✅ Aligns with Kodama's compile-time approach

---

## Examples

### Example 1: Blog Application

```kotlin
// Entities
interface User : Entity<User> {
    var id: Long
    var username: String
    var email: String
    val posts: List<Post>
    val comments: List<Comment>
}

interface Post : Entity<Post> {
    var id: Long
    var userId: Long
    var title: String
    var content: String
    var createdAt: Instant

    val author: User
    val comments: List<Comment>
}

interface Comment : Entity<Comment> {
    var id: Long
    var postId: Long
    var userId: Long
    var text: String
    var createdAt: Instant

    val post: Post
    val author: User
}

// Tables
object Users : EntityTable<User>("users") {
    val id = long("id").primaryKey().bindTo { it.id }
    val username = varchar("username", 255).bindTo { it.username }
    val email = varchar("email", 255).bindTo { it.email }

    val posts = oneToMany(Posts, Posts.userId, this.id)
    val comments = oneToMany(Comments, Comments.userId, this.id)
}

object Posts : EntityTable<Post>("posts") {
    val id = long("id").primaryKey().bindTo { it.id }
    val userId = long("user_id").bindTo { it.userId }
    val title = varchar("title", 255).bindTo { it.title }
    val content = text("content").bindTo { it.content }
    val createdAt = timestamp("created_at").bindTo { it.createdAt }

    val author = manyToOne(Users, this.userId, Users.id)
    val comments = oneToMany(Comments, Comments.postId, this.id)
}

object Comments : EntityTable<Comment>("comments") {
    val id = long("id").primaryKey().bindTo { it.id }
    val postId = long("post_id").bindTo { it.postId }
    val userId = long("user_id").bindTo { it.userId }
    val text = text("text").bindTo { it.text }
    val createdAt = timestamp("created_at").bindTo { it.createdAt }

    val post = manyToOne(Posts, this.postId, Posts.id)
    val author = manyToOne(Users, this.userId, Users.id)
}

// Usage
fun main() = withDatabase { database ->
    // Create user
    val user = User {
        username = "kodama"
        email = "kodama@example.com"
    }
    user.save()

    // Create post
    val post = Post {
        userId = user.id
        title = "First Post"
        content = "Hello, world!"
        createdAt = Clock.System.now()
    }
    post.save()

    // Add comment
    val comment = Comment {
        postId = post.id
        userId = user.id
        text = "Great post!"
        createdAt = Clock.System.now()
    }
    comment.save()

    // Query: Find all posts by user with comments
    val userWithPosts = Users.findById(user.id)!!
    userWithPosts.posts.forEach { post ->
        println("Post: ${post.title}")
        post.comments.forEach { comment ->
            println("  Comment by ${comment.author.username}: ${comment.text}")
        }
    }

    // Entity Sequence API
    val recentPosts = database.sequenceOf(Posts)
        .filter { it.createdAt > Clock.System.now().minus(7.days) }
        .sortedByDescending { it.createdAt }
        .take(10)
        .toList()

    // Complex query with DSL
    val postsWithCommentCount = from(Post)
        .leftJoin(Comment) { comment.postId eq post.id }
        .select { post.id }
        .select { post.title }
        .select { count(comment.id) alias "commentCount" }
        .groupBy { post.id }
        .having { count(comment.id) greater 5 }
        .execute(transaction)
}
```

### Example 2: E-Commerce

```kotlin
// Entities
interface Customer : Entity<Customer> {
    var id: Long
    var name: String
    var email: String
    val orders: List<Order>
}

interface Order : Entity<Order> {
    var id: Long
    var customerId: Long
    var status: OrderStatus
    var totalAmount: BigDecimal
    var createdAt: Instant

    val customer: Customer
    val items: List<OrderItem>
}

interface Product : Entity<Product> {
    var id: Long
    var name: String
    var price: BigDecimal
    var stock: Int
}

interface OrderItem : Entity<OrderItem> {
    var id: Long
    var orderId: Long
    var productId: Long
    var quantity: Int
    var unitPrice: BigDecimal

    val order: Order
    val product: Product
}

enum class OrderStatus {
    PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
}

// Usage
fun placeOrder(customerId: Long, items: List<Pair<Long, Int>>) = withDatabase { database ->
    val customer = Customers.findById(customerId) ?: error("Customer not found")

    // Create order
    val order = Order {
        this.customerId = customer.id
        status = OrderStatus.PENDING
        totalAmount = BigDecimal.ZERO
        createdAt = Clock.System.now()
    }
    order.save()

    // Add order items
    var total = BigDecimal.ZERO
    items.forEach { (productId, quantity) ->
        val product = Products.findById(productId) ?: error("Product not found")

        // Check stock
        if (product.stock < quantity) error("Insufficient stock")

        // Create order item
        val orderItem = OrderItem {
            this.orderId = order.id
            this.productId = product.id
            this.quantity = quantity
            this.unitPrice = product.price
        }
        orderItem.save()

        // Update stock
        product.stock -= quantity
        product.save()

        total += product.price * quantity.toBigDecimal()
    }

    // Update order total
    order.totalAmount = total
    order.save()

    return@withDatabase order
}

// Query: Find top customers by order value
fun findTopCustomers(limit: Int) = withDatabase { database ->
    from(Customer)
        .leftJoin(Order) { order.customerId eq customer.id }
        .select { customer.id }
        .select { customer.name }
        .select { sum(order.totalAmount) alias "totalSpent" }
        .groupBy { customer.id }
        .orderBy { sum(order.totalAmount).desc() }
        .limit(limit)
        .execute(transaction)
}
```

---

## Comparison: Entity Layer vs DSL

### When to Use Entity Layer

✅ **Simple CRUD operations**
```kotlin
val person = Persons.findById(1)!!
person.name = "New Name"
person.save()
```

✅ **Relationship navigation**
```kotlin
person.orders.forEach { order ->
    println(order.product)
}
```

✅ **Collection-like queries**
```kotlin
database.sequenceOf(Persons)
    .filter { it.age > 18 }
    .sortedBy { it.name }
    .toList()
```

✅ **Change tracking needed**
```kotlin
person.age = 26  // Tracked
person.save()    // Only updates changed columns
```

### When to Use DSL

✅ **Complex joins**
```kotlin
from(Person)
    .join(Order) { order.personId eq person.id }
    .join(Product) { product.id eq order.productId }
    .where { product.category eq "Electronics" }
    .selectAll(Person)
    .selectAll(Order)
    .selectAll(Product)
```

✅ **Aggregations and grouping**
```kotlin
from(Order)
    .select { order.personId }
    .select { sum(order.cost) alias "total" }
    .groupBy { order.personId }
    .having { sum(order.cost) greater 1000 }
```

✅ **Subqueries**
```kotlin
from(Person)
    .where {
        person.id inSubquery {
            from(Order)
                .select { order.personId }
                .where { order.cost greater 1000 }
        }
    }
```

✅ **SQL control needed**
```kotlin
// When you need exact SQL for performance
from(Person)
    .select { person.name }
    .where { person.age eq 25 }
// Generates exact SQL you specify
```

---

## Open Questions

1. **Caching Strategy?**
   - Should we add first-level caching (identity map)?
   - Second-level caching (Caffeine, Redis)?
   - Or keep it simple: no caching (user can add external cache)?

2. **Transaction Management?**
   - Current DSL uses `transaction { }` blocks
   - Should entities auto-join current transaction?
   - Or require explicit transaction context?

3. **Batch Operations?**
   - Should we support `Persons.saveAll(listOfPersons)`?
   - How to optimize: single transaction, JDBC batch, COPY command?

4. **Validation?**
   - Should entities support validation (`@NotNull`, `@Min`, etc.)?
   - Or rely on database constraints?
   - Or leave to external validation library (Konform)?

5. **Migrations?**
   - Should entity definitions drive schema migrations?
   - Or keep schema separate (Flyway, Liquibase)?

6. **Testing Support?**
   - Should we provide test utilities (in-memory database, fixtures)?
   - Or rely on Testcontainers?

---

## Success Metrics

### Phase 1-2 (Foundation + CRUD)
- ✅ All tests passing
- ✅ Entity creation, save, update, delete working
- ✅ Change tracking functional
- ✅ Generated code compiles
- ✅ Documentation complete

### Phase 3 (Entity Sequence)
- ✅ All collection operations working (filter, map, sort)
- ✅ Lazy evaluation proven
- ✅ Performance: within 10% of raw DSL
- ✅ API feels Kotlin-native

### Phase 4 (Relationships)
- ✅ Lazy loading working
- ✅ Eager loading working
- ✅ No N+1 queries by default
- ✅ Relationship navigation type-safe

### Phase 5-6 (Advanced + Polish)
- ✅ All advanced features working
- ✅ Performance benchmarks published
- ✅ Real-world examples validated
- ✅ User feedback incorporated

---

## Conclusion

This design proposes a **dual-API entity layer** for Kodama that:

1. ✅ **Maintains compile-time type safety** - Zero reflection, all KSP-generated
2. ✅ **Simplifies common operations** - CRUD in 1-2 lines
3. ✅ **Provides relationship navigation** - Lazy-loaded, type-safe
4. ✅ **Offers Kotlin-idiomatic API** - Entity Sequence feels like Kotlin collections
5. ✅ **Preserves DSL for complex queries** - Best of both worlds
6. ✅ **Learns from 10+ years of ORM evolution** - Proven patterns, known pitfalls avoided

**Next Steps:**
1. Review and approve this design
2. Start Phase 1 implementation (Foundation)
3. Iterate based on feedback

**Estimated Timeline:** 10 weeks to full implementation

---

## References

- [ORM Entity Layer Research](./orm-entity-layer-research.md) - Comprehensive analysis of 10 libraries
- [Kodama 2.0 Plan](../../.claude/plans/vivid-meandering-cake.md) - Explicit relationship system
- [Current DSL API](../../kodama-core/src/main/kotlin/com/obabichev/kodama/query/) - Existing query building
- Exposed: https://github.com/JetBrains/Exposed
- Ktorm: https://www.ktorm.org/
- Requery: https://github.com/requery/requery
- Ebean: https://ebean.io/
