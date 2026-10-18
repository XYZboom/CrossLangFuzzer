package com.github.xyzboom.codesmith.kotlin.bsp

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import java.io.File
import java.nio.file.Files

class BspKotlinCompiler(
    private val version: String,
    private val supplier: String = "Official",
) : ICompiler {
    override fun compile(program: IrProgram): CompileResult {
        return compileSource(IrProgramPrinter(Language.KOTLIN).printToSingle(program))
    }

    internal fun compileSource(fileContent: String): CompileResult {
        return try {
            val tempDir = Files.createTempDirectory("clf-bsp-compile-").toFile()
            val sourceFile = File(tempDir, "Test.kt")
            val outputDir = File(tempDir, "out").apply { mkdirs() }
            sourceFile.writeText(fileContent)

            val process = ProcessBuilder(
                BspServerManager.clientExecutable.absolutePath,
                "--host", "127.0.0.1",
                "--port", BspServerManager.port.toString(),
                "--language", "kotlin",
                "--supplier", supplier,
                "--version", version,
                "--",
                sourceFile.absolutePath,
                "-d", outputDir.absolutePath,
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                CompileResult("Kotlin-BSP-$version", null, null)
            } else {
                CompileResult(
                    "Kotlin-BSP-$version",
                    output.ifBlank { "CompilerRunner client exited with code $exitCode" },
                    null,
                )
            }
        } catch (e: Throwable) {
            CompileResult("Kotlin-BSP-$version", e.stackTraceToString(), null)
        }
    }

    override fun toString(): String = "Kotlin-BSP-$version"
}
