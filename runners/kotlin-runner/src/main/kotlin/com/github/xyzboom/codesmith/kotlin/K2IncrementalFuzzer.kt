package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.materialize
import com.github.xyzboom.codesmith.ir.materializeClassSnapshot
import com.github.xyzboom.codesmith.ir.sccIncrementalPartitionPlan
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.serde.gson
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
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.measureTime

/** Runs seeded, single-module Kotlin source snapshots through retained-state K2 IC and clean controls. */
internal class K2IncrementalFuzzer(
    private val generatorConfig: GeneratorConfig,
    private val baseSeed: Long,
    private val maxCompilerSubmissions: Int,
    private val scheduleRepeatCount: Int,
    private val outputDirectory: File,
    private val reserveCompilerGroup: (Int) -> Boolean,
    private val recordCompilerReturn: () -> Unit,
    private val reportRound: (kotlin.time.Duration) -> Unit,
    private val reportMismatch: () -> Unit,
    private val sccStageCount: Int = 3,
    private val stateChangeSteps: Int = 0,
    private val incrementalCompilesPerRound: Int = 4,
) {
    private val printer = IrProgramPrinter()
    private var completedPairs = 0
    private var completedTestRounds = 0
    private var mismatchCount = 0
    private var programCount = 0
    private var snapshotCount = 0
    private var selectiveRecompileProven = false
    private var sccValidityPairCount = 0
    private var sccValidityCleanSuccesses = 0
    private var parameterStressPairCount = 0
    private var parameterStressCleanSuccesses = 0
    private var stateChangePairCount = 0
    private var stateChangeMutationCount = 0
    private val stateChangeOperatorCounts = sortedMapOf<String, Int>()

    fun run() {
        require(scheduleRepeatCount > 0) { "--schedule-repeats must be positive" }
        require(sccStageCount > 0) { "--scc-stage-count must be positive" }
        require(stateChangeSteps >= 0) { "--state-change-steps cannot be negative" }
        require(incrementalCompilesPerRound > 1) { "--incremental-compiles-per-round must be at least 2" }
        require(maxCompilerSubmissions == 0 || maxCompilerSubmissions >= 2 * incrementalCompilesPerRound) {
            "Incremental mode needs at least ${2 * incrementalCompilesPerRound} --max-rounds submissions for one complete test round."
        }
        val minClassCount = maxOf(2, generatorConfig.topLevelDeclRange.first)
        val maxClassCount = maxOf(minClassCount, generatorConfig.topLevelDeclRange.last)
        val effectiveConfig = generatorConfig.copy(
            javaRatio = 0.5f,
            moduleNumRange = 1..1,
            topLevelDeclRange = minClassCount..maxClassCount,
        )
        val campaign = File(outputDirectory, "incremental").resolve("run-${System.currentTimeMillis()}-seed-$baseSeed")
        campaign.mkdirs()
        println("K2_INCREMENTAL_OUTPUT=${campaign.path}")
        println(
            "K2_INCREMENTAL_CONFIG=single-module mixed Kotlin/Java; seed=$baseSeed; " +
                "maxCompilerSubmissions=$maxCompilerSubmissions; scheduleRepeats=$scheduleRepeatCount; " +
                "sccStageCount=$sccStageCount; stateChangeSteps=$stateChangeSteps; " +
                "incrementalCompilesPerRound=$incrementalCompilesPerRound",
        )

        var programIndex = 0L
        while (maxCompilerSubmissions == 0 || completedPairs * 2 < maxCompilerSubmissions) {
            val seed = Math.addExact(baseSeed, programIndex)
            val program = IrDeclGenerator(effectiveConfig, Random(seed), Language.KOTLIN).genProgram()
            require(program.modules.size == 1) { "Incremental generator produced ${program.modules.size} modules" }
            ensureMixedLanguages(program)
            val caseDirectory = campaign.resolve("program-$programIndex-seed-$seed")
            caseDirectory.mkdirs()
            Files.writeString(caseDirectory.toPath().resolve("program.json"), gson.toJson(program))
            Files.writeString(caseDirectory.toPath().resolve("seed.txt"), "base_seed=$baseSeed\nprogram_index=$programIndex\nprogram_seed=$seed\n")
            programCount++
            val kotlinClassCount = program.classes.count { it.language == Language.KOTLIN }
            val javaClassCount = program.classes.count { it.language == Language.JAVA }
            println(
                "K2_INCREMENTAL_PROGRAM index=$programIndex seed=$seed classes=${program.classes.size} " +
                    "kotlinClasses=$kotlinClassCount javaClasses=$javaClassCount",
            )

            val emptyProgram = program.materializeClassSnapshot(emptyList())
            for (repeatIndex in 0 until scheduleRepeatCount) {
                val scheduleSeed = seed + (repeatIndex + 1L) * 1_000_003L
                val plan = program.sccIncrementalPartitionPlan(scheduleSeed.hashCode())
                val sccStageBatches = plan.sccStages(sccStageCount)
                val repeatDirectory = caseDirectory.toPath()
                    .resolve("repeats/repeat-${repeatIndex.toString().padStart(3, '0')}")
                Files.createDirectories(repeatDirectory)
                Files.writeString(
                    repeatDirectory.resolve("schedule.txt"),
                    "generation_seed=$seed\nschedule_repeat=$repeatIndex\nschedule_seed=$scheduleSeed\n" +
                        "scc_component_order=${plan.components.map { component -> component.map { it.name } }}\n" +
                        "scc_stage_count_requested=$sccStageCount\nscc_stage_count_actual=${sccStageBatches.size}\n" +
                        "scc_stage_components=${sccStageBatches.map { stage -> stage.map { it.componentIndex } }}\n" +
                        "scc_validity_snapshots=${sccStageBatches.size + 1}\n" +
                        "parameter_stress_snapshots=${plan.refinedSnapshots.size + 1}\n",
                )
                val sccSnapshots = buildList {
                    add(emptyProgram)
                    val accumulatedStageBatches = mutableListOf<com.github.xyzboom.codesmith.ir.SccClassBatch>()
                    sccStageBatches.forEach { stageBatches ->
                        accumulatedStageBatches += stageBatches
                        val includedClassNames = accumulatedStageBatches.flatMap { it.classes }.map { it.name }
                        add(program.materializeClassSnapshot(includedClassNames))
                    }
                }
                val sccStages = listOf("EMPTY_BASELINE") + sccStageBatches.mapIndexed { stageIndex, stageBatches ->
                    val componentIndexes = stageBatches.map { it.componentIndex }
                    val classNames = stageBatches.flatMap { it.classes }.map { it.name }
                    "ADD_SCC_PART:${stageIndex + 1}/${sccStageBatches.size}:" +
                        "components=$componentIndexes:classes=$classNames"
                }
                val parameterSnapshots = listOf(emptyProgram) + plan.refinedSnapshots.map { it.materialize(program) }
                val parameterStages = listOf("EMPTY_BASELINE") + plan.refinedSnapshots.map { snapshot ->
                    editDescription(snapshot.edit)
                }
                println(
                    "K2_INCREMENTAL_SCHEDULE program=$programIndex generationSeed=$seed repeat=$repeatIndex " +
                        "scheduleSeed=$scheduleSeed sccParts=${sccStageBatches.size} " +
                        "sccValiditySnapshots=${sccSnapshots.size} " +
                        "parameterStressSnapshots=${parameterSnapshots.size} " +
                        "stateChangeSteps=$stateChangeSteps",
                )
                if (stateChangeSteps > 0 && !runTrajectory(
                        caseDirectory = repeatDirectory,
                        programIndex = programIndex,
                        generationSeed = seed,
                        repeatIndex = repeatIndex,
                        scheduleSeed = scheduleSeed,
                        trajectoryName = "state-changes",
                        snapshots = stateMutationSnapshots(
                            program = program,
                            scheduleSeed = scheduleSeed,
                            requestedSteps = stateChangeSteps,
                            incrementalOutput = repeatDirectory.resolve("state-changes/incremental/classes"),
                        ),
                    )
                ) return
                if (!runTrajectory(
                        caseDirectory = repeatDirectory,
                        programIndex = programIndex,
                        generationSeed = seed,
                        repeatIndex = repeatIndex,
                        scheduleSeed = scheduleSeed,
                        trajectoryName = "scc-validity",
                        snapshots = sccSnapshots.mapIndexed { index, snapshot ->
                            K2IncrementalSnapshot(snapshot, sccStages[index])
                        }.asSequence(),
                    )
                ) return
                if (!runTrajectory(
                        caseDirectory = repeatDirectory,
                        programIndex = programIndex,
                        generationSeed = seed,
                        repeatIndex = repeatIndex,
                        scheduleSeed = scheduleSeed,
                        trajectoryName = "parameter-stress",
                        snapshots = parameterSnapshots.mapIndexed { index, snapshot ->
                            K2IncrementalSnapshot(snapshot, parameterStages[index])
                        }.asSequence(),
                    )
                ) return
            }
            programIndex++
        }
        printSummary()
    }

    private fun runTrajectory(
        caseDirectory: Path,
        programIndex: Long,
        generationSeed: Long,
        repeatIndex: Int,
        scheduleSeed: Long,
        trajectoryName: String,
        snapshots: Sequence<K2IncrementalSnapshot>,
    ): Boolean {
        val trajectoryDirectory = caseDirectory.resolve(trajectoryName)
        Files.createDirectories(trajectoryDirectory)
        val incrementalRoot = trajectoryDirectory.resolve("incremental")
        val incrementalSourceRoot = incrementalRoot.resolve("src")
        val incrementalOutput = incrementalRoot.resolve("classes")
        val incrementalCache = incrementalRoot.resolve("cache")
        val incrementalClasspathSnapshots = incrementalRoot.resolve("classpath-snapshots")
        Files.createDirectories(incrementalSourceRoot)

        var roundPairCount = 0
        var roundDuration = Duration.ZERO
        for ((roundIndex, roundSnapshots) in snapshots.chunked(incrementalCompilesPerRound).withIndex()) {
            if (roundSnapshots.size != incrementalCompilesPerRound) {
                println(
                    "K2_INCREMENTAL_INCOMPLETE_ROUND_SKIPPED program=$programIndex repeat=$repeatIndex " +
                        "trajectory=$trajectoryName snapshots=${roundSnapshots.size}/$incrementalCompilesPerRound",
                )
                break
            }
            val roundSubmissions = 2 * incrementalCompilesPerRound
            if (!reserveCompilerGroup(roundSubmissions)) {
                println(
                    "K2_INCREMENTAL_BUDGET_EXHAUSTED after $completedTestRounds complete test rounds " +
                        "and $completedPairs snapshot comparisons",
                )
                printSummary()
                return false
            }
            println(
                "K2_INCREMENTAL_TEST_ROUND_START index=$completedTestRounds program=$programIndex " +
                    "repeat=$repeatIndex trajectory=$trajectoryName incrementalCompiles=$incrementalCompilesPerRound",
            )
            for ((roundStep, snapshot) in roundSnapshots.withIndex()) {
                val snapshotIndex = roundIndex * incrementalCompilesPerRound + roundStep
                var snapshotMismatch = false
                val duration = measureTime {
                val logicalSources = printer.print(snapshot.program)
                val sources = applySourceLayout(logicalSources, snapshot.sourceLayout)
                val snapshotDirectory = trajectoryDirectory.resolve("snapshots/${snapshotIndex.toString().padStart(4, '0')}")
                val sourceArchive = snapshotDirectory.resolve("sources")
                writeSources(sourceArchive, sources)
                Files.writeString(snapshotDirectory.resolve("ir.json"), gson.toJson(snapshot.program))
                Files.writeString(snapshotDirectory.resolve("stage.txt"), snapshot.description + "\n")
                Files.writeString(
                    snapshotDirectory.resolve("round.txt"),
                    listOf(
                        "test_round_index=$completedTestRounds",
                        "round_step=${roundPairCount + 1}/$incrementalCompilesPerRound",
                    ).joinToString(System.lineSeparator(), postfix = System.lineSeparator()),
                )
                snapshot.outputAction?.let { action ->
                    val relativePath = Path.of(action.deleteRelativePath).normalize()
                    require(!relativePath.isAbsolute && !relativePath.startsWith("..")) {
                        "Unsafe incremental output path: ${action.deleteRelativePath}"
                    }
                    Files.deleteIfExists(incrementalOutput.resolve(relativePath))
                    Files.writeString(snapshotDirectory.resolve("output-action.txt"), "DELETE ${relativePath.toString().replace(File.separatorChar, '/')}\n")
                }
                val incrementalFiles = syncSources(incrementalSourceRoot, sources)
                val incremental = compile(
                    incrementalSourceRoot,
                    incrementalOutput,
                    incrementalCache,
                    incrementalClasspathSnapshots,
                    incrementalFiles,
                    forceAllKotlinSourcesModified = snapshot.outputAction != null,
                )
                if (incremental.compilerReturned) recordCompilerReturn()
                writeJson(snapshotDirectory.resolve("incremental-result.json"), incremental)
                val kotlinSourceCount = sources.keys.count { it.endsWith(".kt") }
                val selective = snapshotIndex > 0 && incremental.iterations.any {
                    it.incremental && it.sourceFiles.isNotEmpty() && it.sourceFiles.size < kotlinSourceCount
                }
                selectiveRecompileProven = selectiveRecompileProven || selective

                val cleanRoot = snapshotDirectory.resolve("clean")
                val cleanSourceRoot = cleanRoot.resolve("src")
                val cleanFiles = writeSources(cleanSourceRoot, sources)
                val clean = compile(
                    cleanSourceRoot,
                    cleanRoot.resolve("classes"),
                    cleanRoot.resolve("cache"),
                    cleanRoot.resolve("classpath-snapshots"),
                    cleanFiles,
                )
                if (clean.compilerReturned) recordCompilerReturn()
                writeJson(snapshotDirectory.resolve("clean-result.json"), clean)

                val bothSuccessful = isSuccessful(incremental) && isSuccessful(clean)
                val binaryComparison = if (bothSuccessful) {
                    compareClassBinaries(incrementalOutput, cleanRoot.resolve("classes"))
                } else {
                    ClassBinaryComparison(compared = false, classFileCount = 0, equal = null, differingClassFiles = emptyList())
                }
                writeJson(snapshotDirectory.resolve("binary-comparison.json"), binaryComparison)
                val differences = compare(incremental, clean, binaryComparison)
                if (differences.isNotEmpty()) {
                    mismatchCount++
                    reportMismatch()
                    snapshotMismatch = true
                    Files.writeString(snapshotDirectory.resolve("mismatch.txt"), differences.joinToString("\n", postfix = "\n"))
                    println(
                        "K2_INCREMENTAL_MISMATCH program=$programIndex repeat=$repeatIndex " +
                            "trajectory=$trajectoryName snapshot=$snapshotIndex ${differences.joinToString("; ")}",
                    )
                }
                val cleanSuccess = clean.exitCode == ExitCode.OK.name && clean.thrownClass == null
                if (trajectoryName == "scc-validity" && cleanSuccess) sccValidityCleanSuccesses++
                if (trajectoryName == "parameter-stress" && cleanSuccess) parameterStressCleanSuccesses++
                if (trajectoryName == "state-changes") {
                    stateChangePairCount++
                    snapshot.operator?.let { operator ->
                        stateChangeMutationCount++
                        stateChangeOperatorCounts[operator] = (stateChangeOperatorCounts[operator] ?: 0) + 1
                    }
                }
                snapshotCount++
                println(
                    "K2_INCREMENTAL_PAIR program=$programIndex generationSeed=$generationSeed repeat=$repeatIndex " +
                        "scheduleSeed=$scheduleSeed trajectory=$trajectoryName snapshot=$snapshotIndex " +
                        "testRound=$completedTestRounds roundStep=${roundPairCount + 1}/$incrementalCompilesPerRound " +
                        "stage=${snapshot.description} incremental=${incremental.exitCode ?: incremental.thrownClass} " +
                        "incrementalKotlin=${incremental.kotlinExitCode} incrementalJava=${incremental.javaExitCode} " +
                        "clean=${clean.exitCode ?: clean.thrownClass} cleanKotlin=${clean.kotlinExitCode} " +
                        "cleanJava=${clean.javaExitCode} cleanSuccess=$cleanSuccess selective=$selective " +
                        "mismatch=${differences.isNotEmpty()}",
                )
            }
            completedPairs++
            roundPairCount++
            roundDuration += duration
            if (trajectoryName == "scc-validity") sccValidityPairCount++
            if (trajectoryName == "parameter-stress") parameterStressPairCount++
            if (snapshotMismatch) {
                println(
                    "K2_INCREMENTAL_TEST_ROUND_INTERRUPTED index=$completedTestRounds " +
                        "completedSteps=$roundPairCount/$incrementalCompilesPerRound",
                )
                printSummary()
                return false
            }
            if (roundPairCount == incrementalCompilesPerRound) {
                println(
                    "K2_INCREMENTAL_TEST_ROUND_COMPLETE index=$completedTestRounds " +
                        "program=$programIndex repeat=$repeatIndex trajectory=$trajectoryName " +
                        "incrementalCompiles=$roundPairCount compilerSubmissions=${2 * roundPairCount}",
                )
                completedTestRounds++
                reportRound(roundDuration)
                roundPairCount = 0
                roundDuration = Duration.ZERO
            }
        }
        }
        return true
    }

    private fun stateMutationSnapshots(
        program: com.github.xyzboom.codesmith.ir.IrProgram,
        scheduleSeed: Long,
        requestedSteps: Int,
        incrementalOutput: Path,
    ): Sequence<K2IncrementalSnapshot> = sequence {
        var state = K2IncrementalFuzzState(program.deepCopy())
        val scheduler = K2IncrementalStateMutationScheduler(scheduleSeed.hashCode())
        yield(K2IncrementalSnapshot(state.program, "FULL_PROGRAM_BASELINE", state.sourceLayout))
        for (stepIndex in 0 until requestedSteps) {
            val logicalSources = printer.print(state.program)
            val outputFiles = classFilePaths(incrementalOutput).keys
            val mutation = scheduler.next(state, logicalSources, outputFiles, stepIndex) ?: break
            state = mutation.state
            yield(
                K2IncrementalSnapshot(
                    program = state.program,
                    description = "${mutation.operator}:${mutation.description}",
                    sourceLayout = state.sourceLayout,
                    outputAction = mutation.outputAction,
                    operator = mutation.operator,
                ),
            )
        }
    }

    private fun editDescription(edit: com.github.xyzboom.codesmith.ir.SccIncrementalEdit): String = when (edit) {
        is com.github.xyzboom.codesmith.ir.SccIncrementalEdit.AddScc ->
            "ADD_COMPLETE_SCC:${edit.componentIndex}:${edit.classes.map { it.name }}"
        is com.github.xyzboom.codesmith.ir.SccIncrementalEdit.TruncateFunctionParameters ->
            "TRUNCATE_PARAMETERS:${edit.componentIndex}:${edit.className}:${edit.functionIndex}:${edit.parameterCount}"
        is com.github.xyzboom.codesmith.ir.SccIncrementalEdit.AddFunctionParameter ->
            "ADD_PARAMETER:${edit.componentIndex}:${edit.className}:${edit.functionIndex}:${edit.parameterCount}"
    }

    private fun ensureMixedLanguages(program: com.github.xyzboom.codesmith.ir.IrProgram) {
        val classes = program.classes
        require(classes.size >= 2) { "Mixed Kotlin/Java fuzzing needs at least two generated classes" }
        if (classes.none { it.language == Language.KOTLIN }) {
            setClassLanguage(classes.first(), Language.KOTLIN)
        }
        if (classes.none { it.language == Language.JAVA }) {
            setClassLanguage(classes.last { it.language == Language.KOTLIN }, Language.JAVA)
        }
        require(classes.any { it.language == Language.KOTLIN } && classes.any { it.language == Language.JAVA }) {
            "Generated program did not contain both Kotlin and Java classes after language assignment"
        }
    }

    private fun setClassLanguage(
        clazz: com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration,
        language: Language,
    ) {
        clazz.language = language
        clazz.functions.forEach { it.language = language }
    }

    private fun compile(
        sourceRoot: Path,
        outputDirectory: Path,
        cacheDirectory: Path,
        classpathSnapshotDirectory: Path,
        sourceFiles: List<File>,
        forceAllKotlinSourcesModified: Boolean = false,
    ): CompileObservation {
        val kotlinOutputDirectory = outputDirectory.resolve("kotlin")
        val javaOutputDirectory = outputDirectory.resolve("java")
        Files.createDirectories(kotlinOutputDirectory)
        Files.createDirectories(javaOutputDirectory)
        Files.createDirectories(cacheDirectory)
        Files.createDirectories(classpathSnapshotDirectory)
        val reporter = CapturingIcReporter(sourceRoot)
        val collector = MessageCollectorImpl()
        val javaSourceFiles = sourceFiles.filter { it.extension == "java" }
        var kotlinExitCode: ExitCode? = null
        var thrownClass: String? = null
        var thrownText: String? = null
        var compilerReturned = false
        try {
            val runtimeClasspath = listOf(ForTestCompileRuntime.runtimeJarForTests(), jetbrainsAnnotationsJar())
                .joinToString(File.pathSeparator) { it.absolutePath }
            val args = K2JVMCompilerArguments().apply {
                moduleName = "codesmith_incremental"
                destination = kotlinOutputDirectory.toAbsolutePath().toString()
                classpath = runtimeClasspath
                languageVersion = LanguageVersion.LATEST_STABLE.versionString
                noStdlib = true
                javaSourceRoots = arrayOf(sourceRoot.toAbsolutePath().toString())
            }
            val snapshotFiles = ClasspathSnapshotFiles(emptyList(), classpathSnapshotDirectory.toFile())
            val classpathChanges = emptyClasspathChanges(snapshotFiles)
            val runner = IncrementalJvmCompilerRunner(
                workingDir = cacheDirectory.toFile(),
                reporter = BuildReporter(reporter, DoNothingBuildMetricsReporter),
                outputDirs = null,
                classpathChanges = classpathChanges,
                icFeatures = IncrementalCompilationFeatures(withAbiSnapshot = false, usePreciseJavaTracking = false),
            )
            kotlinExitCode = withIncrementalCompilation(args) {
                runner.compile(
                    allSourceFiles = sourceFiles,
                    args = args,
                    messageCollector = collector,
                    changedFiles = incrementalChangedFiles(
                        forceAllKotlinSourcesModified,
                        sourceFiles.filter { it.extension == "kt" },
                    ),
                )
            }
            compilerReturned = true
        } catch (failure: Throwable) {
            thrownClass = failure::class.qualifiedName
            thrownText = failure.stackTraceToString()
        }

        var javaExitCode: String? = null
        var javaDiagnostics = emptyList<String>()
        var javaThrownClass: String? = null
        var javaThrownText: String? = null
        if (kotlinExitCode == ExitCode.OK && thrownClass == null && javaSourceFiles.isNotEmpty()) {
            try {
                val javaResult = compileJava(
                    sourceRoot = sourceRoot,
                    javaOutputDirectory = javaOutputDirectory,
                    kotlinOutputDirectory = kotlinOutputDirectory,
                    sourceFiles = javaSourceFiles,
                )
                javaExitCode = if (javaResult.success) "OK" else "COMPILATION_ERROR"
                javaDiagnostics = javaResult.diagnostics
            } catch (failure: Throwable) {
                javaExitCode = "EXCEPTION"
                javaThrownClass = failure::class.qualifiedName
                javaThrownText = failure.stackTraceToString()
            }
        }

        val sourcePath = sourceRoot.toAbsolutePath().toString()
        val kotlinDiagnostics = collector.messages.map { message ->
            val path = message.location?.path?.replace(sourcePath, "<SRC>")
            "${message.severity}|${message.message.replace(sourcePath, "<SRC>")}|$path"
        }
        val overallExitCode = when {
            kotlinExitCode == null -> null
            kotlinExitCode != ExitCode.OK -> kotlinExitCode.name
            javaThrownClass != null -> "JAVA_COMPILATION_EXCEPTION"
            javaExitCode == "COMPILATION_ERROR" -> "JAVA_COMPILATION_ERROR"
            else -> ExitCode.OK.name
        }
        val allDiagnostics = kotlinDiagnostics + javaDiagnostics +
            listOfNotNull(javaThrownClass?.let { "EXCEPTION|Java compiler threw $it|<SRC>" })
        val errorDiagnostics = errorDiagnosticsFrom(allDiagnostics)
        return CompileObservation(
            exitCode = overallExitCode,
            kotlinExitCode = kotlinExitCode?.name,
            javaExitCode = javaExitCode,
            thrownClass = thrownClass ?: javaThrownClass,
            thrownText = thrownText ?: javaThrownText,
            compilerReturned = compilerReturned,
            forcedAllKotlinSourcesModified = forceAllKotlinSourcesModified,
            diagnostics = allDiagnostics,
            errorDiagnostics = errorDiagnostics,
            javaDiagnostics = javaDiagnostics,
            iterations = reporter.iterations.toList(),
            classHashes = classHashes(outputDirectory),
        )
    }

    private fun compileJava(
        sourceRoot: Path,
        javaOutputDirectory: Path,
        kotlinOutputDirectory: Path,
        sourceFiles: List<File>,
    ): JavaCompileObservation {
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("No system Java compiler is available; run the Kotlin incremental fuzzer on a JDK")
        Files.createDirectories(javaOutputDirectory)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val runtimeClasspath = listOf(
            kotlinOutputDirectory.toAbsolutePath().toString(),
            ForTestCompileRuntime.runtimeJarForTests().absolutePath,
            jetbrainsAnnotationsJar().absolutePath,
        ).joinToString(File.pathSeparator)
        val options = listOf(
            "-classpath", runtimeClasspath,
            "-d", javaOutputDirectory.toAbsolutePath().toString(),
        )
        val success = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8).use { manager ->
            val task = compiler.getTask(
                null,
                manager,
                diagnostics,
                options,
                null,
                manager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.call() == true
        }
        val root = sourceRoot.toAbsolutePath().toString()
        val messages = diagnostics.diagnostics.map { diagnostic ->
            val source = diagnostic.source?.name?.replace(root, "<SRC>")
            val line = if (diagnostic.lineNumber > 0) ":${diagnostic.lineNumber}" else ""
            "${diagnostic.kind}|${diagnostic.getMessage(Locale.ROOT).replace(root, "<SRC>")}|$source$line"
        }
        return JavaCompileObservation(success, messages)
    }

    private fun emptyClasspathChanges(snapshotFiles: ClasspathSnapshotFiles): ClasspathChanges {
        if (!snapshotFiles.shrunkPreviousClasspathSnapshotFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val externalizer = Class.forName(
                "org.jetbrains.kotlin.incremental.classpathDiff.AccessibleClassSnapshotExternalizer",
            ).getField("INSTANCE").get(null) as DataExternalizer<Any>
            ListExternalizer(externalizer).saveToFile(snapshotFiles.shrunkPreviousClasspathSnapshotFile, emptyList<Any>())
        }
        return NoChanges(snapshotFiles)
    }

    private fun syncSources(root: Path, sources: Map<String, String>): List<File> {
        Files.createDirectories(root)
        val expectedPaths = sources.keys.map { relativePath ->
            val normalized = Path.of(relativePath).normalize()
            require(!normalized.isAbsolute && !normalized.startsWith("..")) { "Unsafe generated source path: $relativePath" }
            normalized.toString().replace(File.separatorChar, '/')
        }.toSet()
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { existing ->
                val relativePath = root.relativize(existing).toString().replace(File.separatorChar, '/')
                if (relativePath !in expectedPaths) Files.deleteIfExists(existing)
            }
        }
        return writeSources(root, sources)
    }

    private fun writeSources(root: Path, sources: Map<String, String>): List<File> {
        Files.createDirectories(root)
        sources.forEach { (relativePath, source) ->
            val path = Path.of(relativePath).normalize()
            require(!path.isAbsolute && !path.startsWith("..")) { "Unsafe generated source path: $relativePath" }
            val destination = root.resolve(path)
            Files.createDirectories(destination.parent)
            Files.writeString(destination, source)
        }
        return sources.keys.sorted().map { root.resolve(it).toAbsolutePath().toFile() }
    }

    private fun writeJson(path: Path, value: Any) {
        Files.writeString(path, gson.toJson(value))
    }

    private fun classHashes(outputDirectory: Path): Map<String, String> {
        val hashes = sortedMapOf<String, String>()
        if (!Files.exists(outputDirectory)) return hashes
        Files.walk(outputDirectory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                .forEach { file ->
                    val relative = outputDirectory.relativize(file).toString().replace(File.separatorChar, '/')
                    hashes[relative] = MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(file)).joinToString("") { "%02x".format(it) }
                }
        }
        return hashes
    }

    private fun compareClassBinaries(incrementalOutput: Path, cleanOutput: Path): ClassBinaryComparison {
        val incrementalFiles = classFilePaths(incrementalOutput)
        val cleanFiles = classFilePaths(cleanOutput)
        val allNames = (incrementalFiles.keys + cleanFiles.keys).toSortedSet()
        val differentFiles = allNames.filter { name ->
            val incrementalFile = incrementalFiles[name]
            val cleanFile = cleanFiles[name]
            incrementalFile == null || cleanFile == null || Files.mismatch(incrementalFile, cleanFile) != -1L
        }
        return ClassBinaryComparison(
            compared = true,
            classFileCount = allNames.size,
            equal = differentFiles.isEmpty(),
            differingClassFiles = differentFiles,
        )
    }

    private fun classFilePaths(outputDirectory: Path): Map<String, Path> {
        val files = sortedMapOf<String, Path>()
        if (!Files.exists(outputDirectory)) return files
        Files.walk(outputDirectory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                .forEach { file ->
                    val relative = outputDirectory.relativize(file).toString().replace(File.separatorChar, '/')
                    files[relative] = file
                }
        }
        return files
    }

    private fun isSuccessful(observation: CompileObservation): Boolean =
        observation.exitCode == ExitCode.OK.name && observation.thrownClass == null

    private fun compare(
        incremental: CompileObservation,
        clean: CompileObservation,
        binaryComparison: ClassBinaryComparison,
    ): List<String> {
        val incSuccess = isSuccessful(incremental)
        val cleanSuccess = isSuccessful(clean)
        return incrementalCleanMismatchReasons(
            incrementalSuccess = incSuccess,
            cleanSuccess = cleanSuccess,
            incrementalClassHashes = incremental.classHashes,
            cleanClassHashes = clean.classHashes,
            binaryOutputsEqual = binaryComparison.equal,
        )
    }

    private fun printSummary() {
        println(
            "K2_INCREMENTAL_SUMMARY programs=$programCount scheduleRepeats=$scheduleRepeatCount " +
                "testRounds=$completedTestRounds incrementalCompilesPerRound=$incrementalCompilesPerRound " +
                "snapshots=$snapshotCount pairedComparisons=$completedPairs " +
                "sccValidityPairs=$sccValidityPairCount sccValidityCleanSuccesses=$sccValidityCleanSuccesses " +
                "parameterStressPairs=$parameterStressPairCount parameterStressCleanSuccesses=$parameterStressCleanSuccesses " +
                "stateChangePairs=$stateChangePairCount stateChangeMutations=$stateChangeMutationCount " +
                "stateChangeOperators=$stateChangeOperatorCounts " +
                "compilerMismatches=$mismatchCount selectiveRecompileProven=$selectiveRecompileProven",
        )
    }

    private data class K2IncrementalSnapshot(
        val program: com.github.xyzboom.codesmith.ir.IrProgram,
        val description: String,
        val sourceLayout: Map<String, String> = emptyMap(),
        val outputAction: K2IncrementalOutputAction? = null,
        val operator: String? = null,
    )

    private data class CompileObservation(
        val exitCode: String?,
        val kotlinExitCode: String?,
        val javaExitCode: String?,
        val thrownClass: String?,
        val thrownText: String?,
        val compilerReturned: Boolean,
        val forcedAllKotlinSourcesModified: Boolean,
        val diagnostics: List<String>,
        val errorDiagnostics: List<String>,
        val javaDiagnostics: List<String>,
        val iterations: List<CompileIteration>,
        val classHashes: Map<String, String>,
    )

    private data class JavaCompileObservation(
        val success: Boolean,
        val diagnostics: List<String>,
    )

    private data class ClassBinaryComparison(
        val compared: Boolean,
        val classFileCount: Int,
        val equal: Boolean?,
        val differingClassFiles: List<String>,
    )

    private data class CompileIteration(
        val incremental: Boolean,
        val sourceFiles: List<String>,
        val exitCode: String,
    )

    private class CapturingIcReporter(sourceRoot: Path) : ICReporterBase() {
        private val normalizedSourceRoot = sourceRoot.toAbsolutePath().normalize()
        val iterations = mutableListOf<CompileIteration>()
        override fun report(message: () -> String, severity: ICReporter.ReportSeverity) = Unit
        override fun reportCompileIteration(incremental: Boolean, sourceFiles: Collection<File>, exitCode: ExitCode) {
            iterations += CompileIteration(
                incremental,
                sourceFiles.map { normalizedSourceRoot.relativize(it.toPath().toAbsolutePath().normalize()).toString() }.sorted(),
                exitCode.name,
            )
        }
    }
}

internal fun incrementalCleanMismatchReasons(
    incrementalSuccess: Boolean,
    cleanSuccess: Boolean,
    incrementalClassHashes: Map<String, String>,
    cleanClassHashes: Map<String, String>,
    binaryOutputsEqual: Boolean?,
): List<String> {
    if (incrementalSuccess != cleanSuccess) return listOf("compile success differs")
    if (!incrementalSuccess) return emptyList()
    if (incrementalClassHashes != cleanClassHashes || binaryOutputsEqual != true) {
        return listOf("successful class outputs differ")
    }
    return emptyList()
}

internal fun incrementalChangedFiles(
    forceAllKotlinSourcesModified: Boolean,
    currentKotlinSources: List<File>,
): ChangedFiles =
    if (forceAllKotlinSourcesModified) {
        // The direct runner has no build-tool output tracker. Recompile all current Kotlin sources
        // to restore deleted outputs while keeping the incremental history usable on later snapshots.
        ChangedFiles.DeterminableFiles.Known(modified = currentKotlinSources, removed = emptyList())
    } else {
        ChangedFiles.DeterminableFiles.ToBeComputed
    }

internal fun errorDiagnosticsFrom(diagnostics: List<String>): List<String> =
    diagnostics.filter { it.startsWith("ERROR|") || it.startsWith("EXCEPTION|") }
