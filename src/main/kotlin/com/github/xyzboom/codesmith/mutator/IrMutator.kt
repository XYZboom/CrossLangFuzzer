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
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeContainer
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.areEqualTypes
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.types.containsTypeParameter
import com.github.xyzboom.codesmith.ir.types.dependsOnTypeParameter
import com.github.xyzboom.codesmith.ir.types.getTypeArguments
import com.github.xyzboom.codesmith.ir.types.getActualTypeFromArguments
import com.github.xyzboom.codesmith.ir.types.isSubClassOf
import com.github.xyzboom.codesmith.ir.types.isSubTypeOf
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.render
import com.github.xyzboom.codesmith.utils.rouletteSelection
import com.github.xyzboom.codesmith.validator.isRequiredOverride
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.random.Random
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberProperties

class IrMutator(
    private val config: MutatorConfig = MutatorConfig.default,
    private val generator: IrDeclGenerator,
) {
    private val logger = KotlinLogging.logger {}
    private val random: Random = generator.random
    private var semanticValidity = SemanticValidity.UNKNOWN

    @ConfigBy("mutateGenericArgumentInParentWeight")
    fun mutateGenericArgumentInParent(program: IrProgram): Boolean {
        program.randomTraverseClasses(random) { clazz ->
            val implements = clazz.implementedTypes
            for (impl in implements) {
                if (impl is IrParameterizedClassifier) {
                    val typeArguments = impl.getTypeArguments()
                    val entries = typeArguments.entries.filter { it.value.second !== IrAny }
                    if (entries.isEmpty()) continue
                    val (_, pair) = entries.random(random)
                    val (typeParam, typeArg) = pair
                    val replaceArg = generator.randomType(
                        program.classes, clazz.typeParameters, false
                    ) { type ->
                        type !is IrParameterizedClassifier && !areEqualTypes(type, typeArg)
                    } ?: continue
                    logger.trace {
                        "mutateGenericArgumentInParent at: ${clazz.name}, change: $typeArg into $replaceArg"
                    }
                    impl.putTypeArgument(typeParam, replaceArg)
                    semanticValidity = calculateParentGenericArgumentValidity(
                        program = program,
                        targetClass = clazz,
                        parentType = impl,
                        changedTypeParameter = typeParam,
                    )
                    return@mutateGenericArgumentInParent true
                }
            }
            false
        }
        return false
    }

    /**
     * Calculates validity after replacing one argument in an implemented
     * parameterized interface.
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
        for ((_, pair) in typeArguments) {
            val (typeParameter, typeArgument) = pair
            val actualUpperBound = getActualTypeFromArguments(
                typeParameter.upperbound,
                typeArguments,
                true,
            )
            if (!typeArgument.isSubTypeOf(actualUpperBound)) {
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

        val affectedNames = affectedParentFunctions.map { it.name }.toSet()
        if (targetClass.functions.any { it.isOverride && !it.isOverrideStub && it.name in affectedNames }) {
            return SemanticValidity.INVALID
        }
        if (targetClass.classKind == ClassKind.FINAL || targetClass.classKind == ClassKind.OPEN) {
            return SemanticValidity.INVALID
        }

        for (subClass in program.classes) {
            if (subClass === targetClass || !subClass.isSubClassOf(targetClass)) continue
            if (subClass.functions.any { it.isOverride && !it.isOverrideStub && it.name in affectedNames }) {
                return SemanticValidity.INVALID
            }
            if (subClass.classKind == ClassKind.FINAL || subClass.classKind == ClassKind.OPEN) {
                return SemanticValidity.INVALID
            }
        }

        return SemanticValidity.VALID
    }

    @ConfigBy("removeOverrideMemberFunctionWeight")
    fun removeOverrideMemberFunction(program: IrProgram): Boolean {
        program.randomTraverseMemberFunctions(random) { func, clazz ->
            if (func.isOverride && !func.isOverrideStub) {
                func.isOverrideStub = true
                semanticValidity = if (program.isRequiredOverride(clazz, func)) {
                    SemanticValidity.INVALID
                } else {
                    SemanticValidity.VALID
                }
                return@removeOverrideMemberFunction true
            }
            false
        }
        return false
    }

    @ConfigBy("mutateGenericArgumentInMemberFunctionParameterWeight")
    fun mutateGenericArgumentInMemberFunctionParameter(program: IrProgram): Boolean {
        program.randomTraverseMemberFunctions(random) { func, clazz ->
            val param = func.parameterList.parameters.firstOrNull { it.type is IrParameterizedClassifier }
            if (func.isOverride && !func.isOverrideStub && param != null) {
                val paramType = param.type as IrParameterizedClassifier
                val typeArguments = paramType.getTypeArguments()
                val entries = typeArguments.entries.filter { it.value.second !== IrAny }
                if (entries.isEmpty()) return@randomTraverseMemberFunctions false
                val (_, pair) = entries.random(random)
                val (typeParam, typeArg) = pair
                val replaceArg = generator.randomType(
                    program.classes, clazz.typeParameters + func.typeParameters, false
                ) { type ->
                    type !is IrParameterizedClassifier && !areEqualTypes(type, typeArg)
                } ?: return@randomTraverseMemberFunctions false
                paramType.putTypeArgument(typeParam, replaceArg)
                logger.trace {
                    "mutateGenericArgumentInMemberFunctionParameter at: " +
                            "${clazz.name}:${func.name}, " +
                            "change: $typeArg into $replaceArg, new param $param"
                }
                /**
                 * The operator only mutates a non-stub override. Replacing an
                 * invariant parameterized argument changes the source-level
                 * parameter type while preserving only its JVM erasure. The
                 * declaration therefore no longer overrides its recorded parent
                 * method (Java reports a name clash; Kotlin reports that there is
                 * nothing to override).
                 */
                semanticValidity = SemanticValidity.INVALID
                return@mutateGenericArgumentInMemberFunctionParameter true
            }
            false
        }
        return false
    }

    @ConfigBy("mutateParameterNullabilityWeight")
    fun mutateParameterNullability(program: IrProgram): Boolean {
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
            if (func.language == Language.JAVA) {
                func.printNullableAnnotations = true
            }
            semanticValidity = calculateParameterNullabilityValidity(
                program = program,
                function = func,
                containingClass = clazz,
                parameterIndex = func.parameterList.parameters.indexOf(param),
            )
            return@mutateParameterNullability true
        }
        return false
    }

    /**
     * Calculates whether changing one parameter's top-level nullability keeps
     * the declaration valid.
     *
     * A declaration with no upstream or downstream overrides remains valid because
     * nullability does not change its JVM signature. Java and Scala declarations
     * are unaffected as overriding declarations, but changing them can still
     * invalidate a Kotlin override downstream. For every Kotlin override edge,
     * the child and every non-platform parent parameter must have the same
     * top-level nullability after inherited type arguments are substituted. An
     * unannotated Java parameter is a platform type and accepts either Kotlin
     * nullability.
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

        if (function.language == Language.KOTLIN) {
            for (overriddenFunction in function.override) {
                if (!isParameterOverrideCompatible(
                        childFunction = function,
                        childClass = containingClass,
                        parentFunction = overriddenFunction,
                        parameterIndex = parameterIndex,
                    )
                ) {
                    return SemanticValidity.INVALID
                }
            }
        }

        for (clazz in program.classes) {
            for (candidate in clazz.functions) {
                if (candidate === function || candidate.language != Language.KOTLIN || candidate.isOverrideStub) {
                    continue
                }
                if (candidate.overridesTransitively(function) &&
                    !isParameterOverrideCompatible(
                        childFunction = candidate,
                        childClass = clazz,
                        parentFunction = function,
                        parameterIndex = parameterIndex,
                    )
                ) {
                    return SemanticValidity.INVALID
                }
            }
        }
        return SemanticValidity.VALID
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
        if (parentFunction.language == Language.JAVA && !parentFunction.printNullableAnnotations) {
            return true
        }
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

    @ConfigBy("mutateReturnTypeNullabilityWeight")
    fun mutateReturnTypeNullability(program: IrProgram): Boolean {
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
            if (func.language == Language.JAVA) {
                func.printNullableAnnotations = true
            }
            semanticValidity = calculateReturnTypeNullabilityValidity(
                program = program,
                function = func,
                containingClass = clazz,
            )
            return@mutateReturnTypeNullability true
        }
        return false
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
        if (parentFunction.language == Language.JAVA && !parentFunction.printNullableAnnotations) {
            return true
        }
        if (childFunction.language == Language.JAVA && !childFunction.printNullableAnnotations) {
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
        if (parentReturnType is IrPlatformType) {
            return true
        }
        if (childReturnType is IrPlatformType) {
            return true
        }
        if (childReturnType is IrNullableType && parentReturnType !is IrNullableType) {
            return false
        }
        return true
    }

    @ConfigBy("mutateClassTypeParameterUpperBoundNullabilityWeight")
    fun mutateClassTypeParameterUpperBoundNullability(program: IrProgram): Boolean {
        var applied = false
        program.randomTraverseClasses(random) { clazz ->
            if (clazz.typeParameters.isNotEmpty()) {
                val typeParameter = clazz.typeParameters.random(random)
                val upperbound = typeParameter.upperbound
                if (upperbound is IrNullableType) {
                    typeParameter.upperbound = upperbound.innerType
                    semanticValidity = calculateUpperBoundValidity(program, clazz, typeParameter)
                } else {
                    typeParameter.upperbound = buildNullableType { innerType = upperbound }
                    // U -> U? relaxes the bound. Every type argument accepted before
                    // the mutation remains accepted in the current body-free IR model.
                    semanticValidity = SemanticValidity.VALID
                }
                applied = true
                return@randomTraverseClasses true
            }
            false
        }
        return applied
    }

    /**
     * Calculates validity after changing a class type parameter's upper bound.
     *
     * Every occurrence of `Target<Argument>` must satisfy `Argument <: U`.
     * Platform and nested parameterized arguments are handled by the shared
     * subtype checker in the IR type utilities.
     */
    private fun calculateUpperBoundValidity(
        program: IrProgram,
        targetClass: IrClassDeclaration,
        targetTypeParameter: IrTypeParameter,
    ): SemanticValidity {
        var result = SemanticValidity.VALID

        fun inspect(type: IrType?) {
            when (type) {
                null -> Unit
                is IrParameterizedClassifier -> {
                    if (type.classDecl === targetClass) {
                        val argument = type.getTypeArguments().values
                            .firstOrNull { (parameter, _) -> parameter === targetTypeParameter }
                            ?.second
                        if (argument == null || !argument.isSubTypeOf(targetTypeParameter.upperbound)) {
                            result = SemanticValidity.INVALID
                        }
                    }
                    type.arguments.values.forEach { (_, argument) -> inspect(argument) }
                }
                is IrTypeContainer -> inspect(type.innerType)
                is IrTypeParameter -> inspect(type.upperbound)
            }
        }

        fun inspectClass(clazz: IrClassDeclaration) {
            inspect(clazz.superType)
            clazz.implementedTypes.forEach(::inspect)
            clazz.allSuperTypeArguments.values.forEach { (_, type) -> inspect(type) }
            clazz.typeParameters.forEach { inspect(it.upperbound) }
            clazz.functions.forEach { function ->
                inspect(function.returnType)
                function.parameterList.parameters.forEach { inspect(it.type) }
                function.typeParameters.forEach { inspect(it.upperbound) }
            }
        }

        program.classes.forEach(::inspectClass)
        program.functions.forEach { function ->
            inspect(function.returnType)
            function.parameterList.parameters.forEach { inspect(it.type) }
            function.typeParameters.forEach { inspect(it.upperbound) }
        }
        return result
    }

    @ConfigBy("mutateClassTypeParameterUpperBoundWeight")
    fun mutateClassTypeParameterUpperBound(program: IrProgram): Boolean {
        program.randomTraverseClasses(random) { clazz ->
            if (clazz.typeParameters.isNotEmpty()) {
                val typeParameter = clazz.typeParameters.random(random)
                val oldUpperBound = typeParameter.upperbound
                val oldInnerBound = if (oldUpperBound is IrNullableType) {
                    oldUpperBound.innerType
                } else {
                    oldUpperBound
                }
                val replace = generator.randomType(
                    program.classes, clazz.typeParameters, false
                ) { type ->
                    type !is IrParameterizedClassifier && !areEqualTypes(type, typeParameter)
                            && !areEqualTypes(type, oldInnerBound)
                            && !type.dependsOnTypeParameter(typeParameter)
                            && (generator.config.allowUnitInTypeArgument || type !== IrUnit)
                            && (generator.config.allowNothingInTypeArgument || type !== IrNothing)
                } ?: return@mutateClassTypeParameterUpperBound false
                typeParameter.upperbound = if (oldUpperBound is IrNullableType) {
                    buildNullableType { innerType = replace }
                } else {
                    replace
                }
                logger.trace { "mutateClassTypeParameterUpperBound in class ${clazz.name}," +
                        " before: ${oldUpperBound.render()}, after: ${typeParameter.upperbound.render()}" }
                semanticValidity = calculateUpperBoundValidity(program, clazz, typeParameter)
                return@mutateClassTypeParameterUpperBound true
            }
            return@randomTraverseClasses false
        }
        return false
    }

    /**
     * @return the calculated semantic validity when a mutation was applied, or
     * `null` when the selected operator could not mutate this program.
     */
    fun mutate(program: IrProgram): SemanticValidity? {
        semanticValidity = SemanticValidity.UNKNOWN
        val configByMethods = this::class.declaredMemberFunctions.filter {
            it.annotations.any { anno ->
                anno.annotationClass == ConfigBy::class
            }
        }
        val weights = configByMethods.map { method ->
            val anno = method.annotations.single { it.annotationClass == ConfigBy::class } as ConfigBy
            val configProperty = MutatorConfig::class.memberProperties.single { it.name == anno.name }
            configProperty.get(config) as Int
        }
        val mutatorMethod = rouletteSelection(configByMethods, weights, random)
        val applied = mutatorMethod.call(this, program) as Boolean
        return if (applied) semanticValidity else null
    }
}
