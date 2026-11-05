package com.neonlauncher.common.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path

/** Punto único de configuración de Jackson: lo usan settings y los JSON de versión. */
object Json {

    val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.INDENT_OUTPUT, true)

    fun <T> read(path: Path, type: Class<T>): T = mapper.readValue(path.toFile(), type)

    fun write(path: Path, value: Any, indent: Boolean = true) {
        path.parent?.let { Files.createDirectories(it) }
        val writer = if (indent) mapper.writerWithDefaultPrettyPrinter() else mapper.writer()
        writer.writeValue(path.toFile(), value)
    }

    fun toJson(value: Any): String = mapper.writeValueAsString(value)
}
