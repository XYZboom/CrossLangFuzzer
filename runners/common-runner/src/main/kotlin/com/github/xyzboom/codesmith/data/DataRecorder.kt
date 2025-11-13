package com.github.xyzboom.codesmith.data

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ir.visitors.IrTopDownVisitor
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.inheritanceDepth
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.printer.IrProgramPrinter.Companion.extraSourceFileNames
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.math.max

open class DataRecorder {
    companion object {
        private const val COMPILE_TIMES_KEY = "_compile_times"
        private val logger = KotlinLogging.logger {}
    }

    private val programCountMap = ConcurrentHashMap<String, Int>()
    private val allProgramDataMap = ConcurrentHashMap<String, ProgramData>()
    private val otherDataMap = ConcurrentHashMap<String, Any>()
    val programCount: Map<String, Int> get() = programCountMap
    val programData: Map<String, ProgramData> get() = allProgramDataMap
    fun addProgram(key: String, program: IrProgram) {
        programCountMap.merge(key, 1, Int::plus)
        val data = processProgram(program)
        allProgramDataMap.merge(key, data) { old, new ->
            old + new
        }
    }

    fun <T : Any> addData(key: String, data: T) {
        otherDataMap[key] = data
    }

    fun <T : Any> mergeData(key: String, data: T, mergeFunc: (T, T) -> T) {
        @Suppress("UNCHECKED_CAST")
        otherDataMap.merge(key, data, mergeFunc as (Any, Any) -> Any?)
    }

    fun <T : Any> getData(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return otherDataMap[key] as? T?
    }

    fun recordCompiler(compiler: ICompiler): ICompiler {
        return object : ICompiler {
            override fun compile(program: IrProgram): CompileResult {
                val plus: (Int, Int) -> Int = Int::plus
                @Suppress("UNCHECKED_CAST")
                otherDataMap.merge(COMPILE_TIMES_KEY, 1, plus as (Any, Any) -> Any?)
                return compiler.compile(program)
            }
        }
    }

    fun recordCompilers(compilers: List<ICompiler>): List<ICompiler> {
        return compilers.map { recordCompiler(it) }
    }

    fun getCompileTimes(): Int {
        return getData(COMPILE_TIMES_KEY) ?: 0
    }

    class ProgramDataVisitor : IrTopDownVisitor<ProgramData>() {
        override fun visitClassDeclaration(classDeclaration: IrClassDeclaration, data: ProgramData) {
            data.methodCount += classDeclaration.functions.count { !it.isOverrideStub }
            val width = classDeclaration.implementedTypes.size + (if (classDeclaration.superType != null) 1 else 0)
            data.maxInheritanceWidth = max(data.maxInheritanceWidth, width)
            // this avg is avg of max value per program
            data.avgInheritanceWidth = data.maxInheritanceWidth.toFloat()
            val depth = classDeclaration.inheritanceDepth
            data.maxInheritanceDepth = max(data.maxInheritanceDepth, depth)
            data.avgInheritanceDepth = data.maxInheritanceDepth.toFloat()
            data.classCount++
            data.typeParameterCount += classDeclaration.typeParameters.size
            super.visitClassDeclaration(classDeclaration, data)
        }

        override fun visitFunctionDeclaration(functionDeclaration: IrFunctionDeclaration, data: ProgramData) {
            if (functionDeclaration.isOverrideStub) {
                return super.visitFunctionDeclaration(functionDeclaration, data)
            }
            data.typeParameterCount += functionDeclaration.typeParameters.size
            data.parameterCount += functionDeclaration.parameterList.parameters.size
            super.visitFunctionDeclaration(functionDeclaration, data)
        }
    }

    protected fun processProgram(program: IrProgram): ProgramData {
        val data = ProgramData(programCount = 1)
        program.accept(ProgramDataVisitor(), data)
        data.lineOfCode = IrProgramPrinter(printStub = false).print(program).values
            .filter {
                it !in extraSourceFileNames
            }.sumOf { it.lines().count() }
        return data
    }
}