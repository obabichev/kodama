package com.obabichev.kodama.compiler.generator.subqueries

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the SubqueryRegistry object for mapping marker interfaces to subquery tables.
 *
 * The SubqueryRegistry is a centralized registry that associates marker interfaces
 * (like ExpensiveOrders) with their SubqueryTable implementations. This enables
 * fromAliased() and joinAliased() methods to look up subquery definitions.
 *
 * Example output for ExpensiveOrders + HighValueCustomers subqueries:
 * ```
 * object SubqueryRegistry {
 *     private val registry = mutableMapOf<KClass<*>, () -> Table>()
 *
 *     init {
 *         // Register all subquery factories
 *         register<ExpensiveOrders> {
 *             SubqueryTable_ExpensiveOrders(
 *                 query = from(Order)
 *                     .selectAll(Order)
 *                     .where { order.cost gt 1000 }
 *                     .build()
 *             )
 *         }
 *
 *         register<HighValueCustomers> {
 *             SubqueryTable_HighValueCustomers(
 *                 query = from(Person)
 *                     .selectAll(Person)
 *                     .where { person.creditScore gt 750 }
 *                     .build()
 *             )
 *         }
 *     }
 *
 *     inline fun <reified T : Any> register(noinline factory: () -> Table) {
 *         registry[T::class] = factory
 *     }
 *
 *     inline fun <reified T : Any> getOrCreate(): Table {
 *         val factory = registry[T::class]
 *             ?: error("Subquery ${T::class.simpleName} not registered")
 *         return factory()
 *     }
 * }
 * ```
 *
 * Usage:
 * ```
 * // Automatic usage via fromAliased/joinAliased
 * fromAliased(ExpensiveOrders)  // Looks up in registry
 *     .selectAll { expensiveOrders }
 *
 * // Manual registration (optional, for custom subqueries)
 * SubqueryRegistry.register<MyCustomSubquery> {
 *     SubqueryTable_MyCustomSubquery(...)
 * }
 * ```
 *
 * Design decisions:
 * - **Lazy creation**: Subquery tables are created on-demand via factories
 * - **Type-safe**: Uses reified type parameters for compile-time checking
 * - **Extensible**: Users can register custom subqueries at runtime
 * - **Singleton**: Object ensures single registry instance
 *
 * The init block is populated by the generator with all discovered subqueries
 * from the codebase scan.
 */
class SubqueryRegistryGenerator(
    private val subqueries: List<SubqueryInfo>
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Registry for subquery marker interfaces to SubqueryTable implementations.")
        appendLine(" */")
        appendLine("object SubqueryRegistry {")
        appendLine("    @PublishedApi")
        appendLine("    internal val registry = mutableMapOf<KClass<*>, () -> Table>()")
        appendLine()

        if (subqueries.isNotEmpty()) {
            appendLine("    init {")
            subqueries.forEach { subquery ->
                appendLine("        // Register ${subquery.name} subquery")
                appendLine("        register<${subquery.name}> {")

                // If sourceTables is empty, generate error - this subquery must be used inline
                if (subquery.sourceTables.isEmpty()) {
                    appendLine("            error(\"Subquery ${subquery.name} must be used with inline definition: fromAliased(${subquery.name}) { /* query */ }\")")
                } else {
                    // Generate default query from source tables
                    val sourceTable = subquery.sourceTables.first()
                    appendLine("            SubqueryTable_${subquery.name}(")
                    appendLine("                query = from($sourceTable)")
                    appendLine("                    .selectAll($sourceTable)")
                    appendLine("                    .build()")
                    appendLine("            )")
                }

                appendLine("        }")
                appendLine()
            }
            appendLine("    }")
            appendLine()
        }

        appendLine("    inline fun <reified T : Any> register(noinline factory: () -> Table) {")
        appendLine("        registry[T::class] = factory")
        appendLine("    }")
        appendLine()
        appendLine("    inline fun <reified T : Any> getOrCreate(): Table {")
        appendLine("        // Try direct lookup first (for explicit marker instances)")
        appendLine("        var factory = registry[T::class]")
        appendLine("        ")
        appendLine("        // If not found, try looking up by superinterfaces (for companion objects)")
        appendLine("        if (factory == null) {")
        appendLine("            val markerInterface = T::class.java.interfaces.firstOrNull()")
        appendLine("            if (markerInterface != null) {")
        appendLine("                val kotlinClass = markerInterface.kotlin")
        appendLine("                factory = registry[kotlinClass]")
        appendLine("            }")
        appendLine("        }")
        appendLine("        ")
        appendLine("        return factory?.invoke()")
        appendLine("            ?: error(\"Subquery \${T::class.simpleName} not registered\")")
        appendLine("    }")
        appendLine()
        appendLine("    fun createSubquery(markerClass: kotlin.reflect.KClass<*>, query: Query): Any {")
        if (subqueries.isNotEmpty()) {
            appendLine("        // Check if markerClass is or implements one of the expected interfaces")
            appendLine("        // (handles companion objects that implement the marker interface)")
            appendLine("        return when {")
            subqueries.forEach { subquery ->
                appendLine("            ${subquery.name}::class.java.isAssignableFrom(markerClass.java) -> SubqueryTable_${subquery.name}(query)")
            }
            appendLine("            else -> throw IllegalArgumentException(\"Unknown subquery marker: \${markerClass.simpleName}\")")
            appendLine("        }")
        } else {
            appendLine("        throw IllegalArgumentException(\"No subqueries registered. Subquery marker: \${markerClass.simpleName}\")")
        }
        appendLine("    }")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "kotlin.reflect.KClass",
            "com.obabichev.kodama.schema.Table",
            "com.obabichev.kodama.query.Query"
        )
    }
}
