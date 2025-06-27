package com.github.xyzboom.codesmith.ir.types.serde

import com.github.xyzboom.codesmith.ir.serde.SERDE_TYPE_NAME
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrTypeParameterSerializer: JsonSerializer<IrTypeParameter> {
    override fun serialize(
        src: IrTypeParameter?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        return JsonObject().apply {
            addProperty(SERDE_TYPE_NAME, IrTypeParameter::class.simpleName)
            addProperty(src::name.name, src.name)
            if (src.upperbound !== IrAny) {
                add(src::upperbound.name, context?.serialize(src.upperbound, IrType::class.java))
            }
        }
    }
}