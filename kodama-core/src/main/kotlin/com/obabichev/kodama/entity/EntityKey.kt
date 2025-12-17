package com.obabichev.kodama.entity

import kotlin.reflect.KClass

/**
 * Unique key for an entity in the identity map.
 * Combines entity type and primary key value to ensure uniqueness across all entity types.
 *
 * Two entities are considered the same if they have:
 * - Same entity type (KClass)
 * - Same primary key value
 *
 * Example:
 * ```kotlin
 * val key1 = EntityKey(User::class, 1)
 * val key2 = EntityKey(User::class, 1)
 * assert(key1 == key2)  // Same entity
 *
 * val key3 = EntityKey(Order::class, 1)
 * assert(key1 != key3)  // Different entity types
 * ```
 */
data class EntityKey(
    val entityType: KClass<*>,
    val id: Any
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntityKey) return false
        return entityType == other.entityType && id == other.id
    }

    override fun hashCode(): Int {
        return 31 * entityType.hashCode() + id.hashCode()
    }

    override fun toString(): String {
        return "${entityType.simpleName}(id=$id)"
    }
}
