package com.github.xyzboom.codesmith.ir.types.serde

import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrTypeSerializer : JsonSerializer<IrType> {
    override fun serialize(
        src: IrType?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement? {
        if (src == null) return JsonNull.INSTANCE
        return when (src) {
            is IrSimpleClassifier -> context?.serialize(src, IrSimpleClassifier::class.java)
            is IrParameterizedClassifier -> context?.serialize(src, IrParameterizedClassifier::class.java)
            is IrNullableType -> context?.serialize(src, IrNullableType::class.java)
            is IrBuiltInType -> context?.serialize(src, IrBuiltInType::class.java)
            is IrTypeParameter -> context?.serialize(src, IrTypeParameter::class.java)
            else -> throw NoWhenBranchMatchedException("src has unexpected type: ${src::class.simpleName}")
        }
    }

}