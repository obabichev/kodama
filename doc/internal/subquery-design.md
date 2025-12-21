# Subquery Support Design Document

## Overview

This document outlines the design for comprehensive subquery support in Kodama, maintaining 100% type safety and
compile-time verification.

## Current State

**Implemented (v0.5.0):**

- ✅ Unaliased subqueries in WHERE clause with IN/NOT IN operators
- ✅ Basic subquery construction with `from(...).selectAll(...).build()`

**Not Implemented:**

- ❌ Aliased subqueries (derived tables)
- ❌ Subqueries in FROM clause
- ❌ Subqueries in JOIN clause
- ❌ Scalar subqueries in SELECT clause
- ❌ Correlated subqueries
- ❌ EXISTS/NOT EXISTS operators
- ❌ Subqueries with comparison operators (>, <, =, etc.)

---

## Use Cases: Where Can Subqueries Be Used?

### 1. **WHERE Clause - Comparison with Scalar Subquery**

**SQL Example:**

```sql
SELECT *
FROM person
WHERE age > (SELECT AVG(age) FROM person)
```

**Proposed Kodama API:**

```kotlin
from(Person)
    .selectAll(Person)
    .where {
        person.age gt subquery {
            from(Person)
                .select_avgAge { avg(person.age) }
                .build()
        }
    }
```

**Type Safety:**

- Subquery must return exactly one column
- Subquery must return exactly one row (scalar)
- Column type must be compatible with comparison

---

### 2. **WHERE Clause - EXISTS/NOT EXISTS**

**SQL Example:**

```sql
SELECT *
FROM person p
WHERE EXISTS (SELECT 1
              FROM "order" o
              WHERE o.user_name = p.name
                AND o.cost > 1000)
```

**Proposed Kodama API:**

```kotlin
from(Person)
    .selectAll(Person)
    .where {
        exists {
            from(Order)
                .select { order.id }  // Column doesn't matter for EXISTS
                .where {
                    (order.userName eq person.name) and (order.cost gt 1000)
                }
                .build()
        }
    }
```

**Type Safety:**

- EXISTS doesn't care about columns returned
- Allows correlation with outer query
- Returns boolean expression

---

### 3. **WHERE Clause - IN/NOT IN with Subquery** ✅ DONE

**SQL Example:**

```sql
SELECT *
FROM person
WHERE name IN (SELECT user_name FROM "order" WHERE cost > 1000)
```

**Current Kodama API:**

```kotlin
val subquery = from(Order)
    .selectAll(Order)  // Currently requires selectAll
    .where { order.cost gt 1000 }
    .build()

from(Person)
    .selectAll(Person)
    .where {
        person.name.inQuery(subquery)
    }
```

**Improvement Needed:**

- Support single column selection in subquery
- Type safety: ensure subquery returns single column compatible with IN

---

### 4. **FROM Clause - Derived Table (Aliased Subquery)**

**SQL Example:**

```sql
SELECT expensive_orders.user_name,
       expensive_orders.total_cost,
       person.age
FROM (SELECT user_name, SUM(cost) as total_cost
      FROM "order"
      WHERE cost > 100
      GROUP BY user_name) AS expensive_orders
         JOIN person ON person.name = expensive_orders.user_name
```

**Proposed Kodama API:**

```kotlin
// Define derived table using method name pattern (like select_name)
val expensiveOrders = subquery_ExpensiveOrders {
    from(Order)
        .select { order.userName }
        .select_totalCost { sum(order.cost) }
        .where { order.cost gt 100 }
        .build()
}

// Use derived table in query
from(expensiveOrders)  // Use subquery as table source
    .join(Person) { person.name eq expensiveOrders.userName }
    .select { expensiveOrders.userName }
    .select { expensiveOrders.totalCost }
    .select { person.age }
```

**Naming Convention:**
- Use `subquery_PascalCaseName` pattern (consistent with `select_aggregateName`)
- PascalCase after underscore → snake_case SQL alias: `ExpensiveOrders` → `expensive_orders`
- Variable name typically camelCase: `val expensiveOrders = subquery_ExpensiveOrders { ... }`

**Type Safety:**

- Code generation creates typed accessor for subquery columns
- Method name `subquery_ExpensiveOrders` generates type `SubqueryTable_ExpensiveOrders`
- `expensiveOrders.userName` and `expensiveOrders.totalCost` are compile-time safe
- Can only access columns selected in subquery
- Can use in JOIN conditions
- Refactoring-friendly: IDEs can track method usages
- No string literals to maintain

---

### 5. **JOIN Clause - Join with Derived Table**

**SQL Example:**

```sql
SELECT person.name,
       recent_orders.order_count
FROM person
         LEFT JOIN (SELECT user_name, COUNT(*) as order_count
                    FROM "order"
                    WHERE created_at > '2024-01-01'
                    GROUP BY user_name) AS recent_orders ON person.name = recent_orders.user_name
```

**Proposed Kodama API:**

```kotlin
val recentOrders = subquery_RecentOrders {
    from(Order)
        .select { order.userName }
        .select_orderCount { count(order.id) }
        .where { order.createdAt gt LocalDate.of(2024, 1, 1) }
        .build()
}

from(Person)
    .leftJoin(recentOrders) { person.name eq recentOrders.userName }
    .select { person.name }
    .select { recentOrders.orderCount }
```

---

### 6. **SELECT Clause - Scalar Subquery**

**SQL Example:**

```sql
SELECT person.name,
       person.age,
       (SELECT COUNT(*) FROM "order" WHERE user_name = person.name) AS order_count
FROM person
```

**Proposed Kodama API:**

```kotlin
from(Person)
    .select { person.name }
    .select { person.age }
    .select_orderCount {
        scalarSubquery {
            from(Order)
                .select_count { count(order.id) }
                .where { order.userName eq person.name }  // Correlated!
                .build()
        }
    }
```

**Type Safety:**

- Subquery must return exactly one column
- Subquery must return exactly one row
- Result type matches the selected column type

---

### 7. **HAVING Clause - Subquery for Filtering Aggregates**

**SQL Example:**

```sql
SELECT user_name, SUM(cost) as total
FROM "order"
GROUP BY user_name
HAVING SUM(cost) > (SELECT AVG(cost) FROM "order")
```

**Proposed Kodama API:**

```kotlin
from(Order)
    .select { order.userName }
    .select_total { sum(order.cost) }
    .having {
        sum(order.cost) gt subquery {
            from(Order)
                .select_avgCost { avg(order.cost) }
                .build()
        }
    }
```

---

## Implementation Design

### Phase 1: Foundation - Subquery Metadata and Type System

#### 1.1 Subquery Result Type

```kotlin
/**
 * Represents a subquery with known column schema
 */
class SubqueryTable(
    val alias: String,
    val query: Query,
    val columns: List<SubqueryColumn<*>>
) : Table(alias) {
    // Inherits from Table so it can be used in FROM/JOIN
}

/**
 * Column from a subquery result
 */
class SubqueryColumn<T>(
    name: String,
    type: ColumnType<T>,
    table: SubqueryTable
) : Column<T>(name, type, table.relation)
```

#### 1.2 Generated Methods for Creating Aliased Subqueries

**Pattern:** Generator creates `subquery_Name` methods for each subquery usage found in code.

```kotlin
/**
 * Generated method for creating typed subquery
 * Pattern: subquery_PascalCaseName { query builder }
 * SQL Alias: pascal_case_name (converted to snake_case)
 */
// Example generated code:
fun subquery_ExpensiveOrders(
    builder: () -> Query
): SubqueryTable_ExpensiveOrders {
    val query = builder()

    // Validate query structure
    val columns = extractColumnsFromQuery(query)

    return SubqueryTable_ExpensiveOrders("expensive_orders", query, columns)
}

/**
 * Generated typed subquery table class
 */
class SubqueryTable_ExpensiveOrders(
    alias: String,
    query: Query,
    columns: List<SubqueryColumn<*>>
) : SubqueryTable(alias, query, columns) {
    // Generated column accessors based on query's selected columns
    val userName: TypedColumn<String, SubqueryTable_ExpensiveOrders, UserNameColumnMarker>
    val totalCost: TypedColumn<Long?, SubqueryTable_ExpensiveOrders, TotalCostColumnMarker>
}

private fun extractColumnsFromQuery(query: Query): List<SubqueryColumn<*>> {
    // Parse query.selectables to determine:
    // - Column names (from ColumnSelection or named aggregates)
    // - Column types (from underlying Column.type)
    // - Create SubqueryColumn for each
}
```

**Naming Conversion:**
- Method name: `subquery_ExpensiveOrders` (PascalCase)
- SQL alias: `expensive_orders` (snake_case)
- Variable (convention): `val expensiveOrders = subquery_ExpensiveOrders { ... }` (camelCase)

**Benefits of Method Name Pattern:**

1. **Consistency**: Matches `select_aggregateName` pattern already used in Kodama
2. **Type Safety**: Method name becomes part of the type system
3. **Refactoring-Friendly**: IDEs can track method usages, rename refactoring works
4. **No String Literals**: Reduces chance of typos, no runtime string validation needed
5. **Auto-Complete**: IDEs can suggest available subquery methods
6. **Discoverability**: Easy to find all subquery definitions with IDE search
7. **Compile-Time Validation**: Invalid subquery names caught at compile time

**Example IDE Benefits:**
```kotlin
// Auto-complete suggests: subquery_ExpensiveOrders, subquery_RecentOrders, etc.
val sq = subquery_   // <-- IDE shows all available subqueries

// Refactoring: Rename ExpensiveOrders → HighValueOrders
// IDE renames: method, type, all usages automatically
```

### Phase 2: Subqueries in FROM Clause

#### 2.1 Allow SubqueryTable in FROM

```kotlin
// Current: only accepts Table
fun from(table: Table): AfterFromQueryBuilder<...>

// Enhanced: accepts Table or SubqueryTable
fun from(table: Table): AfterFromQueryBuilder<...>
fun from(subquery: SubqueryTable): AfterFromQueryBuilder<...>
```

#### 2.2 SQL Generation

```kotlin
// Query.sql() enhancement
fun sql(): String {
    val fromClause = when (from) {
        is Relation -> "\"${from.name}\""
        is SubqueryRelation -> {
            val subquerySql = from.query.sql()
            "($subquerySql) AS ${from.alias}"
        }
    }
    // ...
}
```

### Phase 3: Subqueries in JOIN Clause

```kotlin
// AfterFromQueryBuilder enhancement
fun join(subquery: SubqueryTable, condition: JoinContext.() -> Pair<Column<*>, Column<*>>)

// Code generation produces:
fun AfterFromQueryBuilder_Person.join(
    subquery: SubqueryTable,
    condition: JoinContext_Person_Subquery.() -> ...
): AfterJoinQueryBuilder_Person_Subquery
```

### Phase 4: Scalar Subqueries

#### 4.1 Scalar Subquery Expression

```kotlin
/**
 * Scalar subquery - returns single value
 */
class ScalarSubqueryExpression(
    val subquery: Query,
    val resultType: ColumnType<*>
) : Expression {
    override fun toSql(): String {
        return "(${subquery.sql()})"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return subquery.arguments()
    }
}
```

#### 4.2 Comparison Operators with Subqueries

```kotlin
// Add to Operators.kt
infix fun <T> Column<T>.gt(subquery: Query): Expression {
    // Validate: subquery must select exactly one column
    // Validate: subquery column type must match
    return BinaryOperand(">", ColumnExpression(this), ScalarSubqueryExpression(subquery))
}

// Similar for: lt, gte, lte, eq, neq
```

#### 4.3 Scalar Subqueries in SELECT

```kotlin
// SelectContext enhancement
fun scalarSubquery(builder: () -> Query): ScalarSubqueryExpression {
    val query = builder()
    // Validate: query must select exactly one column
    // Extract result type from query
    return ScalarSubqueryExpression(query, resultType)
}
```

### Phase 5: EXISTS/NOT EXISTS

```kotlin
/**
 * EXISTS expression
 */
class ExistsExpression(val subquery: Query) : Expression {
    override fun toSql(): String {
        return "EXISTS (${subquery.sql()})"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return subquery.arguments()
    }
}

/**
 * NOT EXISTS expression
 */
class NotExistsExpression(val subquery: Query) : Expression {
    override fun toSql(): String {
        return "NOT EXISTS (${subquery.sql()})"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return subquery.arguments()
    }
}

// In WhereContext
fun exists(builder: () -> Query): Expression {
    return ExistsExpression(builder())
}

fun notExists(builder: () -> Query): Expression {
    return NotExistsExpression(builder())
}
```

### Phase 6: Correlated Subqueries

**Challenge:** Subquery needs access to outer query columns.

**Solution:** Pass outer context to subquery builder.

```kotlin
// For correlated subqueries, context provides access to outer tables
class CorrelatedSubqueryContext(val outerContext: WhereContext) {
    // Can reference outer query columns
}

fun WhereContext.correlatedSubquery(
    builder: CorrelatedSubqueryContext.() -> Query
): Query {
    val context = CorrelatedSubqueryContext(this)
    return context.builder()
}

// Usage:
where {
    exists(correlatedSubquery {
        from(Order)
            .select { order.id }
            .where {
                // Can access both Order (inner) and Person (outer)
                order.userName eq person.name
            }
            .build()
    })
}
```

---

## Code Generation Requirements

### For Aliased Subqueries

When a subquery is used in FROM/JOIN, we need to generate:

1. **Typed accessor for subquery table**

```kotlin
// Generated accessor
val expensive_orders: SubqueryTableAccessor
```

2. **Column accessors for subquery columns**

```kotlin
// Generated accessors for each selected column
expensiveOrders.userName  // TypedColumn<String, SubqueryTable, ...>
expensiveOrders.totalCost // TypedColumn<Long?, SubqueryTable, ...>  // Aggregates are nullable for LEFT JOIN safety
```

3. **Join contexts that include subquery**

```kotlin
// Generated context for joins
class JoinContext_Person_ExpensiveOrders(
    val person: PersonTableAccessor,
    val expensiveOrders: ExpensiveOrdersSubqueryAccessor
)
```

4. **Result accessors**

```kotlin
// Generated result accessor
class QueryResult_Person_ExpensiveOrders {
    val person: PersonResultAccessor
    val expensiveOrders: ExpensiveOrdersResultAccessor
}
```

### Generator Enhancements Needed

1. **Detect subquery usage in test files**
    - Scan for `subquery_PascalCaseName { ... }` patterns
    - Extract name from method call (e.g., `ExpensiveOrders` from `subquery_ExpensiveOrders`)
    - Parse the query builder to determine selected columns
    - Example regex: `subquery_([A-Z][a-zA-Z0-9]*)\s*\{`

2. **Track subquery metadata**
    - Map subquery name → selected columns (from query's select/selectAll calls)
    - Map subquery name → column types (from underlying Column types)
    - Map subquery name → SQL alias (convert PascalCase → snake_case)

3. **Generate subquery-specific code**
    - `subquery_Name()` method that returns typed SubqueryTable
    - `SubqueryTable_Name` class with column accessors
    - Column accessor properties for each selected column
    - Join contexts that include subquery tables
    - Result accessors for queries using subqueries

4. **Naming conventions**
    - Input: `subquery_ExpensiveOrders` → Output: class `SubqueryTable_ExpensiveOrders`
    - SQL alias: `ExpensiveOrders` → `expensive_orders`
    - Column accessors use camelCase: `expensiveOrders.userName`

---

## Type Safety Guarantees

### 1. Column Existence

✅ Can only reference columns that were selected in subquery

```kotlin
val sq = subquery("sq") {
    from(Order).select { order.userName }.build()
}

// ✅ Compiles
sq.userName

// ❌ Compile error - product not selected
sq.product
```

### 2. Scalar Subquery Validation

✅ Scalar subqueries must return exactly one column

```kotlin
// ❌ Compile error - multiple columns
person.age gt subquery {
    from(Order)
        .select { order.userName; order.cost }  // Error! Each select returns only one column
        .build()
}

// ✅ Compiles
person.age gt subquery {
    from(Order)
        .select_avgCost { avg(order.cost) }  // Single aggregate
        .build()
}
```

### 3. Type Compatibility

✅ Subquery column types must match comparison types

```kotlin
// ❌ Compile error - type mismatch
person.age gt subquery {  // age is Int
    from(Order)
        .select { order.userName }  // userName is String - type error!
        .build()
}
```

### 4. Correlation Safety

✅ Correlated subqueries only access available outer columns

```kotlin
from(Person)
    .where {
        exists {
            from(Order)
                .where {
                    // ✅ Can access Person columns (outer)
                    order.userName eq person.name

                    // ❌ Compile error - Profile not in outer query
                    order.userName eq profile.contact
                }
        }
    }
```

---

## SQL Generation Examples

### Example 1: Derived Table in FROM

**Kodama:**

```kotlin
val expensive = subquery_Expensive {
    from(Order)
        .select { order.userName }
        .select_total { sum(order.cost) }
        .where { order.cost gt 100 }
        .build()
}

from(expensive)
    .select { expensive.userName }
    .select { expensive.total }
    .where { expensive.total gt 1000 }
```

**Generated SQL:**

```sql
SELECT expensive.user_name,
       expensive.total
FROM (SELECT user_name, SUM(cost) AS total
      FROM "order"
      WHERE cost > ?
      GROUP BY user_name) AS expensive
WHERE expensive.total > ?
```

**Arguments:** `[100, 1000]`

### Example 2: EXISTS with Correlation

**Kodama:**

```kotlin
from(Person)
    .selectAll(Person)
    .where {
        exists {
            from(Order)
                .select { order.id }
                .where {
                    (order.userName eq person.name) and (order.cost gt 1000)
                }
                .build()
        }
    }
```

**Generated SQL:**

```sql
SELECT person.name, person.age
FROM person
WHERE EXISTS (SELECT "order".id
              FROM "order"
              WHERE "order".user_name = person.name
                AND "order".cost > ?)
```

**Arguments:** `[1000]`

### Example 3: Scalar Subquery in SELECT

**Kodama:**

```kotlin
from(Person)
    .select { person.name }
    .select_orderCount {
        scalarSubquery {
            from(Order)
                .select_count { count(order.id) }
                .where { order.userName eq person.name }
                .build()
        }
    }
```

**Generated SQL:**

```sql
SELECT person.name,
       (SELECT COUNT("order".id)
        FROM "order"
        WHERE "order".user_name = person.name) AS order_count
FROM person
```

---

## Implementation Phases

### Phase 1: Foundation (Week 1)

- [ ] Create `SubqueryTable` and `SubqueryColumn` classes
- [ ] Implement `subquery(alias) { }` builder
- [ ] Add metadata extraction from Query
- [ ] Update SQL generation to handle subqueries in FROM

### Phase 2: FROM and JOIN (Week 2)

- [ ] Allow `SubqueryTable` in `from()` clause
- [ ] Allow `SubqueryTable` in `join()` clause
- [ ] Generate typed accessors for subquery columns
- [ ] Generate join contexts with subqueries
- [ ] Add comprehensive tests

### Phase 3: Scalar Subqueries (Week 3)

- [ ] Implement `ScalarSubqueryExpression`
- [ ] Add comparison operators with subqueries
- [ ] Add `scalarSubquery()` in SELECT clause
- [ ] Add validation (single column, compatible types)
- [ ] Add tests for scalar subqueries

### Phase 4: EXISTS (Week 4)

- [ ] Implement `ExistsExpression` and `NotExistsExpression`
- [ ] Add `exists()` and `notExists()` to WHERE context
- [ ] Add tests for EXISTS operations

### Phase 5: Correlation (Week 5)

- [ ] Design correlated subquery context
- [ ] Implement outer column access in subqueries
- [ ] Add correlation tests
- [ ] Performance testing

### Phase 6: Polish and Documentation (Week 6)

- [ ] Comprehensive test coverage
- [ ] Performance optimization
- [ ] Documentation updates
- [ ] Example applications
- [ ] Migration guide

---

## Testing Strategy

### Unit Tests

- Subquery metadata extraction
- SQL generation for each subquery type
- Parameter binding in subqueries
- Type validation

### Integration Tests

- Derived tables in FROM
- Subqueries in JOIN
- Scalar subqueries in SELECT
- EXISTS with correlation
- Complex nested subqueries
- Performance benchmarks

### Edge Cases

- Empty subquery results
- NULL handling in subqueries
- Multiple levels of nesting
- Subquery with all aggregate functions
- Subquery with no WHERE clause

---

## Open Questions

1. **Nesting Depth Limit?**
    - Should we limit subquery nesting depth?
    - PostgreSQL has no hard limit, but readability suffers

2. **CTE (Common Table Expressions) Support?**
    - WITH clauses are essentially named subqueries
    - Should we support CTEs separately or as part of subquery feature?

3. **Subquery Optimization Hints?**
    - Should we expose LATERAL, MATERIALIZED hints?
    - These are PostgreSQL-specific

4. **Dynamic Subquery Construction?**
    - Allow building subqueries conditionally?
    - May complicate type inference

5. **Subquery Caching?**
    - Should identical subqueries be cached/reused?
    - Affects parameter binding

---

## Success Criteria

✅ **Type Safety**

- All subquery operations are type-checked at compile time
- Invalid subqueries cause compilation errors, not runtime errors

✅ **Completeness**

- Support all standard SQL subquery locations (FROM, JOIN, WHERE, SELECT, HAVING)
- Support EXISTS/NOT EXISTS
- Support correlated subqueries

✅ **Performance**

- Generated SQL is idiomatic and efficient
- Parameter binding works correctly
- No unnecessary query nesting

✅ **Usability**

- Natural Kotlin syntax
- Clear error messages for invalid usage
- Comprehensive documentation and examples

✅ **Maintainability**

- Code generation remains manageable
- Test coverage > 90%
- Performance benchmarks in CI

---

## Alternative Approaches Considered

### Approach 1: Runtime Type Checking

**Rejected:** Violates Kodama's principle of compile-time safety

### Approach 2: Manual Table Definitions for Subqueries

**Rejected:** Too verbose, defeats purpose of derived tables

### Approach 3: Type Erasure for Subqueries

**Rejected:** Loses type safety, allows invalid operations

### Approach 4: Dynamic Proxy Generation

**Rejected:** Requires reflection, adds runtime overhead

### Chosen Approach: Compile-Time Code Generation

**Selected:** Maintains type safety, no runtime overhead, idiomatic Kotlin

---

## Risks and Mitigations

| Risk                       | Impact | Mitigation                                  |
|----------------------------|--------|---------------------------------------------|
| Code generation complexity | High   | Incremental development, extensive testing  |
| Type inference limitations | Medium | Explicit type parameters where needed       |
| SQL generation bugs        | High   | Comprehensive SQL tests, manual review      |
| Performance degradation    | Medium | Benchmarks in CI, profiling                 |
| Breaking changes           | Low    | Separate module initially, deprecation path |

---

## Conclusion

Subquery support is essential for complex queries and will significantly enhance Kodama's capabilities while maintaining
its core principle of 100% compile-time type safety. The proposed design balances power, safety, and usability.

**Recommendation:** Proceed with incremental implementation starting with Phase 1 (Foundation) and Phase 2 (FROM/JOIN),
as these provide the most value and establish the architecture for future phases.
