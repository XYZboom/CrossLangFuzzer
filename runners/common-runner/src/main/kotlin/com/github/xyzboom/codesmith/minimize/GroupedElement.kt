package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrNamedElement
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
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
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.validator.collectFunctionSignatureMap
import com.github.xyzboom.codesmith.validator.getOverrideCandidates
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.first
import kotlin.collections.iterator
import kotlin.collections.plus
import kotlin.collections.set

data class GroupedElement(
    val program: IrProgram,
    var classes: Set<IrClassDeclaration>,
    var superTypeOfs: Set<SuperTypeOf>,
    var functions: Set<FunctionGroup>,
    var typeParameters: Set<TypeParameter>,
    var parameters: Set<Parameter>
) {
    companion object {
        fun groupElements(prog: IrProgram): GroupedElement {
            val classes = LinkedHashSet(prog.classes)
            val superTypeOfs = mutableSetOf<SuperTypeOf>()
            val functions = mutableSetOf<FunctionGroup>()
            val typeParameters = mutableSetOf<TypeParameter>()
            val parameters = mutableSetOf<Parameter>()

            for (clazz in classes) {
                typeParameters.addAll(clazz.typeParameters.map { TypeParameter(it.name) })
                clazz.traverseSuper(
                    enter = { superType ->
                        if (superType is IrClassifier) {
                            val superTypeOf = SuperTypeOf(clazz, superType.classDecl)
                            superTypeOfs += superTypeOf
                        }
                    }
                )
                for (function in clazz.functions) {
                    typeParameters.addAll(function.typeParameters.map { TypeParameter(it.name) })
                    functions.add(FunctionGroup(function.name))
                    parameters.addAll(function.parameterList.parameters.map { Parameter(it.name) })
                }
            }
            return GroupedElement(
                prog, classes, superTypeOfs,
                functions, typeParameters, parameters
            )
        }
    }

    fun makeValid(): GroupedElement {
        val newSuperTypeOfs = LinkedHashSet<SuperTypeOf>(superTypeOfs)
        for (superTypeOf in superTypeOfs) {
            if (superTypeOf.clazz !in classes || superTypeOf.superClass !in classes) {
                newSuperTypeOfs.remove(superTypeOf)
            }
        }
        val newFunctions = mutableSetOf<FunctionGroup>()
        val newTypeParameters = LinkedHashSet<TypeParameter>()
        val newParameters = LinkedHashSet<Parameter>()
        for (clazz in classes) {
            newTypeParameters.addAll(
                clazz.typeParameters
                    .map { TypeParameter(it.name) }
                    .filter { it in this.typeParameters }
            )
            for (f in clazz.functions.filter { it in this.functions }) {
                newFunctions.add(FunctionGroup(f.name))
                newTypeParameters.addAll(
                    f.typeParameters
                        .map { TypeParameter(it.name) }
                        .filter { it in this.typeParameters }
                )
                newParameters.addAll(
                    f.parameterList.parameters
                        .map { Parameter(it.name) }
                        .filter { it in this.parameters }
                )
            }
        }
        return copy(
            superTypeOfs = newSuperTypeOfs,
            functions = newFunctions,
            typeParameters = newTypeParameters,
            parameters = newParameters,
        )
    }

    fun toProgram(): IrProgram {
        return Group2Program().newProg()
    }

    inner class Group2Program {
        val old2NewFunctions = mutableMapOf<IrFunctionDeclaration, IrFunctionDeclaration>()
        val new2OldFunctions = mutableMapOf<IrFunctionDeclaration, IrFunctionDeclaration>()
        val old2NewClasses = mutableMapOf<IrClassDeclaration, IrClassDeclaration>()
        val new2OldClasses = mutableMapOf<IrClassDeclaration, IrClassDeclaration>()

        fun newFunctionFromClosure(oriFunc: IrFunctionDeclaration): IrFunctionDeclaration {
            return buildFunctionDeclaration {
                name = oriFunc.name
                language = oriFunc.language
                val typeParameters = oriFunc.typeParameters.mapNotNull { typeParameter ->
                    val (newType, oriExists) = newTypeFromClosure(typeParameter)
                    if (newType !is IrTypeParameter) return@mapNotNull null
                    newType.takeIf { oriExists }
                }
                this.typeParameters += typeParameters
                body = oriFunc.body
                isFinal = oriFunc.isFinal
                parameterList = buildParameterList {
                    val parameters = oriFunc.parameterList.parameters.mapNotNull { parameter ->
                        if (parameter !in this@GroupedElement.parameters) {
                            return@mapNotNull null
                        }
                        val (type, _) = newTypeFromClosure(parameter.type)
                        buildParameter {
                            name = parameter.name
                            this.type = type
                        }
                    }
                    this.parameters.addAll(parameters)
                }
                val (returnType, _) = newTypeFromClosure(oriFunc.returnType)
                this.returnType = returnType
                containingClassName = oriFunc.containingClassName
            }
        }

        /**
         * Make new type from closure.
         * @return A pair whose first is the new type and
         *         whose second is false if [oriType] is not in closure.
         */
        fun newTypeFromClosure(oriType: IrType): Pair<IrType, Boolean> {
            return when (oriType) {
                is IrBuiltInType -> oriType to true
                is IrNullableType -> {
                    val (newType, oriExists) = newTypeFromClosure(oriType.innerType)
                    buildNullableType {
                        innerType = newType
                    } to oriExists
                }

                is IrPlatformType -> {
                    val (newType, oriExists) = newTypeFromClosure(oriType.innerType)
                    buildPlatformType {
                        innerType = newType
                    } to oriExists
                }

                is IrDefinitelyNotNullType -> {
                    val (newType, oriExists) = newTypeFromClosure(oriType.innerType)
                    if (newType is IrTypeParameter) {
                        buildDefinitelyNotNullType { innerType = newType } to oriExists
                    } else newType to oriExists
                }

                is IrClassifier -> {
                    val oriClass = oriType.classDecl
                    if (oriClass !in this@GroupedElement.classes) {
                        return IrAny to false // todo replace directly may cause new error
                    }
                    val newClass = old2NewClasses[oriClass]!!
                    val newType = newClass.type
                    if (oriType is IrParameterizedClassifier) {
                        for ((typeParamName, pair) in oriType.arguments) {
                            if (newType is IrParameterizedClassifier) {
                                val (typeParam, typeArg) = pair
                                val (newTypeParam, oriTPExists) = newTypeFromClosure(typeParam)
                                if (!oriTPExists || newTypeParam !is IrTypeParameter) continue
                                val newTypeArg = if (typeArg != null) {
                                    val (newTypeArg, oriTAExists) = newTypeFromClosure(typeArg)
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
                    if (oriType !in typeParameters) {
                        return IrAny to false
                    }
                    val newType = buildTypeParameter {
                        name = oriType.name
                        val (newUpperbound, _) = newTypeFromClosure(oriType.upperbound)
                        upperbound = newUpperbound
                    }
                    newType to (oriType in typeParameters)
                }

                else -> throw IllegalArgumentException("No such IrType ${this::class.simpleName}")
            }
        }

        fun firstStageNewClassFromClosure(
            oriClass: IrClassDeclaration,
            newClass: IrClassDeclaration,
        ) {
            val oriSuper = oriClass.superType
            var newSuper: IrType? = null
            val intfs = mutableMapOf<IrClassDeclaration, IrType>()

            fun IrType.toNew(): Pair<IrType, Boolean> {
                return newTypeFromClosure(this)
            }

            //<editor-fold desc="HandleSuper">
            if (oriSuper !is IrClassifier) {
                val newPair = oriSuper?.toNew()
                if (newPair != null && newPair.second) {
                    newSuper = newPair.first
                }
            } else {
                val superClass = oriSuper.classDecl
                val superTypeOf = SuperTypeOf(oriClass, superClass)
                if (superTypeOf in superTypeOfs) {
                    val (newType, oriExists) = oriSuper.toNew()
                    if (oriExists) {
                        newSuper = newType
                    }
                } else {
                    superClass.traverseSuper {
                        if (it !is IrClassifier || it.classDecl !in this@GroupedElement.classes) {
                            return@traverseSuper true
                        }
                        val superSuperClass = it.classDecl
                        val superTypeOf = SuperTypeOf(oriClass, superSuperClass)
                        if (superTypeOf in this@GroupedElement.superTypeOfs) {
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
                if (intf !is IrClassifier || intf.classDecl !in this@GroupedElement.classes) {
                    continue
                }
                val superClass = intf.classDecl
                val superTypeOf = SuperTypeOf(oriClass, superClass)
                if (superTypeOf in this@GroupedElement.superTypeOfs) {
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
                for (f in oriClass.functions.asSequence().filter { it in this@GroupedElement.functions }) {
                    val newF = newFunctionFromClosure(f)
                    old2NewFunctions[f] = newF
                    new2OldFunctions[newF] = f
                    functions.add(newF)
                }
            }
        }

        fun secondStageNewClassFromClosure(newClass: IrClassDeclaration) {
            val signatureMap = newClass.collectFunctionSignatureMap()
            val (must, can, stub) = newClass.getOverrideCandidates(signatureMap)
            fun SuperAndIntfFunctions.handleNewF(isStub: Boolean?) {
                val (superF, intfF) = this
                val allSuperF = if (superF != null) {
                    intfF + superF
                } else intfF
                val fName = allSuperF.first().name
                val funcInNew = newClass.functions.first { it.name == fName }
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

        fun newProg(): IrProgram {
            return buildProgram().apply {
                val existsClasses = program.classes.filter { it in this@GroupedElement.classes }
                for (clazz in existsClasses) {
                    val newClass = newClassSkeleton(clazz)
                    classes.add(newClass)
                    old2NewClasses[clazz] = newClass
                    new2OldClasses[newClass] = clazz
                }
                for (clazz in existsClasses) {
                    val newClass = old2NewClasses[clazz]!!
                    firstStageNewClassFromClosure(clazz, newClass)
                }
                for (clazz in this.topologicalOrderedClasses) {
                    secondStageNewClassFromClosure(clazz)
                }
            }
        }
    }

    interface IEmptyElement : IrElement {
        override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {}

        override fun <D> transformChildren(
            transformer: IrTransformer<D>,
            data: D
        ): IrElement {
            return this
        }
    }

    interface IEmptyNamedElement : IEmptyElement, IrNamedElement {
        override fun <D> transformName(
            transformer: IrTransformer<D>,
            data: D
        ): IrNamedElement {
            return this
        }
    }

    data class SuperTypeOf(val clazz: IrClassDeclaration, val superClass: IrClassDeclaration) : IEmptyElement
    data class FunctionGroup(override var name: String) : IEmptyNamedElement
    data class TypeParameter(override var name: String) : IEmptyNamedElement
    data class Parameter(override var name: String) : IEmptyNamedElement

    operator fun Set<FunctionGroup>.contains(func: IrFunctionDeclaration): Boolean {
        return FunctionGroup(func.name) in this
    }

    operator fun Set<TypeParameter>.contains(typeParam: IrTypeParameter): Boolean {
        return TypeParameter(typeParam.name) in this
    }

    operator fun Set<Parameter>.contains(param: IrParameter): Boolean {
        return Parameter(param.name) in this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupedElement) return false

        if (classes != other.classes) return false
        if (superTypeOfs != other.superTypeOfs) return false
        if (functions != other.functions) return false
        if (typeParameters != other.typeParameters) return false
        if (parameters != other.parameters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = classes.hashCode()
        result = 31 * result + superTypeOfs.hashCode()
        result = 31 * result + functions.hashCode()
        result = 31 * result + typeParameters.hashCode()
        result = 31 * result + parameters.hashCode()
        return result
    }
}