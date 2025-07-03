package com.github.xyzboom.codesmith.kotlin

import com.github.ajalt.clikt.core.main
import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.mutator.IrMutator
import com.github.xyzboom.codesmith.mutator.MutatorConfig
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.recordCompileResult
import com.github.xyzboom.codesmith.utils.nextBoolean
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import kotlin.random.Random
import kotlin.time.measureTime

val testInfo = KotlinTestInfo("CrossLangFuzzerKotlinRunner", "main", emptySet())

class CrossLangFuzzerKotlinRunner: CommonCompilerRunner() {

    private val logger = KotlinLogging.logger {  }

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

    object K2Jdk17Test: AbstractFirPsiBlackBoxCodegenTest(), IKotlinCompilerTest.IJDK17Test {
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

    object K1Jdk17Test: AbstractIrBlackBoxCodegenTest(), IKotlinCompilerTest.IJDK17Test {
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

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRoundDifferential(program: IrProgram, throwException: Boolean) {
        val fileContent = IrProgramPrinter(Language.KOTLIN).printToSingle(program)
        val testResults = testers.map { it.testProgram(fileContent) }
        if (testResults.toSet().size != 1) {
            recordCompileResult(Language.KOTLIN, program, testResults)
            if (throwException) {
                throw RuntimeException()
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRound(program: IrProgram, throwException: Boolean) {
        val printer = IrProgramPrinter(Language.KOTLIN)
        val testResult = K2Jdk8Test.testProgram(printer.printToSingle(program))
        if (!testResult.success) {
            recordCompileResult(Language.KOTLIN, program, testResult)
            if (throwException) {
                throw RuntimeException()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun run() {
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
                        var enableGeneric: Boolean
                        val threadName = Thread.currentThread().name
                        while (true) {
                            enableGeneric = Random.nextBoolean(0.15f)
        //                        enableGeneric = false
                            val generator = IrDeclGenerator(
                                GeneratorConfig(
                                    classHasTypeParameterProbability = if (enableGeneric) {
                                        Random.nextFloat() / 4f
                                    } else {
                                        0f
                                    }
                                )
                            )
                            val prog = generator.genProgram()
                            repeat(langShuffleTimesBeforeMutate) {
                                val dur = measureTime { doOneRoundDifferential(prog, stopOnErrors) }
                                println("$threadName ${i.incrementAndGet()}:${dur}\t\t")
                                generator.shuffleLanguage(prog)
                            }
                            /*val config = MutatorConfig.allZero.copy(
                                    mutateParameterNullabilityWeight = 1
                                )*/
                            val config = MutatorConfig.default
                            val mutator = IrMutator(
                                config,
                                generator = generator
                            )
                            if (mutator.mutate(prog)) {
                                repeat(langShuffleTimesAfterMutate) {
                                    val dur = measureTime { doOneRoundDifferential(prog, stopOnErrors) }
                                    println("$threadName ${i.incrementAndGet()}:${dur}\t\t")
                                    generator.shuffleLanguage(prog)
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
                val dur = measureTime { doOneRound(prog, stopOnErrors) }
                println("${i.incrementAndGet()}:${dur}\t\t")
            }
        }
    }
}

fun main(args: Array<String>) {
    CrossLangFuzzerKotlinRunner().main(args)
}