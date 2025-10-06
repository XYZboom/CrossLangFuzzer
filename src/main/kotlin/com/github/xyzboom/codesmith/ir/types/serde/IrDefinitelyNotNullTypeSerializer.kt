package com.github.xyzboom.codesmith.ir.types.serde

import com.github.xyzboom.codesmith.ir.serde.SERDE_TYPE_NAME
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrDefinitelyNotNullTypeSerializer : JsonSerializer<IrDefinitelyNotNullType> {
    override fun serialize(
        src: IrDefinitelyNotNullType?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        val result = JsonObject().apply {
            addProperty(SERDE_TYPE_NAME, IrDefinitelyNotNullType::class.simpleName)
            add(src::innerType.name, context?.serialize(src.innerType, IrType::class.java))
        }
        return result
    }

}