package com.github.xyzboom.codesmith.scala

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import dotty.tools.dotc.core.Contexts
import dotty.tools.dotc.interfaces.Diagnostic
import dotty.tools.dotc.interfaces.SourcePosition
import dotty.tools.dotc.reporting.Reporter
import scala.jdk.CollectionConverters
import scala.reflect.internal.util.Position
import scala.tools.nsc.CompilerCommand
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.FilteringReporter
import scala.tools.nsc.settings.ScalaVersion
import java.io.File
import java.io.StringWriter
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull

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

class NoMessageScala3Reporter : Reporter() {
    override fun doReport(dia: dotty.tools.dotc.reporting.Diagnostic, context: Contexts.Context) {
    }
}

class NoMessageScala2Reporter(
    private val settings: Settings
) : FilteringReporter() {
    override fun settings(): Settings = settings

    private val errors: MutableList<Pair<Position, String>> = mutableListOf()
    val errorsMessage: String?
        get() = if (errors.isEmpty()) {
            null
        } else {
            errors.joinToString("\n") { "${it.first}: ${it.second}" }
        }

    override fun doReport(pos: Position, msg: String, severity: Severity) {
        if (severity is `ERROR$`) {
            errors.add(Pair(pos, msg))
        }
    }
}

fun compileScala3WithJava(
    printer: IrProgramPrinter,
    program: IrProgram
): CompileResult {
    val tempPath = newTempPath()
    val outDir = File(tempPath, "out-scala3").mkdirsIfNotExists()
    val fileMap = printer.print(program)
    val allSourceFiles = fileMap.map { Path(tempPath, it.key).pathString }
    printer.saveFileMap(fileMap, tempPath)
    val reporter = dotty.tools.dotc.Main.process(
        (allSourceFiles + listOf("-usejavacp", "-d", outDir.absolutePath)).toTypedArray(),
        NoMessageScala3Reporter(), null
    )
    val compileScalaResult = if (reporter.hasErrors()) {
        val allErrors = CollectionConverters.SeqHasAsJava(reporter.allErrors()).asJava()
        allErrors.joinToString("\n") { it.msg }
    } else null
    val compileJavaResult = compileJavaAfterScalaFinished(
        allSourceFiles.filter { it.endsWith(".java") }.map { File(it) },
        outDir.absolutePath
    )
    return CompileResult(compileScalaResult, compileJavaResult)
}

fun compileScala2WithJava(
    printer: IrProgramPrinter,
    program: IrProgram,
): CompileResult {
    val tempPath = newTempPath()
    val outDir = File(tempPath, "out-scala2").mkdirsIfNotExists()
    val fileMap = printer.print(program)
    val allSourceFiles = fileMap.map { Path(tempPath, it.key).pathString }
    printer.saveFileMap(fileMap, tempPath)
    val settings = Settings()
    settings.usejavacp().`v_$eq`(true)
    // enable scala2 to compile scala3
    settings.source().`v_$eq`(ScalaVersion.apply("3"))
    val reporter = NoMessageScala2Reporter(settings)
    val global = Global(settings, reporter)
    val asScala = CollectionConverters.IterableHasAsScala(
        allSourceFiles + listOf("-d", outDir.absolutePath)
    ).asScala().toList()
    val command = CompilerCommand(asScala, settings)

    val main = scala.tools.nsc.MainClass()
    main.`settings_$eq`(settings)
    main.`command_$eq`(command)
    main.`reporter_$eq`(reporter)
    main.doCompile(global)
    val compileScalaResult = reporter.errorsMessage
    val compileJavaResult = compileJavaAfterScalaFinished(
        allSourceFiles.filter { it.endsWith(".java") }.map { File(it) },
        outDir.absolutePath
    )
    return CompileResult(compileScalaResult, compileJavaResult)
}

/**
 * @param outputPath the output path of scala classes, and also for java classes.
 */
fun compileJavaAfterScalaFinished(
    sourceFiles: List<File>,
    outputPath: String
): String? {
    val compiler: JavaCompiler = ToolProvider.getSystemJavaCompiler()
    val javaFileManager = compiler.getStandardFileManager(null, null, null)
    val javaFileObjs = javaFileManager.getJavaFileObjectsFromFiles(sourceFiles)
    val javaOutputWriter = StringWriter()
    val task = compiler.getTask(
        javaOutputWriter, javaFileManager, null,
        listOf("-classpath", outputPath + File.pathSeparator + System.getProperty("java.class.path"), "-d", outputPath),
        null,
        javaFileObjs
    )
    task.call()
    val javaOutput = javaOutputWriter.toString()
    return javaOutput.ifEmpty { null }
}
