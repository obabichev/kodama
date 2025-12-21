package com.obabichev.kodama.compiler.generator

/**
 * Generates type aliases and marker interfaces.
 *
 * These provide shorter names for frequently used types and enable
 * compile-time type tracking for selection states.
 */
class TypeAliasGenerator : CodeGenerator<Unit> {

    override fun generate(model: Unit): String = buildString {
        generateAggregateCountAliases()
        appendLine()
        generateSelectionStateAliases()
        appendLine()
        generateColumnNameMarkers()
    }

    private fun StringBuilder.generateAggregateCountAliases() {
        appendLine("// Type aliases for aggregate count markers")
        appendLine("typealias AggCount = com.obabichev.kodama.query.AggCount")
        appendLine("typealias NoAggregates = com.obabichev.kodama.query.NoAggregates")
        for (i in 1..5) {
            val plural = if (i > 1) "s" else ""
            appendLine("typealias Has${i}Aggregate$plural = com.obabichev.kodama.query.Has${i}Aggregate$plural")
        }
    }

    private fun StringBuilder.generateSelectionStateAliases() {
        appendLine("// Selection state markers (typealiases to core definitions)")
        appendLine("typealias SelectionState = com.obabichev.kodama.query.SelectionState")
        appendLine("typealias NoSelections = com.obabichev.kodama.query.NoSelections")
        for (i in 1..10) {
            val plural = if (i > 1) "s" else ""
            appendLine("typealias Has${i}Selection$plural = com.obabichev.kodama.query.Has${i}Selection$plural")
        }
    }

    private fun StringBuilder.generateColumnNameMarkers() {
        // This would need to be populated from actual table models
        // For now, it's a placeholder showing the pattern
        appendLine("// Column name markers for type accumulation")
        appendLine("// (Generated based on actual columns discovered)")
    }
}

/**
 * Generates column marker interfaces from a set of column names.
 */
class ColumnMarkerGenerator : CodeGenerator<Set<String>> {

    override fun generate(model: Set<String>): String = buildString {
        appendLine("// Column name markers for type accumulation")
        model.sorted().forEach { columnName ->
            val capitalized = columnName.replaceFirstChar { it.uppercase() }
            appendLine("interface $capitalized")
        }
    }
}

/**
 * Generates table marker interfaces.
 */
class TableMarkerGenerator : CodeGenerator<List<String>> {

    override fun generate(model: List<String>): String = buildString {
        appendLine("// Table markers for type safety")
        model.forEach { tableName ->
            val capitalized = tableName.replaceFirstChar { it.uppercase() }
            appendLine("interface ${capitalized}Table")
        }
    }
}

/**
 * Generates the AllMarker interface.
 */
class AllMarkerGenerator : CodeGenerator<Unit> {

    override fun generate(model: Unit): String {
        return """
            |/**
            | * Marker interface for .all() selections.
            | */
            |interface AllMarker : SelectionMarker
        """.trimMargin()
    }
}
