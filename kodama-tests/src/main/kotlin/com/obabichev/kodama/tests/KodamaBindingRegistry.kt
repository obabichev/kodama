package com.obabichev.kodama.tests

import com.obabichev.kodama.entity.EntityBinding
import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.UserOrder
import com.obabichev.kodama.tests.entity.impl.UserEntityBinding
import com.obabichev.kodama.tests.entity.impl.UserOrderEntityBinding
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses

/**
 * Auto-generated binding registry for Kodama entity layer.
 *
 * This object is automatically initialized when tests run, setting up
 * the EntitySession.autoBindingProvider to enable automatic entity binding lookup.
 *
 * Generated bindings:
 * - User -> UserEntityBinding
 * - UserOrder -> UserOrderEntityBinding
 */
object KodamaBindingRegistry {

    private val bindings: Map<KClass<*>, EntityBinding<*, *>> = mapOf(
        User::class to UserEntityBinding,
        UserOrder::class to UserOrderEntityBinding
    )

    init {
        // Set up auto-binding provider for EntitySession
        EntitySession.autoBindingProvider = { entityClass ->
            // First try direct lookup
            bindings[entityClass] ?: run {
                // If not found, look for binding by interface
                // This handles case where entity::class is the implementation (UserImpl)
                // but binding is registered for the interface (User)
                bindings.entries.firstOrNull { (interfaceClass, _) ->
                    interfaceClass.java.isAssignableFrom(entityClass.java)
                }?.value
            }
        }
    }

    /**
     * Get binding for an entity class.
     * Used by EntitySession for automatic binding lookup.
     */
    fun getBinding(entityClass: KClass<*>): EntityBinding<*, *>? {
        return bindings[entityClass] ?: run {
            // Try to find by interface if direct lookup fails
            bindings.entries.firstOrNull { (interfaceClass, _) ->
                interfaceClass.java.isAssignableFrom(entityClass.java)
            }?.value
        }
    }
}
