package com.github.xyzboom.codesmith.printerOld

import com.github.xyzboom.codesmith.generator.impl.IrGeneratorOldImpl
import com.github.xyzboom.codesmith.irOld.types.IrFileType
import com.github.xyzboom.codesmith.printerOld.java.IrJavaFilePrinter
import com.github.xyzboom.codesmith.printerOld.kt.IrKtFilePrinter
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class IrPrinterToSingleFileTest {
    private val printer = IrPrinterToSingleFile(mapOf(
        IrFileType.JAVA to IrJavaFilePrinter(),
        IrFileType.KOTLIN to IrKtFilePrinter()
    ))
    private val generator = IrGeneratorOldImpl()

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