package com.github.xyzboom.codesmith.groovy

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.choice
import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.CompilerTarget
import com.github.xyzboom.codesmith.RunMode
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.setMajorLanguage
import com.github.xyzboom.codesmith.mutator.MutatorLanguageSemantics

class CodeSmithGroovyRunner : CommonCompilerRunner("groovy") {
    private val groovyVersions by option("--gv")
        .choice(*GroovyCompilerWrapper.groovyJarsWithVersion.keys.toTypedArray())
        .split(",")
        .default(listOf("4.0.26", "5.0.0-alpha-12"))

    private val groovyCompilers: List<GroovyCompilerWrapper> by lazy {
        groovyVersions.map { GroovyCompilerWrapper(it) }
    }

    override val runnerLanguage: Language get() = Language.GROOVY4
    override val mutatorLanguageSemantics: MutatorLanguageSemantics get() = MutatorLanguageSemantics.GROOVY

    override fun validateOptions() {
        if (runMode == RunMode.DifferentialTest && groovyVersions.size <= 1) {
            error("You must provide at least 2 different versions of compiler to do differential testing!")
        }
    }

    override fun normalTargets(): List<CompilerTarget> = groovyCompilers.map { compiler ->
        CompilerTarget(
            stableName = "groovy-${compiler.version}",
            compiler = compiler,
            majorLanguage = compiler.language,
        )
    }

    override fun differentialTargets(): List<CompilerTarget> = groovyCompilers.map { compiler ->
        CompilerTarget(
            stableName = "groovy-${compiler.version}",
            compiler = compiler,
            majorLanguage = compiler.language,
        )
    }

    override fun prepareProgramForTarget(program: IrProgram, target: CompilerTarget) {
        program.setMajorLanguage(target.majorLanguage)
    }
}

fun main(args: Array<String>) {
    CodeSmithGroovyRunner().main(args)
}
