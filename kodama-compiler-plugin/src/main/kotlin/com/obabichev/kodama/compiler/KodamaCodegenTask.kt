package com.obabichev.kodama.compiler

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.io.File

@CacheableTask
abstract class KodamaCodegenTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val srcDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val testDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        // Set defaults
        srcDir.convention(project.layout.projectDirectory.dir("src/main/kotlin"))
        testDir.convention(project.layout.projectDirectory.dir("src/test/kotlin"))
        outputDir.convention(project.layout.buildDirectory.dir("generated/kodama"))
    }

    @TaskAction
    fun generate() {
        val selectionOutputFile = outputDir.get().asFile.resolve("com/obabichev/kodama/SelectionExtensions.kt")
        val queryOutputFile = outputDir.get().asFile.resolve("com/obabichev/kodama/QueryExtensions.kt")

        // Track all discovered types
        val discoveredTypes = mutableSetOf<String>()

        // Track unique selection combinations (sorted list of type names)
        val selectionCombinations = mutableSetOf<List<String>>()

        // Track unique query combinations (ordered list to preserve join order)
        val queryCombinations = mutableSetOf<List<String>>()

        // Map from lowercase class name to fully qualified class name
        val classNameMap = mutableMapOf<String, String>()

        // Map to store class properties (class name -> list of property names)
        val classProperties = mutableMapOf<String, List<String>>()

        // Scan source files for Selection usage and class definitions (both main and test sources)
        val dirsToScan = listOfNotNull(
            srcDir.get().asFile.takeIf { it.exists() },
            testDir.orNull?.asFile?.takeIf { it.exists() }
        )

        dirsToScan.forEach { dir ->
            dir.walk().filter { it.isFile && it.name.endsWith(".kt") }.forEach { file ->
                val content = file.readText()

                // Extract package name
                val packageMatch = """package\s+([\w.]+)""".toRegex().find(content)
                val packageName = packageMatch?.groupValues?.get(1) ?: ""

                // Find all class definitions (including nested classes)
                val lines = content.lines()
                val classStack = mutableListOf<String>() // Track nesting
                var currentClassName: String? = null
                val currentClassProperties = mutableListOf<String>()
                var insideDataClass = false

                lines.forEach { line ->
                    val trimmed = line.trim()

                    // Check for class definition
                    val classMatch = """(data class|class|sealed class|interface|object)\s+(\w+)""".toRegex().find(trimmed)
                    if (classMatch != null) {
                        // Save previous class properties if any
                        if (currentClassName != null && currentClassProperties.isNotEmpty()) {
                            classProperties[currentClassName!!] = currentClassProperties.toList()
                            currentClassProperties.clear()
                        }

                        val isDataClass = classMatch.groupValues[1] == "data class"
                        val className = classMatch.groupValues[2]
                        insideDataClass = isDataClass

                        // Build FQN based on current nesting
                        val fqn = buildString {
                            if (packageName.isNotEmpty()) {
                                append(packageName)
                                append(".")
                            }
                            classStack.forEach {
                                append(it)
                                append(".")
                            }
                            append(className)
                        }

                        classNameMap[className.lowercase()] = fqn
                        if (isDataClass) {
                            currentClassName = className.lowercase()
                        }

                        // If line has opening brace, track nesting
                        if (trimmed.contains("{")) {
                            classStack.add(className)
                        }
                    }

                    // Extract properties from data class constructor
                    if (insideDataClass && (trimmed.startsWith("val ") || trimmed.startsWith("var "))) {
                        val propMatch = """(?:val|var)\s+(\w+)\s*:""".toRegex().find(trimmed)
                        if (propMatch != null) {
                            val propName = propMatch.groupValues[1]
                            currentClassProperties.add(propName)
                        }
                    }

                    // Track closing braces
                    if (trimmed == "}" && classStack.isNotEmpty()) {
                        classStack.removeLastOrNull()
                    }

                    // End of data class constructor
                    if (trimmed.startsWith(")") && insideDataClass) {
                        insideDataClass = false
                    }
                }

                // Save last class properties
                if (currentClassName != null && currentClassProperties.isNotEmpty()) {
                    classProperties[currentClassName!!] = currentClassProperties.toList()
                }

                // Find select/join usage and track combinations
                // Match select().join()... chains - capture until .build() or end of statement
                // Use DOTALL mode ((?s)) so . matches newlines, and non-greedy .*? to stop at .build()
                val chainPattern = """(?s)select\s*\([^)]*\)(?:\s*\.(?:join|build)\s*\([^)]*\))*""".toRegex()

                chainPattern.findAll(content).forEach { chainMatch ->
                    val chain = chainMatch.value
                    val typesInChain = mutableListOf<String>()

                    // Extract all types in this chain
                    val typePattern = """(\w+)::class""".toRegex()
                    typePattern.findAll(chain).forEach { typeMatch ->
                        val typeName = typeMatch.groupValues[1].lowercase()
                        typesInChain.add(typeName)
                        discoveredTypes.add(typeName)
                    }

                    // Store all intermediate and final combinations (sorted for consistency)
                    if (typesInChain.isNotEmpty()) {
                        // Add all prefixes: if we have [box, item], add both [box] and [box, item]
                        for (i in 1..typesInChain.size) {
                            val subCombination = typesInChain.take(i).sorted()
                            selectionCombinations.add(subCombination)
                        }
                    }
                }

                // Find query().from().join() chains and track query combinations
                // Support both old style with Pair: .join(Profile::class, Profile::userName to Person::name)
                // And new lambda style: .join(Profile::class) { profile.userName eq person.name }
                // Match until .select(), .where(), or .build()
                val queryChainPattern = """(?s)query\s*\(\s*\)\s*\.from\s*\([^)]*\)(?:\s*\.join\s*\([^)]*\)(?:\s*\{[^}]*\})?)*""".toRegex()

                queryChainPattern.findAll(content).forEach { chainMatch ->
                    val chain = chainMatch.value
                    val typesInChain = mutableListOf<String>()

                    // Extract all types in this chain (preserve order for query chains)
                    // Match both Person::class and <Person>
                    val typePatternOld = """(\w+)::class""".toRegex()
                    val typePatternNew = """<(\w+)>""".toRegex()

                    typePatternOld.findAll(chain).forEach { typeMatch ->
                        val typeName = typeMatch.groupValues[1].lowercase()
                        typesInChain.add(typeName)
                        discoveredTypes.add(typeName)
                    }

                    typePatternNew.findAll(chain).forEach { typeMatch ->
                        val typeName = typeMatch.groupValues[1].lowercase()
                        if (!typesInChain.contains(typeName)) {
                            typesInChain.add(typeName)
                            discoveredTypes.add(typeName)
                        }
                    }

                    // Store all prefixes: if we have [person, order], add both [person] and [person, order]
                    if (typesInChain.isNotEmpty()) {
                        for (i in 1..typesInChain.size) {
                            val subCombination = typesInChain.take(i)
                            queryCombinations.add(subCombination)
                        }
                    }
                }
            }
        }

        // Generate SelectionExtensions.kt
        selectionOutputFile.parentFile.mkdirs()
        selectionOutputFile.writeText(buildString {
            appendLine("@file:Suppress(\"UNCHECKED_CAST\", \"NOTHING_TO_INLINE\")")
            appendLine()
            appendLine("package com.obabichev.kodama")
            appendLine()
            appendLine("import kotlin.reflect.KClass")
            appendLine()

            // Generate imports for all discovered types
            val imports = discoveredTypes.mapNotNull { classNameMap[it] }.filter { it.contains(".") }.toSet().sorted()
            if (imports.isNotEmpty()) {
                imports.forEach { fqn ->
                    appendLine("import $fqn")
                }
                appendLine()
            }

            appendLine("// AUTO-GENERATED by Kodama Compiler Plugin")
            appendLine("// DO NOT EDIT MANUALLY")
            appendLine()

            // Generate interfaces for each unique selection combination
            appendLine("// Selection interfaces - each represents a specific combination of joined types")
            selectionCombinations.forEach { combination ->
                val interfaceName = "Selection_" + combination.joinToString("_") { it.replaceFirstChar { c -> c.uppercase() } }

                appendLine("interface $interfaceName : ISelection {")
                combination.forEach { typeName ->
                    val fqn = classNameMap[typeName]
                    if (fqn != null) {
                        val shortName = fqn.substringAfterLast(".")
                        val propertyName = typeName.lowercase()
                        appendLine("    val $propertyName: $shortName")
                    }
                }
                appendLine("}")
                appendLine()
            }

            appendLine()
            appendLine("// Wrapper implementations for each selection interface")
            selectionCombinations.forEach { combination ->
                val interfaceName = "Selection_" + combination.joinToString("_") { it.replaceFirstChar { c -> c.uppercase() } }
                val className = "${interfaceName}_Impl"

                appendLine("@PublishedApi")
                appendLine("internal class $className(private val impl: SelectionImpl) : $interfaceName {")

                combination.forEach { typeName ->
                    val fqn = classNameMap[typeName]
                    if (fqn != null) {
                        val shortName = fqn.substringAfterLast(".")
                        val propertyName = typeName.lowercase()
                        val className = typeName.replaceFirstChar { it.uppercase() }
                        appendLine("    override val $propertyName: $shortName")
                        appendLine("        get() = impl.get<$shortName>(\"$propertyName\") ?: throw IllegalStateException(\"Type mismatch: $className not found or wrong type in selection\")")
                    }
                }

                appendLine("    override fun <T : Any> get(name: String, type: KClass<T>): T? = impl.get(name, type)")
                appendLine("    override fun has(name: String): Boolean = impl.has(name)")
                appendLine("    override val size: Int get() = impl.size")
                appendLine("}")
                appendLine()
            }

            appendLine()
            appendLine("// Type-specific builder classes")
            appendLine()

            // Generate type-specific builders for each combination
            selectionCombinations.forEach { combination ->
                val interfaceName = "Selection_" + combination.joinToString("_") { it.replaceFirstChar { c -> c.uppercase() } }
                val implClassName = "${interfaceName}_Impl"
                val builderClassName = "SelectBuilder_" + combination.joinToString("_") { it.replaceFirstChar { c -> c.uppercase() } }

                appendLine("/**")
                appendLine(" * Type-specific builder for: ${combination.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }}")
                appendLine(" */")
                appendLine("class $builderClassName @PublishedApi internal constructor(")
                appendLine("    @PublishedApi internal val impl: SelectionImpl")
                appendLine(") {")

                // Generate build() method (only if there's a matching Selection interface)
                if (selectionCombinations.contains(combination)) {
                    appendLine("    /**")
                    appendLine("     * Build the final typed selection.")
                    appendLine("     */")
                    appendLine("    fun build(): $interfaceName {")
                    appendLine("        return ${implClassName}(impl)")
                    appendLine("    }")
                    appendLine()
                }

                // Generate typed join() methods for each type not yet in this combination
                discoveredTypes.forEach { newType ->
                    if (!combination.contains(newType)) {
                        val fqn = classNameMap[newType]
                        if (fqn != null) {
                            val shortName = fqn.substringAfterLast(".")
                            val newCombination = (combination + newType).sorted()

                            // Only generate join if the resulting combination exists
                            if (selectionCombinations.contains(newCombination)) {
                                val newBuilderClassName = "SelectBuilder_" + newCombination.joinToString("_") { it.replaceFirstChar { c -> c.uppercase() } }

                                appendLine("    /**")
                                appendLine("     * Join a $shortName to the selection.")
                                appendLine("     */")
                                appendLine("    fun join(klass: KClass<$shortName>, instance: $shortName): $newBuilderClassName {")
                                appendLine("        return ${newBuilderClassName}(impl.add(klass, instance))")
                                appendLine("    }")
                                appendLine()
                            }
                        }
                    }
                }

                appendLine("}")
                appendLine()
            }

            appendLine()
            appendLine("// Top-level select() functions for each type")

            // Generate select() functions for each discovered type
            discoveredTypes.forEach { typeName ->
                val fqn = classNameMap[typeName]
                if (fqn != null) {
                    val shortName = fqn.substringAfterLast(".")
                    val singleTypeCombination = listOf(typeName)

                    // Only generate if a builder exists for this single type
                    if (selectionCombinations.contains(singleTypeCombination)) {
                        val builderClassName = "SelectBuilder_${typeName.replaceFirstChar { it.uppercase() }}"

                        appendLine()
                        appendLine("/**")
                        appendLine(" * Start a selection with a $shortName.")
                        appendLine(" */")
                        appendLine("fun select(klass: KClass<$shortName>, instance: $shortName): $builderClassName {")
                        appendLine("    val initialSelection = SelectionImpl(listOf(SelectionImpl.Entry(klass, instance)))")
                        appendLine("    return ${builderClassName}(initialSelection)")
                        appendLine("}")
                    }
                }
            }
            appendLine()

        })

        // Generate QueryExtensions.kt
        // Use the package from the first discovered type to place generated code
        val firstTypePackage = discoveredTypes.firstOrNull()?.let { classNameMap[it]?.substringBeforeLast(".") } ?: "com.obabichev.kodama"
        val queryOutputFileInPackage = outputDir.get().asFile.resolve(firstTypePackage.replace(".", "/") + "/QueryExtensions.kt")

        queryOutputFileInPackage.parentFile.mkdirs()
        queryOutputFileInPackage.writeText(buildString {
            appendLine("@file:Suppress(\"UNCHECKED_CAST\")")
            appendLine()
            appendLine("package $firstTypePackage")
            appendLine()
            appendLine("import com.obabichev.kodama.components.JoinType")
            appendLine("import com.obabichev.kodama.query.*")

            // Generate imports for all discovered types
            val imports = discoveredTypes.mapNotNull { classNameMap[it] }.filter { it.contains(".") }.toSet().sorted()
            if (imports.isNotEmpty()) {
                imports.forEach { fqn ->
                    appendLine("import $fqn")
                }
            }

            appendLine("import kotlin.reflect.KClass")
            appendLine("import kotlin.reflect.KProperty1")
            appendLine()
            appendLine("// AUTO-GENERATED by Kodama Compiler Plugin")
            appendLine("// DO NOT EDIT MANUALLY")
            appendLine()

            // Generate typed accessor classes for each discovered type
            discoveredTypes.forEach { typeName ->
                val fqn = classNameMap[typeName]
                if (fqn != null) {
                    val shortName = fqn.substringAfterLast(".")
                    val accessorClassName = "${shortName}Accessor"
                    val properties = classProperties[typeName] ?: emptyList()

                    appendLine("/**")
                    appendLine(" * Type-safe accessor for $shortName table")
                    appendLine(" */")
                    appendLine("class $accessorClassName(")
                    appendLine("    private val tableAccessor: TableAccessor<$shortName>")
                    appendLine(") {")
                    appendLine("    fun all(): List<com.obabichev.kodama.components.Column<*>> = tableAccessor.all()")
                    appendLine()

                    // Generate property accessors for each property
                    properties.forEach { propName ->
                        appendLine("    val $propName: com.obabichev.kodama.components.Column<*>")
                        appendLine("        get() = tableAccessor.column($shortName::$propName)")
                        appendLine()
                    }

                    appendLine("}")
                    appendLine()
                }
            }

            appendLine()

            // Track generated ResultAccessor classes to avoid duplicates
            val generatedResultAccessors = mutableSetOf<String>()

            // Generate typed builders and contexts for each query combination
            queryCombinations.forEach { combination ->
                val typeNames = combination.map { it.replaceFirstChar { c -> c.uppercase() } }
                val builderClassName = "AfterFromQueryBuilder_" + typeNames.joinToString("_")
                val contextClassName = "SelectContext_" + typeNames.joinToString("_")

                appendLine("// ========== ${typeNames.joinToString(" + ")} ==========")
                appendLine()

                // Generate builder class
                appendLine("class $builderClassName(")
                appendLine("    override val state: QueryState")
                appendLine(") : AfterFromQueryBuilderBase")
                appendLine()

                // Generate from() extension only for single-table combinations
                if (combination.size == 1) {
                    val typeName = combination[0]
                    val fqn = classNameMap[typeName]
                    if (fqn != null) {
                        val shortName = fqn.substringAfterLast(".")
                        appendLine("fun InitialQueryBuilder.from(klass: KClass<$shortName>): $builderClassName {")
                        appendLine("    state._from = state.relations.relation(klass)")
                        appendLine("    return $builderClassName(state)")
                        appendLine("}")
                        appendLine()
                    }
                }

                // Generate join() extensions for types not yet in the combination
                discoveredTypes.forEach { newType ->
                    if (!combination.contains(newType)) {
                        val fqn = classNameMap[newType]
                        if (fqn != null) {
                            val shortName = fqn.substringAfterLast(".")
                            val newCombination = combination + newType

                            // Check if this join target exists
                            if (queryCombinations.contains(newCombination)) {
                                val newBuilderClassName = "AfterFromQueryBuilder_" + newCombination.map { it.replaceFirstChar { c -> c.uppercase() } }.joinToString("_")
                                val jvmName = "join" + typeNames.joinToString("") + newType.replaceFirstChar { it.uppercase() }
                                val joinContextClassName = "JoinContext_" + typeNames.joinToString("_") + "_" + newType.replaceFirstChar { it.uppercase() }

                                // Generate JoinContext class for this specific join
                                appendLine("class $joinContextClassName(")
                                appendLine("    private val state: QueryState,")
                                appendLine("    private val joiningRelation: com.obabichev.kodama.components.Relation")
                                appendLine(") {")

                                // Add accessors for all tables in the current combination (already joined)
                                combination.forEach { existingTypeName ->
                                    val existingFqn = classNameMap[existingTypeName]
                                    if (existingFqn != null) {
                                        val existingShortName = existingFqn.substringAfterLast(".")
                                        val existingAccessorClassName = "${existingShortName}Accessor"
                                        appendLine("    val $existingTypeName = $existingAccessorClassName(TableAccessor<$existingShortName>(state.relations.relation($existingShortName::class), state.relations))")
                                    }
                                }

                                // Add accessor for the table being joined
                                val joiningAccessorClassName = "${shortName}Accessor"
                                appendLine("    val $newType = $joiningAccessorClassName(TableAccessor<$shortName>(joiningRelation, state.relations))")

                                appendLine("}")
                                appendLine()

                                // Generate old-style join (deprecated)
                                appendLine("@Deprecated(\"Use join with lambda for type-safe conditions\")")
                                appendLine("@JvmName(\"${jvmName}Old\")")
                                appendLine("fun $builderClassName.join(")
                                appendLine("    klass: KClass<$shortName>,")
                                appendLine("    condition: Pair<KProperty1<$shortName, *>, KProperty1<*, *>>,")
                                appendLine("    type: JoinType = JoinType.INNER")
                                appendLine("): $newBuilderClassName {")
                                appendLine("    val relation = state.relations.relation(klass)")
                                appendLine("    @Suppress(\"UNCHECKED_CAST\")")
                                appendLine("    val leftColumn = state.relations.column(condition.first as KProperty1<$shortName, Any?>)")
                                appendLine("    @Suppress(\"UNCHECKED_CAST\")")
                                appendLine("    val rightColumn = state.relations.column(condition.second as KProperty1<Any, Any?>)")
                                appendLine("    state._joins.add(com.obabichev.kodama.components.Join(type, relation, leftColumn to rightColumn))")
                                appendLine("    return $newBuilderClassName(state)")
                                appendLine("}")
                                appendLine()

                                // Generate new-style type-safe join
                                appendLine("@JvmName(\"$jvmName\")")
                                appendLine("fun $builderClassName.join(")
                                appendLine("    klass: KClass<$shortName>,")
                                appendLine("    type: JoinType = JoinType.INNER,")
                                appendLine("    condition: $joinContextClassName.() -> Pair<com.obabichev.kodama.components.Column<*>, com.obabichev.kodama.components.Column<*>>")
                                appendLine("): $newBuilderClassName {")
                                appendLine("    val relation = state.relations.relation(klass)")
                                appendLine("    val context = $joinContextClassName(state, relation)")
                                appendLine("    val (leftColumn, rightColumn) = context.condition()")
                                appendLine("    state._joins.add(com.obabichev.kodama.components.Join(type, relation, leftColumn to rightColumn))")
                                appendLine("    return $newBuilderClassName(state)")
                                appendLine("}")
                                appendLine()
                            }
                        }
                    }
                }

                // Generate SelectContext
                appendLine("class $contextClassName(")
                appendLine("    private val state: QueryState")
                appendLine(") : SelectContext() {")

                combination.forEach { typeName ->
                    val fqn = classNameMap[typeName]
                    if (fqn != null) {
                        val shortName = fqn.substringAfterLast(".")
                        val accessorClassName = "${shortName}Accessor"
                        appendLine("    val $typeName = $accessorClassName(TableAccessor<$shortName>(state.relations.relation($shortName::class), state.relations))")
                    }
                }

                appendLine("}")
                appendLine()

                // Generate select() extension
                appendLine("fun $builderClassName.select(block: $contextClassName.() -> Unit): $builderClassName {")
                appendLine("    val context = $contextClassName(state)")
                appendLine("    context.block()")
                appendLine("    state._select = context.selectedColumns")
                appendLine("    return this")
                appendLine("}")
                appendLine()

                // Generate WhereContext with same accessors
                val whereContextClassName = "WhereContext_" + typeNames.joinToString("_")
                appendLine("class $whereContextClassName(")
                appendLine("    private val state: QueryState")
                appendLine(") {")

                combination.forEach { typeName ->
                    val fqn = classNameMap[typeName]
                    if (fqn != null) {
                        val shortName = fqn.substringAfterLast(".")
                        val accessorClassName = "${shortName}Accessor"
                        appendLine("    val $typeName = $accessorClassName(TableAccessor<$shortName>(state.relations.relation($shortName::class), state.relations))")
                    }
                }

                appendLine("}")
                appendLine()

                // Generate where() extension
                appendLine("fun $builderClassName.where(block: $whereContextClassName.() -> com.obabichev.kodama.components.expression.Expression): $builderClassName {")
                appendLine("    val context = $whereContextClassName(state)")
                appendLine("    state.whereExpression = context.block()")
                appendLine("    return this")
                appendLine("}")
                appendLine()

                // Generate QueryResult class for this combination
                val resultClassName = "QueryResult_" + typeNames.joinToString("_")
                appendLine("class $resultClassName(")
                appendLine("    override val resultSet: java.sql.ResultSet,")
                appendLine("    override val relations: com.obabichev.kodama.query.RelationsContainer,")
                appendLine("    private val selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
                appendLine(") : com.obabichev.kodama.query.QueryResult {")

                // Add table accessors for each table in the combination
                combination.forEach { typeName ->
                    val fqn = classNameMap[typeName]
                    if (fqn != null) {
                        val shortName = fqn.substringAfterLast(".")
                        val resultAccessorClassName = "${shortName}ResultAccessor"
                        appendLine("    val $typeName = $resultAccessorClassName(resultSet, relations, selectedColumns)")
                    }
                }

                appendLine("}")
                appendLine()

                // Generate result accessor classes for each table (only once per type)
                combination.forEach { typeName ->
                    val fqn = classNameMap[typeName]
                    if (fqn != null) {
                        val shortName = fqn.substringAfterLast(".")
                        val resultAccessorClassName = "${shortName}ResultAccessor"

                        // Only generate if we haven't already
                        if (!generatedResultAccessors.contains(resultAccessorClassName)) {
                            generatedResultAccessors.add(resultAccessorClassName)

                            val properties = classProperties[typeName] ?: emptyList()

                            appendLine("class $resultAccessorClassName(")
                            appendLine("    resultSet: java.sql.ResultSet,")
                            appendLine("    relations: com.obabichev.kodama.query.RelationsContainer,")
                            appendLine("    selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
                            appendLine(") : com.obabichev.kodama.query.TableResultAccessor(resultSet, relations, selectedColumns) {")

                            // Generate property accessors for each property
                            properties.forEach { propName ->
                                appendLine("    val $propName: Any?")
                                appendLine("        get() {")
                                appendLine("            val relation = relations.relation($shortName::class)")
                                appendLine("            val column = relations.column($shortName::$propName)")
                                appendLine("            return readColumn(column)")
                                appendLine("        }")
                                appendLine()
                            }

                            appendLine("}")
                            appendLine()
                        }
                    }
                }

                // Generate execute extension for this builder
                appendLine("fun $builderClassName.execute(transaction: com.obabichev.kodama.execute.JdbcTransaction): com.obabichev.kodama.query.QueryResultIterable<$resultClassName> {")
                appendLine("    val query = this.build()")
                appendLine("    val resultSet = transaction.execute(query)")
                appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, state.relations) { rs, relations ->")
                appendLine("        $resultClassName(rs, relations, query.select)")
                appendLine("    }")
                appendLine("}")
                appendLine()
            }
        })

        logger.lifecycle("Kodama: Generated ${discoveredTypes.size} types, ${queryCombinations.size} query combinations")
    }
}
