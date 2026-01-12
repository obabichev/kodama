# AST Parser Implementation Plan

**Date:** January 12, 2026
**Goal:** Replace 41 regex patterns with Kotlin AST parsing for zero-regex query discovery

---

## Overview

Replace regex-based pattern discovery with structured Kotlin AST parsing. This enables:
- ✅ Zero regex patterns
- ✅ Perfect inline UX (no split between structure and usage)
- ✅ Full query discovery from test files
- ✅ Support for nested subqueries
- ✅ Parameters inline (WHERE, ORDER BY, LIMIT)

**Key Difference from Regex:**
```
Regex:     "from\\(([A-Z]\\w+)\\)" → fragile text pattern matching
AST Parse: KtCallExpression("from", args=[KtClassLiteral(Person)]) → structured tree traversal
```

---

## Architecture

### High-Level Flow

```
Test Files (*.kt)
    ↓
Kotlin PSI Parser
    ↓
AST Tree Structure
    ↓
Query Discovery Walker
    ↓
Extracted Query Patterns
    ↓
Code Generator (existing)
    ↓
Generated Extensions
```

### Components

1. **KotlinASTParser** - Parse Kotlin files into PSI trees
2. **QueryDiscoveryVisitor** - Walk AST to find query patterns
3. **QueryStructureExtractor** - Extract query details from AST nodes
4. **TableCombinationBuilder** - Build combinations from discovered patterns
5. **CodeGenerator** - Generate extensions (existing, enhanced)

---

## Kotlin PSI Primer

### What is PSI?

PSI (Program Structure Interface) is Kotlin compiler's internal representation of code structure.

**Example Code:**
```kotlin
from(Person)
    .join(Order) { order.userName eq person.name }
    .selectAll(Person)
```

**PSI Tree:**
```
KtCallExpression [selectAll]
  ├─ KtQualifiedExpression [receiver]
  │   └─ KtCallExpression [join]
  │       ├─ KtQualifiedExpression [receiver]
  │       │   └─ KtCallExpression [from]
  │       │       └─ KtNameReferenceExpression [Person]
  │       ├─ KtNameReferenceExpression [Order]
  │       └─ KtLambdaExpression [condition]
  └─ KtNameReferenceExpression [Person]
```

### Key PSI Types

```kotlin
// Function calls: from(Person), join(Order), select { ... }
KtCallExpression

// Qualified expressions: person.name, order.userName
KtQualifiedExpression

// Lambda expressions: { order.userName eq person.name }
KtLambdaExpression

// Name references: Person, Order, person, order
KtNameReferenceExpression

// Binary expressions: order.userName eq person.name
KtBinaryExpression
```

---

## Implementation Details

### Phase 1: Add Kotlin Compiler Dependency

**File:** `kodama-compiler-plugin/build.gradle.kts`

```kotlin
dependencies {
    // Existing dependencies
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
    implementation("com.squareup:kotlinpoet:$kotlinPoetVersion")

    // NEW: Kotlin compiler for PSI/AST parsing
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

    // Testing
    testImplementation(kotlin("test"))
}
```

**Why `kotlin-compiler-embeddable`?**
- Contains PSI/AST classes
- Designed for embedding in tools
- Already on classpath (KSP uses it)

---

### Phase 2: Implement KotlinASTParser

**File:** `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/parser/KotlinASTParser.kt`

```kotlin
package com.obabichev.kodama.compiler.parser

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Parses Kotlin source files into PSI trees for AST analysis.
 *
 * This replaces regex-based pattern matching with structured AST traversal.
 */
class KotlinASTParser {

    private val disposable = Disposer.newDisposable()
    private val environment: KotlinCoreEnvironment

    init {
        val configuration = CompilerConfiguration()
        environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    /**
     * Parse a Kotlin file into a PSI tree.
     *
     * @param file The Kotlin source file to parse
     * @return PSI tree root (KtFile)
     */
    fun parse(file: File): KtFile {
        val psiManager = PsiManager.getInstance(environment.project)
        val virtualFile = environment.createVirtualFile(file)

        return psiManager.findFile(virtualFile) as? KtFile
            ?: error("Failed to parse file: ${file.absolutePath}")
    }

    /**
     * Parse Kotlin source text directly.
     *
     * @param sourceText Kotlin source code as string
     * @param fileName Virtual file name for error messages
     * @return PSI tree root (KtFile)
     */
    fun parseText(sourceText: String, fileName: String = "temp.kt"): KtFile {
        return environment.createKtFile(fileName, sourceText)
    }

    /**
     * Clean up resources.
     */
    fun dispose() {
        Disposer.dispose(disposable)
    }

    private fun KotlinCoreEnvironment.createVirtualFile(file: File): com.intellij.openapi.vfs.VirtualFile {
        val localFileSystem = com.intellij.openapi.vfs.VirtualFileManager
            .getInstance()
            .getFileSystem("file")

        return localFileSystem.findFileByPath(file.absolutePath)
            ?: error("File not found: ${file.absolutePath}")
    }

    private fun KotlinCoreEnvironment.createKtFile(fileName: String, text: String): KtFile {
        return org.jetbrains.kotlin.psi.KtPsiFactory(project).createFile(fileName, text)
    }
}
```

---

### Phase 3: Implement QueryDiscoveryVisitor

**File:** `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/parser/QueryDiscoveryVisitor.kt`

```kotlin
package com.obabichev.kodama.compiler.parser

import org.jetbrains.kotlin.psi.*

/**
 * Walks Kotlin PSI tree to discover query patterns.
 *
 * Finds:
 * - from() calls (query entry points)
 * - join() calls (table combinations)
 * - select()/selectAll() calls (column selections)
 * - Subquery patterns (inline subqueries)
 */
class QueryDiscoveryVisitor : KtTreeVisitorVoid() {

    private val _discoveredQueries = mutableListOf<QueryPattern>()
    val discoveredQueries: List<QueryPattern> get() = _discoveredQueries

    override fun visitCallExpression(call: KtCallExpression) {
        // Check if this is a from() call
        val callName = call.calleeExpression?.text

        when (callName) {
            "from" -> {
                // This is a query entry point
                val query = extractQueryPattern(call)
                if (query != null) {
                    _discoveredQueries.add(query)
                }
            }
        }

        // Continue traversing
        super.visitCallExpression(call)
    }

    /**
     * Extract full query pattern from a from() call.
     *
     * Walks the method chain: from().join().select().where()...
     */
    private fun extractQueryPattern(fromCall: KtCallExpression): QueryPattern? {
        val operations = mutableListOf<QueryOperation>()

        // Extract base table from from(Person)
        val baseTable = extractTableArgument(fromCall)
            ?: return null

        operations.add(QueryOperation(
            type = OperationType.FROM,
            table = baseTable,
            sourceNode = fromCall
        ))

        // Walk the chain to find all operations
        var current = findParentQualifiedExpression(fromCall)
        while (current != null) {
            val selector = current.selectorExpression as? KtCallExpression
            if (selector != null) {
                val operation = extractOperation(selector)
                if (operation != null) {
                    operations.add(operation)
                }
            }

            current = findParentQualifiedExpression(current)
        }

        return QueryPattern(
            baseTable = baseTable,
            operations = operations
        )
    }

    /**
     * Extract operation from a call like join(), select(), where(), etc.
     */
    private fun extractOperation(call: KtCallExpression): QueryOperation? {
        val operationName = call.calleeExpression?.text ?: return null

        return when (operationName) {
            "join", "leftJoin", "rightJoin" -> {
                val table = extractTableArgument(call)
                val condition = extractLambdaArgument(call)

                QueryOperation(
                    type = OperationType.JOIN,
                    table = table,
                    joinType = operationName.toJoinType(),
                    condition = condition,
                    sourceNode = call
                )
            }

            "joinAliased" -> {
                // Subquery join: .joinAliased(subquery) { condition }
                val subquery = extractSubqueryArgument(call)
                val condition = extractLambdaArgument(call)

                QueryOperation(
                    type = OperationType.JOIN_SUBQUERY,
                    subquery = subquery,
                    condition = condition,
                    sourceNode = call
                )
            }

            "select" -> {
                val lambda = extractLambdaArgument(call)

                QueryOperation(
                    type = OperationType.SELECT,
                    lambda = lambda,
                    sourceNode = call
                )
            }

            "selectAll" -> {
                val table = extractTableArgument(call)

                QueryOperation(
                    type = OperationType.SELECT_ALL,
                    table = table,
                    sourceNode = call
                )
            }

            "selectAliased" -> {
                val marker = extractTypeArgument(call) // TotalRevenue
                val lambda = extractLambdaArgument(call)

                QueryOperation(
                    type = OperationType.SELECT_ALIASED,
                    marker = marker,
                    lambda = lambda,
                    sourceNode = call
                )
            }

            "where" -> {
                val condition = extractLambdaArgument(call)

                QueryOperation(
                    type = OperationType.WHERE,
                    condition = condition,
                    sourceNode = call
                )
            }

            "groupBy" -> {
                val lambda = extractLambdaArgument(call)

                QueryOperation(
                    type = OperationType.GROUP_BY,
                    lambda = lambda,
                    sourceNode = call
                )
            }

            "orderBy" -> {
                val lambda = extractLambdaArgument(call)

                QueryOperation(
                    type = OperationType.ORDER_BY,
                    lambda = lambda,
                    sourceNode = call
                )
            }

            "limit" -> {
                val value = extractIntArgument(call)

                QueryOperation(
                    type = OperationType.LIMIT,
                    intValue = value,
                    sourceNode = call
                )
            }

            "offset" -> {
                val value = extractIntArgument(call)

                QueryOperation(
                    type = OperationType.OFFSET,
                    intValue = value,
                    sourceNode = call
                )
            }

            else -> null
        }
    }

    /**
     * Extract table name from from(Person) or join(Order).
     */
    private fun extractTableArgument(call: KtCallExpression): String? {
        val arg = call.valueArguments.firstOrNull() ?: return null
        return (arg.getArgumentExpression() as? KtNameReferenceExpression)?.text
    }

    /**
     * Extract type argument from selectAliased<TotalRevenue>.
     */
    private fun extractTypeArgument(call: KtCallExpression): String? {
        val typeArgs = call.typeArguments
        return typeArgs.firstOrNull()?.typeReference?.text
    }

    /**
     * Extract lambda from { order.userName eq person.name }.
     */
    private fun extractLambdaArgument(call: KtCallExpression): LambdaExpression? {
        val lambda = call.lambdaArguments.firstOrNull()?.getLambdaExpression()
            ?: call.valueArguments.lastOrNull()?.getArgumentExpression() as? KtLambdaExpression
            ?: return null

        return LambdaExpression(
            parameters = lambda.valueParameters.map { it.text },
            body = lambda.bodyExpression?.text ?: "",
            sourceNode = lambda
        )
    }

    /**
     * Extract integer argument from limit(10) or offset(5).
     */
    private fun extractIntArgument(call: KtCallExpression): Int? {
        val arg = call.valueArguments.firstOrNull()?.getArgumentExpression()
        return when (arg) {
            is KtConstantExpression -> arg.text.toIntOrNull()
            else -> null
        }
    }

    /**
     * Extract subquery from inline definition.
     *
     * Pattern: from(Order).select{...}.build().aliasAs<UserTotals>()
     */
    private fun extractSubqueryArgument(call: KtCallExpression): SubqueryPattern? {
        val arg = call.valueArguments.firstOrNull()?.getArgumentExpression()
            ?: return null

        // Check if it's a subquery pattern (ends with .aliasAs<T>())
        if (arg is KtQualifiedExpression) {
            val selector = arg.selectorExpression as? KtCallExpression
            if (selector?.calleeExpression?.text == "aliasAs") {
                // Extract type argument: aliasAs<UserTotals>()
                val typeArg = selector.typeArguments.firstOrNull()?.typeReference?.text

                // Extract the query chain before .aliasAs()
                val queryChain = arg.receiverExpression as? KtQualifiedExpression

                if (queryChain != null && typeArg != null) {
                    // Recursively extract subquery operations
                    val subqueryOps = extractSubqueryOperations(queryChain)

                    return SubqueryPattern(
                        alias = typeArg,
                        operations = subqueryOps,
                        sourceNode = arg
                    )
                }
            }
        }

        return null
    }

    /**
     * Extract operations from subquery chain.
     */
    private fun extractSubqueryOperations(expr: KtExpression): List<QueryOperation> {
        val operations = mutableListOf<QueryOperation>()

        var current = expr
        while (current is KtQualifiedExpression) {
            val selector = current.selectorExpression as? KtCallExpression
            if (selector != null) {
                val op = extractOperation(selector)
                if (op != null) {
                    operations.add(0, op) // Prepend to maintain order
                }
            }

            current = current.receiverExpression
        }

        return operations
    }

    /**
     * Find parent qualified expression for method chaining.
     */
    private fun findParentQualifiedExpression(element: KtElement): KtQualifiedExpression? {
        var parent = element.parent
        while (parent != null) {
            if (parent is KtQualifiedExpression && parent.receiverExpression == element) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    private fun String.toJoinType(): JoinType {
        return when (this) {
            "join" -> JoinType.INNER
            "leftJoin" -> JoinType.LEFT
            "rightJoin" -> JoinType.RIGHT
            else -> JoinType.INNER
        }
    }
}

// Data classes for discovered patterns

data class QueryPattern(
    val baseTable: String,
    val operations: List<QueryOperation>
) {
    /**
     * Get all tables involved in this query.
     */
    fun getTables(): List<String> {
        return listOf(baseTable) + operations.mapNotNull { it.table }
    }

    /**
     * Check if query contains subqueries.
     */
    fun hasSubqueries(): Boolean {
        return operations.any { it.type == OperationType.JOIN_SUBQUERY }
    }

    /**
     * Get all subqueries.
     */
    fun getSubqueries(): List<SubqueryPattern> {
        return operations.mapNotNull { it.subquery }
    }
}

data class QueryOperation(
    val type: OperationType,
    val table: String? = null,
    val joinType: JoinType? = null,
    val condition: LambdaExpression? = null,
    val lambda: LambdaExpression? = null,
    val marker: String? = null,
    val intValue: Int? = null,
    val subquery: SubqueryPattern? = null,
    val sourceNode: KtElement
)

data class LambdaExpression(
    val parameters: List<String>,
    val body: String,
    val sourceNode: KtLambdaExpression
)

data class SubqueryPattern(
    val alias: String,
    val operations: List<QueryOperation>,
    val sourceNode: KtElement
) {
    /**
     * Get base table of subquery.
     */
    fun getBaseTable(): String? {
        return operations.firstOrNull { it.type == OperationType.FROM }?.table
    }

    /**
     * Get all tables in subquery.
     */
    fun getTables(): List<String> {
        return operations.mapNotNull { it.table }
    }
}

enum class OperationType {
    FROM,
    JOIN,
    JOIN_SUBQUERY,
    SELECT,
    SELECT_ALL,
    SELECT_ALIASED,
    WHERE,
    GROUP_BY,
    ORDER_BY,
    LIMIT,
    OFFSET
}

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL
}
```

---

### Phase 4: Integrate with GenerateQueryExtensionsTask

**File:** `kodama-compiler-plugin/src/main/kotlin/com/obabichev/kodama/compiler/GenerateQueryExtensionsTask.kt`

**Changes:**

```kotlin
// BEFORE: Regex-based discovery
private fun discoverTableCombinations(testFiles: List<File>): Set<String> {
    val combinations = mutableSetOf<String>()

    testFiles.forEach { file ->
        val content = file.readText()

        // 41 regex patterns here...
        val fromPattern = """from\(([A-Z]\w+)\)""".toRegex()
        val joinPattern = """\.join\(([A-Z]\w+)\)""".toRegex()
        // ... more patterns
    }

    return combinations
}

// AFTER: AST-based discovery
private fun discoverTableCombinations(testFiles: List<File>): Set<String> {
    val parser = KotlinASTParser()
    val visitor = QueryDiscoveryVisitor()

    try {
        testFiles.forEach { file ->
            if (file.extension == "kt") {
                val ktFile = parser.parse(file)
                ktFile.accept(visitor)
            }
        }

        // Extract combinations from discovered queries
        val combinations = visitor.discoveredQueries.flatMap { query ->
            buildCombinations(query)
        }.toSet()

        logger.lifecycle("✅ AST Parser discovered ${visitor.discoveredQueries.size} queries")
        logger.lifecycle("✅ Generated ${combinations.size} table combinations")

        return combinations

    } finally {
        parser.dispose()
    }
}

private fun buildCombinations(query: QueryPattern): List<String> {
    val tables = query.getTables()
    val combinations = mutableListOf<String>()

    // Generate all prefixes: [Person], [Person, Order], [Person, Order, Profile]
    for (i in 1..tables.size) {
        combinations.add(tables.take(i).joinToString("_"))
    }

    // Add subquery combinations
    query.getSubqueries().forEach { subquery ->
        val subqueryTables = tables + subquery.alias
        for (i in 1..subqueryTables.size) {
            combinations.add(subqueryTables.take(i).joinToString("_"))
        }
    }

    return combinations
}
```

---

### Phase 5: Enhanced Code Generation

**Handle Subqueries:**

```kotlin
// Generate marker interface for subqueries
private fun generateSubqueryMarker(subquery: SubqueryPattern): FileSpec {
    return FileSpec.builder(generatedPackage, subquery.alias)
        .addType(
            TypeSpec.interfaceBuilder(subquery.alias)
                .addKdoc("Marker interface for subquery: ${subquery.alias}")
                .apply {
                    // Generate column properties based on subquery selections
                    subquery.operations
                        .filter { it.type == OperationType.SELECT_ALIASED }
                        .forEach { op ->
                            addProperty(
                                PropertySpec.builder(
                                    op.marker!!.decapitalize(),
                                    inferType(op.lambda)
                                ).build()
                            )
                        }
                }
                .build()
        )
        .build()
}

// Generate subquery accessor
private fun generateSubqueryAccessor(subquery: SubqueryPattern): FileSpec {
    return FileSpec.builder(generatedPackage, "${subquery.alias}Accessor")
        .addType(
            TypeSpec.classBuilder("${subquery.alias}Accessor")
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("subquery", ClassName(generatedPackage, subquery.alias))
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("subquery", ClassName(generatedPackage, subquery.alias))
                        .initializer("subquery")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )
                .apply {
                    // Generate column accessors
                    subquery.operations
                        .filter { it.type == OperationType.SELECT_ALIASED }
                        .forEach { op ->
                            addProperty(
                                PropertySpec.builder(
                                    op.marker!!.decapitalize(),
                                    TypedColumn::class.asTypeName()
                                )
                                .getter(
                                    FunSpec.getterBuilder()
                                        .addStatement("return TypedColumn(\"${op.marker}\")")
                                        .build()
                                )
                                .build()
                            )
                        }
                }
                .build()
        )
        .build()
}
```

---

## Testing Strategy

### Unit Tests

**File:** `kodama-compiler-plugin/src/test/kotlin/com/obabichev/kodama/compiler/parser/KotlinASTParserTest.kt`

```kotlin
class KotlinASTParserTest {

    @Test
    fun `test parse simple query`() {
        val source = """
            package test

            fun testQuery() {
                from(Person)
                    .selectAll(Person)
            }
        """.trimIndent()

        val parser = KotlinASTParser()
        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        assertEquals(1, visitor.discoveredQueries.size)
        val query = visitor.discoveredQueries.first()
        assertEquals("Person", query.baseTable)
        assertEquals(2, query.operations.size) // FROM + SELECT_ALL

        parser.dispose()
    }

    @Test
    fun `test parse query with join`() {
        val source = """
            from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
        """.trimIndent()

        val parser = KotlinASTParser()
        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        assertEquals(listOf("Person", "Order"), query.getTables())

        val joinOp = query.operations.find { it.type == OperationType.JOIN }
        assertNotNull(joinOp)
        assertEquals("Order", joinOp!!.table)
        assertEquals(JoinType.INNER, joinOp.joinType)

        parser.dispose()
    }

    @Test
    fun `test parse query with inline subquery`() {
        val source = """
            from(Person)
                .joinAliased(
                    from(Order)
                        .selectAliased(OrderUserName) { order.userName }
                        .selectAliased(TotalCost) { sum(order.cost) }
                        .groupBy { order.userName }
                        .build()
                        .aliasAs<UserTotalSubquery>()
                ) { userTotalSubquery.orderUserName eq person.name }
                .selectAll(Person)
        """.trimIndent()

        val parser = KotlinASTParser()
        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        assertTrue(query.hasSubqueries())

        val subqueries = query.getSubqueries()
        assertEquals(1, subqueries.size)
        assertEquals("UserTotalSubquery", subqueries.first().alias)

        parser.dispose()
    }
}
```

---

## Migration Path

### Step 1: Parallel Processing (Low Risk)

```kotlin
class GenerateQueryExtensionsTask : DefaultTask() {

    @TaskAction
    fun generate() {
        // PHASE 1: Both systems running
        val regexCombinations = discoverViaTables()
        val astCombinations = discoverViaAST(testFiles)

        // Compare outputs
        logger.lifecycle("Regex discovered: ${regexCombinations.size}")
        logger.lifecycle("AST discovered: ${astCombinations.size}")

        val onlyRegex = regexCombinations - astCombinations
        val onlyAST = astCombinations - regexCombinations

        if (onlyRegex.isNotEmpty()) {
            logger.warn("⚠️ Regex found but AST missed: $onlyRegex")
        }
        if (onlyAST.isNotEmpty()) {
            logger.lifecycle("✅ AST found new: $onlyAST")
        }

        // Use AST results (new system)
        val finalCombinations = astCombinations
        generateCode(finalCombinations)
    }
}
```

**Timeline:** Week 1-2 (validation phase)

### Step 2: Remove Regex (After Validation)

```kotlin
// Delete regex-based discovery functions
// Delete 41 regex patterns
// Keep only AST-based discovery
```

**Timeline:** Week 3 (after confirming AST discovers everything)

---

## Expected Impact

### Code Size Reduction

**Current (regex-based):**
- 2.3MB generated code
- 22 files
- 41 regex patterns

**After (AST-based):**
- ~86KB generated code (97% reduction)
- ~40 files (cleaner structure)
- 0 regex patterns (100% elimination)

### Build Time

**Current:** ~10 seconds
**After:** ~3 seconds (70% faster)

**Why faster?**
- Generate only valid combinations (relationship-based)
- No duplicate generation paths
- Smaller code to compile

### Maintainability

**Current:**
- Fragile (formatting breaks regex)
- Unpredictable (depends on test file structure)
- Hard to debug (41 patterns)

**After:**
- Robust (structured parsing)
- Predictable (explicit AST traversal)
- Easy to debug (clear AST structure)

---

## Timeline

**Week 1: Foundation**
- ✅ Add Kotlin compiler dependency
- ✅ Implement KotlinASTParser
- ✅ Unit tests for parser

**Week 2: Discovery**
- ✅ Implement QueryDiscoveryVisitor
- ✅ Extract query patterns from AST
- ✅ Unit tests for visitor

**Week 3: Integration**
- ✅ Integrate with GenerateQueryExtensionsTask
- ✅ Parallel processing (regex + AST)
- ✅ Validation and comparison

**Week 4: Completion**
- ✅ Remove regex patterns
- ✅ Enhance code generation for subqueries
- ✅ End-to-end tests
- ✅ Documentation

**Total: 3-4 weeks**

---

## Risk Mitigation

### Risk 1: AST Parser Missing Patterns

**Mitigation:** Parallel processing with comparison

```kotlin
val onlyRegex = regexCombinations - astCombinations
if (onlyRegex.isNotEmpty()) {
    logger.error("❌ AST missed: $onlyRegex")
    throw GradleException("AST parser incomplete")
}
```

### Risk 2: Kotlin Compiler Dependency Size

**Mitigation:** `kotlin-compiler-embeddable` is already on classpath (KSP uses it)

### Risk 3: PSI API Changes

**Mitigation:**
- Use stable PSI APIs only
- Pin Kotlin version in build
- Test with multiple Kotlin versions

---

## Next Steps

1. **Approve this plan** - Confirm approach is acceptable
2. **Start implementation** - Begin with Phase 1 (add dependency)
3. **Iterative testing** - Test each phase before proceeding
4. **Validation** - Run parallel processing to validate
5. **Migration** - Remove regex after validation

---

## Questions?

- Should we proceed with this implementation?
- Any specific concerns about PSI/AST approach?
- Prefer different timeline/phases?
