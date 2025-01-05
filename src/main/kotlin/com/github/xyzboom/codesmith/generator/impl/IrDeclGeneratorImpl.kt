package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.generator.*
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.container.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.expressions.*
import com.github.xyzboom.codesmith.ir.expressions.constant.IrInt
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.builtin.*
import com.github.xyzboom.codesmith.utils.choice
import com.github.xyzboom.codesmith.utils.nextBoolean
import com.github.xyzboom.codesmith.utils.rouletteSelection
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

open class IrDeclGeneratorImpl(
    private val config: GeneratorConfig = GeneratorConfig.default,
    internal val random: Random = Random.Default,
    private val majorLanguage: Language = Language.KOTLIN
) : IrDeclGenerator {

    private val logger = KotlinLogging.logger {}

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.scala)
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

    var typeParameterCount = 0

    fun nextTypeParameterName(): String {
        return "T${typeParameterCount++}"
    }

    override fun randomIrInt(): IrInt {
        return IrInt(random.nextInt())
    }

    override fun randomClassType(): IrClassType {
        return IrClassType.entries.random(random)
    }

    override fun randomType(
        from: IrContainer,
        classContext: IrClassDeclaration?,
        functionContext: IrFunctionDeclaration?,
        finishTypeArguments: Boolean,
        filter: (IrType) -> Boolean
    ): IrType? {
        val builtins = ALL_BUILTINS.filter(filter)
        val fromClassDecl = from.allClasses.map { it.type }.filter(filter)
        val fromClassTypeParameter = classContext?.typeParameters?.filter(filter) ?: emptyList()
        val fromFuncTypeParameter = functionContext?.typeParameters?.filter(filter) ?: emptyList()
        val allList = arrayOf(
            builtins, fromClassDecl, fromClassTypeParameter, fromFuncTypeParameter
        )
        if (allList.all { it.isEmpty() }) {
            return null
        }
        val result = choice(*allList, random = random)
        if (finishTypeArguments && result is IrParameterizedClassifier) {
            genTypeArguments(from, classContext, result)
        }
        return result.copy()
    }

    fun randomLanguage(): Language {
        if (random.nextBoolean(config.javaRatio)) {
            return Language.JAVA
        }
        return majorLanguage
    }

    /**
     * @return true if [this] is subtype of [parent]
     */
    fun IrClassDeclaration.isSubtypeOf(parent: IrClassDeclaration): Boolean {
        var result = false
        traverseSuper {
            if (it is IrClassifier && it.classDecl == parent) {
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
            for (func in clazz.functions) {
                func.printNullableAnnotations = random.nextBoolean(config.printJavaNullableAnnotationProbability)
            }
        }
        for (func in prog.functions) {
            func.language = randomLanguage()
            func.printNullableAnnotations = random.nextBoolean(config.printJavaNullableAnnotationProbability)
        }
        for (property in prog.properties) {
            property.language = randomLanguage()
        }
    }

    override fun genProgram(): IrProgram {
        logger.trace { "start gen program" }
        return IrProgram(majorLanguage).apply {
            for (i in 0 until config.topLevelDeclRange.random(random)) {
                val generator = randomTopLevelDeclGenerator()
                generator(
                    this, randomLanguage()
                )
            }
            logger.trace { "finish gen program" }
        }
    }

    fun IrTypeParameterContainer.genTypeParameter() {
        val name = nextTypeParameterName()
        typeParameters.add(IrTypeParameter.create(name, IrAny))
    }

    fun genTypeArguments(
        context: IrContainer,
        classContext: IrClassDeclaration?,
        superType: IrParameterizedClassifier
    ) {
        for (typeParam in superType.classDecl.typeParameters) {
            val chooseType = randomType(context, classContext, null, false) {
                it !is IrParameterizedClassifier // for now, we forbid nested cases
                        && (config.allowUnitInTypeArgument || it !== IrUnit)
            } ?: IrAny // for now, we do not talk about upperbound
            superType.putTypeArgument(typeParam, chooseType)
        }
    }

    override fun IrClassDeclaration.genSuperTypes(context: IrContainer) {
        logger.trace { "start gen super types for ${this.name}" }
        val classType = classType
        val selectedSupers = mutableListOf<IrType>()
        val allSuperArguments: MutableMap<IrTypeParameter, IrType> = mutableMapOf()
        if (classType != IrClassType.INTERFACE) {
            val superType = randomType(
                context, classContext = null, functionContext = null,
                finishTypeArguments = false
                //                    ^^^^^
                // as we don't want to search type parameters, so classContext and functionContext here is null,
                // but we want to use type parameters as type arguments, so we do it in our own.
            ) {
                (it.classType == IrClassType.OPEN || it.classType == IrClassType.ABSTRACT) && it != this.type
            }
            logger.trace { "choose super: $superType" }
            if (superType is IrClassifier) {
                subTypeMap.getOrPut(superType.classDecl) { mutableListOf() }.add(this)
                if (superType is IrParameterizedClassifier) {
                    genTypeArguments(context, this, superType)
                    allSuperArguments.putAll(superType.getTypeArguments())
                }
                logger.trace { "all super type args: $allSuperArguments" }
                recordSelectedSuper(superType, selectedSupers, allSuperArguments)
            }
            this.superType = superType ?: IrAny
        }
        val willAdd = mutableSetOf<IrType>()
        for (i in 0 until config.classImplNumRange.random(random)) {
            logger.trace { "selected supers: $selectedSupers" }
            val now = randomType(context, null, null, false) { consideringType ->
                logger.trace { "considering $consideringType" }
                var superWasSelected = false
                if (consideringType is IrClassifier) {
                    consideringType.classDecl.traverseSuper {
                        if (selectedSupers.any { it1 -> it.equalsIgnoreTypeArguments(it1) }) {
                            logger.trace { "$it was selected." }
                            superWasSelected = true
                            return@traverseSuper false
                        }
                        true
                    }
                }
                val result = consideringType.classType == IrClassType.INTERFACE
                        && !superWasSelected
                        && willAdd.all { !it.equalsIgnoreTypeArguments(consideringType) }
                        && consideringType != this.type
                logger.trace {
                    "$consideringType ${
                        if (result) {
                            "can"
                        } else {
                            "can't"
                        }
                    } be considered."
                }
                return@randomType result
            }
            if (now == null) break
            if (now is IrClassifier) {
                logger.trace { "add $now into implement interfaces" }
                subTypeMap.getOrPut(now.classDecl) { mutableListOf() }.add(this)
                if (now is IrParameterizedClassifier) {
                    val mayInSuper = selectedSupers.firstOrNull { it.equalsIgnoreTypeArguments(now) }
                    if (mayInSuper == null) {
                        logger.trace { "$now is not appeared in super, use it with generated type args." }
                        genTypeArguments(context, this, now)
                        allSuperArguments.putAll(now.getTypeArguments())
                    } else {
                        logger.trace { "$now appeared in super, use it directly." }
                        mayInSuper as IrParameterizedClassifier
                        allSuperArguments.putAll(mayInSuper.getTypeArguments())
                        now.putAllTypeArguments(allSuperArguments)
                    }
                }
                recordSelectedSuper(now, selectedSupers, allSuperArguments)
            }
            willAdd.add(now)
        }
        implementedTypes.addAll(willAdd)
        allSuperTypeArguments.putAll(allSuperArguments)
        logger.trace { "finish gen super types for ${this.name}" }
    }

    private fun recordSelectedSuper(
        now: IrClassifier,
        selectedSupers: MutableList<IrType>,
        allSuperArguments: MutableMap<IrTypeParameter, IrType>
    ) {
        logger.trace { "recording $now into selected super" }
        selectedSupers.add(now)
        now.classDecl.traverseSuper {
            if (selectedSupers.all { it1 -> !it.equalsIgnoreTypeArguments(it1) }) {
                logger.trace { "adding $it to selectedSupers" }
                val rawSuper = it.copy()
                if (rawSuper is IrParameterizedClassifier) {
                    rawSuper.putAllTypeArguments(allSuperArguments)
                    /**
                     * Record indirectly use.
                     * GrandParent<T0>
                     * Parent<T1>: GrandParent<T1>
                     * Child<T2>: Parent<T2>
                     * GrandChild<T3>: Child<T3>
                     * When [now] is GrandChild, we first meet Child(T2 [ T3 ]),
                     * and a k-v pair (T2: T3) is recorded into [allSuperArguments].
                     * Then we meet Parent(T1 [ T2 ]), since we already have a (T2: T3) in [allSuperArguments],
                     * a Parent(T1 [ T3 ]) will be successfully recorded into [selectedSupers].
                     * After this, we meet GrandParent(T0 [ T1 ]), that's why we need the following line.
                     * We need to record a (T1: T3).
                     */
                    allSuperArguments.putAll(rawSuper.getTypeArguments())
                }
                selectedSupers.add(rawSuper)
                logger.trace { "added $rawSuper to selectedSupers" }
            }
            true
        }
    }

    /**
     * @param [visitor] return false in [visitor] if want stop
     */
    private fun IrClassDeclaration.traverseSuper(visitor: (IrType) -> Boolean) {
        val superType = superType
        if (superType is IrClassifier) {
            if (!visitor(superType)) return
            superType.classDecl.traverseSuper(visitor)
        }
        for (intf in implementedTypes) {
            if (intf is IrClassifier) {
                if (!visitor(intf)) return
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
                if (nonStubNonAbstractCount > 0 && nonStubOverrides.size > 1) {
                    //                      ^^^ may conflict
                    //    override several functions, conflict confirmed ^^^
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
            val superAndIntf = if (superFunc != null) {
                intfFunc + superFunc
            } else intfFunc
            logger.trace { "must override" }
            genOverrideFunction(
                superAndIntf.toList(), makeAbstract = false,
                isStub = false, superFunc?.isFinal, language = this.language, allSuperTypeArguments
            )
        }

        for ((superFunc, intfFunc) in stubOverride) {
            val superAndIntf = if (superFunc != null) {
                intfFunc + superFunc
            } else intfFunc
            logger.trace { "stub override" }
            genOverrideFunction(
                superAndIntf.toList(), makeAbstract = false,
                isStub = true, superFunc?.isFinal, language = this.language, allSuperTypeArguments
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
            logger.trace { "can override" }
            genOverrideFunction(
                superAndIntf.toList(), makeAbstract, !doOverride, isFinal, this.language, allSuperTypeArguments
            )
        }
    }

    override fun genClass(context: IrContainer, name: String, language: Language): IrClassDeclaration {
        val classType = randomClassType()
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
        require(!topLevel || returnType !is IrTypeParameter)
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
                parameterList.parameters.add(
                    genFunctionParameter(
                        classContainer,
                        funcContainer as? IrClassDeclaration,
                        this
                    )
                )
            }
            if (returnType == null) {
                genFunctionReturnType(classContainer, funcContainer as? IrClassDeclaration, this)
            } else {
                logger.trace { "use given return type: $returnType" }
                this.returnType = returnType
            }
            require(!topLevel || this.returnType !is IrTypeParameter)
            if (random.nextBoolean(config.printJavaNullableAnnotationProbability)) {
                logger.trace { "make $name print nullable annotations" }
                printNullableAnnotations = true
            }
            val block = body
            /*if (block != null) {
                repeat(config.functionExpressionNumRange.random(random)) {
                    val expr = genExpression(block, this, classContainer.program)
                    block.expressions.add(expr)
                }
                if (this.returnType !== IrUnit) {
                    val returnExpr = genExpression(block, this, classContainer.program, this.returnType)
                    block.expressions.add(IrReturnExpression(returnExpr))
                } else {
                    block.expressions.add(IrReturnExpression(null))
                }
            }*/
        }
    }

    /**
     * ```kotlin
     * interface I<T0> {
     *     fun func(i: I<Any>)
     * }
     * ```
     * For `i` in `func`, its type is "I(T0 [ Any ])".
     * If we have a class implements I<String>, the [typeArguments] here will be "T0 [ String ]".
     * For such situation, [onlyValue] must be `true`.
     * @see [IrParameterizedClassifier.putAllTypeArguments]
     */
    private fun getActualTypeFromArguments(
        oriType: IrType,
        typeArguments: Map<IrTypeParameter, IrType>,
        onlyValue: Boolean
    ): IrType {
        if (oriType in typeArguments) {
            // replace type parameter in super with type argument
            return typeArguments[oriType]!!
        }
        if (oriType is IrNullableType) {
            return IrNullableType.nullableOf(getActualTypeFromArguments(oriType.innerType, typeArguments, onlyValue))
        }
        if (oriType is IrParameterizedClassifier) {
            oriType.putAllTypeArguments(typeArguments, onlyValue)
        }
        return oriType
    }

    override fun IrClassDeclaration.genOverrideFunction(
        from: List<IrFunctionDeclaration>,
        makeAbstract: Boolean,
        isStub: Boolean,
        isFinal: Boolean?,
        language: Language,
        putAllTypeArguments: Map<IrTypeParameter, IrType>
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
            for (param in parameterList.parameters) {
                param.type = getActualTypeFromArguments(param.type, putAllTypeArguments, true)
            }
            val returnType = first.returnType.copy()
            this.returnType = getActualTypeFromArguments(returnType, putAllTypeArguments, true)
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
            genPropertyType(classContainer, propContainer as? IrClassDeclaration, this)
        }
    }

    override fun genFunctionParameter(
        classContainer: IrContainer,
        classContext: IrClassDeclaration?,
        target: IrFunctionDeclaration,
        name: String
    ): IrParameter {
        val chooseType = randomType(classContainer, classContext, target, true) {
            if (target.topLevel) {
                it !is IrTypeParameter
            } else {
                true
            } && it !== IrUnit && (it !== IrNothing || config.allowNothingInParameter)
        } ?: IrAny
        logger.trace { "gen parameter: $name, $chooseType" }
        val makeNullableType = if (random.nextBoolean(config.functionParameterNullableProbability)
            || chooseType === IrNothing
        ) {
            IrNullableType.nullableOf(chooseType)
        } else {
            chooseType
        }
        return IrParameter(name, makeNullableType)
    }

    override fun genFunctionReturnType(
        classContainer: IrContainer,
        classContext: IrClassDeclaration?,
        target: IrFunctionDeclaration
    ) {
        val chooseType = randomType(classContainer, classContext, target, true) {
            if (target.topLevel) {
                it !is IrTypeParameter
            } else {
                true
            } && it !== IrNothing || config.allowNothingInReturnType
        }
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
        classContext: IrClassDeclaration?,
        target: IrPropertyDeclaration
    ) {
        val chooseType = randomType(classContainer, classContext, null, true) { true }
        logger.trace {
            val sb = StringBuilder("gen type for property: ")
            sb.traceProperty(target)
            sb.append(". type is: $chooseType")
            sb.toString()
        }
        if (chooseType != null) {
            val makeNullableType = if (random.nextBoolean(config.functionReturnTypeNullableProbability)
                || chooseType === IrNothing
            ) {
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
        leafOnly: Boolean = false
    ): IrExpression {
        val generator = randomExpressionGenerator(block, functionContext, context, type, leafOnly)
        return generator.invoke(block, functionContext, context, type, allowSubType)
    }

    @Suppress("UNUSED_PARAMETER")
    fun expressionAlwaysAvailable(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        type: IrType?
    ): Boolean {
        return true
    }

    fun newExpressionAvailable(
        @Suppress("UNUSED_PARAMETER") block: IrExpressionContainer,
        @Suppress("UNUSED_PARAMETER") functionContext: IrFunctionDeclaration,
        @Suppress("UNUSED_PARAMETER") context: IrProgram,
        type: IrType?
    ): Boolean {
        return type == null ||
                (type !is IrTypeParameter && (type.classType == IrClassType.FINAL
                        || type.classType == IrClassType.OPEN)
                        )
    }

    override fun genNewExpression(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        type: IrType?,
        allowSubType: Boolean
    ): IrNew {
        val chooseType = type ?: randomType(
            context, functionContext.container as? IrClassDeclaration, functionContext, true
        ) {
            it is IrSimpleClassifier
                    && (it.classType == IrClassType.OPEN || it.classType == IrClassType.FINAL)
        } ?: IrAny
        require(chooseType !is IrTypeParameter)
        return IrNew.create(chooseType)
    }

    fun functionCallAvailable(
        @Suppress("UNUSED_PARAMETER") block: IrExpressionContainer,
        @Suppress("UNUSED_PARAMETER") functionContext: IrFunctionDeclaration,
        context: IrProgram,
        type: IrType?
    ): Boolean {
        if (type == null) return true
        searchFunction(context, type, false) ?: return type !is IrTypeParameter
        return true
    }

    override fun genFunctionCall(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        returnType: IrType?,
        allowSubType: Boolean
    ): IrFunctionCall {
        val receiver: IrExpression?
        val func = if (returnType != null) {
            receiver = null
            searchFunction(context, returnType, allowSubType)
                ?: run {
                    logger.trace { "no function returns $returnType, generate a new one." }
                    genTopLevelFunction(
                        context, randomLanguage(),
                        returnType = returnType
                    )
                }
        } else {
            val searchFrom: List<Pair<IrExpression?, IrFunctionDeclaration>> =
                context.functions.map { null to it }
            val memberFunctions = block.variables().flatMap {
                when (val type = it.varType) {
                    is IrClassifier -> type.classDecl.functions
                    else -> emptyList()
                }.map { it1 -> it to it1 }
            }
            if (searchFrom.isEmpty() && memberFunctions.isEmpty()) {
                receiver = null
                genTopLevelFunction(context, randomLanguage())
            } else {
                val pair = choice(searchFrom, memberFunctions, random = random)
                receiver = pair.first
                pair.second
            }
        }
        val args = func.parameterList.parameters.map {
            val generator = randomExpressionGenerator(block, functionContext, context, it.type, true)
            generator(block, functionContext, context, it.type, allowSubType)
        }
        return IrFunctionCall(receiver, func, args)
    }

    fun IrExpressionContainer.variables(): List<IrVariable> {
        return expressions.filterIsInstance<IrVariable>()
    }
    //</editor-fold>

    //<editor-fold desc="Generators">
    fun randomClassMemberGenerator(): IrClassMemberGenerator {
        val generators = listOf(
            this::genFunction,
            this::genProperty
        )
        val weights = listOf(
            config.classMemberIsFunctionWeight,
            config.classMemberIsPropertyWeight,
        )
        return rouletteSelection(generators, weights, random)
    }

    fun randomTopLevelDeclGenerator(): IrTopLevelDeclGenerator {
        val generators = listOf(
            this::genTopLevelClass,
            this::genTopLevelFunction,
            this::genTopLevelProperty
        )
        val weights = listOf(
            config.topLevelClassWeight,
            config.topLevelFunctionWeight,
            config.topLevelPropertyWeight
        )
        return rouletteSelection(generators, weights, random)
    }

    val exprCheckerAndGenerator = listOf(
        this::newExpressionAvailable to (this::genNewExpression to config.newExpressionWeight),
        this::functionCallAvailable to (this::genFunctionCall to config.functionCallExpressionWeight)
    )

    val leafOnlyExprCheckerAndGenerator = listOf(
        this::newExpressionAvailable to (this::genNewExpression to config.newExpressionWeight),

        )

    /**
     * If a generator can generate expression of [type], include it in the scope of consideration.
     * Choose one randomly from the scope of consideration.
     */
    fun randomExpressionGenerator(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        type: IrType?,
        leafOnly: Boolean
    ): IrExpressionGenerator {
        val generators = mutableListOf<IrExpressionGenerator>()
        val weights = mutableListOf<Int>()
        val checkerAndGenerator = if (leafOnly) {
            leafOnlyExprCheckerAndGenerator
        } else {
            exprCheckerAndGenerator
        }
        for ((validator, generatorWeightPair) in checkerAndGenerator) {
            if (validator(block, functionContext, context, type)) {
                generators.add(generatorWeightPair.first)
                weights.add(generatorWeightPair.second)
            }
        }
        if (generators.isEmpty()) {
            val chooseType = type ?: randomType(
                context, functionContext.container as? IrClassDeclaration, functionContext, true
            ) { true } ?: IrAny
            return { _, _, _, _, _ -> IrDefaultImpl(chooseType) }
        }
        return rouletteSelection(generators, weights, random)
    }


    //</editor-fold>
}