package com.obabichev.kodama.ksp.model

/**
 * Represents table metadata discovered by KSP.
 * Contains basic information that KSP can extract.
 */
data class KspTableModel(
    val name: String,              // "Person"
    val packageName: String,       // "com.obabichev.kodama.tests.schema"
    val qualifiedName: String      // "com.obabichev.kodama.tests.schema.Person"
) {
    fun toJson(): String = """
        {
            "name": "$name",
            "package": "$packageName",
            "qualifiedName": "$qualifiedName"
        }
    """.trimIndent()
}
