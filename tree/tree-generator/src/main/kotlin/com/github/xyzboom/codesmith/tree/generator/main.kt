package com.github.xyzboom.codesmith.tree.generator

import com.github.xyzboom.codesmith.tree.generator.printer.BuilderPrinter
import com.github.xyzboom.codesmith.tree.generator.printer.DefaultVisitorVoidPrinter
import com.github.xyzboom.codesmith.tree.generator.printer.ElementPrinter
import com.github.xyzboom.codesmith.tree.generator.printer.ImplementationPrinter
import com.github.xyzboom.codesmith.tree.generator.printer.TransformerPrinter
import com.github.xyzboom.codesmith.tree.generator.printer.VisitorPrinter
import com.github.xyzboom.codesmith.tree.generator.printer.VisitorVoidPrinter
import com.github.xyzboom.codesmith.tree.generator.utils.bind
import org.jetbrains.kotlin.generators.tree.InterfaceAndAbstractClassConfigurator
import org.jetbrains.kotlin.generators.tree.detectBaseTransformerTypes
import org.jetbrains.kotlin.generators.tree.printer.TreeGenerator
import java.io.File

internal const val BASE_PACKAGE = "com.github.xyzboom.codesmith.ir"
internal const val VISITOR_PACKAGE = "$BASE_PACKAGE.visitors"

fun main() {
    val model = TreeBuilder.build()
    TreeGenerator(File("tree/gen"), "README.md").run {
        model.inheritFields()
        detectBaseTransformerTypes(model)

        ImplConfigurator.configureImplementations(model)
        val implementations = model.elements.flatMap { it.implementations }
        InterfaceAndAbstractClassConfigurator((model.elements + implementations))
            .configureInterfacesAndAbstractClasses()

        val builderConfigurator = BuilderConfigurator(model)
        builderConfigurator.configureBuilders()

        printElements(model, ::ElementPrinter)
        printElementImplementations(implementations, ::ImplementationPrinter)
        printElementBuilders(implementations.mapNotNull { it.builder } + builderConfigurator.intermediateBuilders, ::BuilderPrinter)
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