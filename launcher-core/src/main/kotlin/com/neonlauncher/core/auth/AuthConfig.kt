package com.neonlauncher.core.auth

/**
 * Configuración del login de Microsoft.
 *
 * El `client_id` identifica a la **aplicación** frente a Microsoft, no a un usuario, y no es un
 * secreto. Hoy apunta al de PrismLauncher (público en su CMakeLists) como puente mientras el
 * registro propio en Entra ID no está disponible.
 *
 * ⚠️ Si alguna vez publicás NeonLauncher, registrá tu propia app y reemplazá este valor: usar el
 * de otro proyecto depende de su buena voluntad y puede revocarse.
 */
object AuthConfig {

    const val CLIENT_ID: String = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb"

    const val SCOPE: String = "XboxLive.signin offline_access"

    const val DEVICE_CODE_URL: String = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    const val TOKEN_URL: String = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    const val XBL_URL: String = "https://user.auth.xboxlive.com/user/authenticate"
    const val XSTS_URL: String = "https://xsts.auth.xboxlive.com/xsts/authorize"
    const val MC_LOGIN_URL: String = "https://api.minecraftservices.com/authentication/login_with_xbox"
    const val MC_PROFILE_URL: String = "https://api.minecraftservices.com/minecraft/profile"
    const val MC_ENTITLEMENTS_URL: String = "https://api.minecraftservices.com/entitlements/mcstore"
}
