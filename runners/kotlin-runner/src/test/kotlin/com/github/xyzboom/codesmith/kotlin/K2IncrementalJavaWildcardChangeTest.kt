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
import org.jetbrains.kotlin.incremental.storage.ListExternalizer
import org.jetbrains.kotlin.incremental.storage.saveToFile
import org.jetbrains.kotlin.incremental.withIncrementalCompilation
import com.intellij.util.io.DataExternalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Reduced replay of the mixed Java/Kotlin mismatch from campaign batch 06. */
class K2IncrementalJavaWildcardChangeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `both paths reject the Java wildcard mutation without a differential mismatch`() {
        val incrementalRoot = tempDir.resolve("incremental")
        val incrementalSources = incrementalRoot.resolve("src")
        val incrementalOutput = incrementalRoot.resolve("classes")
        val incrementalCache = incrementalRoot.resolve("cache")
        val incrementalSnapshot = incrementalRoot.resolve("classpath-snapshot")

        writeSources(incrementalSources, javaBridgeSource("Box<Object, Object>"))
        val initial = compile(incrementalSources, incrementalOutput, incrementalCache, incrementalSnapshot)
        assertEquals(ExitCode.OK, initial.exitCode, "baseline: $initial")

        writeSources(incrementalSources, javaBridgeSource("Box<?, Object>"))
        val incremental = compile(incrementalSources, incrementalOutput, incrementalCache, incrementalSnapshot)

        val cleanRoot = tempDir.resolve("clean")
        val cleanSources = cleanRoot.resolve("src")
        writeSources(cleanSources, javaBridgeSource("Box<?, Object>"))
        val clean = compile(
            cleanSources,
            cleanRoot.resolve("classes"),
            cleanRoot.resolve("cache"),
            cleanRoot.resolve("classpath-snapshot"),
        )

        println("REDUCED_JAVA_CHANGE initial=$initial incremental=$incremental clean=$clean")
        assertTrue(incremental.iterations.any { it.incremental }, "No incremental K2 iteration: $incremental")
        assertEquals(ExitCode.COMPILATION_ERROR, incremental.exitCode, "incremental S1: $incremental")
        assertEquals(ExitCode.COMPILATION_ERROR, clean.exitCode, "clean S1: $clean")
        assertTrue(
            incrementalCleanMismatchReasons(
                incrementalSuccess = false,
                cleanSuccess = false,
                incrementalClassHashes = emptyMap(),
                cleanClassHashes = emptyMap(),
                binaryOutputsEqual = null,
            ).isEmpty(),
            "Both compile errors must be provisionally treated as no differential mismatch",
        )
    }

    private fun writeSources(root: Path, javaBridge: String) {
        Files.createDirectories(root)
        Files.writeString(root.resolve("Box.java"), "public class Box<A, B> {}\n")
        Files.writeString(
            root.resolve("Api.kt"),
            "interface Api<A : Any, B : Any> { fun clash(value: Box<A, A>) }\n",
        )
        Files.writeString(root.resolve("JavaBridge.java"), javaBridge)
        Files.writeString(
            root.resolve("Impl.kt"),
            "class Impl<T : Any> : JavaBridge<T> { override fun clash(value: Box<Any, Any>) {} }\n",
        )
    }

    private fun javaBridgeSource(parameterType: String): String =
        """
        public interface JavaBridge<T extends Object> extends Api<Object, T> {
            @Override default void clash($parameterType value) {}
        }
        """.trimIndent() + "\n"

    private fun compile(sourceRoot: Path, output: Path, cache: Path, classpathSnapshot: Path): Result {
        Files.createDirectories(output)
        Files.createDirectories(cache)
        Files.createDirectories(classpathSnapshot)
        val reporter = CapturingReporter(sourceRoot)
        val snapshotFiles = ClasspathSnapshotFiles(emptyList(), classpathSnapshot.toFile())
        if (!snapshotFiles.shrunkPreviousClasspathSnapshotFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val externalizer = Class.forName(
                "org.jetbrains.kotlin.incremental.classpathDiff.AccessibleClassSnapshotExternalizer",
            ).getField("INSTANCE").get(null) as DataExternalizer<Any>
            ListExternalizer(externalizer).saveToFile(snapshotFiles.shrunkPreviousClasspathSnapshotFile, emptyList<Any>())
        }
        val runner = IncrementalJvmCompilerRunner(
            workingDir = cache.toFile(),
            reporter = BuildReporter(reporter, DoNothingBuildMetricsReporter),
            outputDirs = null,
            classpathChanges = NoChanges(snapshotFiles),
            icFeatures = IncrementalCompilationFeatures(withAbiSnapshot = false, usePreciseJavaTracking = false),
        )
        val sourceFiles = Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().substringAfterLast('.') in setOf("kt", "java") }
                .map { it.toFile() }
                .toList()
                .sortedBy { it.absolutePath }
        }
        val args = K2JVMCompilerArguments().apply {
            moduleName = "reduced_incremental_mismatch"
            destination = output.toAbsolutePath().toString()
            classpath = listOf(ForTestCompileRuntime.runtimeJarForTests(), jetbrainsAnnotationsJar())
                .joinToString(File.pathSeparator) { it.absolutePath }
            languageVersion = LanguageVersion.LATEST_STABLE.versionString
            noStdlib = true
            javaSourceRoots = arrayOf(sourceRoot.toAbsolutePath().toString())
        }
        val exitCode = withIncrementalCompilation(args) {
            runner.compile(
                allSourceFiles = sourceFiles,
                args = args,
                messageCollector = MessageCollectorImpl(),
                changedFiles = ChangedFiles.DeterminableFiles.ToBeComputed,
            )
        }
        return Result(exitCode, reporter.iterations.toList())
    }

    private data class Result(val exitCode: ExitCode, val iterations: List<Iteration>)

    private data class Iteration(val incremental: Boolean, val sourceFiles: List<String>, val exitCode: ExitCode)

    private class CapturingReporter(sourceRoot: Path) : ICReporterBase() {
        private val root = sourceRoot.toAbsolutePath().normalize()
        val iterations = mutableListOf<Iteration>()
        override fun report(message: () -> String, severity: ICReporter.ReportSeverity) = Unit
        override fun reportCompileIteration(incremental: Boolean, sourceFiles: Collection<File>, exitCode: ExitCode) {
            iterations += Iteration(
                incremental,
                sourceFiles.map { root.relativize(it.toPath().toAbsolutePath().normalize()).toString() }.sorted(),
                exitCode,
            )
        }
    }
}
