package com.github.xyzboom.codesmith.groovy

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.choice
import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.setMajorLanguage
import com.github.xyzboom.codesmith.minimize.MinimizeRunnerImpl
import com.github.xyzboom.codesmith.mutator.IrMutator
import com.github.xyzboom.codesmith.mutator.MutatorConfig
import com.github.xyzboom.codesmith.recordCompileResult
import com.github.xyzboom.codesmith.tempDir
import kotlin.system.exitProcess
import kotlin.time.measureTime


class CodeSmithGroovyRunner : CommonCompilerRunner() {

    private val groovyVersions by option("--gv")
        .choice(*GroovyCompilerWrapper.Companion.groovyJarsWithVersion.keys.toTypedArray())
        .split(",")
        .required()

    private val groovyCompilers: List<GroovyCompilerWrapper> by lazy {
        groovyVersions.map { GroovyCompilerWrapper(it) }
    }

    private val minimizeRunner = MinimizeRunnerImpl(this)

    override fun runnerMain() {
        val doOneRoundFunction: () -> Unit
        if (differentialTesting) {
            if (groovyVersions.size <= 1) {
                System.err.println("You must provide at least 2 different versions of compiler to do differential testing!")
                exitProcess(-1)
            }
            doOneRoundFunction = ::doDifferentialTestingOneRound
        } else {
            doOneRoundFunction = ::doOneRound
        }
        println("start at: $tempDir")
        var i = 0
        while (true) {
            val dur = measureTime { doOneRoundFunction() }
            println("${i++}: $dur")
        }
    }

    private fun runDifferential(
        groovyCompilers: List<GroovyCompilerWrapper>,
        generator: IrDeclGenerator,
        program: IrProgram
    ) {
        val compileResults = groovyCompilers.map { compiler ->
            if (compiler.isGroovy5) {
                program.setMajorLanguage(Language.GROOVY5)
            } else {
                program.setMajorLanguage(Language.GROOVY4)
            }
            // Due to the compatibility between Groovy syntax and Java,
            // it is reasonable to conduct differential testing directly using the 'shuffle language'
//            generator.shuffleLanguage(program)
            compiler.compile(program)
        }

        if (compileResults.toSet().size != 1) {
            val (minimize, minResult) = try {
                minimizeRunner.minimize(program, compileResults, groovyCompilers)
            } catch (_: Throwable) {
                null to null
            }
            recordCompileResult(
                Language.GROOVY4, program, compileResults, minimize, minResult, outDir = nonSimilarOutDir
            )
            if (stopOnErrors) {
                exitProcess(-1)
            }
        }
    }

    private fun doDifferentialTestingOneRound() {
        val generator = IrDeclGenerator(
            runConfig.generatorConfig,
            majorLanguage = Language.GROOVY4,
        )
        val program = generator.genProgram()
        repeat(runConfig.langShuffleTimesBeforeMutate) {
            run normalDifferential@{
                runDifferential(groovyCompilers, generator, program)
            }
            generator.shuffleLanguage(program)
        }
        val mutator = IrMutator(
            generator = generator,
            config = MutatorConfig(
                mutateGenericArgumentInParentWeight = 1,
                removeOverrideMemberFunctionWeight = 1,
                mutateGenericArgumentInMemberFunctionParameterWeight = 1,
                mutateParameterNullabilityWeight = 0
            )
        )
        if (mutator.mutate(program)) {
            repeat(runConfig.langShuffleTimesAfterMutate) {
                run mutatedDifferential@{
                    runDifferential(groovyCompilers, generator, program)
                }
                generator.shuffleLanguage(program)
            }
        }
    }

    private fun doOneRound() {
        for (groovyCompiler in groovyCompilers) {
            val generator = IrDeclGenerator(
                runConfig.generatorConfig,
                majorLanguage = groovyCompiler.language,
            )
            val program = generator.genProgram()
            repeat(runConfig.langShuffleTimesBeforeMutate) {
                val compileResult = groovyCompiler.compile(program)
                if (!compileResult.success) {
                    recordCompileResult(
                        groovyCompiler.language,
                        program,
                        compileResult
                    )
                    if (stopOnErrors) {
                        exitProcess(-1)
                    }
                }
                generator.shuffleLanguage(program)
            }
        }
    }

}

fun main(args: Array<String>) {
    return CodeSmithGroovyRunner().main(args)
}