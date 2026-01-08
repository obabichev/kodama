package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.JoinType
import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the QueryResult data class for query execution results.
 *
 * The QueryResult class encapsulates the results of a query, providing
 * type-safe access to each table's data through result accessors.
 *
 * Example output for Person+Order combination:
 * ```
 * data class QueryResult_Person_Order(
 *     val person: PersonResultAccessor_All,
 *     val order: OrderResultAccessor_All
 * )
 * ```
 *
 * Usage:
 * ```
 * val results: List<QueryResult_Person_Order> = from(Person)
 *     .join(Order) { order.userName eq person.name }
 *     .selectAll(Person)
 *     .selectAll(Order)
 *     .execute(transaction)
 *
 * results.forEach { row ->
 *     val name = row.person.name      // Type-safe access
 *     val product = row.order.product
 *     println("$name ordered $product")
 * }
 * ```
 *
 * Key features:
 * - One property per table in the query
 * - Each property is a result accessor (provides typed column access)
 * - Result accessor type varies based on selection state:
 *   - AllColumnsSelected → ResultAccessor_All
 *   - Specific columns → ResultAccessor_Name_Age
 *   - Aggregates → SelectionResult_*
 *
 * This generator creates the base result class. The actual accessor type
 * used depends on the selection state at compile time.
 */
class QueryResultClassGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Include join pattern in class name for pattern-specific result classes
        val className = if (combination.joinPattern.isEmpty()) {
            // Single table query
            "QueryResult_" + combination.tables.joinToString("_") { it.capitalizedName }
        } else {
            // Multi-table query with joins - include pattern for uniqueness
            "QueryResult_" + combination.tables.joinToString("_") { it.capitalizedName } + "_" + combination.joinPattern
        }

        appendLine("/**")
        appendLine(" * Result class for ${combination.tables.joinToString(" + ") { it.capitalizedName }} query.")
        if (combination.joinPattern.isNotEmpty()) {
            appendLine(" * Join pattern: ${combination.joinPattern}")
        }
        appendLine(" */")
        appendLine("data class $className(")
        appendLine("    override val resultSet: ResultSet,")
        appendLine("    override val relations: RelationsContainer,")
        appendLine("    override val selectedColumns: List<Column<*>>")
        appendLine(") : com.obabichev.kodama.query.QueryResult {")

        // JOIN-TYPE-AWARE NULLABILITY:
        // We now generate separate result classes per join pattern, so we can use
        // precise nullability based on the specific join types used.
        //
        // Nullability rules:
        // - INNER JOIN: Both sides non-nullable (both must exist)
        // - LEFT JOIN: Left side non-nullable, right side nullable
        // - RIGHT JOIN: Left side nullable, right side non-nullable
        // - FULL OUTER JOIN: Both sides nullable
        //
        // Base table nullability: Non-nullable UNLESS there's a RIGHT or FULL join
        // (those can make the left side NULL)

        // Determine if base table can be null (if any RIGHT or FULL join exists)
        val baseTableCanBeNull = combination.joinedTables.any {
            it.joinType == JoinType.RIGHT || it.joinType == JoinType.FULL
        }

        // Base table accessor
        val baseTable = combination.baseTable
        val baseAccessorVariant = if (baseTable.isSubquery) {
            "${baseTable.capitalizedName}ResultAccessor_All"  // Subqueries don't have variants yet
        } else {
            if (baseTableCanBeNull) {
                "${baseTable.capitalizedName}ResultAccessor_All_Nullable"  // RIGHT/FULL joins make base nullable
            } else {
                "${baseTable.capitalizedName}ResultAccessor_All_NonNull"  // INNER/LEFT joins keep base non-null
            }
        }

        appendLine("    val ${baseTable.camelCaseName}: $baseAccessorVariant")
        if (baseTable.isSubquery) {
            appendLine("        get() = $baseAccessorVariant(resultSet, relations)")
        } else {
            appendLine("        get() = $baseAccessorVariant(resultSet, relations, selectedColumns)")
        }

        // Joined tables - nullability depends on specific join type
        combination.joinedTables.forEach { joinedTable ->
            val table = joinedTable.table
            val joinType = joinedTable.joinType

            val accessorVariant = if (table.isSubquery) {
                "${table.capitalizedName}ResultAccessor_All"  // Subqueries don't have variants yet
            } else {
                // Determine nullability based on join type
                when (joinType) {
                    JoinType.INNER -> "${table.capitalizedName}ResultAccessor_All_NonNull"  // INNER: non-nullable
                    JoinType.LEFT -> "${table.capitalizedName}ResultAccessor_All_Nullable"   // LEFT: right side nullable
                    JoinType.RIGHT -> "${table.capitalizedName}ResultAccessor_All_NonNull"   // RIGHT: right side non-nullable
                    JoinType.FULL -> "${table.capitalizedName}ResultAccessor_All_Nullable"   // FULL: both sides nullable
                }
            }

            appendLine("    val ${table.camelCaseName}: $accessorVariant")
            if (table.isSubquery) {
                appendLine("        get() = $accessorVariant(resultSet, relations)")
            } else {
                appendLine("        get() = $accessorVariant(resultSet, relations, selectedColumns)")
            }
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "java.sql.ResultSet",
            "com.obabichev.kodama.query.RelationsContainer",
            "com.obabichev.kodama.components.Column",
            "com.obabichev.kodama.query.QueryResult"
        )
    }
}
