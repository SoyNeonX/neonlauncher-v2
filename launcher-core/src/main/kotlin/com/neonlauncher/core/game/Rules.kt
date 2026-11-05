package com.neonlauncher.core.game

import com.fasterxml.jackson.core.type.TypeReference
import com.neonlauncher.common.util.Json
import com.neonlauncher.common.util.OsFamily
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Evaluación de las `rules` (SO / arquitectura / features) que traen librerías y argumentos. */
object Rules {

    private val RULE_LIST = object : TypeReference<List<Rule>>() {}

    fun allows(rules: List<Rule>, features: Map<String, Boolean>): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, features)) allowed = rule.action.equals("allow", ignoreCase = true)
        }
        return allowed
    }

    private fun matches(rule: Rule, features: Map<String, Boolean>): Boolean {
        rule.os?.let { os ->
            if (!os.name.isNullOrBlank() && os.name != OsFamily.current.id) return false
            if (!os.arch.isNullOrBlank() && os.arch != architecture()) return false
            if (!os.version.isNullOrBlank()) {
                val osVersion = System.getProperty("os.version").orEmpty()
                if (!runCatching { Regex(os.version).containsMatchIn(osVersion) }.getOrDefault(false)) return false
            }
        }
        rule.features?.forEach { (key, expected) ->
            if ((features[key] ?: false) != expected) return false
        }
        return true
    }

    /** `amd64`/`x86_64` → `x86_64`, `aarch64` → `arm64` (los nombres que usa Mojang). */
    fun architecture(): String = when (val arch = System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> arch
    }

    fun parse(nodes: com.fasterxml.jackson.databind.JsonNode?): List<Rule> =
        if (nodes == null || !nodes.isArray) emptyList() else Json.mapper.convertValue(nodes, RULE_LIST)
}

data class NativeJar(val file: Path, val exclude: List<String>)

/** Extrae los `.dll`/`.so` de las librerías `natives-*` (sólo lo necesitan versiones viejas). */
object NativesExtractor {

    fun prepare(jars: List<NativeJar>, target: Path) {
        Files.createDirectories(target)
        if (jars.isEmpty()) return
        clear(target)

        for (jar in jars) {
            runCatching {
                ZipFile(jar.file.toFile()).use { zip ->
                    for (entry in zip.entries()) {
                        if (entry.isDirectory) continue
                        val name = entry.name
                        if (name.startsWith("META-INF/")) continue
                        if (jar.exclude.any { name.startsWith(it) }) continue
                        val out = target.resolve(name).normalize()
                        if (!out.startsWith(target)) continue // zip-slip
                        out.parent?.let { Files.createDirectories(it) }
                        zip.getInputStream(entry).use { input ->
                            Files.copy(input, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }
            }
        }
    }

    private fun clear(dir: Path) {
        Files.list(dir).use { stream ->
            stream.toList().forEach { child ->
                runCatching {
                    if (Files.isDirectory(child)) clear(child)
                    Files.deleteIfExists(child)
                }
            }
        }
    }
}
