package com.github.xyzboom.codesmith.kotlin

import com.github.ajalt.clikt.core.main
import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.RunMode
import com.github.xyzboom.codesmith.data.DataRecorder
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.minimize.MinimizeRunnerImpl
import com.github.xyzboom.codesmith.mutator.IrMutator
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.recordCompileResult
import com.github.xyzboom.codesmith.serde.gson
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.xyzboom.clf.BugData
import io.github.xyzboom.gedlib.GEDEnv
import kotlinx.coroutines.*
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirPsiBlackBoxCodegenTest
import org.jetbrains.kotlin.test.runners.codegen.AbstractIrBlackBoxCodegenTest
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
    // make sure JDK HOME is set
    KtTestUtil.getJdk8Home()
    KtTestUtil.getJdk17Home()
    KotlinTestInfo("CrossLangFuzzerKotlinRunner", "main", emptySet())
}

class CrossLangFuzzerKotlinRunner : CommonCompilerRunner() {

    private val gedEnv = GEDEnv()

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

    object K2Jdk8Compiler : AbstractFirPsiBlackBoxCodegenTest(), IKotlinCompiler.IJDK8Compiler {
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

    object K1Jdk8Compiler : AbstractIrBlackBoxCodegenTest(), IKotlinCompiler.IJDK8Compiler {
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

    object K1Jdk17Compiler : AbstractIrBlackBoxCodegenTest(), IKotlinCompiler.IJDK17Compiler {
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

    private val testers = listOf<IKotlinCompiler>(
        K1Jdk8Compiler, /*K1Jdk17Compiler,*/
        /*K2Jdk8Compiler*/ K2Jdk17Compiler,
    )

    private val minimizeRunner = MinimizeRunnerImpl(this)

    fun doDifferentialCompile(program: IrProgram): List<CompileResult> {
        val fileContent = IrProgramPrinter(Language.KOTLIN).printToSingle(program)
        return testers.map { it.testProgram(fileContent) }
    }

    fun doNormalCompile(program: IrProgram): CompileResult {
        val printer = IrProgramPrinter(Language.KOTLIN)
        val testResult = K2Jdk8Compiler.testProgram(printer.printToSingle(program))
        return testResult
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRoundDifferentialAndRecord(program: IrProgram, throwException: Boolean) {
        val fileContent = IrProgramPrinter(Language.KOTLIN).printToSingle(program)
        val testResults = testers.map { it.testProgram(fileContent) }
        val resultSet = testResults.toSet()
        if (resultSet.size != 1) {
            val (minimize, minResult) = try {
                minimizeRunner.minimize(program, testResults, testers)
            } catch (_: Throwable) {
                null to null
            }
            val anySimilar = if (enableGED && minimize != null) {
                with(BugData) {
                    gedEnv.similarToAnyExistedBug(minimize)
                }
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
    fun doOneRoundAndRecord(program: IrProgram, throwException: Boolean) {
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
                                    if (mutator.mutate(copiedProg)) {
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
                while (true) {
                    val generator = IrDeclGenerator(runConfig.generatorConfig)
                    val prog = generator.genProgram()
                    val dur = measureTime { doOneRoundAndRecord(prog, stopOnErrors) }
                    println("${i.incrementAndGet()}:${dur}\t\t")
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

fun main(args: Array<String>) {
    /*Thread {
        println("start timer")
        Thread.sleep(15 * 3600 * 1000)
        println("timeout")
        exitProcess(0)
    }.start()*/
    CrossLangFuzzerKotlinRunner().main(args)
}