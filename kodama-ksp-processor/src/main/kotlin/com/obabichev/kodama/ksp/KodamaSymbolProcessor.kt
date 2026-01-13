package com.obabichev.kodama.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.obabichev.kodama.ksp.model.KspTableModel
import com.obabichev.kodama.ksp.model.MarkerInterfaceModel
import com.obabichev.kodama.ksp.model.TableWithRelationships
import com.obabichev.kodama.ksp.model.RuntimeMetadataRoot
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Main KSP processor for Kodama.
 * Discovers Table object declarations, extracts relationships, and generates code.
 */
class KodamaSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val discoveredTables = mutableListOf<KspTableModel>()
    private val discoveredMarkers = mutableListOf<MarkerInterfaceModel>()
    private val tablesWithRelationships = mutableListOf<TableWithRelationships>()
    private val relationshipExtractor = RelationshipExtractor(logger)
    private val canJoinGenerator = CanJoinGenerator(codeGenerator, logger)
    private lateinit var resolver: Resolver

    override fun process(resolver: Resolver): List<KSAnnotated> {
        this.resolver = resolver
        logger.info("Kodama KSP: Starting table discovery...")

        // Find all files in the source set
        resolver.getAllFiles().forEach { file ->
            file.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { isTableObject(it) }
                .forEach { tableClass ->
                    val table = extractTableInfo(tableClass)
                    discoveredTables.add(table)
                    logger.info("Kodama KSP: Discovered table: ${table.qualifiedName}")

                    // Also extract relationships from this table
                    val relationships = relationshipExtractor.extractRelationships(tableClass)
                    if (relationships.isNotEmpty()) {
                        tablesWithRelationships.add(
                            TableWithRelationships(table, relationships)
                        )
                        logger.info("Kodama KSP: Table ${table.name} has ${relationships.size} relationship(s)")
                    }
                }
        }

        logger.info("Kodama KSP: Found ${discoveredTables.size} table(s)")
        logger.info("Kodama KSP: Found ${tablesWithRelationships.size} table(s) with relationships")

        // Discover marker interfaces
        discoverMarkerInterfaces(resolver)
        logger.info("Kodama KSP: Found ${discoveredMarkers.size} marker interface(s)")

        // Load runtime metadata (contains SQL column names)
        val runtimeMetadata = loadRuntimeMetadata()

        // Discover and generate entity implementations
        if (discoveredTables.isNotEmpty()) {
            val entityDiscoverer = EntityInterfaceDiscoverer(logger, runtimeMetadata)
            val discoveredEntities = entityDiscoverer.discoverEntityInterfaces(resolver, discoveredTables)

            if (discoveredEntities.isNotEmpty()) {
                logger.info("Kodama KSP: Found ${discoveredEntities.size} entity interface(s)")

                val targetPackage = discoveredEntities.firstOrNull()?.packageName
                    ?: discoveredTables.firstOrNull()?.packageName
                    ?: "com.obabichev.kodama.generated"

                val entityGenerator = EntityImplementationGenerator(codeGenerator, logger)
                entityGenerator.generateEntityImplementations(discoveredEntities, "$targetPackage.generated")
            } else {
                logger.info("Kodama KSP: No entity interfaces found")
            }
        }

        // Write metadata to JSON file
        if (discoveredTables.isNotEmpty() || discoveredMarkers.isNotEmpty()) {
            writeMetadataFile()
        }

        // Generate CanJoin instances
        if (tablesWithRelationships.isNotEmpty()) {
            val targetPackage = tablesWithRelationships.firstOrNull()?.table?.packageName
                ?: "com.obabichev.kodama.generated"

            canJoinGenerator.generateCanJoinInstances(tablesWithRelationships, targetPackage)
        }

        // No deferred symbols
        return emptyList()
    }

    /**
     * Check if a class declaration is a Table object.
     * Must be:
     * 1. An object declaration (singleton)
     * 2. Extends Table or EntityTable
     */
    private fun isTableObject(declaration: KSClassDeclaration): Boolean {
        // Must be an object
        if (declaration.classKind != ClassKind.OBJECT) {
            return false
        }

        // Check if it extends Table or EntityTable
        val superTypes = declaration.superTypes.toList()
        return superTypes.any { superTypeRef ->
            val resolved = superTypeRef.resolve()
            val qualifiedName = resolved.declaration.qualifiedName?.asString() ?: ""
            qualifiedName == "com.obabichev.kodama.schema.Table" ||
                qualifiedName == "com.obabichev.kodama.schema.EntityTable"
        }
    }

    /**
     * Extract basic table information from a class declaration.
     */
    private fun extractTableInfo(declaration: KSClassDeclaration): KspTableModel {
        val name = declaration.simpleName.asString()
        val packageName = declaration.packageName.asString()
        val qualifiedName = declaration.qualifiedName?.asString() ?: "$packageName.$name"

        // Extract columns from table declaration
        val columns = extractColumns(declaration)

        return KspTableModel(
            name = name,
            packageName = packageName,
            qualifiedName = qualifiedName,
            columns = columns
        )
    }

    /**
     * Extract column name from property initializer.
     * For example, from `val userId = integer("user_id")`, extracts "user_id".
     */
    private fun extractColumnNameFromInitializer(property: KSPropertyDeclaration): String? {
        // Read the source file and extract the column name from the initializer
        val containingFile = property.containingFile ?: return null
        val sourceFile = containingFile.filePath

        try {
            val fileContent = java.io.File(sourceFile).readText()
            val propertyName = property.simpleName.asString()

            // Pattern: val propertyName = columnType("column_name")
            // Examples:
            //   val userId = integer("user_id")
            //   val userName = varchar("user_name", 255)
            //   val id = serial("id").primaryKey()
            val pattern = """val\s+$propertyName\s*=\s*\w+\s*\(\s*"([^"]+)"\s*(?:,|\)|\.)""".toRegex()
            val match = pattern.find(fileContent)

            return match?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.warn("Kodama KSP: Failed to extract column name for ${property.simpleName.asString()}: ${e.message}")
            return null
        }
    }

    /**
     * Extract column information from a table declaration.
     */
    private fun extractColumns(declaration: KSClassDeclaration): List<com.obabichev.kodama.ksp.model.ColumnModel> {
        val columns = mutableListOf<com.obabichev.kodama.ksp.model.ColumnModel>()

        declaration.getAllProperties().forEach { property ->
            // Check if property type is Column<T>
            val propertyType = property.type.resolve()
            val typeDeclaration = propertyType.declaration

            if (typeDeclaration.qualifiedName?.asString() == "com.obabichev.kodama.components.Column") {
                val propertyName = property.simpleName.asString()

                // Extract column name from the Column definition
                // Try to extract from initialization expression (e.g., integer("user_id"))
                val columnName = extractColumnNameFromInitializer(property) ?: propertyName

                // Extract type parameter T from Column<T>
                val columnType = propertyType.arguments.firstOrNull()?.type?.resolve()
                val typeName = columnType?.declaration?.qualifiedName?.asString() ?: "kotlin.Any"

                // Check if nullable
                val isNullable = propertyType.isMarkedNullable || (columnType?.isMarkedNullable == true)

                // Check if primary key (look for .primaryKey() call in initialization)
                val isPrimaryKey = isPropertyPrimaryKey(property)

                // Check if auto-generated (SERIAL, BIGSERIAL, SMALLSERIAL)
                val isAutoGenerated = isAutoGeneratedColumn(property)

                columns.add(
                    com.obabichev.kodama.ksp.model.ColumnModel(
                        propertyName = propertyName,
                        columnName = columnName,
                        typeName = typeName,
                        isPrimaryKey = isPrimaryKey,
                        isNullable = isNullable,
                        isAutoGenerated = isAutoGenerated
                    )
                )
            }
        }

        return columns
    }

    /**
     * Check if a property is marked as primary key.
     */
    private fun isPropertyPrimaryKey(property: KSPropertyDeclaration): Boolean {
        // This is a simple heuristic - checks if property name is "id" or contains "Id"
        // A more sophisticated approach would analyze the initialization expression
        val name = property.simpleName.asString()
        return name == "id" || name.endsWith("Id")
    }

    /**
     * Check if a column is auto-generated (SERIAL, BIGSERIAL, SMALLSERIAL).
     */
    private fun isAutoGeneratedColumn(property: KSPropertyDeclaration): Boolean {
        // Check if the column uses SerialColumnType, BigSerialColumnType, or SmallSerialColumnType
        // This would require analyzing the property initializer, which is complex in KSP
        // For now, return false and rely on explicit metadata
        return false
    }

    /**
     * Discover marker interfaces in the codebase.
     * A marker interface is either:
     * 1. Annotated with @Marker
     * 2. An empty interface (no properties, no functions)
     */
    private fun discoverMarkerInterfaces(resolver: Resolver) {
        resolver.getAllFiles().forEach { file ->
            file.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.INTERFACE }
                .forEach { interfaceDecl ->
                    // Check if it has @Marker annotation
                    val hasMarkerAnnotation = interfaceDecl.annotations.any { annotation ->
                        val annotationType = annotation.annotationType.resolve()
                        annotationType.declaration.qualifiedName?.asString() == "com.obabichev.kodama.annotations.Marker"
                    }

                    // Check if it's an empty interface
                    val isEmpty = !interfaceDecl.declarations.any()

                    // Include if it has @Marker or is empty
                    if (hasMarkerAnnotation || isEmpty) {
                        val name = interfaceDecl.simpleName.asString()
                        val packageName = interfaceDecl.packageName.asString()
                        val qualifiedName = interfaceDecl.qualifiedName?.asString() ?: "$packageName.$name"

                        // Skip if it's a table (tables are interfaces but not markers)
                        if (!discoveredTables.any { it.name == name }) {
                            val marker = MarkerInterfaceModel(
                                name = name,
                                packageName = packageName,
                                qualifiedName = qualifiedName,
                                hasMarkerAnnotation = hasMarkerAnnotation
                            )
                            discoveredMarkers.add(marker)
                            logger.info("Kodama KSP: Discovered marker: $name (annotated: $hasMarkerAnnotation, empty: $isEmpty)")
                        }
                    }
                }
        }
    }

    /**
     * Write discovered table metadata to JSON file.
     * Output: build/generated/ksp/main/resources/kodama-ksp-metadata.json
     */
    private fun writeMetadataFile() {
        try {
            val file = codeGenerator.createNewFile(
                dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray()),
                packageName = "",
                fileName = "kodama-ksp-metadata",
                extensionName = "json"
            )

            file.bufferedWriter().use { writer ->
                writer.write(generateJson())
            }

            logger.info("Kodama KSP: Wrote metadata to kodama-ksp-metadata.json")
        } catch (e: Exception) {
            // FileAlreadyExistsException means file was created in a previous round, which is fine
            if (e.javaClass.simpleName == "FileAlreadyExistsException") {
                logger.warn("Kodama KSP: Metadata file already exists, skipping write")
            } else {
                logger.error("Kodama KSP: Failed to write metadata file: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Generate JSON representation of discovered tables and markers.
     */
    private fun generateJson(): String {
        return buildString {
            appendLine("{")

            // Tables
            appendLine("  \"tables\": [")
            discoveredTables.forEachIndexed { index, table ->
                append("    ")
                append(table.toJson().replace("\n", "\n    "))
                if (index < discoveredTables.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine("  ],")

            // Markers
            appendLine("  \"markers\": [")
            discoveredMarkers.forEachIndexed { index, marker ->
                append("    ")
                append(marker.toJson().replace("\n", "\n    "))
                if (index < discoveredMarkers.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine("  ]")

            append("}")
        }
    }

    /**
     * Load runtime metadata JSON file that contains SQL column names.
     * The runtime metadata is generated by GenerateTableMetadataTask after compiling Table objects.
     */
    private fun loadRuntimeMetadata(): RuntimeMetadataRoot? {
        return try {
            // Try to find the runtime metadata JSON file
            // It should be at: build/generated/kodama/runtime-table-metadata.json
            val buildDir = options["kodama.build.dir"]
            if (buildDir == null) {
                logger.warn("Kodama KSP: kodama.build.dir option not set, runtime metadata unavailable")
                return null
            }

            val runtimeMetadataFile = File(buildDir, "generated/kodama/runtime-table-metadata.json")
            if (!runtimeMetadataFile.exists()) {
                logger.warn("Kodama KSP: Runtime metadata file not found at ${runtimeMetadataFile.absolutePath}")
                logger.warn("Kodama KSP: Entity generation will use property names instead of SQL column names")
                return null
            }

            val json = Json { ignoreUnknownKeys = true }
            val metadata = json.decodeFromString<RuntimeMetadataRoot>(runtimeMetadataFile.readText())
            logger.info("Kodama KSP: Loaded runtime metadata with ${metadata.tables.size} table(s)")
            metadata
        } catch (e: Exception) {
            logger.warn("Kodama KSP: Failed to load runtime metadata: ${e.message}")
            logger.warn("Kodama KSP: Entity generation will use property names instead of SQL column names")
            null
        }
    }
}
