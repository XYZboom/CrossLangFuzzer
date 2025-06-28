package com.github.xyzboom.codesmith.ir.types.serde

import com.github.xyzboom.codesmith.ir.serde.SERDE_TYPE_NAME
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrSimpleClassifierSerializer : JsonSerializer<IrSimpleClassifier> {
    override fun serialize(
        src: IrSimpleClassifier?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        return JsonObject().apply {
            addProperty(SERDE_TYPE_NAME, IrSimpleClassifier::class.simpleName)
            // actually is addProperty("classDec", ...)
            addProperty(src::classDecl.name, src.classDecl.name)
        }
    }

}