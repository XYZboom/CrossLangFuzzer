package com.github.xyzboom.codesmith

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.xyzboom.codesmith.RunMode.DifferentialTest
import com.github.xyzboom.codesmith.RunMode.GenerateIROnly
import com.github.xyzboom.codesmith.RunMode.IncrementalTest
import com.github.xyzboom.codesmith.RunMode.NormalTest
import com.github.xyzboom.codesmith.RunMode.ReduceOnly
import com.github.xyzboom.codesmith.config.RunConfig
import com.github.xyzboom.codesmith.data.DataRecorder
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.setMajorLanguage
import com.github.xyzboom.codesmith.minimize.MinimizeRunner2
import com.github.xyzboom.codesmith.mutator.IrMutator
import com.github.xyzboom.codesmith.mutator.MutatorLanguageSemantics
import com.github.xyzboom.codesmith.mutator.SemanticValidity
import com.github.xyzboom.codesmith.serde.configGson
import com.github.xyzboom.codesmith.serde.gson
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.measureTime

data class CompilerTarget(
    val stableName: String,
    val compiler: ICompiler,
    val majorLanguage: Language,
)

/**
 * Checks a known mutation oracle. [SemanticValidity.UNKNOWN] is a programming
 * error here: callers must discard that mutation before compiler submission.
 */
fun isMutationOracleSatisfied(
    semanticValidity: SemanticValidity,
    compileSuccess: Boolean,
): Boolean = when (semanticValidity) {
    SemanticValidity.VALID -> compileSuccess
    SemanticValidity.INVALID -> !compileSuccess
    SemanticValidity.UNKNOWN -> error("Unknown mutation validity must be discarded before compilation")
}

internal data class CompilationClassification(
    val oracleMismatch: Boolean,
    val differentialDisagreement: Boolean,
)

internal fun classifyCompilation(
    expected: SemanticValidity?,
    results: List<CompileResult>,
    compareDifferentially: Boolean,
): CompilationClassification {
    val oracleMismatch = if (expected != null) {
        results.any { result -> !isMutationOracleSatisfied(expected, result.success) }
    } else false
    return CompilationClassification(
        oracleMismatch = oracleMismatch,
        differentialDisagreement = compareDifferentially && results.map { it.success }.toSet().size > 1,
    )
}

internal class CompilationBudget(val maxInvocations: Int) {
    // Primary compiler-submission budget. A reservation is deliberately consumed
    // even if the compiler later throws; completedInvocations counts only returns.
    private val reserved = AtomicInteger(0)
    private val completed = AtomicInteger(0)
    val reservedInvocations: Int get() = reserved.get()
    val completedInvocations: Int get() = completed.get()

    init {
        require(maxInvocations >= 0) { "--max-rounds must be non-negative, but was $maxInvocations" }
    }

    fun hasCapacityFor(size: Int): Boolean {
        require(size > 0)
        return maxInvocations == 0 || reserved.get() + size <= maxInvocations
    }

    fun reserveCompleteGroup(size: Int): Boolean {
        require(size > 0)
        while (true) {
            val current = reserved.get()
            if (maxInvocations != 0 && current + size > maxInvocations) return false
            if (reserved.compareAndSet(current, current + size)) return true
        }
    }

    fun recordCompletedInvocation() {
        completed.incrementAndGet()
    }
}

abstract class CommonCompilerRunner(
    name: String? = null,
) : CliktCommand(name), ICompilerRunner {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        context {
            helpFormatter = { MordantHelpFormatter(it, showDefaultValues = true) }
        }
    }

    // must specify type arguments here. See https://youtrack.jetbrains.com/issue/KT-82511
    protected val runMode by run<OptionWithValues<RunMode, RunMode, RunMode>> {
        option("-m", "--mode")
            .enum<RunMode> {
                when (it) {
                    NormalTest -> "normal"
                    DifferentialTest -> "diff"
                    GenerateIROnly -> "ironly"
                    ReduceOnly -> "reduce"
                    IncrementalTest -> "incremental"
                }
            }
            .default(DifferentialTest, "diff")
            .help {
                val sb = StringBuilder("Run mode.\u0085")
                for (mode in RunMode.entries) {
                    val modeHelpString = when (mode) {
                        NormalTest -> "[normal]: Generate IR automatically. Test compiler(s) normally (not differentially).\u0085"
                        DifferentialTest -> "[diff]: Generate IR automatically. Test compiler(s) differentially.\u0085"
                        GenerateIROnly -> "[ironly]: Generate and save IR automatically only. Do no tests.\u0085"
                        ReduceOnly -> "[reduce]: Reduce the input IR only.\u0085"
                        IncrementalTest -> "[incremental]: Generate single-module mixed Kotlin/Java IR (does not accept --input/-i), run bounded source/signature/declaration/file/output mutations plus staged SCC schedules, and compare every state with a clean compilation.\u0085"
                    }
                    sb.append(modeHelpString)
                }
                sb.toString()
            }
    }
    private val inputIR by run<OptionWithValues<File?, File, File>> {
        option("-i", "--input")
            .file(
                mustExist = true,
                canBeFile = true,
                canBeDir = true,
                mustBeReadable = true,
            ).help(
                "Use input IR file instead of generated. If input is a directory, " +
                        "all the json files in it will be used as input IR files.",
            )
    }
    private val recursivelyInput by run<OptionWithValues<Boolean, Boolean, Boolean>> {
        option("--recursively-input", "-ri")
            .flag()
            .help("Recursively search for JSON files within the input folder")
    }
    protected val useCache by run<OptionWithValues<Boolean, Boolean, Boolean>> {
        option("--use-cache", "-u")
            .flag()
            .help("Use cached reduced IR file for reduce only mode if exists. Save cache also.")
    }
    protected val stopOnErrors by run<OptionWithValues<Boolean, Boolean, Boolean>> {
        option("-s", "--stop-on-errors")
            .flag()
            .help("Stop the runner when it finds a compiler bug or an incremental trajectory error.")
    }
    private val configFile by run<OptionWithValues<File, File, File>> {
        option("--config-file").file(
            mustExist = true,
            canBeFile = true,
            canBeDir = false,
            mustBeReadable = true,
        ).default(File("config/default.json"))
    }
    protected val maxRounds by option("--max-rounds")
        .int()
        .default(0)
    private val seedOption by option("--seed")
        .long()
        .help("Initial seed; each generated program uses this seed plus its zero-based program index.")

    protected lateinit var runConfig: RunConfig
    protected val campaignSeed: Long get() = initialSeed
    protected val outDir by run<OptionWithValues<File, File, File>> {
        option("-o", "--out").file(
            mustExist = false,
            canBeFile = false,
            canBeDir = true,
            mustBeReadable = false,
        ).default(File("out/data"))
    }
    protected val nonSimilarOutDir by run<OptionWithValues<File, File, File>> {
        option("-nso", "--non-similar-out").file(
            mustExist = false,
            canBeFile = false,
            canBeDir = true,
            mustBeReadable = false,
        ).default(File("out/min"))
    }
    protected val generateIROnlyOutDir by run<OptionWithValues<File, File, File>> {
        option("-iro", "--ir-out").file(
            mustExist = false,
            canBeFile = false,
            canBeDir = true,
            mustBeReadable = false,
        ).default(File("out/ir"))
    }
    protected val enableGED by run<OptionWithValues<Boolean, Boolean, Boolean>> {
        option("--enable-ged")
            .flag()
            .help("Enable gedlib for program similarity compare. DEVELOP ONLY!")
    }

    protected val inputIRFiles: Sequence<File>?
        get() {
            val input = inputIR ?: return null
            if (input.isFile) return sequenceOf(input)
            return (if (recursivelyInput) input.walk() else input.walk().maxDepth(1))
                .filter { it.isFile && it.extension.lowercase() == "json" }
        }

    protected abstract val runnerLanguage: Language
    protected abstract val mutatorLanguageSemantics: MutatorLanguageSemantics
    protected abstract fun normalTargets(): List<CompilerTarget>
    protected abstract fun differentialTargets(): List<CompilerTarget>

    protected open fun validateOptions() {}

    /** Kotlin Runner overrides this for the isolated incremental-vs-clean fuzzing mode. */
    protected open fun runIncrementalFuzzing() {
        error("Incremental fuzzing is not supported by this runner")
    }

    protected fun reserveExternalCompilerGroup(size: Int): Boolean = budget.reserveCompleteGroup(size)
    protected fun recordExternalCompilerInvocation() = budget.recordCompletedInvocation()
    protected fun reportExternalCompilerRound(duration: Duration) {
        println("${roundIndex.incrementAndGet()}:$duration\t\t")
    }
    protected fun recordExternalReport() = rawReportCount.incrementAndGet()

    protected open fun createGenerator(majorLanguage: Language = runnerLanguage): IrDeclGenerator =
        IrDeclGenerator(
            runConfig.generatorConfig,
            random = Random(currentProgramSeed ?: initialSeed),
            majorLanguage = majorLanguage,
        )

    protected open fun generateProgram(generator: IrDeclGenerator): IrProgram =
        generator.genProgram()

    private fun createSeededProgram(majorLanguage: Language = runnerLanguage): Pair<IrDeclGenerator, IrProgram> {
        val programIndex = generatedProgramCount
        currentProgramIndex = programIndex
        currentProgramSeed = Math.addExact(initialSeed, programIndex)
        val generator = createGenerator(majorLanguage)
        val program = generateProgram(generator)
        generatedProgramCount = Math.addExact(programIndex, 1L)
        return generator to program
    }

    private fun currentSeedMetadata(): Map<String, String> {
        val seed = currentProgramSeed ?: return emptyMap()
        val index = currentProgramIndex ?: return emptyMap()
        return mapOf(
            "seed.txt" to "initial_seed=$initialSeed\nprogram_index=$index\nprogram_seed=$seed\n",
        )
    }

    protected open fun prepareProgramForTarget(program: IrProgram, target: CompilerTarget) {
        program.setMajorLanguage(target.majorLanguage)
    }

    protected open fun mutateProgram(
        program: IrProgram,
        generator: IrDeclGenerator,
    ): Pair<SemanticValidity, String?>? = IrMutator(
        config = runConfig.mutatorConfig,
        generator = generator,
        languageSemantics = mutatorLanguageSemantics,
    ).mutate(program)

    var recorder = DataRecorder()
        private set
    val minimizeRunner = MinimizeRunner2(this)
    internal lateinit var budget: CompilationBudget
        private set
    // Lifecycle execution is single-threaded. The shutdown hook only reads the
    // atomic counters below and does not participate in stop coordination.
    private var stopRequested = false
    private val roundIndex = AtomicInteger(0)
    private val summaryReported = AtomicBoolean(false)
    private val rawReportCount = AtomicInteger(0)
    private val differentialReportCount = AtomicInteger(0)
    private val generatedIRCount = AtomicInteger(0)
    private var initialSeed = 0L
    private var generatedProgramCount = 0L
    private var currentProgramIndex: Long? = null
    private var currentProgramSeed: Long? = null
    internal var reportedSummary: String? = null
        private set

    private fun compilerForTarget(target: CompilerTarget): ICompiler = object : ICompiler {
        override fun compile(program: IrProgram): CompileResult {
            prepareProgramForTarget(program, target)
            return target.compiler.compile(program)
        }
    }

    private fun invokeCompilers(program: IrProgram, compilers: List<ICompiler>): List<CompileResult> =
        compilers.map { compiler ->
            recorder.recordCompiler(compiler).compile(program.deepCopy()).also {
                budget.recordCompletedInvocation()
            }
        }

    final override fun compile(program: IrProgram, compilers: List<ICompiler>): List<CompileResult> =
        invokeCompilers(program, compilers)

    private fun compileTargets(program: IrProgram, targets: List<CompilerTarget>): List<CompileResult>? {
        require(targets.isNotEmpty())
        if (!budget.reserveCompleteGroup(targets.size)) return null
        return invokeCompilers(program, targets.map(::compilerForTarget))
    }

    private fun minimizerCompilers(targets: List<CompilerTarget>): List<ICompiler> =
        targets.map(::compilerForTarget)

    private fun minimize(
        program: IrProgram,
        results: List<CompileResult>,
        targets: List<CompilerTarget>,
    ): Pair<IrProgram?, List<CompileResult>?> = try {
        val (minimized, minimizedResults) = minimizeRunner.minimize(
            program,
            results,
            minimizerCompilers(targets),
        )
        minimized to minimizedResults
    } catch (exception: Throwable) {
        logger.error(exception) { "Failed to minimize compiler result" }
        null to null
    }

    private fun preparedProgram(program: IrProgram, target: CompilerTarget): IrProgram =
        program.deepCopy().also { prepareProgramForTarget(it, target) }

    private fun recordNormalBaselineFailures(
        program: IrProgram,
        targets: List<CompilerTarget>,
        results: List<CompileResult>,
        metadata: Map<String, String> = emptyMap(),
    ): Boolean {
        var foundError = false
        for ((index, result) in results.withIndex()) {
            if (result.success) continue
            foundError = true
            val target = targets[index]
            val targetProgram = preparedProgram(program, target)
            val (minimized, minimizedResults) = minimize(targetProgram, listOf(result), listOf(target))
            val report = recordCompileResult(
                target.majorLanguage,
                targetProgram,
                listOf(result),
                minimized,
                minimizedResults,
                outDir = outDir,
                metadata = metadata,
            )
            if (report != null) rawReportCount.incrementAndGet()
        }
        return foundError
    }

    private fun recordDifferentialDisagreement(
        program: IrProgram,
        targets: List<CompilerTarget>,
        results: List<CompileResult>,
        metadata: Map<String, String> = emptyMap(),
        forceRecord: Boolean = false,
    ) {
        val (minimized, minimizedResults) = minimize(program, results, targets)
        val report = recordCompileResult(
            runnerLanguage,
            program,
            results,
            minimized,
            minimizedResults,
            outDir = nonSimilarOutDir,
            metadata = metadata,
            forceRecord = forceRecord,
        )
        if (report != null) differentialReportCount.incrementAndGet()
    }

    private fun baseline(
        program: IrProgram,
        targets: List<CompilerTarget>,
        differential: Boolean,
        metadata: Map<String, String> = emptyMap(),
    ): Boolean? {
        var succeeded: Boolean? = null
        val duration = measureTime {
            val results = compileTargets(program, targets) ?: return@measureTime
            val classification = classifyCompilation(null, results, differential)
            val foundError = if (differential) {
                if (classification.differentialDisagreement) {
                    recordDifferentialDisagreement(program, targets, results, metadata)
                    true
                } else false
            } else {
                recordNormalBaselineFailures(program, targets, results, metadata)
            }
            if (foundError && stopOnErrors) stopRequested = true
            succeeded = results.all { it.success }
        }
        if (succeeded != null) println("${roundIndex.incrementAndGet()}:$duration\t\t")
        return succeeded
    }

    private fun mutationMetadata(
        expected: SemanticValidity,
        targets: List<CompilerTarget>,
        results: List<CompileResult>,
        descriptions: List<String>,
    ): String = buildString {
        appendLine("expected=$expected")
        targets.zip(results).forEach { (target, result) ->
            appendLine("${target.stableName}.actual=${if (result.success) SemanticValidity.VALID else SemanticValidity.INVALID}")
        }
        if (descriptions.isNotEmpty()) {
            appendLine("mutations:")
            descriptions.forEach(::appendLine)
        }
    }

    private fun runMutationChain(
        baselineProgram: IrProgram,
        generator: IrDeclGenerator,
        targets: List<CompilerTarget>,
        differential: Boolean,
        programMetadata: Map<String, String>,
    ): Boolean {
        val mutatedProgram = baselineProgram.deepCopy()
        val descriptions = mutableListOf<String>()
        for (mutationIndex in 0 until runConfig.mutateTimes) {
            if (stopRequested) return true
            val mutation = mutateProgram(mutatedProgram, generator) ?: continue
            val (expected, description) = mutation
            if (description != null) descriptions += "[$mutationIndex] $description (expected=$expected)"
            if (expected == SemanticValidity.UNKNOWN) return true

            val variants = if (differential) max(1, runConfig.langShuffleTimesAfterMutate) else 1
            repeat(variants) { variantIndex ->
                var compiled = false
                val duration = measureTime {
                    val actualResults = compileTargets(mutatedProgram, targets) ?: return@measureTime
                    compiled = true
                    val classification = classifyCompilation(expected, actualResults, differential)
                    if (classification.oracleMismatch || classification.differentialDisagreement) {
                        val metadata = buildMap {
                            putAll(programMetadata)
                            if (classification.oracleMismatch) {
                                put("mutation-oracle.txt", mutationMetadata(expected, targets, actualResults, descriptions))
                                put("mutation-summary.txt", mutationMetadata(expected, targets, actualResults, descriptions))
                            }
                            if (classification.differentialDisagreement) {
                                put(
                                    "differential-disagreement.txt",
                                    targets.zip(actualResults).joinToString("\n") { (target, result) ->
                                        "${target.stableName}=${if (result.success) "SUCCESS" else "FAILURE"}"
                                    } + "\n",
                                )
                            }
                        }
                        if (differential && classification.differentialDisagreement) {
                            recordDifferentialDisagreement(
                                mutatedProgram,
                                targets,
                                actualResults,
                                metadata,
                                forceRecord = classification.oracleMismatch,
                            )
                        } else {
                            val (minimized, minimizedResults) = minimize(mutatedProgram, actualResults, targets)
                            val report = recordCompileResult(
                                runnerLanguage,
                                mutatedProgram,
                                actualResults,
                                minimized,
                                minimizedResults,
                                outDir = if (differential) nonSimilarOutDir else outDir,
                                metadata = metadata,
                                forceRecord = true,
                            )
                            if (report != null) {
                                if (differential) differentialReportCount.incrementAndGet()
                                else rawReportCount.incrementAndGet()
                            }
                        }
                        if (stopOnErrors) stopRequested = true
                    }
                }
                if (!compiled) return false
                if (differential) {
                    println("${roundIndex.incrementAndGet()}:$duration\t\t")
                } else {
                    println("${roundIndex.incrementAndGet()}:$duration\tmutation=$expected")
                }
                if (stopRequested) return true
                if (differential && variantIndex + 1 < variants) generator.shuffleLanguage(mutatedProgram)
            }
            if (expected == SemanticValidity.INVALID) return true
        }
        return true
    }

    private fun processGeneratedProgram(
        generator: IrDeclGenerator,
        program: IrProgram,
        targets: List<CompilerTarget>,
        differential: Boolean,
        metadata: Map<String, String>,
    ): Boolean {
        val baselineVariants = if (differential) max(1, runConfig.langShuffleTimesBeforeMutate) else 1
        var allBaselinesSucceeded = true
        repeat(baselineVariants) { variantIndex ->
            val succeeded = baseline(program, targets, differential, metadata) ?: return false
            if (!succeeded) allBaselinesSucceeded = false
            if (stopRequested) return true
            if (differential && variantIndex + 1 < baselineVariants) generator.shuffleLanguage(program)
        }
        if (allBaselinesSucceeded && runConfig.mutateTimes > 0) {
            return runMutationChain(program, generator, targets, differential, metadata)
        }
        return true
    }

    private fun runGenerated(targets: List<CompilerTarget>, differential: Boolean) {
        while (!stopRequested) {
            if (differential) {
                if (!budget.hasCapacityFor(targets.size)) return
                val (generator, program) = createSeededProgram()
                if (!processGeneratedProgram(generator, program, targets, differential = true, metadata = currentSeedMetadata())) return
            } else {
                for (target in targets) {
                    if (!budget.hasCapacityFor(1)) return
                    val (generator, program) = createSeededProgram(target.majorLanguage)
                    if (!processGeneratedProgram(generator, program, listOf(target), differential = false, metadata = currentSeedMetadata())) return
                    if (stopRequested) return
                }
            }
        }
    }

    private fun runGenerateIROnly() {
        var generated = 0
        while (!stopRequested) {
            if (maxRounds in 1..generated) break
            val duration = measureTime {
                val (_, program) = createSeededProgram()
                val outDir = File(generateIROnlyOutDir, System.nanoTime().toHexString()).mkdirsIfNotExists()
                File(outDir, "main.json").writer().use { gson.toJson(program, it) }
                File(outDir, "seed.txt").writeText(currentSeedMetadata().getValue("seed.txt"))
            }
            generated++
            generatedIRCount.incrementAndGet()
            println("${roundIndex.incrementAndGet()}:$duration\t\t")
        }
    }

    private fun reduce(program: IrProgram, targets: List<CompilerTarget>): IrProgram? {
        val results = compileTargets(program, targets) ?: return null
        recorder.addProgram("ori", program)
        var reduced: IrProgram? = null
        val reduceTime = measureTime {
            val minimized = try {
                minimizeRunner.minimize(program, results, minimizerCompilers(targets)).first
            } catch (exception: Exception) {
                logger.error(exception) { "Failed to reduce input IR" }
                null
            }
            reduced = minimized
        }
        if (reduced != null) {
            recorder.addProgram("reduced", reduced)
        }
        recorder.mergeData("reduceTime", reduceTime, Duration::plus)
        return reduced
    }

    private fun runOnInputIRFiles(files: Sequence<File>, targets: List<CompilerTarget>, differential: Boolean) {
        for (file in files) {
            if (stopRequested) break
            val program = file.reader().use { gson.fromJson(it, IrProgram::class.java) }
            when (runMode) {
                NormalTest -> {
                    for (target in targets) {
                        val ok = baseline(program, listOf(target), differential = false)
                        if (ok == null) return
                        if (stopRequested) break
                    }
                }
                DifferentialTest -> {
                    val ok = baseline(program, targets, differential = true)
                    if (ok == null) break
                }
                ReduceOnly -> {
                    var completed = false
                    val duration = measureTime {
                        val reducedFile = File(file.parentFile, file.name + ".reduced")
                        val reducedCache = if (useCache && reducedFile.exists()) {
                            reducedFile.reader().use { gson.fromJson(it, IrProgram::class.java) }
                        } else null
                        if (reducedCache != null) {
                            recorder.addProgram("ori", program)
                            recorder.addProgram("reduced", reducedCache)
                            completed = true
                        } else {
                            val reduced = reduce(program, targets)
                            if (reduced == null && budget.reservedInvocations >= budget.maxInvocations && budget.maxInvocations > 0) {
                                return@measureTime
                            }
                            if (useCache && reduced != null) {
                                reducedFile.writer().use { gson.toJson(reduced, it) }
                            }
                            completed = true
                        }
                    }
                    if (!completed) break
                    println("${roundIndex.incrementAndGet()}:$duration\t\t")
                }
                GenerateIROnly -> error("Using input IR file, cannot run GenerateIROnly mode.")
                IncrementalTest -> error("Incremental mode does not accept input IR.")
            }
            logStatistics()
        }
    }

    internal fun reportSummary(): String? {
        if (!summaryReported.compareAndSet(false, true)) return null
        val rawReports = rawReportCount.get()
        val nsoReports = differentialReportCount.get()
        val irReports = generatedIRCount.get()
        val msg = buildString {
            append("Report summary: completed rounds=${roundIndex.get()}, compiler invocations=${budget.completedInvocations}")
            if (rawReports > 0) append(", raw reports=$rawReports in ${outDir.path}")
            if (nsoReports > 0) append(", differential reports=$nsoReports in ${nonSimilarOutDir.path}")
            if (irReports > 0) append(", generated IR=$irReports in ${generateIROnlyOutDir.path}")
            if (rawReports == 0 && nsoReports == 0 && irReports == 0) append(", no bug reports generated")
        }
        reportedSummary = msg
        println(msg)
        logger.info { msg }
        return msg
    }

    private fun logStatistics() {
        logger.info { gson.toJson(recorder.programCount) }
        logger.info { gson.toJson(recorder.programData) }
        logger.info { recorder.getData<Duration>("reduceTime").toString() }
        logger.info { "compile times: ${recorder.getCompileTimes()}" }
    }

    final override fun run() {
        stopRequested = false
        recorder = DataRecorder()
        roundIndex.set(0)
        summaryReported.set(false)
        rawReportCount.set(0)
        differentialReportCount.set(0)
        generatedIRCount.set(0)
        generatedProgramCount = 0L
        currentProgramIndex = null
        currentProgramSeed = null
        reportedSummary = null
        runConfig = if (!configFile.exists()) RunConfig() else {
            configFile.reader().use { configGson.fromJson(it, RunConfig::class.java) }
        }
        initialSeed = seedOption ?: runConfig.seed
        budget = CompilationBudget(maxRounds)
        validateOptions()

        val shutdownHook = Thread {
            reportSummary()
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            val files = inputIRFiles
            require(!(files != null && runMode == GenerateIROnly)) {
                "Using input IR file, cannot run GenerateIROnly mode."
            }
            require(!(files == null && runMode == ReduceOnly)) {
                "No input IR file, cannot run ReduceOnly mode."
            }
            require(!(files != null && runMode == IncrementalTest)) {
                "Incremental mode currently generates new single-module mixed Kotlin/Java programs; input IR is not supported."
            }

            val targets = when (runMode) {
                NormalTest -> normalTargets()
                DifferentialTest, ReduceOnly -> differentialTargets()
                GenerateIROnly, IncrementalTest -> emptyList()
            }
            if (runMode != GenerateIROnly && runMode != IncrementalTest) {
                require(targets.isNotEmpty()) { "No compiler targets configured." }
            }

            if (files != null) {
                runOnInputIRFiles(files, targets, runMode == DifferentialTest)
            } else {
                when (runMode) {
                    NormalTest -> runGenerated(targets, differential = false)
                    DifferentialTest -> runGenerated(targets, differential = true)
                    GenerateIROnly -> runGenerateIROnly()
                    ReduceOnly -> error("No input IR file, cannot run ReduceOnly mode.")
                    IncrementalTest -> runIncrementalFuzzing()
                }
            }
            logStatistics()
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            } catch (_: IllegalStateException) {
                // JVM is already shutting down
            }
            reportSummary()
        }
    }
}
