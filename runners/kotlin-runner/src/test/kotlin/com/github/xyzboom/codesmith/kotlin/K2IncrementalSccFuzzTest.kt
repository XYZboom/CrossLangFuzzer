package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.createGeneratedMultiClassScc
import com.github.xyzboom.codesmith.ir.generatedSccTestProgram
import com.github.xyzboom.codesmith.ir.materialize
import com.github.xyzboom.codesmith.ir.materializeClassSnapshot
import com.github.xyzboom.codesmith.ir.sccIncrementalPartitionPlan
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.intellij.util.io.DataExternalizer
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.random.Random

class K2IncrementalSccFuzzTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `SCC atomic and refined parameter snapshots match clean K2 results`() {
        val generated = generatedSccTestProgram()
        val program = generated.program
        val generatedCycleClasses = createGeneratedMultiClassScc(program)
        val plan = program.sccIncrementalPartitionPlan(seed = generated.seed)
        assertTrue(plan.components.any { component -> component.map { it.name }.containsAll(generatedCycleClasses) })
        val emptyProgram = program.deepCopy().apply { modules.single().classes.clear() }

        val atomicPrograms = listOf(emptyProgram) + plan.snapshots(plan.sccBatches).map { classes ->
            program.materializeClassSnapshot(classes.map { it.name })
        }
        val atomic = runTrajectory("scc-atomic", atomicPrograms)
        assertTrue(atomic.clean.all { it.success }, "Dependency-first SCC snapshots should compile: ${atomic.clean}")
        assertTrue(atomic.differences.isEmpty(), "SCC-atomic mismatches:\n${atomic.differences.joinToString("\n")}")
        assertTrue(atomic.hasSelectiveIncrementalCompile, "SCC-atomic path did not prove selective recompilation")

        val refinedPrograms = listOf(emptyProgram) + plan.refinedSnapshots.map { it.materialize(program) }
        val refined = runTrajectory("scc-refined", refinedPrograms)
        assertTrue(refined.clean.first().success, "Empty S0 should compile: ${refined.clean.first()}")
        assertTrue(refined.clean.last().success, "Complete final program should compile: ${refined.clean.last()}")
        assertTrue(refined.clean.any { !it.success }, "The refined sequence must include an invalid intermediate state")
        assertTrue(
            refined.clean.drop(1).dropLast(1).any { it.success },
            "The refined sequence must include a legal intermediate state",
        )
        assertTrue(
            refined.clean.indices.filter { !refined.clean[it].success }.any { index ->
                when (val edit = plan.refinedSnapshots[index - 1].edit) {
                    is com.github.xyzboom.codesmith.ir.SccIncrementalEdit.AddScc -> false
                    is com.github.xyzboom.codesmith.ir.SccIncrementalEdit.TruncateFunctionParameters -> true
                    is com.github.xyzboom.codesmith.ir.SccIncrementalEdit.AddFunctionParameter ->
                        edit.parameterCount < program.classes.single { it.name == edit.className }
                            .functions[edit.functionIndex].parameterList.parameters.size
                }
            },
            "An invalid state must be caused by an incomplete parameter-level edit",
        )
        assertTrue(
            plan.refinedEdits.any { it is com.github.xyzboom.codesmith.ir.SccIncrementalEdit.AddFunctionParameter },
            "The plan did not exercise function-parameter granularity",
        )
        assertTrue(refined.hasSelectiveIncrementalCompile, "Refined path did not prove selective recompilation")
        assertTrue(refined.differences.isEmpty(), "Refined mismatches:\n${refined.differences.joinToString("\n")}")
    }

    @Test
    fun `two seeded generated single-module K2 programs match clean results`() {
        val config = GeneratorConfig(
            javaRatio = 0.0f,
            topLevelDeclRange = 3..3,
            classHasSuperProbability = 0.35f,
            classMemberNumRange = 1..2,
            functionParameterNumRange = 0..1,
            functionExpressionNumRange = 0..1,
            classHasTypeParameterProbability = 0.0f,
            functionHasTypeParameterProbability = 0.0f,
            enableUseSiteVariance = false,
            moduleNumRange = 1..1,
        )
        for (seed in listOf(20261025, 20261026)) {
            val program = IrDeclGenerator(config, Random(seed), Language.KOTLIN).genProgram()
            assertSingleModuleKotlin(program, seed)
            val plan = program.sccIncrementalPartitionPlan(seed)
            val emptyProgram = program.deepCopy().apply { modules.single().classes.clear() }
            val atomicPrograms = listOf(emptyProgram) + plan.snapshots(plan.sccBatches).map { classes ->
                program.materializeClassSnapshot(classes.map { it.name })
            }
            val refinedPrograms = listOf(emptyProgram) + plan.refinedSnapshots.map { it.materialize(program) }
            val atomic = runTrajectory("generated-$seed-atomic", atomicPrograms)
            val refined = runTrajectory("generated-$seed-refined", refinedPrograms)
            println(
                "K2_SCC_PILOT seed=$seed classes=${program.classes.map { it.name }} " +
                    "components=${plan.components.map { component -> component.map { it.name } }} " +
                    "atomicStates=${atomic.clean.size} refinedStates=${refined.clean.size} " +
                    "compilerSubmissions=${2 * (atomic.clean.size + refined.clean.size)}",
            )
            assertTrue(atomic.differences.isEmpty(), "Seed $seed atomic mismatches: ${atomic.differences}")
            assertTrue(refined.differences.isEmpty(), "Seed $seed refined mismatches: ${refined.differences}")
            assertTrue(atomic.hasSelectiveIncrementalCompile, "Seed $seed atomic path was not selective")
            assertTrue(refined.hasSelectiveIncrementalCompile, "Seed $seed refined path was not selective")
        }
    }

    private fun assertSingleModuleKotlin(program: IrProgram, seed: Int) {
        assertTrue(program.modules.size == 1, "Seed $seed generated ${program.modules.size} modules")
        assertTrue(program.classes.all { it.language == Language.KOTLIN }, "Seed $seed generated a non-Kotlin class")
    }

    private fun runTrajectory(label: String, snapshots: List<IrProgram>): TrajectoryResult {
        val incrementalRoot = tempDir.resolve(label).resolve("incremental")
        val sourceRoot = incrementalRoot.resolve("src")
        val outputRoot = incrementalRoot.resolve("classes")
        val cacheRoot = incrementalRoot.resolve("cache")
        val classpathSnapshotRoot = incrementalRoot.resolve("classpath-snapshot")
        val differences = mutableListOf<String>()
        val cleanObservations = mutableListOf<CompileObservation>()
        val incrementalObservations = mutableListOf<CompileObservation>()

        snapshots.forEachIndexed { index, snapshotProgram ->
            val sources = IrProgramPrinter().print(snapshotProgram)
            val incrementalSourceFiles = writeSources(sourceRoot, sources)
            val incremental = compile(
                sourceRoot, outputRoot, cacheRoot, classpathSnapshotRoot, incrementalSourceFiles,
            )
            val cleanRoot = tempDir.resolve("$label-clean-$index")
            val cleanSourceFiles = writeSources(cleanRoot.resolve("src"), sources)
            val clean = compile(
                cleanRoot.resolve("src"),
                cleanRoot.resolve("classes"),
                cleanRoot.resolve("cache"),
                cleanRoot.resolve("classpath-snapshot"),
                cleanSourceFiles,
            )
            incrementalObservations += incremental
            cleanObservations += clean
            val difference = compare(index, incremental, clean)
            if (difference.isNotEmpty()) differences += difference
            println(
                "K2_SCC_TRAJECTORY=$label index=$index classes=${snapshotProgram.classes.map { it.name }.sorted()} " +
                    "clean=${clean.summary()} incremental=${incremental.summary()} difference=$difference",
            )
        }
        val hasSelectiveCompile = incrementalObservations.any { observation ->
            observation.iterations.any {
                it.incremental && it.sourceFiles.isNotEmpty() && it.sourceFiles.size < observation.sourceFileCount
            }
        }
        return TrajectoryResult(cleanObservations, differences, hasSelectiveCompile)
    }

    private fun compare(index: Int, incremental: CompileObservation, clean: CompileObservation): String {
        val differences = mutableListOf<String>()
        if (incremental.success != clean.success) differences += "success differs"
        if (incremental.exitCode != clean.exitCode) differences += "exitCode ${incremental.exitCode} != ${clean.exitCode}"
        if (incremental.thrownClass != clean.thrownClass) differences += "thrown ${incremental.thrownClass} != ${clean.thrownClass}"
        if (incremental.thrownClass != null && clean.thrownClass != null &&
            incremental.thrownText?.lineSequence()?.firstOrNull() != clean.thrownText?.lineSequence()?.firstOrNull()
        ) {
            differences += "thrown messages differ"
        }
        if (!incremental.success && !clean.success &&
            incremental.normalizedErrorDiagnostics != clean.normalizedErrorDiagnostics
        ) {
            differences += "errors differ inc=${incremental.normalizedErrorDiagnostics} clean=${clean.normalizedErrorDiagnostics}"
        }
        if (incremental.success && clean.success && incremental.classHashes != clean.classHashes) {
            val paths = (incremental.classHashes.keys + clean.classHashes.keys).toSortedSet()
            differences += "class outputs differ " + paths.filter {
                incremental.classHashes[it] != clean.classHashes[it]
            }.joinToString()
        }
        return if (differences.isEmpty()) "" else "step $index: ${differences.joinToString("; ")}"
    }

    private fun writeSources(root: Path, sources: Map<String, String>): List<File> {
        Files.createDirectories(root)
        sources.forEach { (relativePath, content) ->
            val file = root.resolve(relativePath)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        return sources.keys.sorted().map { root.resolve(it).toFile() }
    }

    private fun compile(
        sourceRoot: Path,
        outputDir: Path,
        cacheDir: Path,
        classpathSnapshotDir: Path,
        sourceFiles: List<File>,
    ): CompileObservation {
        Files.createDirectories(outputDir)
        Files.createDirectories(cacheDir)
        Files.createDirectories(classpathSnapshotDir)
        val reporter = CapturingIcReporter(sourceRoot)
        val collector = MessageCollectorImpl()
        var exitCode: ExitCode? = null
        var thrownClass: String? = null
        var thrownText: String? = null
        try {
            val runner = IncrementalJvmCompilerRunner(
                workingDir = cacheDir.toFile(),
                reporter = BuildReporter(reporter, DoNothingBuildMetricsReporter),
                outputDirs = null,
                classpathChanges = emptyClasspathChanges(classpathSnapshotDir.toFile()),
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
                moduleName = "k2_scc_incremental"
                destination = outputDir.toAbsolutePath().toString()
                classpath = compileClasspath
                languageVersion = LanguageVersion.LATEST_STABLE.versionString
                noStdlib = true
                javaSourceRoots = arrayOf(sourceRoot.toAbsolutePath().toString())
            }
            exitCode = withIncrementalCompilation(args) {
                runner.compile(
                    allSourceFiles = sourceFiles,
                    args = args,
                    messageCollector = collector,
                    changedFiles = ChangedFiles.DeterminableFiles.ToBeComputed,
                )
            }
        } catch (failure: Throwable) {
            thrownClass = failure::class.qualifiedName
            thrownText = failure.stackTraceToString()
        }
        return CompileObservation(
            exitCode = exitCode,
            thrownClass = thrownClass,
            thrownText = thrownText,
            diagnostics = collector.messages.map { message ->
                val sourcePath = sourceRoot.toAbsolutePath().toString()
                val location = message.location?.path?.replace(sourcePath, "<SRC>")
                "${message.severity}|${message.message.replace(sourcePath, "<SRC>")}|$location"
            },
            sourceFileCount = sourceFiles.count { it.extension == "kt" },
            iterations = reporter.iterations.toList(),
            classHashes = classHashes(outputDir),
        )
    }

    private fun emptyClasspathChanges(classpathSnapshotDir: File): ClasspathChanges {
        val snapshotFiles = ClasspathSnapshotFiles(emptyList(), classpathSnapshotDir)
        if (!snapshotFiles.shrunkPreviousClasspathSnapshotFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val externalizer = Class.forName(
                "org.jetbrains.kotlin.incremental.classpathDiff.AccessibleClassSnapshotExternalizer",
            ).getField("INSTANCE").get(null) as DataExternalizer<Any>
            ListExternalizer(externalizer).saveToFile(snapshotFiles.shrunkPreviousClasspathSnapshotFile, emptyList<Any>())
        }
        return NoChanges(snapshotFiles)
    }

    private fun classHashes(outputDir: Path): Map<String, String> {
        val result = sortedMapOf<String, String>()
        if (!Files.exists(outputDir)) return result
        Files.walk(outputDir).use { files ->
            files.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                .forEach { file ->
                    val relative = outputDir.relativize(file).toString().replace(File.separatorChar, '/')
                    result[relative] = MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(file))
                        .joinToString("") { byte -> "%02x".format(byte) }
                }
        }
        return result
    }

    private data class TrajectoryResult(
        val clean: List<CompileObservation>,
        val differences: List<String>,
        val hasSelectiveIncrementalCompile: Boolean,
    )

    private data class CompileObservation(
        val exitCode: ExitCode?,
        val thrownClass: String?,
        val thrownText: String?,
        val diagnostics: List<String>,
        val sourceFileCount: Int,
        val iterations: List<CompileIteration>,
        val classHashes: Map<String, String>,
    ) {
        val success: Boolean get() = exitCode == ExitCode.OK && thrownClass == null
        val normalizedErrorDiagnostics: List<String> get() = diagnostics.filter {
            it.substringBefore('|') in setOf("ERROR", "EXCEPTION")
        }.sorted()
        fun summary() = "success=$success exit=$exitCode thrown=$thrownClass errors=$normalizedErrorDiagnostics " +
            "sourceCount=$sourceFileCount iterations=${iterations.map { it.incremental to it.sourceFiles }} " +
            "classes=${classHashes.keys}"
    }

    private data class CompileIteration(
        val incremental: Boolean,
        val sourceFiles: List<String>,
        val exitCode: ExitCode,
    )

    private class CapturingIcReporter(private val sourceRoot: Path) : ICReporterBase() {
        val iterations = mutableListOf<CompileIteration>()
        override fun report(message: () -> String, severity: ICReporter.ReportSeverity) = Unit
        override fun reportCompileIteration(incremental: Boolean, sourceFiles: Collection<File>, exitCode: ExitCode) {
            iterations += CompileIteration(
                incremental,
                sourceFiles.map { file -> sourceRoot.toFile().toPath().relativize(file.toPath()).toString() }.sorted(),
                exitCode,
            )
        }
    }
}
