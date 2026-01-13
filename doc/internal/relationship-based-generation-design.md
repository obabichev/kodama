# Relationship-Based Code Generation Design

**Status:** Design Phase
**Date:** January 12, 2026
**Goal:** Eliminate 41 regex patterns by generating code purely from declared relationships

---

## Current State Analysis

### Hybrid Approach (Current)

**Phase 1: Regex Scanning** (lines 400-1200 in GenerateQueryExtensionsTask.kt)
```kotlin
// Scan test files with 41 regex patterns
testKtFiles.forEach { file ->
    val content = file.readText()

    // Find: from(Person).join(Order).selectAs(TotalRevenue) { ... }
    val pattern = """from\s*\(\s*([A-Z]\w+)\s*\)...""".toRegex()

    // Extract:
    // - queryCombinations: [Person], [Person, Order], [Person, Order, Profile]
    // - markerTypes: TotalRevenue → Int
    // - selectionPatterns: which columns are selected
}
```

**Phase 2: Relationship Augmentation** (lines 1258-1329)
```kotlin
// Add MORE combinations from relationships
relationshipMetadata.relationships.forEach { (from, to) ->
    val combination = listOf(from, to)
    if (!existingCombinations.contains(combination)) {
        queryCombinations.add(combination)  // Add to regex-discovered combinations
    }
}
```

### Output: 2.3MB generated code
- queryCombinations: ~33 combinations (some from regex, some from relationships)
- PhantomTypes.kt: 1.4MB (29,323 lines)
- 22 generated files total

---

## Pure Relationship-Based Approach

### Input: relationships.json (8 relationships)
```json
{
  "package": "com.obabichev.kodama.tests.schema",
  "relationships": [
    {"from": "Person", "to": "Order"},
    {"from": "Person", "to": "Profile"},
    {"from": "Order", "to": "Person"},
    {"from": "Order", "to": "Company"},
    {"from": "Profile", "to": "Person"},
    {"from": "Company", "to": "Order"},
    {"from": "TradingStrategy", "to": "MarketData"},
    {"from": "MarketData", "to": "TradingStrategy"}
  ]
}
```

### Algorithm: Generate ALL Valid Combinations

```kotlin
/**
 * Pure relationship-based combination generator.
 * No regex scanning - generates all valid combinations from relationships.
 */
class RelationshipBasedCombinationGenerator(
    val tables: List<String>,
    val relationships: List<Pair<String, String>>
) {

    /**
     * Generate all valid table combinations up to maxDepth.
     *
     * Strategy:
     * 1. Start with each table as a 1-table combination
     * 2. For each N-table combination, try adding reachable tables to create N+1 combinations
     * 3. Stop at maxDepth (e.g., 3 tables)
     *
     * Example:
     * - Depth 1: [Person], [Order], [Profile], [Company]
     * - Depth 2: [Person, Order], [Person, Profile], [Order, Company], ...
     * - Depth 3: [Person, Order, Company], [Person, Order, Profile], ...
     */
    fun generateAllCombinations(maxDepth: Int = 3): List<List<String>> {
        val result = mutableListOf<List<String>>()

        // Depth 1: Each table standalone
        tables.forEach { table ->
            result.add(listOf(table))
        }

        // Build relationship graph for fast lookup
        val graph = buildRelationshipGraph()

        // Depth 2+: Expand combinations
        var currentDepth = result.toList()
        repeat(maxDepth - 1) {
            val nextDepth = mutableSetOf<List<String>>()

            currentDepth.forEach { combination ->
                // Try adding each reachable table
                val reachable = getReachableTables(combination, graph)
                reachable.forEach { nextTable ->
                    if (!combination.contains(nextTable)) {
                        val expanded = combination + nextTable
                        nextDepth.add(expanded)
                    }
                }
            }

            result.addAll(nextDepth)
            currentDepth = nextDepth.toList()
        }

        return result
    }

    private fun buildRelationshipGraph(): Map<String, Set<String>> {
        return relationships
            .groupBy { it.first }
            .mapValues { (_, rels) -> rels.map { it.second }.toSet() }
    }

    private fun getReachableTables(
        combination: List<String>,
        graph: Map<String, Set<String>>
    ): Set<String> {
        return combination.flatMap { table ->
            graph[table] ?: emptySet()
        }.toSet()
    }
}
```

### Expected Output

**For 17 tables + 8 relationships:**

Depth 1 (single tables): 17 combinations
- Person, Order, Profile, Company, Product, Settings, Numerics, ...

Depth 2 (valid 2-table joins): ~20-30 combinations
- Person + Order (via Person.orders)
- Person + Profile (via Person.profile)
- Order + Person (via Order.person)
- Order + Company (via Order.company)
- ...

Depth 3 (valid 3-table joins): ~10-20 combinations
- Person + Order + Company (Person → Order → Company)
- Person + Order + Profile (Person → Order, Person → Profile)
- ...

**Total:** ~50-70 combinations (vs current 33)

---

## What We Eliminate

### Regex Patterns Eliminated (41 total)

1. **Query chain discovery** (8 patterns)
   ```kotlin
   // BEFORE
   val queryPattern = """from\s*\(\s*([A-Z]\w+)\s*\)...""".toRegex()

   // AFTER
   // Not needed - all combinations generated from relationships
   ```

2. **Join pattern extraction** (6 patterns)
   ```kotlin
   // BEFORE
   val joinPattern = """\.(?:join|leftJoin)\s*\(\s*([A-Z]\w+)""".toRegex()

   // AFTER
   // Not needed - relationships tell us valid joins
   ```

3. **Selection pattern discovery** (12 patterns)
   ```kotlin
   // BEFORE
   val selectPattern = """\.select\s*\{\s*(\w+)\.(\w+)\s*\}""".toRegex()

   // AFTER
   // Generate ALL possible selections for each combination
   ```

4. **Marker inference** (8 patterns)
   ```kotlin
   // BEFORE
   val markerPattern = """\.selectAs\(([A-Z]\w+)\)""".toRegex()

   // AFTER
   // No marker inference needed - users define markers explicitly
   ```

5. **Subquery discovery** (7 patterns)
   ```kotlin
   // BEFORE
   val subqueryPattern = """fromAliased\([A-Z]\w+\)\s*\{""".toRegex()

   // AFTER
   // Subqueries handled separately (or not generated at all initially)
   ```

---

## Benefits

### 1. Code Size Reduction

**Current:**
- PhantomTypes.kt: 1.4MB (29,323 lines)
- Total: 2.3MB across 22 files

**Estimated with Pure Approach:**
- PhantomTypes.kt: ~50KB (~1,500 lines)
- Total: ~200KB across 25 files

**Reduction:** **91% smaller** (2.3MB → 200KB)

**Why smaller?**
- No duplicate code for "discovered" vs "relationship-based" combinations
- Minimal, focused generation for each valid combination
- No over-generation for patterns that tests don't actually use

### 2. Build Time Improvement

**Current:**
- Regex scanning: ~3-4 seconds
- Code generation: ~4-5 seconds
- Total: ~8-10 seconds

**Estimated:**
- Combination computation: ~0.5 seconds
- Code generation: ~2-3 seconds
- Total: ~3-4 seconds

**Improvement:** **60-70% faster**

### 3. Zero Regex Patterns

**Current:** 41 regex patterns
**After:** 0 regex patterns
**Reduction:** 100%

### 4. Deterministic Generation

**Current:** Changes when test patterns change (fragile)
**After:** Changes only when relationships change (stable)
**Benefit:** Predictable, cache-friendly builds

---

## Tradeoffs

### What We Lose

1. **Usage-Based Optimization**
   - Current: Only generates code for patterns found in tests
   - New: Generates code for ALL valid combinations
   - Impact: Slightly more generated code (50-70 combos vs 33)
   - Mitigation: Still WAY less than current 2.3MB

2. **Automatic Marker Discovery**
   - Current: Discovers `TotalRevenue` marker from `.selectAs(TotalRevenue)`
   - New: All markers must be explicitly defined
   - Impact: Users define markers upfront (2 lines per marker)
   - Mitigation: Already have `@Marker` annotation system

### What We Gain

✅ **Zero regex fragility** - no whitespace/formatting issues
✅ **Faster builds** - no file scanning overhead
✅ **Smaller code** - 91% reduction in generated code
✅ **Predictable** - same relationships → same output
✅ **Cacheable** - deterministic generation enables better caching
✅ **Type-safe** - compile-time validation of joins

---

## Implementation Plan

### Phase 1: Proof-of-Concept (1 week)

**Goal:** Validate the approach with minimal risk

1. Create `RelationshipBasedCombinationGenerator.kt`
2. Implement combination algorithm
3. Generate code for 2-3 table combinations
4. Compare output with current generator
5. Measure: code size, correctness, build time

**Deliverables:**
- Working generator that produces valid Kotlin code
- Size comparison report
- Build time benchmark

### Phase 2: Full Implementation (2 weeks)

**Goal:** Replace regex-based generator

1. Extend generator to all tables
2. Handle all join types (INNER, LEFT, RIGHT, FULL)
3. Generate selection contexts
4. Generate result classes
5. Update all tests

**Deliverables:**
- Complete replacement for GenerateQueryExtensionsTask.kt
- All 224 tests passing
- Documentation updates

### Phase 3: Migration & Cleanup (1 week)

**Goal:** Clean up old code, optimize

1. Delete 41 regex patterns
2. Remove regex scanning logic (800+ lines)
3. Optimize generated code size
4. Add build caching improvements
5. Update migration guide

**Deliverables:**
- Cleaned up codebase (-1000 lines)
- Migration documentation
- v2.0 release

**Total Timeline: 4 weeks**

---

## Next Steps

1. ✅ Analyze current generation structure
2. ✅ Understand relationships.json format
3. ✅ Design pure relationship algorithm
4. ⏭️ Create RelationshipBasedCombinationGenerator.kt
5. ⏭️ Generate proof-of-concept output
6. ⏭️ Compare with current generator

**Ready to start implementation?** → Create `RelationshipBasedCombinationGenerator.kt`
