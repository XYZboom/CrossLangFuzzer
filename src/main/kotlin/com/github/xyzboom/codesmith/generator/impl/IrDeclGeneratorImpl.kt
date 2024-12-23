package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.generator.*
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.expressions.*
import com.github.xyzboom.codesmith.ir.expressions.constant.IrInt
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.utils.nextBoolean
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

class IrDeclGeneratorImpl(
    private val config: GeneratorConfig = GeneratorConfig.default,
    private val random: Random = Random.Default,
) : IrDeclGenerator {

    private val logger = KotlinLogging.logger {}

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    private val subTypeMap = hashMapOf<IrClassDeclaration, MutableList<IrClassDeclaration>>()
    private val functionReturnMap = hashMapOf<IrType, MutableList<IrFunctionDeclaration>>()

    private fun StringBuilder.traceFunction(function: IrFunctionDeclaration) {
        append(function.toString())
        append(" from ")
        val container = function.container
        if (container is IrClassDeclaration) {
            append("class ")
            append(container.name)
        }
    }

    private fun StringBuilder.traceProperty(property: IrPropertyDeclaration) {
        append(property.name)
        append(" from ")
        val container = property.container
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

    fun randomLanguage(): Language {
        if (random.nextBoolean(config.javaRatio)) {
            return Language.JAVA
        }
        return Language.KOTLIN
    }

    /**
     * @return true if [this] is subtype of [parent]
     */
    fun IrClassDeclaration.isSubtypeOf(parent: IrClassDeclaration): Boolean {
        var result = false
        traverseSuper {
            if (it === parent) {
                result = true
                return@traverseSuper false
            }
            true
        }
        return result
    }

    private val IrFunctionDeclaration.fromClassType: IrClassType
        get() {
            val clazz = container as IrClassDeclaration? ?: throw IllegalArgumentException()
            return clazz.classType
        }

    fun searchFunction(
        context: IrContainer,
        returnType: IrType,
        allowSubType: Boolean
    ): IrFunctionDeclaration? {
        val matched = context.functions.filter { it.returnType == returnType }
        return matched.randomOrNull(random)
    }

    @Suppress("unused")
    fun shuffleLanguage(prog: IrProgram) {
        for (clazz in prog.classes) {
            clazz.language = randomLanguage()
        }
        for (func in prog.functions) {
            func.language = randomLanguage()
        }
        for (property in prog.properties) {
            property.language = randomLanguage()
        }
    }

    override fun genProgram(): IrProgram {
        logger.trace { "start gen program" }
        return IrProgram().apply {
            for (i in 0 until config.topLevelDeclRange.random(random)) {
                val generator = config.randomTopLevelDeclGenerator(this@IrDeclGeneratorImpl, random)
                generator(
                    this, randomLanguage()
                )
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
            if (superType is IrClassifier) {
                subTypeMap.getOrPut(superType.classDecl) { mutableListOf() }.add(this)
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
            subTypeMap.getOrPut(now.classDecl) { mutableListOf() }.add(this)
            chosenIntf.add(now.classDecl)
            willAdd.add(now)
        }
        implementedTypes.addAll(willAdd)
    }

    /**
     * @param [visitor] return false in [visitor] if want stop
     */
    private fun IrClassDeclaration.traverseSuper(visitor: (IrClassDeclaration) -> Boolean) {
        val superType = superType
        if (superType is IrClassifier) {
            if (!visitor(superType.classDecl)) return
            superType.classDecl.traverseSuper(visitor)
        }
        for (intf in implementedTypes) {
            if (intf is IrClassifier) {
                if (!visitor(intf.classDecl)) return
                intf.classDecl.traverseSuper(visitor)
            }
        }
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
                Pair<IrFunctionDeclaration?, MutableSet<IrFunctionDeclaration>>>()
        //           ^^^^^^^^^^^^^^^^^^^^^ decl in super
        //           functions in interfaces ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        for (superType in implementedTypes) {
            superType as? IrClassifier ?: continue
            for (func in superType.classDecl.functions) {
                val signature = func.signature
                result.getOrPut(signature) { null to mutableSetOf() }.second.add(func)
            }
        }
        for ((_, pair) in result) {
            val (_, funcs) = pair
            val willRemove = mutableSetOf<IrFunctionDeclaration>()
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
                    result[signature] = function to mutableSetOf()
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
                 * but 'func' in 'I1' actually override it, so we should do a must override for 'func'.
                 * And this situation:
                 * ```kotlin
                 * interface I0 {
                 *     fun func() {}
                 * }
                 * open class P: I0
                 * interface I1 {
                 *     fun func()
                 * }
                 * class C: P(), I1
                 * ```
                 * A stub of 'func' will be generated in class 'P',
                 * but 'func' in 'I1' conflict with the one in 'P'(actually 'I0'),
                 * so we should do a must override for 'func'.
                 */
                val nonStubOverrides = mutableSetOf<IrFunctionDeclaration>()
                // collect all non stubs
                superFunction.traverseOverride {
                    if (!it.isOverrideStub) {
                        nonStubOverrides.add(it)
                    }
                }

                for (func in functions) {
                    if (!func.isOverrideStub) {
                        nonStubOverrides.add(func)
                    } else {
                        func.traverseOverride {
                            if (!it.isOverrideStub) {
                                nonStubOverrides.add(it)
                            }
                        }
                    }
                }
                val nonStubNonAbstractCount = nonStubOverrides.count { it.body != null }
                if (nonStubNonAbstractCount > 0) {
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
                superAndIntf.toList(), makeAbstract = false,
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
                superAndIntf.toList(), makeAbstract = false,
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
            genOverrideFunction(superAndIntf.toList(), makeAbstract, !doOverride, isFinal, this.language)
        }
    }

    override fun genClass(context: IrContainer, name: String, language: Language): IrClassDeclaration {
        val classType = randomClassType()
        return IrClassDeclaration(name, classType).apply {
            this.language = language
            context.classes.add(this)
            genSuperTypes(context)

            for (i in 0 until config.classMemberNumRange.random(random)) {
                val generator: IrClassMemberGenerator = if (classType != IrClassType.INTERFACE) {
                    config.randomClassMemberGenerator(this@IrDeclGeneratorImpl, random)
                } else {
                    this@IrDeclGeneratorImpl::genFunction
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
        }
    }

    override fun genFunction(
        classContainer: IrContainer,
        funcContainer: IrContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        returnType: IrType?,
        name: String,
        language: Language
    ): IrFunctionDeclaration {
        val topLevel = funcContainer is IrProgram
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
            if ((!inIntf && (!inAbstract || random.nextBoolean())) || topLevel) {
                body = IrBlock()
                isFinal = when {
                    topLevel -> true
                    funcContainer is IrClassDeclaration && funcContainer.classType != IrClassType.INTERFACE ->
                        !config.noFinalFunction && random.nextBoolean()

                    else -> false
                }
            }
            for (i in 0 until config.functionParameterNumRange.random(random)) {
                parameterList.parameters.add(genFunctionParameter(classContainer))
            }
            genFunctionReturnType(classContainer, this)
            if (random.nextBoolean(config.printJavaNullableAnnotationProbability)) {
                logger.trace { "make $name print nullable annotations" }
                printNullableAnnotations = true
            }
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
            if (random.nextBoolean(config.printJavaNullableAnnotationProbability)) {
                logger.trace { "make $name print nullable annotations" }
                printNullableAnnotations = true
            }
        })
    }

    override fun genProperty(
        classContainer: IrContainer,
        propContainer: IrContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        type: IrType?,
        name: String,
        language: Language
    ): IrPropertyDeclaration {
        val topLevel = propContainer is IrProgram
        logger.trace {
            val sb = StringBuilder("gen property $name for ")
            if (propContainer is IrClassDeclaration) {
                sb.append("class ")
                sb.append(propContainer.name)
            } else {
                sb.append("program.")
            }
            sb.toString()
        }
        return IrPropertyDeclaration(name, propContainer).apply {
            this.language = language
            propContainer.properties.add(this)
            if ((!inIntf && (!inAbstract || random.nextBoolean())) || topLevel) {
                isFinal = when {
                    topLevel -> true
                    propContainer is IrClassDeclaration && propContainer.classType != IrClassType.INTERFACE ->
                        !config.noFinalFunction && random.nextBoolean()

                    else -> false
                }
            }
            if (random.nextBoolean(config.printJavaNullableAnnotationProbability)) {
                logger.trace { "make $name print nullable annotations" }
                printNullableAnnotations = true
            }
            readonly = topLevel || random.nextBoolean()
            genPropertyType(classContainer, this)
        }
    }

    override fun genFunctionParameter(
        classContainer: IrContainer,
        name: String
    ): IrParameter {
        val chooseType = randomType(classContainer) { true } ?: IrAny
        logger.trace { "gen parameter: $name, $chooseType" }
        val makeNullableType = if (random.nextBoolean(config.functionParameterNullableProbability)) {
            IrNullableType.nullableOf(chooseType)
        } else {
            chooseType
        }
        return IrParameter(name, makeNullableType)
    }

    override fun genFunctionReturnType(
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
            val makeNullableType = if (random.nextBoolean(config.functionReturnTypeNullableProbability)) {
                IrNullableType.nullableOf(chooseType)
            } else {
                chooseType
            }
            target.returnType = makeNullableType
            functionReturnMap.getOrPut(makeNullableType) { mutableListOf() }.add(target)
        }
    }

    fun genPropertyType(
        classContainer: IrContainer,
        target: IrPropertyDeclaration
    ) {
        val chooseType = randomType(classContainer) { true }
        logger.trace {
            val sb = StringBuilder("gen type for property: ")
            sb.traceProperty(target)
            sb.append(". type is: $chooseType")
            sb.toString()
        }
        if (chooseType != null) {
            val makeNullableType = if (random.nextBoolean(config.functionReturnTypeNullableProbability)) {
                IrNullableType.nullableOf(chooseType)
            } else {
                chooseType
            }
            target.type = makeNullableType
        }
    }

    //<editor-fold desc="Expression">
    fun genExpression(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        type: IrType? = null,
        allowSubType: Boolean = false,
        topOnly: Boolean = false
    ): IrExpression {
        val generator = config.randomExpressionGenerator(this, random)
        return generator.invoke(block, functionContext, context, type, allowSubType)
    }

    override fun genNewExpression(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        type: IrType?,
        allowSubType: Boolean
    ): IrNew {
        return IrNew(type ?: TODO())
    }

    override fun genFunctionCall(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        returnType: IrType?,
        allowSubType: Boolean
    ): IrFunctionCall {
        val searchResult = if (returnType != null) {
            searchFunction(context, returnType, allowSubType)
                ?: genTopLevelFunction(
                    context, randomLanguage(),
                    returnType = returnType
                )
        } else {
            TODO()
        }
        return IrFunctionCall(null, searchResult, listOf(TODO("arg todo")))
    }
    //</editor-fold>
}