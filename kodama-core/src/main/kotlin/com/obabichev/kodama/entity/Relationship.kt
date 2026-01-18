package com.obabichev.kodama.entity

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.EntityTable

/**
 * Base interface for all relationship types.
 * Represents metadata about relationships between entities.
 */
sealed interface Relationship

/**
 * One-to-many relationship metadata.
 *
 * Example: Users (1) → UserOrders (N)
 * One user has many orders.
 *
 * @param E Entity type of the "many" side (e.g., UserOrder)
 * @param FK Foreign key type (e.g., Int) - must match the primary key type
 * @param name Relationship property name (e.g., "orders")
 * @param targetTable The "many" side EntityTable (e.g., UserOrders)
 * @param foreignKeyColumn Column in target table pointing back to source (e.g., UserOrders.userId)
 * @param primaryKeyColumn Column in source table being referenced (e.g., Users.id)
 */
data class OneToManyRelationship<E : Any, FK : Any>(
    val name: String,
    val targetTable: EntityTable<E>,
    val foreignKeyColumn: Column<FK>,
    val primaryKeyColumn: Column<FK>
) : Relationship {

    init {
        // Validate that FK column belongs to target table
        require(foreignKeyColumn.relation == targetTable.relation) {
            "Foreign key column ${foreignKeyColumn.name} must belong to target table ${targetTable.tableName}, " +
            "but belongs to ${foreignKeyColumn.relation.name}"
        }
    }
}

/**
 * Many-to-one relationship metadata.
 *
 * Example: UserOrders (N) → Users (1)
 * Many orders belong to one user.
 *
 * @param E Entity type of the "one" side (e.g., User)
 * @param FK Foreign key type (e.g., Int) - must match the primary key type
 * @param name Relationship property name (e.g., "user")
 * @param targetTable The "one" side EntityTable (e.g., Users)
 * @param foreignKeyColumn Column in source table pointing to target (e.g., UserOrders.userId)
 * @param primaryKeyColumn Column in target table being referenced (e.g., Users.id)
 */
data class ManyToOneRelationship<E : Any, FK : Any>(
    val name: String,
    val targetTable: EntityTable<E>,
    val foreignKeyColumn: Column<FK>,
    val primaryKeyColumn: Column<FK>
) : Relationship {

    init {
        // Validate that PK column belongs to target table
        require(primaryKeyColumn.relation == targetTable.relation) {
            "Primary key column ${primaryKeyColumn.name} must belong to target table ${targetTable.tableName}, " +
            "but belongs to ${primaryKeyColumn.relation.name}"
        }
    }
}

/**
 * Many-to-many relationship metadata.
 *
 * Example: Users (N) ← UserRoles → Roles (M)
 * Users can have many roles, and roles can be assigned to many users.
 *
 * A many-to-many relationship requires a junction table that holds foreign keys
 * to both sides of the relationship.
 *
 * @param E Entity type of the target side (e.g., Role)
 * @param SourceFK Foreign key type pointing to source (e.g., Int for userId)
 * @param TargetFK Foreign key type pointing to target (e.g., Int for roleId)
 * @param name Relationship property name (e.g., "roles")
 * @param targetTable The target side EntityTable (e.g., Roles)
 * @param junctionTable The junction table that links both entities (e.g., UserRoles)
 * @param sourceForeignKeyColumn Column in junction table pointing to source (e.g., UserRoles.userId)
 * @param targetForeignKeyColumn Column in junction table pointing to target (e.g., UserRoles.roleId)
 * @param sourcePrimaryKeyColumn Column in source table (e.g., Users.id)
 * @param targetPrimaryKeyColumn Column in target table (e.g., Roles.id)
 */
data class ManyToManyRelationship<E : Any, SourceFK : Any, TargetFK : Any>(
    val name: String,
    val targetTable: EntityTable<E>,
    val junctionTable: EntityTable<*>,
    val sourceForeignKeyColumn: Column<SourceFK>,
    val targetForeignKeyColumn: Column<TargetFK>,
    val sourcePrimaryKeyColumn: Column<SourceFK>,
    val targetPrimaryKeyColumn: Column<TargetFK>
) : Relationship {

    init {
        // Validate that junction table columns belong to junction table
        require(sourceForeignKeyColumn.relation == junctionTable.relation) {
            "Source foreign key column ${sourceForeignKeyColumn.name} must belong to junction table ${junctionTable.tableName}, " +
            "but belongs to ${sourceForeignKeyColumn.relation.name}"
        }
        require(targetForeignKeyColumn.relation == junctionTable.relation) {
            "Target foreign key column ${targetForeignKeyColumn.name} must belong to junction table ${junctionTable.tableName}, " +
            "but belongs to ${targetForeignKeyColumn.relation.name}"
        }

        // Validate that primary key columns belong to their respective tables
        require(targetPrimaryKeyColumn.relation == targetTable.relation) {
            "Target primary key column ${targetPrimaryKeyColumn.name} must belong to target table ${targetTable.tableName}, " +
            "but belongs to ${targetPrimaryKeyColumn.relation.name}"
        }
    }
}
