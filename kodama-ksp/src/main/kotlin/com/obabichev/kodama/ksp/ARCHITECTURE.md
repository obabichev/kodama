# KSP Architecture for Kodama

## The Core Challenge

KSP (Kotlin Symbol Processing) has a **fundamental limitation**: it operates on the symbol level (declarations, types, annotations) but **does not have access to function body implementations**.

This means we **cannot directly analyze** code like:

```kotlin
fun testQuery() {
    val results = query()
        .from(Order)
        .selectAggregates {
            sum(order.cost) alias "totalRevenue"
            count(order.id) alias "orderCount"
        }
        .execute(connection)
}
```

## Alternative Approaches

### Approach 1: Annotation-Based (Recommended)

**Idea**: Users annotate queries to provide metadata for code generation.

```kotlin
@KodamaQuery(
    tables = [Order::class],
    aggregates = [
        Aggregate("sum", Order::cost, alias = "totalRevenue"),
        Aggregate("count", Order::id, alias = "orderCount")
    ]
)
fun testMultipleAggregates() {
    val results = query()
        .from(Order)
        .selectAggregates {
            sum(order.cost) alias "totalRevenue"
            count(order.id) alias "orderCount"
        }
        .execute(connection)

    // IDE now knows: results.first().totalRevenue exists!
}
```

**Pros**:
- KSP can easily process annotations
- Explicit and clear
- Works with IDE immediately

**Cons**:
- Requires manual annotation (extra boilerplate)
- Annotation must stay in sync with actual query

### Approach 2: Separate Declaration (Builder Pattern)

**Idea**: Declare query structure separately, get typed builder.

```kotlin
// Declare query structure
val multiAggregateQuery = defineQuery {
    from(Order)
    selectAggregates {
        sum(Order.cost) alias "totalRevenue"
        count(Order.id) alias "orderCount"
    }
}

// KSP generates: MultiAggregateQuery_Builder with execute() returning typed results

fun test() {
    val results = multiAggregateQuery.execute(connection)
    // Type-safe: results.first().totalRevenue
}
```

**Pros**:
- Clean separation of structure and execution
- KSP can analyze the declaration
- Reusable query definitions

**Cons**:
- Less fluent than inline builder
- Requires separate declaration

### Approach 3: Inline Type Encoding (Current Enhanced)

**Idea**: Encode selections in type parameters, no KSP needed.

```kotlin
fun testQuery() {
    val results = query()
        .from(Order)
        .selectAggregates {
            sum(order.cost) alias "totalRevenue"
            count(order.id) alias "orderCount"
        }
        // selectAggregates returns: AggregateQueryBuilder<Order, Has2Aggregates>
        .execute(connection)
        // execute returns: AggregateResult2<BigDecimal, Long>
}

// AggregateResult2 generated once (not per query)
class AggregateResult2<T1, T2>(
    private val agg1: T1,
    private val agg2: T2,
    private val aggregates: List<AggregateFunction<*>>
) {
    val agg1Value: T1 get() = agg1
    val agg2Value: T2 get() = agg2

    // Access by name using inline function
    inline fun <reified T> get(name: String): T {
        // Runtime lookup but compile-time type
    }
}
```

**Pros**:
- No KSP complexity
- No annotations needed
- Type-safe at compile time

**Cons**:
- Awkward accessor names (agg1Value, agg2Value) OR runtime name lookup
- Need pre-generated classes for 1-N aggregates

### Approach 4: Kotlin K2 Compiler Plugin (Advanced)

**Idea**: True compiler plugin that can analyze function bodies.

**Pros**:
- Can detect actual query patterns
- True "if it compiles, it works" magic
- No user annotations

**Cons**:
- **Extremely complex** to implement
- Requires deep K2 compiler knowledge
- Maintenance burden
- May break with Kotlin updates

## Recommendation

After analyzing all approaches, I recommend:

**Short-term**: Approach 3 (Inline Type Encoding)
- Improve current implementation
- Use type parameters to encode aggregate count
- Generate fixed set of result classes (Result1, Result2, ... Result10)
- Provide named accessors via inline reflection

**Long-term**: Approach 1 (Annotation-Based) if needed
- Add optional @KodamaQuery annotations
- Use KSP to generate perfect result classes
- Annotations provide compile-time verification

## Why NOT Full KSP Detection?

KSP cannot:
1. Parse function bodies
2. Analyze lambda expressions
3. Detect method chains
4. See runtime values

This makes it **fundamentally unsuitable** for detecting query patterns without user hints (annotations).

## Next Steps

1. Document this limitation clearly
2. Implement Approach 3 (type-encoded) for immediate improvement
3. Design annotation API for future KSP enhancement
4. Update ROADMAP.md with realistic expectations
