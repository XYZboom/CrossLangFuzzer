package com.github.xyzboom.codesmith.bf

import com.github.xyzboom.bf.gen.Generator
import com.github.xyzboom.bf.tree.INode
import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.bf.RefType.*
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrClassType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

class Generator(
    config: GeneratorConfig = GeneratorConfig.default,
    random: Random = Random,
    majorLanguage: Language = Language.KOTLIN
) : Generator(
    definition, GenerateStrategy(
        IrDeclGeneratorImpl(config, random, majorLanguage),
        config
    )
) {
    // old gen
    private val gen = (strategy as GenerateStrategy).gen
    private val logger = KotlinLogging.logger {}
    override fun generateNode(name: String, context: INode?, ref: INode?): INode {
        logger.trace { "generateNode for $name with context $context and ref of $ref" }
        if (name.startsWith("_")) {
            return super.generateNode(name, context, ref)
        }
        return when (RefType.valueOfIgnoreCase(name)) {
            PROG -> IrProgram()
            TOP_DECL -> super.generateNode(name, context, ref)
            // in the old version, classType (newly named classKind) is generated earlier
            // now we have to set classType (newly named classKind) later
            CLASS -> IrClassDeclaration(gen.randomName(true), IrClassType.OPEN)
            else -> throw IllegalArgumentException("Unknown name: $name")
        }
    }
}