package com.github.xyzboom.codesmith.mutator.impl

import com.github.xyzboom.codesmith.mutator.MutatorConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IrMutatorImplTest {

    @Test
    fun initWithNotEnabledConfigShouldFail() {
        val copyMethod = MutatorConfig.default::copy
        val config = copyMethod.callBy(copyMethod.parameters.associateWith { false })
        assertThrows<IllegalArgumentException> {
            IrMutatorImpl(config = config)
        }
    }
}