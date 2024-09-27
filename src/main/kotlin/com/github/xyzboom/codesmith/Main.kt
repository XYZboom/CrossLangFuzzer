package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.generator.GeneratorConfig
import com.github.xyzboom.codesmith.ir.generator.IrGeneratorImpl

fun main() {
    for (i in 0 until 1000) {
        val prog = IrGeneratorImpl(config = GeneratorConfig(moduleNumRange = 8..8)).generate()
        println(prog)
    }
}