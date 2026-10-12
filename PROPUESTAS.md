# PROPUESTAS — NeonLauncher (roadmap de mejoras)

> Documento de propuestas generado por el worker de `TAREA-003-propuestas`.
> **No se modificó código**: solo lectura de los 3 módulos (`launcher-common`, `launcher-core`, `launcher-ui`)
> y creación de este archivo. Cada propuesta está anclada a archivos reales del proyecto.

Fecha: 2026-10-12 · Alcance: proyecto raíz `D:\NeonLABS\I+D-Neon\apps\NeonLauncher`

---

## Resumen priorizado

| # | Mejora | Módulo / archivo principal | Impacto | Esfuerzo | Prioridad |
|---|---|---|---|---|---|
| 1 | Consolidar `ConfigManager` (colisión `class`/`object`) | `launcher-core/.../config/ConfigManager.kt` | Crítico (compilación) | Bajo | 🔴 P1 |
| 2 | Implementar autenticación Microsoft (login/refresh) | `launcher-core` (módulo nuevo) + `launcher-ui/.../login` | Crítico (funcional) | Alto | 🔴 P1 |
| 3 | `AutoUpdater`: URL configurable + verificación SHA-256 real | `launcher-core/.../update/AutoUpdater.kt` | Alto (seguridad) | Medio | 🔴 P1 |
| 4 | Corregir armado de comando/classpath en el launcher | `launcher-core/.../launch/MinecraftLauncher.kt` | Alto (arranque MC) | Medio | 🟠 P2 |
| 5 | Evitar acumulación en memoria del log del juego | `launcher-core/.../launch/MinecraftLauncher.kt` | Medio | Bajo | 🟠 P2 |
| 6 | Agregar suite de tests unitarios (hoy = 0) | `launcher-*/src/test/**` | Alto (calidad) | Medio | 🟠 P2 |
| 7 | Centralizar `OkHttpClient` con timeouts y pool | `launcher-core` (DI/config) | Medio | Bajo | 🟡 P3 |
| 8 | UI: no bloquear el hilo JavaFX + reutilizar vistas | `launcher-ui/.../controller/MainController.kt` | Medio | Medio | 🟡 P3 |

---

## 1. Consolidar `ConfigManager`: colisión entre `class` y `object` (🔴 P1)

- **Qué:** el archivo declara dos elementos top-level con el **mismo nombre** en el mismo
  paquete: `class ConfigManager(private val baseDir: File)` y, más abajo,
  `object ConfigManager { fun getDefaultBaseDir(): File }`. En Kotlin eso es una
  **redeclaración en conflicto** ("Conflicting declarations") y es lo primero a resolver;
  el resto del proyecto depende de que `launcher-core` compile. La intención clara es que
  `getDefaultBaseDir()` sea un miembro estático → debe migrar a un `companion object` dentro
  de la clase.
- **Dónde:** `launcher-core/src/main/kotlin/com/neonlauncher/core/config/ConfigManager.kt`
  (líneas 19 y 75-84). El `MainController` ya llama `ConfigManager.getDefaultBaseDir()`, que
  con el companion object sigue funcionando sin cambiar el call site.
- **Por qué:** bloquea la compilación del módulo `launcher-core` y, por transitividad, de la UI.
  Es condición previa para poder correr cualquier test o build de release.
- **Esfuerzo:** **Bajo** (mover el método a `companion object`).

## 2. Implementar autenticación Microsoft real (🔴 P1)

- **Qué:** el modelo ya soporta la cuenta (`MicrosoftAccount` con `accessToken`,
  `refreshToken`, `expiresAt`, `isExpired()`, `isExpiringSoon()`), y existe la excepción
  `AuthException` — pero **no hay ninguna implementación de login/refresh** en `launcher-core`.
  `MicrosoftLauncher`/`MinecraftLauncher` recibe un `accessToken: String` ya resuelto y el
  `Profile` puede venir sin cuenta (cae a `"Player"` y UUID cero). Falta el flujo OAuth
  (device-code o loopback) + endpoint de refresco + persistencia del token vía `ConfigManager`.
- **Dónde:** nuevo paquete `launcher-core/src/main/kotlin/com/neonlauncher/core/auth/`
  (p. ej. `MicrosoftAuthManager.kt`), consumido desde `launcher-ui/src/main/resources/fxml/login.fxml`.
  Reutilizar `com.neonlauncher.common.exception.AuthException` y `Profile.microsoftAccount`.
- **Por qué:** sin auth el launcher no puede iniciar una sesión de Minecraft online; es el
  hueco funcional más grande. También habilita skins/capes que el modelo ya contempla
  (`skinUrl`, `capeUrl`).
- **Esfuerzo:** **Alto** (flujo OAuth, refresco, manejo de expiración y errores).

## 3. `AutoUpdater`: URL configurable y verificación de hash real (🔴 P1)

- **Qué:** el chequeo de updates está **hardcodeado** a
  `https://api.github.com/repos/neonlabs/NeonLauncher/releases/latest` (ignora
  `LauncherConfig.serverManifestUrl`/`updateChannel`), y al construir el `ReleaseInfo` asigna
  `sha256 = ""` con el comentario "Would need separate verification". El código de verificación
  ya existe (`calculateSha256`) pero **nunca se activa** porque el hash siempre llega vacío → se
  descarga y ejecuta un instalador **sin verificar integridad**.
- **Dónde:** `launcher-core/src/main/kotlin/com/neonlauncher/core/update/AutoUpdater.kt`
  (líneas 51, 73-81, 116-123). Inyectar la URL/canal desde `LauncherConfig`
  (`launcher-common/.../model/LauncherConfig.kt`: `serverManifestUrl`, `updateChannel`) y leer el
  hash desde los assets del release (o un `checksums.txt`).
- **Por qué:** riesgo de seguridad (ejecutar binario no verificado) y funcionalidad rota
  (repo placeholder inexistente ⇒ el update falla siempre contra `neonlabs/NeonLauncher`).
- **Esfuerzo:** **Medio**.

## 4. Corregir el armado de comando y classpath del launcher (🟠 P2)

- **Qué:** varios defectos en `MinecraftLauncher`:
  - `--server`/`--port` se agregan **siempre** (líneas 107-111) aunque no se quiera conexión
    directa a un server.
  - `buildClasspath` agrega mods con `manifest.mods.filter { it.required }` (línea 140) e
    **ignora `Profile.enabledMods`** (el usuario no puede desactivar/activar mods).
  - Los natives se resuelven mal: `resolveLibraryPath` (línea 152) arma el nombre con el
    classifier del *maven coord*, pero `extractNatives` (línea 195) busca un archivo
    `...-<classifier>.jar` **sin usar el classifier del maven coord** y descarta las exclusiones
    de `ExtractRules.exclude`; los natives de manifiestos modernos suelen no existir en esa ruta.
  - El jar cliente se busca en `librariesDir/com/mojang/minecraft/...` (línea 146) y no en la
    ruta estándar de `versions/<ver>/<ver>.jar`.
- **Dónde:** `launcher-core/src/main/kotlin/com/neonlauncher/core/launch/MinecraftLauncher.kt`
  (`buildCommand` 53-119, `buildClasspath` 121-150, `prepareNatives`/`extractNatives` 164-210).
- **Por qué:** son causas directas de fallos de arranque (clase no encontrada, natives
  faltantes) y de UX (mods que no se pueden desactivar).
- **Esfuerzo:** **Medio**.

## 5. No acumular el log del juego en memoria (🟠 P2)

- **Qué:** el hilo lector hace `Thread(process.inputStream::readAllBytes)` y recién al terminar
  loguea todo como un único `String` (líneas 44-48). Para una sesión larga esto **acumula
  megabytes en memoria** y no muestra salida en vivo; además conviene nombrar el hilo
  (`setName`/`isDaemon = true`) para que no impida el cierre de la JVM.
- **Dónde:** `launcher-core/src/main/kotlin/com/neonlauncher/core/launch/MinecraftLauncher.kt`
  (líneas 44-48).
- **Por qué:** consumo de memoria creciente + pérdida de feedback en vivo (relevante si
  `LauncherConfig.showConsole` está activo).
- **Esfuerzo:** **Bajo** (leer por líneas con `bufferedReader()` y loguear incremental).

## 6. Suite de tests unitarios: hoy no existe ninguna (🟠 P2)

- **Qué:** no hay directorios `src/test` en ningún módulo, aunque `build.gradle.kts` raíz ya
  configura JUnit 5 + AssertJ. Hay lógica pura, ideal y barata de cubrir:
  `Result` (`launcher-common/.../util/Result.kt`), parseo de versiones de Java
  (`JavaManager.parseVersionOutput`), `compareVersions` (`AutoUpdater`),
  `ManifestDiffCalculator.calculateDiff` y `resolveLibraryPath` (coordenadas Maven).
- **Dónde:** nuevos `launcher-common/src/test/...`, `launcher-core/src/test/...`.
- **Por qué:** sin red de seguridad cualquier refactor de los puntos 3-5 puede romper el arranque;
  los parsers/portadores de reglas son los puntos más propensos a bugs silenciosos.
- **Esfuerzo:** **Medio** (empezar por 3-4 clases de alto valor).

## 7. Centralizar `OkHttpClient` (timeouts + pool compartido) (🟡 P3)

- **Qué:** `ManifestManager`, `ModDownloader` y `AutoUpdater` reciben un `OkHttpClient` por
  constructor pero **nada lo configura**: no hay timeouts de conexión/lectura, ni tamaño de pool,
  ni `retryOnConnectionFailure`. Cada instancia que se cree tiene su propio pool/dispatcher.
- **Dónde:** `launcher-core` (un factory/inyección, p. ej. junto a `ConfigManager`) consumido por
  `manifest/ManifestManager.kt`, `mods/ModDownloader.kt`, `update/AutoUpdater.kt`.
- **Por qué:** descargas y chequeos pueden colgarse indefinidamente; compartir el cliente mejora
  el reuso de conexiones y hace el comportamiento de red predecible/testeable.
- **Esfuerzo:** **Bajo**.

## 8. UI: no bloquear el hilo JavaFX y no recrear vistas (🟡 P3)

- **Qué:** `MainController.initialize`/`loadConfig` y `loadView` hacen trabajo pesado
  (crear `ConfigManager`, leer/escribir JSON) **en el Application Thread** de JavaFX, y
  `loadView` instancia un `FXMLLoader` nuevo en cada navegación (sin cachear controladores).
  Además `applyTheme` limpia *todos* los stylesheets y los re-registra con `?: ""`, que puede
  insertar una entrada vacía. `LauncherUI.build` tampoco maneja `IllegalStateException` de FXML
  fuera de `IOException`.
- **Dónde:** `launcher-ui/src/main/kotlin/com/neonlauncher/ui/controller/MainController.kt`
  (36-42, 89-99, 106-115) y `launcher-ui/src/main/kotlin/com/neonlauncher/ui/LauncherApp.kt` (23-43).
- **Por qué:** I/O en el FX thread congela la ventana durante el arranque; recrear vistas pierde
  estado y es ineficiente; capturar solo `IOException` deja otros fallos de `FXMLLoader` sin
  manejar.
- **Esfuerzo:** **Medio** (mover I/O a background, cache de vistas, manejo de excepciones ampliado).

---

## Notas de método

- Lectura completa de los 11 archivos Kotlin y de los 3 `build.gradle.kts` de los módulos.
- Las rutas y números de línea citados corresponden al estado del repositorio al 2026-10-12.
- Ninguna propuesta implica cambios de código en esta tarea; quedan para el roadmap del dueño.
