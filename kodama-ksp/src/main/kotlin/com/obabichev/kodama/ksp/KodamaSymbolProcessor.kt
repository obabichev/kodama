package com.obabichev.kodama.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSCallableReference
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate

/**
 * KSP Symbol Processor for Kodama.
 *
 * This processor analyzes Kotlin source code during compilation to detect query builder patterns
 * and generates type-safe result classes for each unique query combination.
 *
 * Key advantages over current Gradle task approach:
 * 1. Runs during compilation (not as separate task)
 * 2. IDE sees generated code immediately (instant autocomplete)
 * 3. Each query gets its own unique result type
 * 4. True compile-time type safety - accessing non-selected field = compile error
 */
class KodamaSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    // Track detected query patterns across all files
    private val detectedQueries = mutableListOf<QueryPattern>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Find all files in the project
        val files = resolver.getAllFiles().toList()

        logger.info("KodamaSymbolProcessor: Processing ${files.size} files")

        // Visit each file to detect query patterns
        files.forEach { file ->
            file.accept(QueryDetectorVisitor(logger, detectedQueries), Unit)
        }

        // Generate result classes for detected queries
        if (detectedQueries.isNotEmpty()) {
            logger.info("KodamaSymbolProcessor: Detected ${detectedQueries.size} unique query patterns")
            generateResultClasses(detectedQueries)
        }

        // Return empty list = all symbols processed successfully
        return emptyList()
    }

    /**
     * Generates unique result classes for each detected query pattern.
     */
    private fun generateResultClasses(queries: List<QueryPattern>) {
        val generator = KodamaCodeGenerator(codeGenerator, logger)

        queries.forEachIndexed { index, query ->
            generator.generateResultClass(query, index)
        }

        // Also generate extension functions for query builders
        generator.generateQueryExtensions(queries)
    }
}

/**
 * Visitor that traverses the AST to detect query() builder chains.
 */
private class QueryDetectorVisitor(
    private val logger: KSPLogger,
    private val detectedQueries: MutableList<QueryPattern>
) : KSVisitorVoid() {

    override fun visitFile(file: KSFile, data: Unit) {
        logger.info("Visiting file: ${file.fileName}")
        file.declarations.forEach { it.accept(this, data) }
    }

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        // Visit all functions in the class
        classDeclaration.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .forEach { it.accept(this, data) }
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
        // TODO: Analyze function body to detect query() chains
        // This is the core implementation - will be added in next step

        logger.info("Analyzing function: ${function.simpleName.asString()}")

        // For now, log that we're analyzing the function
        // The actual implementation will parse the function body to find:
        // 1. query() calls
        // 2. .from(Table) calls
        // 3. .join(Table) chains
        // 4. .select {} or .selectAggregates {} blocks
        // 5. Extract exact columns/aggregates selected
    }
}

/**
 * Represents a detected query pattern with its selections.
 */
data class QueryPattern(
    val tables: List<String>,           // e.g., ["Order"]
    val regularColumns: List<ColumnSelection>,  // e.g., [ColumnSelection("order", "id")]
    val aggregates: List<AggregateSelection>,   // e.g., [AggregateSelection("sum", "order.cost", "totalRevenue")]
    val sourceFile: String,              // File where query was found
    val lineNumber: Int                  // Line number for debugging
)

data class ColumnSelection(
    val table: String,
    val column: String
)

data class AggregateSelection(
    val function: String,  // "sum", "count", "avg", etc.
    val column: String?,   // "order.cost" or null for count(*)
    val alias: String      // "totalRevenue" or auto-generated
)
