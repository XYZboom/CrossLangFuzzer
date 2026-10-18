package com.github.xyzboom.codesmith.mutator

/**
 * The semantic validity calculated from the rule of the mutation that was
 * actually applied. This is an oracle produced by the mutator; it is not a
 * classification of a compiler diagnostic.
 *
 * [UNKNOWN] means that the mutation was applied, but the current IR type model
 * cannot prove whether the resulting program is valid. A `null` mutation
 * result has a different meaning: no applicable mutation target was found and
 * the program was not changed.
 */
enum class SemanticValidity {
    VALID,
    INVALID,
    UNKNOWN,
}
