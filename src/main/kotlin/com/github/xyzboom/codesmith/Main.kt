package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrPrinterToSingleFile
import com.github.xyzboom.codesmith.printer.kt.IrKtFilePrinter

fun main() {
    for (i in 0 until 1) {
        val prog = IrGeneratorImpl(
            config = GeneratorConfig(
                moduleNumRange = 1..1,
                fileNumRange = 1..5,
                packageNumRange = 1..1,
                classNumRange = 3..5
            )
        ).generate()
        val result = IrPrinterToSingleFile(listOf(IrKtFilePrinter(), )).print(prog)
        println(result)
    }
}