package com.github.xyzboom.codesmith.mutator.impl

import com.github.xyzboom.codesmith.mutator.MutatorConfigOld
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IrMutatorOldImplTest {

    @Test
    fun initWithNotEnabledConfigShouldFail() {
        val copyMethod = MutatorConfigOld.default::copy
        val config = copyMethod.callBy(copyMethod.parameters.associateWith { false })
        assertThrows<IllegalArgumentException> {
            IrMutatorOldImpl(config = config)
        }
    }
}