package io.github.xyzboom.crosslangfuzzer.tree.generator

import io.github.xyzboom.crosslangfuzzer.tree.generator.utils.generatedType
import io.github.xyzboom.crosslangfuzzer.tree.generator.utils.type
import org.jetbrains.kotlin.generators.tree.TypeKind

val pureAbstractElementType = generatedType("IrPureAbstractElement")
val irBuilderDslAnnotation = type("builder", "BuilderDsl", kind = TypeKind.Class)
val implementationDetailType = generatedType("ImplementationDetail")

val irVisitorType = generatedType("visitors", "IrVisitor")
val irVisitorVoidType = generatedType("visitors", "IrVisitorVoid")
val irDefaultVisitorType = generatedType("visitors", "IrDefaultVisitor")
val irDefaultVisitorVoidType = generatedType("visitors", "IrDefaultVisitorVoid")
val irTransformerType = generatedType("visitors", "IrTransformer")