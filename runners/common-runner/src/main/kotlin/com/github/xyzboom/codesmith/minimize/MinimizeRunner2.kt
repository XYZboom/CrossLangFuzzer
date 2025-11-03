package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.algorithm.DDMin
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.traverseSuper
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeContainer
import com.github.xyzboom.codesmith.ir.types.builder.buildDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import io.github.oshai.kotlinlogging.KotlinLogging

class MinimizeRunner2(
    compilerRunner: ICompilerRunner
) : IMinimizeRunner, ICompilerRunner by compilerRunner {
    companion object {
        private val logger = KotlinLogging.logger {}
        fun IrProgram.buildClosure(elements: Set<IrElement>): Closure {
            return Closure(this, elements)
        }

        fun superTypeOf(child: IrClassDeclaration, parent: IrClassDeclaration): SuperTypeOf {
            return SuperTypeOf(child, parent)
        }

        fun IrProgram.buildClosures(): Set<Closure> {
            val result = mutableMapOf<IrElement, Closure>()
            for (clazz in classes) {
                val classClosure = clazz.closureOf(cache = result)
                result[clazz] = buildClosure(classClosure)
                clazz.traverseSuper(
                    enter = { superType ->
                        if (superType is IrClassifier) {
                            val superTypeOf = superTypeOf(clazz, superType.classDecl)
                            val closure = superTypeOf.closureOf(cache = result)
                            result[superTypeOf] = buildClosure(closure)
                        }
                    }
                )
                for (function in clazz.functions) {
                    val functionClosure = function.closureOf(cache = result)
                    result[function] = buildClosure(functionClosure)
                }
            }
            return result.values.toSet()
        }

        context(containingProg: IrProgram)
        fun SuperTypeOf.closureOf(
            result: MutableSet<IrElement> = mutableSetOf(),
            cache: MutableMap<IrElement, Closure> = mutableMapOf()
        ): MutableSet<IrElement> {
            result.add(this)
            result.addAll(clazz.closureOf(result, cache))
            return result
        }

        context(containingProg: IrProgram)
        fun IrDeclaration.closureOf(
            result: MutableSet<IrElement> = mutableSetOf(),
            cache: MutableMap<IrElement, Closure> = mutableMapOf()
        ): MutableSet<IrElement> {
            if (cache.containsKey(this)) {
                return result.also { it.addAll(cache[this]!!.elements) }
            }
            when (this) {
                is IrClassDeclaration -> result.add(this)

                is IrFunctionDeclaration -> {
                    result.add(this)
                    val containingClass = containingProg.classes.firstOrNull { it.name == containingClassName }
                    if (containingClass != null) {
                        result.add(containingClass)
                    }
                }

                else -> TODO("${this::class.simpleName} not yet implemented")
            }
            return result
        }

        context(containingProg: IrProgram)
        fun IrType.closureOf(
            result: MutableSet<IrElement> = mutableSetOf(),
            cache: MutableMap<IrElement, Closure> = mutableMapOf()
        ): MutableSet<IrElement> {
            result.add(this)
            when (this) {
                is IrTypeContainer -> innerType.closureOf(result, cache)
                is IrSimpleClassifier -> classDecl.closureOf(result, cache)
                is IrParameterizedClassifier -> {
                    classDecl.closureOf(result, cache)
                    for ((_, pair) in this.arguments) {
                        val (_, typeArg) = pair
                        typeArg?.let { result.add(it) }
                    }
                }
            }
            return result
        }

        context(containingProg: IrProgram)
        fun IrElement.closureOf(
            result: MutableSet<IrElement> = mutableSetOf(),
            cache: MutableMap<IrElement, Closure> = mutableMapOf()
        ): MutableSet<IrElement> {
            result.add(this)
            when (this) {
                is IrDeclaration -> closureOf(result, cache)
                is IrType -> closureOf(result, cache)
                else -> {}
            }
            return result
        }

        fun newFunctionFromClosure(
            oriFunc: IrFunctionDeclaration, prog: IrProgram, elements: Set<IrElement>
        ) {

        }

        fun newTypeFromClosure(
            oriType: IrType,
            old2NewClasses: Map<IrClassDeclaration, IrClassDeclaration>,
            elements: Set<IrElement>,
        ): IrType {
            return when (oriType) {
                is IrBuiltInType -> oriType
                is IrNullableType -> buildNullableType {
                    innerType = newTypeFromClosure(oriType, old2NewClasses, elements)
                }

                is IrPlatformType -> buildPlatformType {
                    innerType = newTypeFromClosure(oriType, old2NewClasses, elements)
                }

                is IrDefinitelyNotNullType -> {
                    val newInnerType = newTypeFromClosure(oriType, old2NewClasses, elements)
                    if (newInnerType is IrTypeParameter) {
                        buildDefinitelyNotNullType { innerType = newInnerType }
                    } else newInnerType
                }

                is IrClassifier -> {
                    val oriClass = oriType.classDecl
                    if (oriClass !in elements) {
                        return IrAny // todo replace directly may cause new error
                    }
                    val newClass = old2NewClasses[oriClass]!!
                    val newType = newClass.type
                    if (oriType is IrParameterizedClassifier) {
                        for ((typeParamName, pair) in oriType.arguments) {
                            if (newType is IrParameterizedClassifier) {
                                val typeParam = pair.first
                                val typeArg = pair.second
                                val newTypeArg = if (typeArg != null) {
                                    newTypeFromClosure(typeArg, old2NewClasses, elements)
                                } else null
                                // todo type parameter may not in elements
                                newType.arguments[typeParamName] = typeParam to newTypeArg
                            }
                        }
                    }
                    newType
                }

                is IrTypeParameter -> {
                    oriType // todo type parameter may not in elements
                }

                else -> throw IllegalArgumentException("No such IrType ${this::class.simpleName}")
            }
        }

        fun firstStageNewClassFromClosure(
            oriClass: IrClassDeclaration,
            newClass: IrClassDeclaration,
            ori2NewMap: Map<IrClassDeclaration, IrClassDeclaration>,
            elements: Set<IrElement>
        ) {
            val oriSuper = oriClass.superType
            var newSuper: IrType? = null
            val intfs = mutableMapOf<IrClassDeclaration, IrType>()

            fun IrType.toNew(): IrType {
                return newTypeFromClosure(
                    this,
                    ori2NewMap,
                    elements
                )
            }

            //<editor-fold desc="HandleSuper">
            if (oriSuper !is IrClassifier) {
                newSuper = oriSuper?.toNew()
            } else {
                val superClass = oriSuper.classDecl
                val superTypeOf = superTypeOf(oriClass, superClass)
                if (superTypeOf in elements) {
                    newSuper = ori2NewMap[superClass]!!.type.toNew()
                } else {
                    superClass.traverseSuper {
                        if (it !is IrClassifier || it.classDecl !in elements) {
                            return@traverseSuper true
                        }
                        val superSuperClass = it.classDecl
                        val superTypeOf = superTypeOf(oriClass, superSuperClass)
                        if (superTypeOf in elements) {
                            if (newSuper == null && superSuperClass.classKind != ClassKind.INTERFACE) {
                                newSuper = it.toNew()
                            }
                            if (superSuperClass.classKind == ClassKind.INTERFACE
                                && intfs[superSuperClass] == null
                            ) {
                                intfs[superSuperClass] = it.toNew()
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
                if (superTypeOf in elements && superClass in elements) {
                    intfs[superClass] = intf.toNew()
                } else {
                    superClass.traverseSuper {
                        if (it.classKind == ClassKind.INTERFACE && it is IrClassifier
                            && intfs[it.classDecl] == null && it.classDecl in elements
                        ) {
                            intfs[it.classDecl] = it.toNew()
                        }
                        true
                    }
                }
            }

            newClass.apply {
                typeParameters.addAll(oriClass.typeParameters)
                superType = newSuper
                // todo handle super argument
                allSuperTypeArguments = oriClass.allSuperTypeArguments.toMutableMap() // copy it
                implementedTypes.addAll(intfs.values)
                for (f in oriClass.functions) {
                    if (f in elements) {
                        functions.add(f)
                    }
                }
            }
        }

        fun newClassSkeleton(oriClass: IrClassDeclaration): IrClassDeclaration {
            return buildClassDeclaration {
                name = oriClass.name
                language = oriClass.language
                classKind = oriClass.classKind
            }
        }

        fun newProg(prog: IrProgram, elements: Set<IrElement>): IrProgram {
            return buildProgram().apply {
                val classMap = mutableMapOf<IrClassDeclaration, IrClassDeclaration>()
                for (clazz in prog.classes) {
                    if (clazz !in elements) {
                        continue
                    }
                    val newClass = newClassSkeleton(clazz)
                    classes.add(newClass)
                    classMap[clazz] = newClass
                }
                for (clazz in prog.classes) {
                    if (clazz !in elements) {
                        continue
                    }
                    val newClass = classMap[clazz]!!
                    firstStageNewClassFromClosure(clazz, newClass, classMap, elements)
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

    data class SuperTypeOf(val clazz: IrClassDeclaration, val superClass: IrClassDeclaration) : IEmptyElement

    class Closure(
        val containingProg: IrProgram,
        val elements: Set<IrElement>
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Closure) return false

            if (containingProg !== other.containingProg) return false
            if (elements != other.elements) return false

            return true
        }

        override fun hashCode(): Int {
            var result = containingProg.hashCode()
            result = 31 * result + elements.hashCode()
            return result
        }

        override fun toString(): String {
            return "Closure(elements=$elements)"
        }

        operator fun plus(other: Closure): Closure {
            require(other.containingProg === this.containingProg)
            return Closure(containingProg, elements + other.elements)
        }
    }

    override fun minimize(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>
    ): Pair<IrProgram, List<CompileResult>> {
        val closures = initProg.buildClosures().toList().sortedBy { it.elements.size }
        var lastResult = initCompileResult
        val resultSetCache = mutableMapOf<Set<IrElement>, Boolean>()
        val resultStringCache = mutableMapOf<String, Boolean>()
        var compileTimes = 0
        val ddmin = DDMin<Closure> {
            val combine = it.reduce { a, b -> a + b }.elements
            val cacheResult = resultSetCache[combine]
            if (cacheResult != null) {
                return@DDMin cacheResult
            }
            val newProg = newProg(initProg, combine)
            newProg.deepCopy() // verify dependency
            val fileContent = IrProgramPrinter().printToSingle(newProg)
            val stringCacheResult = resultStringCache[fileContent]
            if (stringCacheResult != null) {
                return@DDMin stringCacheResult
            }
            val compileResult = compile(newProg, compilers)
            compileTimes++
            // todo:
            // 1. 类的引用解析
            // 2. 函数重写
            (compileResult == initCompileResult).also { result ->
                resultSetCache[combine] = result
                resultStringCache[fileContent] = result
                if (result) {
                    lastResult = compileResult
                }
            }
        }
        val resultClosure = ddmin.execute(closures)
        val resultElements = resultClosure.reduce { a, b -> a + b }.elements
        logger.info { "ddmin compile times $compileTimes" }
        return newProg(initProg, resultElements) to lastResult
    }
}
