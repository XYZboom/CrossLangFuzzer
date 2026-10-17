package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.IrModule
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
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
    majorLanguage: Language = Language.KOTLIN,
    private val printStub: Boolean = true,
) : IrPrinter<IrProgram, Map<String, String>> {
    private val majorLanguage = majorLanguage
    private lateinit var javaClassPrinter: JavaIrClassPrinter
    private val ktClassPrinter = KtIrClassPrinter(printStub = printStub)
    private val scalaClassPrinter = ScalaIrClassPrinter(printStub = printStub)

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
        const val MAIN_KT_NAME = "main.kt"
        val extraSourceFileNames = listOf(
            MAIN_KT_NAME, *extraJavaFile.keys.toTypedArray()
        )
    }

    override fun print(element: IrProgram): Map<String, String> {
        val result = mutableMapOf<String, String>()
        javaClassPrinter = JavaIrClassPrinter(majorLanguage, printStub = printStub)
        for (clazz in element.classes) {
            val (fileName, content) = when (clazz.language) {
                Language.KOTLIN -> "${clazz.name}.kt" to ktClassPrinter.print(clazz)
                Language.JAVA -> "${clazz.name}.java" to javaClassPrinter.print(clazz)
                Language.GROOVY4, Language.GROOVY5 -> "${clazz.name}.groovy" to javaClassPrinter.print(clazz)
                Language.SCALA -> "${clazz.name}.scala" to scalaClassPrinter.print(clazz)
            }
            result[fileName] = content
        }
        val javaTopLevelContent = javaClassPrinter.printTopLevelFunctionsAndProperties(element)
        val ktTopLevelContent = ktClassPrinter.printTopLevelFunctionsAndProperties(element)
        if (element.classes.any { it.language == Language.JAVA }) {
            result["${JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME}.java"] = javaTopLevelContent
            result.putAll(extraJavaFile)
        }
        if (majorLanguage == Language.KOTLIN) {
            result[MAIN_KT_NAME] = "${ktTopLevelContent}\n" +
                    "fun box(): String {\n" +
                    "\treturn \"OK\"\n" +
                    "}\n" +
                    "fun main(args: Array<String>) {\n" +
                    "}"
        }
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
            "// JVM_DEFAULT_MODE: no-compatibility\n" +
                    "// TARGET_BACKEND: JVM\n" +
                    "// JVM_TARGET: 1.8\n"
        )
        val effectiveModules: List<IrModule> = if (element.modules.isEmpty()) {
            listOf(buildModule {
                name = "main"
                classes.addAll(element.classes)
            })
        } else {
            element.modules
        }

        val mainModule = effectiveModules.firstOrNull { it.name == "main" } ?: effectiveModules.last()
        val firstJavaModule = effectiveModules.firstOrNull { mod ->
            mod.classes.any { it.language == Language.JAVA }
        }
        val lastJavaModule = effectiveModules.lastOrNull { mod ->
            mod.classes.any { it.language == Language.JAVA }
        }

        for (module in effectiveModules) {
            val depNames = module.dependencies.map { it.name }
            val depString = if (depNames.isEmpty()) "" else "(${depNames.joinToString(", ")})"
            sb.append("// MODULE: ${module.name}$depString\n")

            val modFileNames = buildSet {
                for (clazz in module.classes) {
                    add("${clazz.name}.${clazz.language.extension}")
                }
                if (module === firstJavaModule) {
                    addAll(extraJavaFile.keys)
                }
                if (module === lastJavaModule) {
                    add("${JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME}.java")
                }
                if (module === mainModule) {
                    add(MAIN_KT_NAME)
                }
            }
            appendFiles(sb, map.filterKeys { it in modFileNames })
        }
        return sb.toString()
    }

    private fun appendFiles(sb: StringBuilder, files: Map<String, String>) {
        for ((key, value) in files) {
            sb.append("// FILE: $key\n")
            sb.append(value)
            sb.append("\n")
        }
    }
}
