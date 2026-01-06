package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.MarkerCombinationInfo
import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates execute() methods for hybrid queries that mix marker selections with table-wide selections.
 *
 * When a query uses both `selectAs(Marker)` and `selectAll(Table)`, this generator creates
 * an execute() method that:
 * 1. Matches builders with mixed selection state (e.g., <PersonSel, AllColumnsSelected, SelectionSet_PersonName>)
 * 2. Returns HybridResult_* classes with both marker properties and table accessors
 *
 * Example output for PersonName marker + Order table in Person+Order query:
 * ```
 * @JvmName("execute_Person_Order_PersonSel_AllColumnsSelected_SelectionSet_PersonName")
 * fun <PersonSel>
 * AfterFromQueryBuilder_Person_Order<PersonSel, AllColumnsSelected, SelectionSet_PersonName>.execute(
 *     transaction: JdbcTransaction
 * ): List<HybridResult_PersonName_Order> {
 *     val query = this.build()
 *     val resultSet = transaction.execute(query)
 *
 *     val results = mutableListOf<HybridResult_PersonName_Order>()
 *     while (resultSet.next()) {
 *         val personName = resultSet.getString("person_name")
 *         val orderAccessor = OrderResultAccessor_All(resultSet, state.relations, Order.allColumns())
 *         results.add(HybridResult_PersonName_Order(personName, orderAccessor))
 *     }
 *     return results
 * }
 * ```
 *
 * Type constraints:
 * - Tables with AllColumnsSelected → those tables have all columns selected
 * - Tables without AllColumnsSelected → represented by generic type parameter
 * - AC parameter → SelectionSet_* for the marker combination
 */
class ExecuteHybridMethodGenerator(
    private val queryCombination: QueryCombinationInfo,
    private val markerCombination: MarkerCombinationInfo,
    private val allColumnsTables: List<TableInfo>,
    private val schemaPackage: String
) : CodeGenerator {

    /**
     * Generate the result class name
     */
    private val resultClassName: String
        get() {
            val markerPart = markerCombination.markers.joinToString("_") { it.interfaceName }
            val tablePart = allColumnsTables.joinToString("_") { it.capitalizedName }
            return "HybridResult_${markerPart}_${tablePart}"
        }

    /**
     * Generate the phantom type for the marker combination
     */
    private val selectionSetType: String
        get() = "SelectionSet_" + markerCombination.markers.joinToString("_") { it.interfaceName }

    override fun generate(): String = buildString {
        // Build type parameters for the builder
        // Tables with AllColumnsSelected are concrete, others are generic
        val typeParams = mutableListOf<String>()
        val builderTypeArgs = mutableListOf<String>()

        queryCombination.tables.forEach { table ->
            if (allColumnsTables.contains(table)) {
                // This table has all columns selected - use AllColumnsSelected
                builderTypeArgs.add("AllColumnsSelected")
            } else {
                // This table doesn't have all columns selected - use generic type parameter
                val typeParam = "${table.capitalizedName}Sel"
                typeParams.add(typeParam)
                builderTypeArgs.add(typeParam)
            }
        }

        val typeParamsStr = if (typeParams.isNotEmpty()) "<${typeParams.joinToString(", ")}>" else ""
        val builderTypeArgsStr = builderTypeArgs.joinToString(", ")
        val phantomType = selectionSetType

        // Generate unique JVM name
        val jvmName = "execute_${queryCombination.builderClassName}_${builderTypeArgsStr.replace(", ", "_")}_${phantomType}"

        appendLine("/**")
        appendLine(" * Execute hybrid query with ${markerCombination.markers.size} marker(s) and ${allColumnsTables.size} table(s).")
        appendLine(" * Markers: ${markerCombination.markers.joinToString(", ") { it.interfaceName }}")
        appendLine(" * Tables: ${allColumnsTables.joinToString(", ") { it.capitalizedName }}")
        appendLine(" */")
        appendLine("@JvmName(\"$jvmName\")")
        appendLine("fun $typeParamsStr")
        appendLine("${queryCombination.builderClassName}<$builderTypeArgsStr, $phantomType>.execute(")
        appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
        appendLine("): com.obabichev.kodama.query.QueryResultIterable<$resultClassName> {")
        appendLine("    val query = this.build()")
        appendLine("    val resultSet = transaction.execute(query)")
        appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
        appendLine("        // Extract marker values from result set")

        // Extract marker values
        markerCombination.markers.forEach { marker ->
            appendLine("        val ${marker.propertyName} = rs.getObject(\"${marker.sqlAlias}\") as ${marker.resultType}")
        }

        // Create table accessors
        allColumnsTables.forEach { table ->
            appendLine("        val ${table.camelCaseName}Accessor = ${table.capitalizedName}ResultAccessor_All(")
            appendLine("            rs,")
            appendLine("            relations")

            // Different handling for subqueries vs regular tables
            // Regular tables need selectedColumns parameter, subqueries don't
            if (!table.isSubquery) {
                appendLine("            ,$schemaPackage.${table.capitalizedName}.allColumns()")
            }

            appendLine("        )")
        }

        // Create and return result object
        appendLine("        $resultClassName(")

        // Add QueryResult required fields first
        appendLine("            resultSet = rs,")
        appendLine("            relations = relations,")
        appendLine("            selectedColumns = query.select,")

        // Add marker arguments
        markerCombination.markers.forEach { marker ->
            appendLine("            ${marker.propertyName} = ${marker.propertyName},")
        }

        // Add table accessor arguments
        allColumnsTables.forEachIndexed { index, table ->
            val comma = if (index < allColumnsTables.size - 1) "," else ""
            appendLine("            ${table.camelCaseName} = ${table.camelCaseName}Accessor$comma")
        }

        appendLine("        )")
        appendLine("    }")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.execute.JdbcTransaction",
            "com.obabichev.kodama.query.AllColumnsSelected",
            "com.obabichev.kodama.query.QueryResultIterable"
        )
    }
}
