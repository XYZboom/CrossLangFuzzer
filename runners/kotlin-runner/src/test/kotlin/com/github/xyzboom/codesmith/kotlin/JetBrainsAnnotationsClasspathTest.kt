package com.github.xyzboom.codesmith.kotlin

import org.jetbrains.annotations.NotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.annotation.ElementType
import java.lang.annotation.Target

class JetBrainsAnnotationsClasspathTest {
    @Test
    fun `pinned JetBrains annotations artifact supports type-use`() {
        val jar = jetbrainsAnnotationsJar()
        val targets = NotNull::class.java.getAnnotation(Target::class.java).value.toSet()

        assertEquals("annotations-26.1.0.jar", jar.name)
        assertTrue(ElementType.TYPE_USE in targets, "NotNull must support TYPE_USE")
    }
}
