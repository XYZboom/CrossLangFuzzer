package com.github.xyzboom.codesmith.utils

import java.io.File

fun File.mkdirsIfNotExists(): File = also {
    if (!it.exists()) {
        mkdirs()
    }
}