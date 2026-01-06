package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.data.ColumnInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a simple marker interface for a column name.
 *
 * Column markers are used in type accumulation to track which columns
 * have been selected in a query.
 *
 * Example output:
 * ```
 * interface Name
 * ```
 *
 * Usage in generated code:
 * ```
 * val name: TypedColumn<String, PersonTable, Name>
 * ```
 */
class ColumnMarkerGenerator(
    private val columnInfo: ColumnInfo
) : CodeGenerator {

    override fun generate(): String {
        return "interface ${columnInfo.capitalizedName}"
    }

    override fun requiredImports(): Set<String> {
        // Marker interfaces don't need any imports
        return emptySet()
    }

    override fun dependencies(): List<CodeGenerator> {
        // No dependencies
        return emptyList()
    }
}
