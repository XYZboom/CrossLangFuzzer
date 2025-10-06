package com.github.xyzboom.codesmith.data

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ir.visitors.IrTopDownVisitor
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
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
                otherDataMap.merge(COMPILE_TIMES_KEY, 1) { old, new ->
                    old as Int + new as Int
                }
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
            data.methodCount += classDeclaration.functions.size
            val width = classDeclaration.implementedTypes.size + (if (classDeclaration.superType != null) 1 else 0)
            data.maxInheritanceWidth = max(data.maxInheritanceWidth, width)
            data.avgInheritanceWidth = (data.avgInheritanceWidth * data.classCount + width) / (data.classCount + 1)
            val depth = classDeclaration.inheritanceDepth
            data.maxInheritanceDepth = max(data.maxInheritanceDepth, depth)
            data.avgInheritanceDepth = (data.avgInheritanceDepth * data.classCount + depth) / (data.classCount + 1)
            data.classCount++
            super.visitClassDeclaration(classDeclaration, data)
        }
    }

    protected fun processProgram(program: IrProgram): ProgramData {
        val data = ProgramData()
        program.accept(ProgramDataVisitor(), data)
        data.lineOfCode = IrProgramPrinter(printStub = false).print(program).values
            .filter {
                it !in extraSourceFileNames
            }.sumOf { it.lines().count() }
        return data
    }
}