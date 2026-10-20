package com.github.xyzboom.codesmith

import java.io.File
import java.net.URLDecoder
import java.util.jar.JarFile


fun listResourceFiles(folderPath: String): List<String> {
    val cleanFolderPath = folderPath.trim('/')
    val classLoader = Thread.currentThread().contextClassLoader
        ?: CommonCompilerRunner::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader()
    val resourceUrl = classLoader.getResource(cleanFolderPath)
        ?: ClassLoader.getSystemClassLoader().getResource(cleanFolderPath)
        ?: return emptyList()

    return when (resourceUrl.protocol) {
        "file" -> handleFileSystem(resourceUrl)
        "jar" -> handleJarFile(resourceUrl, cleanFolderPath)
        else -> emptyList()
    }.map { "${cleanFolderPath}/${it}" }
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
            .map { it.name.removePrefix(prefix) }
            .toList()
    }
}
