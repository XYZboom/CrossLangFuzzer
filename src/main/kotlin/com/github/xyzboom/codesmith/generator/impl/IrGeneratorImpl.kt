package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.generator.*
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.declarations.*
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

    private val subTypeMap = hashMapOf<String, MutableList<IrClassDeclaration>>()

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

    override fun randomType(from: IrContainer, filter: (IrClassDeclaration) -> Boolean): IrType? {
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

    override fun IrClassDeclaration.genSuperTypes(context: IrContainer) {
        val classType = classType
        if (classType != IrClassType.INTERFACE) {
            val superType = randomType(context) {
                (it.classType == IrClassType.OPEN || it.classType == IrClassType.ABSTRACT) && it !== this
            }
            if (superType != null) {
                val superName = (superType as IrClassifier).classDecl.name
                subTypeMap.getOrPut(superName) { mutableListOf() }.add(this)
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
            subTypeMap.getOrPut(now.classDecl.name) { mutableListOf() }.add(this)
            chosenIntf.add(now.classDecl)
            willAdd.add(now)
        }
        implementedTypes.addAll(willAdd)
        genOverrides()
    }

    private fun IrFunctionDeclaration.traverseOverride(visitor: (IrFunctionDeclaration) -> Unit) {
        logger.trace {
            val sb = StringBuilder("start traverse: ")
            sb.traceFunction(this)
            sb.toString()
        }
        override.forEach {
            visitor(it)
            it.traverseOverride(visitor)
        }
        logger.trace {
            val sb = StringBuilder("end traverse: ")
            sb.traceFunction(this)
            sb.toString()
        }
    }

    override fun IrClassDeclaration.collectFunctionSignatureMap(): FunctionSignatureMap {
        logger.trace { "start collectFunctionSignatureMap for class: $name" }
        val result = mutableMapOf<IrFunctionDeclaration.Signature,
                Pair<IrFunctionDeclaration?, MutableList<IrFunctionDeclaration>>>()
        //           ^^^^^^^^^^^^^^^^^^^^^ decl in super
        //           functions in interfaces ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        for (superType in implementedTypes) {
            superType as? IrClassifier ?: continue
            for (func in superType.classDecl.functions) {
                val signature = func.signature
                result.getOrPut(signature) { null to mutableListOf() }.second.add(func)
            }
        }
        for ((_, pair) in result) {
            val (_, funcs) = pair
            val willRemove = mutableListOf<IrFunctionDeclaration>()
            for (func in funcs) {
                var found = false
                func.traverseOverride {
                    if (it in funcs) {
                        found = true
                        logger.trace {
                            val sb = StringBuilder("found a override in collected that is: ")
                            sb.traceFunction(it)
                            sb.toString()
                        }
                        willRemove.add(it)
                    }
                }
                if (found) {
                    logger.trace {
                        val sb = StringBuilder("found a override, will remove. For function: ")
                        sb.traceFunction(func)
                        sb.toString()
                    }
                }
            }
            funcs.removeAll(willRemove)
        }
        val superType = superType
        if (superType is IrClassifier) {
            for (function in superType.classDecl.functions) {
                val signature = function.signature
                val pair = result[signature]
                if (pair == null) {
                    result[signature] = function to mutableListOf()
                } else {
                    result[signature] = function to pair.second
                }
            }
        }
        logger.trace { "end collectFunctionSignatureMap for class: $name" }
        return result
    }

    override fun IrClassDeclaration.genOverrides() {
        logger.trace { "start gen overrides for: ${this.name}" }

        val signatureMap = collectFunctionSignatureMap()
        val mustOverrides = mutableListOf<SuperAndIntfFunctions>()
        val canOverride = mutableListOf<SuperAndIntfFunctions>()
        val stubOverride = mutableListOf<SuperAndIntfFunctions>()
        for ((signature, pair) in signatureMap) {
            val (superFunction, functions) = pair
            logger.debug { "name: ${signature.name}" }
            logger.trace { "parameter: (${signature.parameterTypes.joinToString(", ")})" }
            logger.trace {
                val sb = StringBuilder("super function: \n")
                if (superFunction != null) {
                    sb.append("\t\t")
                    sb.traceFunction(superFunction)
                    sb.append("\n")
                } else {
                    sb.append("\t\tnull\n")
                }

                sb.append("intf functions: \n")

                for (function in functions) {
                    sb.append("\t\t")
                    sb.traceFunction(function)
                    sb.append("\n")
                }
                sb.toString()
            }

            val nonAbstractCount = functions.count { it.body != null }
            logger.debug { "nonAbstractCount: $nonAbstractCount" }
            var notMustOverride = true
            if (superFunction == null) {
                if (functions.size > 1 || nonAbstractCount != 1) {
                    //             ^^^ conflict in intf
                    //                    abstract in intf ^^^^
                    logger.debug { "must override because [conflict or all abstract] and no final" }
                    mustOverrides.add(pair)
                    notMustOverride = false
                }
            } else if (superFunction.isFinal) {
                if (nonAbstractCount > 0) {
                    logger.trace { "final conflict and could not override, change to Java" }
                    language = Language.JAVA
                }
                stubOverride.add(pair)
                notMustOverride = false
            } else if (superFunction.body == null) {
                mustOverrides.add(pair)
                notMustOverride = false
            } else if (nonAbstractCount > 0) {
                logger.debug { "must override because super and intf is conflict and no final" }
                mustOverrides.add(pair)
                notMustOverride = false
            } else if (superFunction.isOverrideStub) {
                /**
                 * handle such situation:
                 * ```kotlin
                 * interface I0 {
                 *     fun func() {}
                 * }
                 * interface I1: I0 {
                 *     abstract override fun func()
                 * }
                 * class P: I0
                 * class C: P(), I1
                 * ```
                 * A stub of 'func' will be generated in class 'P',
                 * but 'func' in I1 actually override it, so we should do a must override for 'func'.
                 */
                val superNonStubDeepOverrides = mutableListOf<IrFunctionDeclaration>()
                superFunction.traverseOverride {
                    if (!it.isOverrideStub) {
                        superNonStubDeepOverrides.add(it)
                    }
                }

                for (func in functions) {
                    func.traverseOverride {
                        if (it in superNonStubDeepOverrides) {
                            superNonStubDeepOverrides.remove(it)
                        }
                    }
                }
                if (superNonStubDeepOverrides.isEmpty()) {
                    mustOverrides.add(pair)
                    notMustOverride = false
                }
            }

            logger.trace { "not must override: $notMustOverride" }
            if (notMustOverride) {
                require(pair.first?.isFinal != true)
                require(pair.second.all { !it.isFinal })
                canOverride.add(pair)
            }
        }

        for ((superFunc, intfFunc) in mustOverrides) {
            val first = superFunc ?: intfFunc.first()
            val superAndIntf = if (superFunc != null) {
                intfFunc + superFunc
            } else intfFunc
            require(this.functions.all { !it.signatureEquals(first) })
            logger.trace { "must override" }
            genOverrideFunction(
                superAndIntf, makeAbstract = false,
                isStub = false, superFunc?.isFinal, language = this.language
            )
        }

        for ((superFunc, intfFunc) in stubOverride) {
            val first = superFunc ?: intfFunc.first()
            val superAndIntf = if (superFunc != null) {
                intfFunc + superFunc
            } else intfFunc
            require(this.functions.all { !it.signatureEquals(first) })
            logger.trace { "stub override" }
            genOverrideFunction(
                superAndIntf, makeAbstract = false,
                isStub = true, superFunc?.isFinal, language = this.language
            )
        }

        for ((superFunc, intfFunc) in canOverride) {
            val superAndIntf = if (superFunc != null) {
                intfFunc + superFunc
            } else intfFunc
            val doOverride = if (classType == IrClassType.OPEN || classType == IrClassType.FINAL) {
                false
            } else {
                !config.overrideOnlyMustOnes && random.nextBoolean()
            }
            val makeAbstract = if (doOverride) {
                // if doOverride is true, that means config.overrideOnlyMustOnes is already false
                // So no more judgment is needed.
                random.nextBoolean()
            } else {
                false
            }
            val isFinal = doOverride && !makeAbstract && classType != IrClassType.INTERFACE
            val first = superFunc ?: intfFunc.first()
            require(this.functions.all { !it.signatureEquals(first) })
            logger.trace { "can override" }
            genOverrideFunction(superAndIntf, makeAbstract, !doOverride, isFinal, this.language)
        }
    }

    override fun genClass(context: IrContainer, name: String): IrClassDeclaration {
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
                    context,
                    this,
                    classType == IrClassType.ABSTRACT,
                    classType == IrClassType.INTERFACE,
                    language = language
                )
            }
        }
    }

    override fun genFunction(
        classContainer: IrContainer,
        funcContainer: IrContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        name: String,
        language: Language
    ): IrFunctionDeclaration {
        logger.trace {
            val sb = StringBuilder("gen function $name for ")
            if (funcContainer is IrClassDeclaration) {
                sb.append("class ")
                sb.append(funcContainer.name)
            } else {
                sb.append("program.")
            }
            sb.toString()
        }
        return IrFunctionDeclaration(name, funcContainer).apply {
            this.language = language
            funcContainer.functions.add(this)
            if (!inIntf && (!inAbstract || random.nextBoolean())) {
                body = IrBlock()
                isFinal = if (funcContainer is IrClassDeclaration && funcContainer.classType != IrClassType.INTERFACE) {
                    !config.noFinalFunction && random.nextBoolean()
                } else {
                    false
                }
            }
            for (i in 0 until config.functionParameterNumRange.random(random)) {
                parameterList.parameters.add(genFunctionParameter(classContainer))
            }
            genFunctionReturnType(classContainer, this)
        }
    }

    override fun IrClassDeclaration.genOverrideFunction(
        from: List<IrFunctionDeclaration>,
        makeAbstract: Boolean,
        isStub: Boolean,
        isFinal: Boolean?,
        language: Language
    ) {
        logger.trace {
            val sb = StringBuilder("gen override for class: $name\n")
            for (func in from) {
                sb.append("\t\t")
                sb.traceFunction(func)
                sb.append("\n")
            }
            sb.append("\t\tstillAbstract: $makeAbstract, isStub: $isStub, isFinal: $isFinal")
            sb.toString()
        }
        val first = from.first()
        functions.add(IrFunctionDeclaration(first.name, this).apply {
            this.language = language
            isOverride = true
            isOverrideStub = isStub
            override += from
            parameterList = first.parameterList.copyForOverride()
            returnType = first.returnType
            if (!makeAbstract) {
                body = IrBlock()

                this.isFinal = isFinal ?: if (classType != IrClassType.INTERFACE) {
                    !config.noFinalFunction && !isStub && random.nextBoolean()
                } else {
                    false
                }
            } else {
                if (isFinal != null) {
                    this.isFinal = !isStub && isFinal
                }
            }
            require(!isOverrideStub || (isOverrideStub && override.any { it.isFinal } == this.isFinal))
        })
    }

    override fun genFunctionParameter(
        classContainer: IrContainer,
        name: String
    ): IrParameter {
        val chooseType = randomType(classContainer) { true } ?: IrAny
        logger.trace { "gen parameter: $name, $chooseType" }
        return IrParameter(name, chooseType)
    }

    fun genFunctionReturnType(
        classContainer: IrContainer,
        target: IrFunctionDeclaration
    ) {
        val chooseType = randomType(classContainer) { true }
        logger.trace {
            val sb = StringBuilder("gen return type for: ")
            sb.traceFunction(target)
            sb.append(". return type is: $chooseType")
            sb.toString()
        }
        if (chooseType != null) {
            target.returnType = chooseType
        }
    }
}