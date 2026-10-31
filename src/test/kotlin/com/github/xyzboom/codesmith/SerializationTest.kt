package com.github.xyzboom.codesmith

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.xyzboom.codesmith.ir_old.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.expressions.IrBlock
import com.github.xyzboom.codesmith.ir_old.types.IrClassType
import com.github.xyzboom.codesmith.serde.defaultIrMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SerializationTest {

    @Test
    fun testSerializeSimpleClassWithSimpleFunction() {
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName, clazz).apply {
            isFinal = true
            body = IrBlock()
            returnType = clazz.type
        }
        clazz.functions.add(func)
        val serialized = defaultIrMapper.writeValueAsString(clazz)
        val str = "{\n" +
                "  \"@type\" : \"class\",\n" +
                "  \"@id\" : 1,\n" +
                "  \"name\" : \"SimpleClassWithSimpleFunction\",\n" +
                "  \"classType\" : \"FINAL\",\n" +
                "  \"functions\" : [ {\n" +
                "    \"@type\" : \"function\",\n" +
                "    \"@id\" : 2,\n" +
                "    \"name\" : \"simple\",\n" +
                "    \"container\" : 1,\n" +
                "    \"body\" : {\n" +
                "      \"@type\" : \"IrBlock\",\n" +
                "      \"@id\" : 3\n" +
                "    },\n" +
                "    \"isFinal\" : true\n" +
//                "    \"parameterList\" : {\n" +
//                "      \"@type\" : \"IrParameterList\",\n" +
//                "      \"@id\" : 4\n" +
//                "    },\n" +
//                "    \"returnType\" : {\n" +
//                "      \"@type\" : \"IrUnit\",\n" +
//                "      \"@id\" : 5\n" +
//                "    }\n" +
                "  } ]\n" +
                "}"
        val deserialized = defaultIrMapper.readValue<IrClassDeclaration>(serialized)
        assertEquals(1, deserialized.functions.size)
        assertEquals(deserialized, deserialized.functions.single().container)

        val legacySerialized = serialized.replace(
            "\"name\" : \"simple\",",
            "\"name\" : \"simple\",\n    \"printNullableAnnotations\" : true,"
        )
        val legacyDeserialized = defaultIrMapper.readValue<IrClassDeclaration>(legacySerialized)
        assertEquals(1, legacyDeserialized.functions.size)
    }

    @Test
    fun `new IR function JSON omits the removed nullable annotation flag`() {
        val function = com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration {
            name = "simple"
            parameterList = com.github.xyzboom.codesmith.ir.builder.buildParameterList()
        }
        val serialized = com.github.xyzboom.codesmith.serde.gson.toJson(
            function,
            com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration::class.java
        )
        assertFalse(com.google.gson.JsonParser.parseString(serialized).asJsonObject.has("printNullableAnnotations"))
    }
}