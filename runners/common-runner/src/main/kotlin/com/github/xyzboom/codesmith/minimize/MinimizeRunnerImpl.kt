package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.ir.IrProgram
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A minimize runner to reduce class numbers as much as possible.
 * # Steps
 * ## 1. Split
 * Firstly, we split classes into two groups: suspicious and normal.
 * If a class whose name appears in compile result, it and all supers of it will be marked as suspicious.
 * Other classes are marked as normal.
 * ## 2. Normal Classes Removal
 * Secondly, we will try to remove all normal classes to see if the bug exists.
 * If the bug disappears, we roll back this and remove normal class one by one.
 * Normally, removing all normal classes will not cause the bug disappear.
 * ## 3. Suspicious Classes Removal
 * Thirdly, we try to remove suspicious classes one by one.
 * If there are no class can be removed, we will finish the class level minimization.
 * # Note:
 * When removing a class, all its subclasses will be removed so we must start from classes who have no child.
 * The types using this removed class will be replaced into another class.
 * If this to be removed class has type parameters, this other class will first have some,
 * but soon we will try to remove these type parameters.
 */
class MinimizeRunnerImpl(
    compilerRunner: ICompilerRunner
) : IMinimizeRunner, ICompilerRunner by compilerRunner {
    companion object {
        @JvmStatic
        private val logger = KotlinLogging.logger {}
    }

    var nameNumber = 0
    fun nextReplacementName(): String {
        return "A${nameNumber++}"
    }

    fun List<CompileResult>.mayRelatedWith(name: String): Boolean {
        return any {
            it.javaResult?.contains(name) == true || it.majorResult?.contains(name) == true
        }
    }

    fun removeUnrelatedFunctions(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        val suspicious = mutableSetOf<String>()
        val normal = mutableSetOf<String>()
        classes@ for (clazz in initProg.classes) {
            for (f in clazz.functions) {
                if (initCompileResult.mayRelatedWith(f.name)) {
                    suspicious.add(f.name)
                } else {
                    normal.add(f.name)
                }
            }
        }
        var result = initProg.backup()
        var newCompileResult: List<CompileResult> = initCompileResult
        run tryRemoveAllNormal@{
            for (funcName in normal) {
                result.removeFunction(funcName)
            }
            newCompileResult = compile(result, compilers)
            if (newCompileResult != initCompileResult) {
                logger.trace { "remove all normal functions cause the bug disappear, rollback" }
                // rollback
                result = initProg.backup()
                newCompileResult = initCompileResult
            } else {
                logger.trace { "remove all normal functions success" }
                normal.clear()
            }
        }
        val allIter = (normal.asSequence() + suspicious.asSequence()).iterator()
        var lastCompileResult = newCompileResult
        while (allIter.hasNext()) {
            val next = allIter.next()
            val backup = result.backup()
            logger.trace { "remove function $next" }
            result.removeFunction(next)
            newCompileResult = compile(result, compilers)
            if (newCompileResult != initCompileResult) {
                result = backup
                newCompileResult = lastCompileResult
            }
            lastCompileResult = newCompileResult
        }

        return result to newCompileResult
    }

    fun removeUnrelatedClass(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        var result = initProg.backup()
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val classNames = result.classes.map { it.name }
            for (className in classNames) {
                val backup = result.backup()
                result.replaceClassWithIrAnyDeeply(className)
                newCompileResult = compile(result, compilers)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = backup
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result to lastCompileResult
    }

    fun removeUnrelatedTypeParameters(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        var result = initProg.backup()
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val allTypeParameters = result.collectAllTypeParameters()
            for (typeParameterName in allTypeParameters) {
                val backup = result.backup()
                result.replaceTypeParameterWithIrAny(typeParameterName)
                newCompileResult = compile(result, compilers)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = backup
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result to lastCompileResult
    }

    fun removeUnrelatedParameters(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        var result = initProg.backup()
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val allParameters = result.collectAllParameters()
            for (parameterName in allParameters) {
                val backup = result.backup()
                result.removeParameter(parameterName)
                newCompileResult = compile(result, compilers)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = backup
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result to lastCompileResult
    }

    fun reduceInheritanceHierarchy(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        var result = initProg.backup()
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            if (result.classes.size <= 3) {
                break
            }
            val classNames = result.classes.map { it.name }
            for (className in classNames) {
                val backup = result.backup()
                result.replaceClassWithIrAnyShallowly(className)
                newCompileResult = compile(result, compilers)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = backup
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result to lastCompileResult
    }

    override fun minimize(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<IrProgram, List<CompileResult>> {
        val firstStage = ::removeUnrelatedClass
        val stages = listOf(
            ::removeUnrelatedFunctions,
            ::removeUnrelatedParameters,
            ::removeUnrelatedTypeParameters,
            ::reduceInheritanceHierarchy
        )
        var resultPair = firstStage(ProgramWithRemovedDecl(initProg), initCompileResult, compilers)
        for (stage in stages) {
            resultPair = stage(resultPair.first, resultPair.second, compilers)
        }
        return resultPair.first.prog to resultPair.second
    }
}