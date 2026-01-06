package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the HasAliasedSelections marker interface.
 *
 * This marker indicates that a query has marker-based selections (using selectAs).
 * It's used as a type state marker to track whether aliased selections exist.
 *
 * Example output:
 * ```
 * interface HasAliasedSelections : SelectionState
 * ```
 *
 * Usage in generated code:
 * ```
 * fun selectAs<TotalRevenue>(...): Builder<HasAliasedSelections>
 * ```
 */
class HasAliasedSelectionsMarkerGenerator : CodeGenerator {

    override fun generate(): String {
        return "interface HasAliasedSelections : SelectionState"
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.SelectionState"
        )
    }
}
