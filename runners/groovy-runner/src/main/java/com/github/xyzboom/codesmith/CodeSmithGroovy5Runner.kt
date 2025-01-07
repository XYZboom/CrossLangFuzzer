package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.GroovyCompilerWrapper.Companion.groovy5Compiler
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import kotlin.system.exitProcess
import kotlin.time.measureTime

private fun doOneRound(stopOnErrors: Boolean = false) {
    val printer = IrProgramPrinter(false)
    val generator = IrDeclGeneratorImpl(
        GeneratorConfig(
            classMemberIsPropertyWeight = 0,
            allowUnitInTypeArgument = true,
            printJavaNullableAnnotationProbability = 0f
        ),
        majorLanguage = Language.GROOVY5,
    )
    val program = generator.genProgram()
    repeat(5) {
        val compileResult = groovy5Compiler.compileGroovyWithJava(printer, program)
        if (!compileResult.success) {
            recordCompileResult(Language.GROOVY5, printer.printToSingle(program), compileResult)
            if (stopOnErrors) {
                exitProcess(-1)
            }
        }
        generator.shuffleLanguage(program)
    }
}

fun main() {
    println("start at: $tempDir")
    var i = 0
    while (true) {
        val dur = measureTime { doOneRound(false) }
        println("${i++}: $dur")
    }
}