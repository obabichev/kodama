package com.obabichev.kodama.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.obabichev.kodama.ksp.model.KspTableModel
import com.obabichev.kodama.ksp.model.TableWithRelationships

/**
 * KSP processor that discovers relationships and generates CanJoin instances.
 *
 * This processor runs after the main KodamaSymbolProcessor and:
 * 1. Finds all Table objects
 * 2. Extracts relationship declarations (oneToMany, manyToOne)
 * 3. Generates CanJoin instances for compile-time join validation
 */
class RelationshipSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val relationshipExtractor = RelationshipExtractor(logger)
    private val canJoinGenerator = CanJoinGenerator(codeGenerator, logger)
    private val tablesWithRelationships = mutableListOf<TableWithRelationships>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("Kodama KSP: Starting relationship discovery...")

        // Find all Table objects
        resolver.getAllFiles().forEach { file ->
            file.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { isTableObject(it) }
                .forEach { tableClass ->
                    processTableForRelationships(tableClass)
                }
        }

        logger.info("Kodama KSP: Found ${tablesWithRelationships.size} table(s) with relationships")

        // Generate CanJoin instances
        if (tablesWithRelationships.isNotEmpty()) {
            // Determine target package from first table
            val targetPackage = tablesWithRelationships.firstOrNull()?.table?.packageName
                ?: "com.obabichev.kodama.generated"

            canJoinGenerator.generateCanJoinInstances(tablesWithRelationships, targetPackage)
        }

        // No deferred symbols
        return emptyList()
    }

    /**
     * Check if a class declaration is a Table object.
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
     * Process a table declaration to extract relationships.
     */
    private fun processTableForRelationships(tableClass: KSClassDeclaration) {
        val tableName = tableClass.simpleName.asString()
        val packageName = tableClass.packageName.asString()
        val qualifiedName = tableClass.qualifiedName?.asString() ?: "$packageName.$tableName"

        val table = KspTableModel(
            name = tableName,
            packageName = packageName,
            qualifiedName = qualifiedName
        )

        // Extract relationships
        val relationships = relationshipExtractor.extractRelationships(tableClass)

        if (relationships.isNotEmpty()) {
            tablesWithRelationships.add(
                TableWithRelationships(table, relationships)
            )
            logger.info("Kodama KSP: Table $tableName has ${relationships.size} relationship(s)")
        }
    }
}
