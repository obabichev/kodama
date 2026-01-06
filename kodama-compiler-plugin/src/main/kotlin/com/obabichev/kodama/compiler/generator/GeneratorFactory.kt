package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.data.*
import com.obabichev.kodama.compiler.generator.markers.*
import com.obabichev.kodama.compiler.generator.accessors.*
import com.obabichev.kodama.compiler.generator.contexts.*
import com.obabichev.kodama.compiler.generator.builders.*
import com.obabichev.kodama.compiler.generator.extensions.*
import com.obabichev.kodama.compiler.generator.results.*
import com.obabichev.kodama.compiler.generator.subqueries.*
import com.obabichev.kodama.compiler.transform.DataTransformer

/**
 * Factory for creating all code generators based on transformed data.
 *
 * The GeneratorFactory is the central orchestrator that instantiates all generators
 * in the correct order. It takes structured data from DataTransformer and creates
 * appropriate generator instances for each construct.
 *
 * Organization:
 * - Markers (7 generators)
 * - Accessors (6 generators)
 * - Contexts (6 generators)
 * - Builders (4 generators)
 * - Extensions (22 generators)
 * - Results (8 generators including hybrid)
 * - Subqueries (3 generators)
 *
 * Example usage:
 * ```
 * val factory = GeneratorFactory(transformedData, schemaPackage, generatedPackage)
 * val allGenerators = factory.createAllGenerators()
 *
 * val fileGenerator = FileGenerator(
 *     packageName = generatedPackage,
 *     fileName = "QueryExtensions.kt",
 *     generators = allGenerators
 * )
 *
 * outputFile.writeText(fileGenerator.generate())
 * ```
 */
class GeneratorFactory(
    private val data: DataTransformer.TransformedData,
    private val schemaPackage: String,
    private val generatedPackage: String
) {

    /**
     * Creates all generators in the appropriate order.
     * Returns a flat list of all generators ready for FileGenerator.
     */
    fun createAllGenerators(): List<CodeGenerator> {
        return buildList {
            // Phase 1: Marker Interfaces
            addAll(createMarkerGenerators())

            // Phase 2: Accessors
            addAll(createAccessorGenerators())

            // Phase 3: Contexts
            addAll(createContextGenerators())

            // Phase 4: Subquery Infrastructure (must be before builders that use it)
            addAll(createSubqueryGenerators())

            // Phase 5: Builders
            addAll(createBuilderGenerators())

            // Phase 6: Extensions
            addAll(createExtensionGenerators())

            // Phase 7: Results
            addAll(createResultGenerators())
        }
    }

    /**
     * Phase 1: Marker interface generators (7 types).
     */
    private fun createMarkerGenerators(): List<CodeGenerator> = buildList {
        // Column markers for each UNIQUE column name (deduplicated across all tables)
        val uniqueColumns = data.tables.flatMap { it.columns }
            .distinctBy { it.propertyName }
        uniqueColumns.forEach { column ->
            add(ColumnMarkerGenerator(column))
        }

        // Table markers for each table
        data.tables.forEach { table ->
            add(TableMarkerGenerator(table))
        }

        // NOTE: AllMarker classes (PersonAllMarker, etc.) are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration

        // Selection markers
        data.markers.forEach { marker ->
            add(SelectionMarkerGenerator(marker))
        }

        // Subquery markers
        data.subqueries.forEach { subquery ->
            add(SubqueryMarkerGenerator(subquery))
            add(SubqueryAllMarkerGenerator(subquery))
        }

        // Selection set markers (generated for each marker combination AND all its prefixes)
        val generatedPhantomTypes = mutableSetOf<String>()
        data.markerCombinations.forEach { combination ->
            // Generate phantom types for all prefixes: [M1], [M1, M2], [M1, M2, M3], etc.
            for (prefixLength in 1..combination.markers.size) {
                val prefix = combination.markers.take(prefixLength)
                val phantomTypeName = "SelectionSet_" + prefix.joinToString("_") { it.interfaceName }
                if (phantomTypeName !in generatedPhantomTypes) {
                    generatedPhantomTypes.add(phantomTypeName)
                    add(SelectionSetMarkerGenerator(phantomTypeName))
                }
            }
        }

        // HasAliasedSelections marker (single instance)
        add(HasAliasedSelectionsMarkerGenerator())
    }

    /**
     * Phase 2: Accessor generators (11 types).
     */
    private fun createAccessorGenerators(): List<CodeGenerator> = buildList {
        // Table accessors
        data.tables.forEach { table ->
            add(TableAccessorGenerator(table, schemaPackage))
        }

        // NOTE: OrderByAccessor and GroupByAccessor classes are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration

        // Subquery accessors
        data.subqueries.forEach { subquery ->
            add(SubqueryAccessorGenerator(subquery))
            add(SubqueryOrderByAccessorGenerator(subquery))
            add(SubqueryGroupByAccessorGenerator(subquery))
        }

        // Result accessors - All variant
        // NOTE: ResultAccessor_All, ResultAccessor_<Column>, etc. are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration
    }

    /**
     * Phase 3: Context generators (6 types).
     */
    private fun createContextGenerators(): List<CodeGenerator> = buildList {
        data.queryCombinations.forEach { combination ->
            add(SelectContextGenerator(combination, schemaPackage))
            add(WhereContextGenerator(combination, schemaPackage))
            add(OrderByContextGenerator(combination, schemaPackage))
            add(GroupByContextGenerator(combination, schemaPackage))
            add(SelectAllContextGenerator(combination, schemaPackage))
        }

        // JoinContext requires specific fromCombination + joiningTable
        // Generate for valid table progressions with deduplication
        val generatedJoinContexts = mutableSetOf<String>()
        data.queryCombinations.forEach { combination ->
            if (combination.tables.size >= 2) {
                // For each table except the first (joined tables)
                combination.tables.drop(1).forEachIndexed { index, joiningTable ->
                    val fromCombination = QueryCombinationInfo(
                        tables = combination.tables.take(index + 1)
                    )
                    val allTables = fromCombination.tables + joiningTable
                    val contextName = "JoinContext_" + allTables.joinToString("_") { it.capitalizedName }

                    if (contextName !in generatedJoinContexts) {
                        generatedJoinContexts.add(contextName)
                        add(JoinContextGenerator(fromCombination, joiningTable, schemaPackage))
                    }
                }
            }
        }
    }

    /**
     * Phase 4: Builder generators (4 types).
     */
    private fun createBuilderGenerators(): List<CodeGenerator> = buildList {
        // Query builder classes
        data.queryCombinations.forEach { combination ->
            add(QueryBuilderClassGenerator(combination))
            add(QueryBuilderBuildMethodGenerator(combination))
            add(QueryBuilderAliasAsMethodGenerator(combination))
        }

        // NOTE: Subquery builder classes are now generated via queryCombinations
        // Single-subquery combinations are added automatically in DataTransformer
        // So SubqueryBuilderClassGenerator is no longer needed
    }

    /**
     * Phase 5: Extension method generators (22 types).
     */
    private fun createExtensionGenerators(): List<CodeGenerator> = buildList {
        // NOTE: from() methods are generated in TableMetadata.kt by generateKodamaTableMetadata task
        // Don't generate them here to avoid conflicts

        // Join methods for each combination progression
        // Only generate join methods for combinations that actually exist
        data.queryCombinations.forEach { toCombination ->
            if (toCombination.tables.size >= 2) {
                // This is a multi-table combination, so generate join from the previous combination
                val joiningTable = toCombination.tables.last()
                val fromCombination = QueryCombinationInfo(
                    tables = toCombination.tables.dropLast(1)
                )

                if (joiningTable.isSubquery) {
                    // Generate joinAliased/leftJoinAliased for subqueries
                    val subqueryInfo = data.subqueries.find { it.name == joiningTable.name }
                    if (subqueryInfo != null) {
                        add(JoinAliasedMethodGenerator(fromCombination, subqueryInfo, toCombination))
                        add(LeftJoinAliasedMethodGenerator(fromCombination, subqueryInfo, toCombination))
                    }
                } else {
                    // Generate regular join/leftJoin for tables
                    add(JoinMethodGenerator(fromCombination, joiningTable, toCombination, schemaPackage))
                    add(LeftJoinMethodGenerator(fromCombination, joiningTable, toCombination, schemaPackage))
                }
            }
        }

        // Select methods for each combination
        data.queryCombinations.forEach { combination ->
            add(SelectMethodGenerator(combination))
            // NOTE: Generic selectAs removed - replaced by marker-specific selectAs methods below
            // add(SelectAsMethodGenerator(combination))
            // Generate selectAll() for EACH table in the combination
            combination.tables.forEach { table ->
                add(SelectAllDirectMethodGenerator(combination, table, schemaPackage))

                // For subqueries, also generate selectAll(marker) overload
                if (table.isSubquery) {
                    val subqueryInfo = data.subqueries.find { it.name == table.name }
                    if (subqueryInfo != null) {
                        add(SelectAllSubqueryMarkerMethodGenerator(combination, subqueryInfo))
                    }
                }
            }
            add(SelectAllLambdaMethodGenerator(combination))
        }

        // NEW: Generate marker-specific selectAs with deduplication
        // Track which (queryCombination, fromType, markerName, toType) methods we've generated
        val generatedSelectAsMethods = mutableSetOf<String>()

        data.queryCombinations.forEach { queryCombination ->
            data.markerCombinations.forEach { markerCombination ->
                // Generate selectAs for each marker in the combination
                markerCombination.markers.indices.forEach { markerIndex ->
                    val marker = markerCombination.markers[markerIndex]

                    // Determine from/to types
                    val fromType = if (markerIndex == 0) {
                        "NoSelections"
                    } else {
                        "SelectionSet_" + markerCombination.markers.take(markerIndex).joinToString("_") { it.interfaceName }
                    }
                    val toType = "SelectionSet_" + markerCombination.markers.take(markerIndex + 1).joinToString("_") { it.interfaceName }

                    // Create unique signature for this method
                    val signature = "${queryCombination.builderClassName}|$fromType|${marker.interfaceName}|$toType"

                    if (signature !in generatedSelectAsMethods) {
                        generatedSelectAsMethods.add(signature)
                        add(SelectAsForMarkerGenerator(queryCombination, markerCombination, markerIndex))
                    }
                }
            }
        }

        // Clause methods for each combination
        data.queryCombinations.forEach { combination ->
            add(WhereMethodGenerator(combination))
            add(OrderByMethodGenerator(combination))
            add(GroupByMethodGenerator(combination))
            add(LimitMethodGenerator(combination))
            add(OffsetMethodGenerator(combination))
        }

        // Subquery methods for each combination
        data.queryCombinations.forEach { combination ->
            add(ExistsMethodGenerator(combination))
            add(NotExistsMethodGenerator(combination))
            add(ScalarSubqueryMethodGenerator(combination))
        }

        // Execute method for each combination
        data.queryCombinations.forEach { combination ->
            add(ExecuteMethodGenerator(combination))
        }

        // Subquery-specific methods
        data.subqueries.forEach { subquery ->
            add(FromAliasedMethodGenerator(subquery))
            add(FromAliasedWithLambdaMethodGenerator(subquery))
        }
    }

    /**
     * Phase 6: Result generators (7 types).
     */
    private fun createResultGenerators(): List<CodeGenerator> = buildList {
        // Query result classes
        data.queryCombinations.forEach { combination ->
            add(QueryResultClassGenerator(combination))
            add(ExecuteQueryResultMethodGenerator(combination))
        }

        // Selection result classes and execute methods (for marker combinations)
        data.markerCombinations.forEach { markerCombination ->
            // Generate SelectionResult class for this combination (once per combination)
            add(SelectionResultClassGenerator(markerCombination))
        }

        // Generate execute() methods with deduplication
        // Track which (queryCombination, phantomType, resultClass) methods we've generated
        val generatedExecuteMethods = mutableSetOf<String>()

        data.queryCombinations.forEach { queryCombination ->
            data.markerCombinations.forEach { markerCombination ->
                val phantomType = "SelectionSet_" + markerCombination.markers.joinToString("_") { it.interfaceName }
                val signature = "${queryCombination.builderClassName}|$phantomType|${markerCombination.resultClassName}"

                if (signature !in generatedExecuteMethods) {
                    generatedExecuteMethods.add(signature)
                    add(ExecuteAggregateMethodGenerator(queryCombination, markerCombination))
                }
            }
        }

        // Hybrid result classes and execute methods (for mixed marker + table selections)
        // Generate for each combination of: marker combination + single table with AllColumnsSelected

        // First, generate unique hybrid result classes (deduplicated by marker + table combination)
        val generatedHybridClasses = mutableSetOf<String>()
        data.markerCombinations.forEach { markerCombination ->
            // Get all unique tables across all query combinations
            val allTables = data.queryCombinations.flatMap { it.tables }.distinctBy { it.name }
            allTables.forEach { table ->
                val classSignature = "${markerCombination.resultClassName}_${table.capitalizedName}"
                if (classSignature !in generatedHybridClasses) {
                    generatedHybridClasses.add(classSignature)
                    add(HybridResultClassGenerator(markerCombination, listOf(table)))
                }
            }
        }

        // Then, generate execute methods for each query combination
        data.markerCombinations.forEach { markerCombination ->
            data.queryCombinations.forEach { queryCombination ->
                queryCombination.tables.forEach { table ->
                    // Generate execute method for this hybrid pattern
                    add(ExecuteHybridMethodGenerator(queryCombination, markerCombination, listOf(table), schemaPackage))
                }
            }
        }

        // Subquery result accessors
        data.subqueries.forEach { subquery ->
            add(SubqueryResultAccessorGenerator(subquery))
            add(SubqueryResultAccessorAllGenerator(subquery))
        }
    }

    /**
     * Phase 7: Subquery infrastructure generators (3 types).
     */
    private fun createSubqueryGenerators(): List<CodeGenerator> = buildList {
        data.subqueries.forEach { subquery ->
            add(SubqueryTableClassGenerator(subquery))
        }

        // Always generate SubqueryRegistry (even if empty) since aliasAs() methods depend on it
        add(SubqueryRegistryGenerator(data.subqueries))
    }
}
