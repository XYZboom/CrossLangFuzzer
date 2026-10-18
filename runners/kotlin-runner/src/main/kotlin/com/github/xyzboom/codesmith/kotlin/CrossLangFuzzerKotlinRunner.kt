package com.github.xyzboom.codesmith.kotlin

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.RunMode
import com.github.xyzboom.codesmith.data.DataRecorder
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.minimize.MinimizeRunner2
import com.github.xyzboom.codesmith.mutator.IrMutator
import com.github.xyzboom.codesmith.mutator.SemanticValidity
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.recordCompileResult
import com.github.xyzboom.codesmith.serde.gson
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirPsiBlackBoxCodegenTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.measureTime

val testInfo = run {
    // make sure the JDK used by the active compiler matrix is available
    KtTestUtil.getJdk17Home()
    KotlinTestInfo("CrossLangFuzzerKotlinRunner", "main", emptySet())
}

class CrossLangFuzzerKotlinRunner : CommonCompilerRunner("kotlin") {
    companion object {
        private val logger = KotlinLogging.logger {}
        fun TestConfigurationBuilder.config() {
            /*
             * Containers of different directives, which can be used in tests:
             * - ModuleStructureDirectives
             * - LanguageSettingsDirectives
             * - DiagnosticsDirectives
             * - FirDiagnosticsDirectives
             * - CodegenTestDirectives
             * - JvmEnvironmentConfigurationDirectives
             *
             * All of them are located in `org.jetbrains.kotlin.test.directives` package
             */
            defaultDirectives {
                +CodegenTestDirectives.IGNORE_DEXING // Avoids loading R8 from the classpath.
            }
        }
    }

    object K2Jdk17Compiler : AbstractFirPsiBlackBoxCodegenTest(), IKotlinCompiler.IJDK17Compiler {
        init {
            initTestInfo(testInfo)
        }

        override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
            return EnvironmentBasedStandardLibrariesPathProvider
        }

        override fun configure(builder: TestConfigurationBuilder) {
            super.configure(builder)
            builder.config()
        }
    }

    private val testers = listOf<IKotlinCompiler>(K2Jdk17Compiler)

    private val maxRounds by option("--max-rounds")
        .int()
        .default(0)

    private val minimizeRunner = MinimizeRunner2(this)

    fun doDifferentialCompile(program: IrProgram): List<CompileResult> {
        val fileContent = IrProgramPrinter(Language.KOTLIN).printToSingle(program)
        return testers.map { it.testProgram(fileContent) }
    }

    fun doNormalCompile(program: IrProgram): CompileResult {
        val printer = IrProgramPrinter(Language.KOTLIN)
        val testResult = K2Jdk17Compiler.testProgram(printer.printToSingle(program))
        return testResult
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRoundDifferentialAndRecord(program: IrProgram, throwException: Boolean) {
        val fileContent = IrProgramPrinter(Language.KOTLIN).printToSingle(program)
        val testResults = testers.map { it.testProgram(fileContent) }
        val resultSet = testResults.toSet()
        if (resultSet.size != 1) {
            val (minimize, minResult) = try {
                minimizeRunner.minimize(program, testResults, recorder.recordCompilers(testers))
            } catch (_: Throwable) {
                null to null
            }
            val anySimilar = if (enableGED && minimize != null) {
//                with(BugData) {
//                    gedEnv.similarToAnyExistedBug(minimize)
//                }
                false
            } else false
            if (anySimilar) {
                recordCompileResult(Language.KOTLIN, program, testResults, minimize, minResult)
            } else {
                recordCompileResult(
                    Language.KOTLIN, program, testResults, minimize, minResult, outDir = nonSimilarOutDir
                )
            }
            if (throwException) {
                println("Find a compiler bug with -s, stop the runner")
                exitProcess(0)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRoundAndRecord(program: IrProgram, throwException: Boolean): CompileResult {
        val testResult = doNormalCompile(program)
        if (!testResult.success) {
            val (minimize, minResult) = try {
                minimizeRunner.minimize(program, listOf(testResult), testers)
            } catch (_: Throwable) {
                null to null
            }
            recordCompileResult(Language.KOTLIN, program, listOf(testResult), minimize, minResult)
            if (throwException) {
                throw RuntimeException()
            }
        }
        return testResult
    }

    /**
     * Compiles a mutated program only when its mutator supplied a known oracle.
     * A report is created exactly when compiler acceptance disagrees with that
     * oracle; the concrete compiler diagnostic is deliberately not predicted.
     */
    fun doOneMutatedRoundAndRecord(
        program: IrProgram,
        semanticValidity: SemanticValidity,
        throwException: Boolean,
    ): CompileResult {
        require(semanticValidity != SemanticValidity.UNKNOWN)
        val testResult = doNormalCompile(program)
        if (!isMutationOracleSatisfied(semanticValidity, testResult.success)) {
            recordCompileResult(
                majorLanguage = Language.KOTLIN,
                program = program,
                compileResults = listOf(testResult),
                metadata = mapOf(
                    "mutation-oracle.txt" to
                            "expected=$semanticValidity\nactual=${if (testResult.success) "VALID" else "INVALID"}\n",
                ),
                forceRecord = true,
            )
            if (throwException) {
                throw RuntimeException("Mutation result disagrees with semantic oracle")
            }
        }
        return testResult
    }

    override val availableCompilers: Map<String, ICompiler>
        get() = TODO("Not yet implemented")
    override val defaultCompilers: Map<String, ICompiler>
        get() = TODO("Not yet implemented")

    private val recorder = DataRecorder()

    private fun doReduce(program: IrProgram): IrProgram? {
        val fileContent = IrProgramPrinter(Language.KOTLIN).printToSingle(program)
        val testResults = testers.map { it.testProgram(fileContent) }
        recorder.addProgram("ori", program)
        val reduced: IrProgram
        val reduceTime = measureTime {
            val (result, _) = try {
                minimizeRunner.minimize(program, testResults, recorder.recordCompilers(testers))
            } catch (_: Throwable) {
                return null
            }
            reduced = result
        }
        recorder.addProgram("reduced", reduced)
        recorder.mergeData("reduceTime", reduceTime, Duration::plus)
        return reduced
    }

    private suspend fun CoroutineScope.runOnInputIRFiles() {
        val inputIRFiles = inputIRFiles!!
        val jobs = mutableListOf<Job>()
        for (file in inputIRFiles) {
            val job = launch {
                val prog = file.reader().use { gson.fromJson(it, IrProgram::class.java) }
                val reducedFile = File(file.parentFile, file.name + ".reduced")
                val reducedCache = if (reducedFile.exists()) {
                    reducedFile.reader().use { gson.fromJson(it, IrProgram::class.java) }
                } else null
                when (runMode) {
                    RunMode.DifferentialTest -> {
                        doOneRoundDifferentialAndRecord(prog, false)
                    }

                    RunMode.NormalTest -> {
                        doOneRoundAndRecord(prog, false)
                    }

                    RunMode.ReduceOnly -> {
                        if (reducedCache != null && useCache) {
                            recorder.addProgram("ori", prog)
                            recorder.addProgram("reduced", reducedCache)
                        } else {
                            val reduced = doReduce(prog)
                            if (useCache && reduced != null) {
                                reducedFile.writer().use {
                                    gson.toJson(reduced, it)
                                }
                            }
                        }
                    }

                    RunMode.GenerateIROnly ->
                        throw IllegalStateException("Using input IR file, cannot run GenerateIROnly mode.")
                }
                logger.info { gson.toJson(recorder.programCount) }
                logger.info { gson.toJson(recorder.programData) }
                logger.info { recorder.getData<Duration>("reduceTime").toString() }
                logger.info { "compile times: ${recorder.getCompileTimes()}" }
            }
            jobs.add(job)
        }
        jobs.joinAll()
        logger.info { gson.toJson(recorder.programCount) }
        logger.info { gson.toJson(recorder.programData) }
        logger.info { recorder.getData<Duration>("reduceTime").toString() }
        logger.info { "compile times: ${recorder.getCompileTimes()}" }
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
    override fun runnerMain() {
        logger.info { "start kotlin runner" }
        require(maxRounds >= 0) { "--max-rounds must be non-negative, but was $maxRounds" }
        val i = AtomicInteger(0)
        val parallelSize = 1
        val inputIRFiles = inputIRFiles
        if (inputIRFiles != null) {
            runBlocking(Dispatchers.IO.limitedParallelism(32)) {
                runOnInputIRFiles()
            }
            return
        }
        when (runMode) {
            RunMode.DifferentialTest -> {
                runBlocking(Dispatchers.IO.limitedParallelism(parallelSize)) {
                    val jobs = mutableListOf<Job>()
                    repeat(parallelSize) {
                        val job = launch {
                            val threadName = Thread.currentThread().name
                            while (true) {
                                val generator = IrDeclGenerator(runConfig.generatorConfig)
                                val prog = generator.genProgram()
                                repeat(runConfig.langShuffleTimesBeforeMutate) {
                                    val dur = measureTime { doOneRoundDifferentialAndRecord(prog, stopOnErrors) }
                                    println("$threadName ${i.incrementAndGet()}:${dur}\t\t")
                                    generator.shuffleLanguage(prog)
                                }
                                repeat(runConfig.mutateTimes) {
                                    val mutator = IrMutator(
                                        runConfig.mutatorConfig,
                                        generator = generator
                                    )
                                    val copiedProg = prog.deepCopy()
                                    if (mutator.mutate(copiedProg) != null) {
                                        repeat(runConfig.langShuffleTimesAfterMutate) {
                                            val dur =
                                                measureTime {
                                                    doOneRoundDifferentialAndRecord(
                                                        copiedProg,
                                                        stopOnErrors
                                                    )
                                                }
                                            println("$threadName ${i.incrementAndGet()}:${dur}\t\t")
                                            generator.shuffleLanguage(copiedProg)
                                        }
                                    }
                                }
                            }
                        }
                        jobs.add(job)
                    }
                    jobs.joinAll()
                }
            }

            RunMode.NormalTest -> {
                while (shouldContinueNormalRounds(i.get(), maxRounds)) {
                    val generator = IrDeclGenerator(runConfig.generatorConfig)
                    val prog = generator.genProgram()
                    var originalResult: CompileResult? = null
                    val dur = measureTime {
                        originalResult = doOneRoundAndRecord(prog, stopOnErrors)
                    }
                    println("${i.incrementAndGet()}:${dur}\t\t")

                    // Mutation oracles are meaningful only because the original
                    // generated program is known to compile successfully.
                    if (originalResult?.success != true) continue

                    for (mutationIndex in 0 until runConfig.mutateTimes) {
                        if (!shouldContinueNormalRounds(i.get(), maxRounds)) break
                        val copiedProg = prog.deepCopy()
                        val semanticValidity = IrMutator(
                            runConfig.mutatorConfig,
                            generator = generator,
                        ).mutate(copiedProg)

                        // No mutation and unknown validity are both discarded:
                        // neither provides a usable semantic oracle.
                        if (semanticValidity == null || semanticValidity == SemanticValidity.UNKNOWN) {
                            continue
                        }

                        val mutatedDuration = measureTime {
                            doOneMutatedRoundAndRecord(copiedProg, semanticValidity, stopOnErrors)
                        }
                        println("${i.incrementAndGet()}:${mutatedDuration}\tmutation=$semanticValidity")
                    }
                }
            }

            RunMode.GenerateIROnly -> {
                while (true) {
                    val generator = IrDeclGenerator(runConfig.generatorConfig)
                    val prog = generator.genProgram()
                    val outDir = File(generateIROnlyOutDir, System.nanoTime().toHexString())
                        .mkdirsIfNotExists()
                    File(outDir, "main.json").writer().use {
                        gson.toJson(prog, it)
                    }
                }
            }

            RunMode.ReduceOnly -> {
                throw IllegalStateException("No input IR file, cannot run ReduceOnly mode.")
            }
        }
    }
}

internal fun shouldContinueNormalRounds(completedRounds: Int, maxRounds: Int): Boolean =
    maxRounds == 0 || completedRounds < maxRounds

internal fun isMutationOracleSatisfied(
    semanticValidity: SemanticValidity,
    compileSuccess: Boolean,
): Boolean = when (semanticValidity) {
    SemanticValidity.VALID -> compileSuccess
    SemanticValidity.INVALID -> !compileSuccess
    SemanticValidity.UNKNOWN -> error("Unknown mutation validity must be discarded before compilation")
}

fun main(args: Array<String>) {
    /*Thread {
        println("start timer")
        Thread.sleep(15 * 3600 * 1000)
        println("timeout")
        exitProcess(0)
    }.start()*/
    CrossLangFuzzerKotlinRunner().main(args)
}