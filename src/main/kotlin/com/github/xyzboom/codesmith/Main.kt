package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.serde.gson
import com.github.xyzboom.codesmith.ir.types.builtin.ALL_BUILTINS
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.printer.IrProgramPrinter


fun main() {
//    println(IrAny)
    val predicate: (IrBuiltInType) -> Boolean = { true }
    ALL_BUILTINS.filter(predicate)
//    exitProcess(0)
    val temp = System.getProperty("java.io.tmpdir")
    val printer = IrProgramPrinter()
    for (i in 0 until 1) {
        val generator = IrDeclGenerator(
            GeneratorConfig()
        )
        val prog = generator.genProgram()
        println(prog)
        val fileContent = printer.printToSingle(prog)
        println(fileContent)
        val jsonString = gson.toJson(prog, IrProgram::class.java)
        println(jsonString)
        val prog1 = gson.fromJson(jsonString, IrProgram::class.java)
        val fileContent1 = printer.printToSingle(prog1)
        println(fileContent1)
        /*val mutator = IrMutatorImpl(generator = generator)
        mutator.mutate(prog)
        val fileContent1 = printer.printToSingle(prog)
        println("-----------------")
        println(fileContent1)*/
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