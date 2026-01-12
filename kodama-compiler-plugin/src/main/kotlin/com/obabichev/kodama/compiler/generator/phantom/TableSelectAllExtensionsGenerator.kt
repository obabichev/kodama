package com.obabichev.kodama.compiler.generator.phantom

import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates table-specific selectAll() extensions with phantom type constraints.
 *
 * This provides compile-time safety by ensuring you can only call selectAll(Table)
 * if that table's marker is actually in the query's type parameters.
 *
 * Example generated code for Author table:
 * ```kotlin
 * // For QueryBuilder_1 - only works if T1 is AuthorMarker
 * inline fun <Sel : SelectionSet> QueryBuilder_1<AuthorMarker, Sel>.selectAll(
 *     table: Author
 * ): QueryBuilder_1<AuthorMarker, Sel> {
 *     state.applySelection(TableAllSelection(table, table.allColumns()))
 *     return this
 * }
 *
 * // For QueryBuilder_2 - only works if T1 is AuthorMarker
 * inline fun <T2 : TableMarker, Sel : SelectionSet> QueryBuilder_2<AuthorMarker, T2, Sel>.selectAll(
 *     table: Author
 * ): QueryBuilder_2<AuthorMarker, T2, Sel> {
 *     state.applySelection(TableAllSelection(table, table.allColumns()))
 *     return this
 * }
 *
 * // For QueryBuilder_2 - only works if T2 is AuthorMarker
 * inline fun <T1 : TableMarker, Sel : SelectionSet> QueryBuilder_2<T1, AuthorMarker, Sel>.selectAll(
 *     table: Author
 * ): QueryBuilder_2<T1, AuthorMarker, Sel> {
 *     state.applySelection(TableAllSelection(table, table.allColumns()))
 *     return this
 * }
 * ```
 *
 * This ensures:
 * - from(Author).selectAll(Author) ✅ compiles (AuthorMarker in type)
 * - from(Author).selectAll(Order) ❌ doesn't compile (OrderMarker not in type)
 * - from(Author).join(Book).selectAll(Author) ✅ compiles
 * - from(Author).join(Book).selectAll(Order) ❌ doesn't compile
 */
class TableSelectAllExtensionsGenerator(
    private val tableName: String,
    private val tableCount: Int,  // Which QueryBuilder_N to generate for (1-5)
    private val generatedPackage: String,
    private val schemaPackage: String
) : CodeGenerator {

    override fun requiredImports(): Set<String> = emptySet()

    override fun generate(): String = buildString {
        val markerType = "${tableName}Marker"

        // Generate extensions for each position where this table's marker could appear
        for (position in 1..tableCount) {
            appendLine("/**")
            appendLine(" * Select all columns from $tableName table.")
            appendLine(" * Only available when ${tableName}Marker is at position $position in the query.")
            appendLine(" * Flips selection status at position $position from TableNotSelected to TableSelected.")
            appendLine(" */")

            // Add @JvmName to avoid signature clashes after type erasure
            appendLine("@JvmName(\"selectAll${tableName}AtPosition${position}Of$tableCount\")")

            // Build type parameters: Other table types + all selection statuses + Sel
            val typeParams = buildList {
                // Add other table type parameters (exclude the fixed position)
                for (i in 1..tableCount) {
                    if (i != position) {
                        add("T$i : $generatedPackage.TableMarker")
                    }
                }
                // Add all selection status parameters (all are generic in receiver)
                for (i in 1..tableCount) {
                    add("S$i : $generatedPackage.SelectionStatus")
                }
                add("Sel : $generatedPackage.SelectionSet")
            }.joinToString(", ")

            // Build receiver type arguments: Fixed table at position, others generic, all S generic
            val receiverTypeArgs = buildList {
                // Table markers
                for (i in 1..tableCount) {
                    if (i == position) {
                        add(markerType)
                    } else {
                        add("T$i")
                    }
                }
                // Selection statuses (all generic in receiver)
                for (i in 1..tableCount) {
                    add("S$i")
                }
                add("Sel")
            }.joinToString(", ")

            // Build return type arguments: Same tables, but flip S{position} to Selected
            val returnTypeArgs = buildList {
                // Table markers (unchanged)
                for (i in 1..tableCount) {
                    if (i == position) {
                        add(markerType)
                    } else {
                        add("T$i")
                    }
                }
                // Selection statuses: Flip position to TableSelected, keep others generic
                for (i in 1..tableCount) {
                    if (i == position) {
                        add("$generatedPackage.TableSelected")  // Flip to TableSelected!
                    } else {
                        add("S$i")  // Keep generic
                    }
                }
                add("Sel")
            }.joinToString(", ")

            appendLine("inline fun <$typeParams> $generatedPackage.QueryBuilder_$tableCount<$receiverTypeArgs>.selectAll(")
            appendLine("    table: $schemaPackage.$tableName")
            appendLine("): $generatedPackage.QueryBuilder_$tableCount<$returnTypeArgs> {")
            appendLine("    state.applySelection(com.obabichev.kodama.query.TableAllSelection(table, table.allColumns()))")
            appendLine("    return $generatedPackage.QueryBuilder_$tableCount(state)")
            appendLine("}")
            appendLine()
        }
    }
}
