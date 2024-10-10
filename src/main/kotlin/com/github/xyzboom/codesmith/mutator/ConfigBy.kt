package com.github.xyzboom.codesmith.mutator

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigBy(val name: String)
