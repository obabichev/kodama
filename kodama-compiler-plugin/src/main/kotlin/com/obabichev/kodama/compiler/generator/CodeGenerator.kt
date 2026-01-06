package com.obabichev.kodama.compiler.generator

/**
 * Base interface for all code generators in Kodama.
 *
 * Each generator is responsible for generating exactly ONE code construct:
 * - A single interface
 * - A single class
 * - A single function
 * - A single method
 *
 * This fine-grained approach enables:
 * - Independent unit testing of each construct
 * - Clear separation of concerns (Single Responsibility Principle)
 * - Easy debugging (stack traces point to specific generators)
 * - Simple composition and ordering
 *
 * Example implementations:
 * - ColumnMarkerGenerator generates a single marker interface like `interface Name`
 * - TableAccessorGenerator generates a single accessor class like `PersonAccessor`
 * - JoinMethodGenerator generates a single `.join()` extension function
 */
interface CodeGenerator {
    /**
     * Generate the code for this construct.
     *
     * @return Generated Kotlin code as a string, without package declaration or imports.
     *         The FileGenerator will handle adding the package and import statements.
     *
     * Example return value for ColumnMarkerGenerator:
     * ```
     * interface Name
     * ```
     *
     * Example return value for a simple extension function:
     * ```
     * fun from(table: Person): AfterFromQueryBuilder_Person {
     *     return AfterFromQueryBuilder_Person(QueryState(table))
     * }
     * ```
     */
    fun generate(): String

    /**
     * Get all fully-qualified imports required by this generator's output.
     *
     * The FileGenerator will:
     * - Collect imports from all generators
     * - Deduplicate them
     * - Sort them alphabetically
     * - Add them to the top of the generated file
     *
     * @return Set of fully-qualified import statements (without "import" keyword).
     *
     * Example:
     * ```
     * setOf(
     *     "com.obabichev.kodama.components.TypedColumn",
     *     "com.obabichev.kodama.query.QueryState"
     * )
     * ```
     *
     * Return empty set if no imports are needed (e.g., simple marker interfaces).
     */
    fun requiredImports(): Set<String>

    /**
     * Optional: Declare dependencies on other generators.
     *
     * This is used for:
     * - Ordering: Ensure dependencies are generated before dependents
     * - Validation: Detect circular dependencies
     * - Conditional generation: Skip generator if dependencies don't exist
     *
     * Default implementation returns empty list (no dependencies).
     *
     * Example: JoinMethodGenerator depends on JoinContextGenerator
     * ```
     * override fun dependencies(): List<CodeGenerator> = listOf(
     *     JoinContextGenerator(combination, joiningTable, schemaPackage)
     * )
     * ```
     *
     * @return List of generators that must be generated before this one
     */
    fun dependencies(): List<CodeGenerator> = emptyList()
}
