package com.neonlauncher.core.auth

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Sonda para verificar que un `client_id` acepta device code. `gradlew :launcher-core:authProbe` */
fun main() {
    val clientId = System.getenv("MSA_CLIENT_ID") ?: AuthConfig.CLIENT_ID
    val body = listOf("client_id" to clientId, "scope" to AuthConfig.SCOPE)
        .joinToString("&") { (key, value) -> "$key=" + URLEncoder.encode(value, Charsets.UTF_8) }

    val request = HttpRequest.newBuilder(URI.create(AuthConfig.DEVICE_CODE_URL))
        .timeout(Duration.ofSeconds(25))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()

    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())

    println("client_id=$clientId")
    println("HTTP ${response.statusCode()}")
    println(response.body().take(900))
}
