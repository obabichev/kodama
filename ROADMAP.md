# Kodama Query Builder - Feature Roadmap

## Overview
This roadmap outlines planned features to support the full range of SQL queries for PostgreSQL. Features are prioritized based on usage patterns and user needs.

---

## Completed Features ✅

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

## Top Priority Features

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

## Secondary Priority Features

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

## Additional Features (Lower Priority)

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

**🚧 Phase 4: Essential Query Features (IN PROGRESS)**
10. LIMIT and OFFSET (pagination)
11. HAVING clause for aggregate filtering
12. AND/OR boolean combinations in WHERE
13. More comparison operators (gt, lt, gte, lte, neq, isNull, isNotNull)

**📋 Phase 5: Advanced Queries (PLANNED)**
14. IN operator and subqueries
15. LEFT/RIGHT/FULL OUTER JOIN
16. DISTINCT
17. Additional string operators (LIKE, ILIKE)

**📋 Phase 6: Data Manipulation - Part 2 (PLANNED)**
18. UPDATE statements
19. DELETE statements
20. RETURNING clause support

**📋 Phase 7: Advanced Features (FUTURE)**
21. Window functions
22. CTEs (WITH clause)
23. CASE expressions
24. PostgreSQL-specific features (JSON, arrays)

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
