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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

class IrGeneratorImpl(
    private val config: GeneratorConfig = GeneratorConfig.default,
    private val random: Random = Random.Default,
) : IrGenerator {

    private val logger = KotlinLogging.logger {}

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    private fun StringBuilder.traceFunction(function: IrFunctionDeclaration) {
        append(function.toString())
        append(" from ")
        val container = function.container
        if (container is IrClassDeclaration) {
            append("class ")
            append(container.name)
        }
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
        logger.trace { "start gen program" }
        return IrProgram().apply {
            for (i in 0 until config.classNumRange.random(random)) {
                genClass(this)
            }
            logger.trace { "finish gen program" }
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

    override fun IrClassDeclaration.genOverrides() {
        logger.trace { "start gen overrides for: ${this.name}" }
        val superDecls = ArrayList(implementedTypes)
        if (superType != null) {
            superDecls += superType
        }
        val mustOverrides = mutableListOf<List<IrFunctionDeclaration>>()
        val canOverride = mutableListOf<List<IrFunctionDeclaration>>()
        val visited = mutableMapOf<IrFunctionDeclaration.Signature, MutableList<IrFunctionDeclaration>>()

        for (superType in superDecls) {
            superType as? IrClassifier ?: continue
            for (func in superType.classDecl.functions) {
                val signature = func.signature
                visited.getOrPut(signature) { mutableListOf() }.add(func)
            }
        }

        for ((signature, functions) in visited) {
            if (functions.isEmpty()) {
                continue
            }
            logger.trace { "signature: ${signature.name}(${signature.parameterTypes.joinToString(", ")})" }
            logger.trace {
                val sb = StringBuilder()
                sb.append("functions: \n")

                for (function in functions) {
                    sb.append("\t\t")
                    sb.traceFunction(function)
                    sb.append("\n")
                }
                sb.toString()
            }
            val nonAbstractCount = functions.count { it.body != null }
            var addedToMust = false
            if (nonAbstractCount != 1) {
                mustOverrides.add(functions)
                addedToMust = true
            } else if (functions.size != 1) {
                val containerClass = functions.single { it.body != null }.container as IrClassDeclaration
                // An abstract method in super class will shadow the one in interface
                if (containerClass.classType == IrClassType.INTERFACE) {
                    mustOverrides.add(functions)
                    addedToMust = true
                }
            }
            logger.trace { "must override: $addedToMust" }
            if (!addedToMust && functions.all { !it.isFinal }) {
                canOverride.add(functions)
                logger.trace { "can override" }
            }
        }

        for (functions in mustOverrides) {
            val first = functions.first()
            require(this.functions.all { !it.signatureEquals(first) })
            genOverrideFunction(
                this, functions,
                stillAbstract = false, isStub = false, language = this.language
            )
        }

        for (functions in canOverride) {
            val stillAbstract = if (classType == IrClassType.OPEN || classType == IrClassType.FINAL) {
                false
            } else {
                !config.overrideOnlyMustOnes && random.nextBoolean()
            }
            val isStub = if (stillAbstract) {
                // if stillAbstract is true, that means config.overrideOnlyMustOnes is already false
                // So no more judgment is needed.
                random.nextBoolean()
            } else {
                true
            }
            val first = functions.first()
            if (this.functions.all { !it.signatureEquals(first) }) {
                genOverrideFunction(this, functions, stillAbstract, isStub, this.language)
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
        logger.trace {
            val sb = StringBuilder("gen function $name for ")
            if (context is IrClassDeclaration) {
                sb.append("class ")
                sb.append(context.name)
            } else {
                sb.append("program.")
            }
            sb.toString()
        }
        return IrFunctionDeclaration(name, context).apply {
            this.language = language
            context.functions.add(this)
            if (!inIntf && (!inAbstract || random.nextBoolean())) {
                body = IrBlock()
            }
            isFinal = random.nextBoolean()
        }
    }

    override fun IrClassDeclaration.genOverrideFunction(
        context: IrFunctionContainer,
        from: List<IrFunctionDeclaration>,
        stillAbstract: Boolean,
        isStub: Boolean,
        language: Language
    ) {
        logger.trace {
            val sb = StringBuilder("gen override for class: $name\n")
            for (func in from) {
                sb.append("\t\t")
                sb.traceFunction(func)
                sb.append("\n")
            }
            sb.toString()
        }
        functions.add(IrFunctionDeclaration(from.first().name, context).apply {
            this.language = language
            isOverride = true
            isOverrideStub = isStub
            override += from
            if (!stillAbstract && !isStub) {
                body = IrBlock()
            }
            isFinal = random.nextBoolean()
        })
    }
}