package com.github.xyzboom.codesmith.data

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ir.visitors.IrTopDownVisitor
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.inheritanceDepth
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import kotlin.collections.set
import kotlin.math.max

open class DataRecorder {
    companion object {
        private const val COMPILE_TIMES_KEY = "_compile_times"
    }

    private val programCountMap = mutableMapOf<String, Int>()
    private val allProgramDataMap = mutableMapOf<String, ProgramData>()
    private val otherDataMap = mutableMapOf<String, Any>()
    val programCount: Map<String, Int> get() = programCountMap
    val programData: Map<String, ProgramData> get() = allProgramDataMap
    fun addProgram(key: String, program: IrProgram) {
        programCountMap[key] = programCountMap[key]?.plus(1) ?: 1
        val data = processProgram(program)
        if (allProgramDataMap.containsKey(key)) {
            allProgramDataMap[key]!! += data
        } else {
            allProgramDataMap[key] = data
        }
    }

    fun <T : Any> addData(key: String, data: T) {
        otherDataMap[key] = data
    }

    fun <T : Any> getData(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return otherDataMap[key] as? T?
    }

    fun recordCompiler(compiler: ICompiler): ICompiler {
        return object : ICompiler {
            override fun compile(program: IrProgram): CompileResult {
                addData(COMPILE_TIMES_KEY, getCompileTimes() + 1)
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
        data.lineOfCode = IrProgramPrinter(printStub = false).print(program).values.sumOf { it.lines().count() }
        return data
    }
}