package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a phantom type marker for tracking selections at compile time.
 *
 * SelectionSet markers encode which columns/aggregates have been selected
 * in the type system, enabling compile-time verification of result access.
 *
 * Example output:
 * ```
 * interface SelectionSet_totalRevenue : SelectionState
 * ```
 *
 * Usage in generated code:
 * ```
 * fun selectAs_totalRevenue(...): Builder<SelectionSet_totalRevenue>
 * ```
 *
 * This allows the compiler to verify that only selected values are accessed:
 * ```
 * val result: SelectionResult_totalRevenue = query.execute()
 * result.totalRevenue  // ✅ OK - was selected
 * result.orderCount    // ❌ Compile error - not selected
 * ```
 */
class SelectionSetMarkerGenerator(
    private val markerName: String
) : CodeGenerator {

    override fun generate(): String {
        return "interface $markerName : SelectionState"
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.SelectionState"
        )
    }
}
