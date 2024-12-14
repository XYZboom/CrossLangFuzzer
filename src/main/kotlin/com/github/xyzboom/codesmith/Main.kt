package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.impl.IrGeneratorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter

fun main() {
    for (i in 0 until 1) {
        val prog = IrGeneratorImpl().genProgram()
        val fileMap = IrProgramPrinter().print(prog)
        for ((fileName, file) in fileMap) {
            println(fileName)
            println(file)
        }
    }
}