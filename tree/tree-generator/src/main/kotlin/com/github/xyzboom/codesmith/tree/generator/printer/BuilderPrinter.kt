/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.github.xyzboom.codesmith.tree.generator.printer

import com.github.xyzboom.codesmith.tree.generator.irBuilderDslAnnotation
import com.github.xyzboom.codesmith.tree.generator.irImplementationDetailType
import com.github.xyzboom.codesmith.tree.generator.model.Element
import com.github.xyzboom.codesmith.tree.generator.model.Field
import com.github.xyzboom.codesmith.tree.generator.model.Implementation
import com.github.xyzboom.codesmith.tree.generator.model.ListField
import org.jetbrains.kotlin.generators.tree.AbstractBuilderPrinter
import org.jetbrains.kotlin.generators.tree.ClassRef
import org.jetbrains.kotlin.generators.tree.printer.ImportCollectingPrinter

internal class BuilderPrinter(
    printer: ImportCollectingPrinter
) : AbstractBuilderPrinter<Element, Implementation, Field>(printer) {

    override val implementationDetailAnnotation: ClassRef<*>
        get() = irImplementationDetailType

    override val builderDslAnnotation: ClassRef<*>
        get() = irBuilderDslAnnotation

    override fun actualTypeOfField(field: Field) = field.getMutableType(true)

    override fun ImportCollectingPrinter.printFieldReferenceInImplementationConstructorCall(field: Field) {
        print(field.name)
        if (field is ListField && field.isMutableOrEmptyList) {
//            addImport(toMutableOrEmptyImport)
//            print(".toMutableOrEmpty()")
        }
    }

    /*override fun copyField(
        field: Field,
        originalParameterName: String,
        copyBuilderVariableName: String
    ) {
        if (field.typeRef == declarationAttributesType) {
            printer.println(copyBuilderVariableName, ".", field.name, " = ", originalParameterName, ".", field.name, ".copy()")
        } else {
            super.copyField(field, originalParameterName, copyBuilderVariableName)
        }
    }*/
}
