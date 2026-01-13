# Query API Deep Research: Achieving Zero Regex + Inline UX

**Date:** January 12, 2026
**Context:** Exploring all possible API designs to eliminate query/subquery split

---

## The Core Problem

**User Requirement:**
> "I still see that part of subquery is in outer defined object. It looks ugly because user have to define half of query in one place, and another half in another place."

**Constraints:**
1. ✅ Zero regex (non-negotiable)
2. ✅ Inline UX (whole query visible at usage site)
3. ✅ No split between structure and parameters
4. ✅ Support parameters (WHERE, ORDER BY, LIMIT)
5. ✅ Support nested subqueries
6. ✅ Reusability (define once, use many times)
7. ✅ Breaking changes OK

**The Fundamental Tension:**

```
KSP Discovery          User Experience         Implementation
─────────────         ─────────────────        ───────────────
Can discover:         Wants to write:          Cannot bridge with:
- Objects            - Inline code             - Regex (forbidden)
- Classes            - Single location         - Reflection (too late)
- Properties         - No split                - AST parsing (?)
- Functions          - Reusable                - Macros (don't exist)
```

**Current Best (Query Objects):**
```kotlin
// Structure definition (separate location)
object UserTotalsQuery : Query<UserTotals> {
    val userName = column<String>("user_name")
    val totalCost = column<Long>("total_cost")

    override fun buildBase() = from(Order)
        .select { order.userName as userName }
        .select { sum(order.cost) as totalCost }
        .groupBy { order.userName }
}

// Usage with parameters (inline)
UserTotalsQuery
    .where { totalCost gt 1000 }    // ← Parameters inline
    .orderBy { totalCost.desc() }
    .limit(10)
```

**User's Issue:** Structure (SELECT, FROM, GROUP BY) is separate from usage.

---

## Option 1: Full Kotlin AST Parser (Zero Regex, Full Discovery)

### Concept

Replace regex scanning with full Kotlin parser that can extract function bodies.

**How it works:**
1. Use Kotlin compiler's PSI (Program Structure Interface) to parse test files
2. Find function calls to `from()`, `join()`, etc.
3. Extract full query structure from AST
4. Generate code based on discovered patterns

**Code example:**
```kotlin
// Test file - completely inline
@Test
fun `test user totals`() {
    val results = from(Person)
        .join(
            from(Order)                           // ← Inline subquery
                .select { order.userName }
                .select { sum(order.cost) }
                .groupBy { order.userName }
                .where { order.cost gt 100 }      // ← Parameters inline
                .build()
                .aliasAs<UserTotals>()
        ) { userTotals.userName eq person.name }
        .selectAll(Person)
        .selectAll(UserTotals)
        .execute(tx)
}
```

**What gets generated (from AST analysis):**
```kotlin
// KSP discovers the subquery structure from test code
interface UserTotals {
    val userName: String
    val totalCost: Long
}

class UserTotalsAccessor { /* ... */ }
object PersonCanJoinUserTotals : CanJoin<Person, UserTotals>
```

### Implementation Approach

```kotlin
class KotlinASTQueryDiscovery(private val sourceFiles: List<File>) {

    fun discoverQueries(): List<QueryPattern> {
        val parser = KotlinParser()
        val queries = mutableListOf<QueryPattern>()

        sourceFiles.forEach { file ->
            val psi = parser.parse(file.readText())

            // Traverse AST to find from() calls
            psi.accept(object : KtTreeVisitorVoid() {
                override fun visitCallExpression(call: KtCallExpression) {
                    if (call.calleeExpression?.text == "from") {
                        val query = extractQueryStructure(call)
                        queries.add(query)
                    }
                    super.visitCallExpression(call)
                }
            })
        }

        return queries
    }

    private fun extractQueryStructure(call: KtCallExpression): QueryPattern {
        // Walk the call chain: from().join().select().where()...
        val chain = mutableListOf<QueryOperation>()

        var current: KtExpression? = call
        while (current is KtQualifiedExpression) {
            val operation = current.selectorExpression as? KtCallExpression
            operation?.let {
                chain.add(QueryOperation(
                    name = it.calleeExpression?.text ?: "",
                    arguments = extractArguments(it),
                    lambdaBody = extractLambda(it)
                ))
            }
            current = current.receiverExpression
        }

        return QueryPattern(chain.reversed())
    }
}
```

### Pros

✅ **Zero regex** - Uses structured parsing
✅ **Inline UX** - Write queries exactly as you want
✅ **No split** - Everything in one place
✅ **Full discovery** - Can discover any query pattern
✅ **Parameters inline** - WHERE/ORDER BY at call site
✅ **Nested subqueries** - Naturally supported

### Cons

❌ **Heavy dependency** - Requires Kotlin compiler as dependency
❌ **Compilation complexity** - Must parse Kotlin code (slow)
❌ **Fragile to formatting** - Still sensitive to code structure (though less than regex)
❌ **Test-driven generation** - Like current approach, must exist in tests first
❌ **Maintenance burden** - Must track Kotlin language evolution
❌ **Not truly declarative** - Still discovers from usage

### Feasibility: 🟡 Medium

**Technical:** Possible - Kotlin compiler exposes PSI
**Performance:** Slower than KSP (full parsing)
**Maintenance:** High - must track Kotlin language changes

### Zero Regex? ✅ Yes

Uses structured AST parsing, not pattern matching.

### Inline UX? ✅ Excellent (10/10)

Users write queries exactly as they want with no split.

---

## Option 2: Query Builder Functions (KSP-Discoverable)

### Concept

Define queries as **top-level functions** that KSP can discover, return reusable query templates.

**How it works:**
1. Define queries as functions (KSP discovers function declarations)
2. Functions return query templates
3. Templates can be parameterized at usage site

**Code example:**
```kotlin
// Define query as function (KSP discovers this)
fun userTotalsQuery(): SubqueryBuilder<UserTotals> {
    return subqueryBuilder {
        from(Order)
            .select { order.userName as UserTotals::userName }
            .select { sum(order.cost) as UserTotals::totalCost }
            .groupBy { order.userName }
    }
}

// Usage with inline parameters
from(Person)
    .join(
        userTotalsQuery()
            .where { totalCost gt 1000 }    // ← Inline parameters
            .orderBy { totalCost.desc() }
    ) { userTotals.userName eq person.name }
    .selectAll(Person)
    .selectAll(UserTotals)
```

**What KSP generates:**
```kotlin
// KSP scans for functions returning SubqueryBuilder<T>
interface UserTotals {
    val userName: String
    val totalCost: Long
}

class UserTotalsAccessor { /* ... */ }

// Relationship to function-based subquery
object PersonCanJoinUserTotals : CanJoin<Person, UserTotals>
```

### Implementation

```kotlin
// Core library provides
class SubqueryBuilder<T>(
    private val base: QueryBuilder,
    private val resultType: KClass<T>
) {
    fun where(block: WhereContext.() -> Expression): SubqueryBuilder<T> {
        return SubqueryBuilder(base.where(block), resultType)
    }

    fun orderBy(block: OrderByContext.() -> Unit): SubqueryBuilder<T> {
        return SubqueryBuilder(base.orderBy(block), resultType)
    }

    fun limit(n: Int): SubqueryBuilder<T> {
        return SubqueryBuilder(base.limit(n), resultType)
    }

    internal fun build(): BuiltSubquery<T> {
        return BuiltSubquery(base.build(), resultType)
    }
}

// Helper DSL
fun <T> subqueryBuilder(block: () -> QueryBuilder): SubqueryBuilder<T> {
    return SubqueryBuilder(block(), T::class)
}

// KSP processor
class SubqueryFunctionProcessor : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Find all functions returning SubqueryBuilder<T>
        val subqueryFunctions = resolver
            .getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { func ->
                val returnType = func.returnType?.resolve()
                returnType?.declaration?.simpleName?.asString() == "SubqueryBuilder"
            }

        subqueryFunctions.forEach { func ->
            // Extract T from SubqueryBuilder<T>
            val typeArg = func.returnType?.resolve()?.arguments?.firstOrNull()
            val resultInterface = typeArg?.type?.resolve()?.declaration

            // Generate accessor and CanJoin instances
            generateSubqueryCode(resultInterface)
        }

        return emptyList()
    }
}
```

### Pros

✅ **Zero regex** - KSP discovers function declarations
✅ **Reusability** - Functions can be called multiple times
✅ **Parameters inline** - WHERE/ORDER BY at call site
✅ **Better UX** - Less split (function vs usage)
✅ **Type-safe** - Return type declares result structure
✅ **Composable** - Functions can call other query functions

### Cons

🟡 **Moderate split** - Query structure in function, parameters at call site
🟡 **Verbosity** - Must declare function for each reusable query
❌ **Result type separation** - Must define `UserTotals` interface separately
❌ **Column mapping unclear** - `as UserTotals::userName` is verbose

### Feasibility: 🟢 High

**Technical:** Straightforward - KSP already discovers functions
**Performance:** Fast - standard KSP processing
**Maintenance:** Low - stable API

### Zero Regex? ✅ Yes

KSP natively discovers function declarations.

### Inline UX? 🟡 Good (7/10)

Parameters are inline, but query structure is in separate function. Better than objects, but still has split.

---

## Option 3: Phantom Type Query Templates (Pure Compile-Time)

### Concept

Encode EVERYTHING in types - no runtime discovery, no KSP discovery of patterns. Only generate from explicit declarations.

**How it works:**
1. Users explicitly declare query templates as types
2. All query state lives in phantom types
3. No pattern discovery needed - everything is declared upfront

**Code example:**
```kotlin
// Explicit query template declaration (like Table declarations)
object UserTotalsTemplate : QueryTemplate<UserTotals>(
    from = Order,
    selections = selections {
        column(Order.userName) alias "userName"
        aggregate(sum(Order.cost)) alias "totalCost"
    },
    groupBy = listOf(Order.userName)
)

// Result interface (must be declared)
interface UserTotals {
    val userName: String
    val totalCost: Long
}

// Usage with inline parameters - NO SPLIT!
from(Person)
    .join(
        query(UserTotalsTemplate)              // Reference template
            .where { totalCost gt 1000 }       // Inline parameters
            .orderBy { totalCost.desc() }
            .limit(10)
    ) { userTotals.userName eq person.name }
    .selectAll(Person)
    .selectAll(UserTotals)
```

**What KSP generates:**
```kotlin
// KSP only generates based on EXPLICIT declarations
class UserTotalsAccessor(
    val userName: TypedColumn<String>,
    val totalCost: TypedColumn<Long>
)

object PersonCanJoinUserTotals : CanJoin<Person, UserTotals>
```

### Implementation

```kotlin
// Core library: Query template DSL
abstract class QueryTemplate<T>(
    val from: Table,
    val selections: List<Selection>,
    val joins: List<Join> = emptyList(),
    val groupBy: List<Column<*>> = emptyList()
) {
    // Column accessors for WHERE/ORDER BY
    abstract val columns: ColumnAccessors<T>
}

// Builder for inline parameters
class ParameterizedQuery<T>(
    private val template: QueryTemplate<T>
) {
    private val additionalConditions = mutableListOf<Expression>()
    private val orderByClauses = mutableListOf<OrderByClause>()
    private var limitValue: Int? = null

    fun where(block: T.() -> Expression): ParameterizedQuery<T> {
        additionalConditions.add(block(template.columns as T))
        return this
    }

    fun orderBy(block: T.() -> OrderByClause): ParameterizedQuery<T> {
        orderByClauses.add(block(template.columns as T))
        return this
    }

    fun limit(n: Int): ParameterizedQuery<T> {
        limitValue = n
        return this
    }

    fun build(): BuiltQuery {
        return template.toQueryBuilder()
            .apply {
                additionalConditions.forEach { where(it) }
                orderByClauses.forEach { orderBy(it) }
                limitValue?.let { limit(it) }
            }
            .build()
    }
}

// DSL helper
fun <T> query(template: QueryTemplate<T>): ParameterizedQuery<T> {
    return ParameterizedQuery(template)
}

// KSP processor - ONLY processes explicit declarations
class QueryTemplateProcessor : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Find all QueryTemplate subclasses
        val templates = resolver
            .getSymbolsWithSupertype<QueryTemplate<*>>()

        templates.forEach { template ->
            // Extract T from QueryTemplate<T>
            val resultType = template.typeArguments.first()

            // Generate accessors based on explicit declaration
            generateQueryCode(template, resultType)
        }

        return emptyList()
    }
}
```

### Pros

✅ **Zero regex** - Only processes explicit declarations
✅ **Zero discovery** - No scanning needed
✅ **Parameters inline** - WHERE/ORDER BY at call site
✅ **Fast compilation** - Minimal KSP work
✅ **Explicit** - Everything declared upfront
✅ **Predictable** - No hidden magic

### Cons

❌ **Still has split** - Template declaration vs usage parameters
❌ **Verbose declarations** - Must explicitly declare structure
❌ **Learning curve** - Template DSL syntax
🟡 **Less flexible** - Can't easily create ad-hoc queries

### Feasibility: 🟢 High

**Technical:** Straightforward - just KSP processing of declarations
**Performance:** Very fast - no discovery overhead
**Maintenance:** Low - simple, predictable

### Zero Regex? ✅ Yes

No pattern discovery at all - only explicit declarations.

### Inline UX? 🟡 Moderate (6/10)

Parameters are inline, but template structure must be declared separately. Similar split to current approach.

---

## Option 4: Context Receivers for Inline Queries

### Concept

Use Kotlin's context receivers to make query scope implicit, reducing perceived split.

**How it works:**
1. Define query templates with context receivers
2. Usage site provides context implicitly
3. Feels more inline due to implicit scope

**Code example:**
```kotlin
// Query definition with context receiver
context(QueryScope)
fun userTotalsQuery(): SubqueryTemplate<UserTotals> = subquery {
    from(Order)
        .select { order.userName }
        .select { sum(order.cost) }
        .groupBy { order.userName }
}

// Usage - context makes it feel more inline
fun queryUserTotals() = with(QueryScope) {
    from(Person)
        .join(
            userTotalsQuery()                  // ← Looks inline due to context
                .where { totalCost gt 1000 }
                .orderBy { totalCost.desc() }
        ) { userTotals.userName eq person.name }
        .selectAll(Person)
        .selectAll(UserTotals)
}
```

### Pros

✅ **Zero regex** - Functions discoverable by KSP
✅ **Parameters inline** - WHERE/ORDER BY at call site
🟡 **Feels more inline** - Context reduces boilerplate

### Cons

❌ **Still has split** - Function definition separate from usage
❌ **Context receivers unstable** - Still experimental in Kotlin
❌ **Complexity** - Adds another language feature to learn
🟡 **Limited improvement** - Doesn't fundamentally solve the split

### Feasibility: 🟡 Medium

**Technical:** Possible but uses experimental features
**Performance:** Standard KSP processing
**Maintenance:** Medium - depends on experimental feature

### Zero Regex? ✅ Yes

### Inline UX? 🟡 Moderate (6.5/10)

Context helps but doesn't eliminate the split.

---

## Option 5: Compile-Time Macros (Hypothetical)

### Concept

If Kotlin had macros (like Rust), we could transform inline code at compile-time.

**How it works (hypothetical):**
```kotlin
// What user writes (completely inline)
from(Person)
    .join(
        @macro subquery<UserTotals> {  // ← Macro transforms this at compile-time
            from(Order)
                .select { order.userName }
                .select { sum(order.cost) }
                .groupBy { order.userName }
                .where { order.cost gt 1000 }
        }
    ) { userTotals.userName eq person.name }
```

**What macro expands to:**
```kotlin
// Compiler generates at compile-time
object Generated_UserTotals_12345 : QueryTemplate<UserTotals> {
    // ... structure from inline code
}

// Usage site becomes
from(Person)
    .join(Generated_UserTotals_12345) { /* ... */ }
```

### Pros

✅ **Zero regex** - Compile-time transformation
✅ **Perfect inline UX** - Write exactly what you want
✅ **Zero split** - Everything in one place
✅ **Full power** - Can generate anything

### Cons

❌ **Doesn't exist** - Kotlin has no macro system
❌ **No timeline** - Not planned for Kotlin
❌ **Can't implement** - Would require language changes

### Feasibility: ❌ Impossible

Kotlin does not have and does not plan to have a macro system like Rust.

### Zero Regex? ✅ Yes (if it existed)

### Inline UX? ✅ Perfect (10/10) (if it existed)

---

## Option 6: Inverted Discovery (Two-Pass Generation)

### Concept

Generate query objects automatically FROM usage sites in a two-pass compilation.

**How it works:**
1. **Pass 1 (AST Parser):** Scan code to find inline subqueries, generate template objects
2. **Pass 2 (KSP):** Process generated objects normally

**Code example:**
```kotlin
// User writes completely inline (Pass 1 discovers this)
@Test
fun `test query`() {
    from(Person)
        .join(
            @DiscoverSubquery("UserTotals")    // ← Marker for Pass 1
            from(Order)
                .select { order.userName }
                .select { sum(order.cost) }
                .groupBy { order.userName }
                .where { order.cost gt 1000 }
        ) { userTotals.userName eq person.name }
}

// Pass 1 generates (before KSP)
object UserTotalsQuery : QueryTemplate<UserTotals> {
    // Structure extracted from inline code
}

// Pass 2 (KSP) processes generated objects
interface UserTotals { /* ... */ }
class UserTotalsAccessor { /* ... */ }
```

### Implementation

```kotlin
// Gradle plugin: Two-pass task
class KodamaTwoPassGenerationTask : DefaultTask() {

    @TaskAction
    fun generate() {
        // Pass 1: Discover inline queries
        val discoveredQueries = discoverInlineQueries()

        // Generate template objects
        discoveredQueries.forEach { query ->
            generateTemplateObject(query)
        }

        // Pass 2: Run KSP on generated objects
        project.tasks.getByName("kspKotlin").execute()
    }

    private fun discoverInlineQueries(): List<InlineQuery> {
        // Use AST parser to find @DiscoverSubquery annotations
        val parser = KotlinASTParser()
        return sourceFiles.flatMap { file ->
            parser.findAnnotatedQueries(file, "DiscoverSubquery")
        }
    }

    private fun generateTemplateObject(query: InlineQuery) {
        val code = """
            object ${query.name}Query : QueryTemplate<${query.name}> {
                ${generateStructure(query)}
            }
        """.trimIndent()

        File(generatedDir, "${query.name}Query.kt").writeText(code)
    }
}
```

### Pros

✅ **Zero regex** - Uses AST parser
✅ **Inline UX** - User writes inline code
✅ **Automatic generation** - No manual object creation
✅ **Reusable** - Generated objects can be referenced

### Cons

❌ **Complex build** - Two-pass compilation process
❌ **Still uses AST parsing** - Same issues as Option 1
❌ **Requires markers** - Must annotate subqueries
❌ **Build time** - Slower (double processing)
🟡 **Generated code** - More files to review

### Feasibility: 🟡 Medium

**Technical:** Possible but complex
**Performance:** Slower (two passes)
**Maintenance:** High - complex build pipeline

### Zero Regex? ✅ Yes

Uses AST parsing with two-pass generation.

### Inline UX? 🟢 Good (8/10)

Users write inline, but must add `@DiscoverSubquery` markers.

---

## Option 7: Pure Runtime Queries (No Code Generation)

### Concept

Eliminate code generation entirely - build queries at runtime with reflection.

**How it works:**
1. No KSP, no code generation
2. Queries build structure at runtime
3. Reflection for column access
4. Type safety via generic constraints

**Code example:**
```kotlin
// Completely inline - zero code generation
from(Person)
    .join(
        from(Order)
            .select(Order.userName)
            .select(sum(Order.cost).alias("totalCost"))
            .groupBy(Order.userName)
            .where { Order.cost gt 1000 }
            .asSubquery<UserTotals>()    // Runtime registration
    ) { userTotals["userName"] eq person.name }
    .selectAll(Person)
    .selectAll<UserTotals>()
    .execute(tx)
    .forEach { row ->
        val name = row.person.name
        val total = row.subquery<UserTotals>()["totalCost"] as Long
    }
```

### Pros

✅ **Zero regex** - No discovery needed
✅ **Perfect inline UX** - Everything in one place
✅ **Zero split** - No separation at all
✅ **No code generation** - Simplest possible
✅ **Flexible** - Ad-hoc queries trivial

### Cons

❌ **No compile-time safety** - Type errors at runtime
❌ **Performance overhead** - Reflection cost
❌ **Against project philosophy** - Kodama is about compile-time safety
❌ **Worse IDE support** - No autocomplete
❌ **String-based access** - `row["userName"]` instead of `row.userName`

### Feasibility: 🟢 High (but wrong approach)

**Technical:** Easy to implement
**Performance:** Slower (reflection)
**Maintenance:** Low

**BUT**: Violates Kodama's core principle: "100% compile-time type safety"

### Zero Regex? ✅ Yes

No discovery, no generation, no regex.

### Inline UX? ✅ Perfect (10/10)

But at the cost of type safety (unacceptable trade-off).

---

## Option 8: Kotlin Compiler Plugin (KCP) with IR Transformation

### Concept

Use Kotlin Compiler Plugin to transform inline code at IR level (lower than AST).

**How it works:**
1. User writes inline queries
2. Compiler plugin intercepts IR (Intermediate Representation)
3. Transforms inline queries into generated objects
4. KSP processes generated objects

**Code example:**
```kotlin
// User writes (completely inline)
from(Person)
    .join(
        inlineSubquery<UserTotals> {       // ← Compiler plugin intercepts this
            from(Order)
                .select { order.userName }
                .select { sum(order.cost) }
                .groupBy { order.userName }
        }
            .where { totalCost gt 1000 }   // ← Parameters also inline
    ) { userTotals.userName eq person.name }

// Compiler plugin transforms to (at IR level)
from(Person)
    .join(Generated_UserTotals_Object) { /* ... */ }

// Generated object (from IR transformation)
object Generated_UserTotals : QueryTemplate<UserTotals> { /* ... */ }
```

### Implementation (Conceptual)

```kotlin
// Kotlin Compiler Plugin
class KodamaCompilerPlugin : ComponentRegistrar {
    override fun registerProjectComponents(
        project: MockProject,
        configuration: CompilerConfiguration
    ) {
        IrGenerationExtension.registerExtension(
            project,
            KodamaIrGenerationExtension()
        )
    }
}

class KodamaIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol.owner.name.asString() == "inlineSubquery") {
                    // Extract lambda body
                    val lambda = expression.getValueArgument(0) as IrFunctionExpression

                    // Generate object from lambda
                    val generatedObject = generateQueryObject(lambda)

                    // Replace call with reference to generated object
                    return IrGetObjectValue(generatedObject.symbol)
                }

                return super.visitCall(expression)
            }
        })
    }
}
```

### Pros

✅ **Zero regex** - IR transformation
✅ **Perfect inline UX** - Write exactly what you want
✅ **Zero split** - Everything in one place
✅ **Compile-time** - Transformation before compilation
✅ **Automatic** - No manual object creation
✅ **Type-safe** - Full type checking after transformation

### Cons

❌ **Extreme complexity** - Compiler plugin development is very hard
❌ **IR instability** - Kotlin IR changes between versions
❌ **Debugging nightmare** - Hard to debug IR transformations
❌ **Maintenance burden** - Must track Kotlin compiler changes
❌ **Long development time** - Months to implement correctly
🟡 **Generated code hidden** - Users can't see transformed code easily

### Feasibility: 🔴 Low (extremely complex)

**Technical:** Possible but extremely difficult
**Performance:** Fast (compile-time)
**Maintenance:** Very high - Kotlin IR is unstable
**Development time:** 3-6 months minimum

### Zero Regex? ✅ Yes

IR transformation, not pattern matching.

### Inline UX? ✅ Perfect (10/10)

But at extreme implementation cost.

---

## Comparison Matrix

| Option | Zero Regex | Inline UX | No Split | Feasibility | Dev Time | Maintenance |
|--------|-----------|-----------|----------|-------------|----------|-------------|
| **1. Full AST Parser** | ✅ Yes | ✅ 10/10 | ✅ Yes | 🟡 Medium | 3-4 weeks | High |
| **2. Query Builder Functions** | ✅ Yes | 🟡 7/10 | ❌ Moderate | 🟢 High | 2-3 weeks | Low |
| **3. Phantom Type Templates** | ✅ Yes | 🟡 6/10 | ❌ No | 🟢 High | 2 weeks | Low |
| **4. Context Receivers** | ✅ Yes | 🟡 6.5/10 | ❌ No | 🟡 Medium | 2 weeks | Medium |
| **5. Macros** | N/A | N/A | N/A | ❌ Impossible | N/A | N/A |
| **6. Inverted Discovery** | ✅ Yes | 🟢 8/10 | 🟡 Mostly | 🟡 Medium | 4-5 weeks | High |
| **7. Pure Runtime** | ✅ Yes | ✅ 10/10 | ✅ Yes | 🟢 High | 1 week | Low |
| **8. Compiler Plugin (IR)** | ✅ Yes | ✅ 10/10 | ✅ Yes | 🔴 Low | 3-6 months | Very High |

### Scoring

**Zero Regex:** All options except #5 (impossible) and #7 (wrong philosophy) achieve this.

**Inline UX:**
- **Perfect (10/10):** Options 1, 7, 8 - write exactly what you want
- **Good (8/10):** Option 6 - inline with markers
- **Moderate (7/10):** Option 2 - function-based, less split
- **Okay (6-6.5/10):** Options 3, 4 - still have split

**No Split:**
- ✅ Complete: Options 1, 7, 8
- 🟡 Mostly: Option 6 (needs markers)
- ❌ Still split: Options 2, 3, 4

**Feasibility:**
- 🟢 High: Options 2, 3, 7
- 🟡 Medium: Options 1, 4, 6
- 🔴 Low: Option 8
- ❌ Impossible: Option 5

---

## Deep Analysis: Can We Have It All?

### The Three-Way Constraint

```
          Zero Regex
              /\
             /  \
            /    \
           /      \
          /        \
         /          \
        /            \
   Inline UX -------- Feasibility
```

**Pick any two:**
- **Zero Regex + Inline UX** → Low Feasibility (Option 8: Compiler Plugin)
- **Zero Regex + Feasibility** → Moderate Inline UX (Options 2, 3: Functions/Templates)
- **Inline UX + Feasibility** → No Zero Regex (would use regex/AST - Option 1)

### Why This Is Hard

**The KSP Limitation:**
```kotlin
// KSP can discover THIS (declaration):
object UserTotalsQuery : QueryTemplate<UserTotals> {
    // Structure
}

// KSP CANNOT discover THIS (code in lambda):
from(Person).join(
    from(Order).select { ... }.groupBy { ... }  // ← Lambda body invisible to KSP
)
```

**To have inline code be discoverable, we need:**
1. **Regex** (forbidden) - pattern match lambda bodies
2. **AST Parser** (complex) - parse Kotlin code structure
3. **Compiler Plugin** (very complex) - transform IR
4. **Macros** (don't exist) - compile-time code generation

### The UX vs Safety Trade-off

```
More Inline UX                                 More Type Safety
────────────────────────────────────────────────────────────
Pure Runtime  →  AST Parser  →  Functions  →  Templates  →  Current (Objects)
(no safety)     (discovered)   (moderate)   (declared)   (explicit)
```

**Kodama's Position:** "100% compile-time type safety"

This means pure runtime (Option 7) is off the table.

---

## Recommended Approaches

### Recommendation 1: Full AST Parser (Best Balance) ⭐

**Why:**
- ✅ Achieves zero regex
- ✅ Perfect inline UX (no split)
- ✅ Feasible (3-4 weeks development)
- ✅ Maintains type safety

**Trade-offs:**
- Higher maintenance (track Kotlin evolution)
- Slower compilation (full parsing)
- Still "test-driven" (like current approach)

**Implementation Plan:**
1. Add Kotlin compiler as dependency
2. Implement PSI-based query extraction
3. Replace regex patterns in GenerateQueryExtensionsTask
4. Test with existing queries
5. Migration path: parallel processing (AST + regex) → validate → remove regex

**Example:**
```kotlin
// User writes (completely inline)
from(Person)
    .join(
        from(Order)
            .select { order.userName }
            .select { sum(order.cost) }
            .groupBy { order.userName }
            .where { order.cost gt 1000 }    // ← Everything inline
            .aliasAs<UserTotals>()
    ) { userTotals.userName eq person.name }

// AST parser extracts structure
// KSP generates code
// Zero regex, perfect UX
```

**Risk: 🟡 Medium** (complexity manageable)

---

### Recommendation 2: Query Builder Functions (Pragmatic) ⭐⭐

**Why:**
- ✅ Zero regex (KSP discovers functions)
- ✅ Good UX (parameters inline, less split than objects)
- ✅ High feasibility (2-3 weeks)
- ✅ Low maintenance
- ✅ Familiar pattern (functions are well-understood)

**Trade-offs:**
- Moderate split (function definition vs usage)
- Must define function for each reusable query

**Implementation Plan:**
1. Add `SubqueryBuilder<T>` class to core library
2. Add `subqueryBuilder {}` DSL helper
3. Update KSP to discover functions returning `SubqueryBuilder<T>`
4. Generate accessors and CanJoin instances
5. Update documentation

**Example:**
```kotlin
// Define query as function (KSP discovers)
fun userTotalsQuery(): SubqueryBuilder<UserTotals> {
    return subqueryBuilder {
        from(Order)
            .select { order.userName as UserTotals::userName }
            .select { sum(order.cost) as UserTotals::totalCost }
            .groupBy { order.userName }
    }
}

// Usage with inline parameters
from(Person)
    .join(
        userTotalsQuery()
            .where { totalCost gt 1000 }     // ← Parameters inline!
            .orderBy { totalCost.desc() }
    ) { userTotals.userName eq person.name }
```

**Risk: 🟢 Low** (straightforward implementation)

---

### Recommendation 3: Hybrid Approach (Best of Both) ⭐⭐⭐

**Combine AST Parser + Query Functions:**

**Why:**
- ✅ Zero regex
- ✅ Inline UX for ad-hoc queries (AST discovers)
- ✅ Reusability for common queries (functions)
- ✅ Gradual migration path

**How it works:**
```kotlin
// Ad-hoc inline queries (AST discovers)
from(Person)
    .join(
        from(Order)
            .select { order.userName }
            .select { sum(order.cost) }
            .groupBy { order.userName }
            .aliasAs<UserTotals>()
    ) { userTotals.userName eq person.name }

// Reusable query functions (KSP discovers)
fun userTotalsQuery(): SubqueryBuilder<UserTotals> { /* ... */ }

from(Person)
    .join(userTotalsQuery()
        .where { totalCost gt 1000 }
    ) { userTotals.userName eq person.name }
```

**Benefits:**
- Both patterns supported
- Users choose based on need
- Ad-hoc: inline UX
- Reusable: function-based

**Implementation:**
1. Implement AST parser (Recommendation 1)
2. Implement query functions (Recommendation 2)
3. Both work together seamlessly

**Risk: 🟡 Medium** (more implementation work)

---

## Final Recommendation

### Go with: **Hybrid Approach (Recommendation 3)** ⭐⭐⭐

**Rationale:**
1. **Satisfies all constraints:**
   - ✅ Zero regex (AST + KSP)
   - ✅ Inline UX (ad-hoc queries)
   - ✅ No split for one-time queries
   - ✅ Reusability (functions)
   - ✅ Parameters inline (both patterns)

2. **Best of both worlds:**
   - Ad-hoc queries: Write inline, AST discovers
   - Common queries: Define function, reuse everywhere

3. **User choice:**
   - Simple case → inline
   - Reusable → function
   - Let users decide

4. **Gradual adoption:**
   - Start with functions (easier, lower risk)
   - Add AST parser later (more complex)
   - Or implement both in parallel

5. **Migration path:**
   - Current objects → Functions (easy)
   - Functions → Inline (when AST ready)

### Implementation Phases

**Phase 1: Query Builder Functions (2-3 weeks)**
- Implement `SubqueryBuilder<T>` API
- KSP processor for function discovery
- Test with existing queries
- Document and release

**Phase 2: AST Parser (3-4 weeks)**
- Add Kotlin compiler dependency
- Implement PSI-based extraction
- Parallel processing (AST + functions)
- Validate and test

**Phase 3: Full Migration (1-2 weeks)**
- Remove old object-based approach
- Update all documentation
- Migration guide for users
- Final release

**Total timeline: 6-9 weeks**

---

## Alternative: If Timeline Is Critical

### Go with: **Query Builder Functions Only (Recommendation 2)** ⭐⭐

**If you need results faster:**
- 2-3 weeks to implement
- Low risk
- Zero regex ✅
- Good UX (not perfect, but much better than current)
- Low maintenance

**User feedback:**
> "I still see that part of subquery is in outer defined object. It looks ugly because user have to define half of query in one place, and another half in another place."

**With functions:**
```kotlin
// Define structure (separate)
fun userTotalsQuery(): SubqueryBuilder<UserTotals> = subqueryBuilder {
    from(Order)
        .select { order.userName as UserTotals::userName }
        .select { sum(order.cost) as UserTotals::totalCost }
        .groupBy { order.userName }
}

// Usage with parameters (inline)
userTotalsQuery()
    .where { totalCost gt 1000 }       // ← Inline!
    .orderBy { totalCost.desc() }
    .limit(10)
```

**Is this still "ugly"?**
- Less split than objects (function is more natural than object)
- Parameters are inline (WHERE/ORDER BY at call site)
- Reusable (function can be called multiple times)

**User can decide:** Is this acceptable or do we need perfect inline UX (AST parser)?

---

## Open Questions for User

1. **Urgency vs Perfection:**
   - Need results fast? → Functions (2-3 weeks)
   - Want perfect UX? → Hybrid (6-9 weeks)

2. **Split Tolerance:**
   - Is function-based split acceptable? (better than objects)
   - Or must everything be 100% inline?

3. **Test-Driven Generation:**
   - Is discovering from test files acceptable? (like current)
   - Or should everything be explicit declarations?

4. **Development Resources:**
   - 1 developer? → Functions (simpler)
   - Team available? → Hybrid (parallel work)

5. **Maintenance Preference:**
   - Minimize maintenance? → Functions (low complexity)
   - Accept higher maintenance? → AST Parser (more power)

---

## Conclusion

**The Core Challenge:**
- KSP can't see lambda bodies
- Inline UX requires seeing lambda bodies
- Zero regex eliminates pattern matching

**Viable Solutions:**
1. **AST Parser** - Parse Kotlin code structure (not regex)
2. **Query Functions** - KSP discovers functions (moderate split)
3. **Hybrid** - Both approaches (best of both)

**My Recommendation:** Start with **Query Functions** (quick win), add **AST Parser** later if perfect inline UX is critical.

This gives:
- ✅ Zero regex
- ✅ Better UX than current objects
- ✅ Feasible implementation
- ✅ Low risk
- ✅ Path to perfection later

**Next step:** User decides which approach based on priorities (timeline vs perfection).
