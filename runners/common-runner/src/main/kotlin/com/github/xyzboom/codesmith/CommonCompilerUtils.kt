package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.Language
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
    sourceSingleFileContent: String,
    compileResult: CompileResult,
) {
    val (scalaResult, javaResult) = compileResult
    val dir = File(logFile, System.currentTimeMillis().toHexString()).mkdirsIfNotExists()
    File("codesmith-trace.log").copyTo(File(dir, "codesmith-trace.log"))
    if (scalaResult != null) {
        File(dir, "${compileResult.version}-error.txt").writeText(scalaResult)
    } else if (javaResult != null) {
        File(dir, "java-error.txt").writeText(javaResult)
    }
    File(dir, "main.${majorLanguage.extension}").writeText(sourceSingleFileContent)
}