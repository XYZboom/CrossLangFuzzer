package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.generator.*
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrClassContainer
import com.github.xyzboom.codesmith.ir.container.IrFunctionContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.expressions.constant.IrInt
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import kotlin.random.Random

class IrGeneratorImpl(
    private val config: GeneratorConfig = GeneratorConfig.default,
    private val random: Random = Random.Default,
) : IrGenerator {
    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    override fun randomName(startsWithUpper: Boolean): String {
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

    override fun randomIrInt(): IrInt {
        return IrInt(random.nextInt())
    }

    override fun randomClassType(): IrClassType {
        return IrClassType.entries.random(random)
    }

    override fun randomType(from: IrClassContainer, filter: (IrClassDeclaration) -> Boolean): IrType? {
        val filtered = from.allClasses.filter(filter)
        if (filtered.isEmpty()) return null
        return filtered.random(random).type
    }

    override fun genProgram(): IrProgram {
        return IrProgram().apply {
            for (i in 0 until config.classNumRange.random(random)) {
                genClass(this)
            }
        }
    }

    override fun IrClassDeclaration.genSuperTypes(context: IrClassContainer) {
        val classType = classType
        if (classType != IrClassType.INTERFACE) {
            val superType = randomType(context) {
                (it.classType == IrClassType.OPEN || it.classType == IrClassType.ABSTRACT) && it !== this
            }
            this.superType = superType ?: IrAny
        }
        val chosenIntf = mutableSetOf<IrClassDeclaration>()
        val willAdd = mutableListOf<IrType>()
        for (i in 0 until config.classImplNumRange.random(random)) {
            val now = randomType(context) {
                it.classType == IrClassType.INTERFACE && it !in chosenIntf && it !== this
            } as? IrClassifier?
            if (now == null) break
            chosenIntf.add(now.classDecl)
            willAdd.add(now)
        }
        implementedTypes.addAll(willAdd)
        genOverrides()
    }

    private fun IrClassDeclaration.genOverrides() {
        val superDecls = ArrayList(implementedTypes)
        if (superType != null) {
            superDecls += superType
        }
        val canOverrides = mutableListOf<IrFunctionDeclaration>()

        for (superType in superDecls) {
            superType as? IrClassifier ?: continue
            val functions = superType.classDecl.functions.filter { it.body == null }
            canOverrides.addAll(functions)
        }

        for (function in canOverrides) {
            val stillAbstract = if (classType == IrClassType.OPEN || classType == IrClassType.FINAL) {
                false
            } else {
                random.nextBoolean()
            }
            val isStub = if (stillAbstract) {
                if (classType == IrClassType.INTERFACE) {
                    false
                } else {
                    random.nextBoolean()
                }
            } else {
                false
            }
            if (functions.all { !it.signatureEquals(function) }) {
                genOverrideFunction(this, function, stillAbstract, isStub, this.language)
            }
        }
    }

    override fun genClass(context: IrClassContainer, name: String): IrClassDeclaration {
        val classType = randomClassType()
        return IrClassDeclaration(name, classType).apply {
            language = if (random.nextFloat() < config.javaRatio) {
                Language.JAVA
            } else {
                Language.KOTLIN
            }
            context.classes.add(this)
            if (random.nextFloat() < config.classHasSuperProbability) {
                genSuperTypes(context)
            }
            for (i in 0 until config.functionNumRange.random(random)) {
                genFunction(
                    this,
                    classType == IrClassType.ABSTRACT,
                    classType == IrClassType.INTERFACE,
                    language = language
                )
            }
        }
    }

    override fun genFunction(
        context: IrFunctionContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        name: String,
        language: Language
    ): IrFunctionDeclaration {
        return IrFunctionDeclaration(name).apply {
            this.language = language
            context.functions.add(this)
            if (!inIntf && (!inAbstract || random.nextBoolean())) {
                body = IrBlock()
            }
        }
    }

    override fun IrClassDeclaration.genOverrideFunction(
        context: IrFunctionContainer,
        from: IrFunctionDeclaration,
        stillAbstract: Boolean,
        isStub: Boolean,
        language: Language
    ) {
        functions.add(IrFunctionDeclaration(from.name).apply {
            this.language = language
            isOverride = true
            isOverrideStub = isStub
            override = from
            if (!stillAbstract) {
                body = IrBlock()
            }
        })
    }
}