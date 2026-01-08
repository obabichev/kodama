package com.obabichev.kodama.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.obabichev.kodama.ksp.model.RelationshipModel
import com.obabichev.kodama.ksp.model.RelationshipType

/**
 * Extracts relationship declarations from Table objects.
 *
 * Looks for properties initialized with:
 * - oneToMany(targetTable, foreignKey, primaryKey)
 * - manyToOne(targetTable, foreignKey, primaryKey)
 */
class RelationshipExtractor(private val logger: KSPLogger) {

    /**
     * Extract all relationships declared in a Table object.
     *
     * Example:
     * ```kotlin
     * object Person : Table("person") {
     *     val name = varchar("name", 255).primaryKey()
     *     val orders = oneToMany(Order, Order.userName, this.name)
     *                  ^^^^^^^   ^^^^^  ^^^^^^^^^^^^^^^^^^^^^^^
     *                  property  func   arguments
     * }
     * ```
     */
    fun extractRelationships(tableDeclaration: KSClassDeclaration): List<RelationshipModel> {
        val sourceTableName = tableDeclaration.simpleName.asString()
        val relationships = mutableListOf<RelationshipModel>()

        // Get all property declarations
        tableDeclaration.declarations
            .filterIsInstance<KSPropertyDeclaration>()
            .forEach { property ->
                // Check if property is initialized with oneToMany or manyToOne
                val relationship = extractRelationshipFromProperty(sourceTableName, property)
                if (relationship != null) {
                    relationships.add(relationship)
                    logger.info("Kodama KSP: Found relationship: $sourceTableName -> ${relationship.targetTable} (${relationship.relationshipType})")
                }
            }

        return relationships
    }

    /**
     * Extract relationship information from a property declaration.
     *
     * We need to find properties whose initializer is a call to:
     * - oneToMany(...)
     * - manyToOne(...)
     */
    private fun extractRelationshipFromProperty(
        sourceTableName: String,
        property: KSPropertyDeclaration
    ): RelationshipModel? {
        // Get the property's initializer expression
        val initializer = property.getter?.let { getter ->
            // For properties without custom getter, KSP doesn't expose initializer directly
            // We need to look at the declaration itself
            null
        }

        // Alternative: Look for the type of the property
        // If it's OneToMany or ManyToOne, we know it's a relationship
        val propertyType = property.type.resolve()
        val typeDeclaration = propertyType.declaration
        val typeName = typeDeclaration.simpleName.asString()

        return when (typeName) {
            "OneToMany" -> {
                extractOneToManyRelationship(sourceTableName, property, propertyType)
            }
            "ManyToOne" -> {
                extractManyToOneRelationship(sourceTableName, property, propertyType)
            }
            else -> null
        }
    }

    /**
     * Extract OneToMany relationship details from property type arguments.
     *
     * OneToMany<Target : Table, FK, PK>
     * Type arguments: [Target, FK, PK]
     */
    private fun extractOneToManyRelationship(
        sourceTableName: String,
        property: KSPropertyDeclaration,
        propertyType: KSType
    ): RelationshipModel? {
        val typeArguments = propertyType.arguments
        if (typeArguments.size < 3) {
            logger.warn("Kodama KSP: OneToMany relationship has fewer than 3 type arguments")
            return null
        }

        // First type argument is the target table
        val targetTableType = typeArguments[0].type?.resolve()
        val targetTableName = targetTableType?.declaration?.simpleName?.asString()

        if (targetTableName == null) {
            logger.warn("Kodama KSP: Could not determine target table for OneToMany relationship in $sourceTableName.${property.simpleName.asString()}")
            return null
        }

        // For now, we'll generate the relationship without FK/PK details
        // These will be inferred from the actual relationship registry at runtime
        return RelationshipModel(
            sourceTable = sourceTableName,
            targetTable = targetTableName,
            relationshipType = RelationshipType.ONE_TO_MANY,
            foreignKeyColumn = "?",  // Will be resolved at runtime
            primaryKeyColumn = "?"   // Will be resolved at runtime
        )
    }

    /**
     * Extract ManyToOne relationship details from property type arguments.
     *
     * ManyToOne<Target : Table, FK, PK>
     * Type arguments: [Target, FK, PK]
     */
    private fun extractManyToOneRelationship(
        sourceTableName: String,
        property: KSPropertyDeclaration,
        propertyType: KSType
    ): RelationshipModel? {
        val typeArguments = propertyType.arguments
        if (typeArguments.size < 3) {
            logger.warn("Kodama KSP: ManyToOne relationship has fewer than 3 type arguments")
            return null
        }

        // First type argument is the target table
        val targetTableType = typeArguments[0].type?.resolve()
        val targetTableName = targetTableType?.declaration?.simpleName?.asString()

        if (targetTableName == null) {
            logger.warn("Kodama KSP: Could not determine target table for ManyToOne relationship in $sourceTableName.${property.simpleName.asString()}")
            return null
        }

        return RelationshipModel(
            sourceTable = sourceTableName,
            targetTable = targetTableName,
            relationshipType = RelationshipType.MANY_TO_ONE,
            foreignKeyColumn = "?",  // Will be resolved at runtime
            primaryKeyColumn = "?"   // Will be resolved at runtime
        )
    }
}
