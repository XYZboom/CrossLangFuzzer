package com.github.xyzboom.codesmith.mutator

data class MutatorConfig(
    val mutateGenericArgumentInParentWeight: Int = 1,
    val removeOverrideMemberFunctionWeight: Int = 1,
    val mutateGenericArgumentInMemberFunctionParameterWeight: Int = 3,
) {
    companion object {
        @JvmStatic
        val default = MutatorConfig()

        @JvmStatic
        val allZero = MutatorConfig(
            mutateGenericArgumentInParentWeight = 0,
            removeOverrideMemberFunctionWeight = 0,
            mutateGenericArgumentInMemberFunctionParameterWeight = 0,
        )
    }
}