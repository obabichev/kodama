package com.obabichev.kodama.entity

/**
 * Identity map - ensures only one instance of an entity per ID within a session.
 *
 * This prevents:
 * - Duplicate instances of the same entity
 * - Inconsistent state (two copies with different values)
 * - Unnecessary database queries (cache hit on second load)
 *
 * **Memory Management:**
 * The identity map uses LRU (Least Recently Used) eviction to prevent unbounded memory growth.
 * When the map exceeds `maxSize`, the least recently accessed entities are automatically evicted.
 *
 * Default max size: 10,000 entities
 * Configure via EntitySession constructor: `EntitySession(connection, maxIdentityMapSize = 50_000)`
 *
 * Thread safety: NOT thread-safe. Each thread/transaction should have its own EntitySession.
 *
 * Example:
 * ```kotlin
 * val map = IdentityMap(maxSize = 1000)
 * val key = EntityKey(User::class, 1)
 * val user = User(1, "Alice")
 *
 * map.put(key, user)
 * val retrieved = map.get<User>(key)
 *
 * assert(user === retrieved)  // Same instance!
 * ```
 *
 * @param maxSize Maximum number of entities to cache (default: 10,000)
 * @param onEvict Callback invoked when an entity is evicted (for cleaning up metadata)
 */
class IdentityMap(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val onEvict: ((EntityKey) -> Unit)? = null
) {
    companion object {
        /**
         * Default maximum number of entities in the identity map.
         * Configurable per EntitySession instance.
         */
        const val DEFAULT_MAX_SIZE = 10_000
    }

    // LRU cache implementation using LinkedHashMap with access order
    private val entities = object : LinkedHashMap<EntityKey, Any>(
        /* initialCapacity */ 16,
        /* loadFactor */ 0.75f,
        /* accessOrder */ true  // LRU ordering: true = access order, false = insertion order
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<EntityKey, Any>): Boolean {
            val shouldRemove = size > maxSize
            if (shouldRemove) {
                // Notify callback to clean up metadata
                onEvict?.invoke(eldest.key)
            }
            return shouldRemove
        }
    }

    /**
     * Get an entity from the map.
     * Returns null if not found.
     *
     * @param key The entity key
     * @return The entity instance or null
     */
    @Suppress("UNCHECKED_CAST")
    fun <E : Any> get(key: EntityKey): E? {
        return entities[key] as? E
    }

    /**
     * Put an entity in the map.
     * If an entity with the same key already exists, it will be replaced.
     *
     * @param key The entity key
     * @param entity The entity instance
     */
    fun <E : Any> put(key: EntityKey, entity: E) {
        entities[key] = entity
    }

    /**
     * Check if an entity exists in the map.
     *
     * @param key The entity key
     * @return true if entity exists, false otherwise
     */
    fun contains(key: EntityKey): Boolean {
        return entities.containsKey(key)
    }

    /**
     * Remove an entity from the map.
     *
     * @param key The entity key
     */
    fun remove(key: EntityKey) {
        entities.remove(key)
    }

    /**
     * Clear all entities from the map.
     * Called when session is closed.
     */
    fun clear() {
        entities.clear()
    }

    /**
     * Number of entities currently in the map.
     */
    val size: Int
        get() = entities.size

    /**
     * All entity keys currently in the map.
     * Useful for debugging and bulk operations.
     */
    val keys: Set<EntityKey>
        get() = entities.keys.toSet()

    /**
     * All entities currently in the map.
     * Useful for bulk operations (flush, etc.).
     */
    val values: Collection<Any>
        get() = entities.values
}
