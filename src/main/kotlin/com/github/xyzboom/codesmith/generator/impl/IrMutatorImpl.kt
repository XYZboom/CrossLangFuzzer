package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.generator.IAccessChecker
import com.github.xyzboom.codesmith.generator.IrMutator
import com.github.xyzboom.codesmith.ir.IrAccessModifier.*
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrProgram
import kotlin.random.Random

@Suppress("Unused")
class IrMutatorImpl(
    private val random: Random = Random.Default,
): IrMutator, IAccessChecker by AccessCheckerImpl() {
    override fun mutate(program: IrProgram): IrProgram {
        outer@ for (module in program.modules.shuffled(random)) {
            for (`package` in module.packages.shuffled(random)) {
                for (file in `package`.files.shuffled(random)) {
                    for (clazz in file.declarations.shuffled(random).filterIsInstance<IrClass>()) {
                        if (clazz.accessModifier == PUBLIC &&
                            clazz.superType?.declaration?.accessModifier == PUBLIC &&
                            clazz.superType?.declaration?.isInSamePackage(clazz) != true) {
                            clazz.superType?.declaration?.accessModifier = INTERNAL
                            break@outer
                        }
                    }
                }
            }
        }
        return program
    }
}