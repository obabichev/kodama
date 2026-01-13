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
 * - Future: Save, update, delete operations
 * - Future: Change tracking and flush
 *
 * Lifecycle:
 * - Create per transaction (transaction-scoped)
 * - Use within try-with-resources or use block
 * - Close after commit/rollback
 *
 * Thread safety: NOT thread-safe. One session per thread.
 *
 * Phase 1 Example:
 * ```kotlin
 * EntitySession(connection).use { session ->
 *     // Register binding
 *     session.registerBinding(Users, UserEntityBinding)
 *
 *     // Find user
 *     val user = session.find(Users, 1)
 *     println(user?.name)
 *
 *     // Find again - returns cached instance
 *     val sameUser = session.find(Users, 1)
 *     assert(user === sameUser)
 * }
 * ```
 */
class EntitySession(
    val connection: Connection
) : AutoCloseable {

    companion object {
        /**
         * Phase 5: Auto-binding provider set by generated KodamaBindingRegistry.
         * This allows EntitySession to automatically look up bindings without manual registration.
         */
        @JvmStatic
        var autoBindingProvider: ((KClass<*>) -> EntityBinding<*, *>?)? = null
    }

    private val identityMap = IdentityMap()

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
     * Get the binding for an entity type.
     *
     * Phase 5: Automatically consults KodamaBindingRegistry if binding not manually registered.
     * Throws error if binding not found anywhere.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <E : Any, ID : Any> getBinding(entityClass: KClass<E>): EntityBinding<E, ID> {
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
        entityMetadata[key] = EntityMetadata(
            entity = entity,
            state = EntityState.MANAGED,
            binding = binding,
            snapshot = entity  // Keep snapshot for future change detection (Phase 3)
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
                            snapshot = entity
                        )
                        results.add(entity)
                    }
                }
            }
        }

        return results
    }

    /**
     * Load an entity from the database.
     * Builds and executes a SELECT query by primary key.
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
                    return binding.toEntity(rs)
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
                if (snapshot != null && snapshot != entity) {
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
                    // No snapshot (PENDING_INSERT) or entity unchanged
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
     * Phase 2: Executes INSERT statements for PENDING_INSERT entities
     * Phase 3: Executes UPDATE statements for PENDING_UPDATE entities
     * Phase 4: Executes DELETE statements for PENDING_DELETE entities
     *
     * After flush:
     * - PENDING_INSERT → MANAGED (entities now synchronized with database)
     * - PENDING_UPDATE → MANAGED (changes persisted to database)
     * - PENDING_DELETE → removed from session (deleted from database)
     * - Auto-generated IDs are updated in entities
     * - Snapshots are created/updated for change tracking
     *
     * Important: flush() should be called before transaction commit.
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
    }

    /**
     * Execute INSERT for a single entity.
     *
     * Generates and executes INSERT statement using the binding's toInsertValues().
     * Handles auto-generated IDs (Phase 2 basic support, can be enhanced).
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
