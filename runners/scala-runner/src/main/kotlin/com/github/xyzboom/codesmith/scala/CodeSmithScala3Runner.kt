package com.github.xyzboom.codesmith.scala

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.measureTime


@OptIn(ExperimentalStdlibApi::class)
private fun recordCompileResult(
    sourceSingleFileContent: String,
    compileResult: CompileResult,
) {
    val (scalaResult, javaResult) = compileResult
    val dir = File(logFile, System.currentTimeMillis().toHexString()).mkdirsIfNotExists()
    File("codesmith-trace.log").copyTo(File(dir, "codesmith-trace.log"))
    if (scalaResult != null) {
        File(dir, "scala-error.txt").writeText(scalaResult)
    } else if (javaResult != null) {
        File(dir, "java-error.txt").writeText(javaResult)
    }
    File(dir, "main.scala").writeText(sourceSingleFileContent)
}

private fun doOneRound(stopOnErrors: Boolean = false) {
    val printer = IrProgramPrinter(false)
    val generator = IrDeclGeneratorImpl(
        GeneratorConfig(
            classMemberIsPropertyWeight = 0
        ),
        majorLanguage = Language.SCALA,
    )
    val program = generator.genProgram()
    val compileResult = compileScala3WithJava(printer, program)
    if (!compileResult.success) {
        recordCompileResult(printer.printToSingle(program), compileResult)
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