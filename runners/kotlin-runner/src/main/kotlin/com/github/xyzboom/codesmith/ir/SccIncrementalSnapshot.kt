package com.github.xyzboom.codesmith.ir

/**
 * Materialize a class-set snapshot without mutating the source program.
 * An empty set is valid for baseline S0.
 */
fun IrProgram.materializeClassSnapshot(includedClassNames: Collection<String>): IrProgram {
    require(modules.size == 1) { "SCC incremental snapshots require exactly one module" }
    val available = classes.map { it.name }.toSet()
    val requested = includedClassNames.toSet()
    require(available.containsAll(requested)) {
        "Snapshot contains unknown classes: ${requested - available}"
    }
    return deepCopy().apply {
        modules.single().classes.removeAll { it.name !in requested }
    }
}

/** Materialize one planned state, including any temporarily truncated parameter lists. */
fun SccIncrementalSnapshot.materialize(program: IrProgram): IrProgram {
    val copy = program.materializeClassSnapshot(includedClassNames)
    val module = copy.modules.single()
    parameterCounts.forEach { (key, count) ->
        val clazz = module.classes.singleOrNull { it.name == key.className }
            ?: error("Snapshot refers to absent class '${key.className}'")
        val function = clazz.functions.getOrNull(key.functionIndex)
            ?: error("Snapshot refers to absent function #${key.functionIndex} in '${key.className}'")
        require(count in 0..function.parameterList.parameters.size) {
            "Snapshot parameter count $count is invalid for ${key.className}#${key.functionIndex}"
        }
        function.parameterList.parameters = function.parameterList.parameters.take(count).toMutableList()
    }
    return copy
}
