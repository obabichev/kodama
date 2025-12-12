# Kodama Query Builder - Feature Roadmap

## Overview
This roadmap outlines planned features to support the full range of SQL queries for PostgreSQL. Features are prioritized based on usage patterns and user needs.

---

## Completed Features ✅

### Nullable Column Support
**Status:** ✅ Implemented (v0.1.0)

Kodama now supports nullable columns with full type safety:

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

---

## Top Priority Features

### 1. ORDER BY clause ⭐ Most Common
Almost every query needs sorting. Essential for pagination and user-facing lists.

**Status:** Not implemented

**Example Usage:**
```kotlin
query()
    .from(Person)
    .select { +person.all() }
    .orderBy { person.age.desc() }

// Multiple columns
query()
    .from(Person)
    .select { +person.all() }
    .orderBy {
        person.age.desc()
        person.name.asc()
    }
```

**Implementation Tasks:**
- Add `orderBy` method to query builders
- Support `.asc()` and `.desc()` column modifiers
- Update Query class to track ORDER BY clause
- Update SQL generation to append ORDER BY
- Add tests for single and multiple column ordering

---

### 2. LIMIT and OFFSET (Pagination) ⭐ Critical
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

### 3. Aggregate Functions (COUNT, SUM, AVG, MIN, MAX) ⭐ Very Common
Very common for analytics, dashboards, and reporting.

**Status:** Not implemented

**Example Usage:**
```kotlin
query()
    .from(Order)
    .select {
        +count(order.id)
        +sum(order.cost)
        +avg(order.cost)
        +min(order.cost)
        +max(order.cost)
    }

// With alias
query()
    .from(Order)
    .select {
        +count(order.id).alias("total_orders")
        +sum(order.cost).alias("total_revenue")
    }
```

**Implementation Tasks:**
- Create aggregate function DSL: `count()`, `sum()`, `avg()`, `min()`, `max()`
- Support column expressions in aggregates
- Handle result type mapping for aggregates
- Support optional aliases
- Update SQL generation
- Add tests for all aggregate functions

---

### 4. GROUP BY and HAVING
Required whenever using aggregates with grouping.

**Status:** Not implemented

**Example Usage:**
```kotlin
query()
    .from(Order)
    .select {
        +order.userName
        +count(order.id).alias("order_count")
    }
    .groupBy { order.userName }
    .having { count(order.id) gt 5 }

// Multiple columns
query()
    .from(Order)
    .select {
        +order.userName
        +order.product
        +sum(order.cost)
    }
    .groupBy {
        order.userName
        order.product
    }
```

**Implementation Tasks:**
- Add `groupBy` method to query builders
- Add `having` method to query builders
- Support multiple GROUP BY columns
- Support aggregate functions in HAVING clause
- Update SQL generation
- Add tests for grouping and filtering

---

## Secondary Priority Features

### 5. LEFT/RIGHT/FULL OUTER JOIN
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

### 6. DISTINCT
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

### 7. IN operator and subqueries
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

### 8. More comparison operators
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

### 9. INSERT, UPDATE, DELETE statements
Currently you only have SELECT. Most ORMs need full CRUD.

**Status:** Not implemented

**Example Usage:**
```kotlin
// INSERT
insert(Person)
    .values {
        person.name = "new_user"
        person.age = 25
    }
    .execute(transaction)

// INSERT with returning
insert(Person)
    .values {
        person.name = "new_user"
        person.age = 25
    }
    .returning { +person.name }
    .execute(transaction)

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
- Create INSERT builder with type-safe value assignment
- Create UPDATE builder with type-safe set operations
- Create DELETE builder
- Support RETURNING clause (PostgreSQL specific)
- Handle transaction management
- Add comprehensive CRUD tests

---

### 10. AND/OR boolean combinations in WHERE
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

**Phase 1: Essential Query Features (Weeks 1-2)**
1. ORDER BY clause
2. LIMIT and OFFSET
3. More comparison operators (gt, lt, gte, lte, neq, isNull, isNotNull)
4. AND/OR boolean combinations

**Phase 2: Analytics Support (Weeks 3-4)**
5. Aggregate functions (COUNT, SUM, AVG, MIN, MAX)
6. GROUP BY and HAVING
7. DISTINCT

**Phase 3: Advanced Queries (Weeks 5-6)**
8. IN operator and subqueries
9. LEFT/RIGHT/FULL OUTER JOIN
10. Additional string operators (LIKE, ILIKE)

**Phase 4: Write Operations (Weeks 7-8)**
11. INSERT statements
12. UPDATE statements
13. DELETE statements
14. RETURNING clause support

**Phase 5: Advanced Features (Weeks 9+)**
15. Window functions
16. CTEs
17. CASE expressions
18. PostgreSQL-specific features (JSON, arrays)

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
