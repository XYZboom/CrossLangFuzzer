package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.algorithm.DDMin
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.traverseSuper
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.types.*
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

    private fun Set<IrElement>.minimize(): Set<IrElement> {
        val result = LinkedHashSet(this)
        for (ele in this) {
            if (ele is SuperTypeOf) {
                if (ele.clazz !in result || ele.superClass !in result) {
                    result.remove(ele)
                }
            }
        }
        return result
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
            val minCombine = combine.minimize()
            val cacheMinCombine = resultSetCache[minCombine]
            if (cacheMinCombine != null) {
                return@DDMin cacheMinCombine
            }
            val newProg = Closure2Program(initProg).newProg(combine)
            newProg.deepCopy() // verify dependency
            val fileContent = IrProgramPrinter().printToSingle(newProg)
            val stringCacheResult = resultStringCache[fileContent]
            if (stringCacheResult != null) {
                return@DDMin stringCacheResult
            }
            val compileResult = compile(newProg, compilers)
            compileTimes++
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
        return Closure2Program(initProg).newProg(resultElements) to lastResult
    }
}
