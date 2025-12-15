package com.obabichev.kodama.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import java.io.OutputStream

/**
 * Generates type-safe result classes and query extension functions.
 *
 * This is where the magic happens:
 * - Each unique query pattern gets its own result class
 * - Result class only contains accessors for actually selected columns/aggregates
 * - IDE autocomplete works immediately without regeneration
 */
class KodamaCodeGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) {

    /**
     * Generates a unique result class for a query pattern.
     *
     * Example output for query with sum(order.cost) alias "totalRevenue" and count(order.id) alias "orderCount":
     *
     * ```kotlin
     * class QueryResult_Line45_TestFile(
     *     override val resultSet: java.sql.ResultSet,
     *     override val relations: com.obabichev.kodama.query.RelationsContainer,
     *     override val selectedColumns: List<com.obabichev.kodama.components.Column<*>>,
     *     val aggregates: List<com.obabichev.kodama.query.AggregateFunction<*>>
     * ) : com.obabichev.kodama.query.QueryResult {
     *
     *     val totalRevenue: java.math.BigDecimal
     *         get() {
     *             val agg = aggregates.find { it.accessorName == "totalRevenue" }
     *             requireNotNull(agg) { "Aggregate 'totalRevenue' not found" }
     *             val index = selectedColumns.size + aggregates.indexOf(agg) + 1
     *             return resultSet.getBigDecimal(index)
     *         }
     *
     *     val orderCount: Long
     *         get() {
     *             val agg = aggregates.find { it.accessorName == "orderCount" }
     *             requireNotNull(agg) { "Aggregate 'orderCount' not found" }
     *             val index = selectedColumns.size + aggregates.indexOf(agg) + 1
     *             return resultSet.getLong(index)
     *         }
     * }
     * ```
     */
    fun generateResultClass(query: QueryPattern, queryIndex: Int) {
        val fileName = "QueryResult_${queryIndex}"
        val packageName = "com.obabichev.kodama.generated"

        logger.info("Generating result class: $packageName.$fileName")

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = packageName,
            fileName = fileName
        )

        file.bufferedWriter().use { writer ->
            writer.write(buildResultClassCode(query, fileName, packageName))
        }
    }

    /**
     * Generates extension functions for query builders.
     *
     * Example: For the query pattern above, generates:
     * ```kotlin
     * fun AfterFromQueryBuilder_Order<NoColumnsSelected>.executeAggregateQuery_45(
     *     transaction: java.sql.Connection
     * ): com.obabichev.kodama.query.QueryResultIterable<QueryResult_Line45_TestFile> {
     *     val query = this.build()
     *     return com.obabichev.kodama.query.QueryResultIterable(
     *         query.execute(transaction)
     *     ) { resultSet ->
     *         QueryResult_Line45_TestFile(
     *             resultSet,
     *             query.relations,
     *             query.select,
     *             query.aggregates
     *         )
     *     }
     * }
     * ```
     */
    fun generateQueryExtensions(queries: List<QueryPattern>) {
        val fileName = "QueryExtensions"
        val packageName = "com.obabichev.kodama.generated"

        logger.info("Generating query extensions: $packageName.$fileName")

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = packageName,
            fileName = fileName
        )

        file.bufferedWriter().use { writer ->
            writer.write(buildQueryExtensionsCode(queries, packageName))
        }
    }

    /**
     * Builds the actual Kotlin code for a result class.
     */
    private fun buildResultClassCode(query: QueryPattern, className: String, packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("/**")
            appendLine(" * Type-safe result class for query at ${query.sourceFile}:${query.lineNumber}")
            appendLine(" * Tables: ${query.tables.joinToString()}")
            appendLine(" * Aggregates: ${query.aggregates.joinToString { it.alias }}")
            appendLine(" */")
            appendLine("class $className(")
            appendLine("    override val resultSet: java.sql.ResultSet,")
            appendLine("    override val relations: com.obabichev.kodama.query.RelationsContainer,")
            appendLine("    override val selectedColumns: List<com.obabichev.kodama.components.Column<*>>,")
            appendLine("    val aggregates: List<com.obabichev.kodama.query.AggregateFunction<*>>")
            appendLine(") : com.obabichev.kodama.query.QueryResult {")
            appendLine()

            // Generate accessor for each regular column
            query.regularColumns.forEach { col ->
                val propertyName = "${col.table}_${col.column}"
                appendLine("    val $propertyName: Any?")
                appendLine("        get() {")
                appendLine("            // TODO: implement column accessor")
                appendLine("            return null")
                appendLine("        }")
                appendLine()
            }

            // Generate accessor for each aggregate
            query.aggregates.forEach { agg ->
                val propertyName = agg.alias
                val returnType = getAggregateReturnType(agg.function)

                appendLine("    val $propertyName: $returnType")
                appendLine("        get() {")
                appendLine("            val agg = aggregates.find { it.accessorName == \"$propertyName\" }")
                appendLine("            requireNotNull(agg) { \"Aggregate '$propertyName' not found in query\" }")
                appendLine("            val index = selectedColumns.size + aggregates.indexOf(agg) + 1")
                appendLine("            return resultSet.${getResultSetMethod(agg.function)}(index)")
                appendLine("        }")
                appendLine()
            }

            appendLine("}")
        }
    }

    /**
     * Builds code for query extension functions.
     */
    private fun buildQueryExtensionsCode(queries: List<QueryPattern>, packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("// Extension functions for executing queries with type-safe results")
            appendLine()

            // For now, just add a placeholder
            // Full implementation would generate execute() extensions for each pattern
            appendLine("// TODO: Generate execute() extensions for each query pattern")
        }
    }

    /**
     * Maps aggregate function to Kotlin return type.
     */
    private fun getAggregateReturnType(function: String): String {
        return when (function.lowercase()) {
            "sum", "avg" -> "java.math.BigDecimal"
            "count" -> "Long"
            "min", "max" -> "Any?"
            else -> "Any?"
        }
    }

    /**
     * Maps aggregate function to ResultSet getter method.
     */
    private fun getResultSetMethod(function: String): String {
        return when (function.lowercase()) {
            "sum", "avg" -> "getBigDecimal"
            "count" -> "getLong"
            "min", "max" -> "getObject"
            else -> "getObject"
        }
    }
}
