package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrGeneratorImpl
import com.github.xyzboom.codesmith.generator.impl.IrMutatorImpl
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.visitor.IrTopDownVisitor
import com.github.xyzboom.codesmith.printer.IrPrinterToSingleFile
import com.github.xyzboom.codesmith.printer.java.IrJavaFilePrinter
import com.github.xyzboom.codesmith.printer.kt.IrKtFilePrinter

fun main() {
    for (i in 0 until 10000) {
        val prog = IrGeneratorImpl(
            config = GeneratorConfig(
                moduleNumRange = 1..1,
                fileNumRange = 1..5,
                packageNumRange = 1..1,
                classNumRange = 3..5,
                constructorNumRange = 1..1
            )
        ).generate()
        val visitor = object: IrTopDownVisitor<Nothing?> {
            override fun visitElement(element: IrElement, data: Nothing?) {
                println(element)
                super.visitElement(element, data)
            }
        }
        visitor.visitElement(prog, null)
        val result = IrPrinterToSingleFile(listOf(IrJavaFilePrinter(), )).print(prog)
        println(result)
        IrMutatorImpl().mutate(prog)
        println(IrPrinterToSingleFile(listOf(IrJavaFilePrinter(), )).print(prog))
    }
}