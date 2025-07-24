package com.github.xyzboom.codesmith.kotlin

import com.github.ajalt.clikt.core.main
import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.minimize.MinimizeRunnerImpl
import com.github.xyzboom.codesmith.mutator.IrMutator
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.recordCompileResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirPsiBlackBoxCodegenTest
import org.jetbrains.kotlin.test.runners.codegen.AbstractIrBlackBoxCodegenTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTime

val testInfo = KotlinTestInfo("CrossLangFuzzerKotlinRunner", "main", emptySet())

class CrossLangFuzzerKotlinRunner : CommonCompilerRunner() {

    private val logger = KotlinLogging.logger { }

    companion object {
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

    object K2Jdk8Test : AbstractFirPsiBlackBoxCodegenTest(), IKotlinCompilerTest.IJDK8Test {
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

    object K2Jdk17Test : AbstractFirPsiBlackBoxCodegenTest(), IKotlinCompilerTest.IJDK17Test {
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

    object K1Jdk8Test : AbstractIrBlackBoxCodegenTest(), IKotlinCompilerTest.IJDK8Test {
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

    object K1Jdk17Test : AbstractIrBlackBoxCodegenTest(), IKotlinCompilerTest.IJDK17Test {
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

    private val testers = listOf<IKotlinCompilerTest>(
        K1Jdk8Test, K1Jdk17Test,
        K2Jdk8Test, K2Jdk17Test,
    )

    private val minimizeRunner = MinimizeRunnerImpl(this)

    override fun compile(program: IrProgram): List<CompileResult> {
        return if (differentialTesting) {
            doDifferentialCompile(program)
        } else {
            listOf(doNormalCompile(program))
        }
    }

    fun doDifferentialCompile(program: IrProgram): List<CompileResult> {
        val fileContent = IrProgramPrinter(Language.KOTLIN).printToSingle(program)
        return testers.map { it.testProgram(fileContent) }
    }

    fun doNormalCompile(program: IrProgram): CompileResult {
        val printer = IrProgramPrinter(Language.KOTLIN)
        val testResult = K2Jdk8Test.testProgram(printer.printToSingle(program))
        return testResult
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRoundDifferentialAndRecord(program: IrProgram, throwException: Boolean) {
        val fileContent = IrProgramPrinter(Language.KOTLIN).printToSingle(program)
        val testResults = testers.map { it.testProgram(fileContent) }
        val resultSet = testResults.toSet()
        if (resultSet.size != 1) {
            /**
             * see [KT-60791](https://youtrack.jetbrains.com/issue/KT-60791) and
             * [KT-39603](https://youtrack.jetbrains.com/issue/KT-39603)
             * for more information
            */
            val avoidKT60791 = resultSet.any {
                it.majorResult?.contains("EXPLICIT_OVERRIDE_REQUIRED_IN_COMPATIBILITY_MODE") == true ||
                        it.javaResult?.contains("EXPLICIT_OVERRIDE_REQUIRED_IN_COMPATIBILITY_MODE") == true
            }
            if (avoidKT60791) return
            val (minimize, minResult) = try {
                minimizeRunner.minimize(program, testResults)
            } catch (_: Throwable) {
                null to null
            }
            recordCompileResult(Language.KOTLIN, program, testResults, minimize, minResult)
            if (throwException) {
                throw RuntimeException()
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRoundAndRecord(program: IrProgram, throwException: Boolean) {
        val testResult = doNormalCompile(program)
        if (!testResult.success) {
            val (minimize, minResult) = try {
                minimizeRunner.minimize(program, listOf(testResult))
            } catch (_: Throwable) {
                null to null
            }
            recordCompileResult(Language.KOTLIN, program, listOf(testResult), minimize, minResult)
            if (throwException) {
                throw RuntimeException()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun runnerMain() {
        logger.info { "start kotlin runner" }
        val i = AtomicInteger(0)
        val parallelSize = 1
        // make sure JDK HOME is set
        KtTestUtil.getJdk8Home()
        KtTestUtil.getJdk17Home()
        if (differentialTesting) {
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
                                        val dur = measureTime { doOneRoundDifferentialAndRecord(copiedProg, stopOnErrors) }
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
        } else {
            while (true) {
                val generator = IrDeclGenerator()
                val prog = generator.genProgram()
                val dur = measureTime { doOneRoundAndRecord(prog, stopOnErrors) }
                println("${i.incrementAndGet()}:${dur}\t\t")
            }
        }
    }
}

fun main(args: Array<String>) {
    CrossLangFuzzerKotlinRunner().main(args)
}