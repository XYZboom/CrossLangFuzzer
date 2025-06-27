package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.getTypeArguments
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.utils.rouletteSelection
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
                        program.classes, clazz.typeParameters, null, false
                    ) { type ->
                        type !is IrParameterizedClassifier && type != typeArg
                    } ?: continue
                    logger.trace {
                        "mutateGenericArgumentInParent at: ${clazz.name}, change: $typeArg into $replaceArg"
                    }
                    impl.putTypeArgument(typeParam, replaceArg)
                    return@mutateGenericArgumentInParent true
                }
            }
            false
        }
        return false
    }

    @ConfigBy("removeOverrideMemberFunctionWeight")
    fun removeOverrideMemberFunction(program: IrProgram): Boolean {
        program.randomTraverseMemberFunctions(random) { func, _ ->
            if (func.isOverride && !func.isOverrideStub) {
                func.replaceIsOverrideStub(true)
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
                    program.classes, clazz.typeParameters, func.typeParameters, false
                ) { type ->
                    type !is IrParameterizedClassifier && type != typeArg
                } ?: return@randomTraverseMemberFunctions false
                paramType.putTypeArgument(typeParam, replaceArg)
                logger.trace {
                    "mutateGenericArgumentInMemberFunctionParameter at: " +
                            "${clazz.name}:${func.name}, " +
                            "change: $typeArg into $replaceArg, new param $param"
                }
                return@mutateGenericArgumentInMemberFunctionParameter true
            }
            false
        }
        return false
    }

    @ConfigBy("mutateParameterNullabilityWeight")
    fun mutateParameterNullability(program: IrProgram): Boolean {
        program.randomTraverseMemberFunctions(random) { func, _ ->
            val param = func.parameterList.parameters.firstOrNull {
                if (random.nextBoolean()) {
                    it.type is IrNullableType
                } else {
                    it.type !is IrNullableType
                }
            } ?: return@randomTraverseMemberFunctions false
            val type = param.type
            if (type is IrNullableType) {
                param.replaceType(type.innerType)
            } else {
                param.replaceType(buildNullableType {
                    this.innerType = type
                })
            }
            if (func.language == Language.JAVA) {
                func.replacePrintNullableAnnotations(true)
            }
            return@mutateParameterNullability true
        }
        return false
    }

    fun mutate(program: IrProgram): Boolean {
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
        return mutatorMethod.call(this, program) as Boolean
    }
}