package com.github.xyzboom.codesmith.tree.generator.utils

import com.github.xyzboom.codesmith.tree.generator.BASE_PACKAGE
import org.jetbrains.kotlin.generators.tree.ClassRef
import org.jetbrains.kotlin.generators.tree.PositionTypeParameterRef
import org.jetbrains.kotlin.generators.tree.TypeKind
import org.jetbrains.kotlin.generators.tree.type

fun generatedType(type: String, kind: TypeKind = TypeKind.Class): ClassRef<PositionTypeParameterRef> = generatedType("", type, kind)

fun generatedType(packageName: String, type: String, kind: TypeKind = TypeKind.Class): ClassRef<PositionTypeParameterRef> {
    val realPackage = BASE_PACKAGE + if (packageName.isNotBlank()) ".$packageName" else ""
    return type(realPackage, type, exactPackage = true, kind = kind)
}

fun type(
    packageName: String,
    type: String,
    exactPackage: Boolean = false,
    kind: TypeKind = TypeKind.Interface,
): ClassRef<PositionTypeParameterRef> {
    val realPackage = if (exactPackage) packageName else packageName.let { "com.github.xyzboom.codesmith.$it" }
    return type(realPackage, type, kind)
}

inline fun <reified T : Any> type() = type<T>()
