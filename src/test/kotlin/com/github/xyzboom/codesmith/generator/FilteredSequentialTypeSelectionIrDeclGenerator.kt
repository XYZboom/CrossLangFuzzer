package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import kotlin.random.Random

class FilteredSequentialTypeSelectionIrDeclGenerator(
    typeList: List<IrType>,
    config: GeneratorConfig = GeneratorConfig.default,
    random: Random = Random.Default,
    majorLanguage: Language = Language.KOTLIN
) : IrDeclGenerator(config, random, majorLanguage) {
    private val iterator = typeList.iterator()
    override fun randomType(
        fromClasses: List<IrClassDeclaration>,
        fromTypeParameters: List<IrTypeParameter>,
        finishTypeArguments: Boolean,
        filter: (IrType) -> Boolean
    ): IrType? {
        return if (iterator.hasNext()) {
            val next = iterator.next()
            if (filter(next)) {
                next
            } else null
        } else null
    }
}