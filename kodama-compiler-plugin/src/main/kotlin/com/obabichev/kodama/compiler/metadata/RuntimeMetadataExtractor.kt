package com.obabichev.kodama.compiler.metadata

import java.io.File
import java.net.URLClassLoader
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Extracts full table metadata by loading compiled Table classes at runtime.
 *
 * This uses reflection to:
 * 1. Load compiled Table classes
 * 2. Access the Table singleton instance
 * 3. Read column metadata from Table.relation.columns
 * 4. Match columns to Kotlin property names
 */
class RuntimeMetadataExtractor(
    private val classOutputDir: File,
    private val classpathFiles: List<File> = listOf(classOutputDir)
) {

    private val classLoader: URLClassLoader = URLClassLoader(
        classpathFiles.map { it.toURI().toURL() }.toTypedArray(),
        this.javaClass.classLoader
    )

    /**
     * Extract complete metadata for a table.
     *
     * @param kspMetadata Basic table info from KSP
     * @return Complete table metadata with column information
     */
    fun extractTableMetadata(kspMetadata: KspTableMetadata): TableMetadata {
        try {
            // Load the compiled Table class
            val tableClass = classLoader.loadClass(kspMetadata.qualifiedName)

            // Get the singleton instance (Table objects are Kotlin objects)
            val tableInstance = tableClass.kotlin.objectInstance
                ?: error("${kspMetadata.qualifiedName} is not an object (singleton)")

            // Access relation property via reflection
            val relationProperty = tableClass.kotlin.memberProperties
                .find { it.name == "relation" }
                ?: error("Table ${kspMetadata.qualifiedName} does not have 'relation' property")

            relationProperty.isAccessible = true
            val relation: Any = relationProperty.getter.call(tableInstance)
                ?: error("Could not get relation from ${kspMetadata.qualifiedName}")

            // Get columns from relation via reflection
            val columnsProperty = relation::class.memberProperties
                .find { it.name == "columns" }
                ?: error("Relation does not have 'columns' property")

            columnsProperty.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val columns = columnsProperty.getter.call(relation) as? List<Any>
                ?: error("Could not get columns from relation")

            // Extract column metadata
            val columnMetadata = columns.map { column ->
                extractColumnMetadata(tableClass, tableInstance, column)
            }

            return TableMetadata(
                name = kspMetadata.name,
                packageName = kspMetadata.packageName,
                qualifiedName = kspMetadata.qualifiedName,
                columns = columnMetadata
            )
        } catch (e: ClassNotFoundException) {
            error("Could not load class ${kspMetadata.qualifiedName}. Make sure the class is compiled.")
        } catch (e: Exception) {
            error("Failed to extract metadata for ${kspMetadata.qualifiedName}: ${e.message}")
        }
    }

    /**
     * Extract metadata for a single column.
     *
     * Finds the Kotlin property name by matching column instances.
     */
    private fun extractColumnMetadata(
        tableClass: Class<*>,
        tableInstance: Any,
        column: Any
    ): ColumnMetadata {
        // Extract column properties via reflection
        val columnClass = column::class

        // Get column name (SQL name)
        val nameProperty = columnClass.memberProperties.find { it.name == "name" }
            ?: error("Column does not have 'name' property")
        nameProperty.isAccessible = true
        val sqlColumnName = nameProperty.getter.call(column) as? String
            ?: error("Could not get column name")

        // Get column type
        val typeProperty = columnClass.memberProperties.find { it.name == "type" }
            ?: error("Column does not have 'type' property")
        typeProperty.isAccessible = true
        val columnType: Any = typeProperty.getter.call(column)
            ?: error("Could not get column type")

        // Get nullable flag
        val nullableProperty = columnClass.memberProperties.find { it.name == "nullable" }
            ?: error("Column does not have 'nullable' property")
        nullableProperty.isAccessible = true
        val isNullable = nullableProperty.getter.call(column) as? Boolean
            ?: error("Could not get nullable flag")

        // Get generation strategy
        val generationStrategyProperty = columnClass.memberProperties.find { it.name == "generationStrategy" }
            ?: error("Column does not have 'generationStrategy' property")
        generationStrategyProperty.isAccessible = true
        val generationStrategy: Any = generationStrategyProperty.getter.call(column)
            ?: error("Could not get generation strategy")

        // Check if it's AlwaysGenerated (compare class simple name)
        val isAutoGenerated = generationStrategy::class.simpleName == "AlwaysGenerated"

        // Find the property name by matching column instance
        val propertyName = findPropertyName(tableClass, tableInstance, column)
            ?: error("Could not find property name for column '$sqlColumnName' in ${tableClass.name}")

        // Map ColumnType to Kotlin type string
        val kotlinType = mapColumnTypeToKotlinType(columnType, isNullable)

        return ColumnMetadata(
            propertyName = propertyName,
            sqlColumnName = sqlColumnName,
            kotlinType = kotlinType,
            isNullable = isNullable,
            isAutoGenerated = isAutoGenerated
        )
    }

    /**
     * Find the Kotlin property name that holds this column instance.
     *
     * Iterates through all properties and compares by identity.
     */
    private fun findPropertyName(tableClass: Class<*>, tableInstance: Any, column: Any): String? {
        return tableClass.kotlin.memberProperties
            .firstOrNull { prop ->
                try {
                    prop.isAccessible = true
                    val value: Any? = prop.getter.call(tableInstance)
                    // Check if this property holds the same column instance
                    value === column
                } catch (e: Exception) {
                    false
                }
            }
            ?.name
    }

    /**
     * Map ColumnType to Kotlin type string for code generation.
     */
    private fun mapColumnTypeToKotlinType(columnType: Any, isNullable: Boolean): String {
        val baseType = when (columnType::class.simpleName) {
            "IntColumnType", "SerialColumnType" -> "Int"
            "LongColumnType", "BigSerialColumnType" -> "Long"
            "ShortColumnType", "SmallSerialColumnType" -> "Short"
            "StringColumnType" -> "String"
            "BooleanColumnType" -> "Boolean"
            "FloatColumnType" -> "Float"
            "DoubleColumnType" -> "Double"
            "DecimalColumnType" -> "java.math.BigDecimal"
            "DateColumnType" -> "java.time.LocalDate"
            "TimeColumnType" -> "java.time.LocalTime"
            "TimeWithTimeZoneColumnType" -> "java.time.OffsetTime"
            "TimestampColumnType" -> "java.time.LocalDateTime"
            "TimestampWithTimeZoneColumnType" -> "java.time.OffsetDateTime"
            "IntervalColumnType" -> "java.time.Duration"
            else -> {
                // Fallback: try to infer from generic type parameter
                "Any /* Unknown type: ${columnType::class.simpleName} */"
            }
        }

        return if (isNullable) "$baseType?" else baseType
    }

    /**
     * Extract metadata for all tables.
     */
    fun extractAllTables(kspTables: List<KspTableMetadata>): List<TableMetadata> {
        return kspTables.map { extractTableMetadata(it) }
    }

    /**
     * Close the class loader to release resources.
     */
    fun close() {
        classLoader.close()
    }
}
