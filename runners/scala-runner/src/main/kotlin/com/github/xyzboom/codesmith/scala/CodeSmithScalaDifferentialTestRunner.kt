package com.github.xyzboom.codesmith.scala

import com.github.xyzboom.codesmith.Language
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
    compileScala3Result: CompileResult,
    compileScala2Result: CompileResult,
) {
    val dir = File(logFile, System.currentTimeMillis().toHexString()).mkdirsIfNotExists()
    File("codesmith-trace.log").copyTo(File(dir, "codesmith-trace.log"))
    if (!compileScala3Result.success) {
        File(dir, "scala3-error.txt").writeText(compileScala3Result.toString())
    }
    if (!compileScala2Result.success) {
        File(dir, "scala2-error.txt").writeText(compileScala2Result.toString())
    }
    File(dir, "main.scala").writeText(sourceSingleFileContent)
}

private fun doOneRound(stopOnErrors: Boolean = false) {
    val printer = IrProgramPrinter(false)
    val generator = IrDeclGeneratorImpl(
        GeneratorConfig(
            classMemberIsPropertyWeight = 0,
        ),
        majorLanguage = Language.SCALA,
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
            mutateGenericArgumentInParentWeight = 0,
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
    val compileScala3Result = compileScala3WithJava(printer, program)
    val compileScala2Result = compileScala2WithJava(printer, program)
    if (compileScala3Result != compileScala2Result) {
        recordCompileResult(printer.printToSingle(program), compileScala3Result, compileScala2Result)
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