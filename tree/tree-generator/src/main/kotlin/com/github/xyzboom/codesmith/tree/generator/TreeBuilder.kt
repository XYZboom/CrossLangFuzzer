package com.github.xyzboom.codesmith.tree.generator

import com.github.xyzboom.codesmith.tree.generator.model.Element
import com.github.xyzboom.codesmith.tree.generator.model.Field
import com.github.xyzboom.codesmith.tree.generator.model.ListField
import com.github.xyzboom.codesmith.tree.generator.model.SimpleField
import org.jetbrains.kotlin.generators.tree.ImplementationKind
import org.jetbrains.kotlin.generators.tree.StandardTypes
import org.jetbrains.kotlin.generators.tree.TypeRef
import org.jetbrains.kotlin.generators.tree.TypeRefWithNullability
import org.jetbrains.kotlin.generators.tree.config.AbstractElementConfigurator

object TreeBuilder : AbstractElementConfigurator<Element, Field, Element.Kind>() {
    override val rootElement: Element by element(Element.Kind.Other, name = "Element") {
        hasAcceptChildrenMethod = true
        hasTransformChildrenMethod = true
    }

    val namedElement: Element by element(Element.Kind.Other, name = "NamedElement") {
        kind = ImplementationKind.Interface
        +field("name", StandardTypes.string, isChild = false)
    }

    val program: Element by element(Element.Kind.Other, name = "Program") {
        parent(classContainer)
        parent(funcContainer)
        parent(propertyContainer)
        kind = ImplementationKind.Interface
    }

    val declaration: Element by element(Element.Kind.Declaration, name = "Declaration") {
        parent(namedElement)
        +field("language", languageType, isChild = false)
    }

    val classDecl: Element by element(Element.Kind.Declaration, name = "ClassDeclaration") {
        parent(declaration)
        parent(funcContainer)
        parent(typeParameterContainer)

        +field("classKind", classKindType, isChild = false)
        +field("superType", type, nullable = true, isChild = false)
        +field("allSuperTypeArguments", concreteTypeArgMapType, isChild = false)
        +listField("implementedTypes", type, isChild = false)
    }

    val funcDecl: Element by element(Element.Kind.Declaration, name = "FunctionDeclaration") {
        parent(declaration)
        parent(typeParameterContainer)

        +field("printNullableAnnotations", StandardTypes.boolean, isChild = false)
        +field("body", block, isChild = false, nullable = true)
        +field("isOverride", StandardTypes.boolean, isChild = false)
        +field("isOverrideStub", StandardTypes.boolean, isChild = false)
        +listField("override", funcDecl, isChild = false)
        +field("isFinal", StandardTypes.boolean, isChild = false)
        +field("parameterList", parameterList, isChild = false)
        +field("returnType", type, isChild = false)

        +field("containingClassName", StandardTypes.string, isChild = false, nullable = true)
    }

    val propertyDecl: Element by element(Element.Kind.Declaration, name = "PropertyDeclaration") {
        parent(declaration)
    }

    val parameter: Element by element(Element.Kind.Declaration, name = "Parameter") {
        +field("name", StandardTypes.string)
        +field("type", type)
        +field("defaultValue", expression, nullable = true)
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

    val expressionContainer: Element by element(Element.Kind.Container, name = "ExpressionContainer") {
        +listField("expressions", expression)
    }

    val parameterList: Element by element(Element.Kind.Other, name = "ParameterList") {
        +listField("parameters", parameter)
    }

    val type: Element by element(Element.Kind.Type, name = "Type") {
        +field("classKind", classKindType, withReplace = false, withTransform = false, isChild = false, isMutable = false)
    }

    val nullableType: Element by element(Element.Kind.Type, name = "NullableType") {
        parent(type)

        +field("innerType", type)
    }

    val typeParameter: Element by element(Element.Kind.Type, name = "TypeParameter") {
        parent(type)
        parent(namedElement)

        +field("upperbound", type, isChild = false)
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

        +field("arguments", typeArgMapType, isChild = false)
    }

    val expression: Element by element(Element.Kind.Expression, name = "Expression") {

    }

    val block: Element by element(Element.Kind.Expression, name = "Block") {
        parent(expressionContainer)
    }

    fun field(
        name: String,
        type: TypeRefWithNullability,
        nullable: Boolean = false,
        isMutable: Boolean = true,
        withReplace: Boolean = false,
        withTransform: Boolean = true,
        isChild: Boolean = true,
        initializer: SimpleField.() -> Unit = {},
    ): SimpleField {
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
        withReplace: Boolean = false,
        withTransform: Boolean = true,
        useMutableOrEmpty: Boolean = true,
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