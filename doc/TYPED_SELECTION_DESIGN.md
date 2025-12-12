# Design: Type-Safe Column Selection with Chained select()

## The Better API

Require each `select()` call to return exactly ONE thing:

```kotlin
query()
    .from(Person)
    .join(Order) { order.userName eq person.name }
    .select { person.all() }     // Select 1: Person (all columns)
    .select { order.product }    // Select 2: Order.product (specific column)
    .where { person.name eq "kodama" }
    .execute()
```

## Why This Is Better

### Problem with nullable accessors:
- `row.person?: PersonResultAccessor?` - confuses selection with DB nullability
- When DB columns become nullable: `String??` - double nullability!

### Solution: Type-level tracking
Each `select()` returns a different type encoding selections:

```kotlin
row.person.name  // ✅ PersonResultAccessor (non-nullable!)
row.person.email // String? (only if DB column is nullable)
```

## Implementation: Each select() Returns a Selection Marker

```kotlin
// Selection markers
sealed interface Selection
data class TableAllSelection(val table: Table) : Selection
data class ColumnSelection(val column: Column<*>) : Selection

// Updated API
.select { person.all() }    // Returns TableAllSelection
.select { order.product }   // Returns ColumnSelection
```

This allows the type system to track what was selected!
