package com.github.xyzboom.codesmith.tree.generator

import com.github.xyzboom.codesmith.tree.generator.utils.generatedType
import com.github.xyzboom.codesmith.tree.generator.utils.type
import org.jetbrains.kotlin.generators.tree.TypeKind
import org.jetbrains.kotlin.generators.tree.imports.ArbitraryImportable

val pureAbstractElementType = generatedType("IrPureAbstractElement")
val irBuilderDslAnnotation = type("ir.builder", "BuilderDsl", kind = TypeKind.Class)
val implementationDetailType = generatedType("ImplementationDetail")

val irVisitorType = generatedType("visitors", "IrVisitor")
val irVisitorVoidType = generatedType("visitors", "IrVisitorVoid")
val irDefaultVisitorType = generatedType("visitors", "IrDefaultVisitor")
val irDefaultVisitorVoidType = generatedType("visitors", "IrDefaultVisitorVoid")
val irTransformerType = generatedType("visitors", "IrTransformer")

val classKindType = type("ir", "ClassKind")

val transformInPlaceImport = ArbitraryImportable(VISITOR_PACKAGE, "transformInplace")