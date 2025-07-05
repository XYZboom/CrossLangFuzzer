package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.serde.gson
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import java.io.File
import java.io.StringWriter
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.io.path.Path
import kotlin.io.path.pathString

@OptIn(ExperimentalStdlibApi::class)
val tempDir = System.getProperty("java.io.tmpdir")!! + "/" + System.nanoTime().toHexString()
val logFile = File(
    System.getProperty("codesmith.logger.outdir")
        ?: throw NullPointerException("System property codesmith.logger.outdir must be set")
).also {
    if (!it.exists()) it.mkdirs()
}

@OptIn(ExperimentalStdlibApi::class)
fun newTempPath(): String {
    return Path(tempDir, System.nanoTime().toHexString()).pathString.also {
        val file = File(it)
        if (!file.exists()) {
            file.mkdirs()
        }
    }
}

class JavaCompilerWrapper() {
    private val compiler: JavaCompiler = ToolProvider.getSystemJavaCompiler()
    private val javaFileManager = compiler.getStandardFileManager(null, null, null)

    fun getJavaFileObjs(sourceFiles: List<File>): Iterable<JavaFileObject> {
        return javaFileManager.getJavaFileObjectsFromFiles(sourceFiles)
    }

    /**
     * @param outputPath the output path of major language classes, and also for java classes.
     */
    fun compileJavaAfterMajorFinished(
        sourceFiles: List<File>,
        outputPath: String,
        extraClasspath: String? = null
    ): String? {
        val javaFileObjs = getJavaFileObjs(sourceFiles)
        return compileJavaAfterMajorFinished(javaFileObjs, outputPath, extraClasspath)
    }

    fun compileJavaAfterMajorFinished(
        javaFileObjs: Iterable<JavaFileObject>,
        outputPath: String,
        extraClasspath: String? = null
    ): String? {
        val javaOutputWriter = StringWriter()
        var classpath = outputPath + File.pathSeparator + System.getProperty("java.class.path")
        if (extraClasspath != null) {
            classpath += File.pathSeparator + extraClasspath
        }
        val task = compiler.getTask(
            javaOutputWriter, javaFileManager, null,
            listOf(
                "-classpath", classpath,
                "-d", outputPath
            ),
            null,
            javaFileObjs
        )
        task.call()
        val javaOutput = javaOutputWriter.toString()
        return javaOutput.ifEmpty { null }
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun recordCompileResult(
    majorLanguage: Language,
    program: IrProgram,
    compileResults: List<CompileResult>,
    minimizedProgram: IrProgram? = null,
    minimizedCompileResults: List<CompileResult>? = null,
) {
    require(compileResults.size == 1 || compileResults.toSet().size != 1)
    val dir = File(logFile, System.currentTimeMillis().toHexString()).mkdirsIfNotExists()
    File("codesmith-trace.log").copyTo(File(dir, "codesmith-trace.log"))
    for (compileResult in compileResults) {
        val (majorResult, javaResult) = compileResult
        if (majorResult != null) {
            File(dir, "${compileResult.version}-error.txt").writeText(majorResult)
        } else if (javaResult != null) {
            File(dir, "${compileResult.version}-java-error.txt").writeText(javaResult)
        }
    }
    if (minimizedProgram != null) {
        File(dir, "main-min.${majorLanguage.extension}")
            .writeText(IrProgramPrinter(majorLanguage).printToSingle(minimizedProgram))
    }
    if (minimizedCompileResults != null) {
        for (compileResult in minimizedCompileResults) {
            val (majorResult, javaResult) = compileResult
            if (majorResult != null) {
                File(dir, "${compileResult.version}-error-min.txt").writeText(majorResult)
            } else if (javaResult != null) {
                File(dir, "${compileResult.version}-java-error-min.txt").writeText(javaResult)
            }
        }
    }
    File(dir, "main.${majorLanguage.extension}").writeText(IrProgramPrinter(majorLanguage).printToSingle(program))
    File(dir, "main.json").writeText(gson.toJson(program))
}

fun recordCompileResult(
    majorLanguage: Language,
    program: IrProgram,
    compileResult: CompileResult,
) {
    recordCompileResult(majorLanguage, program, listOf(compileResult))
}