package com.github.xyzboom.codesmith.config

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.mutator.MutatorConfig

data class RunConfig(
    val generatorConfig: GeneratorConfig = GeneratorConfig(),
    val mutatorConfig: MutatorConfig = MutatorConfig(),
    val langShuffleTimesBeforeMutate: Int = 3,
    val langShuffleTimesAfterMutate: Int = 3,
    val mutateTimes: Int = 5,
)