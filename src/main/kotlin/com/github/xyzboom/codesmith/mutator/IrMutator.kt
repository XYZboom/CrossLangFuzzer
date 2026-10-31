package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.functions
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeContainer
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.areEqualTypes
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.types.containsTypeParameter
import com.github.xyzboom.codesmith.ir.types.deepUpperboundNullable
import com.github.xyzboom.codesmith.ir.types.dependsOnTypeParameter
import com.github.xyzboom.codesmith.ir.types.getTypeArguments
import com.github.xyzboom.codesmith.ir.types.getConcreteTypeArguments
import com.github.xyzboom.codesmith.ir.types.getActualTypeFromArguments
import com.github.xyzboom.codesmith.ir.types.isSubClassOf
import com.github.xyzboom.codesmith.ir.types.isSubTypeOf
import com.github.xyzboom.codesmith.ir.types.notNullType
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.render
import com.github.xyzboom.codesmith.utils.rouletteSelection
import com.github.xyzboom.codesmith.validator.hasMissingRequiredOverride
import com.github.xyzboom.codesmith.validator.collectFunctionSignatureMap
import com.github.xyzboom.codesmith.validator.getOverrideCandidates
import com.github.xyzboom.codesmith.validator.implements
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.random.Random

class IrMutator(
    private val config: MutatorConfig = MutatorConfig.default,
    private val generator: IrDeclGenerator,
    private val languageSemantics: MutatorLanguageSemantics = MutatorLanguageSemantics.KOTLIN,
) {
    private val logger = KotlinLogging.logger {}
    private val random: Random = generator.random

    fun mutateGenericArgumentInParent(program: IrProgram): Pair<SemanticValidity, String?>? {
        var result: Pair<SemanticValidity, String?>? = null
        program.randomTraverseClasses(random) { clazz ->
            val parentTypes = listOfNotNull(clazz.superType) + clazz.implementedTypes
            for (parentType in parentTypes) {
                if (parentType is IrParameterizedClassifier) {
                    val typeArguments = parentType.getTypeArguments()
                    val entries = typeArguments.entries.filter { it.value.second.isInvariant && it.value.second.type !== IrAny }
                    if (entries.isEmpty()) continue
                    val (_, pair) = entries.random(random)
                    val (typeParam, typeArg) = pair
                    val visibleClasses = program.visibleClassesFor(clazz)
                    val replaceArg = generator.randomType(
                        visibleClasses, clazz.typeParameters, false
                    ) { type ->
                        type !is IrParameterizedClassifier && !areEqualTypes(type, typeArg.type)
                    } ?: continue
                    val desc = "mutateGenericArgumentInParent at: ${clazz.name}, change: $typeArg into $replaceArg"
                    logger.trace { desc }
                    parentType.putTypeArgument(typeParam, replaceArg)
                    val validity = calculateParentGenericArgumentValidity(
                        program = program,
                        targetClass = clazz,
                        parentType = parentType,
                        changedTypeParameter = typeParam,
                    )
                    result = validity to desc
                    return@randomTraverseClasses true
                }
            }
            false
        }
        return result
    }

    /**
     * Calculates validity after replacing one argument in a parameterized
     * superclass or implemented interface.
     *
     * 1. All arguments of the implemented parameterized type must satisfy their bounds.
     * 2. Any override in the implementing class or downstream subclasses that depends
     *    on this changed generic parameter will have its signature changed upon printing,
     *    causing it to mismatch its upstream or downstream contract, or failing to fulfill
     *    abstract requirements if it is no longer recognized as an override.
     */
    private fun calculateParentGenericArgumentValidity(
        program: IrProgram,
        targetClass: IrClassDeclaration,
        parentType: IrParameterizedClassifier,
        changedTypeParameter: IrTypeParameter,
    ): SemanticValidity {
        val typeArguments = parentType.getTypeArguments()
        val targetModule = program.modules.firstOrNull { targetClass in it.classes }
        if (targetModule != null) {
            val visibleClasses = (targetModule.dependencies + targetModule).flatMap { it.classes }.toSet()
            fun IrType.containsAnyClassNotIn(allowedClasses: Set<IrClassDeclaration>): Boolean = when (this) {
                is IrSimpleClassifier -> classDecl !in allowedClasses
                is IrParameterizedClassifier -> classDecl !in allowedClasses || arguments.values.any { (_, arg) ->
                    arg.type?.containsAnyClassNotIn(allowedClasses) == true
                }
                is IrTypeContainer -> innerType.containsAnyClassNotIn(allowedClasses)
                else -> false
            }
            for ((_, pair) in typeArguments) {
                val innerType = pair.second.type ?: continue
                if (innerType.containsAnyClassNotIn(visibleClasses)) {
                    return SemanticValidity.INVALID
                }
            }
        }
        for ((_, pair) in typeArguments) {
            val (typeParameter, typeArgument) = pair
            val innerType = typeArgument.type ?: continue
            val actualUpperBound = getActualTypeFromArguments(
                typeParameter.upperbound,
                parentType.getConcreteTypeArguments(),
                true,
            )
            if (!innerType.isSubTypeOf(actualUpperBound)) {
                return SemanticValidity.INVALID
            }
        }

        val parentClass = parentType.classDecl
        val affectedParentFunctions = parentClass.functions.filter { function ->
            function.parameterList.parameters.any { it.type.containsTypeParameter(changedTypeParameter) } ||
                    function.returnType.containsTypeParameter(changedTypeParameter) ||
                    function.typeParameters.any {
                        it.upperbound.containsTypeParameter(changedTypeParameter)
                    }
        }
        if (affectedParentFunctions.isEmpty()) {
            return SemanticValidity.VALID
        }

        fun hasAffectedExplicitOverride(clazz: IrClassDeclaration): Boolean {
            return clazz.functions.any { function ->
                function.isOverride &&
                        !function.isOverrideStub &&
                        affectedParentFunctions.any { parentFunction ->
                            function.overridesTransitively(parentFunction)
                        }
            }
        }
        if (hasAffectedExplicitOverride(targetClass) || targetClass.hasMissingRequiredOverride()) {
            return SemanticValidity.INVALID
        }

        for (subClass in program.classes) {
            if (subClass === targetClass || !subClass.isSubClassOf(targetClass)) continue
            if (hasAffectedExplicitOverride(subClass) || subClass.hasMissingRequiredOverride()) {
                return SemanticValidity.INVALID
            }
        }

        return SemanticValidity.VALID
    }

    fun removeOverrideMemberFunction(program: IrProgram): Pair<SemanticValidity, String?>? {
        var result: Pair<SemanticValidity, String?>? = null
        program.randomTraverseMemberFunctions(random) { func, clazz ->
            if (func.isOverride && !func.isOverrideStub) {
                fun hasConcreteFallback(
                    function: IrFunctionDeclaration,
                    visited: MutableSet<IrFunctionDeclaration> = mutableSetOf(),
                ): Boolean {
                    if (!visited.add(function)) return false
                    return function.override.any { parent ->
                        (!parent.isOverrideStub && parent.body != null) ||
                                hasConcreteFallback(parent, visited)
                    }
                }
                val affectedClasses = program.classes.filter { candidate ->
                    candidate === clazz || candidate.isSubClassOf(clazz)
                }
                val hasConcreteFallback = hasConcreteFallback(func)
                func.isOverrideStub = true
                val desc = "removeOverrideMemberFunction in class ${clazz.name}, function ${func.name}"
                logger.trace { desc }
                val concreteDescendantNeedsFunction = affectedClasses.any { candidate ->
                    (candidate.classKind == ClassKind.FINAL || candidate.classKind == ClassKind.OPEN) &&
                            program.classes.none { provider ->
                                candidate.isSubClassOf(provider) &&
                                        provider.functions.any {
                                            !it.isOverrideStub && it.body != null && it.overridesTransitively(func)
                                        }
                            }
                }
                val validity = if (!hasConcreteFallback && concreteDescendantNeedsFunction) {
                    SemanticValidity.INVALID
                } else {
                    SemanticValidity.VALID
                }
                result = validity to desc
                return@randomTraverseMemberFunctions true
            }
            false
        }
        return result
    }

    fun mutateGenericArgumentInMemberFunctionParameter(program: IrProgram): Pair<SemanticValidity, String?>? {
        var result: Pair<SemanticValidity, String?>? = null
        program.randomTraverseMemberFunctions(random) { func, clazz ->
            if (!func.isOverride || func.isOverrideStub) {
                return@randomTraverseMemberFunctions false
            }
            val candidates = func.parameterList.parameters.mapNotNull { parameter ->
                val parameterizedType = parameter.type.parameterizedClassifierOrNull()
                    ?: return@mapNotNull null
                val entries = parameterizedType.getTypeArguments().entries.filter {
                    it.value.second.isInvariant && it.value.second.type !== IrAny
                }
                if (entries.isEmpty()) null else Triple(parameter, parameterizedType, entries)
            }
            if (candidates.isEmpty()) {
                return@randomTraverseMemberFunctions false
            }
            val (param, paramType, entries) = candidates.random(random)
            val (_, pair) = entries.random(random)
            val (typeParam, typeArg) = pair
            val visibleClasses = program.visibleClassesFor(clazz)
            val replaceArg = generator.randomType(
                visibleClasses, clazz.typeParameters + func.typeParameters, false
            ) { type ->
                type !is IrParameterizedClassifier && !areEqualTypes(type, typeArg.type)
            } ?: return@randomTraverseMemberFunctions false
            paramType.putTypeArgument(typeParam, replaceArg)
            val desc = "mutateGenericArgumentInMemberFunctionParameter at: ${clazz.name}.${func.name}, " +
                    "parameter ${param.name}, change: $typeArg into $replaceArg"
            logger.trace { desc }
            /**
             * The operator only mutates a non-stub override. Replacing an
             * invariant parameterized argument changes the source-level
             * parameter type while preserving only its JVM erasure. The
             * declaration therefore no longer overrides its recorded parent
             * method (Java reports a name clash; Kotlin reports that there is
             * nothing to override).
             */
            result = SemanticValidity.INVALID to desc
            return@randomTraverseMemberFunctions true
        }
        return result
    }

    fun mutateParameterNullability(program: IrProgram): Pair<SemanticValidity, String?>? {
        var result: Pair<SemanticValidity, String?>? = null
        program.randomTraverseMemberFunctions(random) { func, clazz ->
            val param = func.parameterList.parameters.firstOrNull {
                if (random.nextBoolean()) {
                    it.type is IrNullableType
                } else {
                    it.type !is IrNullableType
                }
            } ?: return@randomTraverseMemberFunctions false
            val type = param.type
            if (type is IrNullableType) {
                param.type = type.innerType
            } else {
                param.type = buildNullableType {
                    this.innerType = type
                }
            }
            val desc = "mutateParameterNullability at: ${clazz.name}.${func.name}, " +
                    "parameter ${param.name}, before: ${type.render()}, after: ${param.type.render()}"
            logger.trace { desc }
            val validity = if (languageSemantics == MutatorLanguageSemantics.GROOVY) {
                SemanticValidity.UNKNOWN
            } else {
                calculateParameterNullabilityValidity(
                    program = program,
                    function = func,
                    containingClass = clazz,
                    parameterIndex = func.parameterList.parameters.indexOf(param),
                )
            }
            result = validity to desc
            return@randomTraverseMemberFunctions true
        }
        return result
    }

    /**
     * Calculates whether changing one parameter's top-level nullability keeps
     * the declaration valid.
     *
     * A declaration with no effective upstream or downstream Kotlin override
     * remains valid because Java nullability annotations do not change the JVM
     * signature. Override stubs are absent from the emitted source, so their
     * parents remain effective; a real Java/Kotlin redeclaration, however,
     * shields later descendants from older annotations. For a Kotlin override,
     * a non-platform parent normally requires equal top-level nullability after
     * inherited type arguments are substituted. The exception is a conflicting
     * Java annotation when the same child also directly satisfies a compatible
     * Kotlin parent contract. Java nullability annotations do not change the source emitted by the
     * Java printer and therefore must not affect semantic validity.
     */
    private fun calculateParameterNullabilityValidity(
        program: IrProgram,
        function: IrFunctionDeclaration,
        containingClass: IrClassDeclaration,
        parameterIndex: Int,
    ): SemanticValidity {
        if (function.isOverrideStub) {
            return SemanticValidity.VALID
        }

        if (function.language == Language.KOTLIN &&
            !areEffectiveParameterOverridesCompatible(
                childFunction = function,
                childClass = containingClass,
                changedParent = null,
                parameterIndex = parameterIndex,
            )
        ) {
            return SemanticValidity.INVALID
        }

        for (clazz in program.classes) {
            for (candidate in clazz.functions) {
                if (candidate === function || candidate.language != Language.KOTLIN || candidate.isOverrideStub) {
                    continue
                }
                if (!areEffectiveParameterOverridesCompatible(
                        childFunction = candidate,
                        childClass = clazz,
                        changedParent = function,
                        parameterIndex = parameterIndex,
                    )
                ) {
                    return SemanticValidity.INVALID
                }
            }
        }
        return SemanticValidity.VALID
    }

    /**
     * Returns the declarations whose signatures are actually visible to this
     * override. Commented-out override stubs do not shield their parents, while
     * a real redeclaration does.
     */
    private fun IrFunctionDeclaration.effectiveOverrideParents(): Set<IrFunctionDeclaration> {
        val result = linkedSetOf<IrFunctionDeclaration>()
        val visited = mutableSetOf<IrFunctionDeclaration>()

        fun collect(parent: IrFunctionDeclaration) {
            if (!visited.add(parent)) return
            if (parent.isOverrideStub) {
                parent.override.forEach(::collect)
            } else {
                result += parent
            }
        }

        override.forEach(::collect)
        return result
    }

    /**
     * When [changedParent] is non-null, only validates the edge affected by that
     * mutation. A transitive ancestor hidden by a real redeclaration cannot
     * affect the child. A Java annotation conflict is also harmless when the
     * child simultaneously overrides a compatible Kotlin declaration.
     */
    private fun areEffectiveParameterOverridesCompatible(
        childFunction: IrFunctionDeclaration,
        childClass: IrClassDeclaration,
        changedParent: IrFunctionDeclaration?,
        parameterIndex: Int,
    ): Boolean {
        val effectiveParents = childFunction.effectiveOverrideParents()
        val parentsToCheck = if (changedParent == null) {
            effectiveParents
        } else {
            if (changedParent !in effectiveParents) return true
            setOf(changedParent)
        }
        val hasCompatibleKotlinParent = effectiveParents.any { parent ->
            parent.language == Language.KOTLIN &&
                    isParameterOverrideCompatible(
                        childFunction = childFunction,
                        childClass = childClass,
                        parentFunction = parent,
                        parameterIndex = parameterIndex,
                    )
        }

        return parentsToCheck.all { parent ->
            isParameterOverrideCompatible(
                childFunction = childFunction,
                childClass = childClass,
                parentFunction = parent,
                parameterIndex = parameterIndex,
            ) || (parent.language == Language.JAVA && hasCompatibleKotlinParent)
        }
    }

    private fun IrFunctionDeclaration.overridesTransitively(target: IrFunctionDeclaration): Boolean {
        val visited = mutableSetOf<IrFunctionDeclaration>()
        fun contains(function: IrFunctionDeclaration): Boolean {
            if (!visited.add(function)) return false
            return function.override.any { it === target || contains(it) }
        }
        return contains(this)
    }

    private fun isParameterOverrideCompatible(
        childFunction: IrFunctionDeclaration,
        childClass: IrClassDeclaration,
        parentFunction: IrFunctionDeclaration,
        parameterIndex: Int,
    ): Boolean {
        val childParameter = childFunction.parameterList.parameters.getOrNull(parameterIndex)
            ?: return false
        val parentParameter = parentFunction.parameterList.parameters.getOrNull(parameterIndex)
            ?: return false
        val childType = getActualTypeFromArguments(
            childParameter.type,
            childClass.allSuperTypeArguments,
            true,
        )
        val parentType = getActualTypeFromArguments(
            parentParameter.type,
            childClass.allSuperTypeArguments,
            true,
        )
        if (parentType is IrPlatformType) {
            return true
        }
        if (childType is IrPlatformType) {
            return false
        }
        return (childType is IrNullableType) == (parentType is IrNullableType)
    }

    fun mutateReturnTypeNullability(program: IrProgram): Pair<SemanticValidity, String?>? {
        var result: Pair<SemanticValidity, String?>? = null
        program.randomTraverseMemberFunctions(random) { func, clazz ->
            if (func.isOverrideStub) return@randomTraverseMemberFunctions false
            val returnType = func.returnType
            if (returnType === IrUnit || (returnType as? IrNullableType)?.innerType === IrUnit) {
                return@randomTraverseMemberFunctions false
            }
            if (returnType is IrNullableType) {
                func.returnType = returnType.innerType
            } else {
                func.returnType = buildNullableType {
                    this.innerType = returnType
                }
            }
            val desc = "mutateReturnTypeNullability at: ${clazz.name}.${func.name}, " +
                    "before: ${returnType.render()}, after: ${func.returnType.render()}"
            logger.trace { desc }
            val validity = if (languageSemantics == MutatorLanguageSemantics.GROOVY) {
                SemanticValidity.UNKNOWN
            } else {
                calculateReturnTypeNullabilityValidity(
                    program = program,
                    function = func,
                    containingClass = clazz,
                )
            }
            result = validity to desc
            return@randomTraverseMemberFunctions true
        }
        return result
    }

    /**
     * Calculates whether changing a member function's return type top-level nullability
     * keeps the declaration valid.
     *
     * In Kotlin and Java, return types in overriding methods are covariant:
     * a child method may return a subtype of the parent's return type (e.g., non-null
     * instead of nullable), but may not return a supertype (e.g., nullable instead of
     * non-null).
     */
    private fun calculateReturnTypeNullabilityValidity(
        program: IrProgram,
        function: IrFunctionDeclaration,
        containingClass: IrClassDeclaration,
    ): SemanticValidity {
        if (function.isOverrideStub) {
            return SemanticValidity.VALID
        }

        for (overriddenFunction in function.override) {
            if (!isReturnTypeOverrideCompatible(
                    childFunction = function,
                    childClass = containingClass,
                    parentFunction = overriddenFunction,
                )
            ) {
                return SemanticValidity.INVALID
            }
        }

        for (clazz in program.classes) {
            for (candidate in clazz.functions) {
                if (candidate === function || candidate.isOverrideStub) {
                    continue
                }
                if (candidate.overridesTransitively(function) &&
                    !isReturnTypeOverrideCompatible(
                        childFunction = candidate,
                        childClass = clazz,
                        parentFunction = function,
                    )
                ) {
                    return SemanticValidity.INVALID
                }
            }
        }
        return SemanticValidity.VALID
    }

    private fun isReturnTypeOverrideCompatible(
        childFunction: IrFunctionDeclaration,
        childClass: IrClassDeclaration,
        parentFunction: IrFunctionDeclaration,
    ): Boolean {
        if (childFunction.language == Language.JAVA) {
            return true
        }
        val childReturnType = getActualTypeFromArguments(
            childFunction.returnType,
            childClass.allSuperTypeArguments,
            true,
        )
        val parentReturnType = getActualTypeFromArguments(
            parentFunction.returnType,
            childClass.allSuperTypeArguments,
            true,
        )
        val parentIsPlatform = parentReturnType is IrPlatformType ||
                (parentFunction.language == Language.JAVA &&
                        (parentFunction.returnType as? IrTypeParameter)?.deepUpperboundNullable() == true)
        if (parentIsPlatform || childReturnType is IrPlatformType) {
            return true
        }
        return childReturnType.isSubTypeOf(parentReturnType)
    }

    fun mutateClassTypeParameterUpperBoundNullability(program: IrProgram): Pair<SemanticValidity, String?>? {
        var result: Pair<SemanticValidity, String?>? = null
        program.randomTraverseClasses(random) { clazz ->
            if (clazz.typeParameters.isNotEmpty()) {
                val typeParameter = clazz.typeParameters.random(random)
                val upperbound = typeParameter.upperbound
                val validity = if (upperbound is IrNullableType) {
                    typeParameter.upperbound = upperbound.innerType
                    if (languageSemantics == MutatorLanguageSemantics.GROOVY) {
                        SemanticValidity.UNKNOWN
                    } else {
                        calculateUpperBoundValidity(
                            program,
                            clazz,
                            typeParameter,
                            previousUpperBound = upperbound,
                            ignoreJavaNullabilityAnnotations = true,
                        )
                    }
                } else {
                    typeParameter.upperbound = buildNullableType { innerType = upperbound }
                    val validity = if (languageSemantics == MutatorLanguageSemantics.GROOVY) {
                        SemanticValidity.UNKNOWN
                    } else {
                        calculateUpperBoundValidity(
                            program,
                            clazz,
                            typeParameter,
                            previousUpperBound = upperbound,
                            ignoreJavaNullabilityAnnotations = true,
                            validateSelectedArgument = false,
                        )
                    }
                    validity
                }
                val desc = "mutateClassTypeParameterUpperBoundNullability in class ${clazz.name}, " +
                        "typeParam ${typeParameter.name}, before: ${upperbound.render()}, after: ${typeParameter.upperbound.render()}"
                logger.trace { desc }
                result = validity to desc
                return@randomTraverseClasses true
            }
            false
        }
        return result
    }

    /**
     * Calculates validity after changing one class type parameter's upper bound.
     *
     * Only occurrences of [targetClass] and the argument corresponding to
     * [targetTypeParameter] are relevant. Java nullability annotations are not
     * javac generic constraints, so nullability-only mutations ignore such
     * mismatches in Java source positions while still checking Kotlin positions.
     */
    private fun calculateUpperBoundValidity(
        program: IrProgram,
        targetClass: IrClassDeclaration,
        targetTypeParameter: IrTypeParameter,
        previousUpperBound: IrType,
        ignoreJavaNullabilityAnnotations: Boolean = false,
        validateSelectedArgument: Boolean = true,
    ): SemanticValidity {
        var result = SemanticValidity.VALID

        fun isTargetReference(typeParameter: IrTypeParameter): Boolean =
            typeParameter === targetTypeParameter ||
                    (typeParameter.name == targetTypeParameter.name &&
                            areEqualTypes(typeParameter.upperbound, previousUpperBound))

        fun dependsOnTarget(type: IrType?): Boolean = when (type) {
            null -> false
            is IrTypeParameter -> isTargetReference(type) ||
                    dependsOnTarget(type.upperbound)
            is IrTypeContainer -> dependsOnTarget(type.innerType)
            is IrParameterizedClassifier -> type.arguments.values.any { (_, argument) ->
                dependsOnTarget(argument.type)
            }
            else -> false
        }

        fun resolveDependentTypeParameter(type: IrType): IrType {
            var current = type
            val visited = mutableSetOf<IrTypeParameter>()
            while (current is IrTypeParameter &&
                !isTargetReference(current) &&
                dependsOnTarget(current) &&
                visited.add(current)
            ) {
                current = current.upperbound
            }
            return if (current is IrTypeParameter && isTargetReference(current)) {
                targetTypeParameter
            } else {
                current
            }
        }

        fun inspect(
            type: IrType?,
            ownerLanguage: Language,
            allowDependentReferences: Boolean,
        ) {
            when (type) {
                null -> Unit
                is IrParameterizedClassifier -> {
                    val concreteArguments = type.getConcreteTypeArguments()
                    for (declarationParameter in type.classDecl.typeParameters) {
                        val argument = type.arguments.entries.firstOrNull {
                            it.key.value == declarationParameter.name
                        }?.value?.second
                        val innerType = argument?.type
                        val selectedArgument = type.classDecl.name == targetClass.name &&
                                declarationParameter.name == targetTypeParameter.name
                        val declarationSelfArgument = allowDependentReferences &&
                                innerType is IrTypeParameter &&
                                innerType.name == declarationParameter.name &&
                                areEqualTypes(innerType.upperbound, declarationParameter.upperbound)
                        if (selectedArgument) {
                            if (!validateSelectedArgument) continue
                        } else if (declarationSelfArgument) {
                            continue
                        } else if (!allowDependentReferences || !dependsOnTarget(innerType)) continue
                        if (argument == null || argument.isStar ||
                            (ignoreJavaNullabilityAnnotations && ownerLanguage == Language.JAVA)
                        ) continue
                        if (innerType == null) continue
                        val declaredUpperBound = if (selectedArgument) {
                            targetTypeParameter.upperbound
                        } else {
                            declarationParameter.upperbound
                        }
                        if (ownerLanguage == Language.JAVA && argument.isInvariant) {
                            val dependsOnCovariantOrStar = type.classDecl.typeParameters.any { otherParam ->
                                otherParam !== declarationParameter &&
                                        declaredUpperBound.dependsOnTypeParameter(otherParam) &&
                                        type.arguments.entries.any {
                                            it.key.value == otherParam.name &&
                                                    (it.value.second.isCovariant || it.value.second.isStar)
                                        }
                            }
                            if (dependsOnCovariantOrStar) {
                                result = SemanticValidity.INVALID
                            }
                        }
                        val actualUpperBound = getActualTypeFromArguments(
                            declaredUpperBound,
                            concreteArguments,
                            true,
                        )
                        val substitutedInnerType = getActualTypeFromArguments(
                            innerType,
                            mapOf(
                                IrTypeParameterName(targetTypeParameter.name) to
                                        (targetTypeParameter to targetTypeParameter),
                            ),
                            true,
                        )
                        val effectiveInnerType = if (!selectedArgument) {
                            resolveDependentTypeParameter(substitutedInnerType)
                        } else {
                            substitutedInnerType
                        }
                        val javaAnnotationOnlyMismatch = ownerLanguage == Language.JAVA &&
                                effectiveInnerType.notNullType.isSubTypeOf(actualUpperBound.notNullType)
                        if (!effectiveInnerType.isSubTypeOf(actualUpperBound) &&
                            !javaAnnotationOnlyMismatch
                        ) {
                            result = SemanticValidity.INVALID
                        }
                    }
                    type.arguments.values.forEach { (_, argument) ->
                        inspect(argument.type, ownerLanguage, allowDependentReferences)
                    }
                }
                is IrTypeContainer -> inspect(type.innerType, ownerLanguage, allowDependentReferences)
                is IrTypeParameter -> inspect(type.upperbound, ownerLanguage, allowDependentReferences)
            }
        }

        fun inspectClass(clazz: IrClassDeclaration) {
            val ownerLanguage = clazz.language
            val allowDependentReferences = clazz.name == targetClass.name
            inspect(clazz.superType, ownerLanguage, allowDependentReferences)
            clazz.implementedTypes.forEach { inspect(it, ownerLanguage, allowDependentReferences) }
            clazz.typeParameters.forEach {
                inspect(it.upperbound, ownerLanguage, allowDependentReferences)
            }
            clazz.functions.forEach { function ->
                if (function.isOverrideStub) return@forEach
                inspect(function.returnType, ownerLanguage, allowDependentReferences)
                function.parameterList.parameters.forEach {
                    inspect(it.type, ownerLanguage, allowDependentReferences)
                }
                function.typeParameters.forEach {
                    inspect(it.upperbound, ownerLanguage, allowDependentReferences)
                }
            }
        }

        program.classes.forEach(::inspectClass)
        program.functions.forEach { function ->
            if (function.isOverrideStub) return@forEach
            inspect(function.returnType, function.language, false)
            function.parameterList.parameters.forEach { inspect(it.type, function.language, false) }
            function.typeParameters.forEach { inspect(it.upperbound, function.language, false) }
        }
        return result
    }

    fun mutateClassTypeParameterUpperBound(program: IrProgram): Pair<SemanticValidity, String?>? {
        var result: Pair<SemanticValidity, String?>? = null
        program.randomTraverseClasses(random) { clazz ->
            if (clazz.typeParameters.isNotEmpty()) {
                val typeParameter = clazz.typeParameters.random(random)
                val oldUpperBound = typeParameter.upperbound
                val oldInnerBound = if (oldUpperBound is IrNullableType) {
                    oldUpperBound.innerType
                } else {
                    oldUpperBound
                }
                val visibleClasses = program.visibleClassesFor(clazz)
                val replace = generator.randomType(
                    visibleClasses, clazz.typeParameters, false
                ) { type ->
                    type !is IrParameterizedClassifier && !areEqualTypes(type, typeParameter)
                            && !areEqualTypes(type, oldInnerBound)
                            && !type.dependsOnTypeParameter(typeParameter)
                            && (generator.config.allowUnitInTypeArgument || type !== IrUnit)
                            && (generator.config.allowNothingInTypeArgument || type !== IrNothing)
                } ?: return@randomTraverseClasses false
                typeParameter.upperbound = if (oldUpperBound is IrNullableType) {
                    buildNullableType { innerType = replace }
                } else {
                    replace
                }
                val desc = "mutateClassTypeParameterUpperBound in class ${clazz.name}, " +
                        "typeParam ${typeParameter.name}, before: ${oldUpperBound.render()}, after: ${typeParameter.upperbound.render()}"
                logger.trace { desc }
                val validity = calculateUpperBoundValidity(
                    program,
                    clazz,
                    typeParameter,
                    previousUpperBound = oldUpperBound,
                )
                result = validity to desc
                return@randomTraverseClasses true
            }
            return@randomTraverseClasses false
        }
        return result
    }

    private fun hasMissingRequiredOverrideRelatedTo(
        program: IrProgram,
        targetFunction: IrFunctionDeclaration,
    ): Boolean {
        fun isRelated(function: IrFunctionDeclaration): Boolean =
            function === targetFunction ||
                    function.overridesTransitively(targetFunction)

        return program.classes.any { clazz ->
            val (mustOverrides, _, _) = clazz.getOverrideCandidates(
                clazz.collectFunctionSignatureMap(exactSignatures = true),
                doNotEditLanguage = true,
            )
            mustOverrides.any { candidate ->
                val (superFunction, interfaceFunctions) = candidate
                val related = superFunction?.let(::isRelated) == true || interfaceFunctions.any(::isRelated)
                if (!related || clazz.functions.any { it.implements(candidate) }) {
                    return@any false
                }
                if (clazz.classKind == ClassKind.FINAL || clazz.classKind == ClassKind.OPEN) {
                    return@any true
                }
                superFunction == null && interfaceFunctions.count { it.body != null } > 1
            }
        }
    }

    fun mutateInterfaceMethodDefault(program: IrProgram): Pair<SemanticValidity, String?>? {
        val candidates = mutableListOf<Pair<IrClassDeclaration, IrFunctionDeclaration>>()
        for (clazz in program.classes) {
            if (clazz.classKind != ClassKind.INTERFACE) continue
            for (func in clazz.functions) {
                if (func.isOverrideStub) continue
                candidates.add(clazz to func)
            }
        }
        if (candidates.isEmpty()) return null
        val (targetClass, targetFunc) = candidates.random(random)
        val oldBody = targetFunc.body
        if (oldBody != null) {
            targetFunc.body = null
        } else {
            targetFunc.body = buildBlock()
        }
        val desc = "mutateInterfaceMethodDefault in interface ${targetClass.name}, " +
                "function ${targetFunc.name}: ${if (oldBody != null) "default -> abstract" else "abstract -> default"}"
        logger.trace { desc }
        val validity = if (hasMissingRequiredOverrideRelatedTo(program, targetFunc)) {
            SemanticValidity.INVALID
        } else {
            SemanticValidity.VALID
        }
        return validity to desc
    }

    private data class OperatorDescriptor(
        val mutate: (IrProgram) -> Pair<SemanticValidity, String?>?,
        val weight: (MutatorConfig) -> Int,
        val supportedSemantics: Set<MutatorLanguageSemantics>,
    )

    private val operatorDescriptors = listOf(
        OperatorDescriptor(
            this::mutateGenericArgumentInParent,
            { it.mutateGenericArgumentInParentWeight },
            setOf(MutatorLanguageSemantics.KOTLIN),
        ),
        OperatorDescriptor(
            this::removeOverrideMemberFunction,
            { it.removeOverrideMemberFunctionWeight },
            setOf(MutatorLanguageSemantics.KOTLIN),
        ),
        OperatorDescriptor(
            this::mutateGenericArgumentInMemberFunctionParameter,
            { it.mutateGenericArgumentInMemberFunctionParameterWeight },
            setOf(MutatorLanguageSemantics.KOTLIN, MutatorLanguageSemantics.SCALA),
        ),
        OperatorDescriptor(
            this::mutateParameterNullability,
            { it.mutateParameterNullabilityWeight },
            setOf(MutatorLanguageSemantics.KOTLIN, MutatorLanguageSemantics.GROOVY),
        ),
        OperatorDescriptor(
            this::mutateReturnTypeNullability,
            { it.mutateReturnTypeNullabilityWeight },
            setOf(MutatorLanguageSemantics.KOTLIN, MutatorLanguageSemantics.GROOVY),
        ),
        OperatorDescriptor(
            this::mutateClassTypeParameterUpperBoundNullability,
            { it.mutateClassTypeParameterUpperBoundNullabilityWeight },
            setOf(MutatorLanguageSemantics.KOTLIN, MutatorLanguageSemantics.GROOVY),
        ),
        OperatorDescriptor(
            this::mutateClassTypeParameterUpperBound,
            { it.mutateClassTypeParameterUpperBoundWeight },
            setOf(MutatorLanguageSemantics.KOTLIN),
        ),
        OperatorDescriptor(
            this::mutateInterfaceMethodDefault,
            { it.mutateInterfaceMethodDefaultWeight },
            setOf(MutatorLanguageSemantics.KOTLIN),
        ),
    )

    private val enabledOperators = operatorDescriptors.mapNotNull { descriptor ->
        val weight = descriptor.weight(config)
        if (languageSemantics in descriptor.supportedSemantics && weight > 0) {
            descriptor.mutate to weight
        } else {
            null
        }
    }
    private val enabledMutations = enabledOperators.map { it.first }
    private val enabledWeights = enabledOperators.map { it.second }

    /**
     * @return a pair of the calculated semantic validity and description when a mutation was applied, or
     * `null` when the selected operator could not mutate this program.
     */
    fun mutate(program: IrProgram): Pair<SemanticValidity, String?>? {
        if (enabledMutations.isEmpty()) return null
        val mutatorMethod = rouletteSelection(enabledMutations, enabledWeights, random)
        return mutatorMethod(program)
    }

    private fun IrProgram.visibleClassesFor(clazz: IrClassDeclaration): List<IrClassDeclaration> {
        val targetModule = modules.firstOrNull { clazz in it.classes } ?: return classes
        return (targetModule.dependencies + targetModule).flatMap { it.classes }
    }
}

private fun IrType.parameterizedClassifierOrNull(): IrParameterizedClassifier? = when (this) {
    is IrParameterizedClassifier -> this
    is IrTypeContainer -> innerType.parameterizedClassifierOrNull()
    else -> null
}
