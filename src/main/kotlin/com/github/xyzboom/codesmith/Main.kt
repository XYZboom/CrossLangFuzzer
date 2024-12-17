package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.runner.CoverageRunner
import java.io.File
import java.time.LocalTime

fun main() {
    val temp = System.getProperty("java.io.tmpdir")
    for (i in 0 until 100) {
        val prog = IrGeneratorImpl(
            GeneratorConfig(
                classNumRange = 5..9
            )
        ).genProgram()
        val fileMap = IrProgramPrinter().print(prog)
        val dir = File(temp, "code-smith-${LocalTime.now().nano}")
        IrProgramPrinter().saveTo(dir.path, prog)
        val counter = CoverageRunner.getCoverageCounter(dir.path)
        println(counter.totalCount)
        println(counter.coveredCount)
    }
}