package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrModule
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.Visibility
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.functions
import com.github.xyzboom.codesmith.ir.properties
import com.github.xyzboom.codesmith.ir.partitionClassesToModules
import com.github.xyzboom.codesmith.ir.repartitionModules
import com.github.xyzboom.codesmith.ir.containers.IrFuncContainer
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.copyForOverride
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.declarations.builder.*
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.isSubTypeOf as isSharedSubTypeOf
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.ALL_BUILTINS
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.utils.choice
import com.github.xyzboom.codesmith.utils.nextBoolean
import com.github.xyzboom.codesmith.validator.getOverrideCandidates
import com.github.xyzboom.codesmith.validator.collectFunctionSignatureMap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

open class IrDeclGenerator(
    internal val config: GeneratorConfig = GeneratorConfig.default,
    internal val random: Random = Random.Default,
    majorLanguage: Language = Language.KOTLIN
) {
    private val majorLanguage = majorLanguage
    private val logger = KotlinLogging.logger {}

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.scala)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    internal val subClassMap = hashMapOf<IrClassDeclaration, MutableList<IrClassDeclaration>>()
    internal val notSubClassCache = hashMapOf<IrClassDeclaration, MutableList<IrClassDeclaration>>()

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
            return if (this !is IrTypeParameter) {
                this !is IrNullableType
            } else {
                /**
                 * T0: Any?
                 * T1: T0
                 * T1 is not subType of Any
                 */
                !this.deepUpperboundNullable()
            }
        }
        if (this === IrNothing) {
            return true
        }

        return when (this) {
            is IrBuiltInType -> false
            is IrTypeParameter -> when (other) {
                is IrTypeParameter -> this.upperboundHave(other)
                is IrSimpleClassifier, is IrNullableType -> this.upperbound.isSubTypeOf(other)
                else -> false // todo other is IrParameterizedClassifier
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

    private var currentAllowVariance: Boolean = false

    private inline fun <T> withVarianceAllowed(allowVariance: Boolean, block: () -> T): T {
        val previousAllowVariance = currentAllowVariance
        currentAllowVariance = allowVariance
        return try {
            block()
        } finally {
            currentAllowVariance = previousAllowVariance
        }
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
            genTypeArguments(fromClasses, fromTypeParameters, result, allowVariance = currentAllowVariance)
        }
        return result.copy()
    }

    fun randomLanguage(): Language {
        if (random.nextBoolean(config.javaRatio)) {
            return Language.JAVA
        }
        return majorLanguage
    }

    private fun randomVisibility(language: Language, topLevel: Boolean): Visibility {
        // Top-level types are used as cross-file and cross-language signature types throughout
        // the graph. Keep them public so no generated public API leaks a less-visible type.
        if (topLevel) return Visibility.PUBLIC
        val choices = when (language) {
            Language.JAVA -> listOf(Visibility.PUBLIC, Visibility.PROTECTED, Visibility.PACKAGE_PRIVATE, Visibility.PRIVATE)
            Language.KOTLIN -> listOf(Visibility.PUBLIC, Visibility.PROTECTED, Visibility.INTERNAL, Visibility.PRIVATE)
            else -> listOf(Visibility.PUBLIC)
        }
        return choices.random(random)
    }

    private fun randomMethodVisibility(language: Language): Visibility {
        val choices = when (language) {
            Language.JAVA -> listOf(
                Visibility.PUBLIC to config.functionPublicVisibilityWeight,
                Visibility.PROTECTED to config.functionProtectedVisibilityWeight,
                Visibility.PACKAGE_PRIVATE to config.functionPackagePrivateVisibilityWeight,
                Visibility.PRIVATE to config.functionPrivateVisibilityWeight,
            )
            Language.KOTLIN -> listOf(
                Visibility.PUBLIC to config.functionPublicVisibilityWeight,
                Visibility.PROTECTED to config.functionProtectedVisibilityWeight,
                Visibility.INTERNAL to config.functionInternalVisibilityWeight,
                Visibility.PRIVATE to config.functionPrivateVisibilityWeight,
            )
            else -> listOf(Visibility.PUBLIC to 1)
        }
        val totalWeight = choices.sumOf { (_, weight) -> weight }
        require(totalWeight > 0) { "Method visibility weights for $language must sum to a positive value" }
        var selection = random.nextInt(totalWeight)
        for ((visibility, weight) in choices) {
            if (selection < weight) return visibility
            selection -= weight
        }
        error("Unreachable method visibility selection for $language")
    }

    private fun overrideVisibility(
        overridden: List<IrFunctionDeclaration>,
        language: Language,
    ): Visibility {
        val inheritedVisibilities = overridden.map { it.visibility }.distinct()
        val preserved = when (inheritedVisibilities.singleOrNull()) {
            Visibility.PUBLIC -> Visibility.PUBLIC
            Visibility.PROTECTED -> Visibility.PROTECTED
            Visibility.PACKAGE_PRIVATE -> if (language == Language.JAVA) Visibility.PACKAGE_PRIVATE else Visibility.PUBLIC
            Visibility.INTERNAL -> if (language == Language.KOTLIN) Visibility.INTERNAL else Visibility.PUBLIC
            Visibility.PRIVATE, null -> Visibility.PUBLIC
        }
        return if (preserved != Visibility.PUBLIC &&
            random.nextBoolean(config.overrideVisibilityWidenProbability)
        ) {
            when {
                inheritedVisibilities.singleOrNull() == Visibility.PACKAGE_PRIVATE && language == Language.JAVA ->
                    if (random.nextBoolean()) Visibility.PROTECTED else Visibility.PUBLIC
                else -> Visibility.PUBLIC
            }
        } else {
            preserved
        }
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
        prog.repartitionModules(config.moduleNumRange, random)
    }

    fun genProgram(): IrProgram {
        logger.trace { "start gen program" }
        val program = buildProgram {
            modules += buildModule { name = "main" }
            nullabilityAnnotationProvider = config.nullabilityAnnotationProvider
        }
        repeat(config.topLevelDeclRange.random(random)) {
            genClass(context = program, randomName(true), randomLanguage())
        }
        // Nested declarations retain the enclosing language. Random class kinds make some
        // visible nested types usable as class or interface supertypes.
        val nestedDeclarations = mutableListOf<Pair<IrClassDeclaration, IrClassDeclaration>>()
        for (outer in program.classes.toList()) {
            if (random.nextBoolean(config.nestedClassProbability)) {
                repeat(config.nestedClassNumRange.random(random)) {
                    val kind = randomClassKind()
                    val nested = buildClassDeclaration {
                        name = randomName(true)
                        language = outer.language
                        // Keep nested declarations inside interfaces public; otherwise vary visibility
                        // independently of whether the nested type is final, open, or abstract.
                        visibility = if (outer.classKind == ClassKind.INTERFACE) {
                            Visibility.PUBLIC
                        } else {
                            randomVisibility(outer.language, topLevel = false)
                        }
                        classKind = kind
                        allSuperTypeArguments = mutableMapOf()
                    }
                    outer.classes += nested
                    nestedDeclarations += outer to nested
                }
            }
        }
        addNestedSupertypes(program, nestedDeclarations)
        logger.trace { "finish gen program" }
        return partitionClassesToModules(program.classes, config.moduleNumRange, random).also { generated ->
            generated.nullabilityAnnotationProvider = program.nullabilityAnnotationProvider
            if (generated.modules.size > 1) {
                val classToModule = mutableMapOf<IrClassDeclaration, IrModule>()
                val functionOwners = mutableMapOf<IrFunctionDeclaration, IrClassDeclaration>()
                fun register(clazz: IrClassDeclaration, module: IrModule) {
                    classToModule[clazz] = module
                    clazz.functions.forEach { functionOwners[it] = clazz }
                    clazz.classes.forEach { register(it, module) }
                }
                for (module in generated.modules) {
                    for (clazz in module.classes) register(clazz, module)
                }
                // Kotlin internal members are module-scoped. Widen internal declarations
                // crossing module boundaries, then propagate public visibility through override
                // descendants so a later widening cannot leave an earlier override narrowed.
                val forcedPublic = mutableSetOf<IrFunctionDeclaration>()
                var changed: Boolean
                do {
                    changed = false
                    for ((function, childClass) in functionOwners) {
                        if (function.language != Language.KOTLIN) continue
                        for (parent in function.override) {
                            if (parent.language != Language.KOTLIN) continue
                            val parentClass = functionOwners[parent] ?: continue
                            val crossesModule = classToModule[childClass] !== classToModule[parentClass]
                            val widensInternalAcrossModules =
                                parent.visibility == Visibility.INTERNAL && crossesModule
                            if (parent.visibility == Visibility.PUBLIC || widensInternalAcrossModules || parent in forcedPublic) {
                                if (forcedPublic.add(function)) changed = true
                                if (widensInternalAcrossModules && forcedPublic.add(parent)) changed = true
                            }
                        }
                    }
                } while (changed)
                forcedPublic.forEach { it.visibility = Visibility.PUBLIC }
            }
        }
    }

    private fun addNestedSupertypes(
        program: IrProgram,
        nestedDeclarations: List<Pair<IrClassDeclaration, IrClassDeclaration>>,
    ) {
        val rootClasses = program.classes.toList()
        val rootIndex = rootClasses.withIndex().associate { it.value to it.index }
        val nestedInheritanceLanguages = setOf(Language.JAVA, Language.KOTLIN)

        fun addNestedInterface(
            target: IrClassDeclaration,
            owner: IrClassDeclaration?,
            candidates: List<Pair<IrClassDeclaration, IrClassDeclaration>>,
        ) {
            if (target.language !in nestedInheritanceLanguages) return
            val interfaces = candidates.filter { (nestedOwner, nested) ->
                nestedOwner !== owner && nested.language in nestedInheritanceLanguages &&
                    nested.visibility == Visibility.PUBLIC && nested.classKind == ClassKind.INTERFACE &&
                    target.implementedTypes.none { it is IrClassifier && it.classDecl === nested }
            }
            if (interfaces.isEmpty() || !random.nextBoolean(config.nestedTypeAsSuperProbability)) return
            val nestedInterface = interfaces.random(random).second
            target.implementedTypes += nestedInterface.type
            subClassMap.getOrPut(nestedInterface) { mutableListOf() }.add(target)
        }

        for ((targetIndex, target) in rootClasses.withIndex()) {
            // Only point from a later top-level declaration to an earlier owner. This preserves
            // the generator's dependency-first acyclic inheritance construction.
            val eligibleNested = nestedDeclarations.filter { (owner, _) ->
                rootIndex[owner]?.let { it < targetIndex } == true
            }
            if (target.language in nestedInheritanceLanguages && target.classKind != ClassKind.INTERFACE &&
                target.superType === IrAny && random.nextBoolean(config.nestedTypeAsSuperProbability)
            ) {
                val nestedBases = eligibleNested.filter { (_, nested) ->
                    nested.language in nestedInheritanceLanguages && nested.visibility == Visibility.PUBLIC &&
                        nested.classKind in setOf(ClassKind.OPEN, ClassKind.ABSTRACT)
                }
                nestedBases.randomOrNull(random)?.second?.let { nestedBase ->
                    target.superType = nestedBase.type
                    subClassMap.getOrPut(nestedBase) { mutableListOf() }.add(target)
                }
            }
            addNestedInterface(target, owner = target, candidates = eligibleNested)
        }

        for ((index, pair) in nestedDeclarations.withIndex()) {
            val (owner, target) = pair
            val earlierNested = nestedDeclarations.take(index).filter { it.first !== owner }
            if (target.language in nestedInheritanceLanguages && target.classKind != ClassKind.INTERFACE &&
                target.superType === IrAny && random.nextBoolean(config.nestedTypeAsSuperProbability)
            ) {
                val nestedBases = earlierNested.filter { (_, nested) ->
                    nested.language in nestedInheritanceLanguages && nested.visibility == Visibility.PUBLIC &&
                        nested.classKind in setOf(ClassKind.OPEN, ClassKind.ABSTRACT)
                }
                nestedBases.randomOrNull(random)?.second?.let { nestedBase ->
                    target.superType = nestedBase.type
                    subClassMap.getOrPut(nestedBase) { mutableListOf() }.add(target)
                }
            }
            addNestedInterface(target, owner, earlierNested)
        }
    }

    fun IrTypeParameterContainer.genTypeParameter(
        context: IrProgram,
        availableTypeParameters: List<IrTypeParameter>,
    ) {
        // TODO: to support nested upperbound like T1 : A<B<T1>>,
        //  we need to generate upperbound after all type parameters are generated
        val classesShouldNotBeIncluded = if (this is IrClassDeclaration) {
            listOf(this)
        } else emptyList()
        val isFunction = this is IrFunctionDeclaration
        logger.trace { "isFunction: $isFunction, allowFunctionLevelTypeParameterAsUpperbound: ${config.allowFunctionLevelTypeParameterAsUpperbound}" }
        this.typeParameters.add(buildTypeParameter {
            this.name = nextTypeParameterName()
            logger.trace { "generated parameter ${this.name}" }
            val upperboundOri = if (config.typeParameterUpperboundAlwaysAny) {
                logger.trace { "choose IrAny as upperbound config set always" }
                IrAny
            } else {
                // allow upperbound to be a type parameter generated just now
                val typeParametersGeneratedJustNow =
                    if (!isFunction || config.allowFunctionLevelTypeParameterAsUpperbound) {
                        this@genTypeParameter.typeParameters
                    } else {
                        // to avoid KT-78819
                        emptyList()
                    }
                logger.trace {
                    "typeParametersGeneratedJustNow: ${
                        typeParametersGeneratedJustNow
                            .joinToString { it.render() }
                    }"
                }
                randomType(
                    context.classes - classesShouldNotBeIncluded,
                    availableTypeParameters + typeParametersGeneratedJustNow,
                    false
                ) {
                    // currently not support nested
                    it !is IrParameterizedClassifier
                            && (config.allowUnitInTypeArgument || it !== IrUnit)
                            && (config.allowNothingInTypeArgument || it !== IrNothing)
                }
            } ?: IrAny
            val makeNullable = if (random.nextBoolean(config.typeParameterUpperboundNullableProbability)) {
                buildNullableType { innerType = upperboundOri }
            } else {
                upperboundOri
            }
            this.upperbound = makeNullable
            logger.trace { "choose upperbound ${this.upperbound.render()}" }
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
                    allSuperArguments.putAll(superType.getConcreteTypeArguments())
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
                        allSuperArguments.putAll(now.getConcreteTypeArguments())
                    } else {
                        logger.trace { "${now.render()} appeared in super, use it directly." }
                        mayInSuper as IrParameterizedClassifier
                        allSuperArguments.putAll(mayInSuper.getConcreteTypeArguments())
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
                    allSuperArguments.putAll(rawSuper.getConcreteTypeArguments())
                }
                selectedSupers.add(rawSuper)
                logger.trace { "added ${rawSuper.render()} to selectedSupers" }
            }
            true
        }
    }

    /**
     * Check whether a candidate [IrType] is legally available at the current type argument position.
     */
    fun isTypeAvailableForPosition(
        candidate: IrType,
        upperbound: IrType
    ): Boolean {
        if (candidate is IrParameterizedClassifier) {
            return false
        }
        if (!config.allowUnitInTypeArgument && candidate === IrUnit) {
            return false
        }
        if (!config.allowNothingInTypeArgument && candidate === IrNothing) {
            return false
        }
        return candidate.isSharedSubTypeOf(upperbound)
    }

    /**
     * Get available candidate types for the current type argument position.
     */
    fun getAvailableTypesForPosition(
        fromClasses: List<IrClassDeclaration>,
        fromTypeParameters: List<IrTypeParameter>,
        upperbound: IrType
    ): List<IrType> {
        val candidates = mutableListOf<IrType>()
        candidates.addAll(ALL_BUILTINS)
        candidates.addAll(fromClasses.map { it.type })
        candidates.addAll(fromTypeParameters)
        return getAvailableTypesForPosition(candidates, upperbound)
    }

    /**
     * Filter available candidate types for the current type argument position from a given list of [candidateTypes].
     */
    fun getAvailableTypesForPosition(
        candidateTypes: List<IrType>,
        upperbound: IrType
    ): List<IrType> {
        return candidateTypes.filter { candidate ->
            isTypeAvailableForPosition(candidate, upperbound)
        }
    }

    fun genTypeArguments(
        fromClasses: List<IrClassDeclaration>,
        fromTypeParameters: List<IrTypeParameter>,
        targetType: IrParameterizedClassifier,
        allowVariance: Boolean = false
    ) {
        val recordedChosen = mutableMapOf<IrTypeParameterName, IrType>()

        /**
         * ```kt
         * class A<T0, T1: T0, T2>
         * ```
         * Consuming we have replaced `T1` with `B`,
         * and we are considering `T2` in the second loop.
         * now we can call [getGeneratedTypeArg] passing `T1` and get `B`.
         */
        fun getGeneratedTypeArg(typeParam: IrTypeParameter): IrType {
            if (targetType.classDecl.typeParameters.all { it.name != typeParam.name }) {
                return typeParam
            }
            val record = recordedChosen[IrTypeParameterName(typeParam.name)]
                ?: typeParam.upperbound.notNullType
            return if (record is IrTypeParameter) {
                recordedChosen[IrTypeParameterName(record.name)] ?: record.upperbound.notNullType
            } else record
        }
        for (typeParamInTarget in targetType.classDecl.typeParameters) {
            val upperboundInTarget = typeParamInTarget.upperbound
            val argUpperbound = getGeneratedTypeArg(typeParamInTarget)
            val notNullArgUpperbound = argUpperbound.notNullType
            val usedAsLaterUpperbound = targetType.classDecl.typeParameters
                .dropWhile { it !== typeParamInTarget }
                .drop(1)
                .any { it.upperbound.containsTypeParameter(typeParamInTarget) }

            val chosenVariance = if (!usedAsLaterUpperbound && allowVariance && config.enableUseSiteVariance &&
                random.nextBoolean(config.useSiteVarianceProbability)
            ) {
                if (random.nextBoolean(config.starProjectionProbability)) {
                    IrVariance.STAR
                } else if (random.nextBoolean(config.covariantProbability)) {
                    IrVariance.COVARIANT
                } else {
                    IrVariance.CONTRAVARIANT
                }
            } else {
                IrVariance.INVARIANT
            }

            val typeArgument: IrTypeArgument = if (chosenVariance == IrVariance.STAR) {
                recordedChosen[IrTypeParameterName(typeParamInTarget.name)] = notNullArgUpperbound
                IrTypeArgument.STAR
            } else {
                val availableTypes = getAvailableTypesForPosition(
                    fromClasses,
                    fromTypeParameters,
                    argUpperbound
                )
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
                val chooseType = notNullArgUpperbound as? IrTypeParameter
                    ?: (randomType(fromClasses, fromTypeParameters, false) {
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
                        availableTypes.any { availableType -> areEqualTypes(availableType, it) }
                    } ?: run {
                        logger.trace {
                            "choose default upperbound as the argument of ${typeParamInTarget.render()}"
                        }
                        /*
                        val defaultArg = argUpperbound.copy()
                        if (defaultArg is IrTypeParameter) {
                            recordedChosen[IrTypeParameterName(defaultArg.name)] ?: defaultArg
                        } else {
                            defaultArg
                        }*/
                        argUpperbound.copy()
                    })
                val makeNullable = if (upperboundInTarget is IrNullableType &&
                    !random.nextBoolean(config.notNullTypeArgForNullableUpperboundProbability)
                ) {
                    logger.trace { "make nullable (type parameter ${typeParamInTarget.render()} upperbound is nullable)" }
                    buildNullableType { innerType = chooseType }
                } else {
                    chooseType
                }
                logger.trace { "choose ${makeNullable.render()} as the the argument of ${typeParamInTarget.render()}" }
                recordedChosen[IrTypeParameterName(typeParamInTarget.name)] = makeNullable
                IrTypeArgument(chosenVariance, makeNullable)
            }
            targetType.putTypeArgument(typeParamInTarget, typeArgument)
        }
    }

    fun IrClassDeclaration.genOverrides() {
        logger.trace { "start gen overrides for: ${this.name}" }

        val signatureMap = collectFunctionSignatureMap().mapNotNull { (signature, pair) ->
            fun availableFromCurrentLanguage(function: IrFunctionDeclaration): Boolean =
                function.visibility != Visibility.PRIVATE &&
                    !(language == Language.JAVA && function.language == Language.KOTLIN &&
                        function.visibility == Visibility.INTERNAL)
            val visibleSuper = pair.first?.takeIf(::availableFromCurrentLanguage)
            val visibleInterfaces = pair.second.filter(::availableFromCurrentLanguage).toMutableSet()
            if (visibleSuper == null && visibleInterfaces.isEmpty()) null
            else signature to (visibleSuper to visibleInterfaces)
        }.toMap().toMutableMap()
        val (mustOverrides, canOverride, stubOverride) = getOverrideCandidates(signatureMap)

        for ((superFunc, intfFunc) in mustOverrides) {
            val superAndIntf = if (superFunc != null) {
                intfFunc + superFunc
            } else intfFunc
            logger.trace { "must override" }
            genOverrideFunction(
                superAndIntf.toList(), makeAbstract = false,
                isStub = false, superFunc?.isFinal, language = this.language
            )
        }

        for ((superFunc, intfFunc) in stubOverride) {
            val superAndIntf = if (superFunc != null) {
                intfFunc + superFunc
            } else intfFunc
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
                superAndIntf.toList(), makeAbstract, !doOverride, isFinal, this.language
            )
        }
    }

    private fun IrClassDeclaration.hasUnimplementedKotlinInternalAbstractMember(): Boolean {
        if (classKind == ClassKind.ABSTRACT || classKind == ClassKind.INTERFACE) {
            return false
        }

        val ancestors = mutableListOf<IrClassDeclaration>()
        val visited = mutableSetOf(this)
        fun collect(type: IrType?) {
            val classifier = type as? IrClassifier ?: return
            val ancestor = classifier.classDecl
            if (!visited.add(ancestor)) return
            ancestors += ancestor
            collect(ancestor.superType)
            ancestor.implementedTypes.forEach(::collect)
        }
        collect(superType)
        implementedTypes.forEach(::collect)

        val inheritedInternalAbstracts = ancestors
            .asSequence()
            .flatMap { it.functions.asSequence() }
            .filter {
                it.language == Language.KOTLIN &&
                    it.visibility == Visibility.INTERNAL &&
                    it.body == null &&
                    !it.isOverrideStub
            }
            .toList()
        if (inheritedInternalAbstracts.isEmpty()) return false

        val hierarchyFunctions = (listOf(this) + ancestors).flatMap { it.functions }
        fun IrFunctionDeclaration.overridesTransitively(target: IrFunctionDeclaration): Boolean {
            val seen = mutableSetOf<IrFunctionDeclaration>()
            fun visits(function: IrFunctionDeclaration): Boolean {
                if (!seen.add(function)) return false
                if (function === target) return true
                return function.override.any(::visits)
            }
            return override.any(::visits)
        }

        return inheritedInternalAbstracts.any { required ->
            hierarchyFunctions.none { implementation ->
                implementation.body != null &&
                    !implementation.isOverrideStub &&
                    implementation.overridesTransitively(required)
            }
        }
    }

    fun genClass(
        context: IrProgram, name: String = randomName(true), language: Language = randomLanguage()
    ): IrClassDeclaration {
        val clazz = buildClassDeclaration {
            this.name = name
            this.classKind = randomClassKind()
            this.language = language
            this.visibility = randomVisibility(language, topLevel = true)
            allSuperTypeArguments = mutableMapOf()
        }
        context.modules.first().classes.add(clazz)
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
            if (hasUnimplementedKotlinInternalAbstractMember()) {
                // Keep the inheritance edge, but do not emit a concrete class when the
                // generated source has no legal implementation for an inherited abstract member.
                classKind = ClassKind.ABSTRACT
            }
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
            this.visibility = when {
                inIntf -> Visibility.PUBLIC
                topLevel -> randomVisibility(language, topLevel = true)
                else -> randomMethodVisibility(language)
            }
            if (funcContainer is IrClassDeclaration) {
                this.containingClassName = funcContainer.name
            }
            parameterList = buildParameterList()
        }.apply {
            if (random.nextBoolean(config.functionHasTypeParameterProbability)) {
                logger.trace { "generate function parameters for: ${this.name}" }
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
            // Abstract declarations cannot be private in either source language. Private
            // declarations also cannot be open/overridden in Kotlin.
            if (body == null) visibility = Visibility.PUBLIC
            if (visibility == Visibility.PRIVATE) isFinal = true
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

    fun IrClassDeclaration.genOverrideFunction(
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
            this.visibility = overrideVisibility(from, language)
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
                 * The logic for replacing type arguments has been postponed to the print phase.
                 */
                this.typeParameters.add(typeParameter)
            }
            isOverride = true
            isOverrideStub = isStub
            override += from
            parameterList = first.parameterList.copyForOverride()
            containingClassName = this@genOverrideFunction.name
            this.returnType = first.returnType.copy()
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
        logger.trace {
            val sb = StringBuilder("finish gen override function.\n")
            sb.traceFunc(function)
            sb.append("\n")
            sb.toString()
        }
    }

    fun genFunctionParameter(
        classContainer: IrProgram,
        classContext: IrClassDeclaration?,
        typeParameterFromFunction: List<IrTypeParameter>,
        name: String = randomName(false)
    ): IrParameter {
        val chooseType =
            withVarianceAllowed(true) {
                randomType(
                    classContainer.classes,
                    typeParameterFromFunction + (classContext?.typeParameters ?: emptyList()),
                    true
                ) {
                    it !== IrUnit && (it !== IrNothing || config.allowNothingInParameter)
                }
            } ?: IrAny
        logger.trace { "gen parameter: $name, ${chooseType.render()}" }
        val makeNullableOrPlatformType = if (random.nextBoolean(config.functionParameterNullableProbability)
            || chooseType === IrNothing
        ) {
            buildNullableType {
                innerType = chooseType
            }
        } else if (config.functionParameterPlatformProbability > 0 &&
            random.nextBoolean(config.functionParameterPlatformProbability)
        ) {
            buildPlatformType {
                innerType = chooseType
            }
        } else {
            chooseType
        }
        return buildParameter {
            this.name = name
            this.type = makeNullableOrPlatformType
        }
    }

    fun genFunctionReturnType(
        classContainer: IrProgram,
        classContext: IrClassDeclaration?,
        target: IrFunctionDeclaration
    ) {
        val chooseType = withVarianceAllowed(true) {
            randomType(
                classContainer.classes,
                target.typeParameters + (classContext?.typeParameters ?: emptyList()),
                true
            ) {
                it !== IrNothing || config.allowNothingInReturnType
            }
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