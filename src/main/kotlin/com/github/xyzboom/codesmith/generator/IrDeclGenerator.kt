package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.containers.IrFuncContainer
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.copyForOverride
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.declarations.builder.*
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.ALL_BUILTINS
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.utils.choice
import com.github.xyzboom.codesmith.utils.nextBoolean
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

open class IrDeclGenerator(
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

    private val subClassMap = hashMapOf<IrClassDeclaration, MutableList<IrClassDeclaration>>()
    private val notSubClassCache = hashMapOf<IrClassDeclaration, MutableList<IrClassDeclaration>>()

    fun IrClassDeclaration.isSubClassOf(other: IrClassDeclaration): Boolean {
        if (notSubClassCache.containsKey(other)) {
            if (this in notSubClassCache[other]!!) {
                logger.trace {
                    "${this.render()} is not a subclass of ${other.render()} in cache"
                }
                return false
            }
        }
        if (!subClassMap.containsKey(other)) {
            notSubClassCache.getOrPut(other) { mutableListOf() }.add(this)
            return false
        }
        for (subClass in subClassMap[other]!!) {
            if (subClass.name == this.name) {
                return true
            }
        }
        logger.trace {
            "${this.render()} is not a subclass of ${other.render()}, record into cache"
        }
        notSubClassCache.getOrPut(this) { mutableListOf() }.add(other)
        return false
    }

    fun IrTypeParameter.upperboundHave(other: IrTypeParameter): Boolean {
        if (areEqualTypes(this.upperbound, other)) {
            return true
        }
        val upperbound = this.upperbound
        return if (upperbound is IrTypeParameter) {
            upperbound.upperboundHave(other)
        } else {
            false
        }
    }

    fun IrType.isSubTypeOf(other: IrType): Boolean {
        if (areEqualTypes(this, other)) {
            return true
        }
        if (other === IrAny) {
            return this !is IrNullableType
        }

        return when (this) {
            is IrBuiltInType -> false
            is IrTypeParameter -> when (other) {
                is IrTypeParameter -> this.upperboundHave(other)
                is IrSimpleClassifier, is IrNullableType -> this.upperbound.isSubTypeOf(other)
                else -> false
            }

            is IrSimpleClassifier -> when (other) {
                is IrTypeParameter -> false
                is IrSimpleClassifier -> this.classDecl.isSubClassOf(other.classDecl)
                is IrNullableType -> false
                else -> false
            }

            is IrNullableType ->
                if (other !is IrNullableType) {
                    false
                } else {
                    this.innerType.isSubTypeOf(other.innerType)
                }

            else -> false
        }
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

    var typeParameterCount = 0

    fun nextTypeParameterName(): String {
        return "T${typeParameterCount++}"
    }

    fun randomClassKind(): ClassKind {
        return ClassKind.entries.random(random)
    }

    open fun randomType(
        fromClasses: List<IrClassDeclaration>,
        fromTypeParameters: List<IrTypeParameter>,
        finishTypeArguments: Boolean,
        filter: (IrType) -> Boolean
    ): IrType? {
        val builtins = ALL_BUILTINS.filter(filter)
        val fromClassDecl = fromClasses.map { it.type }.filter(filter)
        val filteredTypeParameters = fromTypeParameters.filter(filter)
        val allList = arrayOf(
            builtins, fromClassDecl, filteredTypeParameters
        )
        if (allList.all { it.isEmpty() }) {
            return null
        }
        val result = choice(*allList, random = random)
        if (finishTypeArguments && result is IrParameterizedClassifier) {
            genTypeArguments(fromClasses, fromTypeParameters, result)
        }
        return result.copy()
    }

    fun randomLanguage(): Language {
        if (random.nextBoolean(config.javaRatio)) {
            return Language.JAVA
        }
        return majorLanguage
    }

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

    fun genProgram(): IrProgram {
        logger.trace { "start gen program" }
        return buildProgram().apply {
            repeat(config.topLevelDeclRange.random(random)) {
                genClass(context = this, randomName(true), randomLanguage())
            }
            logger.trace { "finish gen program" }
        }
    }

    fun IrTypeParameterContainer.genTypeParameter(
        context: IrProgram,
        availableTypeParameters: List<IrTypeParameter>,
    ) {
        // TODO: to support nested upperbound like T1 : A<B<T1>>,
        //  we need to generate upperbound after all type parameters are generated
        val classesShouldBeRemove = if (this is IrClassDeclaration) {
            listOf(this)
        } else emptyList()
        this.typeParameters.add(buildTypeParameter {
            this.name = nextTypeParameterName()
            if (config.typeParameterUpperboundAlwaysAny) {
                upperbound = IrAny
            } else {
                this.upperbound = randomType(
                    context.classes - classesShouldBeRemove,
                    // allow upperbound to be a type parameter generated just now
                    availableTypeParameters + this@genTypeParameter.typeParameters,
                    false
                ) {
                    // currently not support nested
                    it !is IrParameterizedClassifier
                            && (config.allowUnitInTypeArgument || it !== IrUnit)
                            && (config.allowNothingInTypeArgument || it !== IrNothing)
                } ?: IrAny
            }
        })
    }

    fun IrClassDeclaration.genSuperTypes(context: IrProgram) {
        logger.trace { "start gen super types for ${this.name}" }
        val selectedSupers = mutableListOf<IrType>()
        val allSuperArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>> = mutableMapOf()
        if (classKind != ClassKind.INTERFACE) {
            val superType = randomType(
                context.classes, fromTypeParameters = emptyList(),
                finishTypeArguments = false
                //                    ^^^^^
                // as we don't want to search type parameters,
                // so typeParameterFromClass and typeParameterFromFunction here is null,
                // but we want to use type parameters as type arguments, so we do it in our own.
            ) {
                (it.classKind == ClassKind.OPEN || it.classKind == ClassKind.ABSTRACT) && !areEqualTypes(it, this.type)
            }
            logger.trace { "choose super: ${superType?.render()}" }
            // record superType
            if (superType is IrClassifier) {
                subClassMap.getOrPut(superType.classDecl) { mutableListOf() }.add(this)
                if (superType is IrParameterizedClassifier) {
                    genTypeArguments(context.classes, typeParameters, superType)
                    allSuperArguments.putAll(superType.getTypeArguments())
                }
                logger.trace {
                    "all super type args: " +
                            allSuperArguments.values.joinToString {
                                it.first.name + "[" + it.second.render() + "]"
                            }
                }
                recordSelectedSuper(superType, selectedSupers, allSuperArguments)
            }
            this.superType = superType ?: IrAny
        }
        val willAdd = mutableSetOf<IrType>()
        for (i in 0 until config.classImplNumRange.random(random)) {
            logger.trace { "selected supers: ${selectedSupers.joinToString { it.render() }}" }
            val now = randomType(context.classes, emptyList(), false) { consideringType ->
                logger.trace { "considering ${consideringType.render()}" }
                var superWasSelected = false
                if (consideringType is IrClassifier) {
                    consideringType.classDecl.traverseSuper {
                        if (selectedSupers.any { it1 -> it.equalsIgnoreTypeArguments(it1) }) {
                            logger.trace { "${it.render()} was selected." }
                            superWasSelected = true
                            return@traverseSuper false
                        }
                        true
                    }
                }
                val result = consideringType.classKind == ClassKind.INTERFACE
                        && !superWasSelected
                        && willAdd.all { !it.equalsIgnoreTypeArguments(consideringType) }
                        && !areEqualTypes(consideringType, this.type)
                logger.trace {
                    "${consideringType.render()} ${
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
                subClassMap.getOrPut(now.classDecl) { mutableListOf() }.add(this)
                logger.trace { "add ${now.render()} into implement interfaces" }
                if (now is IrParameterizedClassifier) {
                    val mayInSuper = selectedSupers.firstOrNull { it.equalsIgnoreTypeArguments(now) }
                    if (mayInSuper == null) {
                        logger.trace { "${now.render()} is not appeared in super, use it with generated type args." }
                        genTypeArguments(context.classes, typeParameters, now)
                        allSuperArguments.putAll(now.getTypeArguments())
                    } else {
                        logger.trace { "${now.render()} appeared in super, use it directly." }
                        mayInSuper as IrParameterizedClassifier
                        allSuperArguments.putAll(mayInSuper.getTypeArguments())
                        now.putAllTypeArguments(allSuperArguments)
                    }
                }
                recordSelectedSuper(now, selectedSupers, allSuperArguments)
            }
            willAdd.add(now)
        }
        this.implementedTypes.addAll(willAdd.toList())
        this.allSuperTypeArguments = allSuperArguments
        logger.trace { "finish gen super types for ${this.name}" }
    }

    private fun recordSelectedSuper(
        now: IrClassifier,
        selectedSupers: MutableList<IrType>,
        allSuperArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>
    ) {
        logger.trace { "recording ${now.render()} into selected super" }
        selectedSupers.add(now)
        now.classDecl.traverseSuper {
            if (selectedSupers.all { it1 -> !it.equalsIgnoreTypeArguments(it1) }) {
                logger.trace { "adding ${it.render()} to selectedSupers" }
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
                logger.trace { "added ${rawSuper.render()} to selectedSupers" }
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

    fun genTypeArguments(
        fromClasses: List<IrClassDeclaration>,
        fromTypeParameters: List<IrTypeParameter>,
        superType: IrParameterizedClassifier
    ) {
        val recordedChosen = mutableMapOf<IrTypeParameterName, IrType>()
        fun getTypeArg(typeParam: IrTypeParameter): IrType {
            if (superType.classDecl.typeParameters.all { it.name != typeParam.name }) {
                return typeParam
            }
            val record = recordedChosen[IrTypeParameterName(typeParam.name)] ?: typeParam.upperbound
            return if (record is IrTypeParameter) {
                recordedChosen[IrTypeParameterName(record.name)] ?: record.upperbound
            } else record
        }
        for (typeParam in superType.classDecl.typeParameters) {
            val upperbound = typeParam.upperbound
            val argUpperbound = getTypeArg(typeParam)

            /**
             * ```kt
             * class A<T0, T1: T0, T2>
             * ```
             * For this case, upperbound of `T1` will not be `T0` , instead it will be argument of `T0`.
             *
             * If the unfinished type is `A<T2, ...>`, now we are considering argument for `T1`.
             * We can found that the only type we can choose is `T2`. So we got: `A<T2, T2, MyClass>`
             * Not that `T2` is type parameter from class `A` and the type argument for `T1` in **TYPE** `A`.
             * Full example:
             * ```kt
             * class A<T0, T1: T0, T2> {
             *     fun func(a: A<T2, T2, MyClass>) {}
             * }
             * ```
             */
            val chooseType = argUpperbound as? IrTypeParameter
                ?: (randomType(fromClasses, fromTypeParameters, false) {
                    it !is IrParameterizedClassifier // for now, we forbid nested cases
                            && (config.allowUnitInTypeArgument || it !== IrUnit)
                            && (config.allowNothingInTypeArgument || it !== IrNothing)
                            /**
                             *  If some type parameter has been replaced by a type argument,
                             *  we should consider the upperbound as this type argument,
                             *  not the original type parameter.
                             *  For example: `A<T1: Any?, T2: T1>`
                             *  Consuming we have replaced `T1` with `MyClass`,
                             *  and we are considering `T2` in the second loop.
                             *  Now the upperbound of `T2` is no longer `T1` but `MyClass` instead.
                             */
                            /**
                             *  If some type parameter has been replaced by a type argument,
                             *  we should consider the upperbound as this type argument,
                             *  not the original type parameter.
                             *  For example: `A<T1: Any?, T2: T1>`
                             *  Consuming we have replaced `T1` with `MyClass`,
                             *  and we are considering `T2` in the second loop.
                             *  Now the upperbound of `T2` is no longer `T1` but `MyClass` instead.
                             */
                            && it.isSubTypeOf(argUpperbound)
                            /**
                             * If we have `A <: B`, `class C<T1: A, T2: T1, T3: B>`
                             * Consuming we have replaced `T1` with `A`,
                             * and we are considering `T2` in the second loop.
                             * We can found that `T3 <: A <: B`, but `C<A, T3, ...>` is not a legal type.
                             * That's because the argument of `T3` can be `B`
                             * which is not within the upperbound `T1`(replaced by type argument `A` just now)
                             */
                            /**
                             * If we have `A <: B`, `class C<T1: A, T2: T1, T3: B>`
                             * Consuming we have replaced `T1` with `A`,
                             * and we are considering `T2` in the second loop.
                             * We can found that `T3 <: A <: B`, but `C<A, T3, ...>` is not a legal type.
                             * That's because the argument of `T3` can be `B`
                             * which is not within the upperbound `T1`(replaced by type argument `A` just now)
                             */
                            && (it !is IrTypeParameter || upperbound !is IrTypeParameter)
                } ?: run {
                    logger.trace {
                        "choose default upperbound as the argument of ${typeParam.render()}"
                    }
                    argUpperbound.copy()
                })
            logger.trace { "choose ${chooseType.render()} as the the argument of ${typeParam.render()}" }
            recordedChosen[IrTypeParameterName(typeParam.name)] = chooseType
            superType.putTypeArgument(typeParam, chooseType)
        }
    }

    /**
     * collect a map whose value is a set of function inherited directly from the supers
     * and whose key is the signature of whose value.
     */
    fun IrClassDeclaration.collectFunctionSignatureMap(): FunctionSignatureMap {
        logger.trace { "start collectFunctionSignatureMap for class: $name" }
        val result = mutableMapOf<Signature,
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
                            sb.traceFunc(it)
                            sb.toString()
                        }
                        willRemove.add(it)
                    }
                }
                if (found) {
                    logger.trace {
                        val sb = StringBuilder("found a override, will remove. For function: ")
                        sb.traceFunc(func)
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

    fun IrClassDeclaration.genOverrides() {
        logger.trace { "start gen overrides for: ${this.name}" }

        val signatureMap = collectFunctionSignatureMap()
        val mustOverrides = mutableListOf<SuperAndIntfFunctions>()
        val canOverride = mutableListOf<SuperAndIntfFunctions>()
        val stubOverride = mutableListOf<SuperAndIntfFunctions>()
        for ((signature, pair) in signatureMap) {
            val (superFunction, functions) = pair
            logger.debug { "name: ${signature.name}" }
            logger.trace { "parameter: (${signature.parameterTypes.joinToString { it.render() }})" }
            logger.trace {
                val sb = StringBuilder("super function: \n")
                if (superFunction != null) {
                    sb.append("\t\t")
                    sb.traceFunc(superFunction)
                    sb.append("\n")
                } else {
                    sb.append("\t\tnull\n")
                }

                sb.append("intf functions: \n")

                for (function in functions) {
                    sb.append("\t\t")
                    sb.traceFunc(function)
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
            val doOverride = /*if (classType == IrClassType.OPEN || classType == IrClassType.FINAL) {
                false
            } else {*/
                !config.overrideOnlyMustOnes && random.nextBoolean()
            //}
            val makeAbstract =
                if (doOverride && (classKind == ClassKind.INTERFACE || classKind == ClassKind.ABSTRACT)) {
                    // if doOverride is true, that means config.overrideOnlyMustOnes is already false
                    // So no more judgment is needed.
                    random.nextBoolean()
                } else {
                    false
                }
            val isFinal = doOverride && !makeAbstract && classKind != ClassKind.INTERFACE
            logger.trace { "can override" }
            genOverrideFunction(
                superAndIntf.toList(), makeAbstract, !doOverride, isFinal, this.language, allSuperTypeArguments
            )
        }
    }

    fun genClass(
        context: IrProgram, name: String = randomName(true), language: Language = randomLanguage()
    ): IrClassDeclaration {
        val clazz = buildClassDeclaration {
            this.name = name
            this.classKind = randomClassKind()
            this.language = language
            allSuperTypeArguments = mutableMapOf()
        }
        context.classes.add(clazz)
        clazz.apply {
            if (random.nextBoolean(config.classHasTypeParameterProbability)) {
                repeat(config.classTypeParameterNumberRange.random(random)) {
                    genTypeParameter(context, emptyList())
                }
            }
            genSuperTypes(context)
            repeat(config.classMemberNumRange.random(random)) {
                genFunction(
                    context,
                    this,
                    classKind == ClassKind.ABSTRACT,
                    classKind == ClassKind.INTERFACE,
                    null,
                    randomName(false),
                    language
                )
            }
            genOverrides()
        }
        return clazz
    }

    fun genFunction(
        classContainer: IrProgram,
        funcContainer: IrFuncContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        returnType: IrType?,
        name: String,
        language: Language
    ): IrFunctionDeclaration? {
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
        return buildFunctionDeclaration {
            this.name = name
            this.language = language
            if (funcContainer is IrClassDeclaration) {
                this.containingClassName = funcContainer.name
            }
            parameterList = buildParameterList()
        }.apply {
            if (random.nextBoolean(config.functionHasTypeParameterProbability)) {
                repeat(config.functionTypeParameterNumberRange.random(random)) {
                    genTypeParameter(
                        classContainer, if (funcContainer is IrClassDeclaration) {
                            funcContainer.typeParameters
                        } else emptyList()
                    )
                }
            }
            if ((!inIntf && (!inAbstract || random.nextBoolean())) || topLevel) {
                body = buildBlock()
                isFinal = when {
                    topLevel -> true
                    funcContainer is IrClassDeclaration && funcContainer.classKind != ClassKind.INTERFACE ->
                        !config.noFinalFunction && random.nextBoolean()

                    else -> false
                }
            }
            for (i in 0 until config.functionParameterNumRange.random(random)) {
                parameterList.parameters.add(
                    genFunctionParameter(
                        classContainer,
                        funcContainer as? IrClassDeclaration,
                        this.typeParameters
                    )
                )
            }
            if (returnType == null) {
                genFunctionReturnType(classContainer, funcContainer as? IrClassDeclaration, this)
            } else {
                logger.trace { "use given return type: ${returnType.render()}" }
                this.returnType = returnType
            }
            require(!topLevel || this.returnType !is IrTypeParameter)
            if (random.nextBoolean(config.printJavaNullableAnnotationProbability)) {
                logger.trace { "make $name print nullable annotations" }
                printNullableAnnotations = true
            }
            // todo expressions here
            funcContainer.functions.add(this)
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
        typeArguments: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
        onlyValue: Boolean
    ): IrType {
        if (oriType is IrTypeParameter) {
            val oriName = IrTypeParameterName(oriType.name)
            if (oriName in typeArguments) {
                // replace type parameter in super with type argument
                return typeArguments[oriName]!!.second
            }
        }
        if (oriType is IrNullableType) {
            return buildNullableType {
                innerType = getActualTypeFromArguments(oriType.innerType, typeArguments, onlyValue)
            }
        }
        if (oriType is IrParameterizedClassifier) {
            oriType.putAllTypeArguments(typeArguments, onlyValue)
        }
        return oriType
    }

    fun IrClassDeclaration.genOverrideFunction(
        from: List<IrFunctionDeclaration>,
        makeAbstract: Boolean,
        isStub: Boolean,
        isFinal: Boolean?,
        language: Language,
        putAllTypeArguments: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>
    ) {
        logger.trace {
            val sb = StringBuilder("gen override for class: $name\n")
            for (func in from) {
                sb.append("\t\t")
                sb.traceFunc(func)
                sb.append("\n")
            }
            sb.append("\t\tstillAbstract: $makeAbstract, isStub: $isStub, isFinal: $isFinal")
            sb.toString()
        }
        val first = from.first()
        val function = buildFunctionDeclaration {
            this.name = first.name
            this.language = language
            for (typeParam in first.typeParameters) {
                val typeParameter = typeParam.copy()
                /**
                 * ```kt
                 * interface I<T1> {
                 *     fun <T2: T1> func()
                 * }
                 * class A: I<Any> {
                 *     override fun <T2: T1> func() {}
                 *     //                ^^ need to be replaced by `Any`
                 * }
                 * ```
                 */
                typeParameter.upperbound = getActualTypeFromArguments(
                    typeParameter.upperbound,
                    this@genOverrideFunction.allSuperTypeArguments,
                    false
                )
                this.typeParameters.add(typeParameter)
            }
            isOverride = true
            isOverrideStub = isStub
            override += from
            parameterList = first.parameterList.copyForOverride()
            containingClassName = this@genOverrideFunction.name
            for (param in parameterList.parameters) {
                param.type = getActualTypeFromArguments(param.type, putAllTypeArguments, true)
            }
            val returnType = first.returnType.copy()
            this.returnType = getActualTypeFromArguments(returnType, putAllTypeArguments, true)
            if (!makeAbstract) {
                body = buildBlock()

                this.isFinal = isFinal ?: if (this@genOverrideFunction.classKind != ClassKind.INTERFACE) {
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
        }
        functions.add(function)
    }

    fun genFunctionParameter(
        classContainer: IrProgram,
        classContext: IrClassDeclaration?,
        typeParameterFromFunction: List<IrTypeParameter>,
        name: String = randomName(false)
    ): IrParameter {
        val chooseType =
            randomType(
                classContainer.classes,
                typeParameterFromFunction + (classContext?.typeParameters ?: emptyList()),
                true
            ) {
                if (classContext == null) {
                    it !is IrTypeParameter
                } else {
                    true
                } && it !== IrUnit && (it !== IrNothing || config.allowNothingInParameter)
            } ?: IrAny
        logger.trace { "gen parameter: $name, ${chooseType.render()}" }
        val makeNullableType = if (random.nextBoolean(config.functionParameterNullableProbability)
            || chooseType === IrNothing
        ) {
            buildNullableType {
                innerType = chooseType
            }
        } else {
            chooseType
        }
        return buildParameter {
            this.name = name
            this.type = makeNullableType
        }
    }

    fun genFunctionReturnType(
        classContainer: IrProgram,
        classContext: IrClassDeclaration?,
        target: IrFunctionDeclaration
    ) {
        val chooseType = randomType(
            classContainer.classes,
            target.typeParameters + (classContext?.typeParameters ?: emptyList()),
            true
        ) {
            it !== IrNothing || config.allowNothingInReturnType
        }
        logger.trace {
            val sb = StringBuilder("gen return type for: ")
            sb.traceFunc(target, classContext)
            sb.append(". return type is: ${chooseType?.render()}")
            sb.toString()
        }
        if (chooseType != null && chooseType !== IrUnit) {
            val makeNullableType = if (random.nextBoolean(config.functionReturnTypeNullableProbability)) {
                buildNullableType { innerType = chooseType }
            } else {
                chooseType
            }
            target.returnType = makeNullableType
        }
    }

}