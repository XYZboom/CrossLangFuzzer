package com.github.xyzboom.codesmith

import io.github.xyzboom.bf.tree.INode
import com.github.xyzboom.codesmith.bf.generated.ICrossLangFuzzerDefTopDownVisitor
import com.github.xyzboom.codesmith.generator.BfGenerator
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.mutator.impl.IrMutatorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter

private fun doGenerateOld() {
    val printer = IrProgramPrinter()
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

private fun doGenerateNew() {
    val generator = BfGenerator()
    val prog = generator.generate()
    prog.accept(object : ICrossLangFuzzerDefTopDownVisitor<Nothing?> {
        var indentCount = 0
        val indent: String get() = "  ".repeat(indentCount)
        override fun visitNode(node: INode, data: Nothing?) {
            println("${indent}visit: $node")
            indentCount++
            super.visitNode(node, data)
            indentCount--
        }
    }, null)
}

fun main() {
    val temp = System.getProperty("java.io.tmpdir")
    repeat(1) {
        // doGenerateOld()
        doGenerateNew()
    }
}