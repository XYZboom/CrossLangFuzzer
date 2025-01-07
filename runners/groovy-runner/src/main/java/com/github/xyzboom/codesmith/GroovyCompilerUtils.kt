package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

class GroovyCompilerWrapper private constructor(
    resourcePath: String
) {
    companion object {
        val groovy4Compiler = GroovyCompilerWrapper("groovy-4.0.24.jar")
        val groovy5Compiler = GroovyCompilerWrapper("groovy-5.0.0-alpha-11.jar")
    }

    private val jarUrl: URL = ClassLoader.getSystemClassLoader().getResource(resourcePath)!!
    private val classLoader = URLClassLoader(arrayOf(jarUrl), ClassLoader.getSystemClassLoader())
    private val javaAwareCompilationUnitClass =
        classLoader.loadClass("org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit")
    private val compilationUnitClass = classLoader.loadClass("org.codehaus.groovy.control.CompilationUnit")
    private val compilerConfigClass = classLoader.loadClass("org.codehaus.groovy.control.CompilerConfiguration")
    private val fileSystemCompilerClass = classLoader.loadClass("org.codehaus.groovy.tools.FileSystemCompiler")
    private val doCompilationMethod = fileSystemCompilerClass.getMethod(
        "doCompilation", compilerConfigClass, compilationUnitClass, Array<String>::class.java
    )
    private val groovyClassLoaderClass = classLoader.loadClass("groovy.lang.GroovyClassLoader")
    private val groovyClassLoader =
        groovyClassLoaderClass.getConstructor(ClassLoader::class.java).newInstance(classLoader)
    private val compilerConfigConstructor = compilerConfigClass.getConstructor()
    private val setTargetDirectoryMethod = compilerConfigClass.getMethod("setTargetDirectory", String::class.java)
    private val compilationUnitConstructor = javaAwareCompilationUnitClass
        .getConstructor(compilerConfigClass, groovyClassLoaderClass)
    private val setJointCompilationOptionsMethod =
        compilerConfigClass.getMethod("setJointCompilationOptions", Map::class.java)
    private val fsc = classLoader.loadClass("org.codehaus.groovy.tools.FileSystemCompiler")
    private val commandLineCompileMethod = fsc.getMethod("commandLineCompile", Array<String>::class.java)

    fun compileGroovyWithJava(
        printer: IrProgramPrinter,
        program: IrProgram
    ): CompileResult {
        val tempPath = newTempPath()
        val outDir = File(tempPath, "out-scala3").mkdirsIfNotExists()
        val fileMap = printer.print(program)
        printer.saveFileMap(fileMap, tempPath)
        val allSourceFiles = fileMap.map { Path(tempPath, it.key).pathString }
        val compilerConfig = compilerConfigConstructor.newInstance()
        setTargetDirectoryMethod.invoke(compilerConfig, outDir.absolutePath)
        setJointCompilationOptionsMethod.invoke(compilerConfig, mutableMapOf<String, Any>(
            "stubDir" to createTempDirectory("groovy").toFile()
        ))
        val compilationUnit =
            compilationUnitConstructor.newInstance(compilerConfig, groovyClassLoader)
        val groovyResult = try {
            doCompilationMethod.invoke(null, compilerConfig, compilationUnit, allSourceFiles.toTypedArray())
            null
        } catch (e: InvocationTargetException) {
            e.cause!!.message
        }
        if (groovyResult == null) {
            return CompileResult(null, null)
        }
        return if (groovyResult.contains("javac")) {
            CompileResult(null, groovyResult)
        } else {
            CompileResult(groovyResult, null)
        }
    }
}
