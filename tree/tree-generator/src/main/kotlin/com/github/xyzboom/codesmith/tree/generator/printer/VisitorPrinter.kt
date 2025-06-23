/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.github.xyzboom.codesmith.tree.generator.printer

import com.github.xyzboom.codesmith.tree.generator.TreeBuilder
import com.github.xyzboom.codesmith.tree.generator.irVisitorType
import com.github.xyzboom.codesmith.tree.generator.model.Element
import com.github.xyzboom.codesmith.tree.generator.model.Field
import org.jetbrains.kotlin.generators.tree.*
import org.jetbrains.kotlin.generators.tree.printer.ImportCollectingPrinter

internal class VisitorPrinter(
    printer: ImportCollectingPrinter,
    override val visitorType: ClassRef<*>,
    private val visitSuperTypeByDefault: Boolean,
) : AbstractVisitorPrinter<Element, Field>(printer) {

    override val visitorTypeParameters: List<TypeVariable>
        get() = listOf(resultTypeVariable, dataTypeVariable)

    override val visitorSuperTypes: List<ClassRef<PositionTypeParameterRef>> =
        listOfNotNull(irVisitorType.takeIf { visitSuperTypeByDefault }?.withArgs(resultTypeVariable, dataTypeVariable))

    override val visitorDataType: TypeRef
        get() = dataTypeVariable

    override fun visitMethodReturnType(element: Element) = resultTypeVariable

    override val allowTypeParametersInVisitorMethods: Boolean
        get() = true

    override fun skipElement(element: Element): Boolean = visitSuperTypeByDefault && element.isRootElement

    override fun parentInVisitor(element: Element): Element? = when {
        element.isRootElement -> null
        visitSuperTypeByDefault -> element.parentInVisitor
        else -> TreeBuilder.rootElement
    }
}
