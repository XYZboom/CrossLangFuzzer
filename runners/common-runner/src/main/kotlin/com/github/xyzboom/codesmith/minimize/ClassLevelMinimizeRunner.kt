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
import com.github.xyzboom.codesmith.ir.types.copy
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.validator.IrValidator
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
        val usedClassReplaceWith = hashSetOf<String>()

        private inner class DelegateMutableMap(val ori: MutableMap<String, IrClassDeclaration>) :
            MutableMap<String, IrClassDeclaration> by ori {
            override operator fun get(key: String): IrClassDeclaration? {
                return ori[key]?.also {
                    usedClassReplaceWith.add(it.name)
                }
            }
        }

        val classReplaceWith: MutableMap<String, IrClassDeclaration> = DelegateMutableMap(mutableMapOf())

        fun replaceTypeArgsForClass(oriTypeArgs: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>)
                : HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>> {
            val newTypeArgs = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>()
            for ((typeParamName, pair) in oriTypeArgs) {
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
                val (typeParam, typeArg) = pair
                val newTypeParam = replaceType(typeParam.copy()) as IrTypeParameter
                newTypeArgs[typeParamName] = newTypeParam to typeArg?.let { replaceType(it) }
            }
            return newTypeArgs
        }

        fun replaceType(type: IrType): IrType {
            return when {
                type is IrParameterizedClassifier && type.classDecl !in removedClasses -> {
                    type.arguments = replaceTypeArgs(type.arguments)
                    type
                }

                type is IrClassifier && type.classDecl in removedClasses -> {
                    val replaceWith = classReplaceWith[type.classDecl.name]!!.type
                    if (type is IrParameterizedClassifier) {
                        val newTypeArgs = replaceTypeArgs(type.arguments)
                        replaceWith as IrParameterizedClassifier
                        replaceWith.arguments = newTypeArgs
                    }
                    replaceWith
                }

                type is IrNullableType -> {
                    val innerType = type.innerType
                    type.innerType = replaceType(innerType)
                    type
                }

                type is IrTypeParameter -> {
                    val newUpperbound = type.upperbound
                    type.upperbound = replaceType(newUpperbound)
                    type
                }

                else -> type
            }
        }

        fun removeClass(clazz: IrClassDeclaration, nextReplacementName: String) {
            logger.trace { "try to remove ${clazz.render()}" }
            val replaceWith = buildClassDeclaration {
                name = nextReplacementName
                classKind = ClassKind.FINAL
                typeParameters.addAll(clazz.typeParameters)
            }
            classReplaceWith[clazz.name] = replaceWith
            classes.remove(clazz)
            removedClasses.add(clazz)
            //<editor-fold desc="Replace types">
            val funcToBeRemoveFromOverride = mutableSetOf<IrFunctionDeclaration>()
            for (c in classes) {
                c.superType?.let {
                    val replaced = replaceType(it)
                    // super was deleted
                    if (replaced !== c.superType) {
                        c.superType = null
                    }
                }
                val iterImpl = c.implementedTypes.iterator()
                while (iterImpl.hasNext()) {
                    val impl = iterImpl.next()
                    val replaced = replaceType(impl)
                    // super was deleted
                    if (replaced !== impl) {
                        iterImpl.remove()
                    }
                }
                for (typeParam in c.typeParameters) {
                    typeParam.upperbound = replaceType(typeParam.upperbound)
                }
                c.allSuperTypeArguments = replaceTypeArgsForClass(c.allSuperTypeArguments)
                val iter = c.functions.iterator()
                while (iter.hasNext()) {
                    val f = iter.next()
                    if (f.override.isNotEmpty()) {
                        val overrideCopy = ArrayList(f.override)
                        f.postorderTraverseOverride { overrideF, it ->
                            if (overrideF.containingClassName in classReplaceWith) {
                                funcToBeRemoveFromOverride.add(overrideF)
                                it.remove()
                                return@postorderTraverseOverride
                            }
                            if (overrideF.override.isEmpty()) {
                                return@postorderTraverseOverride
                            }
                            if (overrideF.override.all { it.containingClassName in classReplaceWith }) {
                                funcToBeRemoveFromOverride.add(overrideF)
                                it.remove()
                            } else if (overrideF.override.all { it in funcToBeRemoveFromOverride }) {
                                funcToBeRemoveFromOverride.add(overrideF)
                                it.remove()
                            }
                        }
                        if (overrideCopy.all { it in funcToBeRemoveFromOverride } ||
                            overrideCopy.all { it.containingClassName in classReplaceWith }) {
                            funcToBeRemoveFromOverride.add(f)
                            iter.remove()
                        }
                    }
                    for (typeParam in f.typeParameters) {
                        typeParam.upperbound = replaceType(typeParam.upperbound)
                    }
                    for (param in f.parameterList.parameters) {
                        param.type = replaceType(param.type)
                    }
                    f.returnType = replaceType(f.returnType)
                }
            }
            //</editor-fold>
            if (usedClassReplaceWith.contains(nextReplacementName)) {
                classes.add(replaceWith)
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

    fun ProgramWithRemovedDecl.withValidate(block: ProgramWithRemovedDecl.() -> Unit) {
        block()
        IrValidator().validate(this).throwOnErrors()
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
        run tryRemoveAllNormal@{
            for (funcName in normal) {
                result.withValidate { removeFunction(funcName) }
            }
            val newCompileResult = compile(result)
            if (newCompileResult != initCompileResult) {
                logger.trace { "remove all normal functions cause the bug disappear, rollback" }
                // rollback
                result = ProgramWithRemovedDecl(initProg.deepCopy())
            } else {
                logger.trace { "remove all normal functions success" }
                normal.clear()
            }
        }
        val allIter = (normal.asSequence() + suspicious.asSequence()).iterator()
        var newCompileResult: List<CompileResult> = emptyList()
        while (allIter.hasNext()) {
            val next = allIter.next()
            val backup = result.prog.deepCopy()
            logger.trace { "remove function $next" }
            result.withValidate { removeFunction(next) }
            newCompileResult = compile(result)
            if (newCompileResult != initCompileResult) {
                result = ProgramWithRemovedDecl(backup)
            }
        }

        return result to newCompileResult
    }

    override fun minimize(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>
    ): Pair<IrProgram, List<CompileResult>> {
        return removeUnrelatedFunctions(initProg, initCompileResult)
    }
}