package com.neonlauncher.core.auth

import com.fasterxml.jackson.databind.JsonNode
import com.neonlauncher.common.util.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

data class DeviceCode(
    val userCode: String,
    val verificationUri: String,
    val deviceCode: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int
)

/** Sesión de Microsoft ya resuelta a un token de Minecraft. */
data class MsSession(
    val name: String,
    val uuid: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant
) {
    fun isExpiringSoon(minutes: Long = 120): Boolean =
        Instant.now().plusSeconds(minutes * 60).isAfter(expiresAt)
}

/**
 * Flujo completo de Microsoft para Minecraft:
 * device code → Xbox Live → XSTS → login_with_xbox → perfil → entitlement.
 *
 * No guarda nada: devuelve la sesión y la app decide dónde persistirla.
 */
class MicrosoftAuth {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun login(
        onCode: (DeviceCode) -> Unit,
        onStatus: (String) -> Unit,
        isCancelled: () -> Boolean
    ): Result<MsSession> = runCatching {
        val code = requestDeviceCode()
        onCode(code)
        onStatus("Esperando que apruebes el acceso en el navegador…")

        val tokens = pollForToken(code, isCancelled)
        finishLogin(tokens.first, tokens.second, onStatus)
    }

    fun refresh(refreshToken: String): Result<MsSession> = runCatching {
        val json = postForm(
            AuthConfig.TOKEN_URL,
            listOf(
                "client_id" to AuthConfig.CLIENT_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "scope" to AuthConfig.SCOPE
            )
        )
        val access = json.text("access_token")
        val refresh = json.text("refresh_token")
        finishLogin(access, refresh, {})
    }

    /** Sube una skin (PNG 64x64) a la cuenta de Minecraft. */
    fun uploadSkin(accessToken: String, file: java.nio.file.Path, slim: Boolean): Result<Unit> = runCatching {
        val boundary = "----NeonLauncher${System.nanoTime()}"
        val image = java.nio.file.Files.readAllBytes(file)

        val head = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"variant\"\r\n\r\n")
            append(if (slim) "slim" else "classic").append("\r\n")
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n")
            append("Content-Type: image/png\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)

        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val body = head + image + tail

        val request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()}: ${response.body().take(180)}")
        }
    }

    // ------------------------------------------------------------ pasos

    private fun requestDeviceCode(): DeviceCode {
        val json = postForm(
            AuthConfig.DEVICE_CODE_URL,
            listOf("client_id" to AuthConfig.CLIENT_ID, "scope" to AuthConfig.SCOPE)
        )
        return DeviceCode(
            userCode = json.text("user_code"),
            verificationUri = json.text("verification_uri"),
            deviceCode = json.text("device_code"),
            intervalSeconds = json.path("interval").asInt(5).coerceAtLeast(1),
            expiresInSeconds = json.path("expires_in").asInt(900)
        )
    }

    private fun pollForToken(code: DeviceCode, isCancelled: () -> Boolean): Pair<String, String> {
        val deadline = Instant.now().plusSeconds(code.expiresInSeconds.toLong())
        var interval = code.intervalSeconds

        while (true) {
            if (isCancelled()) error("Inicio de sesión cancelado")
            if (Instant.now().isAfter(deadline)) error("El código expiró, probá de nuevo")
            Thread.sleep(interval * 1000L)

            val json = postForm(
                AuthConfig.TOKEN_URL,
                listOf(
                    "client_id" to AuthConfig.CLIENT_ID,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code" to code.deviceCode
                )
            )

            when (json.path("error").asText("")) {
                "" -> return json.text("access_token") to json.text("refresh_token")
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> error("El código expiró, probá de nuevo")
                "authorization_declined" -> error("Cancelaste el acceso en Microsoft")
                else -> error(json.path("error_description").asText("Error de Microsoft"))
            }
        }
    }

    private fun finishLogin(msAccess: String, msRefresh: String, onStatus: (String) -> Unit): MsSession {
        onStatus("Autenticando con Xbox Live…")
        val (xblToken, xblUhs) = xboxLive(msAccess)

        onStatus("Autorizando con XSTS…")
        val (xstsToken, xstsUhs) = xsts(xblToken, xblUhs)

        onStatus("Entrando a Minecraft…")
        val mc = postJson(
            AuthConfig.MC_LOGIN_URL,
            mapOf("identityToken" to "XBL3.0 x=$xstsUhs;$xstsToken"),
            emptyMap()
        )
        val mcToken = mc.text("access_token")
        val loginName = mc.path("username").asText("")
        val expiresIn = mc.path("expires_in").asLong(86_400)

        onStatus("Verificando que tengas el juego…")
        val entitlements = get(AuthConfig.MC_ENTITLEMENTS_URL, mcToken)
        if (entitlements != 200) {
            error("Tu cuenta no tiene Minecraft Java comprado (HTTP $entitlements)")
        }

        onStatus("Leyendo tu perfil…")
        val profile = postProfile(mcToken)

        // El nombre puede venir vacío si el perfil todavía no está propagado: usamos el
        // que devolvió login_with_xbox y, si tampoco está, el de la cuenta.
        val profileName = profile.path("name").asText("")
        val name = profileName.trim().ifBlank { loginName.trim() }.ifBlank { "Player" }

        return MsSession(
            name = name,
            uuid = profile.path("id").asText(""),
            accessToken = mcToken,
            refreshToken = msRefresh,
            expiresAt = Instant.now().plusSeconds(expiresIn)
        )
    }

    private fun xboxLive(accessToken: String): Pair<String, String> {
        val json = postJson(
            AuthConfig.XBL_URL,
            mapOf(
                "Properties" to mapOf(
                    "AuthMethod" to "RPS",
                    "SiteName" to "user.auth.xboxlive.com",
                    "RpsTicket" to "d=$accessToken"
                ),
                "RelyingParty" to "http://auth.xboxlive.com",
                "TokenType" to "JWT"
            ),
            emptyMap()
        )
        return json.text("Token") to json.path("DisplayClaims").path("xui").first().text("uhs")
    }

    private fun xsts(xblToken: String, expectedUhs: String): Pair<String, String> {
        val response = postRaw(
            AuthConfig.XSTS_URL,
            mapOf(
                "Properties" to mapOf(
                    "SandboxId" to "RETAIL",
                    "UserTokens" to listOf(xblToken)
                ),
                "RelyingParty" to "rp://api.minecraftservices.com/",
                "TokenType" to "JWT"
            ),
            emptyMap()
        )
        val json = Json.mapper.readTree(response.body())

        val errorCode = json.path("XErr").asLong(0)
        if (errorCode != 0L) {
            error(
                when (errorCode) {
                    2148916233L -> "Tu cuenta de Microsoft no tiene perfil de Xbox. Entrá una vez a xbox.com y probá de nuevo."
                    2148916238L -> "Es una cuenta de menor de edad: necesita aprobación de un adulto del grupo familiar."
                    2148916235L -> "Xbox Live no está disponible en el país de esa cuenta."
                    2148916227L -> "Esa cuenta está bloqueada por Microsoft."
                    else -> "Xbox rechazó la cuenta (código $errorCode)"
                }
            )
        }

        val uhs = json.path("DisplayClaims").path("xui").first().text("uhs")
        if (uhs != expectedUhs) error("Xbox devolvió un identificador distinto")
        return json.text("Token") to uhs
    }

    private fun postProfile(token: String): JsonNode {
        val request = HttpRequest.newBuilder(URI.create(AuthConfig.MC_PROFILE_URL))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) error("No se pudo leer el perfil de Minecraft (HTTP ${response.statusCode()})")
        return Json.mapper.readTree(response.body())
    }

    private fun get(url: String, token: String): Int {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    // ------------------------------------------------------------ HTTP

    private fun postForm(url: String, params: List<Pair<String, String>>): JsonNode {
        val body = params.joinToString("&") { (key, value) ->
            "$key=" + URLEncoder.encode(value, Charsets.UTF_8)
        }
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return Json.mapper.readTree(response.body())
    }

    private fun postRaw(url: String, payload: Any, headers: Map<String, String>): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .POST(HttpRequest.BodyPublishers.ofString(Json.mapper.writeValueAsString(payload)))
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun postJson(url: String, payload: Any, headers: Map<String, String>): JsonNode {
        val response = postRaw(url, payload, headers)
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} en $url")
        }
        return Json.mapper.readTree(response.body())
    }

    private fun JsonNode.text(field: String): String =
        path(field).asText("").ifBlank { error("Microsoft no devolvió '$field'") }
}
