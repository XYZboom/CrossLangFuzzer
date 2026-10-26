package com.github.xyzboom.codesmith.kotlin

import org.jetbrains.kotlin.build.report.BuildReporter
import org.jetbrains.kotlin.build.report.ICReporter
import org.jetbrains.kotlin.build.report.ICReporterBase
import org.jetbrains.kotlin.build.report.metrics.DoNothingBuildMetricsReporter
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollectorImpl
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.incremental.ChangedFiles
import org.jetbrains.kotlin.incremental.ClasspathChanges
import org.jetbrains.kotlin.incremental.ClasspathChanges.ClasspathSnapshotEnabled.IncrementalRun.NoChanges
import org.jetbrains.kotlin.incremental.ClasspathSnapshotFiles
import org.jetbrains.kotlin.incremental.IncrementalCompilationFeatures
import org.jetbrains.kotlin.incremental.IncrementalJvmCompilerRunner
import com.intellij.util.io.DataExternalizer
import org.jetbrains.kotlin.incremental.storage.ListExternalizer
import org.jetbrains.kotlin.incremental.storage.saveToFile
import org.jetbrains.kotlin.incremental.withIncrementalCompilation
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Direct single-module K2 probe using the compiler artifact's incremental runner API. */
class K2IncrementalCompilerApiProbeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `retained incremental state is compared with a clean K2 compile of identical S1`() {
        val incrementalRoot = tempDir.resolve("incremental")
        val incrementalSources = incrementalRoot.resolve("src")
        val incrementalOutput = incrementalRoot.resolve("classes")
        val incrementalCache = incrementalRoot.resolve("cache")
        val classpathSnapshotDir = incrementalRoot.resolve("classpath-snapshot")

        writeSources(incrementalSources, retryS0)
        val initial = compile(
            sourceRoot = incrementalSources.toFile(),
            outputDir = incrementalOutput.toFile(),
            cacheDir = incrementalCache.toFile(),
            classpathSnapshotDir = classpathSnapshotDir.toFile(),
        )
        assertEquals(ExitCode.OK, initial.exitCode, initial.toString())
        assertTrue(initial.iterations.isNotEmpty(), "Initial compilation was not reported")
        assertTrue(
            initial.iterations.any { it.sourceFiles.containsAll(listOf("IOSupplier.kt", "Retry.kt")) },
            "Initial build did not compile the complete module: ${initial.iterations}",
        )

        writeSources(incrementalSources, retryS1)
        val incremental = compile(
            sourceRoot = incrementalSources.toFile(),
            outputDir = incrementalOutput.toFile(),
            cacheDir = incrementalCache.toFile(),
            classpathSnapshotDir = classpathSnapshotDir.toFile(),
        )
        assertEquals(ExitCode.OK, incremental.exitCode, incremental.toString())
        val selectiveIncrementalIteration = incremental.iterations.firstOrNull {
            it.incremental && "Retry.kt" in it.sourceFiles && "IOSupplier.kt" !in it.sourceFiles
        }
        assertTrue(
            selectiveIncrementalIteration != null,
            "The reporter did not confirm a selective incremental compile of Retry.kt: ${incremental.iterations}",
        )

        val cleanRoot = tempDir.resolve("clean")
        val cleanSources = cleanRoot.resolve("src")
        val cleanOutput = cleanRoot.resolve("classes")
        writeSources(cleanSources, retryS1)
        assertEquals(
            Files.readString(incrementalSources.resolve("repro/Retry.kt")),
            Files.readString(cleanSources.resolve("repro/Retry.kt")),
            "Incremental and clean source states differ",
        )
        assertEquals(
            Files.readString(incrementalSources.resolve("repro/IOSupplier.kt")),
            Files.readString(cleanSources.resolve("repro/IOSupplier.kt")),
            "Incremental and clean source states differ",
        )

        val clean = compile(
            sourceRoot = cleanSources.toFile(),
            outputDir = cleanOutput.toFile(),
            cacheDir = cleanRoot.resolve("cache").toFile(),
            classpathSnapshotDir = cleanRoot.resolve("classpath-snapshot").toFile(),
        )
        assertEquals(ExitCode.OK, clean.exitCode, clean.toString())
        assertTrue(clean.iterations.isNotEmpty(), "Clean compilation was not reported")
        assertTrue(
            clean.iterations.any { it.sourceFiles.containsAll(listOf("IOSupplier.kt", "Retry.kt")) },
            "Fresh clean build did not compile the complete final module: ${clean.iterations}",
        )

        val incrementalExceptions = exceptionsByMethod(incrementalOutput)
        val cleanExceptions = exceptionsByMethod(cleanOutput)
        val lostIOExceptionAttributes = (incrementalExceptions.keys + cleanExceptions.keys)
            .filter { method ->
                method.contains("#invoke") &&
                    "java/io/IOException" in cleanExceptions[method].orEmpty() &&
                    "java/io/IOException" !in incrementalExceptions[method].orEmpty()
            }
        println("K2_IC_ITERATIONS initial=${initial.iterations} incremental=${incremental.iterations} clean=${clean.iterations}")
        println(
            "K2_IC_EXCEPTIONS_DIFF=" + lostIOExceptionAttributes.joinToString { method ->
                "$method clean=${cleanExceptions[method]} incremental=${incrementalExceptions[method]}"
            },
        )
        assertTrue(
            lostIOExceptionAttributes.isNotEmpty(),
            "Expected the known KT-89698 Exceptions-attribute discrepancy; " +
                "incremental=${incrementalExceptions.filterValues { it.isNotEmpty() }}, " +
                "clean=${cleanExceptions.filterValues { it.isNotEmpty() }}",
        )
    }

    private fun compile(
        sourceRoot: File,
        outputDir: File,
        cacheDir: File,
        classpathSnapshotDir: File,
    ): CompileObservation {
        outputDir.mkdirs()
        cacheDir.mkdirs()
        classpathSnapshotDir.mkdirs()
        val reporter = CapturingIcReporter()
        val buildReporter = BuildReporter(reporter, DoNothingBuildMetricsReporter)
        val classpathChanges = emptyClasspathChanges(classpathSnapshotDir)
        val runner = IncrementalJvmCompilerRunner(
            workingDir = cacheDir,
            reporter = buildReporter,
            outputDirs = null,
            classpathChanges = classpathChanges,
            icFeatures = IncrementalCompilationFeatures(
                withAbiSnapshot = false,
                usePreciseJavaTracking = false,
            ),
        )
        val compileClasspath = listOf(
            ForTestCompileRuntime.runtimeJarForTests(),
            jetbrainsAnnotationsJar(),
        ).joinToString(File.pathSeparator) { it.absolutePath }
        val args = K2JVMCompilerArguments().apply {
            moduleName = "k2_incremental_probe"
            destination = outputDir.absolutePath
            classpath = compileClasspath
            languageVersion = LanguageVersion.LATEST_STABLE.versionString
            noStdlib = true
            javaSourceRoots = arrayOf(sourceRoot.absolutePath)
        }
        val sourceFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .sortedBy { it.absolutePath }
            .toList()
        val exitCode = withIncrementalCompilation(args) {
            runner.compile(
                allSourceFiles = sourceFiles,
                args = args,
                messageCollector = MessageCollectorImpl(),
                changedFiles = ChangedFiles.DeterminableFiles.ToBeComputed,
            )
        }
        return CompileObservation(exitCode, reporter.iterations.toList())
    }

    private fun emptyClasspathChanges(classpathSnapshotDir: File): ClasspathChanges {
        val snapshotFiles = ClasspathSnapshotFiles(emptyList(), classpathSnapshotDir)
        if (!snapshotFiles.shrunkPreviousClasspathSnapshotFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val snapshotExternalizer = Class.forName(
                "org.jetbrains.kotlin.incremental.classpathDiff.AccessibleClassSnapshotExternalizer",
            ).getField("INSTANCE").get(null) as DataExternalizer<Any>
            ListExternalizer(snapshotExternalizer).saveToFile(
                snapshotFiles.shrunkPreviousClasspathSnapshotFile,
                emptyList<Any>(),
            )
        }
        return NoChanges(snapshotFiles)
    }

    private fun writeSources(sourceRoot: Path, retrySource: String) {
        val packageDir = Files.createDirectories(sourceRoot.resolve("repro"))
        Files.writeString(packageDir.resolve("IOSupplier.kt"), ioSupplierSource)
        Files.writeString(packageDir.resolve("Retry.kt"), retrySource)
    }

    private fun exceptionsByMethod(outputDir: Path): Map<String, List<String>> {
        val result = sortedMapOf<String, List<String>>()
        Files.walk(outputDir).use { files ->
            files.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                .forEach { classFile ->
                    val reader = ClassReader(Files.readAllBytes(classFile))
                    val owner = reader.className
                    reader.accept(
                        object : ClassVisitor(Opcodes.ASM9) {
                            override fun visitMethod(
                                access: Int,
                                name: String,
                                descriptor: String,
                                signature: String?,
                                exceptions: Array<out String>?,
                            ): MethodVisitor? {
                                result["$owner#$name$descriptor"] = exceptions?.toList().orEmpty()
                                return null
                            }
                        },
                        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
                    )
                }
        }
        return result
    }

    private data class CompileObservation(
        val exitCode: ExitCode,
        val iterations: List<CompileIteration>,
    ) {
        override fun toString(): String = "exitCode=$exitCode, iterations=$iterations"
    }

    private data class CompileIteration(
        val incremental: Boolean,
        val sourceFiles: List<String>,
        val exitCode: ExitCode,
    )

    private class CapturingIcReporter : ICReporterBase() {
        val iterations = mutableListOf<CompileIteration>()

        override fun report(message: () -> String, severity: ICReporter.ReportSeverity) = Unit

        override fun reportCompileIteration(
            incremental: Boolean,
            sourceFiles: Collection<File>,
            exitCode: ExitCode,
        ) {
            iterations += CompileIteration(incremental, sourceFiles.map(File::getName).sorted(), exitCode)
        }
    }

    private companion object {
        val ioSupplierSource = """
            package repro

            import java.io.IOException

            fun interface IOSupplier<T> {
                @Throws(IOException::class)
                operator fun invoke(): T
            }
        """.trimIndent()

        val retryS0 = """
            package repro

            inline fun retry(): Unit = consume(IOSupplier { Unit })

            fun <T> consume(operation: IOSupplier<T>): T = operation()
        """.trimIndent()

        val retryS1 = """
            package repro

            inline fun retry(): Unit = consume(IOSupplier { Unit })

            fun <T> consume(operation: IOSupplier<T>): T = operation()

            // S1: comment-only edit to trigger incremental compilation.
        """.trimIndent()
    }
}
