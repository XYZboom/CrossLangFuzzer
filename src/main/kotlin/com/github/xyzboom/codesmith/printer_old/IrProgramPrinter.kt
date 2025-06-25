package com.github.xyzboom.codesmith.printer_old

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir_old.IrProgram
import com.github.xyzboom.codesmith.printer_old.clazz.JavaIrClassPrinter
import com.github.xyzboom.codesmith.printer_old.clazz.KtIrClassPrinter
import com.github.xyzboom.codesmith.printer_old.clazz.ScalaIrClassPrinter
import java.io.File

/**
 * Printer for whole program.
 * Key of result is file name and value is file content.
 */
@Suppress("unused")
class IrProgramPrinter(
    /**
     * Print a file that contains a `box` function.
     * Use for Kotlin box test.
     */
    private val printBox: Boolean = true
) : IrPrinter<IrProgram, Map<String, String>> {
    private lateinit var javaClassPrinter: JavaIrClassPrinter
    private val ktClassPrinter = KtIrClassPrinter()
    private val scalaClassPrinter = ScalaIrClassPrinter()

    private val extraJavaFile = buildMap {
        put(
            "NotNull.java",
            "package org.jetbrains.annotations;\n" +
                    "\n" +
                    "import java.lang.annotation.*;\n" +
                    "\n" +
                    "// org.jetbrains.annotations used in the compiler is version 13, whose @NotNull does not support the TYPE_USE target (version 15 does).\n" +
                    "// We're using our own @org.jetbrains.annotations.NotNull for testing purposes.\n" +
                    "@Documented\n" +
                    "@Retention(RetentionPolicy.CLASS)\n" +
                    "@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})\n" +
                    "public @interface NotNull {\n" +
                    "}"
        )
        put(
            "Nullable.java",
            "package org.jetbrains.annotations;\n" +
                    "\n" +
                    "import java.lang.annotation.*;\n" +
                    "\n" +
                    "// org.jetbrains.annotations used in the compiler is version 13, whose @Nullable does not support the TYPE_USE target (version 15 does).\n" +
                    "// We're using our own @org.jetbrains.annotations.Nullable for testing purposes.\n" +
                    "@Documented\n" +
                    "@Retention(RetentionPolicy.CLASS)\n" +
                    "@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})\n" +
                    "public @interface Nullable {\n" +
                    "}"
        )
    }

    override fun print(element: IrProgram): Map<String, String> {
        val result = mutableMapOf<String, String>()
        javaClassPrinter = JavaIrClassPrinter(element.majorLanguage)
        for (clazz in element.classes) {
            clazz.changeLanguageIfNotSuitable()
            val (fileName, content) = when (clazz.language) {
                Language.KOTLIN -> "${clazz.name}.kt" to ktClassPrinter.print(clazz)
                Language.JAVA -> "${clazz.name}.java" to javaClassPrinter.print(clazz)
                Language.GROOVY4, Language.GROOVY5 -> "${clazz.name}.groovy" to javaClassPrinter.print(clazz)
                Language.SCALA -> "${clazz.name}.scala" to scalaClassPrinter.print(clazz)
                else -> TODO("The language ${clazz.language} has not been implemented yet")
            }
            result[fileName] = content
        }
        val javaTopLevelContent = javaClassPrinter.printTopLevelFunctionsAndProperties(element)
        val ktTopLevelContent = ktClassPrinter.printTopLevelFunctionsAndProperties(element)
        result["${JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME}.java"] = javaTopLevelContent
        if (printBox) {
            result["main.kt"] = "${ktTopLevelContent}\n" +
                    "fun box(): String {\n" +
                    "\treturn \"OK\"\n" +
                    "}\n" +
                    "fun main(args: Array<String>) {\n" +
                    "}"
        }
        result.putAll(extraJavaFile)
        return result
    }

    fun saveTo(path: String, program: IrProgram) {
        val saveDir = File(path)
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }
        val fileMap = print(program)
        saveFileMap(fileMap, path)
    }

    fun saveFileMap(fileMap: Map<String, String>, path: String) {
        for ((fileName, content) in fileMap) {
            val file = File(path, fileName)
            file.createNewFile()
            file.writeText(content)
        }
    }

    fun printToSingle(element: IrProgram): String {
        val map = print(element)
        val sb = StringBuilder(
            "// JVM_DEFAULT_MODE: enable\n" +
                    "// TARGET_BACKEND: JVM\n" +
                    "// JVM_TARGET: 1.8\n"
        )
        for ((key, value) in map) {
            sb.append("// FILE: $key\n")
            sb.append(value)
            sb.append("\n")
        }
        return sb.toString()
    }
}