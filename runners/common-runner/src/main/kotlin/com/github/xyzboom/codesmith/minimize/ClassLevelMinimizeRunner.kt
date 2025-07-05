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

    private val logger = KotlinLogging.logger {}

    class ProgramWithReachableTag(private val prog: IrProgram) {
        val reachableTag: HashSet<IrClassDeclaration> = HashSet()


    }

    fun splitClasses(prog: IrProgram) {


    }

    class ProgramWithRemovedClasses(val prog: IrProgram) : IrProgram by prog {
        val removedClasses = mutableSetOf<IrClassDeclaration>()

        /**
         * If a replaceWith class is never used, no need to add it into program.
         */
        val usedClassReplaceWith = hashSetOf<String>()
        private inner class DelegateMutableMap(val ori: MutableMap<String, IrClassDeclaration>)
            : MutableMap<String, IrClassDeclaration> by ori {
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

    fun ProgramWithRemovedClasses.withValidate(block: ProgramWithRemovedClasses.() -> Unit) {
        block()
        IrValidator().validate(this).throwOnErrors()
    }

    override fun minimize(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>
    ): IrProgram {
        val suspicious = mutableListOf<IrClassDeclaration>()
        val normal = mutableListOf<IrClassDeclaration>()
        classes@ for (clazz in initProg.classes) {
            if (initCompileResult.mayRelatedWith(clazz.name)) {
                suspicious.add(clazz)
            } else {
                for (f in clazz.functions) {
                    if (initCompileResult.mayRelatedWith(f.name)) {
                        suspicious.add(clazz)
                        continue@classes
                    }
                }
                normal.add(clazz)
            }
        }
        val replaceWith = mutableSetOf<String>()
        val progNew = initProg.deepCopy()
        var result = ProgramWithRemovedClasses(progNew)
        val canNotBeRemovedClasses = mutableSetOf<String>()
        val canBeRemovedClasses = mutableSetOf<String>()
        run tryRemoveAllNormal@{
            val toBeRemove = result.classes.filter { it.name in normal.map { it1 -> it1.name } }
            for (c in toBeRemove) {
                result.withValidate { removeClass(c, nextReplacementName()) }
            }
            val newCompileResult = compile(result)
            if (newCompileResult != initCompileResult) {
                // rollback
                result = ProgramWithRemovedClasses(progNew)
            } else {
                replaceWith.addAll(normal.map { it.name })
                canBeRemovedClasses.addAll(normal.map { it.name })
            }
        }

        while (canNotBeRemovedClasses.size + canBeRemovedClasses.size != initProg.classes.size) {
            val clazz = (normal + suspicious).firstOrNull {
                it.name !in canNotBeRemovedClasses && it.name !in canBeRemovedClasses && it.name !in replaceWith
            } ?: break
            logger.trace { "try to remove ${clazz.render()}" }
            val backup = result.prog.deepCopy()
            val oriRemoved = HashSet(result.removedClasses)
            val oriReplaced = HashMap(result.classReplaceWith)
            result.removeClass(clazz, nextReplacementName())
            if (result.classes.isEmpty()) {
                val rollback = ProgramWithRemovedClasses(backup).apply {
                    removedClasses.addAll(oriRemoved)
                    classReplaceWith.putAll(oriReplaced)
                }
                result = rollback
                canNotBeRemovedClasses.add(clazz.name)
                continue
            }
            val newCompileResult = compile(result)
            if (newCompileResult != initCompileResult) {
                logger.trace { "${clazz.render()} is suspicious" }
                val rollback = ProgramWithRemovedClasses(backup).apply {
                    removedClasses.addAll(oriRemoved)
                    classReplaceWith.putAll(oriReplaced)
                }
                result = rollback
                canNotBeRemovedClasses.add(clazz.name)
            } else {
                replaceWith.addAll(result.removedClasses.map { it.name })
                canBeRemovedClasses.addAll(result.removedClasses.map { it.name })
            }
        }

        return result
    }
}