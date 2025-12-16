package com.obabichev.kodama.insert

/**
 * Result of an INSERT operation.
 *
 * @property rowsAffected Number of rows inserted (typically 1 for single insert)
 * @property generatedKeys Map of column names to generated values (e.g., auto-increment IDs)
 *
 * Example usage:
 * ```kotlin
 * val result = Order.insert(transaction, id = 1, userName = "kodama", product = "Laptop", cost = 1000)
 * println("Inserted ${result.rowsAffected} row(s)")
 * result.generatedKeys["id"]?.let { println("Generated ID: $it") }
 * ```
 */
data class InsertResult(
    val rowsAffected: Int,
    val generatedKeys: Map<String, Any> = emptyMap()
) {
    /**
     * Check if insert was successful (at least 1 row affected).
     */
    val isSuccess: Boolean
        get() = rowsAffected > 0

    /**
     * Get a generated key by column name with type safety.
     */
    inline fun <reified T> getGeneratedKey(columnName: String): T? {
        return generatedKeys[columnName] as? T
    }
}
