package com.obabichev.kodama.entity

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.EntityTable
import kotlin.reflect.KClass

/**
 * Eager loading API for preventing N+1 queries.
 *
 * Phase 5.3: Extension functions that enable batch loading of relationships.
 *
 * Example:
 * ```kotlin
 * // Without eager loading - N+1 queries (1 + 100)
 * val users = session.findAll<User>()
 * users.forEach { user ->
 *     user.orders(session)  // SELECT per user!
 * }
 *
 * // With eager loading - 2 queries total
 * val users = session.findAll<User>()
 *     .with(session, User::class, "orders", UserOrders, UserOrders.userId, Users.id)
 * users.forEach { user ->
 *     user.orders(session)  // Already cached!
 * }
 * ```
 */

/**
 * Alias for the source foreign key column in many-to-many join queries.
 * This column is added to SELECT to associate related entities with their source entities.
 */
private const val SOURCE_FK_ALIAS = "__source_fk__"

/**
 * Eagerly load a one-to-many relationship for a collection of entities.
 *
 * This method batch-loads related entities for all source entities in one query,
 * caching the results to prevent N+1 queries when the relationship is accessed.
 *
 * Example:
 * ```kotlin
 * val users: List<User> = session.findAll<User>()
 *     .withOneToMany(
 *         session = session,
 *         sourceEntityType = User::class,
 *         relationshipName = "orders",
 *         targetTable = UserOrders,
 *         foreignKeyColumn = UserOrders.userId,
 *         sourceIdExtractor = { it.id }
 *     )
 *
 * users.forEach { user ->
 *     user.orders(session)  // Returns cached results, no query
 * }
 * ```
 *
 * @param E Source entity type (e.g., User)
 * @param R Related entity type (e.g., UserOrder)
 * @param ID Type of the related entity's primary key
 * @param FK Foreign key type
 * @param session EntitySession for batch loading
 * @param sourceEntityType KClass of the source entity
 * @param relationshipName Name of the relationship (e.g., "orders")
 * @param targetTable Target EntityTable
 * @param foreignKeyColumn Foreign key column in target table
 * @param sourceIdExtractor Function to extract source entity ID
 * @return The same list (for chaining)
 */
fun <E : Any, R : Any, ID : Any, FK : Any> List<E>.withOneToMany(
    session: EntitySession,
    sourceEntityType: KClass<E>,
    relationshipName: String,
    targetTable: EntityTable<R>,
    foreignKeyColumn: Column<FK>,
    sourceIdExtractor: (E) -> FK
): List<E> {
    // Early return for empty list
    if (this.isEmpty()) {
        return this
    }

    // Extract all source entity IDs
    val sourceIds = this.map(sourceIdExtractor)

    // Batch load related entities
    val relatedBySourceId = session.findByForeignKeyBatch<R, ID, FK>(
        targetTable = targetTable,
        foreignKeyColumn = foreignKeyColumn,
        foreignKeyValues = sourceIds
    )

    // Cache the results (cast map keys to Any for the generic cache)
    @Suppress("UNCHECKED_CAST")
    session.cacheRelationships(
        sourceEntityType = sourceEntityType,
        relationshipName = relationshipName,
        relationshipsBySourceId = relatedBySourceId as Map<Any, List<R>>
    )

    return this
}

/**
 * Eagerly load a many-to-many relationship for a collection of entities.
 *
 * This method batch-loads related entities through a junction table,
 * caching the results to prevent N+1 queries.
 *
 * Example:
 * ```kotlin
 * val users: List<User> = session.findAll<User>()
 *     .withManyToMany(
 *         session = session,
 *         sourceEntityType = User::class,
 *         relationshipName = "roles",
 *         targetTable = Roles,
 *         junctionTable = UserRoles,
 *         sourceForeignKeyColumn = UserRoles.userId,
 *         targetForeignKeyColumn = UserRoles.roleId,
 *         targetPrimaryKeyColumn = Roles.id,
 *         sourceIdExtractor = { it.id }
 *     )
 *
 * users.forEach { user ->
 *     user.roles(session)  // Returns cached results, no query
 * }
 * ```
 *
 * @param E Source entity type (e.g., User)
 * @param R Related entity type (e.g., Role)
 * @param ID Type of the related entity's primary key
 * @param SourceFK Source foreign key type
 * @param TargetFK Target foreign key type
 * @param session EntitySession for batch loading
 * @param sourceEntityType KClass of the source entity
 * @param relationshipName Name of the relationship (e.g., "roles")
 * @param targetTable Target EntityTable
 * @param junctionTable Junction EntityTable
 * @param sourceForeignKeyColumn Column in junction table pointing to source
 * @param targetForeignKeyColumn Column in junction table pointing to target
 * @param targetPrimaryKeyColumn Primary key column in target table
 * @param sourceIdExtractor Function to extract source entity ID
 * @return The same list (for chaining)
 */
fun <E : Any, R : Any, ID : Any, SourceFK : Any, TargetFK : Any> List<E>.withManyToMany(
    session: EntitySession,
    sourceEntityType: KClass<E>,
    relationshipName: String,
    targetTable: EntityTable<R>,
    junctionTable: EntityTable<*>,
    sourceForeignKeyColumn: Column<SourceFK>,
    targetForeignKeyColumn: Column<TargetFK>,
    targetPrimaryKeyColumn: Column<TargetFK>,
    sourceIdExtractor: (E) -> SourceFK
): List<E> {
    // Early return for empty list
    if (this.isEmpty()) {
        return this
    }

    // Extract all source entity IDs
    val sourceIds = this.map(sourceIdExtractor)

    // Build a query to batch load all related entities through the junction table
    // SQL: SELECT target.*, junction.source_fk FROM target
    //      INNER JOIN junction ON target.pk = junction.target_fk
    //      WHERE junction.source_fk IN (?, ?, ...)
    val binding = session.getBinding<R, ID>(targetTable.entityType)

    val placeholders = sourceIds.joinToString(", ") { "?" }
    val sql = """
        SELECT target.*, junction.${sourceForeignKeyColumn.name} as $SOURCE_FK_ALIAS
        FROM "${targetTable.tableName}" target
        INNER JOIN "${junctionTable.tableName}" junction
        ON target.${targetPrimaryKeyColumn.name} = junction.${targetForeignKeyColumn.name}
        WHERE junction.${sourceForeignKeyColumn.name} IN ($placeholders)
    """.trimIndent()

    // Initialize result map with empty lists for all source IDs
    val relatedBySourceId = sourceIds.associateWith { mutableListOf<R>() }.toMutableMap()

    session.connection.prepareStatement(sql).use { stmt ->
        // Set parameters
        sourceIds.forEachIndexed { index, sourceId ->
            stmt.setObject(index + 1, sourceId)
        }

        // Log query
        println("[Kodama] SQL (batch many-to-many): $sql | params: $sourceIds")

        stmt.executeQuery().use { rs ->
            while (rs.next()) {
                val entity = binding.toEntity(rs)
                val id = binding.entityId(entity)
                val key = EntityKey(targetTable.entityType, id)

                // Check identity map first
                val cachedEntity = session.identityMap.get<R>(key)
                val finalEntity = cachedEntity ?: entity

                // If not cached, add to identity map
                if (cachedEntity == null) {
                    session.identityMap.put(key, entity)
                    // Note: We can't access entityMetadata from here, but the entity is in the identity map
                }

                // Extract the source foreign key from the result
                @Suppress("UNCHECKED_CAST")
                val sourceFk = rs.getObject(SOURCE_FK_ALIAS) as SourceFK
                relatedBySourceId[sourceFk]?.add(finalEntity)
            }
        }
    }

    // Cache the results (cast map keys to Any for the generic cache)
    @Suppress("UNCHECKED_CAST")
    session.cacheRelationships(
        sourceEntityType = sourceEntityType,
        relationshipName = relationshipName,
        relationshipsBySourceId = relatedBySourceId as Map<Any, List<R>>
    )

    return this
}
