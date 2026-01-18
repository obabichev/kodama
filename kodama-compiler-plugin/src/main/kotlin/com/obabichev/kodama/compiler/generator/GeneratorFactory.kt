package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.data.*
import com.obabichev.kodama.compiler.generator.markers.*
import com.obabichev.kodama.compiler.generator.accessors.*
import com.obabichev.kodama.compiler.generator.subqueries.*
import com.obabichev.kodama.compiler.generator.phantom.*
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
    private val schemaPackage: String,  // Kept for backward compatibility, but not used
    private val generatedPackage: String,
    private val relationshipMetadata: com.obabichev.kodama.compiler.RelationshipMetadata? = null,
    private val maxTableCount: Int = 5
) {

    // Build map of table name -> package name from table metadata
    private val tablePackages: Map<String, String> = data.tables.associate { it.capitalizedName to it.packageName }

    /**
     * Creates all generators in the appropriate order.
     * Returns a flat list of all generators ready for FileGenerator.
     */
    fun createAllGenerators(): List<CodeGenerator> {
        return buildList {
            // Phase 0: Phantom Types Infrastructure (NEW - eliminates combinatorial explosion)
            addAll(createPhantomTypesGenerators())

            // Phase 1: Marker Interfaces (needed for column markers)
            addAll(createMarkerGenerators())

            // Phase 2: Accessors (PersonAccessor, etc. - needed by phantom types extensions)
            addAll(createAccessorGenerators())

            // Phase 4: Subquery Infrastructure (kept for now)
            addAll(createSubqueryGenerators())

            // NOTE: Legacy phases 3, 5, 6, 7 (Contexts, Builders, Extensions, Results) were removed.
            // These generated combination-specific classes like SelectContext_Person_Order.
            // Now replaced by generic phantom types: SelectContext_2<PersonMarker, OrderMarker>
        }
    }

    /**
     * Helper function to wrap a generator with target file information for a combination.
     */
    private fun CodeGenerator.forCombination(combination: QueryCombinationInfo): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forCombination(combination))

    /**
     * Helper function to wrap a generator with target file information for a subquery.
     */
    private fun CodeGenerator.forSubquery(subquery: SubqueryInfo): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forSubquery(subquery))

    /**
     * Helper function to wrap a generator for markers file.
     */
    private fun CodeGenerator.forMarkersFile(): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forMarkers())

    /**
     * Helper function to wrap a generator for selection sets file.
     */
    private fun CodeGenerator.forSelectionSetsFile(): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forSelectionSets())

    /**
     * Helper function to wrap a generator for join patterns file.
     */
    private fun CodeGenerator.forJoinPatternsFile(): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forJoinPatterns())

    /**
     * Helper function to wrap a generator for subquery infrastructure file.
     */
    private fun CodeGenerator.forSubqueryInfrastructureFile(): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forSubqueryInfrastructure())

    /**
     * Helper function to wrap a generator for a single table's file.
     */
    private fun CodeGenerator.forTable(table: com.obabichev.kodama.compiler.data.TableInfo): CodeGenerator =
        this.withTargetFile("single_table/${table.capitalizedName}Query.kt")

    /**
     * Helper function to wrap a generator for phantom types infrastructure file.
     */
    private fun CodeGenerator.forPhantomTypesFile(): CodeGenerator =
        this.withTargetFile("_infrastructure/PhantomTypes.kt")

    /**
     * Phase 0: Phantom Types Infrastructure generators.
     *
     * Generates the new phantom types approach that eliminates combinatorial explosion:
     * - TableMarker interface and concrete marker objects (PersonMarker, OrderMarker, etc.)
     * - QueryBuilder_N classes for N-table queries (fixed number, not per-combination)
     * - SelectContext_N, WhereContext_N for phantom type contexts
     * - QueryResultIterable_N, QueryResult_N for results
     * - Table accessor extensions based on phantom types
     *
     * This replaces the O(N!) combination-specific generation with O(N) generation.
     */
    private fun createPhantomTypesGenerators(): List<CodeGenerator> = buildList {
        // 1. Generate table markers (one per table and subquery)
        val tableNames = data.tables.map { it.capitalizedName }
        add(TableMarkersGenerator(tableNames, data.subqueries, generatedPackage).forPhantomTypesFile())

        // 2. Generate selection set phantom types for marker-based selection
        add(SelectionSetTypesGenerator(generatedPackage).forPhantomTypesFile())

        // 3. Generate QueryBuilder_N classes for N=1 to N=maxTableCount
        for (n in 1..maxTableCount) {
            add(QueryBuilderNGenerator(n, generatedPackage, tablePackages).forPhantomTypesFile())
        }

        // 4. Generate SelectContext_N and WhereContext_N for N=1 to N=5
        for (n in 1..maxTableCount) {
            add(SelectContextNGenerator(n, generatedPackage).forPhantomTypesFile())
            add(WhereContextNGenerator(n, generatedPackage).forPhantomTypesFile())
        }

        // 5. Generate QueryResultIterable_N and QueryResult_N for N=1 to N=5
        for (n in 1..maxTableCount) {
            add(QueryResultIterableNGenerator(n, generatedPackage).forPhantomTypesFile())
        }

        // 5a. Generate marker accessor extensions for each (marker, tableCount) pair
        // These provide compile-time typed accessors like row.totalRevenue
        data.markers.forEach { marker ->
            for (n in 1..maxTableCount) {
                add(MarkerAccessorExtensionGenerator(
                    markerInfo = marker,
                    tableCount = n,
                    generatedPackage = generatedPackage
                ).forPhantomTypesFile())
            }
        }

        // 6. Generate table-specific context extensions for each (table, N) pair
        // These provide table accessors in contexts based on phantom type position
        tableNames.forEach { tableName ->
            val tablePackage = tablePackages[tableName] ?: schemaPackage
            for (n in 1..maxTableCount) {
                // SelectContext extensions
                add(TableContextExtensionsGenerator(tableName, n, generatedPackage, tablePackage).forPhantomTypesFile())
                // WhereContext extensions
                add(TableWhereExtensionsGenerator(tableName, n, generatedPackage, tablePackage).forPhantomTypesFile())
            }
        }

        // 6-SELECT. Generate table-specific selectAll() extensions with phantom type constraints
        // These provide COMPILE-TIME safety: you can only selectAll(Table) if that table is in the query
        tableNames.forEach { tableName ->
            val tablePackage = tablePackages[tableName] ?: schemaPackage
            for (n in 1..maxTableCount) {
                add(TableSelectAllExtensionsGenerator(tableName, n, generatedPackage, tablePackage).forPhantomTypesFile())
            }
        }

        // 6a. Generate OrderByContext_N and GroupByContext_N classes
        for (n in 1..maxTableCount) {
            add(OrderByContextNGenerator(n, generatedPackage).forPhantomTypesFile())
            add(GroupByContextNGenerator(n, generatedPackage).forPhantomTypesFile())
        }

        // 6b. Generate table accessor extensions for OrderBy and GroupBy contexts
        tableNames.forEach { tableName ->
            val tablePackage = tablePackages[tableName] ?: schemaPackage
            for (n in 1..maxTableCount) {
                add(TableOrderByExtensionsGenerator(tableName, n, generatedPackage, tablePackage).forPhantomTypesFile())
                add(TableGroupByExtensionsGenerator(tableName, n, generatedPackage, tablePackage).forPhantomTypesFile())
            }
        }

        // 7. Generate JoinContext classes for each relationship (from → to)
        // These provide table accessors within join condition lambdas
        relationshipMetadata?.relationships?.forEach { (from, to) ->
            val fromPackage = tablePackages[from] ?: schemaPackage
            val toPackage = tablePackages[to] ?: schemaPackage
            add(PhantomJoinContextGenerator(from, to, generatedPackage, fromPackage, toPackage).forPhantomTypesFile())
        }

        // 8. Generate join() extensions for each relationship (from → to) - 1-table queries only
        // These enable QueryBuilder_1 → QueryBuilder_2 transitions
        relationshipMetadata?.relationships?.forEach { (from, to) ->
            val fromPackage = tablePackages[from] ?: schemaPackage
            val toPackage = tablePackages[to] ?: schemaPackage
            add(PhantomJoinExtensionGenerator(from, to, generatedPackage, fromPackage, toPackage).forPhantomTypesFile())
        }

        // 9. Generate MultiTableJoinContext_N classes for N=2, 3, 4, 5
        // These provide table accessors for multi-table join conditions
        // Starting from 2 to support subquery joins (1-table + subquery = 2-table context)
        for (n in 2..maxTableCount) {
            add(MultiTableJoinContextNGenerator(n, generatedPackage).forPhantomTypesFile())
        }

        // 10. Generate table accessor extensions for MultiTableJoinContext_N
        // These provide position-based table access in multi-table join contexts
        tableNames.forEach { tableName ->
            val tablePackage = tablePackages[tableName] ?: schemaPackage
            for (n in 2..maxTableCount) {
                add(MultiTableJoinContextExtensionsGenerator(tableName, n, generatedPackage, tablePackage).forPhantomTypesFile())
            }
        }

        // 11. Generate multi-table join() extensions for each relationship
        // These enable QueryBuilder_N → QueryBuilder_{N+1} transitions (N >= 2)
        relationshipMetadata?.relationships?.forEach { (from, to) ->
            val fromPackage = tablePackages[from] ?: schemaPackage
            val toPackage = tablePackages[to] ?: schemaPackage
            // For QueryBuilder_2 → QueryBuilder_3
            for (position in 1..2) {
                add(PhantomMultiTableJoinExtensionGenerator(from, to, 2, position, generatedPackage, fromPackage, toPackage).forPhantomTypesFile())
            }
            // For QueryBuilder_3 → QueryBuilder_4
            for (position in 1..3) {
                add(PhantomMultiTableJoinExtensionGenerator(from, to, 3, position, generatedPackage, fromPackage, toPackage).forPhantomTypesFile())
            }
            // For QueryBuilder_4 → QueryBuilder_5
            for (position in 1..4) {
                add(PhantomMultiTableJoinExtensionGenerator(from, to, 4, position, generatedPackage, fromPackage, toPackage).forPhantomTypesFile())
            }
        }

        // 11. Generate table result accessor extensions for QueryResult_N
        // These provide position-based table access in query results
        tableNames.forEach { tableName ->
            val tablePackage = tablePackages[tableName] ?: schemaPackage
            for (n in 1..maxTableCount) {
                add(TableResultExtensionsGenerator(tableName, n, generatedPackage, tablePackage).forPhantomTypesFile())
            }
        }

        // 12. Generate fromAliased() functions for subqueries
        // These allow starting queries from subqueries: fromAliased(UserTotals) { ... }
        data.subqueries.forEach { subquery ->
            add(PhantomFromAliasedGenerator(subquery, generatedPackage).forPhantomTypesFile())
        }

        // 13. Generate subquery accessor extensions for contexts
        // These provide subquery accessors in SelectContext, WhereContext, and MultiTableJoinContext
        data.subqueries.forEach { subquery ->
            for (n in 1..maxTableCount) {
                add(SubqueryContextExtensionsGenerator(subquery, n, generatedPackage, generatedPackage).forPhantomTypesFile())
                add(SubqueryWhereExtensionsGenerator(subquery, n, generatedPackage, generatedPackage).forPhantomTypesFile())
                // MultiTableJoinContext starts at 2 (need at least 2 tables for a join)
                if (n >= 2) {
                    add(SubqueryJoinExtensionsGenerator(subquery, n, generatedPackage, generatedPackage).forPhantomTypesFile())
                }
            }
        }

        // 14. Generate subquery result accessor extensions
        // These provide subquery accessors in QueryResult
        data.subqueries.forEach { subquery ->
            for (n in 1..maxTableCount) {
                add(SubqueryResultExtensionsGenerator(subquery, n, generatedPackage, generatedPackage).forPhantomTypesFile())
            }
        }

        // 15. Generate joinAliased() methods for subqueries
        // These allow joining subqueries to queries: .joinAliased(UsersWithOrders) { ... }
        data.subqueries.forEach { subquery ->
            for (n in 1..4) {  // Max 5 tables, so join from 1-4
                add(PhantomJoinAliasedGenerator(subquery, n, generatedPackage).forPhantomTypesFile())
                add(PhantomLeftJoinAliasedGenerator(subquery, n, generatedPackage).forPhantomTypesFile())
                // TODO: Add RightJoinAliased and FullJoinAliased generators
            }
        }

        // Note: Query.aliasAs<T>() is provided by SubqueryAliasing.kt in core library

        // 16. Generate result accessor classes for subqueries
        // These provide type-safe access to subquery columns in result rows
        data.subqueries.forEach { subquery ->
            add(PhantomSubqueryResultAccessorGenerator(subquery, generatedPackage).forPhantomTypesFile())
        }
    }

    /**
     * Phase 1: Marker interface generators (8 types).
     */
    private fun createMarkerGenerators(): List<CodeGenerator> = buildList {
        // Column markers for each UNIQUE column name (deduplicated across all tables)
        val uniqueColumns = data.tables.flatMap { it.columns }
            .distinctBy { it.propertyName }
        uniqueColumns.forEach { column ->
            add(ColumnMarkerGenerator(column).forMarkersFile())
        }

        // Table markers for each table
        data.tables.forEach { table ->
            add(TableMarkerGenerator(table).forMarkersFile())
        }

        // NOTE: AllMarker classes (PersonAllMarker, etc.) are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration

        // Selection markers (for marker-based selections like .selectAs(TotalRevenue) { ... })
        data.markers.forEach { marker ->
            add(SelectionMarkerGenerator(marker).forSelectionSetsFile())
        }

        // Subquery markers
        data.subqueries.forEach { subquery ->
            add(SubqueryMarkerGenerator(subquery).forMarkersFile())
            add(SubqueryAllMarkerGenerator(subquery).forSubquery(subquery))
        }

        // NOTE: The following generators were deleted (replaced by phantom types):
        // - SelectionSetMarkerGenerator - Old selection set tracking (phantom types track this now)
        // - HasAliasedSelectionsMarkerGenerator - Old aggregate counting (phantom types track this now)
        // - JoinPatternMarkerGenerator - Old join pattern types (replaced by JoinType enum)
    }

    /**
     * Phase 2: Accessor generators (11 types).
     */
    private fun createAccessorGenerators(): List<CodeGenerator> = buildList {
        // Table accessors - go to their respective table files
        data.tables.forEach { table ->
            add(TableAccessorGenerator(table, table.packageName).forTable(table))
        }

        // NOTE: OrderByAccessor and GroupByAccessor classes are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration

        // Subquery accessors - go to their respective subquery files
        data.subqueries.forEach { subquery ->
            add(SubqueryAccessorGenerator(subquery).forSubquery(subquery))
            add(SubqueryOrderByAccessorGenerator(subquery).forSubquery(subquery))
            add(SubqueryGroupByAccessorGenerator(subquery).forSubquery(subquery))
        }

        // Result accessors - All variant
        // NOTE: ResultAccessor_All, ResultAccessor_<Column>, etc. are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration
    }




    /**
     * DELETED METHODS (284 lines removed):
     * - createContextGenerators() - Generated SelectContext_Person_Order, WhereContext_Person_Order, etc.
     * - createBuilderGenerators() - Generated AfterFromQueryBuilder_Person_Order, etc.
     * - createExtensionGenerators() - Generated join() extension methods for combinations
     * - createResultGenerators() - Generated QueryResult_Person_Order with nullability
     *
     * All replaced by phantom types system (createPhantomTypesGenerators).
     */


    /**
     * Phase 7: Subquery infrastructure generators (3 types).
     */
    private fun createSubqueryGenerators(): List<CodeGenerator> = buildList {
        data.subqueries.forEach { subquery ->
            add(SubqueryTableClassGenerator(subquery).forSubqueryInfrastructureFile())
        }

        // Always generate SubqueryRegistry (even if empty) since aliasAs() methods depend on it
        add(SubqueryRegistryGenerator(data.subqueries).forSubqueryInfrastructureFile())
    }
}
