package com.github.xyzboom.codesmith

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int

abstract class CommonCompilerRunner : CliktCommand() {
    protected val differentialTesting by option("--dt").boolean().default(true)
    protected val stopOnErrors by option("-s", "--stop-on-errors").boolean().default(false)
    protected val langShuffleTimesBeforeMutate by option("--lang-shuffle-times-before-mutate").int().default(5)
    protected val langShuffleTimesAfterMutate by option("--lang-shuffle-times-after-mutate").int().default(5)

}