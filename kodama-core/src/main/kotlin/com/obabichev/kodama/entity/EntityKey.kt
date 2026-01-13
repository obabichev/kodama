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
 * ⚠️ **IMPORTANT: ID Type Consistency**
 *
 * The `id` parameter must have a consistent type for each entity class.
 * Different numeric types (Int vs Long vs Short) are treated as different keys
 * even if they represent the same value.
 *
 * **Correct Usage:**
 * ```kotlin
 * val key1 = EntityKey(User::class, 1)   // Int
 * val key2 = EntityKey(User::class, 1)   // Int
 * assert(key1 == key2)  // ✅ Same key
 * ```
 *
 * **Incorrect Usage:**
 * ```kotlin
 * val key1 = EntityKey(User::class, 1)   // Int
 * val key2 = EntityKey(User::class, 1L)  // Long
 * assert(key1 != key2)  // ❌ Different keys (hash codes differ)
 * ```
 *
 * @throws IllegalArgumentException if id is null or Unit
 */
data class EntityKey(
    val entityType: KClass<*>,
    val id: Any
) {
    init {
        // Validate that ID is not null (Kotlin's Any should not be null, but we check Unit as a proxy)
        // Unit is often used as a placeholder for null in generic contexts
        require(id !is Unit) {
            "Entity ID cannot be Unit (null) for ${entityType.simpleName}. " +
            "Check your EntityBinding.entityId() implementation."
        }
    }
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
