package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.generator.IrGeneratorImpl
import com.github.xyzboom.codesmith.printer.kt.IrKtFilePrinter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IrSingleFilePrinterTest {
    private val printer = IrPrinterToSingleFile(listOf(IrKtFilePrinter()))
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
        assertEquals("// MODULE: A\n// MODULE: B(A)\n// MODULE: C(A,B)\n", printer.print(prog))
    }
}