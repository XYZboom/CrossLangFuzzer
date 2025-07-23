package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.config.RunConfig
import com.github.xyzboom.codesmith.serde.configGson
import java.io.File

fun main() {
    val config = RunConfig()
    val gson = configGson.newBuilder().setPrettyPrinting().create()
    File("config/default.json").writer().use {
        gson.toJson(config, it)
    }
}