package io.github.xyzboom.crosslangfuzzer.tree.generator

import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Element
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Field
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.ListField
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.SimpleField
import org.jetbrains.kotlin.generators.tree.ElementOrRef
import org.jetbrains.kotlin.generators.tree.TypeRef
import org.jetbrains.kotlin.generators.tree.TypeRefWithNullability
import org.jetbrains.kotlin.generators.tree.config.AbstractElementConfigurator

class TreeBuilder : AbstractElementConfigurator<Element, Field, Element.Kind>() {
    override val rootElement: Element by element(Element.Kind.Other, name = "Element") {
        hasAcceptChildrenMethod = true
        hasTransformChildrenMethod = true
    }

    val program: Element by element(Element.Kind.Other, name = "Program") {
    }

    val declaration: Element by element(Element.Kind.Declaration, name = "Declaration") {

    }

    val classDecl: Element by element(Element.Kind.Declaration, name= "ClassDeclaration") {
        parent(declaration)
    }

    val container: Element by element(Element.Kind.Other, name = "Container") {
        +listField("classes", classDecl)
    }

    fun field(
        name: String,
        type: TypeRefWithNullability,
        nullable: Boolean = false,
        withReplace: Boolean = false,
        withTransform: Boolean = false,
        isChild: Boolean = true,
        initializer: SimpleField.() -> Unit = {},
    ): SimpleField {
        val isMutable = type is ElementOrRef<*> || withReplace
        return SimpleField(
            name,
            type.copy(nullable),
            isChild = isChild,
            isMutable = isMutable,
//            withReplace = withReplace,
//            withTransform = withTransform
        ).apply(initializer)
    }

    fun listField(
        name: String,
        baseType: TypeRef,
        withReplace: Boolean = false,
        withTransform: Boolean = false,
        useMutableOrEmpty: Boolean = false,
        isChild: Boolean = true,
        initializer: ListField.() -> Unit = {},
    ): Field {
        return ListField(
            name,
            baseType,
//            withReplace = withReplace,
            isChild = isChild,
            isMutableOrEmptyList = useMutableOrEmpty,
//            withTransform = withTransform,
        ).apply(initializer)
    }

    override fun createElement(
        name: String,
        propertyName: String,
        category: Element.Kind
    ): Element {
        return Element(name, propertyName, category)
    }

}