package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.SuperAndIntfFunctions
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.declarations.traverseSuper
import com.github.xyzboom.codesmith.ir.topologicalOrderedClasses
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builder.buildDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.minimize.MinimizeRunner2.Companion.superTypeOf
import com.github.xyzboom.codesmith.validator.collectFunctionSignatureMap
import com.github.xyzboom.codesmith.validator.getOverrideCandidates
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class Closure2Program(
    val program: IrProgram
) {
    val old2NewFunctions = mutableMapOf<IrFunctionDeclaration, IrFunctionDeclaration>()
    val new2OldFunctions = mutableMapOf<IrFunctionDeclaration, IrFunctionDeclaration>()
    val old2NewClasses = mutableMapOf<IrClassDeclaration, IrClassDeclaration>()
    val new2OldClasses = mutableMapOf<IrClassDeclaration, IrClassDeclaration>()

    fun newFunctionFromClosure(
        oriFunc: IrFunctionDeclaration,
        elements: Set<IrElement>
    ): IrFunctionDeclaration {
        return buildFunctionDeclaration {
            name = oriFunc.name
            language = oriFunc.language
            val typeParameters = oriFunc.typeParameters.mapNotNull { typeParameter ->
                val (newType, oriExists) = newTypeFromClosure(typeParameter, elements)
                if (newType !is IrTypeParameter) return@mapNotNull null
                newType.takeIf { oriExists }
            }
            this.typeParameters += typeParameters
            body = oriFunc.body
            isFinal = oriFunc.isFinal
            parameterList = buildParameterList {
                // todo remove parameter if not exists
                val parameters = oriFunc.parameterList.parameters.mapNotNull { parameter ->
                    // todo ori may not exists
                    val (type, _) = newTypeFromClosure(parameter.type, elements)
                    buildParameter {
                        name = parameter.name
                        this.type = type
                    }
                }
                this.parameters.addAll(parameters)
            }
            // todo ori may not exists
            val (returnType, _) = newTypeFromClosure(oriFunc.returnType, elements)
            this.returnType = returnType
            containingClassName = oriFunc.containingClassName
        }
    }

    /**
     * Make new type from closure.
     * @return A pair whose first is the new type and
     *         whose second is false if [oriType] is not in closure.
     */
    fun newTypeFromClosure(
        oriType: IrType,
        elements: Set<IrElement>,
    ): Pair<IrType, Boolean> {
        return when (oriType) {
            is IrBuiltInType -> oriType to true
            is IrNullableType -> {
                val (newType, oriExists) = newTypeFromClosure(oriType.innerType, elements)
                buildNullableType {
                    innerType = newType
                } to oriExists
            }

            is IrPlatformType -> {
                val (newType, oriExists) = newTypeFromClosure(oriType.innerType, elements)
                buildPlatformType {
                    innerType = newType
                } to oriExists
            }

            is IrDefinitelyNotNullType -> {
                val (newType, oriExists) = newTypeFromClosure(oriType.innerType, elements)
                if (newType is IrTypeParameter) {
                    buildDefinitelyNotNullType { innerType = newType } to oriExists
                } else newType to oriExists
            }

            is IrClassifier -> {
                val oriClass = oriType.classDecl
                if (oriClass !in elements) {
                    return IrAny to false // todo replace directly may cause new error
                }
                val newClass = old2NewClasses[oriClass]!!
                val newType = newClass.type
                if (oriType is IrParameterizedClassifier) {
                    for ((typeParamName, pair) in oriType.arguments) {
                        if (newType is IrParameterizedClassifier) {
                            val (typeParam, typeArg) = pair
                            val (newTypeParam, oriTPExists) = newTypeFromClosure(typeParam, elements)
                            if (!oriTPExists || newTypeParam !is IrTypeParameter) continue
                            val newTypeArg = if (typeArg != null) {
                                val (newTypeArg, oriTAExists) = newTypeFromClosure(typeArg, elements)
                                if (oriTAExists) {
                                    newTypeArg
                                } else {
                                    newTypeParam.upperbound
                                }
                            } else null
                            newType.arguments[typeParamName] = newTypeParam to newTypeArg
                        }
                    }
                }
                newType to true
            }

            is IrTypeParameter -> {
                val newType = buildTypeParameter {
                    name = oriType.name
                    val (newUpperbound, _) = newTypeFromClosure(oriType.upperbound, elements)
                    upperbound = newUpperbound
                }
                newType to true
            }

            else -> throw IllegalArgumentException("No such IrType ${this::class.simpleName}")
        }
    }

    fun firstStageNewClassFromClosure(
        oriClass: IrClassDeclaration,
        newClass: IrClassDeclaration,
        elements: Set<IrElement>
    ) {
        val oriSuper = oriClass.superType
        var newSuper: IrType? = null
        val intfs = mutableMapOf<IrClassDeclaration, IrType>()

        fun IrType.toNew(): Pair<IrType, Boolean> {
            return newTypeFromClosure(
                this,
                elements
            )
        }

        //<editor-fold desc="HandleSuper">
        if (oriSuper !is IrClassifier) {
            val newPair = oriSuper?.toNew()
            if (newPair != null && newPair.second) {
                newSuper = newPair.first
            }
        } else {
            val superClass = oriSuper.classDecl
            val superTypeOf = superTypeOf(oriClass, superClass)
            if (superTypeOf in elements) {
                val (newType, oriExists) = oriSuper.toNew()
                if (oriExists) {
                    newSuper = newType
                }
            } else {
                superClass.traverseSuper {
                    if (it !is IrClassifier || it.classDecl !in elements) {
                        return@traverseSuper true
                    }
                    val superSuperClass = it.classDecl
                    val superTypeOf = superTypeOf(oriClass, superSuperClass)
                    if (superTypeOf in elements) {
                        if (newSuper == null && superSuperClass.classKind != ClassKind.INTERFACE) {
                            val (newType, oriExists) = it.toNew()
                            if (oriExists) {
                                newSuper = newType
                            }
                        }
                        if (superSuperClass.classKind == ClassKind.INTERFACE
                            && intfs[superSuperClass] == null
                        ) {
                            val (newType, oriExists) = it.toNew()
                            if (oriExists) {
                                intfs[superSuperClass] = newType
                            }
                        }
                    }
                    true
                }
            }
        }
        //</editor-fold>

        for (intf in oriClass.implementedTypes) {
            if (intf !is IrClassifier || intf.classDecl !in elements) {
                continue
            }
            val superClass = intf.classDecl
            val superTypeOf = superTypeOf(oriClass, superClass)
            if (superTypeOf in elements) {
                val (newType, oriExists) = intf.toNew()
                if (oriExists) {
                    intfs[superClass] = newType
                }
            } else {
                superClass.traverseSuper {
                    if (it.classKind == ClassKind.INTERFACE && it is IrClassifier
                        && intfs[it.classDecl] == null
                    ) {
                        val (newType, oriExists) = intf.toNew()
                        if (oriExists) {
                            intfs[it.classDecl] = newType
                        }
                    }
                    true
                }
            }
        }

        newClass.apply {
            for (typeParam in oriClass.typeParameters) {
                val (newTypeParam, oriExists) = typeParam.toNew()
                if (oriExists && newTypeParam is IrTypeParameter) {
                    typeParameters.add(newTypeParam)
                }
            }
            superType = newSuper
            for ((typeParamName, pair) in oriClass.allSuperTypeArguments) {
                val (typeParam, typeArg) = pair
                val (newTypeParam, oriTypeParamExists) = typeParam.toNew()
                if (!oriTypeParamExists || newTypeParam !is IrTypeParameter) {
                    continue
                }
                val (newArg, oriExists) = typeArg.toNew()
                val finalNewArg = if (oriExists) {
                    newArg
                } else {
                    newTypeParam.upperbound
                }
                allSuperTypeArguments[typeParamName] = newTypeParam to finalNewArg
            }
            implementedTypes.addAll(intfs.values)
            for (f in oriClass.functions.asSequence().filter { it in elements }) {
                if (f in elements) {
                    val newF = newFunctionFromClosure(f, elements)
                    old2NewFunctions[f] = newF
                    new2OldFunctions[newF] = f
                    functions.add(newF)
                }
            }
        }
    }

    fun secondStageNewClassFromClosure(
        newClass: IrClassDeclaration,
        newProg: IrProgram,
        elements: Set<IrElement>
    ) {
        val signatureMap = newClass.collectFunctionSignatureMap()
        val (must, can, stub) = newClass.getOverrideCandidates(signatureMap)
        fun SuperAndIntfFunctions.handleNewF(isStub: Boolean?) {
            val (superF, intfF) = this
            val allSuperF = if (superF != null) {
                intfF + superF
            } else intfF
            val fName = allSuperF.first().name
            val funcInNew = newClass.functions.firstOrNull { it.name == fName }
                ?: run {
                    val oldF = program.classes
                        .first { it.name == newClass.name }
                        .functions.first { it.name == fName }
                    newFunctionFromClosure(
                        oldF,
                        elements
                    ).apply {
                        newClass.functions.add(this)
                        new2OldFunctions[this] = oldF
                        old2NewFunctions[oldF] = this
                    }
                }
            funcInNew.apply {
                isOverride = true
                override.addAll(allSuperF)
                if (isStub != null) {
                    isOverrideStub = isStub
                } else {
                    val oldF = new2OldFunctions[this]!!
                    isOverrideStub = oldF.isOverrideStub
                }
            }
        }

        for (funcs in must) {
            funcs.handleNewF(false)
        }
        for (funcs in can) {
            funcs.handleNewF(null)
        }
        for (funcs in stub) {
            funcs.handleNewF(true)
        }
    }

    fun newClassSkeleton(oriClass: IrClassDeclaration): IrClassDeclaration {
        return buildClassDeclaration {
            name = oriClass.name
            language = oriClass.language
            classKind = oriClass.classKind
        }
    }

    fun newProg(elements: Set<IrElement>): IrProgram {
        old2NewFunctions.clear()
        new2OldFunctions.clear()
        old2NewClasses.clear()
        new2OldClasses.clear()
        return buildProgram().apply {
            val existsClasses = program.classes.filter { it in elements }
            for (clazz in existsClasses) {
                val newClass = newClassSkeleton(clazz)
                classes.add(newClass)
                old2NewClasses[clazz] = newClass
                new2OldClasses[newClass] = clazz
            }
            for (clazz in existsClasses) {
                val newClass = old2NewClasses[clazz]!!
                firstStageNewClassFromClosure(clazz, newClass, elements)
            }
            for (clazz in this.topologicalOrderedClasses) {
                secondStageNewClassFromClosure(clazz, this, elements)
            }
        }
    }
}