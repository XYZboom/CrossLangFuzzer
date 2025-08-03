package io.github.xyzboom.clf

import com.github.xyzboom.codesmith.ir.IrProgram
import io.github.xyzboom.gedlib.GEDEnv
import io.github.xyzboom.gedlib.GEDGraph

fun IrProgram.toGEDGraph(env: GEDEnv): GEDGraph {
    return ProgramContextForGEDGraph(env, this).toGraph()
}

fun GEDEnv.lowerBoundOf(prog1: IrProgram, prog2: IrProgram): Double {
    return getLowerBound(prog1.toGEDGraph(this), prog2.toGEDGraph(this))
}

fun GEDEnv.upperBoundOf(prog1: IrProgram, prog2: IrProgram): Double {
    return getUpperBound(prog1.toGEDGraph(this), prog2.toGEDGraph(this))
}