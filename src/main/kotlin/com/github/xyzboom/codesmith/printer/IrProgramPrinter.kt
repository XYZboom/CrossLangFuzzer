package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.printer.java.JavaIrClassPrinter
import com.github.xyzboom.codesmith.printer.kt.KtIrClassPrinter
import java.io.File

/**
 * Printer for whole program.
 * Key of result is file name and value is file content.
 */
class IrProgramPrinter: IrPrinter<IrProgram, Map<String, String>> {
    private val javaClassPrinter = JavaIrClassPrinter()
    private val ktClassPrinter = KtIrClassPrinter()

    override fun print(element: IrProgram): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (clazz in element.classes) {
            val (fileName, content) = when (clazz.language) {
                Language.KOTLIN -> "${clazz.name}.kt" to ktClassPrinter.print(clazz)
                Language.JAVA -> "${clazz.name}.java" to javaClassPrinter.print(clazz)
            }
            result[fileName] = content
        }
        result["main.kt"] = "fun main(args: Array<String>) {\n}"
        return result
    }

    fun saveTo(path: String, program: IrProgram) {
        val saveDir = File(path)
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }
        val fileMap = print(program)
        for ((fileName, content) in fileMap) {
            val file = File(path, fileName)
            file.createNewFile()
            file.writeText(content)
        }
    }
}