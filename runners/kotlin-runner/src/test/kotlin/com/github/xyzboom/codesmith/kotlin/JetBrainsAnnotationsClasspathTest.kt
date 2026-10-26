package com.github.xyzboom.codesmith.kotlin

import org.jetbrains.annotations.NotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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

    @Test
    fun `missing code source reports class loader and classpath`() {
        val error = assertThrows(IllegalStateException::class.java) {
            annotationsJarFromCodeSource(null, javaClass.classLoader, "/test/classpath")
        }

        assertTrue(error.message.orEmpty().contains("java.class.path=/test/classpath"))
        assertTrue(error.message.orEmpty().contains(javaClass.classLoader.toString()))
    }
}
