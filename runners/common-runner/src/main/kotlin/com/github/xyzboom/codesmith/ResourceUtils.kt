package com.github.xyzboom.codesmith

import java.io.File
import java.net.URLDecoder
import java.util.jar.JarFile


fun listResourceFiles(folderPath: String): List<String> {
    val resourceUrl = ClassLoader.getSystemClassLoader().getResource(folderPath)
        ?: return emptyList()

    return when (resourceUrl.protocol) {
        "file" -> handleFileSystem(resourceUrl)
        "jar" -> handleJarFile(resourceUrl, folderPath)
        else -> emptyList()
    }.map { "${folderPath}/${it}" }
}

private fun handleFileSystem(resourceUrl: java.net.URL): List<String> {
    return File(resourceUrl.toURI()).takeIf { it.isDirectory }
        ?.listFiles()
        ?.map { it.name }
        ?: emptyList()
}

private fun handleJarFile(resourceUrl: java.net.URL, folderPath: String): List<String> {
    val jarPath = resourceUrl.path
        .substringAfter("file:")
        .substringBefore("!")
        .let { URLDecoder.decode(it, "UTF-8") }

    return JarFile(jarPath).use { jar ->
        val prefix = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
        jar.entries()
            .asSequence()
            .filter { !it.isDirectory && it.name.startsWith(prefix) }
            .map { it.name }
            .toList()
    }
}
