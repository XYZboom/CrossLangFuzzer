package com.github.xyzboom.codesmith.bf

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import org.junit.jupiter.api.Test

class GeneratorTest {
    @Test
    fun testGen() {
        val generator = Generator(GeneratorConfig.default)
        generator.generate("prog")
    }
}