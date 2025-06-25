package com.github.xyzboom.codesmith.tree.generator

import com.github.xyzboom.codesmith.tree.generator.model.Element
import com.github.xyzboom.codesmith.tree.generator.model.Field
import com.github.xyzboom.codesmith.tree.generator.model.ListField
import com.github.xyzboom.codesmith.tree.generator.model.SimpleField
import org.jetbrains.kotlin.generators.tree.ElementOrRef
import org.jetbrains.kotlin.generators.tree.ImplementationKind
import org.jetbrains.kotlin.generators.tree.StandardTypes
import org.jetbrains.kotlin.generators.tree.TypeRef
import org.jetbrains.kotlin.generators.tree.TypeRefWithNullability
import org.jetbrains.kotlin.generators.tree.config.AbstractElementConfigurator
import org.jetbrains.kotlin.generators.tree.type
import org.jetbrains.kotlin.generators.tree.withArgs

object TreeBuilder : AbstractElementConfigurator<Element, Field, Element.Kind>() {
    override val rootElement: Element by element(Element.Kind.Other, name = "Element") {
        hasAcceptChildrenMethod = true
        hasTransformChildrenMethod = true
    }

    val program: Element by element(Element.Kind.Other, name = "Program") {
        parent(classContainer)
        parent(funcContainer)
        parent(propertyContainer)
    }

    val declaration: Element by element(Element.Kind.Declaration, name = "Declaration") {
        +field("name", StandardTypes.string)
        +field("language", languageType)
    }

    val classDecl: Element by element(Element.Kind.Declaration, name = "ClassDeclaration") {
        parent(declaration)
        parent(funcContainer)
        parent(typeParameterContainer)

        +field("classKind", classKindType)
        +field("superType", type, nullable = true)
        +field("allSuperTypeArguments", typeArgMapType)
        +listField("implementedTypes", type)
    }

    val funcDecl: Element by element(Element.Kind.Declaration, name = "FunctionDeclaration") {
        parent(declaration)
        parent(typeParameterContainer)
    }

    val propertyDecl: Element by element(Element.Kind.Declaration, name = "PropertyDeclaration") {
        parent(declaration)
    }

    val parameter: Element by element(Element.Kind.Declaration, name = "Parameter") {

    }

    val classContainer: Element by element(Element.Kind.Container, name = "ClassContainer") {
        +listField("classes", classDecl)
    }

    val funcContainer: Element by element(Element.Kind.Container, name = "FuncContainer") {
        +listField("functions", funcDecl)
    }

    val propertyContainer: Element by element(Element.Kind.Container, name = "PropertyContainer") {
        +listField("properties", propertyDecl)
    }

    val typeParameterContainer: Element by element(Element.Kind.Container, name = "TypeParameterContainer") {
        +listField("typeParameters", typeParameter)
    }

    val parameterList: Element by element(Element.Kind.Other, name = "ParameterList") {
        +listField("parameters", parameter)
    }

    val type: Element by element(Element.Kind.Type, name = "Type") {
        +field("classKind", classKindType, withReplace = false, withTransform = false)
    }

    val nullableType: Element by element(Element.Kind.Type, name = "NullableType") {
        parent(type)

        +field("innerType", type)
    }

    val typeParameter: Element by element(Element.Kind.Type, name = "TypeParameter") {
        parent(type)

        +field("name", StandardTypes.string)
        +field("upperbound", type)
    }

    val classifier: Element by sealedElement(Element.Kind.Type, name = "Classifier") {
        parent(type)

        +field("classDecl", classDecl)
    }

    val simpleClassifier: Element by element(Element.Kind.Type, name = "SimpleClassifier") {
        parent(classifier)
    }

    val parameterizedClassifier: Element by element(Element.Kind.Type, name = "ParameterizedClassifier") {
        parent(classifier)

        +field("arguments", typeArgMapType)
    }

    fun field(
        name: String,
        type: TypeRefWithNullability,
        nullable: Boolean = false,
        withReplace: Boolean = true,
        withTransform: Boolean = true,
        isChild: Boolean = true,
        initializer: SimpleField.() -> Unit = {},
    ): SimpleField {
        val isMutable = type is ElementOrRef<*> || withReplace
        return SimpleField(
            name,
            type.copy(nullable),
            isChild = isChild,
            isMutable = isMutable,
            withReplace = withReplace,
            withTransform = withTransform
        ).apply(initializer)
    }

    fun listField(
        name: String,
        baseType: TypeRef,
        withReplace: Boolean = true,
        withTransform: Boolean = true,
        useMutableOrEmpty: Boolean = false,
        isChild: Boolean = true,
        initializer: ListField.() -> Unit = {},
    ): Field {
        return ListField(
            name,
            baseType,
            withReplace = withReplace,
            isChild = isChild,
            isMutableOrEmptyList = useMutableOrEmpty,
            withTransform = withTransform,
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