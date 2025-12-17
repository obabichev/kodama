package com.obabichev.kodama.entity

/**
 * Represents the lifecycle state of an entity within an EntitySession.
 *
 * State Transitions:
 * ```
 * NEW → PENDING_INSERT → MANAGED
 * MANAGED → PENDING_UPDATE → MANAGED
 * MANAGED → PENDING_DELETE → (removed from session)
 * ```
 *
 * Phase 1: Only MANAGED state used (for entities loaded from database)
 * Phase 2: NEW, PENDING_INSERT added (for creating new entities)
 * Phase 3: PENDING_UPDATE added (for modifying entities)
 * Phase 4: PENDING_DELETE added (for deleting entities)
 */
enum class EntityState {
    /**
     * Entity created in memory, not yet staged for INSERT.
     * Not tracked by session until save() is called.
     */
    NEW,

    /**
     * Entity staged for INSERT via save().
     * Will be inserted on next flush().
     */
    PENDING_INSERT,

    /**
     * Entity is synchronized with database.
     * Loaded via find() or freshly inserted after flush().
     */
    MANAGED,

    /**
     * Entity has been modified and staged for UPDATE.
     * Will be updated on next flush().
     * (Phase 3)
     */
    PENDING_UPDATE,

    /**
     * Entity has been marked for deletion via delete().
     * Will be deleted on next flush().
     * (Phase 4)
     */
    PENDING_DELETE
}

/**
 * Internal metadata about an entity tracked by the session.
 *
 * Stores:
 * - The entity instance
 * - Its current state
 * - Original snapshot (for change detection in Phase 3)
 * - Binding information
 */
internal data class EntityMetadata<E : Any, ID : Any>(
    /**
     * The entity instance.
     */
    val entity: E,

    /**
     * Current lifecycle state.
     */
    var state: EntityState,

    /**
     * The binding for this entity type.
     */
    val binding: EntityBinding<E, ID>,

    /**
     * Original snapshot of the entity (for change detection).
     * Null for NEW/PENDING_INSERT entities.
     * Used in Phase 3 for UPDATE support.
     */
    val snapshot: E? = null
)
