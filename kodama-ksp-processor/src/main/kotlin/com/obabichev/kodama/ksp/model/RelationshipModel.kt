package com.obabichev.kodama.ksp.model

/**
 * Represents a relationship between two tables discovered by KSP.
 */
data class RelationshipModel(
    val sourceTable: String,           // "Person"
    val targetTable: String,           // "Order"
    val relationshipType: RelationshipType,
    val foreignKeyColumn: String,      // "userName"
    val primaryKeyColumn: String       // "name"
) {
    fun toJson(): String = """
        {
            "sourceTable": "$sourceTable",
            "targetTable": "$targetTable",
            "type": "${relationshipType.name}",
            "foreignKey": "$foreignKeyColumn",
            "primaryKey": "$primaryKeyColumn"
        }
    """.trimIndent()
}

enum class RelationshipType {
    ONE_TO_MANY,
    MANY_TO_ONE
}

/**
 * Represents a table with its relationships.
 */
data class TableWithRelationships(
    val table: KspTableModel,
    val relationships: List<RelationshipModel>
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"table\": ${table.toJson().replace("\n", "\n  ")},")
        appendLine("  \"relationships\": [")
        relationships.forEachIndexed { index, rel ->
            append("    ")
            append(rel.toJson().replace("\n", "\n    "))
            if (index < relationships.size - 1) {
                appendLine(",")
            } else {
                appendLine()
            }
        }
        appendLine("  ]")
        append("}")
    }
}
