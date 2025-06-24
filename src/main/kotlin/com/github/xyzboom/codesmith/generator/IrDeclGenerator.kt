package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.builder.IrProgramBuilder
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.utils.nextBoolean
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

class IrDeclGenerator(
    private val config: GeneratorConfig = GeneratorConfig.default,
    internal val random: Random = Random.Default,
    private val majorLanguage: Language = Language.KOTLIN
) {
    private val logger = KotlinLogging.logger {}

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.scala)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    fun randomName(startsWithUpper: Boolean): String {
        val length = config.nameLengthRange.random(random)
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

    fun randomLanguage(): Language {
        if (random.nextBoolean(config.javaRatio)) {
            return Language.JAVA
        }
        return majorLanguage
    }

    fun genProgram(): IrProgram {
        logger.trace { "start gen program" }
        return buildProgram {
            repeat(config.topLevelDeclRange.random(random)) {
                val clazz = genClass(context = this, randomName(true), randomLanguage())
                classes.add(clazz)
            }
            logger.trace { "finish gen program" }
        }
    }

    fun genClass(
        context: IrProgramBuilder, name: String = randomName(true), language: Language = randomLanguage()
    ): IrClassDeclaration {
        /*val classType = randomClassType()
        return IrClassDeclaration(name, classType).apply {
            this.language = language
            context.classes.add(this)
            if (random.nextBoolean(config.classHasTypeParameterProbability)) {
                repeat(config.classTypeParameterNumberRange.random(random)) {
                    genTypeParameter()
                }
            }
            genSuperTypes(context)

            for (i in 0 until config.classMemberNumRange.random(random)) {
                val generator: IrClassMemberGenerator = if (classType != IrClassType.INTERFACE) {
                    randomClassMemberGenerator()
                } else {
                    this@IrDeclGeneratorImplOld::genFunction
                }
                generator(
                    context,
                    this,
                    classType == IrClassType.ABSTRACT,
                    classType == IrClassType.INTERFACE,
                    null,
                    randomName(false),
                    language
                )
            }
            genOverrides()
        }*/
    }

}