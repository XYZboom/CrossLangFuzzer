package com.github.xyzboom.codesmith.tree.generator

import com.github.xyzboom.codesmith.tree.generator.TreeBuilder.typeParameter
import com.github.xyzboom.codesmith.tree.generator.utils.generatedType
import com.github.xyzboom.codesmith.tree.generator.utils.type
import org.jetbrains.kotlin.generators.tree.StandardTypes
import org.jetbrains.kotlin.generators.tree.TypeKind
import org.jetbrains.kotlin.generators.tree.imports.ArbitraryImportable
import org.jetbrains.kotlin.generators.tree.withArgs

val pureAbstractElementType = generatedType("IrPureAbstractElement")
val irBuilderDslAnnotation = type("ir.builder", "BuilderDsl", kind = TypeKind.Class)
val implementationDetailType = generatedType("ImplementationDetail")

val irVisitorType = generatedType("visitors", "IrVisitor")
val irVisitorVoidType = generatedType("visitors", "IrVisitorVoid")
val irDefaultVisitorType = generatedType("visitors", "IrDefaultVisitor")
val irDefaultVisitorVoidType = generatedType("visitors", "IrDefaultVisitorVoid")
val irTransformerType = generatedType("visitors", "IrTransformer")

val classKindType = type("ir", "ClassKind")
val languageType = type("ir", "Language")

val transformInPlaceImport = ArbitraryImportable(VISITOR_PACKAGE, "transformInplace")

val irTypeParameterNameType = type("ir.types", "IrTypeParameterName", kind = TypeKind.Class)
val concreteTypeArgMapType = org.jetbrains.kotlin.generators.tree.type("kotlin.collections", "MutableMap").withArgs(
    irTypeParameterNameType,
    org.jetbrains.kotlin.generators.tree.type<Pair<*, *>>().withArgs(typeParameter, TreeBuilder.type)
)

val typeArgMapType = org.jetbrains.kotlin.generators.tree.type("kotlin.collections", "MutableMap").withArgs(
    irTypeParameterNameType,
    org.jetbrains.kotlin.generators.tree.type<Pair<*, *>>().withArgs(typeParameter, TreeBuilder.type.copy(nullable = true))
)