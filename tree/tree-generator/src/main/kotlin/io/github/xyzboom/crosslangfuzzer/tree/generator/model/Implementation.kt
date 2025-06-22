package io.github.xyzboom.crosslangfuzzer.tree.generator.model

import org.jetbrains.kotlin.generators.tree.AbstractImplementation

class Implementation(element: Element, name: String?) : AbstractImplementation<Implementation, Element, Field>(element, name) {
    override val allFields = element.allFields.map { it.copy() }
}