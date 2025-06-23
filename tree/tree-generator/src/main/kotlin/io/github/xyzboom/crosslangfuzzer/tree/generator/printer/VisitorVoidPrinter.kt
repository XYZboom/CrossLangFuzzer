/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package io.github.xyzboom.crosslangfuzzer.tree.generator.printer

import io.github.xyzboom.crosslangfuzzer.tree.generator.TreeBuilder
import io.github.xyzboom.crosslangfuzzer.tree.generator.irVisitorType
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Element
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Field
import org.jetbrains.kotlin.generators.tree.AbstractVisitorVoidPrinter
import org.jetbrains.kotlin.generators.tree.ClassRef
import org.jetbrains.kotlin.generators.tree.PositionTypeParameterRef
import org.jetbrains.kotlin.generators.tree.printer.ImportCollectingPrinter

internal class VisitorVoidPrinter(
    printer: ImportCollectingPrinter,
    override val visitorType: ClassRef<*>,
) : AbstractVisitorVoidPrinter<Element, Field>(printer) {

    override val visitorSuperClass: ClassRef<PositionTypeParameterRef>
        get() = irVisitorType

    override val allowTypeParametersInVisitorMethods: Boolean
        get() = true

    override val useAbstractMethodForRootElement: Boolean
        get() = true

    override val overriddenVisitMethodsAreFinal: Boolean
        get() = true

    override fun parentInVisitor(element: Element): Element = TreeBuilder.rootElement
}
