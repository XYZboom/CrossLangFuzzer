/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.github.xyzboom.codesmith.tree.generator.printer

import com.github.xyzboom.codesmith.tree.generator.irImplementationDetailType
import com.github.xyzboom.codesmith.tree.generator.irTransformerType
import com.github.xyzboom.codesmith.tree.generator.irVisitorType
import com.github.xyzboom.codesmith.tree.generator.model.*
import com.github.xyzboom.codesmith.tree.generator.model.ListField
import com.github.xyzboom.codesmith.tree.generator.pureAbstractElementType
import com.github.xyzboom.codesmith.tree.generator.transformInPlaceImport
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.generators.tree.*
import org.jetbrains.kotlin.generators.tree.printer.ImportCollectingPrinter
import org.jetbrains.kotlin.generators.tree.printer.call
import org.jetbrains.kotlin.generators.tree.printer.printAcceptChildrenMethod
import org.jetbrains.kotlin.generators.tree.printer.printTransformChildrenMethod
import org.jetbrains.kotlin.generators.util.printBlock

private class ImplementationFieldPrinter(printer: ImportCollectingPrinter) : AbstractFieldPrinter<Field>(printer) {
    override fun forceMutable(field: Field): Boolean =
        field.isMutable && (field !is ListField || field.isMutableOrEmptyList)

    override fun actualTypeOfField(field: Field) = field.getMutableType()

    override val wrapOptInAnnotations
        get() = true
}

internal class ImplementationPrinter(
    printer: ImportCollectingPrinter
) : AbstractImplementationPrinter<Implementation, Element, Field>(printer) {

    override val implementationOptInAnnotation: ClassRef<*>
        get() = irImplementationDetailType

    override fun getPureAbstractElementType(implementation: Implementation): ClassRef<*> =
        pureAbstractElementType

    override fun makeFieldPrinter(printer: ImportCollectingPrinter): AbstractFieldPrinter<Field> =
        ImplementationFieldPrinter(printer)

    private inline fun Implementation.anyParent(condition: (Element) -> Boolean): Boolean {
        val visited = mutableSetOf<Element>()
        val stack = this.allParents.toMutableList()

        while (stack.isNotEmpty()) {
            val next = stack.removeLast()

            when {
                !visited.add(next) -> continue
                condition(next) -> return true
                else -> stack += next.allParents
            }
        }

        return false
    }

    override fun ImportCollectingPrinter.printAdditionalMethods(implementation: Implementation) {
        fun Field.transform() {
            when (this) {
                is SimpleField ->
                    println("$name = ${name}${call()}transform(transformer, data)")

                is ListField -> {
                    addImport(transformInPlaceImport)
                    println("${name}.transformInplace(transformer, data)")
                }
            }
        }
        with(implementation) {
            val isInterface = kind == ImplementationKind.Interface || kind == ImplementationKind.SealedInterface
            val isAbstract = kind == ImplementationKind.AbstractClass || kind == ImplementationKind.SealedClass
            val bindingCalls = element.allFields.filter {
                it.withBindThis && it.hasSymbolType && it !is ListField
            }.takeIf {
                it.isNotEmpty() && !isInterface && !isAbstract
            }.orEmpty()

            val customCalls = fieldsInConstructor.filter { it.customInitializationCall != null }
            if (bindingCalls.isNotEmpty() || customCalls.isNotEmpty()) {
                println()
                println("init {")
                withIndent {
                    for (symbolField in bindingCalls) {
                        println("${symbolField.name}${symbolField.call()}bind(this)")
                    }

                    for (customCall in customCalls) {
                        addAllImports(customCall.arbitraryImportables)
                        println("${customCall.name} = ${customCall.customInitializationCall}")
                    }
                }
                println("}")
            }

            fun Field.acceptString(): String = "${name}${call()}accept(visitor, data)"

            if (hasAcceptChildrenMethod) {
                printAcceptChildrenMethod(this, irVisitorType, TypeVariable("R"), override = true)
                print(" {")

                val walkableFields = walkableChildren
                if (walkableFields.isNotEmpty()) {
                    println()
                    withIndent {
                        for (field in walkableFields) {
                            when (field) {
                                is SimpleField -> {
                                    println(field.acceptString())
                                }

                                is ListField -> {
                                    println(field.name, field.call(), "forEach { it.accept(visitor, data) }")
                                }
                            }
                        }
                    }
                }
                println("}")
            }

            if (hasTransformChildrenMethod) {
                printTransformChildrenMethod(
                    implementation,
                    irTransformerType,
                    implementation,
                    modality = Modality.ABSTRACT.takeIf { isAbstract },
                    override = true,
                )
                if (!isInterface && !isAbstract) {
                    printBlock {
                        for (field in transformableChildren) {
                            when {
                                field.withTransform -> {
                                    if (!(element.needTransformOtherChildren && field.needTransformInOtherChildren)) {
                                        println("transform${field.name.replaceFirstChar(Char::uppercaseChar)}(transformer, data)")
                                    }
                                }

                                !element.needTransformOtherChildren -> {
                                    field.transform()
                                }

                                else -> {}
                            }
                        }
                        if (element.needTransformOtherChildren) {
                            println("transformOtherChildren(transformer, data)")
                        }
                        println("return this")
                    }
                }
            }

            for (field in allFields) {
                if (!field.withTransform) continue
                println()
                transformFunctionDeclaration(field, implementation, override = true, kind!!)
                if (isInterface || isAbstract) {
                    println()
                    continue
                }
                printBlock {
                    if (field.isMutable && field.containsElement) {
                        field.transform()
                    }
                    println("return this")
                }
            }

            if (element.needTransformOtherChildren) {
                println()
                transformOtherChildrenFunctionDeclaration(implementation, override = true, kind!!)
                if (isInterface || isAbstract) {
                    println()
                } else {
                    printBlock {
                        for (field in allFields) {
                            if (!field.isMutable || !field.containsElement || field.name == "subjectVariable") continue
                            if (!field.withTransform) {
                                field.transform()
                            }
                            if (field.needTransformInOtherChildren) {
                                println("transform${field.name.replaceFirstChar(Char::uppercaseChar)}(transformer, data)")
                            }
                        }
                        println("return this")
                    }
                }
            }

            fun generateReplace(
                field: Field,
                overridenType: TypeRefWithNullability? = null,
                forceNullable: Boolean = false,
                body: () -> Unit,
            ) {
                println()
                /*if (field.name == "source") {
                    println("@${firImplementationDetailType.render()}")
                }*/
                replaceFunctionDeclaration(field, override = true, kind!!, overridenType, forceNullable)
                if (isInterface || isAbstract) {
                    println()
                    return
                }
                print(" {")
                if (!field.isMutable) {
                    println("}")
                    return
                }
                println()
                withIndent {
                    body()
                }
                println("}")
            }

            for (field in allFields.filter { it.withReplace }) {
                val capitalizedFieldName = field.name.replaceFirstChar(Char::uppercaseChar)
                val newValue = "new$capitalizedFieldName"
                generateReplace(field, forceNullable = field.receiveNullableTypeInReplace) {
                    when {
                        field.implementationDefaultStrategy!!.withGetter -> {}

                        field is ListField && !field.isMutableOrEmptyList -> {
                            println("if (${field.name} === $newValue) return")
                            println("${field.name}.clear()")
                            println("${field.name}.addAll($newValue)")
                        }

                        else -> {
                            if (field.receiveNullableTypeInReplace && !field.typeRef.nullable) {
                                println("require($newValue != null)")
                            }
                            print("${field.name} = $newValue")
                            /*if (field is ListField && field.isMutableOrEmptyList) {
                                addImport(toMutableOrEmptyImport)
                                print(".toMutableOrEmpty()")
                            }*/
                            println()
                        }
                    }
                }

                val additionalOverriddenTypes =
                    field.overriddenFields.map { it.typeRef.copy(nullable = false) }.toSet() - field.typeRef.copy(
                        nullable = false
                    )
                for (overriddenType in additionalOverriddenTypes) {
                    generateReplace(field, overriddenType) {
                        println("require($newValue is ${field.typeRef.render()})")
                        println("replace$capitalizedFieldName($newValue)")
                    }
                }
            }
        }
    }
}

private val Field.hasSymbolType: Boolean
    get() = (typeRef as? ClassRef<*>)?.simpleName?.contains("Symbol") ?: false
