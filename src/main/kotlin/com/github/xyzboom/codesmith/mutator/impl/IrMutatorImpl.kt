package com.github.xyzboom.codesmith.mutator.impl

import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.mutator.*
import com.github.xyzboom.codesmith.utils.rouletteSelection
import kotlin.random.Random
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberProperties

class IrMutatorImpl(
    private val config: MutatorConfig = MutatorConfig.default,
    private val generator: IrDeclGeneratorImpl,
) : IrMutator() {
    private val random: Random = generator.random

    @ConfigBy("mutateGenericArgumentInParentWeight")
    fun mutateGenericArgumentInParent(program: IrProgram): Boolean {
        program.randomTraverseClasses(random) { clazz ->
            val implements = clazz.implementedTypes
            for (impl in implements) {
                if (impl is IrParameterizedClassifier) {
                    val typeArguments = impl.getTypeArguments()
                    val entries = typeArguments.entries.filter { it.value !== IrAny }
                    if (entries.isEmpty()) continue
                    val (typeParam, typeArg) = entries.random(random)
                    val replaceArg = generator.randomType(
                        program, clazz, null, false
                    ) { type ->
                        type !is IrParameterizedClassifier && type != typeArg
                    } ?: continue
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
        program.randomTraverseMemberFunctions(random) { func ->
            if (func.isOverride && !func.isOverrideStub) {
                func.isOverrideStub = true
                return@removeOverrideMemberFunction true
            }
            false
        }
        return false
    }

    @ConfigBy("mutateGenericArgumentInMemberFunctionParameterWeight")
    fun mutateGenericArgumentInMemberFunctionParameter(program: IrProgram): Boolean {
        program.randomTraverseMemberFunctions(random) { func ->
            val param = func.parameterList.parameters.firstOrNull { it.type is IrParameterizedClassifier }
            if (func.isOverride && !func.isOverrideStub && param != null) {
                val paramType = param.type as IrParameterizedClassifier
                val typeArguments = paramType.getTypeArguments()
                val entries = typeArguments.entries.filter { it.value !== IrAny }
                if (entries.isEmpty()) return@randomTraverseMemberFunctions false
                val (typeParam, typeArg) = entries.random(random)
                val replaceArg = generator.randomType(
                    program, func.container as IrClassDeclaration, func, false
                ) { type ->
                    type !is IrParameterizedClassifier && type != typeArg
                } ?: return@randomTraverseMemberFunctions false
                paramType.putTypeArgument(typeParam, replaceArg)
                return@mutateGenericArgumentInMemberFunctionParameter true
            }
            false
        }
        return false
    }

    override fun mutate(program: IrProgram): Boolean {
        val configByMethods = IrMutatorImpl::class.declaredMemberFunctions.filter {
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