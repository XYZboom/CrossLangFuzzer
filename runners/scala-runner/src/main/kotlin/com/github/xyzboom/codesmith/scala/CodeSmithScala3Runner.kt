package com.github.xyzboom.codesmith.scala

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.recordCompileResult
import com.github.xyzboom.codesmith.tempDir
import kotlin.system.exitProcess
import kotlin.time.measureTime

private fun doOneRound(stopOnErrors: Boolean = false) {
    val printer = IrProgramPrinter(false)
    val generator = IrDeclGenerator(
        GeneratorConfig(
            classMemberIsPropertyWeight = 0,
            allowUnitInTypeArgument = true
        ),
        majorLanguage = Language.SCALA,
    )
    val program = generator.genProgram()
    val compileResult = compileScala3WithJava(printer, program)
    if (!compileResult.success) {
        recordCompileResult(Language.SCALA, printer.printToSingle(program), compileResult)
        if (stopOnErrors) {
            exitProcess(-1)
        }
    }
}

fun main() {
    println("start at: $tempDir")
    var i = 0
    while (true) {
        val dur = measureTime { doOneRound(true) }
        println("${i++}: $dur")
    }
}