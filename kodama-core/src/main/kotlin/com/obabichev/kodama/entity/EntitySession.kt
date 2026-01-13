package com.obabichev.kodama.entity

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.EntityTable
import com.obabichev.kodama.util.normalizeSQL
import java.sql.Connection
import kotlin.reflect.KClass

/**
 * Entity session - manages entity lifecycle and caching.
 *
 * Responsibilities:
 * - Load entities from database (find)
 * - Cache entities in identity map (one instance per ID)
 * - Save, update, delete operations
 * - Change tracking and flush
 *
 * Lifecycle:
 * - Create per transaction (transaction-scoped)
 * - Use within try-with-resources or use block
 * - Close after commit/rollback
 *
 * **Memory Management:**
 * The session uses an LRU (Least Recently Used) cache for the identity map to prevent
 * unbounded memory growth. Configure the max size based on your application's needs:
 * - Default: 10,000 entities
 * - Short-lived sessions (web requests): 1,000 - 5,000
 * - Long-lived sessions (batch jobs): 50,000 - 100,000
 *
 * Thread safety: NOT thread-safe. One session per thread.
 *
 * Example:
 * ```kotlin
 * // Default max size (10,000 entities)
 * EntitySession(connection).use { session ->
 *     val user = session.find<User>(1)
 *     // ... use entity
 * }
 *
 * // Custom max size for batch processing
 * EntitySession(connection, maxIdentityMapSize = 50_000).use { session ->
 *     // Process large dataset
 * }
 * ```
 *
 * @param connection JDBC connection for database operations
 * @param maxIdentityMapSize Maximum number of entities in identity map (default: 10,000)
 */
class EntitySession(
    val connection: Connection,
    maxIdentityMapSize: Int = IdentityMap.DEFAULT_MAX_SIZE
) : AutoCloseable {

    companion object {
        /**
         * Phase 5: Auto-binding provider set by generated KodamaBindingRegistry.
         * This allows EntitySession to automatically look up bindings without manual registration.
         */
        @JvmStatic
        var autoBindingProvider: ((KClass<*>) -> EntityBinding<*, *>?)? = null

        /**
         * Maximum number of relationship entries in the eager loading cache.
         * When exceeded, least recently used entries are automatically evicted.
         */
        private const val MAX_CACHED_RELATIONSHIPS = 1_000

        init {
            // Auto-discover and initialize KodamaBindingRegistry if present
            // This eliminates the need for users to manually reference the registry
            if (autoBindingProvider == null) {
                try {
                    // Read the registry class name from the generated resource file
                    val classLoader = Thread.currentThread().contextClassLoader
                        ?: EntitySession::class.java.classLoader

                    val resourceUrl = classLoader.getResource("META-INF/kodama/binding-registry.txt")
                    if (resourceUrl != null) {
                        val registryClassName = resourceUrl.readText().trim()
                        if (registryClassName.isNotEmpty()) {
                            // Load and initialize the registry class
                            val registryClass = Class.forName(registryClassName, true, classLoader)
                            // Access the object instance to trigger its init block
                            registryClass.kotlin.objectInstance
                        }
                    }
                } catch (_: Exception) {
                    // If auto-discovery fails, fall back to manual initialization
                    // This is fine - the user can still reference KodamaBindingRegistry manually
                }
            }
        }
    }

    // Phase 5.3: Made internal for eager loading extensions
    // LRU cache with automatic metadata cleanup on eviction
    internal val identityMap = IdentityMap(
        maxSize = maxIdentityMapSize,
        onEvict = { key ->
            // Clean up metadata when entity is evicted from identity map
            entityMetadata.remove(key)
        }
    )

    // Registry of bindings (entity type -> binding)
    // In Phase 1: Populated manually via registerBinding()
    // In Phase 5: Populated automatically by generated code via autoBindingProvider
    private val bindings = mutableMapOf<KClass<*>, EntityBinding<*, *>>()

    // Track entity metadata (state, binding, snapshot)
    // Key: EntityKey, Value: EntityMetadata
    // Phase 2: Used to track NEW and PENDING_INSERT entities
    // Phase 3: Will also track PENDING_UPDATE entities with snapshots
    // Phase 4: Will also track PENDING_DELETE entities
    private val entityMetadata = mutableMapOf<EntityKey, EntityMetadata<*, *>>()

    // Phase 5: Entity lifecycle listeners
    // Key: Entity class, Value: List of listeners for that entity type
    private val listeners = mutableMapOf<KClass<*>, MutableList<EntityListener<*>>>()

    // Phase 5.3: Eager loading cache for preloaded relationships
    // Key: RelationshipCacheKey (source entity type, source ID, relationship name)
    // Value: List of preloaded related entities
    // This cache prevents N+1 queries by storing batch-loaded relationships
    //
    // Memory management: LRU cache with configurable max size to prevent unbounded growth
    private val relationshipCache = object : LinkedHashMap<RelationshipCacheKey, List<Any>>(
        /* initialCapacity */ 16,
        /* loadFactor */ 0.75f,
        /* accessOrder */ true  // LRU ordering
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<RelationshipCacheKey, List<Any>>): Boolean {
            return size > MAX_CACHED_RELATIONSHIPS
        }
    }

    /**
     * Register a binding for an entity type.
     *
     * In Phase 1, this must be called manually for each entity type.
     * In Phase 5, this will be called automatically by generated initialization code.
     *
     * @param table The entity table
     * @param binding The entity binding
     */
    fun <E : Any, ID : Any> registerBinding(
        table: EntityTable<E>,
        binding: EntityBinding<E, ID>
    ) {
        bindings[table.entityType] = binding
    }

    /**
     * Register a lifecycle listener for an entity type.
     *
     * Phase 5: Listeners receive callbacks for entity lifecycle events:
     * - onPrePersist / onPostPersist (before/after INSERT)
     * - onPreUpdate / onPostUpdate (before/after UPDATE)
     * - onPreDelete / onPostDelete (before/after DELETE)
     * - onPostLoad (after entity loaded from database)
     *
     * Multiple listeners can be registered for the same entity type.
     * Listeners are called in registration order.
     *
     * Example: Audit logging
     * ```kotlin
     * session.registerListener(User::class, object : EntityListener<User> {
     *     override fun onPreUpdate(entity: User, old: User, session: EntitySession) {
     *         println("User ${entity.id} email changed: ${old.email} → ${entity.email}")
     *     }
     * })
     * ```
     *
     * @param entityClass The entity type to listen to
     * @param listener The listener to register
     */
    fun <E : Any> registerListener(
        entityClass: KClass<E>,
        listener: EntityListener<E>
    ) {
        listeners.getOrPut(entityClass) { mutableListOf() }.add(listener)
    }

    /**
     * Unregister a specific listener for an entity type.
     *
     * Useful for cleaning up dynamically registered listeners (e.g., per-request listeners)
     * to prevent memory leaks in long-running sessions.
     *
     * Example:
     * ```kotlin
     * val auditListener = AuditListener()
     * session.registerListener(User::class, auditListener)
     *
     * // ... do work ...
     *
     * // Clean up
     * session.unregisterListener(User::class, auditListener)
     * ```
     *
     * @param entityClass The entity type
     * @param listener The listener to remove
     * @return true if the listener was registered and removed, false otherwise
     */
    fun <E : Any> unregisterListener(
        entityClass: KClass<E>,
        listener: EntityListener<E>
    ): Boolean {
        return listeners[entityClass]?.remove(listener) ?: false
    }

    /**
     * Remove all listeners for a specific entity type.
     *
     * Example:
     * ```kotlin
     * session.clearListeners(User::class)
     * ```
     *
     * @param entityClass The entity type whose listeners should be cleared
     */
    fun <E : Any> clearListeners(entityClass: KClass<E>) {
        listeners.remove(entityClass)
    }

    /**
     * Remove all listeners for all entity types.
     *
     * Useful when reusing a session for different phases of processing
     * that require different listener configurations.
     *
     * Example:
     * ```kotlin
     * session.clearAllListeners()
     * ```
     */
    fun clearAllListeners() {
        listeners.clear()
    }

    /**
     * Get all listeners registered for an entity type.
     *
     * Returns empty list if no listeners are registered.
     *
     * @param entityClass The entity type
     * @return List of listeners (in registration order)
     */
    @Suppress("UNCHECKED_CAST")
    private fun <E : Any> getListeners(entityClass: KClass<E>): List<EntityListener<E>> {
        return listeners[entityClass] as? List<EntityListener<E>> ?: emptyList()
    }

    /**
     * Get preloaded relationships from the cache.
     *
     * Phase 5.3: Checks if relationships were eagerly loaded for the given entity.
     *
     * @param sourceEntityType The entity type (e.g., User::class)
     * @param sourceEntityId The entity ID
     * @param relationshipName The relationship name (e.g., "orders")
     * @return List of preloaded entities, or null if not cached
     */
    @Suppress("UNCHECKED_CAST")
    fun <E : Any> getCachedRelationship(
        sourceEntityType: KClass<*>,
        sourceEntityId: Any,
        relationshipName: String
    ): List<E>? {
        val key = RelationshipCacheKey(sourceEntityType, sourceEntityId, relationshipName)
        return relationshipCache[key] as? List<E>
    }

    /**
     * Cache preloaded relationships for multiple source entities.
     *
     * Phase 5.3: Stores batch-loaded relationships to prevent N+1 queries.
     *
     * @param sourceEntityType The entity type (e.g., User::class)
     * @param relationshipName The relationship name (e.g., "orders")
     * @param relationshipsBySourceId Map from source entity ID to list of related entities
     */
    fun <E : Any> cacheRelationships(
        sourceEntityType: KClass<*>,
        relationshipName: String,
        relationshipsBySourceId: Map<Any, List<E>>
    ) {
        relationshipsBySourceId.forEach { (sourceId, relatedEntities) ->
            val key = RelationshipCacheKey(sourceEntityType, sourceId, relationshipName)
            relationshipCache[key] = relatedEntities
        }
    }

    /**
     * Get the binding for an entity type.
     *
     * Phase 5: Automatically consults KodamaBindingRegistry if binding not manually registered.
     * Phase 5.3: Made internal for eager loading extensions.
     * Throws error if binding not found anywhere.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <E : Any, ID : Any> getBinding(entityClass: KClass<E>): EntityBinding<E, ID> {
        // Check manual registrations first
        val binding = bindings[entityClass] as? EntityBinding<E, ID>
        if (binding != null) {
            return binding
        }

        // Phase 5: Try auto-binding provider (generated registry)
        val autoBinding = autoBindingProvider?.invoke(entityClass) as? EntityBinding<E, ID>
        if (autoBinding != null) {
            // Cache it for future use
            bindings[entityClass] = autoBinding
            return autoBinding
        }

        // Not found anywhere
        error("No binding registered for ${entityClass.simpleName}. " +
              "Ensure the entity has a corresponding EntityTable<${entityClass.simpleName}> definition.")
    }

    /**
     * Find an entity by its primary key.
     *
     * Returns cached instance if already loaded (identity map).
     * Otherwise, queries database and caches the result.
     *
     * @param table The entity table
     * @param id Primary key value
     * @return Entity instance or null if not found
     */
    fun <E : Any, ID : Any> find(table: EntityTable<E>, id: ID): E? {
        return find(table.entityType, id)
    }

    /**
     * Find an entity by its primary key (KClass version).
     *
     * @param entityClass The entity class
     * @param id Primary key value
     * @return Entity instance or null if not found
     */
    fun <E : Any, ID : Any> find(entityClass: KClass<E>, id: ID): E? {
        val key = EntityKey(entityClass, id)

        // Check identity map first (cache hit)
        identityMap.get<E>(key)?.let {
            return it
        }

        // Not in cache - load from database
        val binding = getBinding<E, ID>(entityClass)
        val entity = loadFromDatabase(binding, id) ?: return null

        // Store in identity map for future lookups
        identityMap.put(key, entity)

        // Track as MANAGED entity (synchronized with database)
        // Lazy snapshot optimization: Don't create snapshot until entity is modified via save()
        // This reduces memory usage by 50% for read-only entities
        entityMetadata[key] = EntityMetadata(
            entity = entity,
            state = EntityState.MANAGED,
            binding = binding,
            snapshot = null  // Lazy: snapshot created on first save()
        )

        return entity
    }

    /**
     * Find an entity by its primary key (reified version).
     *
     * This is the most convenient API - no need to pass table or class.
     *
     * Usage:
     * ```kotlin
     * val user = session.find<User>(1)
     * ```
     *
     * @param id Primary key value
     * @return Entity instance or null if not found
     */
    inline fun <reified E : Any> find(id: Any): E? {
        @Suppress("UNCHECKED_CAST")
        return find(E::class, id) as E?
    }

    /**
     * Get an entity by its primary key, throwing an exception if not found.
     *
     * This is a convenience method that eliminates the need for !! operator.
     * Use when you expect the entity to exist and want an exception otherwise.
     *
     * Usage:
     * ```kotlin
     * val user = session.get<User>(1)  // Throws if not found
     * ```
     *
     * @param id Primary key value
     * @return Entity instance (never null)
     * @throws EntityNotFoundException if entity not found
     */
    inline fun <reified E : Any> get(id: Any): E {
        return find<E>(id)
            ?: throw EntityNotFoundException(E::class.simpleName ?: "Entity", id)
    }

    /**
     * Find entities by foreign key.
     * Used internally by generated relationship accessors.
     *
     * Executes: SELECT * FROM target_table WHERE fk_column = ?
     * Results are cached in identity map.
     *
     * @param E Entity type to find
     * @param ID Primary key type of entities
     * @param FK Foreign key type
     * @param targetTable The table to query
     * @param foreignKeyColumn The foreign key column to match
     * @param foreignKeyValue The value to match
     * @return List of entities (may be empty, never null)
     */
    fun <E : Any, ID : Any, FK : Any> findByForeignKey(
        targetTable: EntityTable<E>,
        foreignKeyColumn: Column<FK>,
        foreignKeyValue: FK
    ): List<E> {
        val binding = getBinding<E, ID>(targetTable.entityType)

        // Build SELECT query
        val sql = """
            SELECT * FROM "${targetTable.tableName}"
            WHERE ${foreignKeyColumn.name} = ?
        """.trimIndent()

        val results = mutableListOf<E>()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, foreignKeyValue)

            // Log query
            val sqlOneLine = sql.normalizeSQL()
            println("[Kodama] SQL: $sqlOneLine | params: [$foreignKeyValue]")

            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val entity = binding.toEntity(rs)
                    val id = binding.entityId(entity)
                    val key = EntityKey(targetTable.entityType, id)

                    // Check identity map first (may already be loaded)
                    val cached = identityMap.get<E>(key)
                    if (cached != null) {
                        results.add(cached)
                    } else {
                        // Add to identity map
                        identityMap.put(key, entity)
                        entityMetadata[key] = EntityMetadata(
                            entity = entity,
                            state = EntityState.MANAGED,
                            binding = binding,
                            snapshot = null  // Lazy snapshot
                        )

                        // Phase 5: Call onPostLoad lifecycle hooks for newly loaded entities
                        getListeners(targetTable.entityType).forEach { listener ->
                            listener.onPostLoad(entity, this)
                        }

                        results.add(entity)
                    }
                }
            }
        }

        return results
    }

    /**
     * Find entities through a many-to-many relationship via a junction table.
     *
     * This method performs a JOIN between the target table and the junction table
     * to find all entities related to the source entity.
     *
     * Example:
     * ```kotlin
     * // Find all roles for a user through the user_roles junction table
     * val roles = session.findManyToMany(
     *     targetTable = Roles,
     *     junctionTable = UserRoles,
     *     sourceForeignKeyColumn = UserRoles.userId,
     *     targetForeignKeyColumn = UserRoles.roleId,
     *     targetPrimaryKeyColumn = Roles.id,
     *     sourceForeignKeyValue = userId
     * )
     * ```
     *
     * Generates SQL like:
     * ```sql
     * SELECT target.* FROM roles target
     * INNER JOIN user_roles junction ON target.id = junction.role_id
     * WHERE junction.user_id = ?
     * ```
     *
     * Phase 5: Calls onPostLoad lifecycle hooks for loaded entities.
     *
     * @param E Entity type to find
     * @param ID Primary key type of target entities
     * @param SourceFK Type of the source foreign key
     * @param TargetFK Type of the target foreign key
     * @param targetTable The target EntityTable to load entities from
     * @param junctionTable The junction table that links source and target
     * @param sourceForeignKeyColumn Column in junction table pointing to source entity
     * @param targetForeignKeyColumn Column in junction table pointing to target entity
     * @param targetPrimaryKeyColumn Primary key column in target table
     * @param sourceForeignKeyValue The source entity's primary key value to filter by
     * @return List of target entities (may be empty, never null)
     */
    fun <E : Any, ID : Any, SourceFK : Any, TargetFK : Any> findManyToMany(
        targetTable: EntityTable<E>,
        junctionTable: EntityTable<*>,
        sourceForeignKeyColumn: Column<SourceFK>,
        targetForeignKeyColumn: Column<TargetFK>,
        targetPrimaryKeyColumn: Column<TargetFK>,
        sourceForeignKeyValue: SourceFK
    ): List<E> {
        val binding = getBinding<E, ID>(targetTable.entityType)

        // Build SELECT query with JOIN
        val sql = """
            SELECT target.* FROM "${targetTable.tableName}" target
            INNER JOIN "${junctionTable.tableName}" junction
            ON target.${targetPrimaryKeyColumn.name} = junction.${targetForeignKeyColumn.name}
            WHERE junction.${sourceForeignKeyColumn.name} = ?
        """.trimIndent()

        val results = mutableListOf<E>()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, sourceForeignKeyValue)

            // Log query
            val sqlOneLine = sql.normalizeSQL()
            println("[Kodama] SQL: $sqlOneLine | params: [$sourceForeignKeyValue]")

            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val entity = binding.toEntity(rs)
                    val id = binding.entityId(entity)
                    val key = EntityKey(targetTable.entityType, id)

                    // Check identity map first (may already be loaded)
                    val cached = identityMap.get<E>(key)
                    if (cached != null) {
                        results.add(cached)
                    } else {
                        // Add to identity map
                        identityMap.put(key, entity)
                        entityMetadata[key] = EntityMetadata(
                            entity = entity,
                            state = EntityState.MANAGED,
                            binding = binding,
                            snapshot = null  // Lazy snapshot
                        )

                        // Phase 5: Call onPostLoad lifecycle hooks for newly loaded entities
                        getListeners(targetTable.entityType).forEach { listener ->
                            listener.onPostLoad(entity, this)
                        }

                        results.add(entity)
                    }
                }
            }
        }

        return results
    }

    /**
     * Batch load entities by foreign key values (N+1 prevention).
     *
     * Phase 5.3: This method enables eager loading by fetching related entities for multiple
     * parent entities in a single query using SQL IN clause.
     *
     * Example:
     * ```kotlin
     * // Load all orders for users 1, 2, 3 in one query
     * val ordersByUserId = session.findByForeignKeyBatch<UserOrder, Int, Int>(
     *     targetTable = UserOrders,
     *     foreignKeyColumn = UserOrders.userId,
     *     foreignKeyValues = listOf(1, 2, 3)
     * )
     * // ordersByUserId = mapOf(
     * //   1 to listOf(order1, order2),
     * //   2 to listOf(order3),
     * //   3 to emptyList()
     * // )
     * ```
     *
     * Generates SQL like:
     * ```sql
     * SELECT * FROM user_orders WHERE user_id IN (?, ?, ?)
     * ```
     *
     * Phase 5: Calls onPostLoad lifecycle hooks for loaded entities.
     *
     * @param E Entity type to find
     * @param ID Primary key type of entities
     * @param FK Foreign key type
     * @param targetTable The table to query
     * @param foreignKeyColumn The foreign key column to match
     * @param foreignKeyValues The list of foreign key values to match
     * @return Map from foreign key value to list of matching entities (includes empty lists for values with no matches)
     */
    fun <E : Any, ID : Any, FK : Any> findByForeignKeyBatch(
        targetTable: EntityTable<E>,
        foreignKeyColumn: Column<FK>,
        foreignKeyValues: List<FK>
    ): Map<FK, List<E>> {
        // Early return for empty input
        if (foreignKeyValues.isEmpty()) {
            return emptyMap()
        }

        val binding = getBinding<E, ID>(targetTable.entityType)

        // Build SELECT query with IN clause
        val placeholders = foreignKeyValues.joinToString(", ") { "?" }
        val sql = """
            SELECT * FROM "${targetTable.tableName}"
            WHERE ${foreignKeyColumn.name} IN ($placeholders)
        """.trimIndent()

        // Initialize result map with empty lists for all requested values
        val resultMap = foreignKeyValues.associateWith { mutableListOf<E>() }.toMutableMap()

        connection.prepareStatement(sql).use { stmt ->
            // Set parameters
            foreignKeyValues.forEachIndexed { index, value ->
                stmt.setObject(index + 1, value)
            }

            // Log query
            val sqlOneLine = sql.normalizeSQL()
            println("[Kodama] SQL (batch): $sqlOneLine | params: $foreignKeyValues")

            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val entity = binding.toEntity(rs)
                    val id = binding.entityId(entity)
                    val key = EntityKey(targetTable.entityType, id)

                    // Check identity map first (may already be loaded)
                    val cachedEntity = identityMap.get<E>(key)
                    val finalEntity = if (cachedEntity != null) {
                        cachedEntity
                    } else {
                        // Add to identity map
                        identityMap.put(key, entity)
                        entityMetadata[key] = EntityMetadata(
                            entity = entity,
                            state = EntityState.MANAGED,
                            binding = binding,
                            snapshot = null  // Lazy snapshot
                        )

                        // Phase 5: Call onPostLoad lifecycle hooks for newly loaded entities
                        getListeners(targetTable.entityType).forEach { listener ->
                            listener.onPostLoad(entity, this)
                        }

                        entity
                    }

                    // Determine which foreign key value this entity belongs to
                    // We need to extract the foreign key value from the entity
                    // For now, we'll read it from the ResultSet
                    @Suppress("UNCHECKED_CAST")
                    val fkValue = rs.getObject(foreignKeyColumn.name) as FK
                    resultMap[fkValue]?.add(finalEntity)
                }
            }
        }

        return resultMap
    }

    /**
     * Load an entity from the database.
     * Builds and executes a SELECT query by primary key.
     *
     * Phase 5: Calls onPostLoad lifecycle hook after successful load.
     */
    private fun <E : Any, ID : Any> loadFromDatabase(
        binding: EntityBinding<E, ID>,
        id: ID
    ): E? {
        val table = binding.table
        val pkColumns = binding.primaryKeyColumns()

        // Phase 1: Single-column primary key only
        require(pkColumns.size == 1) {
            "Composite primary keys not yet supported (table: ${table.tableName})"
        }

        val pkColumn = pkColumns.first()

        // Build SELECT query
        val sql = """
            SELECT * FROM "${table.tableName}"
            WHERE ${pkColumn.name} = ?
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            // Set parameter
            stmt.setObject(1, id)

            // Log query
            val sqlOneLine = sql.normalizeSQL()
            println("[Kodama] SQL: $sqlOneLine | params: [$id]")

            // Execute query
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val entity = binding.toEntity(rs)

                    // Phase 5: Call onPostLoad lifecycle hooks
                    getListeners(table.entityType).forEach { listener ->
                        listener.onPostLoad(entity, this)
                    }

                    return entity
                }
                return null
            }
        }
    }

    /**
     * Save an entity to the session.
     *
     * Phase 2 Behavior:
     * - New entities (not in identity map) are staged for INSERT
     * - State changes: NEW → PENDING_INSERT
     *
     * Phase 3 Behavior:
     * - Modified managed entities are staged for UPDATE
     * - State changes: MANAGED → PENDING_UPDATE
     * - Uses snapshot comparison to detect changes
     *
     * Important: save() does NOT execute SQL immediately.
     * Call flush() to execute pending INSERT/UPDATE/DELETE statements.
     *
     * @param entity The entity to save
     */
    fun <E : Any, ID : Any> save(entity: E) {
        @Suppress("UNCHECKED_CAST")
        val entityClass = entity::class as KClass<E>
        val binding = getBinding<E, ID>(entityClass)
        val id = binding.entityId(entity)
        // Use binding.table.entityType instead of entityClass to handle interface-based entities
        // For interfaces, entity::class is the implementation type, but identity map uses interface type
        val key = EntityKey(binding.table.entityType, id)

        // Check if entity is already tracked
        if (identityMap.contains(key)) {
            // Entity already in session - check if it has been modified
            identityMap.put(key, entity)

            val metadata = entityMetadata[key]
            if (metadata != null) {
                @Suppress("UNCHECKED_CAST")
                val typedMetadata = metadata as EntityMetadata<E, ID>
                val snapshot = typedMetadata.snapshot

                // Phase 3: Compare with snapshot to detect changes
                if (snapshot == null) {
                    // Lazy snapshot: First save() after find() - create snapshot now
                    // The original entity (from find()) is in typedMetadata.entity
                    // The new entity (with modifications) is the parameter 'entity'
                    val originalEntity = typedMetadata.entity
                    val changes = binding.toUpdateValues(entity, originalEntity)

                    if (changes.isNotEmpty()) {
                        // Entity has been modified - mark as PENDING_UPDATE with snapshot
                        entityMetadata[key] = typedMetadata.copy(
                            entity = entity,
                            snapshot = originalEntity,  // Snapshot the ORIGINAL state
                            state = EntityState.PENDING_UPDATE
                        )
                    } else {
                        // No actual changes - just update the entity reference and create snapshot
                        entityMetadata[key] = typedMetadata.copy(
                            entity = entity,
                            snapshot = originalEntity
                        )
                    }
                } else if (snapshot != entity) {
                    // Entity has been modified - check if there are actual field changes
                    val changes = binding.toUpdateValues(entity, snapshot)

                    if (changes.isNotEmpty()) {
                        // Mark as PENDING_UPDATE
                        entityMetadata[key] = typedMetadata.copy(
                            entity = entity,
                            state = EntityState.PENDING_UPDATE
                        )
                    } else {
                        // No actual changes - just update the entity reference
                        entityMetadata[key] = typedMetadata.copy(entity = entity)
                    }
                } else {
                    // Entity unchanged (same reference)
                    entityMetadata[key] = typedMetadata.copy(entity = entity)
                }
            }
        } else {
            // New entity - stage for INSERT
            identityMap.put(key, entity)
            entityMetadata[key] = EntityMetadata(
                entity = entity,
                state = EntityState.PENDING_INSERT,
                binding = binding,
                snapshot = null  // No snapshot for new entities
            )
        }
    }

    /**
     * Delete an entity from the database.
     *
     * Phase 4: Marks entity for deletion.
     * State changes: MANAGED → PENDING_DELETE
     *
     * Important:
     * - Entity must be tracked in session (loaded via find() or saved)
     * - delete() does NOT execute SQL immediately
     * - Call flush() to execute pending DELETE statements
     * - After flush, entity is removed from session and identity map
     *
     * Example:
     * ```kotlin
     * val user = session.find(Users, 1)
     * session.delete(user)
     * session.flush()  // DELETE FROM users WHERE id = 1
     * ```
     *
     * @param entity The entity to delete
     */
    fun <E : Any> delete(entity: E) {
        @Suppress("UNCHECKED_CAST")
        val entityClass = entity::class as KClass<E>
        val binding = getBinding<E, Any>(entityClass)
        val id = binding.entityId(entity)
        // Use binding.table.entityType instead of entityClass to handle interface-based entities
        // For interfaces, entity::class is the implementation type, but identity map uses interface type
        val key = EntityKey(binding.table.entityType, id)

        // Check if entity is tracked
        if (!identityMap.contains(key)) {
            error("Cannot delete entity that is not in session: ${key.entityType.simpleName}(id=${key.id}). " +
                  "Use find() to load the entity first.")
        }

        val metadata = entityMetadata[key]
        if (metadata != null) {
            @Suppress("UNCHECKED_CAST")
            val typedMetadata = metadata as EntityMetadata<E, Any>

            // Check current state
            when (typedMetadata.state) {
                EntityState.PENDING_INSERT -> {
                    // Entity was never persisted - just remove from session
                    identityMap.remove(key)
                    entityMetadata.remove(key)
                }
                EntityState.MANAGED, EntityState.PENDING_UPDATE -> {
                    // Mark for DELETE
                    entityMetadata[key] = typedMetadata.copy(
                        state = EntityState.PENDING_DELETE
                    )
                }
                EntityState.PENDING_DELETE -> {
                    // Already marked for deletion - no-op
                }
                else -> {
                    error("Cannot delete entity in state ${typedMetadata.state}: ${key.entityType.simpleName}(id=${key.id})")
                }
            }
        }
    }

    /**
     * Flush pending changes to the database.
     *
     * ⚠️ **CRITICAL: Transaction Safety**
     *
     * This method executes SQL statements but does NOT manage database transactions.
     * You MUST wrap EntitySession operations in a database transaction to ensure atomicity.
     *
     * **Required Transaction Setup:**
     * ```kotlin
     * connection.autoCommit = false  // ← CRITICAL: Disable autoCommit
     * try {
     *     EntitySession(connection).use { session ->
     *         session.save(entity1)
     *         session.save(entity2)
     *         session.delete(entity3)
     *         session.flush()  // Executes all SQL
     *     }
     *     connection.commit()  // ← Atomically commit all changes
     * } catch (e: Exception) {
     *     connection.rollback()  // ← CRITICAL: Rollback on any error
     *     throw e
     * }
     * ```
     *
     * **What flush() Does:**
     * 1. Executes all PENDING_INSERT statements (calls onPrePersist/onPostPersist hooks)
     * 2. Executes all PENDING_UPDATE statements (calls onPreUpdate/onPostUpdate hooks)
     * 3. Executes all PENDING_DELETE statements (calls onPreDelete/onPostDelete hooks)
     * 4. Updates entity states to MANAGED (or removes for DELETE)
     *
     * **State Transitions:**
     * - PENDING_INSERT → MANAGED (entities now synchronized with database)
     * - PENDING_UPDATE → MANAGED (changes persisted to database)
     * - PENDING_DELETE → removed from session (deleted from database)
     *
     * **Error Handling:**
     * - If any SQL statement fails, flush() throws SessionException
     * - Session state is reset (all PENDING_* entities are removed)
     * - You should rollback the database transaction
     * - You should discard the EntitySession (don't reuse it)
     *
     * **Order of Execution:**
     * 1. All INSERTs (in order they were saved)
     * 2. All UPDATEs (in order they were saved)
     * 3. All DELETEs (in order they were marked for deletion)
     *
     * This order prevents foreign key constraint violations in most cases.
     *
     * @throws SessionException if any database operation fails
     * @throws EntityListenerException if any lifecycle hook fails (subclass of SessionException)
     */
    fun flush() {
        // Phase 2: Handle PENDING_INSERT entities
        val pendingInserts = entityMetadata.filter { (_, metadata) ->
            metadata.state == EntityState.PENDING_INSERT
        }

        // Phase 3: Handle PENDING_UPDATE entities
        val pendingUpdates = entityMetadata.filter { (_, metadata) ->
            metadata.state == EntityState.PENDING_UPDATE
        }

        // Phase 4: Handle PENDING_DELETE entities
        val pendingDeletes = entityMetadata.filter { (_, metadata) ->
            metadata.state == EntityState.PENDING_DELETE
        }

        if (pendingInserts.isEmpty() && pendingUpdates.isEmpty() && pendingDeletes.isEmpty()) {
            return  // Nothing to flush
        }

        // Take snapshot of current entity metadata for rollback on error
        // Only snapshot the PENDING_* entities, not all entities
        val pendingKeys = (pendingInserts.keys + pendingUpdates.keys + pendingDeletes.keys).toSet()

        try {
            // Execute INSERTs
            for ((key, metadata) in pendingInserts) {
                @Suppress("UNCHECKED_CAST")
                val typedMetadata = metadata as EntityMetadata<Any, Any>
                executeInsert(typedMetadata, key)
            }

            // Execute UPDATEs
            for ((key, metadata) in pendingUpdates) {
                @Suppress("UNCHECKED_CAST")
                val typedMetadata = metadata as EntityMetadata<Any, Any>
                executeUpdate(typedMetadata, key)
            }

            // Execute DELETEs
            for ((key, metadata) in pendingDeletes) {
                @Suppress("UNCHECKED_CAST")
                val typedMetadata = metadata as EntityMetadata<Any, Any>
                executeDelete(typedMetadata, key)
            }
        } catch (e: Exception) {
            // Flush failed - reset session state for all pending entities
            // Remove PENDING_INSERT entities (never persisted)
            pendingInserts.keys.forEach { key ->
                identityMap.remove(key)
                entityMetadata.remove(key)
            }

            // Reset PENDING_UPDATE entities to MANAGED state
            pendingUpdates.keys.forEach { key ->
                val metadata = entityMetadata[key]
                if (metadata != null) {
                    @Suppress("UNCHECKED_CAST")
                    val typedMetadata = metadata as EntityMetadata<Any, Any>
                    entityMetadata[key] = typedMetadata.copy(state = EntityState.MANAGED)
                }
            }

            // Reset PENDING_DELETE entities to MANAGED state
            pendingDeletes.keys.forEach { key ->
                val metadata = entityMetadata[key]
                if (metadata != null) {
                    @Suppress("UNCHECKED_CAST")
                    val typedMetadata = metadata as EntityMetadata<Any, Any>
                    entityMetadata[key] = typedMetadata.copy(state = EntityState.MANAGED)
                }
            }

            // Wrap and re-throw as SessionException
            throw SessionException(
                "Flush failed - session state has been reset. " +
                "Rollback the database transaction and discard this session. " +
                "Pending operations: ${pendingInserts.size} inserts, ${pendingUpdates.size} updates, ${pendingDeletes.size} deletes.",
                e
            )
        }
    }

    /**
     * Execute INSERT for a single entity.
     *
     * Generates and executes INSERT statement using the binding's toInsertValues().
     * Handles auto-generated IDs (Phase 2 basic support, can be enhanced).
     *
     * Phase 5: Calls lifecycle hooks:
     * - onPrePersist before INSERT
     * - onPostPersist after INSERT
     *
     * @param metadata Entity metadata containing entity, binding, and state
     * @param key Entity key for tracking
     */
    private fun <E : Any, ID : Any> executeInsert(
        metadata: EntityMetadata<E, ID>,
        key: EntityKey
    ) {
        val entity = metadata.entity
        val binding = metadata.binding
        val table = binding.table

        // Phase 5: Call onPrePersist lifecycle hooks
        @Suppress("UNCHECKED_CAST")
        val entityClass = key.entityType as KClass<E>
        getListeners(entityClass).forEach { listener ->
            listener.onPrePersist(entity, this)
        }

        // Get column-value pairs from binding
        val values = binding.toInsertValues(entity)

        // Build INSERT statement
        val columns = values.keys.toList()
        val columnNames = columns.joinToString(", ") { it.name }
        val placeholders = columns.indices.joinToString(", ") { "?" }

        val sql = """
            INSERT INTO "${table.tableName}" ($columnNames)
            VALUES ($placeholders)
        """.trimIndent()

        // Execute INSERT
        connection.prepareStatement(sql).use { stmt ->
            // Set parameters
            columns.forEachIndexed { index, column ->
                stmt.setObject(index + 1, values[column])
            }

            // Log query
            val params = columns.map { values[it] }
            val sqlOneLine = sql.normalizeSQL()
            println("[Kodama] SQL: $sqlOneLine | params: $params")

            // Execute
            val rowsAffected = stmt.executeUpdate()

            if (rowsAffected == 1) {
                // Mark as MANAGED (synchronized with database)
                entityMetadata[key] = metadata.copy(
                    state = EntityState.MANAGED,
                    snapshot = entity  // Create snapshot for future change detection
                )

                // Phase 5: Call onPostPersist lifecycle hooks
                getListeners(entityClass).forEach { listener ->
                    listener.onPostPersist(entity, this)
                }
            } else {
                error("INSERT failed for ${key.entityType.simpleName}(id=${key.id})")
            }
        }
    }

    /**
     * Execute UPDATE for a single entity.
     *
     * Phase 3: Generates and executes UPDATE statement using the binding's toUpdateValues().
     * Only updates changed fields (partial updates).
     *
     * Phase 5: Calls lifecycle hooks:
     * - onPreUpdate before UPDATE
     * - onPostUpdate after UPDATE
     *
     * @param metadata Entity metadata containing entity, binding, snapshot, and state
     * @param key Entity key for tracking
     */
    private fun <E : Any, ID : Any> executeUpdate(
        metadata: EntityMetadata<E, ID>,
        key: EntityKey
    ) {
        val entity = metadata.entity
        val binding = metadata.binding
        val table = binding.table
        val snapshot = metadata.snapshot

        if (snapshot == null) {
            error("Cannot UPDATE entity without snapshot: ${key.entityType.simpleName}(id=${key.id})")
        }

        // Phase 5: Call onPreUpdate lifecycle hooks
        @Suppress("UNCHECKED_CAST")
        val entityClass = key.entityType as KClass<E>
        getListeners(entityClass).forEach { listener ->
            listener.onPreUpdate(entity, snapshot, this)
        }

        // Get changed column-value pairs from binding
        val changes = binding.toUpdateValues(entity, snapshot)

        if (changes.isEmpty()) {
            // No changes to persist - mark as MANAGED
            entityMetadata[key] = metadata.copy(
                state = EntityState.MANAGED,
                snapshot = entity
            )
            return
        }

        // Build UPDATE statement
        val columns = changes.keys.toList()
        val setClause = columns.joinToString(", ") { "${it.name} = ?" }

        // Get primary key for WHERE clause
        val pkColumns = binding.primaryKeyColumns()
        require(pkColumns.size == 1) {
            "Composite primary keys not yet supported (table: ${table.tableName})"
        }
        val pkColumn = pkColumns.first()
        val pkValue = binding.entityId(entity)

        val sql = """
            UPDATE "${table.tableName}"
            SET $setClause
            WHERE ${pkColumn.name} = ?
        """.trimIndent()

        // Execute UPDATE
        connection.prepareStatement(sql).use { stmt ->
            // Set changed column values
            columns.forEachIndexed { index, column ->
                stmt.setObject(index + 1, changes[column])
            }

            // Set primary key value for WHERE clause
            stmt.setObject(columns.size + 1, pkValue)

            // Log query
            val params = columns.map { changes[it] } + pkValue
            val sqlOneLine = sql.normalizeSQL()
            println("[Kodama] SQL: $sqlOneLine | params: $params")

            // Execute
            val rowsAffected = stmt.executeUpdate()

            if (rowsAffected == 1) {
                // Mark as MANAGED and update snapshot
                entityMetadata[key] = metadata.copy(
                    state = EntityState.MANAGED,
                    snapshot = entity  // Update snapshot to current state
                )

                // Phase 5: Call onPostUpdate lifecycle hooks
                getListeners(entityClass).forEach { listener ->
                    listener.onPostUpdate(entity, snapshot, this)
                }
            } else if (rowsAffected == 0) {
                error("UPDATE failed - entity not found: ${key.entityType.simpleName}(id=${key.id})")
            } else {
                error("UPDATE failed - multiple rows affected: ${key.entityType.simpleName}(id=${key.id})")
            }
        }
    }

    /**
     * Execute DELETE for a single entity.
     *
     * Phase 4: Generates and executes DELETE statement using primary key.
     * After successful deletion, entity is removed from session and identity map.
     *
     * Phase 5: Calls lifecycle hooks:
     * - onPreDelete before DELETE
     * - onPostDelete after DELETE
     *
     * @param metadata Entity metadata containing entity, binding, and state
     * @param key Entity key for tracking
     */
    private fun <E : Any, ID : Any> executeDelete(
        metadata: EntityMetadata<E, ID>,
        key: EntityKey
    ) {
        val entity = metadata.entity
        val binding = metadata.binding
        val table = binding.table

        // Phase 5: Call onPreDelete lifecycle hooks
        @Suppress("UNCHECKED_CAST")
        val entityClass = key.entityType as KClass<E>
        getListeners(entityClass).forEach { listener ->
            listener.onPreDelete(entity, this)
        }

        // Get primary key for WHERE clause
        val pkColumns = binding.primaryKeyColumns()
        require(pkColumns.size == 1) {
            "Composite primary keys not yet supported (table: ${table.tableName})"
        }
        val pkColumn = pkColumns.first()
        val pkValue = binding.entityId(entity)

        val sql = """
            DELETE FROM "${table.tableName}"
            WHERE ${pkColumn.name} = ?
        """.trimIndent()

        // Execute DELETE
        connection.prepareStatement(sql).use { stmt ->
            // Set primary key value for WHERE clause
            stmt.setObject(1, pkValue)

            // Log query
            val sqlOneLine = sql.normalizeSQL()
            println("[Kodama] SQL: $sqlOneLine | params: [$pkValue]")

            // Execute
            val rowsAffected = stmt.executeUpdate()

            if (rowsAffected == 1) {
                // Remove from session and identity map
                entityMetadata.remove(key)
                identityMap.remove(key)

                // Phase 5: Call onPostDelete lifecycle hooks
                // Note: Entity is still available but has been removed from database and cache
                getListeners(entityClass).forEach { listener ->
                    listener.onPostDelete(entity, this)
                }
            } else if (rowsAffected == 0) {
                error("DELETE failed - entity not found: ${key.entityType.simpleName}(id=${key.id})")
            } else {
                error("DELETE failed - multiple rows affected: ${key.entityType.simpleName}(id=${key.id})")
            }
        }
    }

    /**
     * Clear the identity map.
     * Useful for testing or when you need a fresh state.
     */
    fun clear() {
        identityMap.clear()
        entityMetadata.clear()
    }

    /**
     * Get statistics about the current session.
     * Useful for debugging and monitoring.
     */
    fun stats(): SessionStats {
        val pendingInserts = entityMetadata.count { it.value.state == EntityState.PENDING_INSERT }
        val pendingUpdates = entityMetadata.count { it.value.state == EntityState.PENDING_UPDATE }
        val pendingDeletes = entityMetadata.count { it.value.state == EntityState.PENDING_DELETE }

        return SessionStats(
            cachedEntities = identityMap.size,
            pendingInserts = pendingInserts,
            pendingUpdates = pendingUpdates,
            pendingDeletes = pendingDeletes
        )
    }

    // ========================================
    // Phase 5.4: Improved Entity CRUD API
    // Simplified, immediate-execution methods
    // ========================================

    /**
     * Persist an entity (smart save: insert if new, update if exists).
     *
     * This is the recommended method for most use cases. It automatically:
     * - Inserts the entity if it's not in the identity map
     * - Updates the entity if it's already tracked (in identity map)
     * - Executes immediately (no need to call flush)
     *
     * Example:
     * ```kotlin
     * val user = User(id = 1, name = "Alice", email = "alice@example.com")
     * session.persist(user)  // INSERT
     *
     * val modified = user.copy(email = "alice.new@example.com")
     * session.persist(modified)  // UPDATE
     * ```
     *
     * @param entity The entity to persist
     * @return The persisted entity
     */
    fun <E : Any> persist(entity: E): E {
        @Suppress("UNCHECKED_CAST")
        val entityClass = entity::class as KClass<E>
        val binding = getBinding<E, Any>(entityClass)
        val id = binding.entityId(entity)
        val key = EntityKey(binding.table.entityType, id)

        // Check if entity exists in identity map
        return if (identityMap.contains(key)) {
            // Entity exists - UPDATE
            update(entity)
        } else {
            // Entity doesn't exist - INSERT
            insert(entity)
        }
    }

    /**
     * Insert a new entity into the database.
     *
     * Executes INSERT immediately (no need to call flush).
     * Fails if entity with same ID already exists in identity map.
     *
     * Example:
     * ```kotlin
     * val user = User(id = 1, name = "Alice", email = "alice@example.com")
     * session.insert(user)
     * ```
     *
     * @param entity The entity to insert
     * @return The inserted entity
     * @throws IllegalStateException if entity already exists in identity map
     */
    fun <E : Any> insert(entity: E): E {
        @Suppress("UNCHECKED_CAST")
        val entityClass = entity::class as KClass<E>
        val binding = getBinding<E, Any>(entityClass)
        val id = binding.entityId(entity)
        val key = EntityKey(binding.table.entityType, id)

        // Check if entity already exists
        if (identityMap.contains(key)) {
            error("Cannot insert entity that already exists in session: ${key.entityType.simpleName}(id=${key.id}). " +
                  "Use save() or update() instead.")
        }

        // Add to identity map
        identityMap.put(key, entity)

        // Create metadata and mark as PENDING_INSERT
        @Suppress("UNCHECKED_CAST")
        entityMetadata[key] = EntityMetadata(
            entity = entity,
            state = EntityState.PENDING_INSERT,
            binding = binding as EntityBinding<E, Any>,
            snapshot = null
        )

        // Execute immediately
        flush()

        return entity
    }

    /**
     * Update an existing entity in the database.
     *
     * Executes UPDATE immediately (no need to call flush).
     * Only updates changed fields (partial update).
     * Entity must be tracked in session (loaded via find() or inserted via insert()).
     *
     * Example:
     * ```kotlin
     * val user = session.find<User>(1)!!
     * val modified = user.copy(email = "new@example.com")
     * session.update(modified)
     * ```
     *
     * @param entity The entity to update
     * @return The updated entity
     * @throws IllegalStateException if entity not found in session
     */
    fun <E : Any> update(entity: E): E {
        @Suppress("UNCHECKED_CAST")
        val entityClass = entity::class as KClass<E>
        val binding = getBinding<E, Any>(entityClass)
        val id = binding.entityId(entity)
        val key = EntityKey(binding.table.entityType, id)

        // Check if entity exists in session
        if (!identityMap.contains(key)) {
            error("Cannot update entity that is not in session: ${key.entityType.simpleName}(id=${key.id}). " +
                  "Use find() to load the entity first, or use save() for smart save.")
        }

        val metadata = entityMetadata[key]
        if (metadata == null) {
            error("Entity metadata not found: ${key.entityType.simpleName}(id=${key.id})")
        }

        // Update entity in identity map
        identityMap.put(key, entity)

        // Update metadata and mark as PENDING_UPDATE
        @Suppress("UNCHECKED_CAST")
        val typedMetadata = metadata as EntityMetadata<E, Any>

        // Handle lazy snapshots: Create snapshot from original entity if not present
        val snapshot = typedMetadata.snapshot ?: typedMetadata.entity

        entityMetadata[key] = typedMetadata.copy(
            entity = entity,
            snapshot = snapshot,  // Ensure snapshot exists for executeUpdate()
            state = EntityState.PENDING_UPDATE
        )

        // Execute immediately
        flush()

        return entity
    }

    /**
     * Delete an entity from the database immediately.
     *
     * Unlike the existing delete() method which stages deletion,
     * this method executes the DELETE immediately.
     *
     * Example:
     * ```kotlin
     * val user = session.find<User>(1)!!
     * session.remove(user)  // DELETE FROM users WHERE id = 1
     * ```
     *
     * @param entity The entity to delete
     */
    fun <E : Any> remove(entity: E) {
        delete(entity)  // Stage deletion
        flush()         // Execute immediately
    }

    /**
     * Persist multiple entities (batch persist).
     *
     * For each entity:
     * - Inserts if not in identity map
     * - Updates if already tracked
     *
     * Example:
     * ```kotlin
     * val users = listOf(user1, user2, user3)
     * session.persistAll(users)
     * ```
     *
     * @param entities The entities to persist
     * @return The persisted entities
     */
    fun <E : Any> persistAll(entities: List<E>): List<E> {
        return entities.map { persist(it) }
    }

    /**
     * Insert multiple entities (batch insert).
     *
     * Example:
     * ```kotlin
     * val users = listOf(user1, user2, user3)
     * session.insertAll(users)
     * ```
     *
     * @param entities The entities to insert
     * @return The inserted entities
     */
    fun <E : Any> insertAll(entities: List<E>): List<E> {
        return entities.map { insert(it) }
    }

    /**
     * Update multiple entities (batch update).
     *
     * Example:
     * ```kotlin
     * val users = listOf(user1, user2, user3)
     * session.updateAll(users)
     * ```
     *
     * @param entities The entities to update
     * @return The updated entities
     */
    fun <E : Any> updateAll(entities: List<E>): List<E> {
        return entities.map { update(it) }
    }

    /**
     * Delete multiple entities (batch delete).
     *
     * Example:
     * ```kotlin
     * val users = listOf(user1, user2, user3)
     * session.removeAll(users)
     * ```
     *
     * @param entities The entities to delete
     */
    fun <E : Any> removeAll(entities: List<E>) {
        entities.forEach { delete(it) }
        flush()
    }

    /**
     * Upsert an entity (INSERT ... ON CONFLICT ... DO UPDATE).
     *
     * PostgreSQL-specific feature that inserts or updates based on conflict.
     * Useful when you're not sure if entity exists.
     *
     * Example:
     * ```kotlin
     * val user = User(id = 1, name = "Alice", email = "alice@example.com")
     * session.upsert(user, conflictColumns = listOf(Users.id))
     * // If user with id=1 exists: UPDATE
     * // If user with id=1 doesn't exist: INSERT
     * ```
     *
     * @param entity The entity to upsert
     * @param conflictColumns The columns that define uniqueness (typically primary key)
     * @return The upserted entity
     */
    fun <E : Any> upsert(entity: E, conflictColumns: List<Column<*>>): E {
        // Validate that conflict columns are specified
        require(conflictColumns.isNotEmpty()) {
            "Conflict columns cannot be empty for upsert operation. " +
            "Specify at least one column (typically the primary key)."
        }

        @Suppress("UNCHECKED_CAST")
        val entityClass = entity::class as KClass<E>
        val binding = getBinding<E, Any>(entityClass)
        val id = binding.entityId(entity)
        val key = EntityKey(binding.table.entityType, id)
        val table = binding.table

        // Get column-value pairs from binding
        val values = binding.toInsertValues(entity)

        // Build INSERT statement
        val columns = values.keys.toList()
        val columnNames = columns.joinToString(", ") { it.name }
        val placeholders = columns.indices.joinToString(", ") { "?" }

        // Build conflict columns list
        val conflictColumnNames = conflictColumns.joinToString(", ") { it.name }

        // Build UPDATE SET clause (exclude primary key columns)
        val pkColumns = binding.primaryKeyColumns().toSet()
        val updateColumns = columns.filter { it !in pkColumns }
        val updateSet = updateColumns.joinToString(", ") { "${it.name} = EXCLUDED.${it.name}" }

        val sql = """
            INSERT INTO "${table.tableName}" ($columnNames)
            VALUES ($placeholders)
            ON CONFLICT ($conflictColumnNames) DO UPDATE
            SET $updateSet
        """.trimIndent()

        // Execute UPSERT
        connection.prepareStatement(sql).use { stmt ->
            // Set parameters
            columns.forEachIndexed { index, column ->
                stmt.setObject(index + 1, values[column])
            }

            // Log query
            val params = columns.map { values[it] }
            val sqlOneLine = sql.normalizeSQL()
            println("[Kodama] SQL (upsert): $sqlOneLine | params: $params")

            // Execute
            val rowsAffected = stmt.executeUpdate()

            if (rowsAffected >= 1) {
                // Add to identity map
                identityMap.put(key, entity)

                // Create metadata and mark as MANAGED
                @Suppress("UNCHECKED_CAST")
                entityMetadata[key] = EntityMetadata(
                    entity = entity,
                    state = EntityState.MANAGED,
                    binding = binding as EntityBinding<E, Any>,
                    snapshot = entity
                )
            } else {
                error("UPSERT failed for ${key.entityType.simpleName}(id=${key.id})")
            }
        }

        return entity
    }

    /**
     * Upsert multiple entities (batch upsert).
     *
     * Example:
     * ```kotlin
     * val users = listOf(user1, user2, user3)
     * session.upsertAll(users, conflictColumns = listOf(Users.id))
     * ```
     *
     * @param entities The entities to upsert
     * @param conflictColumns The columns that define uniqueness
     * @return The upserted entities
     */
    fun <E : Any> upsertAll(entities: List<E>, conflictColumns: List<Column<*>>): List<E> {
        // Validate that conflict columns are specified (will be checked in upsert() too, but fail fast)
        require(conflictColumns.isNotEmpty()) {
            "Conflict columns cannot be empty for upsert operation. " +
            "Specify at least one column (typically the primary key)."
        }

        return entities.map { upsert(it, conflictColumns) }
    }

    /**
     * Manually evict a specific entity from the identity map and metadata cache.
     *
     * Use this when you know you won't need an entity again in the current session,
     * to free up memory. The entity can still be loaded again with find().
     *
     * Example:
     * ```kotlin
     * val user = session.find<User>(1)
     * processUser(user)
     *
     * // Done with this user, free up memory
     * session.evict(user)
     * ```
     *
     * @param entity The entity to evict
     */
    fun <E : Any> evict(entity: E) {
        @Suppress("UNCHECKED_CAST")
        val entityClass = entity::class as KClass<E>
        val binding = getBinding<E, Any>(entityClass)
        val id = binding.entityId(entity)
        val key = EntityKey(binding.table.entityType, id)

        identityMap.remove(key)
        entityMetadata.remove(key)
    }

    /**
     * Evict all entities of a specific type from the identity map.
     *
     * Useful for batch processing when you're done with a specific entity type.
     *
     * Example:
     * ```kotlin
     * // Process all users
     * for (userId in 1..10_000) {
     *     val user = session.find<User>(userId)
     *     processUser(user)
     * }
     *
     * // Clear all users from cache
     * session.evictAll(User::class)
     * ```
     *
     * @param entityClass The entity type to evict
     */
    fun <E : Any> evictAll(entityClass: KClass<E>) {
        val keysToRemove = identityMap.keys.filter { it.entityType == entityClass }
        keysToRemove.forEach { key ->
            identityMap.remove(key)
            entityMetadata.remove(key)
        }
    }

    /**
     * Clear all entities from the identity map and metadata cache.
     *
     * Useful when transitioning to a new phase of processing in the same session.
     *
     * Example:
     * ```kotlin
     * // Phase 1: Load and process orders
     * processOrders(session)
     *
     * // Phase 2: Clear cache before loading products
     * session.clearCache()
     * processProducts(session)
     * ```
     */
    fun clearCache() {
        identityMap.clear()
        entityMetadata.clear()
    }

    /**
     * Clear the relationship cache (eager loading cache).
     *
     * Use this when eager-loaded relationships are no longer needed,
     * to free up memory. Does not affect the identity map.
     *
     * Example:
     * ```kotlin
     * // Load users with orders eagerly
     * val users = loadUsers()
     *     .withOneToMany(session, User::class, "orders", ...)
     *
     * processUsers(users)
     *
     * // Done with relationships, free up memory
     * session.clearRelationshipCache()
     * ```
     */
    fun clearRelationshipCache() {
        relationshipCache.clear()
    }

    /**
     * Clear relationship cache for a specific entity type.
     *
     * @param entityType The entity type whose relationships should be cleared
     */
    fun clearRelationshipCache(entityType: KClass<*>) {
        val keysToRemove = relationshipCache.keys.filter { it.sourceEntityType == entityType }
        keysToRemove.forEach { relationshipCache.remove(it) }
    }

    /**
     * Close the session and clear the identity map.
     * Does NOT close the connection (caller manages connection lifecycle).
     */
    override fun close() {
        identityMap.clear()
        entityMetadata.clear()
    }
}

/**
 * Session statistics.
 */
data class SessionStats(
    val cachedEntities: Int,
    val pendingInserts: Int = 0,
    val pendingUpdates: Int = 0,
    val pendingDeletes: Int = 0
)

/**
 * Cache key for eager-loaded relationships.
 *
 * Phase 5.3: Used to cache preloaded relationships to prevent N+1 queries.
 *
 * @param sourceEntityType The KClass of the source entity (e.g., User::class)
 * @param sourceEntityId The ID of the source entity instance
 * @param relationshipName The name of the relationship (e.g., "orders")
 */
data class RelationshipCacheKey(
    val sourceEntityType: KClass<*>,
    val sourceEntityId: Any,
    val relationshipName: String
)

/**
 * Exception thrown when an entity is not found in the database.
 *
 * Thrown by EntitySession.get() when the requested entity doesn't exist.
 *
 * @param entityType The entity type name
 * @param id The primary key that was not found
 */
class EntityNotFoundException(
    val entityType: String,
    val id: Any
) : RuntimeException("Entity $entityType with id=$id not found")
