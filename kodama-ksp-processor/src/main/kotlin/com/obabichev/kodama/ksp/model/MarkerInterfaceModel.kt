package com.obabichev.kodama.ksp.model

/**
 * Represents a marker interface discovered by KSP.
 * Marker interfaces are used for type-safe selection results.
 *
 * Examples: TotalRevenue, OrderCount, IsOld
 */
data class MarkerInterfaceModel(
    val name: String,              // "TotalRevenue"
    val packageName: String,       // "com.obabichev.kodama.tests"
    val qualifiedName: String,     // "com.obabichev.kodama.tests.TotalRevenue"
    val hasMarkerAnnotation: Boolean = false  // true if @Marker annotation is present
) {
    fun toJson(): String = """
        {
            "name": "$name",
            "package": "$packageName",
            "qualifiedName": "$qualifiedName",
            "hasMarkerAnnotation": $hasMarkerAnnotation
        }
    """.trimIndent()
}
