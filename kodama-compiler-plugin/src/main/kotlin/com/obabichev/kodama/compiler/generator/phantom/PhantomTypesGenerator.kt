package com.obabichev.kodama.compiler.generator.phantom

import com.obabichev.kodama.compiler.generator.CodeGenerator
import com.obabichev.kodama.compiler.util.toSnakeCase

/**
 * Generates table marker types for phantom type-based query builders.
 *
 * Instead of generating AfterFromQueryBuilder_Person_Order classes for every
 * combination, we generate:
 * - TableMarker sealed interface
 * - PersonMarker, OrderMarker, etc. objects (one per table)
 * - QueryBuilder_1, QueryBuilder_2, etc. classes (fixed number)
 *
 * Type parameters encode which tables are joined:
 * - QueryBuilder_1<PersonMarker>
 * - QueryBuilder_2<PersonMarker, OrderMarker>
 * - QueryBuilder_3<PersonMarker, OrderMarker, CompanyMarker>
 */
class TableMarkersGenerator(
    private val tables: List<String>,
    private val subqueries: List<com.obabichev.kodama.compiler.data.SubqueryInfo>,
    private val generatedPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Table marker types for phantom type-based query builders.")
        appendLine(" * These types exist only at compile-time to encode which tables are joined.")
        appendLine(" * They have no runtime representation (phantom types).")
        appendLine(" */")
        appendLine()

        // Sealed interface for all table markers
        appendLine("sealed interface TableMarker")
        appendLine()

        // Generate marker object for each table
        tables.forEach { tableName ->
            appendLine("/**")
            appendLine(" * Phantom type marker for $tableName table.")
            appendLine(" */")
            appendLine("object ${tableName}Marker : TableMarker")
            appendLine()
        }

        // Generate marker object for each subquery
        subqueries.forEach { subquery ->
            appendLine("/**")
            appendLine(" * Phantom type marker for ${subquery.name} subquery.")
            appendLine(" */")
            appendLine("object ${subquery.name}Marker : TableMarker")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = emptySet()
}

/**
 * Generates SelectionSet phantom types for tracking selected markers.
 *
 * Instead of generating result classes for every marker combination,
 * we use nested phantom types to track selections at compile-time:
 *
 * - SelectionSet: Base interface for all selection sets
 * - NoSelections: Empty selection set (initial state)
 * - Selected<Marker, Rest>: Accumulates selections as nested types
 *
 * Example type evolution:
 * - Initial: QueryBuilder_1<PersonMarker, NoSelections>
 * - After .selectAs(TotalRevenue): QueryBuilder_1<PersonMarker, Selected<TotalRevenue, NoSelections>>
 * - After .selectAs(OrderCount): QueryBuilder_1<PersonMarker, Selected<OrderCount, Selected<TotalRevenue, NoSelections>>>
 *
 * This scales O(M) where M = number of markers, not O(2^M) for all combinations!
 */
class SelectionSetTypesGenerator(
    private val generatedPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Selection set phantom types for compile-time tracking of selected markers.")
        appendLine(" * These types exist only at compile-time to encode which markers are selected.")
        appendLine(" * They enable type-safe named accessors in query results.")
        appendLine(" */")
        appendLine()

        // Base sealed interface for marker selections
        appendLine("sealed interface SelectionSet")
        appendLine()

        // Empty selection set
        appendLine("/**")
        appendLine(" * Empty selection set - no markers selected yet.")
        appendLine(" */")
        appendLine("interface NoSelections : SelectionSet")
        appendLine()

        // Accumulated selection
        appendLine("/**")
        appendLine(" * Accumulated selection set - marker M selected, with Rest selections.")
        appendLine(" * Forms a type-level linked list of selected markers.")
        appendLine(" */")
        appendLine("interface Selected<Marker, Rest : SelectionSet> : SelectionSet")
        appendLine()

        // Per-position selection status for tables
        appendLine("/**")
        appendLine(" * Selection status - tracks whether a table at a specific position has been selected.")
        appendLine(" * Each table position (T1, T2, T3, etc.) has its own selection status (S1, S2, S3, etc.).")
        appendLine(" * This enables direct compile-time checking: row.table requires S_N : TableSelected")
        appendLine(" */")
        appendLine("sealed interface SelectionStatus")
        appendLine()

        appendLine("/**")
        appendLine(" * TableSelected status - table at this position has been selected with .selectAll().")
        appendLine(" */")
        appendLine("interface TableSelected : SelectionStatus")
        appendLine()

        appendLine("/**")
        appendLine(" * TableNotSelected status - table at this position has NOT been selected yet.")
        appendLine(" */")
        appendLine("interface TableNotSelected : SelectionStatus")
        appendLine()
    }

    override fun requiredImports(): Set<String> = emptySet()
}

/**
 * Generates marker accessor extensions for QueryResult_N.
 *
 * For each marker interface (e.g., TotalRevenue) and each QueryResult_N,
 * generates an extension that provides compile-time access when the marker
 * is in the SelectionSet.
 *
 * Example:
 * ```kotlin
 * val <T1, Rest : SelectionSet> QueryResult_1<T1, Selected<TotalRevenue, Rest>>.totalRevenue: Number
 *     get() = state.getMarkerValue(TotalRevenue::class) as Number
 * ```
 */
class MarkerAccessorExtensionGenerator(
    private val markerInfo: com.obabichev.kodama.compiler.data.MarkerInfo,
    private val tableCount: Int,     // 1 to 5
    private val generatedPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val markerName = markerInfo.interfaceName
        val accessorName = markerInfo.propertyName
        val markerType = markerInfo.resultType

        // Build type parameters: T1, T2, ..., TN, S1, S2, ..., SN, Sel
        val tableTypeParams = (1..tableCount).joinToString(", ") { "T$it : TableMarker" }
        val selectionStatusParams = (1..tableCount).joinToString(", ") { "S$it : SelectionStatus" }
        val allTypeParams = if (tableTypeParams.isNotEmpty()) {
            "$tableTypeParams, $selectionStatusParams, Sel : SelectionSet"
        } else {
            "$selectionStatusParams, Sel : SelectionSet"
        }

        // Build type arguments for QueryResult: T1, T2, ..., TN, S1, S2, ..., SN, Sel
        val tableTypeArgs = (1..tableCount).joinToString(", ") { "T$it" }
        val selectionStatusArgs = (1..tableCount).joinToString(", ") { "S$it" }
        val allTypeArgs = if (tableTypeArgs.isNotEmpty()) {
            "$tableTypeArgs, $selectionStatusArgs, Sel"
        } else {
            "$selectionStatusArgs, Sel"
        }

        appendLine("/**")
        appendLine(" * Accessor for $markerName marker in $tableCount-table query results.")
        appendLine(" * Available when $markerName is selected via .selectAs($markerName) { ... }")
        appendLine(" */")
        appendLine("val <$allTypeParams> QueryResult_$tableCount<$allTypeArgs>.$accessorName: $markerType")
        appendLine("    get() = markerValueCache[$markerName::class] as $markerType")
        appendLine()
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState"
    )
}

/**
 * Generates QueryBuilder_N classes for N-table queries.
 *
 * Example for N=2:
 * class QueryBuilder_2<T1 : TableMarker, T2 : TableMarker>(val state: QueryState) {
 *     fun <R> select(selector: SelectContext_2<T1, T2>.() -> R): QueryBuilder_2<T1, T2>
 *     fun execute(transaction: JdbcTransaction): QueryResultIterable<QueryResult_2<T1, T2>>
 * }
 */
class QueryBuilderNGenerator(
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    private val typeParams = (1..tableCount).joinToString(", ") { "T$it : TableMarker" } + ", " +
            (1..tableCount).joinToString(", ") { "S$it : SelectionStatus" } + ", Sel : SelectionSet"
    private val typeArgs = (1..tableCount).joinToString(", ") { "T$it" } + ", " +
            (1..tableCount).joinToString(", ") { "S$it" } + ", Sel"

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Query builder for $tableCount-table queries using phantom types.")
        appendLine(" * Type parameters T1..T$tableCount encode which specific tables are joined.")
        appendLine(" * Type parameters S1..S$tableCount encode each table's selection status (TableNotSelected initially, TableSelected after .selectAll()).")
        appendLine(" * Type parameter Sel encodes which markers have been selected (NoSelections initially).")
        appendLine(" *")
        appendLine(" * Example: QueryBuilder_$tableCount<PersonMarker, OrderMarker${if (tableCount > 2) ", CompanyMarker" else ""}, TableNotSelected, TableNotSelected${if (tableCount > 2) ", TableNotSelected" else ""}, NoSelections>")
        appendLine(" */")
        appendLine("class QueryBuilder_$tableCount<$typeParams>(")
        appendLine("    val state: QueryState")
        appendLine(") {")
        appendLine()

        // Define table-only type args (without Sel) for contexts
        val tableTypeArgs = (1..tableCount).joinToString(", ") { "T$it" }

        // select method
        appendLine("    /**")
        appendLine("     * Add a column selection to the query.")
        appendLine("     */")
        appendLine("    fun select(selector: SelectContext_$tableCount<$tableTypeArgs>.() -> Expression): QueryBuilder_$tableCount<$typeArgs> {")
        appendLine("        val context = SelectContext_$tableCount<$tableTypeArgs>(state)")
        appendLine("        val expression = context.selector()")
        appendLine("        val column = when (expression) {")
        appendLine("            is com.obabichev.kodama.components.TypedColumn<*, *, *> -> expression.column")
        appendLine("            is com.obabichev.kodama.components.Column<*> -> expression")
        appendLine("            else -> throw IllegalArgumentException(\"Expected Column or TypedColumn, got \${expression::class}\")")
        appendLine("        }")
        appendLine("        state._selectedColumns.add(column)")
        appendLine("        return this")
        appendLine("    }")
        appendLine()

        // NOTE: Generic selectAll(table: Table) method has been REMOVED for compile-time safety.
        // Table-specific selectAll extensions with phantom type constraints are generated separately
        // by TableSelectAllExtensionsGenerator. This ensures you can only select from tables
        // that are actually part of the query (checked at compile time, not runtime).
        appendLine()

        // selectAll for subqueries
        appendLine("    /**")
        appendLine("     * Select all columns from a subquery.")
        appendLine("     */")
        appendLine("    inline fun <reified T : com.obabichev.kodama.schema.SubqueryType> selectAll(marker: T): QueryBuilder_$tableCount<$typeArgs> {")
        appendLine("        // Find the subquery table by alias")
        appendLine("        val subqueryTable = state._subqueryTables[marker.alias]")
        appendLine("            ?: error(\"Subquery table '\${marker.alias}' not found in query state. Available: \${state._subqueryTables.keys}\")")
        appendLine("        state.applySelection(com.obabichev.kodama.query.TableAllSelection(subqueryTable, subqueryTable.allColumns()))")
        appendLine("        return this")
        appendLine("    }")
        appendLine()

        // selectAs method - marker-based selection
        appendLine("    /**")
        appendLine("     * Select an expression with a named marker.")
        appendLine("     * Returns a new QueryBuilder with the marker added to the selection set.")
        appendLine("     *")
        appendLine("     * Example:")
        appendLine("     * ```")
        appendLine("     * .selectAs(TotalRevenue) { sum(order.cost) }")
        appendLine("     * ```")
        appendLine("     */")
        val selectionStatusArgs = (1..tableCount).joinToString(", ") { "S$it" }
        val returnTypeArgs = "$tableTypeArgs, $selectionStatusArgs, Selected<Marker, Sel>"

        appendLine("    inline fun <reified Marker> selectAs(")
        appendLine("        marker: Marker,")
        appendLine("        noinline selector: SelectContext_$tableCount<$tableTypeArgs>.() -> Expression")
        appendLine("    ): QueryBuilder_$tableCount<$returnTypeArgs> {")
        appendLine("        val context = SelectContext_$tableCount<$tableTypeArgs>(state)")
        appendLine("        val expression = context.selector()")
        appendLine("        state.addMarkerSelection(Marker::class, expression)")
        appendLine("        return QueryBuilder_$tableCount(state)")
        appendLine("    }")
        appendLine()

        // where method
        appendLine("    /**")
        appendLine("     * Add a WHERE condition to the query.")
        appendLine("     */")
        appendLine("    fun where(condition: WhereContext_$tableCount<$tableTypeArgs>.() -> Expression): QueryBuilder_$tableCount<$typeArgs> {")
        appendLine("        val context = WhereContext_$tableCount<$tableTypeArgs>(state)")
        appendLine("        state.whereExpression = context.condition()")
        appendLine("        return this")
        appendLine("    }")
        appendLine()

        // orderBy method
        appendLine("    /**")
        appendLine("     * Add ORDER BY clause to the query.")
        appendLine("     */")
        appendLine("    fun orderBy(clause: OrderByContext_$tableCount<$tableTypeArgs>.() -> OrderByClause): QueryBuilder_$tableCount<$typeArgs> {")
        appendLine("        val context = OrderByContext_$tableCount<$tableTypeArgs>(state)")
        appendLine("        val orderByClause = context.clause()")
        appendLine("        state._orderBy.add(orderByClause)")
        appendLine("        return this")
        appendLine("    }")
        appendLine()

        // groupBy method
        appendLine("    /**")
        appendLine("     * Add GROUP BY clause to the query.")
        appendLine("     */")
        appendLine("    fun groupBy(clause: GroupByContext_$tableCount<$tableTypeArgs>.() -> Column<*>): QueryBuilder_$tableCount<$typeArgs> {")
        appendLine("        val context = GroupByContext_$tableCount<$tableTypeArgs>(state)")
        appendLine("        val column = context.clause()")
        appendLine("        state._groupBy.add(column)")
        appendLine("        return this")
        appendLine("    }")
        appendLine()

        // limit method
        appendLine("    /**")
        appendLine("     * Add LIMIT clause to the query.")
        appendLine("     */")
        appendLine("    fun limit(count: Int): QueryBuilder_$tableCount<$typeArgs> {")
        appendLine("        state._limit = count")
        appendLine("        return this")
        appendLine("    }")
        appendLine()

        // offset method
        appendLine("    /**")
        appendLine("     * Add OFFSET clause to the query.")
        appendLine("     */")
        appendLine("    fun offset(count: Int): QueryBuilder_$tableCount<$typeArgs> {")
        appendLine("        state._offset = count")
        appendLine("        return this")
        appendLine("    }")
        appendLine()

        // build method
        appendLine("    /**")
        appendLine("     * Build the Query object without executing it.")
        appendLine("     */")
        appendLine("    fun build(): Query {")
        appendLine("        return Query(")
        appendLine("            select = state._selectedColumns,")
        appendLine("            from = state._from!!,")
        appendLine("            joins = state._joins,")
        appendLine("            whereExpression = state.whereExpression,")
        appendLine("            orderBy = state._orderBy,")
        appendLine("            relations = state.relations,")
        appendLine("            aggregates = state._aggregateSelections,")
        appendLine("            groupBy = state._groupBy,")
        appendLine("            selectables = state._selectables,")
        appendLine("            limit = state._limit,")
        appendLine("            offset = state._offset")
        appendLine("        )")
        appendLine("    }")
        appendLine()

        // execute method
        appendLine("    /**")
        appendLine("     * Execute the query and return results.")
        appendLine("     */")
        appendLine("    fun execute(transaction: JdbcTransaction): QueryResultIterable_$tableCount<$typeArgs> {")
        appendLine("        val query = Query(")
        appendLine("            select = state._selectedColumns,")
        appendLine("            from = state._from!!,")
        appendLine("            joins = state._joins,")
        appendLine("            whereExpression = state.whereExpression,")
        appendLine("            orderBy = state._orderBy,")
        appendLine("            relations = state.relations,")
        appendLine("            aggregates = state._aggregateSelections,")
        appendLine("            groupBy = state._groupBy,")
        appendLine("            selectables = state._selectables,")
        appendLine("            limit = state._limit,")
        appendLine("            offset = state._offset")
        appendLine("        )")
        appendLine("        val resultSet = transaction.execute(query)")
        appendLine("        return QueryResultIterable_$tableCount(resultSet, state)")
        appendLine("    }")
        appendLine()

        appendLine("}")
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState",
        "com.obabichev.kodama.query.Query",
        "com.obabichev.kodama.query.TableAllSelection",
        "com.obabichev.kodama.execute.JdbcTransaction",
        "com.obabichev.kodama.components.expression.Expression",
        "com.obabichev.kodama.components.Column",
        "com.obabichev.kodama.schema.Table",
        "java.sql.ResultSet"
    )
}

/**
 * Generates SelectContext_N for N-table queries.
 */
class SelectContextNGenerator(
    private val tableCount: Int,
    private val generatedPackage: String
) : CodeGenerator {

    private val typeParams = (1..tableCount).joinToString(", ") { "T$it : TableMarker" }

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Select context for $tableCount-table queries.")
        appendLine(" * Extension functions provide table accessors based on phantom types.")
        appendLine(" */")
        appendLine("class SelectContext_$tableCount<$typeParams>(")
        appendLine("    val state: QueryState")
        appendLine(")")
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState",
        "com.obabichev.kodama.components.expression.Expression"
    )
}

/**
 * Generates WhereContext_N for N-table queries.
 */
class WhereContextNGenerator(
    private val tableCount: Int,
    private val generatedPackage: String
) : CodeGenerator {

    private val typeParams = (1..tableCount).joinToString(", ") { "T$it : TableMarker" }

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Where context for $tableCount-table queries.")
        appendLine(" * Extension functions provide table accessors based on phantom types.")
        appendLine(" */")
        appendLine("class WhereContext_$tableCount<$typeParams>(")
        appendLine("    val state: QueryState")
        appendLine(")")
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState",
        "com.obabichev.kodama.components.expression.Expression"
    )
}

/**
 * Generates QueryResultIterable_N for N-table queries.
 */
class QueryResultIterableNGenerator(
    private val tableCount: Int,
    private val generatedPackage: String
) : CodeGenerator {

    private val typeParams = (1..tableCount).joinToString(", ") { "T$it : TableMarker" } + ", " +
            (1..tableCount).joinToString(", ") { "S$it : SelectionStatus" } + ", Sel : SelectionSet"
    private val typeArgs = (1..tableCount).joinToString(", ") { "T$it" } + ", " +
            (1..tableCount).joinToString(", ") { "S$it" } + ", Sel"

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Result iterable for $tableCount-table queries.")
        appendLine(" * S1..S$tableCount parameters track which tables have been selected (per-position status).")
        appendLine(" * Sel parameter tracks which markers have been selected.")
        appendLine(" */")
        appendLine("class QueryResultIterable_$tableCount<$typeParams>(")
        appendLine("    private val resultSet: ResultSet,")
        appendLine("    private val state: QueryState")
        appendLine(") : Iterable<QueryResult_$tableCount<$typeArgs>> {")
        appendLine()
        appendLine("    override fun iterator(): Iterator<QueryResult_$tableCount<$typeArgs>> {")
        appendLine("        return object : Iterator<QueryResult_$tableCount<$typeArgs>> {")
        appendLine("            override fun hasNext() = resultSet.next()")
        appendLine("            override fun next() = QueryResult_$tableCount<$typeArgs>(resultSet, state)")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
        appendLine()

        appendLine("/**")
        appendLine(" * Result row for $tableCount-table queries.")
        appendLine(" * Table accessors are provided via extension functions based on phantom types.")
        appendLine(" * Table accessors require that the table was selected (constrained by S1..S$tableCount : TableSelected).")
        appendLine(" * Marker accessors (e.g., row.totalRevenue) are provided for selected markers in Sel.")
        appendLine(" */")
        appendLine("class QueryResult_$tableCount<$typeParams>(")
        appendLine("    val resultSet: ResultSet,")
        appendLine("    val state: QueryState")
        appendLine(") {")
        appendLine("    /**")
        appendLine("     * Cached marker values read from ResultSet when this row was created.")
        appendLine("     * Prevents issues with accessing values after ResultSet has moved.")
        appendLine("     */")
        appendLine("    internal val markerValueCache: Map<kotlin.reflect.KClass<*>, Any?> = buildMap {")
        appendLine("        state._markerSelections.keys.forEach { markerClass ->")
        appendLine("            put(markerClass, state.getMarkerValue(markerClass, resultSet))")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState",
        "java.sql.ResultSet"
    )
}

/**
 * Generates table-specific extensions for SelectContext_N.
 *
 * Example for Person table:
 * inline fun <T2, T3> SelectContext_3<PersonMarker, T2, T3>.person(): PersonAccessor
 * inline fun <T1, T3> SelectContext_3<T1, PersonMarker, T3>.person(): PersonAccessor
 * inline fun <T1, T2> SelectContext_3<T1, T2, PersonMarker>.person(): PersonAccessor
 */
class TableContextExtensionsGenerator(
    private val tableName: String,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Extension functions to access $tableName in $tableCount-table contexts.")
        appendLine(" * These are generated for each position where $tableName can appear.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val fullTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${tableName}Marker" else "T$i"
            }.joinToString(", ")

            val paramsList = if (otherParams.isNotEmpty()) "<$otherParams>" else ""

            val accessorName = tableName.replaceFirstChar { it.lowercase() }
            appendLine("/**")
            appendLine(" * Access $tableName when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList SelectContext_$tableCount<$fullTypeArgs>.$accessorName: ${tableName}Accessor")
            appendLine("    @JvmName(\"get_${accessorName}_SelectCtx${tableCount}_P${position}\")")
            appendLine("    get() = ${tableName}Accessor(TableAccessor($tableName, state.relations))")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$tableName",
        "${schemaPackage}.generated.${tableName}Accessor",
        "com.obabichev.kodama.query.TableAccessor"
    )
}

/**
 * Generates join() extension for a specific relationship (from → to).
 *
 * Example for Person → Order:
 * fun QueryBuilder_1<PersonMarker>.join(
 *     table: Order,
 *     condition: JoinContext_Person_Order.() -> Expression
 * ): QueryBuilder_2<PersonMarker, OrderMarker> {
 *     val context = JoinContext_Person_Order(state, Order)
 *     val joinCondition = context.condition()
 *     state.addJoin(Join(Order.relation, joinCondition, JoinType.INNER))
 *     return QueryBuilder_2(state)
 * }
 */
class PhantomJoinExtensionGenerator(
    private val fromTable: String,
    private val toTable: String,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Generate all join type variants (including innerJoin alias)
        val joinTypes = listOf(
            "join" to "INNER",
            "innerJoin" to "INNER",  // Alias for join()
            "leftJoin" to "LEFT",
            "rightJoin" to "RIGHT",
            "fullJoin" to "FULL"
        )

        joinTypes.forEach { (methodName, joinType) ->
            appendLine("/**")
            appendLine(" * ${joinType.replaceFirstChar { it.uppercase() }} JOIN $toTable table to a query starting from $fromTable.")
            appendLine(" * This join is validated at compile-time via relationship declaration.")
            appendLine(" * Preserves selection status S1 from first table, adds TableNotSelected for joined table.")
            appendLine(" */")
            appendLine("@JvmName(\"${methodName}_${fromTable}_to_${toTable}\")")
            appendLine("fun <S1 : SelectionStatus, Sel : SelectionSet> $generatedPackage.QueryBuilder_1<$generatedPackage.${fromTable}Marker, S1, Sel>.$methodName(")
            appendLine("    table: $schemaPackage.$toTable,")
            appendLine("    condition: $generatedPackage.JoinContext_${fromTable}_$toTable.() -> com.obabichev.kodama.components.expression.Expression")
            appendLine("): $generatedPackage.QueryBuilder_2<$generatedPackage.${fromTable}Marker, $generatedPackage.${toTable}Marker, S1, TableNotSelected, Sel> {")
            appendLine("    val context = $generatedPackage.JoinContext_${fromTable}_$toTable(state, table)")
            appendLine("    val joinCondition = context.condition()")
            appendLine("    state._joins.add(com.obabichev.kodama.components.Join(")
            appendLine("        com.obabichev.kodama.components.JoinType.$joinType,")
            appendLine("        state.relations.relation(table),")
            appendLine("        joinCondition")
            appendLine("    ))")
            appendLine("    return $generatedPackage.QueryBuilder_2(state)")
            appendLine("}")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$fromTable",
        "$schemaPackage.$toTable",
        "$generatedPackage.QueryBuilder_1",
        "$generatedPackage.QueryBuilder_2",
        "$generatedPackage.${fromTable}Marker",
        "$generatedPackage.${toTable}Marker",
        "$generatedPackage.JoinContext_${fromTable}_$toTable",
        "com.obabichev.kodama.components.expression.Expression",
        "com.obabichev.kodama.components.Join",
        "com.obabichev.kodama.components.JoinType"
    )
}

/**
 * Generates JoinContext for a specific relationship (from → to).
 *
 * Provides table accessors within join condition lambdas for type-safe column access.
 *
 * Example for Person → Order:
 * class JoinContext_Person_Order(
 *     val state: QueryState,
 *     private val joiningTable: Order
 * ) {
 *     val person: PersonAccessor = PersonAccessor(TableAccessor(Person, state.relations))
 *     val order: OrderAccessor = OrderAccessor(TableAccessor(joiningTable, state.relations))
 * }
 */
class PhantomJoinContextGenerator(
    private val fromTable: String,
    private val toTable: String,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Join context for $fromTable → $toTable relationship.")
        appendLine(" * Provides type-safe access to both tables within join conditions.")
        appendLine(" */")
        appendLine("class JoinContext_${fromTable}_$toTable(")
        appendLine("    val state: com.obabichev.kodama.query.QueryState,")
        appendLine("    private val joiningTable: $schemaPackage.$toTable")
        appendLine(") {")
        appendLine("    val ${fromTable.replaceFirstChar { it.lowercase() }}: $generatedPackage.${fromTable}Accessor = $generatedPackage.${fromTable}Accessor(")
        appendLine("        com.obabichev.kodama.query.TableAccessor($schemaPackage.$fromTable, state.relations)")
        appendLine("    )")
        appendLine("    val ${toTable.replaceFirstChar { it.lowercase() }}: $generatedPackage.${toTable}Accessor = $generatedPackage.${toTable}Accessor(")
        appendLine("        com.obabichev.kodama.query.TableAccessor(joiningTable, state.relations)")
        appendLine("    )")
        appendLine("}")
        appendLine()
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$fromTable",
        "$schemaPackage.$toTable",
        "$generatedPackage.${fromTable}Accessor",
        "$generatedPackage.${toTable}Accessor",
        "com.obabichev.kodama.query.QueryState",
        "com.obabichev.kodama.query.TableAccessor"
    )
}

/**
 * Generates join() extensions for multi-table queries (QueryBuilder_N → QueryBuilder_{N+1}).
 *
 * For a relationship like Order → Company, generates join() for:
 * - QueryBuilder_2 (with Order in position 1 or 2) → QueryBuilder_3
 * - QueryBuilder_3 (with Order in position 1, 2, or 3) → QueryBuilder_4
 * - QueryBuilder_4 (with Order in position 1, 2, 3, or 4) → QueryBuilder_5
 *
 * Example for Order → Company at position 2 in QueryBuilder_2:
 * ```kotlin
 * fun <T1 : TableMarker> QueryBuilder_2<T1, OrderMarker>.join(
 *     table: Company,
 *     condition: MultiTableJoinContext_3<T1, OrderMarker, CompanyMarker>.() -> Expression
 * ): QueryBuilder_3<T1, OrderMarker, CompanyMarker>
 * ```
 */
class PhantomMultiTableJoinExtensionGenerator(
    private val fromTable: String,
    private val toTable: String,
    private val currentTableCount: Int,  // N (current number of tables in query)
    private val fromPosition: Int,       // Which position the fromTable is in (1 to N)
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    private val nextTableCount = currentTableCount + 1

    override fun generate(): String = buildString {
        // Build type parameters: T1, T2, ..., TN (excluding the position where fromTable is) + S1...SN + Sel
        val otherTableParams = (1..currentTableCount)
            .filter { it != fromPosition }
            .joinToString(", ") { "T$it : TableMarker" }
        val selectionStatusParams = (1..currentTableCount)
            .joinToString(", ") { "S$it : SelectionStatus" }
        val otherTypeParams = if (otherTableParams.isNotEmpty()) {
            "$otherTableParams, $selectionStatusParams, Sel : SelectionSet"
        } else {
            "$selectionStatusParams, Sel : SelectionSet"
        }

        // Build full type arguments for QueryBuilder_N, with fromTable at fromPosition
        val currentTableTypeArgs = (1..currentTableCount).map { i ->
            if (i == fromPosition) "${fromTable}Marker" else "T$i"
        }.joinToString(", ")
        val currentSelectionStatusArgs = (1..currentTableCount).joinToString(", ") { "S$it" }
        val currentTypeArgs = "$currentTableTypeArgs, $currentSelectionStatusArgs, Sel"

        // Build type arguments for QueryBuilder_{N+1} (all current tables + toTable, all current S + TableNotSelected)
        val nextTableTypeArgs = "$currentTableTypeArgs, ${toTable}Marker"
        val nextSelectionStatusArgs = "$currentSelectionStatusArgs, TableNotSelected"  // New table starts as TableNotSelected
        val nextTypeArgs = "$nextTableTypeArgs, $nextSelectionStatusArgs, Sel"

        // Build type arguments for MultiTableJoinContext_{N+1} (table types only, no selection statuses/Sel)
        val nextContextTypeArgs = nextTableTypeArgs

        // Build type parameter list for the function
        val funcTypeParams = "<$otherTypeParams>"

        // Generate all join type variants (including innerJoin alias)
        val joinTypes = listOf(
            "join" to "INNER",
            "innerJoin" to "INNER",  // Alias for join()
            "leftJoin" to "LEFT",
            "rightJoin" to "RIGHT",
            "fullJoin" to "FULL"
        )

        joinTypes.forEach { (methodName, joinType) ->
            appendLine("/**")
            appendLine(" * ${joinType.replaceFirstChar { it.uppercase() }} JOIN $toTable to a ${currentTableCount}-table query where $fromTable is in position $fromPosition.")
            appendLine(" */")
            appendLine("@JvmName(\"${methodName}_${fromTable}_to_${toTable}_T${currentTableCount}_P${fromPosition}\")")
            appendLine("fun $funcTypeParams QueryBuilder_$currentTableCount<$currentTypeArgs>.$methodName(")
            appendLine("    table: $toTable,")
            appendLine("    condition: MultiTableJoinContext_${nextTableCount}<$nextContextTypeArgs>.() -> Expression")
            appendLine("): QueryBuilder_$nextTableCount<$nextTypeArgs> {")
            appendLine("    val context = MultiTableJoinContext_${nextTableCount}<$nextContextTypeArgs>(state, table)")
            appendLine("    val joinCondition = context.condition()")
            appendLine("    state._joins.add(com.obabichev.kodama.components.Join(")
            appendLine("        com.obabichev.kodama.components.JoinType.$joinType,")
            appendLine("        state.relations.relation(table),")
            appendLine("        joinCondition")
            appendLine("    ))")
            appendLine("    return QueryBuilder_$nextTableCount(state)")
            appendLine("}")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$fromTable",
        "$schemaPackage.$toTable",
        "$generatedPackage.QueryBuilder_$currentTableCount",
        "$generatedPackage.QueryBuilder_$nextTableCount",
        "$generatedPackage.${fromTable}Marker",
        "$generatedPackage.${toTable}Marker",
        "$generatedPackage.MultiTableJoinContext_$nextTableCount",
        "com.obabichev.kodama.components.expression.Expression",
        "com.obabichev.kodama.components.Join",
        "com.obabichev.kodama.components.JoinType"
    )
}

/**
 * Generates MultiTableJoinContext_N for N-table queries.
 *
 * Provides access to all N tables in the query (N-1 existing + 1 new).
 * Table accessors are provided via extension functions based on phantom types.
 *
 * Example for 3 tables:
 * ```kotlin
 * class MultiTableJoinContext_3<T1 : TableMarker, T2 : TableMarker, T3 : TableMarker>(
 *     val state: QueryState,
 *     private val joiningTable: Table
 * )
 * ```
 *
 * Extension functions provide table access:
 * ```kotlin
 * inline val <T2, T3> MultiTableJoinContext_3<PersonMarker, T2, T3>.person: PersonAccessor
 * ```
 */
class MultiTableJoinContextNGenerator(
    private val tableCount: Int,
    private val generatedPackage: String
) : CodeGenerator {

    private val typeParams = (1..tableCount).joinToString(", ") { "T$it : TableMarker" }

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Join context for $tableCount-table queries.")
        appendLine(" * Provides access to all $tableCount tables via extension functions.")
        appendLine(" */")
        appendLine("class MultiTableJoinContext_$tableCount<$typeParams>(")
        appendLine("    val state: QueryState,")
        appendLine("    val joiningTable: Table")
        appendLine(")")
        appendLine()
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState",
        "com.obabichev.kodama.schema.Table"
    )
}

/**
 * Generates table accessor extensions for MultiTableJoinContext_N.
 *
 * For each table and each position it can appear in, generates:
 * ```kotlin
 * inline val <T2, T3> MultiTableJoinContext_3<PersonMarker, T2, T3>.person: PersonAccessor
 *     get() = PersonAccessor(TableAccessor(Person, state.relations))
 * ```
 */
class MultiTableJoinContextExtensionsGenerator(
    private val tableName: String,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Extension functions to access $tableName in MultiTableJoinContext_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val fullTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${tableName}Marker" else "T$i"
            }.joinToString(", ")

            val paramsList = if (otherParams.isNotEmpty()) "<$otherParams>" else ""

            val accessorName = tableName.replaceFirstChar { it.lowercase() }
            appendLine("/**")
            appendLine(" * Access $tableName when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList MultiTableJoinContext_$tableCount<$fullTypeArgs>.$accessorName: ${tableName}Accessor")
            appendLine("    @JvmName(\"get_${accessorName}_Context${tableCount}_P${position}\")")
            appendLine("    get() = ${tableName}Accessor(TableAccessor($tableName, state.relations))")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$tableName",
        "${schemaPackage}.generated.${tableName}Accessor",
        "com.obabichev.kodama.query.TableAccessor"
    )
}

/**
 * Generates table accessor extensions for QueryResult_N.
 *
 * For each table and each position it can appear in, generates:
 * ```kotlin
 * inline val <T2, T3> QueryResult_3<PersonMarker, T2, T3>.person: PersonResultAccessor_All_NonNull
 *     get() = PersonResultAccessor_All_NonNull(resultSet, state.relations, state._selectedColumns)
 * ```
 */
class TableResultExtensionsGenerator(
    private val tableName: String,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Extension functions to access $tableName results in QueryResult_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            // Build other table type parameters (exclude fixed position)
            val otherTableParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }

            // Build all selection status parameters
            val selectionStatusParams = (1..tableCount)
                .joinToString(", ") { "S$it : SelectionStatus" }

            // Build table type arguments for QueryResult
            val tableTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${tableName}Marker" else "T$i"
            }.joinToString(", ")

            // Build selection status arguments
            val selectionStatusArgs = (1..tableCount).joinToString(", ") { "S$it" }

            // Full type arguments for QueryResult_N
            val fullTypeArgs = "$tableTypeArgs, $selectionStatusArgs, Sel"

            // Build type parameters list
            val paramsList = if (otherTableParams.isNotEmpty()) {
                "<$otherTableParams, $selectionStatusParams, Sel : SelectionSet>"
            } else {
                "<$selectionStatusParams, Sel : SelectionSet>"
            }

            // Build where clause - constrain this position's selection status to TableSelected
            val whereClause = "where S$position : TableSelected"

            val accessorName = tableName.replaceFirstChar { it.lowercase() }
            appendLine("/**")
            appendLine(" * Access $tableName results when it's in position $position.")
            appendLine(" * Only available when $tableName has been selected via .selectAll($tableName).")
            appendLine(" * Requires S$position : TableSelected (compile-time enforced).")
            if (tableCount == 1) {
                appendLine(" * Single-table query - properties are non-nullable.")
            } else {
                appendLine(" * Multi-table query - properties are nullable (safe for all join types).")
                appendLine(" *")
                appendLine(" * LIMITATION: This is conservative for INNER JOIN where columns could be non-nullable.")
                appendLine(" * The join type (INNER/LEFT/RIGHT/FULL) is determined at runtime but accessor types")
                appendLine(" * are determined at compile-time, so we must be conservative and use nullable accessors")
                appendLine(" * for all multi-table queries to handle RIGHT/FULL joins where any table can be NULL.")
                appendLine(" *")
                appendLine(" * Future improvement: Encode join types in phantom types for full compile-time safety.")
            }
            appendLine(" */")

            if (tableCount == 1) {
                // Single-table query - always non-nullable
                appendLine("inline val $paramsList QueryResult_$tableCount<$fullTypeArgs>.$accessorName: ${tableName}ResultAccessor_All_NonNull")
                appendLine("    $whereClause")
                appendLine("    @JvmName(\"get_${accessorName}_T${tableCount}_P${position}\")")
                appendLine("    get() = ${tableName}ResultAccessor_All_NonNull(resultSet, state.relations, state._selectedColumns)")
            } else {
                // Multi-table query - always use nullable accessor for safety
                // This correctly handles LEFT/RIGHT/FULL joins and is safe (albeit conservative) for INNER joins
                appendLine("inline val $paramsList QueryResult_$tableCount<$fullTypeArgs>.$accessorName: ${tableName}ResultAccessor_All_Nullable")
                appendLine("    $whereClause")
                appendLine("    @JvmName(\"get_${accessorName}_T${tableCount}_P${position}\")")
                appendLine("    get() = ${tableName}ResultAccessor_All_Nullable(resultSet, state.relations, state._selectedColumns)")
            }
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$tableName",
        "${schemaPackage}.generated.${tableName}ResultAccessor_All_NonNull",
        "${schemaPackage}.generated.${tableName}ResultAccessor_All_Nullable"
    )
}

/**
 * Generates table accessor extensions for WhereContext_N.
 */
class TableWhereExtensionsGenerator(
    private val tableName: String,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Extension functions to access $tableName in WhereContext_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val fullTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${tableName}Marker" else "T$i"
            }.joinToString(", ")

            val paramsList = if (otherParams.isNotEmpty()) "<$otherParams>" else ""

            val accessorName = tableName.replaceFirstChar { it.lowercase() }
            appendLine("/**")
            appendLine(" * Access $tableName when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList WhereContext_$tableCount<$fullTypeArgs>.$accessorName: ${tableName}Accessor")
            appendLine("    @JvmName(\"get_${accessorName}_WhereCtx${tableCount}_P${position}\")")
            appendLine("    get() = ${tableName}Accessor(TableAccessor($tableName, state.relations))")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$tableName",
        "${schemaPackage}.generated.${tableName}Accessor",
        "com.obabichev.kodama.query.TableAccessor"
    )
}

/**
 * Generates OrderByContext_N for N-table queries.
 */
class OrderByContextNGenerator(
    private val tableCount: Int,
    private val generatedPackage: String
) : CodeGenerator {

    private val typeParams = (1..tableCount).joinToString(", ") { "T$it : TableMarker" }

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Order by context for $tableCount-table queries.")
        appendLine(" */")
        appendLine("class OrderByContext_$tableCount<$typeParams>(")
        appendLine("    val state: QueryState")
        appendLine(") {")
        appendLine("    /**")
        appendLine("     * Extension function to capture OrderByClause and add to query state.")
        appendLine("     * This allows syntax: .orderBy { person.age.desc() }")
        appendLine("     */")
        appendLine("    operator fun OrderByClause.unaryPlus() {")
        appendLine("        state._orderBy.add(this)")
        appendLine("    }")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState",
        "com.obabichev.kodama.query.OrderByClause"
    )
}

/**
 * Generates GroupByContext_N for N-table queries.
 */
class GroupByContextNGenerator(
    private val tableCount: Int,
    private val generatedPackage: String
) : CodeGenerator {

    private val typeParams = (1..tableCount).joinToString(", ") { "T$it : TableMarker" }

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Group by context for $tableCount-table queries.")
        appendLine(" */")
        appendLine("class GroupByContext_$tableCount<$typeParams>(")
        appendLine("    val state: QueryState")
        appendLine(")")
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState"
    )
}

/**
 * Generates table accessor extensions for OrderByContext_N.
 */
class TableOrderByExtensionsGenerator(
    private val tableName: String,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Extension functions to access $tableName in OrderByContext_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val fullTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${tableName}Marker" else "T$i"
            }.joinToString(", ")

            val paramsList = if (otherParams.isNotEmpty()) "<$otherParams>" else ""

            val accessorName = tableName.replaceFirstChar { it.lowercase() }
            appendLine("/**")
            appendLine(" * Access $tableName when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList OrderByContext_$tableCount<$fullTypeArgs>.$accessorName: ${tableName}OrderByAccessor")
            appendLine("    @JvmName(\"get_${accessorName}_OrderByCtx${tableCount}_P${position}\")")
            appendLine("    get() = ${tableName}OrderByAccessor(TableAccessor($tableName, state.relations))")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$tableName",
        "${schemaPackage}.generated.${tableName}OrderByAccessor",
        "com.obabichev.kodama.query.TableAccessor"
    )
}

/**
 * Generates table accessor extensions for GroupByContext_N.
 */
class TableGroupByExtensionsGenerator(
    private val tableName: String,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Extension functions to access $tableName in GroupByContext_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val fullTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${tableName}Marker" else "T$i"
            }.joinToString(", ")

            val paramsList = if (otherParams.isNotEmpty()) "<$otherParams>" else ""

            val accessorName = tableName.replaceFirstChar { it.lowercase() }
            appendLine("/**")
            appendLine(" * Access $tableName when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList GroupByContext_$tableCount<$fullTypeArgs>.$accessorName: ${tableName}GroupByAccessor")
            appendLine("    @JvmName(\"get_${accessorName}_GroupByCtx${tableCount}_P${position}\")")
            appendLine("    get() = ${tableName}GroupByAccessor(TableAccessor($tableName, state.relations))")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "$schemaPackage.$tableName",
        "${schemaPackage}.generated.${tableName}GroupByAccessor",
        "com.obabichev.kodama.query.TableAccessor"
    )
}

/**
 * Generates fromAliased() function for a subquery using phantom types.
 *
 * Example for UsersWithOrders subquery:
 * ```kotlin
 * inline fun <reified T> fromAliased(
 *     marker: T,
 *     queryBuilder: () -> Query
 * ): QueryBuilder_1<UsersWithOrdersMarker, NoSelections>
 *     where T : UsersWithOrders {
 *     val query = queryBuilder()
 *     val subqueryTable = SubqueryRegistry.createSubquery(T::class, query) as SubqueryTable_UsersWithOrders
 *     val state = QueryState()
 *     state._from = state.relations.relation(subqueryTable)
 *     state._subqueryTables[subqueryTable.alias] = subqueryTable
 *     return QueryBuilder_1(state)
 * }
 * ```
 */
class PhantomFromAliasedGenerator(
    private val subqueryInfo: com.obabichev.kodama.compiler.data.SubqueryInfo,
    private val generatedPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val subqueryName = subqueryInfo.name
        val subqueryTableClassName = subqueryInfo.subqueryTableClassName

        appendLine("/**")
        appendLine(" * Start a query from $subqueryName subquery with inline definition.")
        appendLine(" */")
        appendLine("inline fun <reified T> fromAliased(")
        appendLine("    marker: T,")
        appendLine("    queryBuilder: () -> Query")
        appendLine("): QueryBuilder_1<${subqueryName}Marker, NoSelections>")
        appendLine("    where T : $subqueryName {")
        appendLine("    val query = queryBuilder()")
        appendLine("    val subqueryTable = SubqueryRegistry.createSubquery(T::class, query) as $subqueryTableClassName")
        appendLine("    val state = QueryState()")
        appendLine("    state._from = state.relations.relation(subqueryTable)")
        appendLine("    state._subqueryTables[subqueryTable.alias] = subqueryTable")
        appendLine("    return QueryBuilder_1(state)")
        appendLine("}")
        appendLine()
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.query.QueryState",
        "com.obabichev.kodama.query.Query"
    )
}

/**
 * Generates subquery accessor extensions for SelectContext_N.
 *
 * Example for UsersWithOrders subquery at position 1 in 2-table context:
 * ```kotlin
 * inline val <T2 : TableMarker> SelectContext_2<UsersWithOrdersMarker, T2>.usersWithOrders: UsersWithOrdersAccessor
 *     get() {
 *         val table = state._subqueryTables["users_with_orders"] ?: error("Subquery users_with_orders not found")
 *         return UsersWithOrdersAccessor(TableAccessor(table, state.relations))
 *     }
 * ```
 */
class SubqueryContextExtensionsGenerator(
    private val subqueryInfo: com.obabichev.kodama.compiler.data.SubqueryInfo,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val subqueryName = subqueryInfo.name
        val subqueryAlias = subqueryInfo.name.toSnakeCase()
        val accessorName = subqueryInfo.name.replaceFirstChar { it.lowercase() }
        val accessorClassName = "${subqueryInfo.name}Accessor"

        appendLine("/**")
        appendLine(" * Extension functions to access $subqueryName in SelectContext_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val fullTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${subqueryName}Marker" else "T$i"
            }.joinToString(", ")

            val paramsList = if (otherParams.isNotEmpty()) "<$otherParams>" else ""

            appendLine("/**")
            appendLine(" * Access $subqueryName when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList SelectContext_$tableCount<$fullTypeArgs>.$accessorName: $accessorClassName")
            appendLine("    @JvmName(\"get_${accessorName}_select_T${tableCount}_P${position}\")")
            appendLine("    get() {")
            appendLine("        val table = state._subqueryTables[\"$subqueryAlias\"] ?: error(\"Subquery $subqueryAlias not found\")")
            appendLine("        return $accessorClassName(TableAccessor(table, state.relations))")
            appendLine("    }")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "${schemaPackage}.generated.$accessorClassName",
        "com.obabichev.kodama.query.TableAccessor"
    )

    private val accessorClassName: String
        get() = "${subqueryInfo.name}Accessor"
}

/**
 * Generates subquery accessor extensions for WhereContext_N.
 */
class SubqueryWhereExtensionsGenerator(
    private val subqueryInfo: com.obabichev.kodama.compiler.data.SubqueryInfo,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val subqueryName = subqueryInfo.name
        val subqueryAlias = subqueryInfo.name.toSnakeCase()
        val accessorName = subqueryInfo.name.replaceFirstChar { it.lowercase() }
        val accessorClassName = "${subqueryInfo.name}Accessor"

        appendLine("/**")
        appendLine(" * Extension functions to access $subqueryName in WhereContext_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val fullTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${subqueryName}Marker" else "T$i"
            }.joinToString(", ")

            val paramsList = if (otherParams.isNotEmpty()) "<$otherParams>" else ""

            appendLine("/**")
            appendLine(" * Access $subqueryName when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList WhereContext_$tableCount<$fullTypeArgs>.$accessorName: $accessorClassName")
            appendLine("    @JvmName(\"get_${accessorName}_where_T${tableCount}_P${position}\")")
            appendLine("    get() {")
            appendLine("        val table = state._subqueryTables[\"$subqueryAlias\"] ?: error(\"Subquery $subqueryAlias not found\")")
            appendLine("        return $accessorClassName(TableAccessor(table, state.relations))")
            appendLine("    }")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "${schemaPackage}.generated.$accessorClassName",
        "com.obabichev.kodama.query.TableAccessor"
    )

    private val accessorClassName: String
        get() = "${subqueryInfo.name}Accessor"
}

/**
 * Generates subquery accessor extensions for MultiTableJoinContext_N.
 */
class SubqueryJoinExtensionsGenerator(
    private val subqueryInfo: com.obabichev.kodama.compiler.data.SubqueryInfo,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val subqueryName = subqueryInfo.name
        val subqueryAlias = subqueryInfo.name.toSnakeCase()
        val accessorName = subqueryInfo.name.replaceFirstChar { it.lowercase() }
        val accessorClassName = "${subqueryInfo.name}Accessor"

        appendLine("/**")
        appendLine(" * Extension functions to access $subqueryName in MultiTableJoinContext_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val fullTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${subqueryName}Marker" else "T$i"
            }.joinToString(", ")

            val paramsList = if (otherParams.isNotEmpty()) "<$otherParams>" else ""

            appendLine("/**")
            appendLine(" * Access $subqueryName when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList MultiTableJoinContext_$tableCount<$fullTypeArgs>.$accessorName: $accessorClassName")
            appendLine("    @JvmName(\"get_${accessorName}_join_T${tableCount}_P${position}\")")
            appendLine("    get() {")
            appendLine("        val table = state._subqueryTables[\"$subqueryAlias\"] ?: error(\"Subquery $subqueryAlias not found\")")
            appendLine("        return $accessorClassName(TableAccessor(table, state.relations))")
            appendLine("    }")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "${schemaPackage}.generated.$accessorClassName",
        "com.obabichev.kodama.query.TableAccessor"
    )

    private val accessorClassName: String
        get() = "${subqueryInfo.name}Accessor"
}

/**
 * Generates subquery accessor extensions for QueryResult_N.
 */
class SubqueryResultExtensionsGenerator(
    private val subqueryInfo: com.obabichev.kodama.compiler.data.SubqueryInfo,
    private val tableCount: Int,
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val subqueryName = subqueryInfo.name
        val accessorName = subqueryInfo.name.replaceFirstChar { it.lowercase() }
        val resultAccessorClassName = "${subqueryInfo.name}ResultAccessor_All_NonNull"

        appendLine("/**")
        appendLine(" * Extension functions to access $subqueryName results in QueryResult_$tableCount.")
        appendLine(" */")
        appendLine()

        // Generate extension for each type parameter position
        for (position in 1..tableCount) {
            val otherParams = (1..tableCount).filter { it != position }
                .joinToString(", ") { "T$it : TableMarker" }
            val tableTypeArgs = (1..tableCount).map { i ->
                if (i == position) "${subqueryName}Marker" else "T$i"
            }.joinToString(", ")

            // Add Sel parameter to match QueryResult_N signature
            val fullTypeArgs = "$tableTypeArgs, Sel"
            val paramsList = if (otherParams.isNotEmpty()) {
                "<$otherParams, Sel : SelectionSet>"
            } else {
                "<Sel : SelectionSet>"
            }

            appendLine("/**")
            appendLine(" * Access $subqueryName results when it's in position $position.")
            appendLine(" */")
            appendLine("inline val $paramsList QueryResult_$tableCount<$fullTypeArgs>.$accessorName: $resultAccessorClassName")
            appendLine("    @JvmName(\"get_${accessorName}_T${tableCount}_P${position}\")")
            appendLine("    get() = $resultAccessorClassName(resultSet, state.relations, state._selectedColumns)")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> = setOf(
        "${schemaPackage}.generated.$resultAccessorClassName"
    )

    private val resultAccessorClassName: String
        get() = "${subqueryInfo.name}ResultAccessor_All_NonNull"
}

/**
 * Generates joinAliased() method for adding a subquery to a query.
 *
 * Example for joining UsersWithOrders to a single-table query:
 * ```kotlin
 * inline fun <T1 : TableMarker, Sel : SelectionSet> QueryBuilder_1<T1, Sel>.joinAliased(
 *     subquery: UsersWithOrders,
 *     crossinline condition: MultiTableJoinContext_2<T1, UsersWithOrdersMarker>.() -> Expression
 * ): QueryBuilder_2<T1, UsersWithOrdersMarker, Sel> {
 *     val table = subquery as SubqueryTable
 *     state._subqueryTables[table.alias] = table
 *     val join = Join(
 *         type = JoinType.INNER,
 *         relation = state.relations.relation(table),
 *         condition = {
 *             val context = MultiTableJoinContext_2<T1, UsersWithOrdersMarker>(state, table)
 *             context.condition()
 *         }()
 *     )
 *     state._joins.add(join)
 *     return QueryBuilder_2(state)
 * }
 * ```
 */
class PhantomJoinAliasedGenerator(
    private val subqueryInfo: com.obabichev.kodama.compiler.data.SubqueryInfo,
    private val fromTableCount: Int,  // 1 to 4 (since max is 5 tables)
    private val generatedPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val subqueryName = subqueryInfo.name
        val subqueryMarker = "${subqueryName}Marker"
        val toTableCount = fromTableCount + 1

        // Type parameters for source QueryBuilder
        val fromTypeParams = (1..fromTableCount).joinToString(", ") { "T$it : TableMarker" }
        val fromTypeArgs = (1..fromTableCount).joinToString(", ") { "T$it" }

        // Type parameters for target QueryBuilder (with subquery added)
        val toTypeArgs = fromTypeArgs + ", $subqueryMarker"

        appendLine("/**")
        appendLine(" * INNER JOIN $subqueryName subquery to $fromTableCount-table query.")
        appendLine(" */")
        appendLine("inline fun <$fromTypeParams, Sel : SelectionSet> QueryBuilder_$fromTableCount<$fromTypeArgs, Sel>.joinAliased(")
        appendLine("    subquery: $subqueryName,")
        appendLine("    crossinline condition: MultiTableJoinContext_$toTableCount<$toTypeArgs>.() -> Expression")
        appendLine("): QueryBuilder_$toTableCount<$toTypeArgs, Sel> {")
        appendLine("    val table = subquery as SubqueryTable")
        appendLine("    state._subqueryTables[table.alias] = table")
        appendLine("    val join = Join(")
        appendLine("        type = JoinType.INNER,")
        appendLine("        relation = state.relations.relation(table),")
        appendLine("        condition = {")
        appendLine("            val context = MultiTableJoinContext_$toTableCount<$toTypeArgs>(state, table)")
        appendLine("            context.condition()")
        appendLine("        }()")
        appendLine("    )")
        appendLine("    state._joins.add(join)")
        appendLine("    return QueryBuilder_$toTableCount(state)")
        appendLine("}")
        appendLine()
    }

    override fun requiredImports(): Set<String> {
        val toTableCount = fromTableCount + 1
        return setOf(
            "com.obabichev.kodama.components.Join",
            "com.obabichev.kodama.components.JoinType",
            "com.obabichev.kodama.query.SubqueryTable",
            "com.obabichev.kodama.components.expression.Expression",
            "$generatedPackage.MultiTableJoinContext_$toTableCount"
        )
    }
}

/**
 * Generates leftJoinAliased() method for adding a subquery with LEFT JOIN.
 */
class PhantomLeftJoinAliasedGenerator(
    private val subqueryInfo: com.obabichev.kodama.compiler.data.SubqueryInfo,
    private val fromTableCount: Int,
    private val generatedPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val subqueryName = subqueryInfo.name
        val subqueryMarker = "${subqueryName}Marker"
        val toTableCount = fromTableCount + 1

        // Type parameters for source QueryBuilder
        val fromTypeParams = (1..fromTableCount).joinToString(", ") { "T$it : TableMarker" }
        val fromTypeArgs = (1..fromTableCount).joinToString(", ") { "T$it" }

        // Type parameters for target QueryBuilder (with subquery added)
        val toTypeArgs = fromTypeArgs + ", $subqueryMarker"

        appendLine("/**")
        appendLine(" * LEFT JOIN $subqueryName subquery to $fromTableCount-table query.")
        appendLine(" */")
        appendLine("inline fun <$fromTypeParams, Sel : SelectionSet> QueryBuilder_$fromTableCount<$fromTypeArgs, Sel>.leftJoinAliased(")
        appendLine("    subquery: $subqueryName,")
        appendLine("    crossinline condition: MultiTableJoinContext_$toTableCount<$toTypeArgs>.() -> Expression")
        appendLine("): QueryBuilder_$toTableCount<$toTypeArgs, Sel> {")
        appendLine("    val table = subquery as SubqueryTable")
        appendLine("    state._subqueryTables[table.alias] = table")
        appendLine("    val join = Join(")
        appendLine("        type = JoinType.LEFT,")
        appendLine("        relation = state.relations.relation(table),")
        appendLine("        condition = {")
        appendLine("            val context = MultiTableJoinContext_$toTableCount<$toTypeArgs>(state, table)")
        appendLine("            context.condition()")
        appendLine("        }()")
        appendLine("    )")
        appendLine("    state._joins.add(join)")
        appendLine("    return QueryBuilder_$toTableCount(state)")
        appendLine("}")
        appendLine()
    }

    override fun requiredImports(): Set<String> {
        val toTableCount = fromTableCount + 1
        return setOf(
            "com.obabichev.kodama.components.Join",
            "com.obabichev.kodama.components.JoinType",
            "com.obabichev.kodama.query.SubqueryTable",
            "com.obabichev.kodama.components.expression.Expression",
            "$generatedPackage.MultiTableJoinContext_$toTableCount"
        )
    }
}

/**
 * Generates result accessor classes for subqueries in phantom types system.
 *
 * These classes provide type-safe access to subquery columns in result rows.
 * They extend TableResultAccessor to integrate with the phantom types result system.
 *
 * Example output:
 * ```kotlin
 * class ExpensiveOrdersResultAccessor_All_NonNull(
 *     resultSet: ResultSet,
 *     relations: RelationsContainer,
 *     selectedColumns: List<Column<*>>
 * ) : TableResultAccessor(resultSet, relations, selectedColumns) {
 *     val orderUserName: String
 *         get() = resultSet.getString("order_user_name") as String
 *     val orderProduct: String
 *         get() = resultSet.getString("order_product") as String
 * }
 * ```
 */
class PhantomSubqueryResultAccessorGenerator(
    private val subqueryInfo: com.obabichev.kodama.compiler.data.SubqueryInfo,
    private val generatedPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val className = "${subqueryInfo.name}ResultAccessor_All_NonNull"

        appendLine("/**")
        appendLine(" * Result accessor for ${subqueryInfo.name} subquery.")
        appendLine(" * Provides type-safe access to subquery columns.")
        appendLine(" */")
        appendLine("class $className(")
        appendLine("    resultSet: java.sql.ResultSet,")
        appendLine("    relations: com.obabichev.kodama.query.RelationsContainer,")
        appendLine("    selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
        appendLine(") : com.obabichev.kodama.query.TableResultAccessor(resultSet, relations, selectedColumns) {")

        // Generate property for each column
        subqueryInfo.columns.forEach { column ->
            val getterMethod = when (column.kotlinType) {
                "String" -> "getString"
                "Int" -> "getInt"
                "Long" -> "getLong"
                "Boolean" -> "getBoolean"
                "Double" -> "getDouble"
                "BigDecimal" -> "getBigDecimal"
                "Number" -> "getLong"  // For aggregates that return Number
                else -> "getObject"
            }

            appendLine()
            appendLine("    val ${column.propertyName}: ${column.kotlinType}")
            appendLine("        get() = resultSet.$getterMethod(\"${column.sqlColumnName}\") as ${column.kotlinType}")
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> = setOf(
        "com.obabichev.kodama.components.Column",
        "com.obabichev.kodama.query.RelationsContainer",
        "com.obabichev.kodama.query.TableResultAccessor",
        "java.sql.ResultSet"
    )
}
