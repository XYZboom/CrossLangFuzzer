package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.copyForOverride
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.RemoveOnlyIterator
import com.github.xyzboom.codesmith.ir.declarations.SuperAndIntfFunctions
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.postorderTraverseOverride
import com.github.xyzboom.codesmith.ir.declarations.render
import com.github.xyzboom.codesmith.ir.declarations.traceFunc
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.copy
import com.github.xyzboom.codesmith.ir.types.notNullType
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.validator.collectFunctionSignatureMap
import com.github.xyzboom.codesmith.validator.getOverrideCandidates
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.plusAssign
import kotlin.collections.set

/**
 * A minimize runner to reduce class numbers as much as possible.
 * # Steps
 * ## 1. Split
 * Firstly, we split classes into two groups: suspicious and normal.
 * If a class whose name appears in compile result, it and all supers of it will be marked as suspicious.
 * Other classes are marked as normal.
 * ## 2. Normal Classes Removal
 * Secondly, we will try to remove all normal classes to see if the bug exists.
 * If the bug disappears, we roll back this and remove normal class one by one.
 * Normally, removing all normal classes will not cause the bug disappear.
 * ## 3. Suspicious Classes Removal
 * Thirdly, we try to remove suspicious classes one by one.
 * If there are no class can be removed, we will finish the class level minimization.
 * # Note:
 * When removing a class, all its subclasses will be removed so we must start from classes who have no child.
 * The types using this removed class will be replaced into another class.
 * If this to be removed class has type parameters, this other class will first have some,
 * but soon we will try to remove these type parameters.
 */
class MinimizeRunnerImpl(
    compilerRunner: ICompilerRunner
) : IMinimizeRunner, ICompilerRunner by compilerRunner {
    companion object {
        @JvmStatic
        private val logger = KotlinLogging.logger {}
    }

    class ProgramWithRemovedDecl(val prog: IrProgram) : IrProgram by prog {
        val removedClasses = mutableSetOf<IrClassDeclaration>()

        /**
         * If a replaceWith class is never used, no need to add it into program.
         */
        val usedClassReplaceWith = hashSetOf<IrType>()

        private inner class DelegateMutableMap(val ori: MutableMap<String, IrType>) :
            MutableMap<String, IrType> by ori {
            override operator fun get(key: String): IrType? {
                return ori[key]?.also {
                    usedClassReplaceWith.add(it)
                }
            }
        }

        val classReplaceWith: MutableMap<String, IrType> = DelegateMutableMap(mutableMapOf())
        val typeParameterReplaceWith = HashMap<IrTypeParameterName, IrType>()

        fun backup(): ProgramWithRemovedDecl {
            return ProgramWithRemovedDecl(prog.deepCopy()).also {
                it.removedClasses.addAll(removedClasses)
                it.usedClassReplaceWith.addAll(usedClassReplaceWith)
                it.classReplaceWith.putAll(classReplaceWith)
                it.typeParameterReplaceWith.putAll(typeParameterReplaceWith)
            }
        }

        fun replaceTypeArgsForClass(oriTypeArgs: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>)
                : HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>> {
            val newTypeArgs = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>()
            for ((typeParamName, pair) in oriTypeArgs) {
                if (typeParamName in typeParameterReplaceWith) {
                    continue
                }
                val (typeParam, typeArg) = pair
                val replaceTypeParameter = replaceType(typeParam.copy())
                if (replaceTypeParameter !is IrTypeParameter) {
                    continue
                }
                newTypeArgs[typeParamName] = replaceTypeParameter to replaceType(typeArg)
            }
            return newTypeArgs
        }

        fun replaceTypeArgs(oriTypeArgs: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType?>>)
                : HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType?>> {
            val newTypeArgs = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType?>>()
            for ((typeParamName, pair) in oriTypeArgs) {
                if (typeParamName in typeParameterReplaceWith) {
                    continue
                }
                val (typeParam, typeArg) = pair
                val newTypeParam = replaceType(typeParam.copy()) as IrTypeParameter
                newTypeArgs[typeParamName] = newTypeParam to typeArg?.let { replaceType(it) }
            }
            return newTypeArgs
        }

        fun replaceType(type: IrType): IrType {
            return when {
                type is IrParameterizedClassifier && type.classDecl.typeParameters.isEmpty() -> {
                    // when the type parameters were all removed,
                    // we must replace IrParameterizedClassifier with IrSimpleClassifier
                    val replaceWithSimple = buildSimpleClassifier {
                        classDecl = type.classDecl
                    }
                    replaceType(replaceWithSimple)
                }

                type is IrParameterizedClassifier && type.classDecl !in removedClasses -> {
                    type.arguments = replaceTypeArgs(type.arguments)
                    type
                }

                type is IrClassifier && type.classDecl in removedClasses -> {
                    val replaceWith = classReplaceWith[type.classDecl.name]!!
                    if (type is IrParameterizedClassifier) {
                        val newTypeArgs = replaceTypeArgs(type.arguments)
                        if (replaceWith is IrParameterizedClassifier) {
                            replaceWith.arguments = newTypeArgs
                        }
                    }
                    replaceWith
                }

                type is IrNullableType -> {
                    val innerType = type.innerType
                    type.innerType = replaceType(innerType)
                    type
                }

                type is IrPlatformType -> {
                    val innerType = type.innerType
                    type.innerType = replaceType(innerType)
                    type
                }

                type is IrDefinitelyNotNullType -> {
                    val innerType = type.innerType
                    val replaceWith = replaceType(innerType)
                    // if innerType was replaced with a non-type-parameter, DNN will be not-null type
                    if (replaceWith is IrTypeParameter) {
                        type.innerType = replaceWith
                        type
                    } else {
                        replaceWith.notNullType
                    }
                }

                type is IrTypeParameter -> {
                    if (IrTypeParameterName(type.name) in typeParameterReplaceWith) {
                        typeParameterReplaceWith[IrTypeParameterName(type.name)]!!
                    } else {
                        val newUpperbound = type.upperbound
                        type.upperbound = replaceType(newUpperbound)
                        type
                    }
                }

                else -> type
            }
        }

        /**
         * ```kt
         * open class A<T0> {}
         * open class A1<T1> : A<T1>() {}
         * open class A2 : A1<A2>() {}
         * ```
         * Remove `A1`.
         * ```kt
         * open class A<T0> {}
         * open class A2 : A1<A2>() {}
         * ```
         * Before: T1[ A2 ], T0[ T1 ], after: T0[ A2 ]
         */
        fun arrangeOnTypeParameter(
            args: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
            after: IrParameterizedClassifier
        ) {
            for ((typeParamName, pair) in after.arguments) {
                val (_, typeArg) = pair
                if (typeArg is IrTypeParameter && typeParamName in args) {
                    after.arguments[typeParamName] = args[typeParamName]!!
                }
            }
        }

        /**
         * make super of replaced super be current super.
         * ```kt
         * open class I {}
         * open class I1: I {}
         * open class I2: I1 {}
         * ```
         * After remove `I1` with `arrangeHierarchy`:
         * ```kt
         * open class I {}
         * open class I2: I {}
         * ```
         */
        fun arrangeSuperHierarchy(c: IrClassDeclaration, superType: IrType): Pair<IrType?, List<IrType>> {
            if (superType is IrNullableType) {
                // what an amazing thing if super type is nullable, but we still handle this.
                return arrangeSuperHierarchy(c, superType.innerType)
            }
            if (superType !is IrClassifier) {
                return superType to emptyList()
            }
            val superOfSuper = superType.classDecl.superType?.copy()?.let { replaceType(it) }
            val intfOfSuper = nonRepeatIntf(c, superType)
            if (superOfSuper is IrParameterizedClassifier) {
                arrangeOnTypeParameter(c.allSuperTypeArguments, superOfSuper)
            }
            for (intfType in intfOfSuper) {
                if (intfType is IrParameterizedClassifier) {
                    arrangeOnTypeParameter(c.allSuperTypeArguments, intfType)
                }
            }

            return superOfSuper to intfOfSuper
        }

        fun arrangeIntfHierarchy(c: IrClassDeclaration, intfType: IrType): List<IrType> {
            if (intfType is IrNullableType) {
                // what an amazing thing if super type is nullable, but we still handle this.
                return arrangeIntfHierarchy(c, intfType.innerType)
            }
            if (intfType !is IrClassifier) {
                return emptyList()
            }
            val intfOfSuper = nonRepeatIntf(c, intfType)
            for (intfType in intfOfSuper) {
                if (intfType is IrParameterizedClassifier) {
                    arrangeOnTypeParameter(c.allSuperTypeArguments, intfType)
                }
            }

            return intfOfSuper
        }

        private fun nonRepeatIntf(
            c: IrClassDeclaration,
            intfType: IrClassifier
        ): List<IrType> {
            val existsIntfNames = c.implementedTypes.mapNotNull { (it as? IrClassDeclaration)?.name }.toSet()
            val intfOfSuper = intfType.classDecl.implementedTypes.mapNotNull {
                if (it in c.implementedTypes) {
                    return@mapNotNull null
                }
                if (it is IrSimpleClassifier && it.classDecl.name in existsIntfNames) {
                    return@mapNotNull null
                }
                replaceType(it.copy())
            }
            return intfOfSuper
        }

        /**
         * @param arrangeHierarchy do [ProgramWithRemovedDecl.arrangeSuperHierarchy]
         * and [ProgramWithRemovedDecl.arrangeIntfHierarchy] when super was replaced
         */
        private fun doReplaceTypes(
            removeIfSuperChanged: Boolean = true,
            arrangeHierarchy: Boolean = false,
        ) {
            for (c in classes) {
                c.superType?.let {
                    val replaced = replaceType(it)
                    if (replaced !== c.superType && removeIfSuperChanged) {
                        if (arrangeHierarchy) {
                            val (newSuper, newIntf) = arrangeSuperHierarchy(c, it)
                            c.superType = newSuper
                            c.implementedTypes += newIntf
                        } else {
                            c.superType = null
                        }
                    } else {
                        c.superType = replaced
                    }
                }
                val adding2Impl = ArrayList<IrType>()
                if (removeIfSuperChanged) {
                    val iterImpl = c.implementedTypes.iterator()
                    while (iterImpl.hasNext()) {
                        val impl = iterImpl.next()
                        val replaced = replaceType(impl)
                        // super was deleted
                        if (replaced !== impl) {
                            iterImpl.remove()
                            if (arrangeHierarchy) {
                                adding2Impl.addAll(arrangeIntfHierarchy(c, impl))
                            }
                        }
                    }
                } else {
                    for ((index, impl) in c.implementedTypes.withIndex()) {
                        val replaced = replaceType(impl)
                        if (replaced !== impl) {
                            c.implementedTypes[index] = replaced
                        }
                    }
                }
                val existsIntfNames = c.implementedTypes.mapNotNull { (it as? IrClassDeclaration)?.name }.toMutableSet()
                if (adding2Impl.isNotEmpty()) {
                    for (impl in adding2Impl) {
                        if (impl is IrClassifier) {
                            if (impl.classDecl.name in existsIntfNames) {
                                continue
                            }
                            c.implementedTypes.add(impl)
                            existsIntfNames.add(impl.classDecl.name)
                        }
                    }
                }

                fun removeIfTypeParameterWasReplaced(iter: MutableIterator<IrTypeParameter>) {
                    while (iter.hasNext()) {
                        val typeParam = iter.next()
                        if (IrTypeParameterName(typeParam.name) in typeParameterReplaceWith) {
                            iter.remove()
                            continue
                        }
                        typeParam.upperbound = replaceType(typeParam.upperbound)
                    }
                }

                removeIfTypeParameterWasReplaced(c.typeParameters.iterator())
                c.allSuperTypeArguments = replaceTypeArgsForClass(c.allSuperTypeArguments)
                val iter = c.functions.iterator()
                while (iter.hasNext()) {
                    val f = iter.next()
                    removeIfTypeParameterWasReplaced(f.typeParameters.iterator())
                    for (param in f.parameterList.parameters) {
                        param.type = replaceType(param.type)
                    }
                    f.returnType = replaceType(f.returnType)
                }
            }
        }

        fun removeIfOverrideWereAllRemoved() {
            val funcToBeRemoveFromOverride = mutableSetOf<IrFunctionDeclaration>()
            for (c in classes) {
                val iter = c.functions.iterator()
                while (iter.hasNext()) {
                    val f = iter.next()
                    if (f.override.isNotEmpty()) {
                        val overrideCopy = ArrayList(f.override)
                        f.postorderTraverseOverride { overrideF, iter ->
                            if (overrideF in funcToBeRemoveFromOverride) {
                                iter.remove()
                                return@postorderTraverseOverride
                            }
                            if (overrideF.containingClassName in classReplaceWith) {
                                funcToBeRemoveFromOverride.add(overrideF)
                                iter.remove()
                                return@postorderTraverseOverride
                            }
                            if (overrideF.override.isEmpty()) {
                                return@postorderTraverseOverride
                            }
                            if (overrideF.override.all { it.containingClassName in classReplaceWith }) {
                                funcToBeRemoveFromOverride.add(overrideF)
                                iter.remove()
                            } else if (overrideF.override.all { it in funcToBeRemoveFromOverride }) {
                                funcToBeRemoveFromOverride.add(overrideF)
                                iter.remove()
                            }
                        }
                        if (overrideCopy.all { it in funcToBeRemoveFromOverride } ||
                            overrideCopy.all { it.containingClassName in classReplaceWith }) {
                            funcToBeRemoveFromOverride.add(f)
                            iter.remove()
                        }
                    }
                }
            }
        }

        fun replaceClassDeeply(className: String, nextReplacementName: String) {
            val clazz = classes.first { it.name == className }
            logger.trace { "try to remove ${clazz.render()}" }
            val replaceWith = buildClassDeclaration {
                name = nextReplacementName
                classKind = ClassKind.FINAL
                typeParameters.addAll(clazz.typeParameters)
            }
            val replaceWithType = replaceWith.type
            classReplaceWith[clazz.name] = replaceWithType
            classes.remove(clazz)
            removedClasses.add(clazz)
            //<editor-fold desc="Replace types">
            doReplaceTypes()
            removeIfOverrideWereAllRemoved()
            //</editor-fold>
            if (usedClassReplaceWith.contains(replaceWithType)) {
                classes.add(replaceWith)
            }
        }

        fun replaceClassWithIrAnyDeeply(className: String) {
            val clazz = classes.first { it.name == className }
            logger.trace { "try to remove ${clazz.render()} deeply" }
            val replaceWith = IrAny
            classReplaceWith[clazz.name] = replaceWith
            classes.remove(clazz)
            removedClasses.add(clazz)
            //<editor-fold desc="Replace types">
            doReplaceTypes()
            removeIfOverrideWereAllRemoved()
            //</editor-fold>
        }

        fun IrClassDeclaration.regenOverrideFunction(
            from: List<IrFunctionDeclaration>,
            makeAbstract: Boolean,
            isStub: Boolean,
            isFinal: Boolean,
            printJavaNullableAnnotationProbability: Boolean,
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
                containingClassName = this@regenOverrideFunction.name
                this.returnType = first.returnType.copy()
                if (!makeAbstract) {
                    body = buildBlock()

                    this.isFinal = isFinal
                }
                require(!isOverrideStub || (isOverrideStub && override.any { it.isFinal } == this.isFinal))
                printNullableAnnotations = printJavaNullableAnnotationProbability
            }
            functions.add(function)
        }

        fun replaceClassWithIrAnyShallowly(className: String) {
            val clazz = classes.first { it.name == className }
            logger.trace { "try to remove ${clazz.render()} shallowly" }
            val replaceWith = IrAny
            classReplaceWith[clazz.name] = replaceWith
            classes.remove(clazz)
            removedClasses.add(clazz)
            doReplaceTypes()

            // value: ClassName::FunctionName
            val handledFunctions = hashSetOf<String>()
            val topologicalOrderedFunctions = sequence<Triple<IrClassDeclaration,
                    IrFunctionDeclaration, RemoveOnlyIterator>> {
                outer@ while (true) {
                    for (c in classes) {
                        val fIter = c.functions.iterator()
                        while (fIter.hasNext()) {
                            val f = fIter.next()
                            if ("${f.containingClassName}::${f.name}" in handledFunctions) {
                                continue
                            }

                            class RemoveCurrent : RemoveOnlyIterator {
                                override fun remove() {
                                    fIter.remove()
                                }
                            }

                            val override = f.override
                            if (override.isEmpty()) {
                                yield(Triple(c, f, RemoveCurrent()))
                                continue@outer
                            }
                            if (override.size == 1 && override.single().containingClassName == className) {
                                yield(Triple(c, f, RemoveCurrent()))
                                continue@outer
                            }
                            var anyInReplacedClass = false
                            var allNotInReplacedClass = true
                            // except the one in the replaced class, others are all handled
                            var otherAllHandled = true
                            var allHandled = true
                            for (o in f.override) {
                                val handled = "${o.containingClassName}::${o.name}" in handledFunctions
                                if (!handled) {
                                    allHandled = false
                                }
                                if (o.containingClassName == className) {
                                    anyInReplacedClass = true
                                    allNotInReplacedClass = false
                                } else if (!handled) {
                                    otherAllHandled = false
                                }

                            }
                            if ((anyInReplacedClass && otherAllHandled) || (allNotInReplacedClass && allHandled)) {
                                yield(Triple(c, f, RemoveCurrent()))
                                continue@outer
                            }
                        }
                    }
                    break
                }
            }

            for ((c, f, iter) in topologicalOrderedFunctions) {
                val override = f.override
                if (override.size == 1 && override.single().containingClassName == className) {
                    override.clear()
                    f.isOverride = false
                    f.isOverrideStub = false
                } else if (override.size > 1) {
                    override.removeIf { it.containingClassName == className }
                    iter.remove()
                    val signatureMap = c.collectFunctionSignatureMap()
                    val (must, can, stub) = c.getOverrideCandidates(signatureMap)
                    fun List<SuperAndIntfFunctions>.matchesOrNull(): SuperAndIntfFunctions? {
                        return firstOrNull { it.first?.name == f.name || it.second.any { it1 -> it1.name == f.name } }
                    }

                    fun SuperAndIntfFunctions.flatten(): List<IrFunctionDeclaration> {
                        return (if (first != null) {
                            second + first!!
                        } else second).toList()
                    }

                    val mustMatches = must.matchesOrNull()
                    val canMatches = can.matchesOrNull()
                    val stubMatches = stub.matchesOrNull()
                    if (mustMatches != null || canMatches != null) {
                        val mustOrCan = (mustMatches ?: canMatches)!!
                        c.regenOverrideFunction(
                            mustOrCan.flatten(), makeAbstract = false,
                            isStub = false, mustOrCan.first?.isFinal ?: false,
                            printJavaNullableAnnotationProbability = f.printNullableAnnotations,
                            language = c.language
                        )
                    } else {
                        c.regenOverrideFunction(
                            stubMatches!!.flatten(), makeAbstract = false,
                            isStub = true, stubMatches.first?.isFinal ?: false,
                            printJavaNullableAnnotationProbability = f.printNullableAnnotations,
                            language = c.language
                        )
                    }
                }
                handledFunctions.add("${f.containingClassName}::${f.name}")
            }
        }

        fun removeFunction(name: String) {
            for (c in classes) {
                val fIter = c.functions.iterator()
                while (fIter.hasNext()) {
                    val f = fIter.next()
                    f.postorderTraverseOverride { overrideF, it ->
                        if (overrideF.name == name) {
                            it.remove()
                        }
                    }
                    if (f.name == name) {
                        fIter.remove()
                    }
                }
            }
        }

        fun collectAllTypeParameters(): Set<IrTypeParameterName> {
            val result = HashSet<IrTypeParameterName>()
            for (c in classes) {
                result.addAll(c.typeParameters.map { IrTypeParameterName(it.name) })
                for (f in c.functions) {
                    result.addAll(f.typeParameters.map { IrTypeParameterName(it.name) })
                }
            }
            return result
        }

        fun replaceTypeParameterWithIrAny(typeParam: IrTypeParameterName) {
            typeParameterReplaceWith[typeParam] = IrAny
            for (c in classes) {
                c.typeParameters.removeIf { it.name == typeParam.value }
                c.allSuperTypeArguments.remove(typeParam)
                for ((name, pair) in c.allSuperTypeArguments) {
                    val (typeParamNow, typeArg) = pair
                    if (typeArg is IrTypeParameter && typeArg.name == typeParam.value) {
                        c.allSuperTypeArguments[name] = typeParamNow to IrAny
                    }
                }
                for (f in c.functions) {
                    f.typeParameters.removeIf { it.name == typeParam.value }
                }
            }
            doReplaceTypes(false)
        }

        fun collectAllParameters(): Set<String> {
            val result = HashSet<String>()
            for (c in classes) {
                for (f in c.functions) {
                    result.addAll(f.parameterList.parameters.map { it.name })
                }
            }
            return result
        }

        fun removeParameter(name: String) {
            for (c in classes) {
                for (f in c.functions) {
                    f.parameterList.parameters.removeIf { it.name == name }
                }
            }
        }
    }

    var nameNumber = 0
    fun nextReplacementName(): String {
        return "A${nameNumber++}"
    }

    fun List<CompileResult>.mayRelatedWith(name: String): Boolean {
        return any {
            it.javaResult?.contains(name) == true || it.majorResult?.contains(name) == true
        }
    }

    fun removeUnrelatedFunctions(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        val suspicious = mutableSetOf<String>()
        val normal = mutableSetOf<String>()
        classes@ for (clazz in initProg.classes) {
            for (f in clazz.functions) {
                if (initCompileResult.mayRelatedWith(f.name)) {
                    suspicious.add(f.name)
                } else {
                    normal.add(f.name)
                }
            }
        }
        var result = initProg.backup()
        var newCompileResult: List<CompileResult> = initCompileResult
        run tryRemoveAllNormal@{
            for (funcName in normal) {
                result.removeFunction(funcName)
            }
            newCompileResult = compile(result, compilers)
            if (newCompileResult != initCompileResult) {
                logger.trace { "remove all normal functions cause the bug disappear, rollback" }
                // rollback
                result = initProg.backup()
                newCompileResult = initCompileResult
            } else {
                logger.trace { "remove all normal functions success" }
                normal.clear()
            }
        }
        val allIter = (normal.asSequence() + suspicious.asSequence()).iterator()
        var lastCompileResult = newCompileResult
        while (allIter.hasNext()) {
            val next = allIter.next()
            val backup = result.backup()
            logger.trace { "remove function $next" }
            result.removeFunction(next)
            newCompileResult = compile(result, compilers)
            if (newCompileResult != initCompileResult) {
                result = backup
                newCompileResult = lastCompileResult
            }
            lastCompileResult = newCompileResult
        }

        return result to newCompileResult
    }

    fun removeUnrelatedClass(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        var result = initProg.backup()
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val classNames = result.classes.map { it.name }
            for (className in classNames) {
                val backup = result.backup()
                result.replaceClassWithIrAnyDeeply(className)
                newCompileResult = compile(result, compilers)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = backup
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result to lastCompileResult
    }

    fun removeUnrelatedTypeParameters(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        var result = initProg.backup()
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val allTypeParameters = result.collectAllTypeParameters()
            for (typeParameterName in allTypeParameters) {
                val backup = result.backup()
                result.replaceTypeParameterWithIrAny(typeParameterName)
                newCompileResult = compile(result, compilers)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = backup
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result to lastCompileResult
    }

    fun removeUnrelatedParameters(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        var result = initProg.backup()
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val allParameters = result.collectAllParameters()
            for (parameterName in allParameters) {
                val backup = result.backup()
                result.removeParameter(parameterName)
                newCompileResult = compile(result, compilers)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = backup
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result to lastCompileResult
    }

    fun reduceInheritanceHierarchy(
        initProg: ProgramWithRemovedDecl,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<ProgramWithRemovedDecl, List<CompileResult>> {
        var result = initProg.backup()
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            if (result.classes.size <= 3) {
                break
            }
            val classNames = result.classes.map { it.name }
            for (className in classNames) {
                val backup = result.backup()
                result.replaceClassWithIrAnyShallowly(className)
                newCompileResult = compile(result, compilers)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = backup
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result to lastCompileResult
    }

    override fun minimize(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<IrProgram, List<CompileResult>> {
        val firstStage = ::removeUnrelatedClass
        val stages = listOf(
            ::removeUnrelatedFunctions,
            ::removeUnrelatedParameters,
            ::removeUnrelatedTypeParameters,
            ::reduceInheritanceHierarchy
        )
        var resultPair = firstStage(ProgramWithRemovedDecl(initProg), initCompileResult, compilers)
        for (stage in stages) {
            resultPair = stage(resultPair.first, resultPair.second, compilers)
        }
        return resultPair.first.prog to resultPair.second
    }
}