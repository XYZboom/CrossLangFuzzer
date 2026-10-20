package com.github.xyzboom.codesmith.scala

import com.github.ajalt.clikt.core.main
import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.CompilerTarget
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.mutator.MutatorLanguageSemantics
import com.github.xyzboom.codesmith.printer.IrProgramPrinter

class CrossLangFuzzerScalaRunner : CommonCompilerRunner("scala") {
    object Scala2Compiler : ICompiler {
        override fun compile(program: IrProgram): CompileResult {
            val printer = IrProgramPrinter(Language.SCALA)
            return compileScala2WithJava(printer, program)
        }
    }

    object Scala3Compiler : ICompiler {
        override fun compile(program: IrProgram): CompileResult {
            val printer = IrProgramPrinter(Language.SCALA)
            return compileScala3WithJava(printer, program)
        }
    }

    override val runnerLanguage: Language get() = Language.SCALA
    override val mutatorLanguageSemantics: MutatorLanguageSemantics get() = MutatorLanguageSemantics.SCALA

    override fun normalTargets(): List<CompilerTarget> = listOf(
        CompilerTarget("scala3", Scala3Compiler, Language.SCALA)
    )

    override fun differentialTargets(): List<CompilerTarget> = listOf(
        CompilerTarget("scala2", Scala2Compiler, Language.SCALA),
        CompilerTarget("scala3", Scala3Compiler, Language.SCALA),
    )
}

fun main(args: Array<String>) {
    CrossLangFuzzerScalaRunner().main(args)
}
