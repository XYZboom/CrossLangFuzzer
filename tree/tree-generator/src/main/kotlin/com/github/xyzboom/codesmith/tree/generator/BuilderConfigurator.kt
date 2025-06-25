/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.github.xyzboom.codesmith.tree.generator

import com.github.xyzboom.codesmith.tree.generator.model.Element
import com.github.xyzboom.codesmith.tree.generator.model.Field
import com.github.xyzboom.codesmith.tree.generator.model.Implementation
import org.jetbrains.kotlin.generators.tree.Model
import org.jetbrains.kotlin.generators.tree.config.AbstractBuilderConfigurator


class BuilderConfigurator(model: Model<Element>) : AbstractBuilderConfigurator<Element, Implementation, Field>(model) {

    override val namePrefix: String
        get() = "Ir"

    override val defaultBuilderPackage: String
        get() = "$BASE_PACKAGE.builder"

    override fun configureBuilders() = with(TreeBuilder) {
        val typeParameterContainerBuilder by builder {
            fields from typeParameterContainer
        }
        builder(classDecl) {
            parents += typeParameterContainerBuilder
        }
        noBuilder(nullableType)
    }
}
