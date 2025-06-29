package com.github.xyzboom.codesmith

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import kotlin.io.path.Path
import java.util.*
import kotlin.system.exitProcess

object CodeSmithPrinterTestGenerator {
    private val logger = KotlinLogging.logger {}

    private const val TEST_DATA_PATH = "src/testData/printer/"
    private const val TEST_OUTPUT_PATH =
        "src/test/kotlin/com/github/xyzboom/codesmith/printer_old/generated/ClassPrinterTest.kt"
    private const val IGNORE_FILE_NAME = "ignore"
    private const val IR_FILE_NAME = "ir.json"
    private const val JAVA_FILE_NAME = "result.kt"
    private const val KT_FILE_NAME = "result.java"
    private const val MAIN_TEST_CLASS_NAME = "ClassPrinterTest"
    private val sb = StringBuilder()
    private val fileStateStack = Stack<Pair<File, StringBuilder>>()

    /**
     * Invoke when enter a test root directory.
     *
     * A test root always contains a text file named "result".
     *
     * @param dir test root directory
     */
    private fun enterTestRootDir(dir: File) {
        val testNameDir = dir.path.split(File.separator).last()
        val testName = "test_$testNameDir"
        val (file, sb) = fileStateStack.peek()
        require(File(file, testNameDir) == dir)
        val ignoreFile = File(dir, IGNORE_FILE_NAME)
        sb.append(
            """
            |@Test${if (ignoreFile.exists()) "\n@Disabled" else ""}
            |fun $testName() {
            |    doValidate(
            |        ${"\"\"\""}${Path(dir.path, IR_FILE_NAME)}${"\"\"\""},
            |        ${"\"\"\""}${Path(dir.path, JAVA_FILE_NAME)}${"\"\"\""},
            |        ${"\"\"\""}${Path(dir.path, KT_FILE_NAME)}${"\"\"\""}
            |    )
            |}
            |
        """.replaceIndentByMargin(" ".repeat(4))
        )
        sb.append(System.lineSeparator())
    }

    /**
     * invoke when enter sub-test-directory in order to generate test classes.
     *
     * For example, test directory in [TEST_DATA_PATH] is "import/myImportTest1/",
     * the class "Import" inside [MAIN_TEST_CLASS_NAME] while be generated.
     */
    private fun enterSubTestDir(dir: File) {
        fileStateStack.push(dir to StringBuilder())
    }

    private fun CharSequence.prependIndent(indent: String = "    "): String =
        lineSequence()
            .map {
                when {
                    it.isBlank() -> {
                        when {
                            it.length < indent.length -> indent
                            else -> it
                        }
                    }

                    else -> indent + it
                }
            }
            .joinToString("\n")

    private fun leaveSubTestDir(dir: File) {
        val (_, sb) = fileStateStack.pop()
        if (sb.isEmpty()) return
        val (_, parentSb) = fileStateStack.peek()
        val className = dir.path.split(File.separator).last()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val sbNow = StringBuilder()
        sbNow.append(System.lineSeparator())
        sbNow.append("@Nested")
        sbNow.append(System.lineSeparator())
        sbNow.append("""inner class ${className}Test {""")
        sbNow.append(System.lineSeparator())
        sbNow.append(sb)
        sbNow.append(System.lineSeparator())
        sbNow.append("}")
        sbNow.append(System.lineSeparator())
        parentSb.append(sbNow.prependIndent())
    }

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info { "Start CodeSmithPrinterTestGenerator" }
        if (sb.isNotEmpty()) {
            logger.error { "Test Class Has Already Generated!" }
            exitProcess(-1)
        }
        sb.append(
            """
            |// auto generated, do not manually edit!
            |package com.github.xyzboom.codesmith.printer_old.generated
            |
            |import com.github.xyzboom.codesmith.printer_old.BaseClassPrinterTester
            |import org.junit.jupiter.api.Nested
            |import org.junit.jupiter.api.Test
            |import org.junit.jupiter.api.Disabled
            |import java.nio.file.Path
            |
            |class $MAIN_TEST_CLASS_NAME : BaseClassPrinterTester() {
            |
        """.trimMargin()
        )
        val testPath = File(TEST_DATA_PATH)
        fileStateStack.push(testPath to sb)
        val fileTreeWalk = testPath.walkTopDown()
            .onEnter {
                if (it == testPath) return@onEnter true
                if (it.isDirectory && File(it, IR_FILE_NAME).exists()) {
                    enterTestRootDir(it)
                    return@onEnter true
                }
                enterSubTestDir(it)
                return@onEnter true
            }.onLeave {
                if (it == testPath) return@onLeave
                if (it.isDirectory && File(it, IR_FILE_NAME).exists()) {
                    return@onLeave
                }
                leaveSubTestDir(it)
                return@onLeave
            }
        fileTreeWalk.forEach { _ -> }
        sb.append(System.lineSeparator())
        sb.append("}")
        val testOut = File(TEST_OUTPUT_PATH)
        if (testOut.exists()) {
            testOut.deleteRecursively()
        }
        testOut.writeText(sb.toString())
    }
}