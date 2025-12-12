# Column Access API

## The Problem

When selecting specific columns (not `.all()`), the old nullable accessor approach had issues:

```kotlin
.select {
    +person.name
    +order.product
}

// Problem 1: Unwanted nullability
val name: String? = row.person?.name  // String? instead of String

// Problem 2: Accessing unselected columns compiles
val cost = row.order?.cost  // Compiles but cost wasn't selected!
```

## The Solution: Two Access Patterns

Kodama now provides two ways to access result data, depending on what you selected:

### 1. Table Accessor (for `.all()` selections)

When you select **all columns** from a table using `.all()`:

```kotlin
query()
    .from(Person)
    .select {
        +person.all()  // ✅ Select all columns
    }
    .execute()

// Access via table accessor (non-nullable table, but requires !!)
val person = row.person!!  // PersonResultAccessor (non-null)
val name: String = person.name  // Direct property access
val age: Int = person.age
```

**Why `!!`?** The accessor is nullable at compile time because the type system doesn't know if `.all()` was called. But when you know you called `.all()`, `!!` is safe and documents your intent.

### 2. Bracket Operator (for specific column selections)

When you select **specific columns** (not `.all()`):

```kotlin
query()
    .from(Person)
    .select {
        +person.name  // ✅ Select specific columns
        +person.age
    }
    .execute()

// Access via bracket operator (type-safe, non-nullable)
val name: String = row[Person.name]  // Non-nullable!
val age: Int = row[Person.age]      // Non-nullable!

// Accessing unselected column gives clear error
row[Person.id]  // ❌ Runtime error: "Column id was not selected"
```

## Mixed Access

You can mix both approaches in the same query:

```kotlin
query()
    .from(Person)
    .join(Order) { order.userName eq person.name }
    .select {
        +person.all()      // Full table for Person
        +order.product     // Specific column for Order
    }
    .execute()

// Person: Use table accessor
val personName = row.person!!.name
val personAge = row.person!!.age

// Order: Use bracket operator
val product: String = row[Order.product]

// Order table accessor is null (because .all() wasn't used)
assert(row.order == null)
```

## Comparison Table

| Scenario | Access Method | Nullability | Example |
|----------|--------------|-------------|---------|
| Selected `.all()` | Table accessor | Non-null properties | `row.person!!.name` |
| Selected `.all()` | Bracket operator | Non-null values | `row[Person.name]` |
| Specific columns | Table accessor | `null` | `row.person == null` |
| Specific columns | Bracket operator | Non-null values | `row[Person.name]` |
| Unselected column | Bracket operator | Runtime error | Clear error message |

## Best Practices

### When to use Table Accessors

✅ Use when you selected `.all()` for the table:
```kotlin
.select { +person.all() }
row.person!!.name  // Clear and direct
```

### When to use Bracket Operator

✅ Use when you selected specific columns:
```kotlin
.select {
    +person.name
    +person.age
}
val name: String = row[Person.name]  // No extra nullability!
```

✅ Use when you need guaranteed type safety:
```kotlin
// This will fail fast if column wasn't selected
val product = row[Order.product]
```

## Why This Design?

1. **No unwanted nullability**: `row[Person.name]` returns `String`, not `String?`
2. **Clear runtime errors**: Accessing unselected columns fails with helpful messages
3. **Flexible**: Mix `.all()` and specific columns in the same query
4. **Type-safe**: Column types are preserved (`Int`, `String`, etc.)
5. **Works with Kotlin's type system**: No complex generics or phantom types needed

## Migration Guide

If you were using nullable accessors:

```kotlin
// Before
val name = row.person?.name  // String?

// After - Option 1: Use .all()
.select { +person.all() }
val name = row.person!!.name  // String

// After - Option 2: Use bracket operator
.select { +person.name }
val name = row[Person.name]  // String
```

## Future: Nullable Columns

When nullable columns are added to the schema, the bracket operator will preserve nullability:

```kotlin
object Person : Table("person") {
    val name = varchar("name", 255)  // NOT NULL
    val email = varchar("email", 255).nullable()  // NULL
}

val name: String = row[Person.name]    // Non-null
val email: String? = row[Person.email]  // Nullable (from schema)
```

This is future work when `.nullable()` is implemented.
