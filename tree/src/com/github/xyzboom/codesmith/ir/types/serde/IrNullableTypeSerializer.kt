package com.github.xyzboom.codesmith.ir.types.serde

import com.github.xyzboom.codesmith.ir.serde.CONTENT_NAME_FOR_IR_TYPE
import com.github.xyzboom.codesmith.ir.serde.SERDE_TYPE_NAME
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrNullableTypeSerializer : JsonSerializer<IrNullableType> {
    override fun serialize(
        src: IrNullableType?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        val result = JsonObject().apply {
            addProperty(SERDE_TYPE_NAME, IrNullableType::class.simpleName)
            add(CONTENT_NAME_FOR_IR_TYPE, context?.serialize(src.innerType, IrType::class.java))
        }
        return result
    }

}