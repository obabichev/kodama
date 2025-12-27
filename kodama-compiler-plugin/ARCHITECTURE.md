# Kodama Compiler Plugin - Architecture

## Overview

This document describes the modular architecture implemented for the Kodama compiler plugin's code generation system.

## Current Status

**Architecture Phase: Partially Complete**

The new architecture has been designed and core components have been implemented with unit tests demonstrating testability. However, the architecture is not yet fully integrated because some generators are still missing.

### ✅ Completed Components

1. **Core Architecture** - Layered design with clear separation of concerns
2. **Domain Models** - Pure data classes representing all generation targets
3. **Generator Infrastructure** - Base interfaces and patterns
4. **Implemented Generators:**
   - TypeAliasGenerator - Generates type aliases and markers
   - ColumnMarkerGenerator - Generates column marker interfaces
   - TableMarkerGenerator - Generates table marker interfaces
   - AllMarkerGenerator - Generates AllMarker interface
   - TableAccessorGenerator - Generates table accessor classes
   - TableOrderByAccessorGenerator - Generates ORDER BY accessors
   - InsertMethodGenerator - Generates INSERT extension methods
   - KodamaFileGenerator - Facade orchestrating all generators
5. **Builder Infrastructure** - Transforms scanned data into domain models
6. **CodegenOrchestrator** - Main entry point coordinating the pipeline
7. **Unit Tests** - Demonstrates testability of the new architecture

### ❌ Missing Components

These generators need to be implemented to achieve feature parity:

1. **JoinExtensionGenerator** - Generates `.join(Table) { ... }` extensions
3. **QueryBuilderGenerator** - Generates AfterFromQueryBuilder_* classes
4. **ResultAccessorGenerator** - Complete implementation for multi-column accessors
5. **ExecuteMethodGenerator** - Generates execute() overloads for selection patterns

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    Gradle Task Layer                         │
│  (KodamaTableBasedCodegenTask)                              │
│  - Coordinates process                                       │
│  - Handles file I/O                                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  Orchestrator Layer                          │
│  (CodegenOrchestrator)                                       │
│  - Single entry point                                        │
│  - Coordinates scan → model → generate pipeline             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Scanner Layer                             │
│  - AggregateScanner                                          │
│  - SelectionPatternScanner (interface)                       │
│  - Extracts raw data from source files                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                     Model Layer                              │
│  (DomainModels.kt)                                           │
│  - TableModel, ColumnModel                                   │
│  - QueryBuilderModel, SelectionPatternModel                  │
│  - ResultAccessorModel, ContextModel                         │
│  - InsertMethodModel, ExecuteMethodModel                     │
│  - Pure data classes, no logic                               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Builder Layer                             │
│  (ModelBuilder.kt)                                           │
│  - TableModelBuilder                                         │
│  - InsertMethodModelBuilder                                  │
│  - ContextModelBuilder                                       │
│  - CodeGenerationModelBuilder                                │
│  - Constructs models from scanned data                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   Generator Layer                            │
│  (generator/*.kt)                                            │
│  - TypeAliasGenerator, TableAccessorGenerator                │
│  - InsertMethodGenerator, ContextGenerator                   │
│  - KodamaFileGenerator (facade)                              │
│  - Transforms models to Kotlin code                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Output Layer                              │
│  - Writes generated code to files                            │
│  - Creates directory structure                               │
└─────────────────────────────────────────────────────────────┘
```

## Design Patterns

### 1. Strategy Pattern
**Where:** `CodeGenerator<TModel>` interface

Allows different generation strategies to be plugged in:

```kotlin
interface CodeGenerator<TModel> {
    fun generate(model: TModel): String
}

class TypeAliasGenerator : CodeGenerator<Unit>
class TableAccessorGenerator : CodeGenerator<TableModel>
class InsertMethodGenerator : CodeGenerator<InsertMethodModel>
```

### 2. Composite Pattern
**Where:** `CompositeGenerator`

Combines multiple generators into one:

```kotlin
class CompositeGenerator<TModel>(
    private val generators: List<CodeGenerator<TModel>>
) : CodeGenerator<TModel> {
    override fun generate(model: TModel): String {
        return generators.joinToString("\n\n") { it.generate(model) }
    }
}
```

### 3. Builder Pattern
**Where:** `ModelBuilder<TInput, TOutput>`

Constructs complex domain models:

```kotlin
interface ModelBuilder<TInput, TOutput> {
    fun build(input: TInput): TOutput
}

class TableModelBuilder : ModelBuilder<TableBuildInput, TableModel>
class CodeGenerationModelBuilder : ModelBuilder<CodeGenerationBuildInput, CodeGenerationModel>
```

### 4. Facade Pattern
**Where:** `KodamaFileGenerator`, `CodegenOrchestrator`

Provides simple interface to complex subsystems:

```kotlin
class KodamaFileGenerator : FileGenerator<CodeGenerationModel> {
    override fun generate(model: CodeGenerationModel): String {
        // Coordinates all individual generators
        // Returns complete file content
    }
}

class CodegenOrchestrator(...) {
    fun execute(schemaDir: File, testDir: File): String {
        // Coordinates entire pipeline
    }
}
```

### 5. Decorator Pattern
**Where:** `SectionGenerator`

Wraps generators with section headers:

```kotlin
class SectionGenerator<TModel>(
    private val sectionName: String,
    private val delegate: CodeGenerator<TModel>
) : CodeGenerator<TModel> {
    override fun generate(model: TModel): String {
        return buildString {
            appendLine("// " + "=".repeat(76))
            appendLine("// $sectionName")
            appendLine("// " + "=".repeat(76))
            append(delegate.generate(model))
        }
    }
}
```

## Key Benefits

### 1. Testability
Each generator is a pure function: `Model → String`

Example unit test:
```kotlin
@Test
fun `generates insert method signature`() {
    val generator = InsertMethodGenerator("com.example.schema")
    val model = InsertMethodModel(
        tableName = "Person",
        parameters = listOf(...)
    )

    val result = generator.generate(model)

    assertContains(result, "fun com.example.schema.Person.insert(")
}
```

**No Gradle context, no file I/O, no database required!**

### 2. Maintainability
- Each generator handles exactly one type of output
- Clear separation of scanning, modeling, and generation
- Easy to locate and fix issues

### 3. Extensibility
- New generators can be added without modifying existing code
- New scanners can be plugged in via interface
- Models can be extended with new properties

### 4. Composability
- Generators can be combined using CompositeGenerator
- Decorators can add cross-cutting concerns (logging, metrics, headers)

## File Structure

```
kodama-compiler-plugin/
├── src/
│   ├── main/kotlin/com/obabichev/kodama/compiler/
│   │   ├── CodegenOrchestrator.kt         # Main orchestrator
│   │   ├── SelectionPatternScanner.kt     # Scanner interface
│   │   ├── AggregateScanner.kt            # Aggregate pattern scanner
│   │   ├── model/
│   │   │   └── DomainModels.kt            # All domain models
│   │   ├── builder/
│   │   │   └── ModelBuilder.kt            # All builders
│   │   └── generator/
│   │       ├── CodeGenerator.kt           # Base interfaces
│   │       ├── TypeAliasGenerator.kt      # Type aliases
│   │       ├── TableAccessorGenerator.kt  # Table accessors
│   │       ├── InsertMethodGenerator.kt   # INSERT methods
│   │       ├── ContextGenerator.kt        # Context classes
│   │       ├── ResultAccessorGenerator.kt # Result accessors
│   │       └── KodamaFileGenerator.kt     # Main facade
│   └── test/kotlin/com/obabichev/kodama/compiler/generator/
│       ├── TypeAliasGeneratorTest.kt      # Unit tests
│       ├── TableAccessorGeneratorTest.kt  # Unit tests
│       └── InsertMethodGeneratorTest.kt   # Unit tests
└── ARCHITECTURE.md                        # This document
```

## Usage Example

```kotlin
// Create orchestrator
val orchestrator = CodegenOrchestrator(
    schemaPackage = "com.example.schema",
    generatedPackage = "com.example.generated",
    scanners = listOf(AggregateScanner())
)

// Build model from files
val model = orchestrator.buildModelFromFiles(
    schemaDir = File("src/main/kotlin/schema"),
    testDir = File("src/test/kotlin")
)

// Generate code
val generatedCode = orchestrator.generateCode(model)

// Write to file
orchestrator.writeToFile(generatedCode, outputFile)
```

## Next Steps

To complete the architecture refactoring:

1. **Implement Missing Generators**
   - JoinExtensionGenerator
   - QueryBuilderGenerator
   - Complete ResultAccessorGenerator
   - ExecuteMethodGenerator

2. **Write Tests for New Generators**
   - Unit tests demonstrating testability
   - Integration tests with mock models

3. **Migrate KodamaTableBasedCodegenTask**
   - Replace inline generation with orchestrator call
   - Remove 2000+ lines of monolithic code
   - Keep only file I/O and Gradle integration

4. **Documentation**
   - Update README with new architecture
   - Add generator implementation guide
   - Document model structure

5. **Performance Optimization** (if needed)
   - Parallel generation of independent components
   - Caching of frequently generated patterns

## Testing Philosophy

**Old Approach:**
- Tests required full Gradle build
- Hard to isolate failures
- Slow feedback loop

**New Approach:**
```kotlin
// Fast, isolated, pure function testing
@Test
fun `test specific generation logic`() {
    val generator = SomeGenerator()
    val model = SomeModel(...)
    val result = generator.generate(model)
    assertContains(result, "expected output")
}
```

## Migration Strategy

**Phase 1: Foundation (✅ COMPLETE)**
- Core architecture design
- Domain models
- Base generator interfaces
- Initial generator implementations
- Unit tests

**Phase 2: Complete Generators (IN PROGRESS)**
- Implement missing generators
- Achieve feature parity with old code
- Comprehensive test coverage

**Phase 3: Integration**
- Modify KodamaTableBasedCodegenTask
- Run all existing tests
- Verify identical output

**Phase 4: Cleanup**
- Remove old monolithic code
- Update documentation
- Performance tuning if needed

## Comparison: Old vs New

### Old Architecture (KodamaTableBasedCodegenTask)
- **Lines of Code:** 2,347
- **Structure:** Monolithic, everything in one class
- **Testability:** Difficult, requires Gradle context
- **Maintainability:** Hard to navigate, find specific logic
- **Extensibility:** Adding features requires modifying large class

### New Architecture
- **Lines of Code:** ~1,500 (across multiple focused files)
- **Structure:** Modular, each component has one responsibility
- **Testability:** Easy, pure functions with unit tests
- **Maintainability:** Clear structure, easy to locate code
- **Extensibility:** Add new generators/scanners without touching existing code

## Conclusion

The new architecture provides a solid foundation for maintainable, testable, and extensible code generation. While not yet complete, it demonstrates significant improvements in code organization and testability. The remaining work involves implementing the missing generators to achieve full feature parity with the existing system.
