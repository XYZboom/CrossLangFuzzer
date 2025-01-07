package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.GroovyCompilerWrapper.Companion.groovy4Compiler
import com.github.xyzboom.codesmith.GroovyCompilerWrapper.Companion.groovy5Compiler
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.mutator.MutatorConfig
import com.github.xyzboom.codesmith.mutator.impl.IrMutatorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.measureTime

@OptIn(ExperimentalStdlibApi::class)
private fun recordCompileResult(
    sourceSingleFileContent: String,
    compileGroovy4Result: CompileResult,
    compileGroovy5Result: CompileResult,
) {
    val dir = File(logFile, System.currentTimeMillis().toHexString()).mkdirsIfNotExists()
    File("codesmith-trace.log").copyTo(File(dir, "codesmith-trace.log"))
    if (!compileGroovy4Result.success) {
        File(dir, "groovy4-error.txt").writeText(compileGroovy4Result.toString())
    }
    if (!compileGroovy5Result.success) {
        File(dir, "groovy5-error.txt").writeText(compileGroovy5Result.toString())
    }
    File(dir, "main.groovy").writeText(sourceSingleFileContent)
}

private fun doOneRound(stopOnErrors: Boolean = false) {
    val printer = IrProgramPrinter(false)
    val generator = IrDeclGeneratorImpl(
        GeneratorConfig(
            classMemberIsPropertyWeight = 0,
            allowUnitInTypeArgument = true,
            printJavaNullableAnnotationProbability = 0f
        ),
        majorLanguage = Language.GROOVY4,
    )
    val program = generator.genProgram()
    repeat(5) {
        run normalDifferential@{
            runDifferential(printer, program, stopOnErrors)
        }
        generator.shuffleLanguage(program)
    }
    val mutator = IrMutatorImpl(
        generator = generator,
        config = MutatorConfig(
            mutateGenericArgumentInParentWeight = 1,
            removeOverrideMemberFunctionWeight = 1,
            mutateGenericArgumentInMemberFunctionParameterWeight = 1,
            mutateParameterNullabilityWeight = 0
        )
    )
    if (mutator.mutate(program)) {
        repeat(5) {
            run mutatedDifferential@{
                runDifferential(printer, program, stopOnErrors)
            }
            generator.shuffleLanguage(program)
        }
    }
}

private fun runDifferential(
    printer: IrProgramPrinter,
    program: IrProgram,
    stopOnErrors: Boolean
) {
    val compileGroovy4Result = groovy4Compiler.compileGroovyWithJava(printer, program)
    val compileGroovy5Result = groovy5Compiler.compileGroovyWithJava(printer, program)
    if (compileGroovy4Result != compileGroovy5Result) {
        recordCompileResult(printer.printToSingle(program), compileGroovy4Result, compileGroovy5Result)
        if (stopOnErrors) {
            exitProcess(-1)
        }
    }
}

fun main() {
    println("start at: $tempDir")
    var i = 0
    while (true) {
        val dur = measureTime { doOneRound() }
        println("${i++}: $dur")
    }
}