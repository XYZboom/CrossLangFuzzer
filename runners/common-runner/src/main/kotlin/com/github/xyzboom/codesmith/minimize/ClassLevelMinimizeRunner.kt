package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.postorderTraverseOverride
import com.github.xyzboom.codesmith.ir.declarations.render
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.copy
import com.github.xyzboom.codesmith.ir.types.type
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}


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
class ClassLevelMinimizeRunner(
    compilerRunner: ICompilerRunner
) : IMinimizeRunner, ICompilerRunner by compilerRunner {


    class ProgramWithReachableTag(private val prog: IrProgram) {
        val reachableTag: HashSet<IrClassDeclaration> = HashSet()


    }

    fun splitClasses(prog: IrProgram) {


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

        fun replaceTypeArgsForClass(oriTypeArgs: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>)
                : HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>> {
            val newTypeArgs = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>()
            for ((typeParamName, pair) in oriTypeArgs) {
                if (typeParamName in typeParameterReplaceWith) {
                    continue
                }
                val (typeParam, typeArg) = pair
                val newTypeParam = replaceType(typeParam.copy()) as IrTypeParameter
                newTypeArgs[typeParamName] = newTypeParam to replaceType(typeArg)
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

        private fun doReplaceTypes(removeIfSuperChanged: Boolean = true) {
            for (c in classes) {
                c.superType?.let {
                    val replaced = replaceType(it)
                    // super was deleted
                    if (removeIfSuperChanged && replaced !== c.superType) {
                        c.superType = null
                    } else {
                        c.superType = replaced
                    }
                }
                if (removeIfSuperChanged) {
                    val iterImpl = c.implementedTypes.iterator()
                    while (iterImpl.hasNext()) {
                        val impl = iterImpl.next()
                        val replaced = replaceType(impl)
                        // super was deleted
                        if (replaced !== impl) {
                            iterImpl.remove()
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

        fun replaceClass(className: String, nextReplacementName: String) {
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

        fun replaceClassWithIrAny(className: String) {
            val clazz = classes.first { it.name == className }
            logger.trace { "try to remove ${clazz.render()}" }
            val replaceWith = IrAny
            classReplaceWith[clazz.name] = replaceWith
            classes.remove(clazz)
            removedClasses.add(clazz)
            //<editor-fold desc="Replace types">
            doReplaceTypes()
            removeIfOverrideWereAllRemoved()
            //</editor-fold>
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
        initProg: IrProgram,
        initCompileResult: List<CompileResult>
    ): Pair<IrProgram, List<CompileResult>> {
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
        val progNew = initProg.deepCopy()
        var result = ProgramWithRemovedDecl(progNew)
        var newCompileResult: List<CompileResult> = initCompileResult
        run tryRemoveAllNormal@{
            for (funcName in normal) {
                result.removeFunction(funcName)
            }
            newCompileResult = compile(result)
            if (newCompileResult != initCompileResult) {
                logger.trace { "remove all normal functions cause the bug disappear, rollback" }
                // rollback
                result = ProgramWithRemovedDecl(initProg.deepCopy())
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
            val backup = result.prog.deepCopy()
            logger.trace { "remove function $next" }
            result.removeFunction(next)
            newCompileResult = compile(result)
            if (newCompileResult != initCompileResult) {
                result = ProgramWithRemovedDecl(backup)
                newCompileResult = lastCompileResult
            }
            lastCompileResult = newCompileResult
        }

        return result.prog to newCompileResult
    }

    fun removeUnrelatedClass(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>
    ): Pair<IrProgram, List<CompileResult>> {
        val progNew = initProg.deepCopy()
        var result = ProgramWithRemovedDecl(progNew)
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val classNames = result.classes.map { it.name }
            for (className in classNames) {
                val backup = result.prog.deepCopy()
                result.replaceClassWithIrAny(className)
                newCompileResult = compile(result)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = ProgramWithRemovedDecl(backup)
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result.prog to lastCompileResult
    }

    fun removeUnrelatedTypeParameters(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>
    ): Pair<IrProgram, List<CompileResult>> {
        val progNew = initProg.deepCopy()
        var result = ProgramWithRemovedDecl(progNew)
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val allTypeParameters = result.collectAllTypeParameters()
            for (typeParameterName in allTypeParameters) {
                val backup = result.prog.deepCopy()
                result.replaceTypeParameterWithIrAny(typeParameterName)
                newCompileResult = compile(result)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = ProgramWithRemovedDecl(backup)
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result.prog to lastCompileResult
    }

    fun removeUnrelatedParameters(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>
    ): Pair<IrProgram, List<CompileResult>> {
        val progNew = initProg.deepCopy()
        var result = ProgramWithRemovedDecl(progNew)
        var anyClassRemoved = false
        var newCompileResult: List<CompileResult> = initCompileResult
        var lastCompileResult = newCompileResult
        while (true) {
            val allParameters = result.collectAllParameters()
            for (parameterName in allParameters) {
                val backup = result.prog.deepCopy()
                result.removeParameter(parameterName)
                newCompileResult = compile(result)
                if (newCompileResult != initCompileResult) {
                    // bug disappear, rollback
                    result = ProgramWithRemovedDecl(backup)
                    newCompileResult = lastCompileResult
                } else {
                    anyClassRemoved = true
                }
                lastCompileResult = newCompileResult
            }
            if (!anyClassRemoved) break
            anyClassRemoved = false
        }
        return result.prog to lastCompileResult
    }

    override fun minimize(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>
    ): Pair<IrProgram, List<CompileResult>> {
        var resultPair = removeUnrelatedFunctions(initProg, initCompileResult)
        resultPair = removeUnrelatedClass(resultPair.first, resultPair.second)
        resultPair = removeUnrelatedParameters(resultPair.first, resultPair.second)
        resultPair = removeUnrelatedTypeParameters(resultPair.first, resultPair.second)
        return resultPair.first to resultPair.second
    }
}