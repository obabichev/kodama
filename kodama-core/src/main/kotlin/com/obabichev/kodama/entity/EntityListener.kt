package com.obabichev.kodama.entity

/**
 * Entity lifecycle listener.
 *
 * Allows listening to entity lifecycle events for audit logging, validation,
 * computed fields, and other cross-cutting concerns.
 *
 * Example: Audit logging
 * ```kotlin
 * session.registerListener(User::class, object : EntityListener<User> {
 *     override fun onPreUpdate(entity: User, old: User, session: EntitySession) {
 *         auditLog.log("User ${entity.id} changed: ${old.email} → ${entity.email}")
 *     }
 * })
 * ```
 *
 * Example: Validation
 * ```kotlin
 * session.registerListener(User::class, object : EntityListener<User> {
 *     override fun onPrePersist(entity: User, session: EntitySession) {
 *         require(entity.email.contains("@")) { "Invalid email format" }
 *     }
 * })
 * ```
 *
 * Example: Computed fields
 * ```kotlin
 * session.registerListener(Article::class, object : EntityListener<Article> {
 *     override fun onPrePersist(entity: Article, session: EntitySession) {
 *         // Auto-set slug from title
 *         entity.slug = entity.title.toLowerCase().replace(" ", "-")
 *     }
 *
 *     override fun onPreUpdate(entity: Article, old: Article, session: EntitySession) {
 *         // Update modified timestamp
 *         entity.updatedAt = Instant.now()
 *     }
 * })
 * ```
 *
 * Thread safety: Listeners are called synchronously within the session's thread.
 * Not thread-safe for concurrent access.
 *
 * @param E Entity type this listener handles
 */
interface EntityListener<E : Any> {

    /**
     * Called before a new entity is inserted into the database.
     *
     * Use cases:
     * - Set default values (timestamps, UUIDs)
     * - Validate entity before persistence
     * - Trigger side effects (publish event)
     *
     * Throwing an exception from this method will prevent the INSERT
     * and cause flush() to fail.
     *
     * @param entity The entity about to be inserted
     * @param session The current entity session
     */
    fun onPrePersist(entity: E, session: EntitySession) {}

    /**
     * Called after an entity has been inserted into the database.
     *
     * Use cases:
     * - Audit logging (record creation)
     * - Publish domain events
     * - Update related entities
     *
     * @param entity The entity that was inserted
     * @param session The current entity session
     */
    fun onPostPersist(entity: E, session: EntitySession) {}

    /**
     * Called before an existing entity is updated in the database.
     *
     * Use cases:
     * - Validate changes before persistence
     * - Update computed fields (e.g., "updatedAt" timestamp)
     * - Check permissions
     *
     * The `old` parameter contains the original entity state (snapshot)
     * before modifications. Use it to detect which fields changed.
     *
     * Throwing an exception from this method will prevent the UPDATE
     * and cause flush() to fail.
     *
     * @param entity The entity about to be updated (with new values)
     * @param old The original entity state (before modifications)
     * @param session The current entity session
     */
    fun onPreUpdate(entity: E, old: E, session: EntitySession) {}

    /**
     * Called after an entity has been updated in the database.
     *
     * Use cases:
     * - Audit logging (record modifications)
     * - Publish domain events
     * - Invalidate caches
     *
     * @param entity The entity that was updated
     * @param old The original entity state (before modifications)
     * @param session The current entity session
     */
    fun onPostUpdate(entity: E, old: E, session: EntitySession) {}

    /**
     * Called before an entity is deleted from the database.
     *
     * Use cases:
     * - Validate deletion is allowed
     * - Check referential integrity
     * - Archive entity before deletion
     *
     * Throwing an exception from this method will prevent the DELETE
     * and cause flush() to fail.
     *
     * @param entity The entity about to be deleted
     * @param session The current entity session
     */
    fun onPreDelete(entity: E, session: EntitySession) {}

    /**
     * Called after an entity has been deleted from the database.
     *
     * Use cases:
     * - Audit logging (record deletion)
     * - Clean up related resources
     * - Publish domain events
     *
     * Note: The entity is still available in this callback, but has been
     * removed from the database and the identity map.
     *
     * @param entity The entity that was deleted
     * @param session The current entity session
     */
    fun onPostDelete(entity: E, session: EntitySession) {}

    /**
     * Called after an entity has been loaded from the database.
     *
     * Use cases:
     * - Initialize computed fields
     * - Apply access control (mask sensitive data)
     * - Track access for auditing
     *
     * This is called for:
     * - find() operations
     * - get() operations
     * - Relationship loading (findByForeignKey)
     *
     * @param entity The entity that was loaded
     * @param session The current entity session
     */
    fun onPostLoad(entity: E, session: EntitySession) {}
}

/**
 * Sealed interface representing entity lifecycle events.
 *
 * These events can be used for event-driven architectures,
 * domain event publishing, or reactive systems.
 *
 * Example: Event-driven audit logging
 * ```kotlin
 * val auditLog = mutableListOf<EntityEvent<*>>()
 *
 * session.registerListener(User::class, object : EntityListener<User> {
 *     override fun onPostUpdate(entity: User, old: User, session: EntitySession) {
 *         auditLog.add(EntityEvent.Updated(entity, old, session))
 *     }
 * })
 * ```
 */
sealed interface EntityEvent<E : Any> {
    /**
     * The entity involved in this event.
     */
    val entity: E

    /**
     * The session in which this event occurred.
     */
    val session: EntitySession

    /**
     * Entity was created (inserted into database).
     */
    data class Created<E : Any>(
        override val entity: E,
        override val session: EntitySession
    ) : EntityEvent<E>

    /**
     * Entity was updated (modified in database).
     *
     * @property old The original entity state before modification
     */
    data class Updated<E : Any>(
        override val entity: E,
        val old: E,
        override val session: EntitySession
    ) : EntityEvent<E>

    /**
     * Entity was deleted (removed from database).
     */
    data class Deleted<E : Any>(
        override val entity: E,
        override val session: EntitySession
    ) : EntityEvent<E>

    /**
     * Entity was loaded from database.
     */
    data class Loaded<E : Any>(
        override val entity: E,
        override val session: EntitySession
    ) : EntityEvent<E>
}
