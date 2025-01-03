package com.github.xyzboom.codesmith.scala3

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.mutator.MutatorConfig
import com.github.xyzboom.codesmith.mutator.impl.IrMutatorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.pathString
import dotty.tools.dotc.core.Contexts
import dotty.tools.dotc.interfaces.Diagnostic
import dotty.tools.dotc.interfaces.SourcePosition
import dotty.tools.dotc.reporting.Reporter
import scala.jdk.CollectionConverters
import java.io.StringWriter
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess
import kotlin.time.measureTime

@OptIn(ExperimentalStdlibApi::class)
val tempDir = System.getProperty("java.io.tmpdir")!! + "/" + System.nanoTime().toHexString()
val logFile = File(System.getProperty("codesmith.logger.outdir")).also {
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

val SourcePosition?.msg: String
    get() = if (this == null) {
        "no position"
    } else {
        "${source().path()}:${line()}:${column()}"
    }

val Diagnostic.msg: String
    get() {
        return "${position().getOrNull().msg} ${message()}"
    }

class NoMessageReporter : Reporter() {
    override fun doReport(dia: dotty.tools.dotc.reporting.Diagnostic, context: Contexts.Context) {
    }
}

fun doCompileAndRecordResult(
    printer: IrProgramPrinter, fileMap: Map<String, String>,
    shouldFail: Boolean = false,
    stopOnErrors: Boolean = false
) {
    val tempPath = newTempPath()
    val outDir = File(tempPath, "out")
    if (!outDir.exists()) {
        outDir.mkdirs()
    }
    val allSourceFiles = fileMap.map { Path(tempPath, it.key).pathString }
    printer.saveFileMap(fileMap, tempPath)
    val reporter = dotty.tools.dotc.Main.process(
        (allSourceFiles/*.filter { it.endsWith(".scala") }*/
                + listOf("-usejavacp", "-d", outDir.absolutePath)).toTypedArray(),
        NoMessageReporter(), null
    )
    if (reporter.hasErrors()) {
        val allErrors = CollectionConverters.SeqHasAsJava(reporter.allErrors()).asJava()
        val compileResult = allErrors.joinToString("\n") { it.msg } to null
        if (!shouldFail) {
            recordCompileResult(compileResult, false, tempPath)
            if (stopOnErrors) {
                exitProcess(-1)
            }
            return
        }
    }

    val compiler: JavaCompiler = ToolProvider.getSystemJavaCompiler()
    val javaFileManager = compiler.getStandardFileManager(null, null, null)
    val javaFileObjs = javaFileManager.getJavaFileObjectsFromFiles(allSourceFiles.filter {
        it.endsWith(".java")
    }.map { File(it) })
    val javaOutputWriter = StringWriter()
    val task = compiler.getTask(
        javaOutputWriter, javaFileManager, null,
        listOf("-classpath", outDir.absolutePath, "-d", outDir.absolutePath),
        null,
        javaFileObjs
    )
    task.call()
    val javaOutput = javaOutputWriter.toString()
    val compileResult = null to javaOutput.ifEmpty { null }
    val fail = javaOutput.isNotEmpty()
    if (fail) {
        if (!shouldFail) {
            recordCompileResult(compileResult, false, tempPath)
            if (stopOnErrors) {
                exitProcess(-1)
            }
        }
    } else {
        if (shouldFail) {
            recordCompileResult(compileResult, true, tempPath)
            if (stopOnErrors) {
                exitProcess(-1)
            }
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun recordCompileResult(
    compileResult: Pair<String?, String?>,
    shouldFail: Boolean,
    from: String
) {
    val (scalaResult, javaResult) = compileResult
    if (scalaResult == null && javaResult == null) {
        if (!shouldFail) {
            return
        }
    }
    val dir = File(logFile, System.currentTimeMillis().toHexString()).also {
        if (!it.exists()) it.mkdirs()
    }
    File("codesmith-trace.log").copyTo(File(dir, "codesmith-trace.log"))
    File(from).copyRecursively(dir)
    if (scalaResult != null) {
        File(dir, "scala-error.txt").writeText(scalaResult)
    } else if (javaResult != null ){
        File(dir, "java-error.txt").writeText(javaResult)
    } else {
        File(dir, "should-fail").createNewFile()
    }
}

fun doOneRound(stopOnErrors: Boolean = false) {
    val printer = IrProgramPrinter(false)
    val generator = IrDeclGeneratorImpl(
        GeneratorConfig(
            classMemberIsPropertyWeight = 0
        ),
        majorLanguage = Language.SCALA3,
    )
    val prog = generator.genProgram()
    val fileMap = printer.print(prog)
    doCompileAndRecordResult(printer, fileMap, false, stopOnErrors)
    val mutator = IrMutatorImpl(
        generator = generator,
        config = MutatorConfig(
            mutateGenericArgumentInParentWeight = 1,
            removeOverrideMemberFunctionWeight = 0,
            mutateGenericArgumentInMemberFunctionParameterWeight = 1,
            mutateParameterNullabilityWeight = 0
        )
    )
    if (mutator.mutate(prog)) {
        // todo this
        // doCompileAndRecordResult(printer, printer.print(prog), true, stopOnErrors)
    }
}

fun main() {
    println("start at: $tempDir")
    var i = 0
    while (true) {
        val dur = measureTime { doOneRound() }
        println("${i++}: $dur")
    }
}