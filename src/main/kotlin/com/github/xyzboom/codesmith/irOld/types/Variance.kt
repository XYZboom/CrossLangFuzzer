package com.github.xyzboom.codesmith.irOld.types

enum class Variance(
    val label: String,
    val allowsInPosition: Boolean,
    val allowsOutPosition: Boolean,
    private val superpositionFactor: Int
) {
    INVARIANT("", true, true, 0),
    IN_VARIANCE("in", true, false, -1),
    OUT_VARIANCE("out", false, true, +1);
}