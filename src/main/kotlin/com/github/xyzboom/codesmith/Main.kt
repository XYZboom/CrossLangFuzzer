package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.runner.CompilerRunner
import com.github.xyzboom.codesmith.runner.CoverageRunner
import java.io.File
import java.nio.file.Paths
import java.time.LocalTime
import kotlin.random.Random


fun main() {
    val temp = System.getProperty("java.io.tmpdir")
    val printer = IrProgramPrinter()
    for (i in 0 until 1) {
        val prog = IrDeclGeneratorImpl(
            GeneratorConfig(
//                topLevelDeclRange = 5..9,
//                topLevelClassWeight = 1,
//                topLevelFunctionWeight = 0,
//                topLevelPropertyWeight = 0,
//                classHasTypeParameterProbability = 1f,
//                classHasSuperProbability = 0.8f,
                classMemberNumRange = 1..1,
                javaRatio = 0f,
//                classImplNumRange = 3..4,
            ),
            Random(8)
        ).genProgram()
        val fileContent = printer.printToSingle(prog)
        println(fileContent)
        val dir = File(temp, "code-smith-${LocalTime.now().nano}")
        printer.saveTo(dir.path, prog)
        val projectPath = dir.path
        /*val counter = CoverageRunner.getCoverageCounter(dir.path)
        println(counter.totalCount)
        println(counter.coveredCount)*/
        CompilerRunner.compile(
            projectPath,
            "-d", Paths.get(projectPath, "out").toString(),
            "-Xuse-javac",
            "-Xcompile-java",
        )
    }
}