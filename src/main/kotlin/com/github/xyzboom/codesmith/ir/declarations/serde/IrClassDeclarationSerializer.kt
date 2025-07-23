package com.github.xyzboom.codesmith.ir.declarations.serde

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.serde.addTypeParameters
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import kotlin.collections.iterator

object IrClassDeclarationSerializer : JsonSerializer<IrClassDeclaration> {
    fun serializeAllSuperTypeArguments(
        p2: JsonSerializationContext?, allSuperTypeArguments: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>
    ): JsonElement {
        val result = JsonObject().apply {
            for ((typeParamName, pair) in allSuperTypeArguments) {
                val array = JsonArray()
                array.add(p2?.serialize(pair.first, IrTypeParameter::class.java))
                array.add(p2?.serialize(pair.second, IrType::class.java))
                add(typeParamName.value, array)
            }
        }
        return result
    }

    override fun serialize(
        src: IrClassDeclaration?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        val result = JsonObject().apply {
            with(src) {
                addProperty(::name.name, name)
                add(::language.name, context?.serialize(language))
                if (functions.isNotEmpty()) {
                    val funcsObj = JsonArray()
                    for (func in functions) {
                        funcsObj.add(context?.serialize(func, IrFunctionDeclaration::class.java))
                    }
                    add(::functions.name, funcsObj)
                }
                addTypeParameters(this, context)
                add(::classKind.name, context?.serialize(classKind))
                if (superType != null && superType !== IrAny) {
                    add(::superType.name, context?.serialize(superType, IrType::class.java))
                }
                if (allSuperTypeArguments.isNotEmpty()) {
                    add(::allSuperTypeArguments.name, serializeAllSuperTypeArguments(context, allSuperTypeArguments))
                }
                if (implementedTypes.isNotEmpty()) {
                    val implTypesObj = JsonArray()
                    for (implType in implementedTypes) {
                        implTypesObj.add(context?.serialize(implType, IrType::class.java))
                    }
                    add(::implementedTypes.name, implTypesObj)
                }
            }
        }
        return result
    }
}