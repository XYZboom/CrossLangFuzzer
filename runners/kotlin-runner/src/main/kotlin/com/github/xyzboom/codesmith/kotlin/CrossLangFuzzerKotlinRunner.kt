package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.logFile
import com.github.xyzboom.codesmith.mutator.IrMutator
import com.github.xyzboom.codesmith.mutator.MutatorConfig
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
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
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirPsiBlackBoxCodegenTest
import org.jetbrains.kotlin.test.runners.codegen.AbstractIrBlackBoxCodegenTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.measureTime

val testInfo = KotlinTestInfo("CrossLangFuzzerKotlinRunner", "main", emptySet())

object CrossLangFuzzerKotlinRunner: CommonCompilerRunner() {

    private val logger = KotlinLogging.logger {  }

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
            +JvmEnvironmentConfigurationDirectives.FULL_JDK

            +CodegenTestDirectives.IGNORE_DEXING // Avoids loading R8 from the classpath.
        }
    }

    object K2Jdk8Test : AbstractFirPsiBlackBoxCodegenTest(), IDifferentialTest.IJDK8Test {
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

    object K2Jdk17Test: AbstractFirPsiBlackBoxCodegenTest(), IDifferentialTest.IJDK17Test {
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

    object K1Jdk8Test : AbstractIrBlackBoxCodegenTest(), IDifferentialTest.IJDK8Test {
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

    object K1Jdk17Test: AbstractIrBlackBoxCodegenTest(), IDifferentialTest.IJDK17Test {
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

    private val testers = listOf<IDifferentialTest>(
        K1Jdk8Test, K1Jdk17Test,
        K2Jdk8Test, K2Jdk17Test,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        run()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun doOneRound(fileContent: String, throwException: Boolean) {
        val testResults = testers.map { it.testProgram(fileContent) }
        if (testResults.toSet().size != 1) {
            val dir = File(logFile, System.currentTimeMillis().toHexString())
            if (!dir.exists()) {
                dir.mkdirs()
            }
            File(dir, "main.kt").writeText(fileContent)
            File("codesmith-trace.log").copyTo(File(dir, "codesmith-trace.log"))
            for (result in testResults) {
                if (result.e != null) {
                    val writer = File(dir, "exception-${result.testName}.txt").printWriter()
                    result.e.printStackTrace(writer)
                    writer.flush()
                }
            }
            if (throwException) {
                throw RuntimeException()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun run() {
        val throwException = true
        logger.info { "start kotlin runner" }
        val i = AtomicInteger(0)
        val parallelSize = 32
        runBlocking(Dispatchers.IO.limitedParallelism(parallelSize)) {
            val jobs = mutableListOf<Job>()
            repeat(parallelSize) {
                val job = launch {
                    var enableGeneric: Boolean
                    val threadName = Thread.currentThread().name
                    while (true) {
                        enableGeneric = Random.nextBoolean(0.15f)
//                        enableGeneric = false
                        val printer = IrProgramPrinter()
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
                        repeat(5) {
                            val fileContent = printer.printToSingle(prog)
                            val dur = measureTime { doOneRound(fileContent, throwException) }
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
                            repeat(5) {
                                val fileContent = printer.printToSingle(prog)
                                val dur = measureTime { doOneRound(fileContent, throwException) }
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
    }
}