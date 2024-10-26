package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrGeneratorImpl
import com.github.xyzboom.codesmith.mutator.impl.IrMutatorImpl
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.types.IrFileType
import com.github.xyzboom.codesmith.ir.visitor.IrTopDownVisitor
import com.github.xyzboom.codesmith.mutator.MutatorConfig
import com.github.xyzboom.codesmith.printer.IrPrinterToSingleFile
import com.github.xyzboom.codesmith.printer.java.IrJavaFilePrinter
import com.github.xyzboom.codesmith.printer.kt.IrKtFilePrinter

fun main() {
    for (i in 0 until 100) {
        val prog = IrGeneratorImpl(
            config = GeneratorConfig(
                moduleNumRange = 1..5,
                fileNumRange = 3..5,
                packageNumRange = 1..5,
                classNumRange = 3..5,
                constructorNumRange = 1..2
            )
        ).generate()
        val visitor = object: IrTopDownVisitor<Nothing?> {
            override fun visitElement(element: IrElement, data: Nothing?) {
                println(element)
                super.visitElement(element, data)
            }
        }
//        visitor.visitElement(prog, null)
        val result = IrPrinterToSingleFile(
            mapOf(
                IrFileType.JAVA to IrJavaFilePrinter(),
                IrFileType.KOTLIN to IrKtFilePrinter()
            )
        ).print(prog)
        println(result)
        println(IrMutatorImpl(
            config = MutatorConfig(
                ktExposeKtInternal = false,
                constructorSuperCallInternal = true
            )
        ).mutate(prog))
        println(IrPrinterToSingleFile(mapOf(
            IrFileType.JAVA to IrJavaFilePrinter(),
            IrFileType.KOTLIN to IrKtFilePrinter()
        )).print(prog))
    }
}