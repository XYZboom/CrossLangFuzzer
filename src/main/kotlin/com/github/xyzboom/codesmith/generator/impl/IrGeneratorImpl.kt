package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.generator.*
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.expressions.constant.IrInt
import kotlin.random.Random

class IrGeneratorImpl(
    private val config: GeneratorConfig = GeneratorConfig.default,
    private val random: Random = Random.Default,
): IrGenerator {
    private val nameLengthRange = 3..8
    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    override fun randomName(startsWithUpper: Boolean): String {
        val length = nameLengthRange.random(random)
        val sb = StringBuilder(
            if (startsWithUpper) {
                "${upperLetters.random(random)}"
            } else {
                "${lowerStartingLetters.random(random)}"
            }
        )
        for (i in 1 until length) {
            sb.append(lettersAndNumbers.random(random))
        }
        val result = sb.toString()
        val lowercase = result.lowercase()
        if (generatedNames.contains(lowercase)) {
            return randomName(startsWithUpper)
        }
        generatedNames.add(lowercase)
        return result
    }

    override fun randomIrInt(): IrInt {
        return IrInt(random.nextInt())
    }

    override fun genProgram(): IrProgram {
        return IrProgram().apply {
            for (i in 0 until config.classNumRange.random(random)) {
                classes.add(genClass())
            }
        }
    }

    override fun genClass(): IrClassDeclaration {
        return IrClassDeclaration(randomName(true)).apply {
            language = if (random.nextFloat() < config.javaRatio) {
                Language.JAVA
            } else {
                Language.KOTLIN
            }
        }
    }
}