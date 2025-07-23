package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.serde.gson

fun IrProgram.deepCopy(): IrProgram {
    val ser = gson.toJson(this)
    return gson.fromJson(ser, IrProgram::class.java)
}