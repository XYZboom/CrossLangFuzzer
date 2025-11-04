package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.algorithm.DDMin
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.mutableMapOf

class MinimizeRunner2(
    compilerRunner: ICompilerRunner
) : IMinimizeRunner, ICompilerRunner by compilerRunner {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun List<CompileResult>.mayRelatedWith(name: String): Boolean {
        return any {
            it.javaResult?.contains(name) == true || it.majorResult?.contains(name) == true
        }
    }

    fun Set<GroupedElement.FunctionGroup>.sortWithRelated(
        compileResult: List<CompileResult>
    ): List<GroupedElement.FunctionGroup> {
        return sortedWith { a, b ->
            val aRelated = if (compileResult.mayRelatedWith(a.name)) 1 else 0
            val bRelated = if (compileResult.mayRelatedWith(b.name)) 1 else 0
            return@sortedWith aRelated - bRelated
        }
    }

    override fun minimize(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>
    ): Pair<IrProgram, List<CompileResult>> {
        var group = GroupedElement.groupElements(initProg)
        var lastResult = initCompileResult
        val groupCache = mutableMapOf<GroupedElement, Boolean>()
        val stringCache = mutableMapOf<String, Boolean>()

        fun cacheOrCompile(newGroup: GroupedElement): Boolean {
            val groupCacheResult = groupCache[newGroup]
            if (groupCacheResult != null) {
                return groupCacheResult
            }
            val makeSuperValid = newGroup.makeSuperTypeOfValid()
            val groupCacheResult2 = groupCache[makeSuperValid]
            if (groupCacheResult2 != null) {
                return groupCacheResult2
            }
            val newProg = newGroup.toProgram().deepCopy() // todo verify only remove this
            val fileContent = IrProgramPrinter().printToSingle(newProg)
            val stringCacheResult = stringCache[fileContent]
            if (stringCacheResult != null) {
                return stringCacheResult
            }
            val compileResult = compile(newProg, compilers)
            return (compileResult == initCompileResult).also { result ->
                groupCache[newGroup] = result
                stringCache[fileContent] = result
                if (result) {
                    lastResult = compileResult
                }
            }
        }

        fun reduceClasses(initGroup: GroupedElement): GroupedElement {
            val classes = initGroup.classes.toList()
            val ddmin = DDMin {
                val newGroup = initGroup.copy(classes = it.toSet())
                return@DDMin cacheOrCompile(newGroup)
            }
            val remainClasses = ddmin.execute(classes)
            return initGroup.copy(classes = remainClasses.toSet())
        }

        fun reduceInheritance(initGroup: GroupedElement): GroupedElement {
            val elements = (initGroup.superTypeOfs + initGroup.classes).toList()
            val ddmin = DDMin<IrElement> {
                val newGroup = initGroup.copy(
                    classes = it.filterIsInstance<IrClassDeclaration>().toSet(),
                    superTypeOfs = it.filterIsInstance<GroupedElement.SuperTypeOf>().toSet()
                )
                return@DDMin cacheOrCompile(newGroup)
            }
            val remainElements = ddmin.execute(elements)
            return initGroup.copy(
                classes = remainElements.filterIsInstance<IrClassDeclaration>().toSet(),
                superTypeOfs = remainElements.filterIsInstance<GroupedElement.SuperTypeOf>().toSet()
            )
        }

        fun reduceFunctions(initGroup: GroupedElement): GroupedElement {
            val functions = initGroup.functions.sortWithRelated(initCompileResult)
            val ddmin = DDMin {
                val newGroup = initGroup.copy(functions = it.toSet())
                return@DDMin cacheOrCompile(newGroup)
            }
            val remainFunctions = ddmin.execute(functions)
            return initGroup.copy(functions = remainFunctions.toSet())
        }

        fun reduceTypeParametersAndParameters(initGroup: GroupedElement): GroupedElement {
            val elements = (initGroup.typeParameters + initGroup.parameters).toList()
            val ddmin = DDMin<IrElement> {
                val newGroup = initGroup.copy(
                    typeParameters = it.filterIsInstance<GroupedElement.TypeParameter>().toSet(),
                    parameters = it.filterIsInstance<GroupedElement.Parameter>().toSet()
                )
                return@DDMin cacheOrCompile(newGroup)
            }
            val remainElements = ddmin.execute(elements)
            return initGroup.copy(
                typeParameters = remainElements.filterIsInstance<GroupedElement.TypeParameter>().toSet(),
                parameters = remainElements.filterIsInstance<GroupedElement.Parameter>().toSet()
            )
        }

        val stages = listOf(
            ::reduceClasses,
            ::reduceInheritance,
            ::reduceFunctions,
            ::reduceTypeParametersAndParameters
        )
        for (stage in stages) {
            group = stage(group)
        }
        return group.toProgram() to lastResult
    }
}
