package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the JoinPattern marker interface and concrete phantom types.
 *
 * These markers provide compile-time type-level tracking of join patterns,
 * enabling the compiler to distinguish between different join type combinations
 * for overload resolution.
 *
 * Example output:
 * ```
 * sealed interface JoinPattern
 *
 * interface JoinPattern_NONE : JoinPattern  // Single table, no joins
 * interface JoinPattern_INNER : JoinPattern
 * interface JoinPattern_LEFT : JoinPattern
 * interface JoinPattern_RIGHT : JoinPattern
 * interface JoinPattern_FULL : JoinPattern
 * interface JoinPattern_INNER_LEFT : JoinPattern  // Multi-join patterns
 * interface JoinPattern_INNER_RIGHT : JoinPattern
 * // ... etc for discovered patterns
 * ```
 *
 * Usage in generated code:
 * ```
 * class AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC, JP : JoinPattern>
 *
 * fun innerJoin(...): AfterFromQueryBuilder_Person_Order<..., ..., ..., JoinPattern_INNER>
 * fun leftJoin(...): AfterFromQueryBuilder_Person_Order<..., ..., ..., JoinPattern_LEFT>
 *
 * fun <...> AfterFromQueryBuilder<..., JoinPattern_INNER>.execute(): QueryResult_..._INNER
 * fun <...> AfterFromQueryBuilder<..., JoinPattern_LEFT>.execute(): QueryResult_..._LEFT
 * ```
 */
class JoinPatternMarkerGenerator(
    private val allJoinPatterns: Set<String>
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Generate sealed interface
        appendLine("/**")
        appendLine(" * Marker interface for join pattern phantom types.")
        appendLine(" * Enables compile-time tracking of join type combinations.")
        appendLine(" */")
        appendLine("sealed interface JoinPattern")
        appendLine()

        // Sort patterns for consistent output
        val sortedPatterns = allJoinPatterns.sorted()

        sortedPatterns.forEach { pattern ->
            val typeName = if (pattern.isEmpty()) {
                "JoinPattern_NONE"
            } else {
                "JoinPattern_$pattern"
            }

            appendLine("/**")
            appendLine(" * Phantom type for join pattern: ${if (pattern.isEmpty()) "NONE (single table)" else pattern}")
            appendLine(" */")
            appendLine("interface $typeName : JoinPattern")
            appendLine()
        }
    }

    override fun requiredImports(): Set<String> {
        return emptySet()  // No imports needed
    }
}
