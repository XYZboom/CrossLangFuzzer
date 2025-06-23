package com.github.xyzboom.codesmith.tree.generator.model

import org.jetbrains.kotlin.generators.tree.AbstractImplementation

class Implementation(element: Element, name: String?) : AbstractImplementation<Implementation, Element, Field>(element, name) {
    override val allFields = element.allFields.map { it.copy() }
}