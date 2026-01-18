package com.obabichev.kodama.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.obabichev.kodama.ksp.model.EntityInterfaceModel
import com.obabichev.kodama.ksp.model.EntityPropertyModel
import com.obabichev.kodama.ksp.model.KspTableModel
import com.obabichev.kodama.ksp.model.RuntimeMetadataRoot

/**
 * Discovers entity interfaces and matches them with their EntityTable definitions.
 *
 * An entity interface is:
 * 1. An interface (not class or object)
 * 2. Has a corresponding EntityTable<E> object declaration
 * 3. Declares properties (val) that map to table columns
 */
class EntityInterfaceDiscoverer(
    private val logger: KSPLogger,
    private val runtimeMetadata: RuntimeMetadataRoot?
) {

    /**
     * Discover all entity interfaces that have corresponding EntityTable declarations.
     */
    fun discoverEntityInterfaces(
        resolver: Resolver,
        discoveredTables: List<KspTableModel>
    ): List<EntityInterfaceModel> {
        val entityInterfaces = mutableListOf<EntityInterfaceModel>()

        // Build map of EntityTable objects to their entity type
        val tableToEntityType = buildTableToEntityTypeMap(discoveredTables, resolver)

        logger.info("Kodama KSP: Found ${tableToEntityType.size} EntityTable declarations")

        // For each EntityTable, find the corresponding interface
        tableToEntityType.forEach { (tableName, entityTypeName) ->
            try {
                val entityInterface = findEntityInterface(resolver, entityTypeName, tableName, discoveredTables)
                if (entityInterface != null) {
                    entityInterfaces.add(entityInterface)
                    logger.info("Kodama KSP: Discovered entity interface: $entityTypeName")
                }
            } catch (e: Exception) {
                logger.error("Kodama KSP: Failed to process entity $entityTypeName: ${e.message}")
            }
        }

        return entityInterfaces
    }

    /**
     * Build a map from EntityTable object name to entity type name.
     *
     * For example: "Portfolios" -> "Portfolio"
     */
    private fun buildTableToEntityTypeMap(
        tables: List<KspTableModel>,
        resolver: Resolver
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()

        tables.forEach { table ->
            // Find the table's KSClassDeclaration
            val tableClass = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString(table.qualifiedName)
            )

            if (tableClass != null) {
                // Check if it extends EntityTable<E>
                val entityType = extractEntityTypeParameter(tableClass)
                if (entityType != null) {
                    map[table.name] = entityType
                }
            }
        }

        return map
    }

    /**
     * Extract the entity type parameter E from EntityTable<E>.
     */
    private fun extractEntityTypeParameter(tableClass: KSClassDeclaration): String? {
        return tableClass.superTypes
            .map { it.resolve() }
            .firstOrNull { superType ->
                val qualifiedName = superType.declaration.qualifiedName?.asString()
                qualifiedName == "com.obabichev.kodama.schema.EntityTable"
            }
            ?.arguments
            ?.firstOrNull()
            ?.type
            ?.resolve()
            ?.declaration
            ?.qualifiedName
            ?.asString()
    }

    /**
     * Find the entity interface declaration and extract its properties.
     */
    private fun findEntityInterface(
        resolver: Resolver,
        entityTypeName: String,
        tableObjectName: String,
        discoveredTables: List<KspTableModel>
    ): EntityInterfaceModel? {
        // Find the interface declaration
        val interfaceDecl = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(entityTypeName)
        ) ?: return null

        // Must be an interface
        if (interfaceDecl.classKind != ClassKind.INTERFACE) {
            logger.warn("Kodama KSP: $entityTypeName is not an interface, skipping entity generation")
            return null
        }

        // Find the corresponding table model
        val tableModel = discoveredTables.firstOrNull { it.name == tableObjectName }
        if (tableModel == null) {
            logger.warn("Kodama KSP: Could not find table $tableObjectName for entity $entityTypeName")
            return null
        }

        // Extract properties from the interface
        val properties = extractProperties(interfaceDecl, tableModel)
        if (properties.isEmpty()) {
            logger.warn("Kodama KSP: Entity $entityTypeName has no properties, skipping")
            return null
        }

        // Extract relationship methods from the interface, enriched with table relationship data
        val relationshipMethods = extractRelationshipMethods(interfaceDecl, resolver, tableModel.qualifiedName)

        // Extract necessary imports
        val propertyImports = extractPropertyImports(properties, interfaceDecl)

        return EntityInterfaceModel(
            simpleName = interfaceDecl.simpleName.asString(),
            qualifiedName = entityTypeName,
            packageName = interfaceDecl.packageName.asString(),
            properties = properties,
            propertyImports = propertyImports,
            tableObjectName = tableObjectName,
            tableQualifiedName = tableModel.qualifiedName,
            relationshipMethods = relationshipMethods,
            containingFile = interfaceDecl.containingFile!!
        )
    }

    /**
     * Extract properties from the interface and match them with table columns.
     */
    private fun extractProperties(
        interfaceDecl: KSClassDeclaration,
        tableModel: KspTableModel
    ): List<EntityPropertyModel> {
        val properties = mutableListOf<EntityPropertyModel>()

        // Get runtime table metadata for SQL column names
        val runtimeTable = runtimeMetadata?.tables?.firstOrNull { it.qualifiedName == tableModel.qualifiedName }

        // Get all properties declared in the interface
        interfaceDecl.getAllProperties().forEach { prop ->
            if (prop.extensionReceiver != null) {
                // Skip extension properties
                return@forEach
            }

            val propName = prop.simpleName.asString()
            val typeName = prop.type.resolve().declaration.qualifiedName?.asString() ?: "Any"

            // Find matching column in table model
            val column = tableModel.columns.firstOrNull { col ->
                // Match by property name (exact match or snake_case conversion)
                col.propertyName == propName ||
                col.columnName == propName ||
                col.columnName == propName.toSnakeCase()
            }

            if (column != null) {
                // Get SQL column name from runtime metadata (if available)
                val sqlColumnName = runtimeTable?.columns?.firstOrNull { runtimeCol ->
                    runtimeCol.propertyName == propName
                }?.sqlColumnName ?: column.columnName

                properties.add(
                    EntityPropertyModel(
                        name = propName,
                        typeName = formatTypeName(typeName, column.isNullable),
                        tablePropertyName = column.propertyName,  // Property name in Table object
                        columnName = sqlColumnName,  // SQL column name for ResultSet
                        isPrimaryKey = column.isPrimaryKey,
                        isAutoGenerated = column.isAutoGenerated,
                        isNullable = column.isNullable
                    )
                )
            } else {
                logger.warn("Kodama KSP: Property $propName in ${interfaceDecl.simpleName.asString()} has no matching table column")
            }
        }

        return properties
    }

    /**
     * Format type name for generated code (remove package prefixes for common types).
     */
    private fun formatTypeName(qualifiedName: String, isNullable: Boolean): String {
        val baseName = when (qualifiedName) {
            "kotlin.Int" -> "Int"
            "kotlin.Long" -> "Long"
            "kotlin.Short" -> "Short"
            "kotlin.String" -> "String"
            "kotlin.Boolean" -> "Boolean"
            "kotlin.Double" -> "Double"
            "kotlin.Float" -> "Float"
            "java.math.BigDecimal" -> "BigDecimal"
            "java.time.LocalDate" -> "LocalDate"
            "java.time.LocalTime" -> "LocalTime"
            "java.time.LocalDateTime" -> "LocalDateTime"
            "java.time.OffsetDateTime" -> "OffsetDateTime"
            "java.time.OffsetTime" -> "OffsetTime"
            "java.time.Duration" -> "Duration"
            else -> qualifiedName.substringAfterLast('.')
        }

        return if (isNullable) "$baseName?" else baseName
    }

    /**
     * Extract necessary imports for property types.
     * Now enhanced to discover custom types by re-examining the interface properties.
     */
    private fun extractPropertyImports(
        properties: List<EntityPropertyModel>,
        interfaceDecl: KSClassDeclaration
    ): List<String> {
        val imports = mutableSetOf<String>()

        // Map properties by name for lookup
        val propsByName = properties.associateBy { it.name }

        // Re-examine interface properties to get qualified names
        interfaceDecl.getAllProperties().forEach { ksProp ->
            if (ksProp.extensionReceiver != null) return@forEach

            val propName = ksProp.simpleName.asString()
            val entityProp = propsByName[propName] ?: return@forEach

            val baseType = entityProp.typeName.removeSuffix("?")

            // Get the qualified name from KSP
            val qualifiedName = ksProp.type.resolve().declaration.qualifiedName?.asString()

            if (qualifiedName != null) {
                // Check if it's a standard type that needs import
                when (baseType) {
                    "BigDecimal" -> imports.add("java.math.BigDecimal")
                    "LocalDate" -> imports.add("java.time.LocalDate")
                    "LocalTime" -> imports.add("java.time.LocalTime")
                    "LocalDateTime" -> imports.add("java.time.LocalDateTime")
                    "OffsetDateTime" -> imports.add("java.time.OffsetDateTime")
                    "OffsetTime" -> imports.add("java.time.OffsetTime")
                    "Duration" -> imports.add("java.time.Duration")
                    else -> {
                        // For custom types, add import if it's not a kotlin built-in type
                        if (!qualifiedName.startsWith("kotlin.")) {
                            imports.add(qualifiedName)
                        }
                    }
                }
            }
        }

        return imports.toList()
    }

    /**
     * Extract relationship methods from the interface and enrich with table relationship metadata.
     */
    private fun extractRelationshipMethods(
        interfaceDecl: KSClassDeclaration,
        resolver: Resolver,
        tableQualifiedName: String
    ): List<com.obabichev.kodama.ksp.model.RelationshipMethodModel> {
        val methods = mutableListOf<com.obabichev.kodama.ksp.model.RelationshipMethodModel>()

        // Get the EntityTable object to access its relationships
        val tableClass = resolver.getClassDeclarationByName(resolver.getKSNameFromString(tableQualifiedName))

        interfaceDecl.getAllFunctions().forEach { function ->
            // Check if function takes EntitySession parameter
            val hasSessionParam = function.parameters.any { param ->
                val paramType = param.type.resolve()
                paramType.declaration.qualifiedName?.asString() == "com.obabichev.kodama.entity.EntitySession"
            }

            if (hasSessionParam) {
                val functionName = function.simpleName.asString()
                val returnType = function.returnType?.resolve()
                val returnTypeName = returnType?.declaration?.qualifiedName?.asString()

                val imports = mutableListOf<String>()

                // Format return type and extract target entity type
                val (formattedReturnType, targetEntityType) = when {
                    returnTypeName == "kotlin.collections.List" -> {
                        val typeArg = returnType.arguments.firstOrNull()?.type?.resolve()
                        val elementType = typeArg?.declaration?.simpleName?.asString() ?: "Any"
                        val elementQualifiedName = typeArg?.declaration?.qualifiedName?.asString()

                        // Add import for element type
                        if (elementQualifiedName != null && !elementQualifiedName.startsWith("kotlin.")) {
                            imports.add(elementQualifiedName)
                        }

                        "List<$elementType>" to elementType
                    }
                    else -> {
                        val simpleType = returnType?.declaration?.simpleName?.asString() ?: "Unit"
                        val qualifiedType = returnType?.declaration?.qualifiedName?.asString()

                        // Add import for simple type
                        if (qualifiedType != null && !qualifiedType.startsWith("kotlin.") && qualifiedType != "kotlin.Unit") {
                            imports.add(qualifiedType)
                        }

                        simpleType to simpleType
                    }
                }

                // Extract relationship data from EntityTable
                val relationshipData = extractRelationshipData(tableClass, functionName, targetEntityType, resolver)

                methods.add(
                    com.obabichev.kodama.ksp.model.RelationshipMethodModel(
                        name = functionName,
                        returnType = formattedReturnType,
                        returnTypeImports = imports,
                        relationshipData = relationshipData
                    )
                )
            }
        }

        return methods
    }

    /**
     * Extract relationship metadata using heuristics based on entity structure.
     */
    private fun extractRelationshipData(
        tableClass: KSClassDeclaration?,
        relationshipName: String,
        targetEntityType: String,
        resolver: Resolver
    ): com.obabichev.kodama.ksp.model.RelationshipData? {
        if (tableClass == null) return null

        val sourceTableName = tableClass.simpleName.asString()
        val sourceEntityName = sourceTableName.removeSuffix("s") // Users -> User

        // Try to find the target table (usually {Entity}s)
        val targetTableName = "${targetEntityType}s"  // User -> Users, UserOrder -> UserOrders
        val sourcePackage = tableClass.packageName.asString()
        val targetTableQualifiedName = "$sourcePackage.$targetTableName"

        // Determine relationship type based on return type
        val isList = relationshipName.endsWith("s") || targetEntityType.endsWith("Order") || targetEntityType == "Role"

        return if (isList) {
            // Could be oneToMany or manyToMany
            // Check for junction table pattern: {Source}{Target} or {Target}{Source}
            // Try multiple naming conventions: UsersRoles, UserRoles, RolesUsers, RoleUsers
            val junctionTableName1 = "$sourceTableName$targetTableName"  // e.g., UsersRoles
            val junctionTableName2 = "$targetTableName$sourceTableName"  // e.g., RolesUsers
            val junctionTableName3 = "$sourceEntityName$targetTableName"  // e.g., UserRoles
            val junctionTableName4 = "${targetTableName.removeSuffix("s")}$sourceTableName"  // e.g., RoleUsers

            val junctionTableQualifiedName1 = "$sourcePackage.$junctionTableName1"
            val junctionTableQualifiedName2 = "$sourcePackage.$junctionTableName2"
            val junctionTableQualifiedName3 = "$sourcePackage.$junctionTableName3"
            val junctionTableQualifiedName4 = "$sourcePackage.$junctionTableName4"

            val junctionTable1 = resolver.getClassDeclarationByName(resolver.getKSNameFromString(junctionTableQualifiedName1))
            val junctionTable2 = resolver.getClassDeclarationByName(resolver.getKSNameFromString(junctionTableQualifiedName2))
            val junctionTable3 = resolver.getClassDeclarationByName(resolver.getKSNameFromString(junctionTableQualifiedName3))
            val junctionTable4 = resolver.getClassDeclarationByName(resolver.getKSNameFromString(junctionTableQualifiedName4))

            if (junctionTable1 != null || junctionTable2 != null || junctionTable3 != null || junctionTable4 != null) {
                // Many-to-many relationship
                val junctionTableName = when {
                    junctionTable1 != null -> junctionTableName1
                    junctionTable2 != null -> junctionTableName2
                    junctionTable3 != null -> junctionTableName3
                    else -> junctionTableName4
                }
                val junctionTableQualifiedName = when {
                    junctionTable1 != null -> junctionTableQualifiedName1
                    junctionTable2 != null -> junctionTableQualifiedName2
                    junctionTable3 != null -> junctionTableQualifiedName3
                    else -> junctionTableQualifiedName4
                }

                com.obabichev.kodama.ksp.model.ManyToManyData(
                    targetTableName = targetTableName,
                    targetTableQualifiedName = targetTableQualifiedName,
                    junctionTableName = junctionTableName,
                    junctionTableQualifiedName = junctionTableQualifiedName,
                    sourceForeignKeyColumn = "${sourceEntityName.replaceFirstChar { it.lowercase() }}Id",
                    targetForeignKeyColumn = "${targetEntityType.replaceFirstChar { it.lowercase() }}Id",
                    sourcePrimaryKeyColumn = "id",
                    targetPrimaryKeyColumn = "id"
                )
            } else {
                // One-to-many relationship
                com.obabichev.kodama.ksp.model.OneToManyData(
                    targetTableName = targetTableName,
                    targetTableQualifiedName = targetTableQualifiedName,
                    foreignKeyColumn = "${sourceEntityName.replaceFirstChar { it.lowercase() }}Id",
                    primaryKeyColumn = "id"
                )
            }
        } else {
            // Many-to-one relationship
            // Foreign key is usually {targetEntity}Id in the source entity
            com.obabichev.kodama.ksp.model.ManyToOneData(
                targetTableName = targetTableName,
                targetTableQualifiedName = targetTableQualifiedName,
                foreignKeyColumn = "${targetEntityType.replaceFirstChar { it.lowercase() }}Id",
                primaryKeyColumn = "id"
            )
        }
    }

    /**
     * Convert camelCase to snake_case.
     */
    private fun String.toSnakeCase(): String {
        return this.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }
}
