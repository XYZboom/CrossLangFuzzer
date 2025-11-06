package com.github.xyzboom.codesmith.scala

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
import com.github.xyzboom.codesmith.minimize.MinimizeRunner2
import com.github.xyzboom.codesmith.mutator.IrMutator
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.recordCompileResult
import com.github.xyzboom.codesmith.serde.gson
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.measureTime

class CrossLangFuzzerScalaRunner : CommonCompilerRunner() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override val availableCompilers: Map<String, ICompiler>
        get() = TODO("Not yet implemented")
    override val defaultCompilers: Map<String, ICompiler>
        get() = TODO("Not yet implemented")
    private val minimizeRunner = MinimizeRunner2(this)

    object Scala2Compiler : ICompiler {
        override fun compile(program: IrProgram): CompileResult {
            val printer = IrProgramPrinter(Language.SCALA)
            return compileScala2WithJava(printer, program)
        }
    }

    object Scala3Compiler : ICompiler {
        override fun compile(program: IrProgram): CompileResult {
            val printer = IrProgramPrinter(Language.SCALA)
            return compileScala3WithJava(printer, program)
        }
    }

    private val compilers = listOf(
        Scala2Compiler, Scala3Compiler,
    )

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRoundDifferentialAndRecord(program: IrProgram, throwException: Boolean) {
        val testResults = compilers.map { it.compile(program) }
        val resultSet = testResults.toSet()
        if (resultSet.size != 1) {
            val (minimize, minResult) = try {
                minimizeRunner.minimize(program, testResults, recorder.recordCompilers(compilers))
            } catch (e: Throwable) {
                if (true) throw e
                null to null
            }
            val anySimilar = if (enableGED && minimize != null) {
//                with(BugData) {
//                    gedEnv.similarToAnyExistedBug(minimize)
//                }
                false
            } else false
            if (anySimilar) {
                recordCompileResult(Language.SCALA, program, testResults, minimize, minResult)
            } else {
                recordCompileResult(
                    Language.SCALA, program, testResults, minimize, minResult, outDir = nonSimilarOutDir
                )
            }
            if (throwException) {
                println("Find a compiler bug with -s, stop the runner")
                exitProcess(0)
            }
        }
    }

    fun doNormalCompile(program: IrProgram): CompileResult {
        return Scala3Compiler.compile(program)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRoundAndRecord(program: IrProgram, throwException: Boolean) {
        val testResult = doNormalCompile(program)
        if (!testResult.success) {
            val (minimize, minResult) = try {
                minimizeRunner.minimize(program, listOf(testResult), compilers)
            } catch (_: Throwable) {
                null to null
            }
            recordCompileResult(Language.SCALA, program, listOf(testResult), minimize, minResult)
            if (throwException) {
                throw RuntimeException()
            }
        }
    }

    private val recorder = DataRecorder()

    private fun doReduce(program: IrProgram): IrProgram? {
        val testResults = compilers.map { it.compile(program) }
        recorder.addProgram("ori", program)
        val reduced: IrProgram
        val reduceTime = measureTime {
            val (result, _) = try {
                minimizeRunner.minimize(program, testResults, recorder.recordCompilers(compilers))
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
        logger.info { "start scala runner" }
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
                                val generator = IrDeclGenerator(
                                    runConfig.generatorConfig,
                                    majorLanguage = Language.SCALA
                                )
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
    CrossLangFuzzerScalaRunner().main(args)
}