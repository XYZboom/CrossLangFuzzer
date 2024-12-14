package com.github.xyzboom.codesmith.runner

import com.github.xyzboom.codesmith.generator.impl.IrGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.runner.CompilerRunner.kotlincFile
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.tools.ExecFileLoader
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Paths

object CoverageRunner {

    private val jacocoAgentPath = (System.getProperty("jacocoAgentPath")
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

    private val kotlinCompilerJarFile = File(kotlincFile.parentFile.parentFile, "lib/kotlin-compiler.jar").also {
        if (!it.exists()) {
            throw FileNotFoundException("Path for 'kotlin compiler jar' not exists!")
        }
    }

    fun getCoverageCounter(projectPath: String): ICounter {
        val tempFile = File.createTempFile("jacoco", ".exec")
        val tempPath = tempFile.absoluteFile
        val jacocoAgent4Kotlinc = "\"-J-javaagent:$jacocoAgentPath=destfile=$tempPath,inclnolocationclasses=true\""
        CompilerRunner.compile(jacocoAgent4Kotlinc, projectPath, "-d", Paths.get(projectPath, "out.jar").toString())
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
        val counter = getCoverageCounter(args[0])
        println(counter.totalCount)
        println(counter.coveredCount)
        val prog = IrGeneratorImpl().genProgram()
        IrProgramPrinter().saveTo(args[1], prog)
        val counter1 = getCoverageCounter(args[1])
        println(counter1.totalCount)
        println(counter1.coveredCount)
    }
}