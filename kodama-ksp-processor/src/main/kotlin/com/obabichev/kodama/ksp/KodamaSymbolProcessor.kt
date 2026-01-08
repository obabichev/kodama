package com.obabichev.kodama.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.obabichev.kodama.ksp.model.KspTableModel
import com.obabichev.kodama.ksp.model.TableWithRelationships

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

        // Write metadata to JSON file
        if (discoveredTables.isNotEmpty()) {
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

        return KspTableModel(
            name = name,
            packageName = packageName,
            qualifiedName = qualifiedName
        )
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
     * Generate JSON representation of discovered tables.
     */
    private fun generateJson(): String {
        return buildString {
            appendLine("{")
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
            appendLine("  ]")
            append("}")
        }
    }
}
