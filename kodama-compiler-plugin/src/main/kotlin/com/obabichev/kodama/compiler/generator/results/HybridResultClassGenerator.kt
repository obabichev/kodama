package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.MarkerCombinationInfo
import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates hybrid result classes that combine marker-based selections with table-wide selections.
 *
 * When a query mixes `selectAs(Marker)` with `selectAll(Table)`, we need a result class
 * that has:
 * - Named properties for each marker (e.g., `personName: String`)
 * - Table accessor properties for each selectAll table (e.g., `order: OrderResultAccessor_All`)
 *
 * Example output for PersonName marker + Order table:
 * ```
 * data class HybridResult_PersonName_Order(
 *     val personName: String,  // From selectAs(PersonName)
 *     val order: OrderResultAccessor_All  // From selectAll(Order)
 * )
 * ```
 *
 * This allows code like:
 * ```
 * val result = from(Person)
 *     .join(Order) { ... }
 *     .selectAs(PersonName) { person.name }
 *     .selectAll(Order)
 *     .execute(transaction)
 *     .first()
 *
 * val name: String = result.personName  // Marker property
 * val cost: Int = result.order.cost      // Table accessor
 * ```
 */
class HybridResultClassGenerator(
    private val markerCombination: MarkerCombinationInfo,
    private val allColumnsTables: List<TableInfo>
) : CodeGenerator {

    /**
     * Generate the class name: HybridResult_{Markers}_{Tables}
     */
    private val className: String
        get() {
            val markerPart = markerCombination.markers.joinToString("_") { it.interfaceName }
            val tablePart = allColumnsTables.joinToString("_") { it.capitalizedName }
            return "HybridResult_${markerPart}_${tablePart}"
        }

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Hybrid result combining marker selections with table-wide selections.")
        appendLine(" * Markers: ${markerCombination.markers.joinToString(", ") { it.interfaceName }}")
        appendLine(" * Tables: ${allColumnsTables.joinToString(", ") { it.capitalizedName }}")
        appendLine(" */")
        appendLine("data class $className(")

        // Add QueryResult required fields first
        appendLine("    override val resultSet: java.sql.ResultSet,")
        appendLine("    override val relations: com.obabichev.kodama.query.RelationsContainer,")
        appendLine("    override val selectedColumns: List<com.obabichev.kodama.components.Column<*>>,")

        // Add marker properties
        markerCombination.markers.forEach { marker ->
            appendLine("    val ${marker.propertyName}: ${marker.resultType},")
        }

        // Add table accessor properties
        allColumnsTables.forEachIndexed { index, table ->
            val comma = if (index < allColumnsTables.size - 1) "," else ""
            // For hybrid results, table is always base table (non-nullable)
            // Use NonNull variant unless it's a subquery (subqueries don't have variants yet)
            val accessorVariant = if (table.isSubquery) {
                "${table.capitalizedName}ResultAccessor_All"
            } else {
                "${table.capitalizedName}ResultAccessor_All_NonNull"
            }
            appendLine("    val ${table.camelCaseName}: $accessorVariant$comma")
        }

        appendLine(") : com.obabichev.kodama.query.QueryResult")
    }

    override fun requiredImports(): Set<String> {
        return emptySet()
    }
}
