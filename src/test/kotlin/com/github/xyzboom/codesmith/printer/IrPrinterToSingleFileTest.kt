package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.generator.impl.IrGeneratorImpl
import com.github.xyzboom.codesmith.ir.types.IrFileType
import com.github.xyzboom.codesmith.printer.java.IrJavaFilePrinter
import com.github.xyzboom.codesmith.printer.kt.IrKtFilePrinter
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class IrPrinterToSingleFileTest {
    private val printer = IrPrinterToSingleFile(mapOf(
        IrFileType.JAVA to IrJavaFilePrinter(),
        IrFileType.KOTLIN to IrKtFilePrinter()
    ))
    private val generator = IrGeneratorImpl()

    @Test
    fun testOnlyModuleAndFile() {
        val prog = with(generator) {
            program {
                val mA = module("A")
                val mB = module("B")
                val mC = module("C")
                mB.dependsOn(mA)
                mC.dependsOn(listOf(mA, mB))
            }
        }
        assertEquals("// MODULE: A\n// MODULE: B(A)\n// MODULE: C(A, B)\n", printer.print(prog))
    }

    @Test
    fun visitProgram() {
        val prog = with(generator) {
            program {
                val mB = module("B")
                val mC = module("C")
                val mA = module("A")
                mB.dependsOn(mA)
                mC.dependsOn(listOf(mA, mB))
            }
        }
        assertEquals("// MODULE: A\n// MODULE: B(A)\n// MODULE: C(A, B)\n", printer.print(prog))
    }
}