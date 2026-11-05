package com.neonlauncher.core.download

import com.fasterxml.jackson.databind.JsonNode
import com.neonlauncher.common.util.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

data class RemoteVersion(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: Instant?
)

/** Catálogo oficial de versiones de Mojang (`version_manifest_v2.json`). */
class MojangCatalog {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun versions(): List<RemoteVersion> {
        val request = HttpRequest.newBuilder(URI.create(MANIFEST_URL))
            .timeout(Duration.ofSeconds(25))
            .header("User-Agent", "NeonLauncher")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()} al pedir el catálogo")

        val root = Json.mapper.readTree(response.body())
        return root.path("versions").mapNotNull { node ->
            val id = node.path("id").asText("")
            val url = node.path("url").asText("")
            if (id.isBlank() || url.isBlank()) null
            else RemoteVersion(
                id = id,
                type = node.path("type").asText("release"),
                url = url,
                releaseTime = runCatching { Instant.parse(node.path("releaseTime").asText()) }.getOrNull()
            )
        }
    }

    companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}

internal fun JsonNode.textOrEmpty(field: String): String = path(field).asText("")
