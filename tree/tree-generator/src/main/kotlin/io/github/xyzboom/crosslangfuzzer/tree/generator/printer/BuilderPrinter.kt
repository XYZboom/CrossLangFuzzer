/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package io.github.xyzboom.crosslangfuzzer.tree.generator.printer

import io.github.xyzboom.crosslangfuzzer.tree.generator.implementationDetailType
import io.github.xyzboom.crosslangfuzzer.tree.generator.irBuilderDslAnnotation
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Element
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Field
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.Implementation
import io.github.xyzboom.crosslangfuzzer.tree.generator.model.ListField
import org.jetbrains.kotlin.generators.tree.AbstractBuilderPrinter
import org.jetbrains.kotlin.generators.tree.ClassRef
import org.jetbrains.kotlin.generators.tree.StandardTypes
import org.jetbrains.kotlin.generators.tree.printer.ImportCollectingPrinter
import org.jetbrains.kotlin.generators.tree.withArgs

internal class BuilderPrinter(
    printer: ImportCollectingPrinter
) : AbstractBuilderPrinter<Element, Implementation, Field>(printer) {

    override val implementationDetailAnnotation: ClassRef<*>
        get() = implementationDetailType

    override val builderDslAnnotation: ClassRef<*>
        get() = irBuilderDslAnnotation

    override fun actualTypeOfField(field: Field) = when (field) {
        is ListField -> StandardTypes.mutableList.withArgs(field.baseType)
        else -> field.typeRef
    }

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
