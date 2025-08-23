package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.printer.clazz.JavaIrClassPrinter
import com.github.xyzboom.codesmith.printer.clazz.KtIrClassPrinter
import com.github.xyzboom.codesmith.printer.clazz.ScalaIrClassPrinter
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import java.io.File

/**
 * Printer for whole program.
 * Key of result is file name and value is file content.
 */
@Suppress("unused")
class IrProgramPrinter(
    private val majorLanguage: Language = Language.KOTLIN
) : IrPrinter<IrProgram, Map<String, String>> {
    private lateinit var javaClassPrinter: JavaIrClassPrinter
    private val ktClassPrinter = KtIrClassPrinter()
    private val scalaClassPrinter = ScalaIrClassPrinter()

    companion object {
        private val extraJavaFile = buildMap {
            put(
                "org/jetbrains/annotations/NotNull.java",
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
                "org/jetbrains/annotations/Nullable.java",
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
    }

    override fun print(element: IrProgram): Map<String, String> {
        val result = mutableMapOf<String, String>()
        javaClassPrinter = JavaIrClassPrinter(majorLanguage)
        for (clazz in element.classes) {
            // todo
//            clazz.changeLanguageIfNotSuitable()
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
        if (majorLanguage == Language.KOTLIN) {
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
            file.parentFile.mkdirsIfNotExists()
            file.createNewFile()
            file.writeText(content)
        }
    }

    fun printToSingle(element: IrProgram): String {
        val map = print(element)
        val sb = StringBuilder(
            "// JVM_DEFAULT_MODE: all\n" +
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