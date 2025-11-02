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
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeContainer
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
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

        fun newClassFromClosure(
            oriClass: IrClassDeclaration, prog: IrProgram, elements: Set<IrElement>
        ): IrClassDeclaration {
            val oriSuper = oriClass.superType
            var newSuper: IrType? = null
            val intfs = mutableMapOf<IrClassDeclaration, IrType>()

            //<editor-fold desc="HandleSuper">
            if (oriSuper !is IrClassifier) {
                newSuper = oriSuper
            } else {
                val superClass = oriSuper.classDecl
                val superTypeOf = superTypeOf(oriClass, superClass)
                if (superTypeOf in elements) {
                    newSuper = oriSuper
                } else {
                    superClass.traverseSuper {
                        if (it !is IrClassifier || it.classDecl !in elements) {
                            return@traverseSuper true
                        }
                        val superSuperClass = it.classDecl
                        val superTypeOf = superTypeOf(oriClass, superSuperClass)
                        if (superTypeOf in elements) {
                            if (newSuper == null && superSuperClass.classKind != ClassKind.INTERFACE) {
                                newSuper = it
                            }
                            if (superSuperClass.classKind == ClassKind.INTERFACE
                                && intfs[superSuperClass] == null
                            ) {
                                intfs[superSuperClass] = it
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
                    intfs[superClass] = intf
                } else {
                    superClass.traverseSuper {
                        if (it.classKind == ClassKind.INTERFACE && it is IrClassifier
                            && intfs[it.classDecl] == null && it.classDecl in elements
                        ) {
                            intfs[it.classDecl] = it
                        }
                        true
                    }
                }
            }

            return buildClassDeclaration {
                name = oriClass.name
                language = oriClass.language
                for (f in oriClass.functions) {
                    if (f in elements) {
                        functions.add(f)
                    }
                }
                typeParameters.addAll(oriClass.typeParameters)
                classKind = oriClass.classKind
                superType = newSuper
                // todo handle super argument
                allSuperTypeArguments = oriClass.allSuperTypeArguments.toMutableMap() // copy it
                implementedTypes.addAll(intfs.values)
            }
        }

        fun newProg(prog: IrProgram, elements: Set<IrElement>): IrProgram {
            return buildProgram {
                for (clazz in prog.classes) {
                    if (clazz !in elements) {
                        continue
                    }
                    val newClass = newClassFromClosure(clazz, prog, elements)
                    classes.add(newClass)
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
            val fileContent = IrProgramPrinter().printToSingle(newProg)
            val stringCacheResult = resultStringCache[fileContent]
            if (stringCacheResult != null) {
                return@DDMin stringCacheResult
            }
            lastResult = compile(newProg, compilers)
            compileTimes++
            // todo:
            // 1. 类的引用解析
            // 2. 函数重写
            (lastResult == initCompileResult).also { result ->
                resultSetCache[combine] = result
                resultStringCache[fileContent] = result
            }
        }
        val resultClosure = ddmin.execute(closures)
        val resultElements = resultClosure.reduce { a, b -> a + b }.elements
        logger.info { "ddmin compile times $compileTimes" }
        return newProg(initProg, resultElements) to lastResult
    }
}
