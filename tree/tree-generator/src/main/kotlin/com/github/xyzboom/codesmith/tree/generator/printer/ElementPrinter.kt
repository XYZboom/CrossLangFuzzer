package com.github.xyzboom.codesmith.tree.generator.printer

import com.github.xyzboom.codesmith.tree.generator.TreeBuilder
import com.github.xyzboom.codesmith.tree.generator.irTransformerType
import com.github.xyzboom.codesmith.tree.generator.model.Element
import com.github.xyzboom.codesmith.tree.generator.model.Field
import com.github.xyzboom.codesmith.tree.generator.irVisitorType
import com.github.xyzboom.codesmith.tree.generator.irVisitorVoidType
import org.jetbrains.kotlin.generators.tree.AbstractElementPrinter
import org.jetbrains.kotlin.generators.tree.AbstractFieldPrinter
import org.jetbrains.kotlin.generators.tree.TypeRefWithNullability
import org.jetbrains.kotlin.generators.tree.TypeVariable
import org.jetbrains.kotlin.generators.tree.printer.ImportCollectingPrinter
import org.jetbrains.kotlin.generators.tree.printer.printAcceptChildrenMethod
import org.jetbrains.kotlin.generators.tree.printer.printAcceptChildrenVoidMethod
import org.jetbrains.kotlin.generators.tree.printer.printAcceptMethod
import org.jetbrains.kotlin.generators.tree.printer.printAcceptVoidMethod
import org.jetbrains.kotlin.generators.tree.printer.printTransformChildrenMethod
import org.jetbrains.kotlin.generators.tree.printer.printTransformMethod

internal class ElementPrinter(printer: ImportCollectingPrinter) : AbstractElementPrinter<Element, Field>(printer) {

    override fun makeFieldPrinter(printer: ImportCollectingPrinter) = object : AbstractFieldPrinter<Field>(printer) {
        override fun forceMutable(field: Field) = field.isMutable
    }

    override fun ImportCollectingPrinter.printAdditionalMethods(element: Element) {
        val kind = element.kind ?: error("Expected non-null element kind")
        with(element) {
            val treeName = "IR"
            printAcceptMethod(element, irVisitorType, hasImplementation = true, treeName = treeName)

            printTransformMethod(
                element = element,
                transformerClass = irTransformerType,
                implementation = "transformer.transform${element.name}(this, data)",
                returnType = TypeVariable("E", listOf(TreeBuilder.rootElement)),
                treeName = treeName,
            )

            fun Field.replaceDeclaration(
                override: Boolean,
                overriddenType: TypeRefWithNullability? = null,
                forceNullable: Boolean = false,
            ) {
                println()
/*                if (name == "source") {
                    println("@", irImplementationDetailType.render())
                }*/
                replaceFunctionDeclaration(this, override, kind, overriddenType, forceNullable)
                println()
            }

            allFields.filter { it.withReplace }.forEach { field ->
                val clazz = field.typeRef.copy(nullable = false)
                val overriddenClasses = field.overriddenFields.map { it -> it.typeRef.copy(nullable = false) }.toSet()

                val override = clazz in overriddenClasses /*&& !(field.name == "source" && element in elementsWithReplaceSource)*/
                field.replaceDeclaration(override, forceNullable = field.receiveNullableTypeInReplace)

                for (overriddenClass in overriddenClasses - clazz) {
                    field.replaceDeclaration(true, overriddenType = overriddenClass)
                }
            }

            for (field in allFields) {
                if (!field.withTransform) continue
                println()
                transformFunctionDeclaration(
                    field = field,
                    returnType = element.withSelfArgs(),
                    override = field.overriddenFields.any { it.withTransform },
                    implementationKind = kind
                )
                println()
            }
            if (needTransformOtherChildren) {
                println()
                transformOtherChildrenFunctionDeclaration(
                    element.withSelfArgs(),
                    override = element.elementParents.any { it.element.needTransformOtherChildren },
                    kind,
                )
                println()
            }

            if (element.isRootElement) {
                println()
                printAcceptVoidMethod(irVisitorVoidType, treeName)
                printAcceptChildrenMethod(
                    element = element,
                    visitorClass = irVisitorType,
                    visitorResultType = TypeVariable("R"),
                )
                println()
                println()
                printAcceptChildrenVoidMethod(irVisitorVoidType)
                printTransformChildrenMethod(
                    element = element,
                    transformerClass = irTransformerType,
                    returnType = TreeBuilder.rootElement,
                )
                println()
            }

        }
    }
}