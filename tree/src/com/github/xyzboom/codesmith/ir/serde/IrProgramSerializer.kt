package com.github.xyzboom.codesmith.ir.serde

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrProgramSerializer : JsonSerializer<IrProgram> {
    override fun serialize(
        p0: IrProgram?,
        p1: Type?,
        p2: JsonSerializationContext?
    ): JsonElement {
        if (p0 == null) return JsonNull.INSTANCE
        val result = JsonObject()
        result.addProperty(SERDE_TYPE_NAME, IrProgram::class.simpleName)
        if (p0.classes.isNotEmpty()) {
            val classesObj = JsonArray()
            for (clazz in p0.classes) {
                classesObj.add(p2?.serialize(clazz, IrClassDeclaration::class.java))
            }
            result.add(IrProgram::classes.name, classesObj)
        }
        return result
    }
}