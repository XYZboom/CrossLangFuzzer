package io.github.xyzboom.crosslangfuzzer.tree.generator

import io.github.xyzboom.crosslangfuzzer.tree.generator.printer.BuilderPrinter
import io.github.xyzboom.crosslangfuzzer.tree.generator.printer.DefaultVisitorVoidPrinter
import io.github.xyzboom.crosslangfuzzer.tree.generator.printer.ElementPrinter
import io.github.xyzboom.crosslangfuzzer.tree.generator.printer.ImplementationPrinter
import io.github.xyzboom.crosslangfuzzer.tree.generator.printer.TransformerPrinter
import io.github.xyzboom.crosslangfuzzer.tree.generator.printer.VisitorPrinter
import io.github.xyzboom.crosslangfuzzer.tree.generator.printer.VisitorVoidPrinter
import io.github.xyzboom.crosslangfuzzer.tree.generator.utils.bind
import org.jetbrains.kotlin.generators.tree.InterfaceAndAbstractClassConfigurator
import org.jetbrains.kotlin.generators.tree.printer.TreeGenerator
import java.io.File

internal const val BASE_PACKAGE = "io.github.xyzboom.bf"
internal const val VISITOR_PACKAGE = "$BASE_PACKAGE.visitors"

fun main() {
    val model = TreeBuilder.build()
    TreeGenerator(File("tree/gen"), "README.md").run {
        model.inheritFields()

        ImplConfigurator.configureImplementations(model)
        val implementations = model.elements.flatMap { it.implementations }
        InterfaceAndAbstractClassConfigurator((model.elements + implementations))
            .configureInterfacesAndAbstractClasses()

        printElements(model, ::ElementPrinter)
        printElementImplementations(implementations, ::ImplementationPrinter)
        printElementBuilders(implementations.mapNotNull { it.builder }, ::BuilderPrinter)
        printVisitors(
            model,
            listOf(
                irVisitorType to ::VisitorPrinter.bind(false),
                irDefaultVisitorType to ::VisitorPrinter.bind(true),
                irVisitorVoidType to ::VisitorVoidPrinter,
                irDefaultVisitorVoidType to ::DefaultVisitorVoidPrinter,
                irTransformerType to ::TransformerPrinter.bind(model.rootElement),
            )
        )
    }
}