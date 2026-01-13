package com.obabichev.kodama.compiler.parser

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

/**
 * Parses Kotlin source files into PSI (Program Structure Interface) trees for AST analysis.
 *
 * This replaces regex-based pattern matching with structured AST traversal, enabling:
 * - Zero regex patterns
 * - Robust query discovery
 * - Support for complex nested queries
 *
 * Usage:
 * ```kotlin
 * val parser = KotlinASTParser()
 * try {
 *     val ktFile = parser.parse(File("MyTest.kt"))
 *     val visitor = QueryDiscoveryVisitor()
 *     ktFile.accept(visitor)
 *     // Use visitor.discoveredQueries
 * } finally {
 *     parser.dispose()
 * }
 * ```
 */
class KotlinASTParser {

    private val disposable = Disposer.newDisposable("KotlinASTParser")
    private val environment: KotlinCoreEnvironment

    init {
        val configuration = CompilerConfiguration()
        environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    /**
     * Parse a Kotlin file into a PSI tree.
     *
     * @param file The Kotlin source file to parse
     * @return PSI tree root (KtFile)
     * @throws IllegalStateException if file cannot be parsed
     */
    fun parse(file: File): KtFile {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        require(file.extension == "kt") { "File must be a Kotlin source file (.kt): ${file.absolutePath}" }

        val virtualFile = createVirtualFile(file)
        val psiManager = PsiManager.getInstance(environment.project)

        return psiManager.findFile(virtualFile) as? KtFile
            ?: error("Failed to parse Kotlin file: ${file.absolutePath}")
    }

    /**
     * Parse Kotlin source text directly (useful for testing).
     *
     * @param sourceText Kotlin source code as string
     * @param fileName Virtual file name for error messages (default: "temp.kt")
     * @return PSI tree root (KtFile)
     */
    fun parseText(sourceText: String, fileName: String = "temp.kt"): KtFile {
        val psiFactory = KtPsiFactory(environment.project)
        return psiFactory.createFile(fileName, sourceText)
    }

    /**
     * Parse multiple Kotlin files.
     *
     * @param files List of Kotlin source files
     * @return List of parsed PSI trees
     */
    fun parseAll(files: List<File>): List<KtFile> {
        return files
            .filter { it.extension == "kt" }
            .map { parse(it) }
    }

    /**
     * Clean up resources. Must be called when done using the parser.
     *
     * Note: Parser cannot be reused after disposal.
     */
    fun dispose() {
        Disposer.dispose(disposable)
    }

    /**
     * Create a virtual file handle for the given physical file.
     */
    private fun createVirtualFile(file: File): VirtualFile {
        val fileSystem = VirtualFileManager.getInstance().getFileSystem("file")
            ?: error("Local file system not available")

        return fileSystem.findFileByPath(file.absolutePath)
            ?: error("Could not create virtual file for: ${file.absolutePath}")
    }
}
