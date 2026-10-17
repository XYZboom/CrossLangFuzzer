package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.deepCopy

/**
 * Tries to downgrade a multi-module program by merging modules together,
 * thereby eliminating inter-module binary compilation boundaries step by step.
 */
class BytecodeDowngradeMinimizer {
    fun tryDowngrade(
        runner: ICompilerRunner,
        program: IrProgram,
        originalResults: List<CompileResult>,
        compilers: List<ICompiler>,
    ): IrProgram {
        var current = program.deepCopy()
        while (current.modules.size > 1) {
            val candidate = current.deepCopy()
            val merged = tryDowngrade(candidate)
            if (!merged) break
            val keepDowngrade = try {
                matches(originalResults, runner.compile(candidate, compilers))
            } catch (_: Throwable) {
                false
            }
            if (keepDowngrade) {
                current = candidate
            } else {
                break
            }
        }
        return current
    }

    /**
     * Merges the first module into its downstream module, removing one inter-module boundary.
     *
     * @return `true` if modules were merged; `false` if only 1 module remains.
     */
    fun tryDowngrade(program: IrProgram): Boolean {
        if (program.modules.size <= 1) return false
        val firstMod = program.modules.first()
        val nextMod = program.modules[1]
        nextMod.classes.addAll(0, firstMod.classes)
        nextMod.functions.addAll(0, firstMod.functions)
        nextMod.properties.addAll(0, firstMod.properties)
        (program.modules as MutableList).removeAt(0)
        for (mod in program.modules) {
            mod.dependencies.remove(firstMod)
        }
        return true
    }

    private fun matches(expected: List<CompileResult>, actual: List<CompileResult>): Boolean {
        if (expected.size != actual.size) return false
        return expected.indices.all { expected[it] == actual[it] }
    }
}
