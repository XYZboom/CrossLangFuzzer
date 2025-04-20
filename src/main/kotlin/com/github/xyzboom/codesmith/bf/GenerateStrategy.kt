package com.github.xyzboom.codesmith.bf

import com.github.xyzboom.bf.def.Reference
import com.github.xyzboom.bf.def.Statement
import com.github.xyzboom.bf.gen.strategy.IGenerateStrategy
import com.github.xyzboom.bf.tree.INode
import com.github.xyzboom.codesmith.bf.RefType.*
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.ir.types.IrType
import io.github.oshai.kotlinlogging.KotlinLogging

class GenerateStrategy(
    // old generator
    val gen: IrDeclGeneratorImpl,
    private val config: GeneratorConfig
) : IGenerateStrategy {
    private val logger = KotlinLogging.logger {}

    override fun chooseIndex(statement: Statement, context: INode?): Int {
        // todo
        return 0
    }

    override fun chooseLeaf(statement: Statement, context: INode?): Boolean {
        // todo
        return false
    }

    override fun chooseReference(
        reference: Reference,
        context: INode,
        generatedNode: Map<String, List<INode>>
    ): INode? {
        logger.trace { "chooseReference: statement name ${reference.name}, context: $context" }
        if (reference.name.startsWith("_")) {
            return null
        }
        // todo
        val result = when (RefType.valueOfIgnoreCase(reference.name)) {
            TOP_DECL -> null
            CLASS -> {
                if (context is IrType) {
                    TODO("ref a class for type")
                } else {
                    null
                }
            }
            else -> throw IllegalStateException("chooseReference should never be called on reference ${reference.name}")
        }
        logger.trace { "chooseReference of node: $result" }
        return result
    }

    override fun chooseSize(reference: Reference, context: INode): Int {
        // todo
        val size = when (RefType.valueOfIgnoreCase(reference.name)) {
            TOP_DECL -> config.topLevelDeclRange.random()
            null -> -1
            else ->
                throw IllegalStateException("chooseSize should never be called on reference ${reference.name}")
        }
        logger.trace { "chooseSize: $size" }
        return size
    }
}