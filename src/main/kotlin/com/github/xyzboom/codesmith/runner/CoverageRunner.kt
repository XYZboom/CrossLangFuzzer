package com.github.xyzboom.codesmith.runner

import com.github.xyzboom.codesmith.generator.impl.IrGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.runner.CompilerRunner.kotlincFile
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.IBundleCoverage
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.data.ExecutionDataReader
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfoStore
import org.jacoco.core.tools.ExecFileLoader
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Method
import java.nio.file.Paths
import kotlin.time.measureTime

@Suppress("unused")
object CoverageRunner {

    private val jacocoAgentPath by lazy {
        (System.getProperty("jacocoAgentPath")
            ?: throw IllegalStateException("System property 'jacocoAgentPath' not set")).also {
            File(it).also { file ->
                if (!file.exists()) {
                    throw FileNotFoundException("Path for 'jacocoAgentPath' $it not exists!")
                }
                if (!file.isFile) {
                    throw IllegalStateException("Path for 'jacocoAgentPath' $it is not a file!")
                }
            }
        }
    }

    private val kotlinCompilerJarFile by lazy {
        File(kotlincFile.parentFile.parentFile, "lib/kotlin-compiler.jar").also {
            if (!it.exists()) {
                throw FileNotFoundException("Path for 'kotlin compiler jar' not exists!")
            }
        }
    }

    private val agent: Any by lazy {
        Class.forName("org.jacoco.agent.rt.RT").getMethod("getAgent").invoke(null)
    }
    private val agentClass: Class<*> by lazy { agent::class.java }
    private val agentClassGetExecutionDataMethod: Method by lazy {
        agentClass.getDeclaredMethod("getExecutionData", Boolean::class.java).apply { isAccessible = true }
    }

    fun getJacocoRuntimeData(clear: Boolean = true): ExecutionDataStore {
        val data = agentClassGetExecutionDataMethod.invoke(agent, clear) as ByteArray
        val reader = ExecutionDataReader(data.inputStream())
        val executionDataStore = ExecutionDataStore()
        val sessionInfoStore = SessionInfoStore()
        reader.setExecutionDataVisitor(executionDataStore)
        reader.setSessionInfoVisitor(sessionInfoStore)
        reader.read()
        return executionDataStore
    }

    fun getBundleCoverage(
        executionDataStore: ExecutionDataStore,
        analyzePaths: List<String>,
        classFileFilter: (File) -> Boolean
    ): IBundleCoverage {
        val builder = CoverageBuilder()
        val analyzer = Analyzer(executionDataStore, builder)
        for (analyzePath in analyzePaths) {
            val walker = File(analyzePath).walkTopDown().iterator()
            while (walker.hasNext()) {
                val file = walker.next()
                if (file.isFile && file.extension == "class"
                    && "build" in file.absolutePath && "classes" in file.absolutePath
                ) {
                    if (classFileFilter(file)) {
                        analyzer.analyzeAll(file)
                    }
                }
            }
        }
        return builder.getBundle("temp")
    }

    fun getCoverageCounter(projectPath: String): ICounter {
        val tempFile = File.createTempFile("jacoco", ".exec")
        val tempPath = tempFile.absoluteFile
        val jacocoAgent4Kotlinc = "\"-J-javaagent:$jacocoAgentPath=destfile=$tempPath," +
                "inclnolocationclasses=true," +
                "exclclassloader=*.reflect.DelegatingClassLoader\""
        val compileTime = measureTime {
            CompilerRunner.compile(
                jacocoAgent4Kotlinc, projectPath,
                "-d", Paths.get(projectPath, "out").toString(),
                "-Xuse-javac",
                "-Xcompile-java",
                "\"-Xjavac-arguments=-encoding UTF-8\""
            )
        }
        val execFileLoader = ExecFileLoader()
        execFileLoader.load(tempFile)
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(execFileLoader.executionDataStore, coverageBuilder)
        analyzer.analyzeAll(kotlinCompilerJarFile)
        val bundle = coverageBuilder.getBundle("temp")
        tempFile.delete()
        return bundle.lineCounter
    }

    @JvmStatic
    fun main(args: Array<String>) {
//        val counter = getCoverageCounter(args[0])
//        println(counter.totalCount)
//        println(counter.coveredCount)
//        val prog = IrGeneratorImpl().genProgram()
//        IrProgramPrinter().saveTo(args[1], prog)
//        val counter1 = getCoverageCounter(args[1])
//        println(counter1.totalCount)
//        println(counter1.coveredCount)

        val tempFile = File.createTempFile("jacoco", ".exec")
        val tempPath = tempFile.absoluteFile
        val jacocoAgent4Kotlinc = "\"-J-javaagent:$jacocoAgentPath=destfile=$tempPath," +
                "inclnolocationclasses=true," +
                "exclclassloader=*.reflect.DelegatingClassLoader\""
        val compileTime = measureTime {
            CompilerRunner.compile(
                jacocoAgent4Kotlinc, args[1],
                "-d", Paths.get(args[1], "out").toString(),
                "-Xuse-javac",
                "-Xcompile-java",
                "\"-Xjavac-arguments=-encoding UTF-8\""
            )
        }
        val execFileLoader = ExecFileLoader()
        execFileLoader.load(tempFile)
        val dataStore = execFileLoader.executionDataStore
        val programInfo = ProgramInfo()
        programInfo.collect(listOf(kotlinCompilerJarFile.absolutePath)) { true }
        println(programInfo.coverageInfo(dataStore))
    }
}