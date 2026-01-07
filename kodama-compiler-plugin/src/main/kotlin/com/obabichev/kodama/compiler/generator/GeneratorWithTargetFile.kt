package com.obabichev.kodama.compiler.generator

/**
 * Wrapper that adds target file information to any CodeGenerator.
 *
 * This allows us to specify target files without modifying all existing generator classes.
 * The wrapper delegates all CodeGenerator methods to the wrapped generator, and adds
 * the targetFile() implementation.
 *
 * Example usage:
 * ```kotlin
 * val generator = ColumnMarkerGenerator(columnInfo)
 * val withTarget = GeneratorWithTargetFile(generator, "_infrastructure/Markers.kt")
 * ```
 */
class GeneratorWithTargetFile(
    private val delegate: CodeGenerator,
    private val targetFilePath: String
) : CodeGenerator {

    override fun generate(): String = delegate.generate()

    override fun requiredImports(): Set<String> = delegate.requiredImports()

    override fun dependencies(): List<CodeGenerator> = delegate.dependencies()

    override fun targetFile(): String = targetFilePath

    override fun toString(): String =
        "${delegate::class.simpleName} → $targetFilePath"
}

/**
 * Extension function to easily wrap a generator with target file information.
 *
 * Example usage:
 * ```kotlin
 * val generator = ColumnMarkerGenerator(columnInfo).withTargetFile("_infrastructure/Markers.kt")
 * ```
 */
fun CodeGenerator.withTargetFile(targetFilePath: String): CodeGenerator =
    GeneratorWithTargetFile(this, targetFilePath)
