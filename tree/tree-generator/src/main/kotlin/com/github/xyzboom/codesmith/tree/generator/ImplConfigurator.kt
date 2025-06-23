package com.github.xyzboom.codesmith.tree.generator

import com.github.xyzboom.codesmith.tree.generator.model.Element
import com.github.xyzboom.codesmith.tree.generator.model.Field
import com.github.xyzboom.codesmith.tree.generator.model.Implementation
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