package com.github.xyzboom.codesmith.ir.declarations.serde

import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.types.IrType
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrParameterSerializer: JsonSerializer<IrParameter> {
    override fun serialize(
        src: IrParameter?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement? {
        if (src == null) return JsonNull.INSTANCE
        return JsonObject().apply {
            with (src) {
                addProperty(::name.name, name)
                add(::type.name, context?.serialize(type, IrType::class.java))
            }
        }
    }
}