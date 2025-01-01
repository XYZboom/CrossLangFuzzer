package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.ir.types.builtin.ALL_BUILTINS
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.mutator.impl.IrMutatorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.runner.CompilerRunner
import com.github.xyzboom.codesmith.runner.CoverageRunner
import java.io.File
import java.nio.file.Paths
import java.time.LocalTime
import kotlin.random.Random
import kotlin.system.exitProcess


fun main() {
//    println(IrAny)
    val predicate: (IrBuiltInType) -> Boolean = { true }
    ALL_BUILTINS.filter(predicate)
//    exitProcess(0)
    val temp = System.getProperty("java.io.tmpdir")
    val printer = IrProgramPrinter()
    for (i in 0 until 1) {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig()
        )
        val prog = generator.genProgram()
        val fileContent = printer.printToSingle(prog)
        println(fileContent)
        val mutator = IrMutatorImpl(generator = generator)
        mutator.mutate(prog)
        val fileContent1 = printer.printToSingle(prog)
        println("-----------------")
        println(fileContent1)
        /*val dir = File(temp, "code-smith-${LocalTime.now().nano}")
        printer.saveTo(dir.path, prog)
        val projectPath = dir.path*/
        /*val counter = CoverageRunner.getCoverageCounter(dir.path)
        println(counter.totalCount)
        println(counter.coveredCount)*/
        /*CompilerRunner.compile(
            projectPath,
            "-d", Paths.get(projectPath, "out").toString(),
            "-Xuse-javac",
            "-Xcompile-java",
        )*/
    }
}