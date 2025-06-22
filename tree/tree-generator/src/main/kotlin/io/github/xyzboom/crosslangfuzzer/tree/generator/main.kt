package io.github.xyzboom.crosslangfuzzer.tree.generator

import org.jetbrains.kotlin.generators.tree.InterfaceAndAbstractClassConfigurator
import org.jetbrains.kotlin.generators.tree.printer.TreeGenerator
import java.io.File

internal const val BASE_PACKAGE = "io.github.xyzboom.bf"
internal const val VISITOR_PACKAGE = "$BASE_PACKAGE.visitors"

fun main() {
    val model = TreeBuilder().build()
    TreeGenerator(File("tree/gen"), "README.md").run {
        model.inheritFields()

//        ImplementationConfigurator.configureImplementations(model)
        val implementations = model.elements.flatMap { it.implementations }
        InterfaceAndAbstractClassConfigurator((model.elements + implementations))
            .configureInterfacesAndAbstractClasses()

        printElements(model, ::ElementPrinter)
//        printElementImplementations(implementations, ::ImplementationPrinter)
//        printElementBuilders(implementations.mapNotNull { it.builder } + builderConfigurator.intermediateBuilders, ::BuilderPrinter)

    }
}