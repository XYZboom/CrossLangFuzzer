package io.github.xyzboom.crosslangfuzzer.tree.generator

import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Element
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Field
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Implementation
import org.jetbrains.kotlin.generators.tree.Model
import org.jetbrains.kotlin.generators.tree.config.AbstractImplementationConfigurator

object ImplConfigurator : AbstractImplementationConfigurator<Implementation, Element, Field>() {
    override fun createImplementation(
        element: Element,
        name: String?
    ): Implementation {
        return Implementation(element, name)
    }

    override fun configure(model: Model<Element>) = with(TreeBuilder) {
        impl(program)
        Unit
    }

    override fun configureAllImplementations(model: Model<Element>) {

    }

}