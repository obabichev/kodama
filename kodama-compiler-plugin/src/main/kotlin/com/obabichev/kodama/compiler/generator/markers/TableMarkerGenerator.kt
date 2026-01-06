package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a marker interface for a table.
 *
 * Table markers are used in type accumulation to disambiguate columns
 * from different tables that have the same name.
 *
 * Example output for Person table:
 * ```
 * interface PersonTable
 * ```
 *
 * Usage in generated code:
 * ```
 * val name: TypedColumn<String, PersonTable, Name>
 * //                                ^ table marker distinguishes Person.name from Order.name
 * ```
 */
class TableMarkerGenerator(
    private val tableInfo: TableInfo
) : CodeGenerator {

    override fun generate(): String {
        return "interface ${tableInfo.capitalizedName}Table"
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
