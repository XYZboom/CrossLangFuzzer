package com.github.xyzboom.codesmith.printer

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.xyzboom.codesmith.ir_old.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.printer.clazz.JavaIrClassPrinter
import com.github.xyzboom.codesmith.printer.clazz.KtIrClassPrinter
import com.github.xyzboom.codesmith.serde.defaultIrMapper
import org.opentest4j.MultipleFailuresError
import java.io.File
import kotlin.test.assertEquals

abstract class BaseClassPrinterTester {

    fun doValidate(
        irPath: String,
        ktPath: String,
        javaPath: String
    ) {
        val irRaw = File(irPath).readText()
        val javaFile = File(javaPath)
        val ktFile = File(ktPath)
        val javaExpected = javaFile.readText()
        val ktExpected = ktFile.readText()
        val clazz = defaultIrMapper.readValue<IrClassDeclaration>(irRaw)
        val javaPrinter = JavaIrClassPrinter()
        val ktPrinter = KtIrClassPrinter()
        val javaResult = javaPrinter.print(clazz)
        val ktResult = ktPrinter.print(clazz)
        var javaUnexpected: Throwable? = null
        var ktUnexpected: Throwable? = null
        try {
            assertEquals(
                javaExpected, javaResult,
                "Java printer prints unexpected result, see file at: file://${javaFile.absolutePath}"
            )
        } catch (e: Throwable) {
            javaUnexpected = e
        }

        try {
            assertEquals(
                ktExpected, ktResult,
                "Kotlin printer prints unexpected result, see file at: file://${ktFile.absolutePath}"
            )
        } catch (e: Throwable) {
            ktUnexpected = e
        }
        if (javaUnexpected != null && ktUnexpected != null) {
            throw MultipleFailuresError(
                "Java and Kotlin are both unexpected!",
                listOf(javaUnexpected, ktUnexpected)
            )
        } else if (javaUnexpected != null){
            throw javaUnexpected
        } else if (ktUnexpected != null) {
            throw ktUnexpected
        }
    }
}