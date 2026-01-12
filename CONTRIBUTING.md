# Contributing to Kodama

Thank you for your interest in contributing to Kodama! This document provides guidelines for code contributions.

## Code Quality Standards

### When to Use Regular Expressions

Kodama has specific guidelines about regex usage based on lessons learned from refactoring:

#### ✅ **Appropriate Use Cases**

1. **Pattern Discovery**: Finding usage patterns in test/source files
   ```kotlin
   // ✅ GOOD: Discovering query patterns in test files
   val queryPattern = """from\s*\(\s*([A-Z]\w+)\s*\)""".toRegex()
   ```

2. **Expression Analysis**: Parsing DSL expressions where full parser is overkill
   ```kotlin
   // ✅ GOOD: Extracting column references from simple expressions
   val columnRef = """(\w+)\.(\w+)""".toRegex()
   ```

3. **Simple Text Matching**: When alternatives are more complex
   ```kotlin
   // ✅ GOOD: Finding package declarations
   val packagePattern = """package\s+([\w.]+)""".toRegex()
   ```

#### ❌ **Inappropriate Use Cases**

1. **Structured Data Parsing**: Use proper parsers instead
   ```kotlin
   // ❌ BAD: Parsing JSON with regex
   val jsonPattern = """"field":\s*"([^"]+)"""".toRegex()

   // ✅ GOOD: Use kotlinx.serialization
   val data = json.decodeFromString<DataClass>(jsonContent)
   ```

2. **Symbol/Type Discovery**: Use KSP instead
   ```kotlin
   // ❌ BAD: Finding interfaces with regex
   val interfacePattern = """interface\s+([A-Z]\w+)""".toRegex()

   // ✅ GOOD: Use KSP to discover types
   resolver.getSymbolsWithAnnotation("@Marker")
       .filterIsInstance<KSClassDeclaration>()
   ```

3. **Case Conversion**: Use string algorithms instead
   ```kotlin
   // ❌ BAD: Regex-based case conversion
   val snakeCase = camelCase.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

   // ✅ GOOD: Use string iteration (3× faster)
   val snakeCase = camelCase.toSnakeCase()  // See StringUtils.kt
   ```

### Decision Matrix

| Use Case | Use Regex? | Alternative | Why |
|----------|-----------|-------------|-----|
| **JSON/XML Parsing** | ❌ NO | kotlinx.serialization | Type-safe, handles edge cases |
| **Symbol Discovery** | ❌ NO | KSP | Compiler-aware, accurate |
| **Package Detection** | ❌ NO | KSP metadata | Already available |
| **Case Conversion** | ❌ NO | String algorithms | 3× faster, clearer intent |
| **Pattern Mining** | ✅ YES | — | Appropriate use case |
| **Expression Analysis** | ✅ YES | — | Pragmatic (parser overkill) |
| **Text Matching** | ✅ YES | — | When alternatives are complex |

**General Rule:** Use regex for **pattern discovery**, not **structure parsing**.

## Code Generation Architecture

### Three-Phase Approach

1. **Phase 1: KSP Discovery**
   - Discovers table definitions at compile-time
   - Outputs metadata to JSON
   - Use KSP for type/symbol discovery

2. **Phase 2: Runtime Reflection**
   - Extracts column metadata from compiled code
   - Accesses DSL results via reflection
   - Complements KSP with runtime information

3. **Phase 3: Pattern Scanning**
   - Discovers usage patterns in test files
   - Generates only needed combinations
   - Use regex for pattern discovery (appropriate)

### Hybrid KSP + Regex Approach

For new discovery features:

1. **Primary**: Use KSP for compile-time discovery
   - More reliable (compiler-aware)
   - Type-safe
   - Better error messages

2. **Fallback**: Keep regex for test sources
   - Handles test-only declarations
   - Backward compatibility
   - Flexibility

Example: Marker interface discovery uses KSP-first with regex fallback.

## Testing Standards

### Unit Tests for Utilities

All utility functions must have comprehensive unit tests:

```kotlin
// Example: StringUtils has 20+ test cases
class StringUtilsTest {
    @Test
    fun `toSnakeCase handles simple camelCase`() {
        assertEquals("my_property", "myProperty".toSnakeCase())
    }

    @Test
    fun `toSnakeCase handles edge cases`() {
        assertEquals("", "".toSnakeCase())
        assertEquals("a", "A".toSnakeCase())
    }

    @Test
    fun `performance comparison with regex`() {
        // Document performance benefits
    }
}
```

### Integration Tests

- Place in `kodama-tests/src/test/kotlin/`
- Test real query building scenarios
- Verify generated code compiles and runs

## Performance Considerations

### Prefer String Algorithms over Regex

String iteration is typically 3× faster than regex for simple transformations:

```kotlin
// String iteration: ~3× faster
fun String.toSnakeCase(): String {
    return buildString(length + 5) {
        this@toSnakeCase.forEachIndexed { index, char ->
            when {
                index == 0 -> append(char.lowercaseChar())
                char.isUpperCase() -> {
                    append('_')
                    append(char.lowercaseChar())
                }
                else -> append(char)
            }
        }
    }
}
```

### Use Industry-Standard Libraries

- **JSON**: `kotlinx.serialization` (not regex)
- **XML**: Proper XML parsers (not regex)
- **AST**: KSP (not regex)

## Documentation Standards

### Inline Comments for Regex

All regex patterns should have explanatory comments:

```kotlin
// === REGEX USAGE: Pattern Discovery ===
// Justified because: Finding usage patterns in test files
// Alternative: Full AST parsing would be overkill
val queryPattern = """from\s*\(\s*([A-Z]\w+)\s*\)""".toRegex()
```

### Document Why, Not What

```kotlin
// ❌ BAD: What it does
// This function converts to snake case

// ✅ GOOD: Why it exists
// Eliminates regex for case conversion (~3× faster, clearer intent)
fun String.toSnakeCase()
```

## Pull Request Guidelines

1. **Run all tests**: `./gradlew test`
2. **Regenerate code**: `./gradlew generateKodamaExtensions`
3. **Add tests**: For new utility functions
4. **Update docs**: CLAUDE.md if architecture changes
5. **Follow guidelines**: Regex usage, performance, testing

## Questions?

- Check `CLAUDE.md` for project context
- Check `REGEX_ELIMINATION_PLAN.md` for refactoring rationale
- Open an issue for clarification

## Recent Improvements (January 2026)

- ✅ Reduced regex usage from 43 to ~35 patterns (-19%)
- ✅ Added StringUtils with 3× faster case conversion
- ✅ Replaced regex JSON parsing with kotlinx.serialization
- ✅ Added KSP-based marker discovery with @Marker annotation
- ✅ Comprehensive test coverage for utilities

See `REGEX_ELIMINATION_PLAN.md` for full details.
