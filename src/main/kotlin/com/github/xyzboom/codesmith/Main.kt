package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrPrinterToSingleFile
import com.github.xyzboom.codesmith.printer.java.IrJavaFilePrinter
import com.github.xyzboom.codesmith.printer.kt.IrKtFilePrinter

fun main() {
    for (i in 0 until 100) {
        val prog = IrGeneratorImpl(config = GeneratorConfig(moduleNumRange = 8..8)).generate()
        val result = IrPrinterToSingleFile(listOf(IrKtFilePrinter(), IrJavaFilePrinter())).print(prog)
        println(result)
    }
}