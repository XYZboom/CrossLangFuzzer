package io.github.xyzboom.crosslangfuzzer.tree.generator.printer

import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Element
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Field
import io.github.xyzboom.crosslangfuzzer.tree.generator.irVisitorType
import org.jetbrains.kotlin.generators.tree.AbstractElementPrinter
import org.jetbrains.kotlin.generators.tree.AbstractFieldPrinter
import org.jetbrains.kotlin.generators.tree.printer.ImportCollectingPrinter
import org.jetbrains.kotlin.generators.tree.printer.printAcceptMethod

internal class ElementPrinter(printer: ImportCollectingPrinter) : AbstractElementPrinter<Element, Field>(printer) {

    override fun makeFieldPrinter(printer: ImportCollectingPrinter) = object : AbstractFieldPrinter<Field>(printer) {}

    override fun ImportCollectingPrinter.printAdditionalMethods(element: Element) {
        val kind = element.kind ?: error("Expected non-null element kind")
        with(element) {
            val treeName = "IR"
            printAcceptMethod(element, irVisitorType, hasImplementation = true, treeName = treeName)

            /*printTransformMethod(
                element = element,
                transformerClass = firTransformerType,
                implementation = "transformer.transform${element.name}(this, data)",
                returnType = TypeVariable("E", listOf(FirTree.rootElement)),
                treeName = treeName,
            )

            fun Field.replaceDeclaration(
                override: Boolean,
                overriddenType: TypeRefWithNullability? = null,
                forceNullable: Boolean = false,
            ) {
                println()
                if (name == "source") {
                    println("@", firImplementationDetailType.render())
                }
                replaceFunctionDeclaration(this, override, kind, overriddenType, forceNullable)
                println()
            }

            allFields.filter { it.withReplace }.forEach { field ->
                val clazz = field.typeRef.copy(nullable = false)
                val overriddenClasses = field.overriddenFields.map { it -> it.typeRef.copy(nullable = false) }.toSet()

                val override = clazz in overriddenClasses && !(field.name == "source" && element in elementsWithReplaceSource)
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
                printAcceptVoidMethod(firVisitorVoidType, treeName)
                printAcceptChildrenMethod(
                    element = element,
                    visitorClass = firVisitorType,
                    visitorResultType = TypeVariable("R"),
                )
                println()
                println()
                printAcceptChildrenVoidMethod(firVisitorVoidType)
                printTransformChildrenMethod(
                    element = element,
                    transformerClass = firTransformerType,
                    returnType = FirTree.rootElement,
                )
                println()
            }*/
        }
    }
}