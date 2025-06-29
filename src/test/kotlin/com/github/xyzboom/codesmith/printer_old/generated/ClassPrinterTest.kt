// auto generated, do not manually edit!
package com.github.xyzboom.codesmith.printer_old.generated

import com.github.xyzboom.codesmith.printer_old.BaseClassPrinterTester
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import java.nio.file.Path

class ClassPrinterTest : BaseClassPrinterTester() {
    @Test
    fun test_newExpression() {
        doValidate(
            """src/testData/printer/newExpression/ir.json""",
            """src/testData/printer/newExpression/result.kt""",
            """src/testData/printer/newExpression/result.java"""
        )
    }
    
    @Test
    fun test_simpleClassWithFunctionHasParameter() {
        doValidate(
            """src/testData/printer/simpleClassWithFunctionHasParameter/ir.json""",
            """src/testData/printer/simpleClassWithFunctionHasParameter/result.kt""",
            """src/testData/printer/simpleClassWithFunctionHasParameter/result.java"""
        )
    }
    
    @Test
    fun test_simpleClassWithSimpleFunction() {
        doValidate(
            """src/testData/printer/simpleClassWithSimpleFunction/ir.json""",
            """src/testData/printer/simpleClassWithSimpleFunction/result.kt""",
            """src/testData/printer/simpleClassWithSimpleFunction/result.java"""
        )
    }
    
    @Test
    fun test_simpleClassWithSimpleStubFunction() {
        doValidate(
            """src/testData/printer/simpleClassWithSimpleStubFunction/ir.json""",
            """src/testData/printer/simpleClassWithSimpleStubFunction/result.kt""",
            """src/testData/printer/simpleClassWithSimpleStubFunction/result.java"""
        )
    }
    
    @Test
    fun test_simpleProperty() {
        doValidate(
            """src/testData/printer/simpleProperty/ir.json""",
            """src/testData/printer/simpleProperty/result.kt""",
            """src/testData/printer/simpleProperty/result.java"""
        )
    }
    

}