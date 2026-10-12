# NeonLauncher — Launcher de Minecraft

Launcher de Minecraft de escritorio escrito en **Kotlin + JavaFX 21**, organizado como proyecto
**Gradle multi-módulo** (`launcher-common`, `launcher-core`, `launcher-ui`).

El proyecto está **en desarrollo temprano**: la estructura y los modelos están definidos, pero
**el código no compila en su estado actual** (ver [Estado de desarrollo](#estado-de-desarrollo)).
Este README describe el proyecto real y verificado contra el código; no documenta funcionalidades
que todavía no existen.

- **groupId / version:** `com.neonlauncher` / `1.0.0-SNAPSHOT`
- **Kotlin:** 1.9.24 · **Gradle (wrapper):** 8.5 · **JavaFX:** 21.0.6

---

## Módulos

Cadena de dependencias: `launcher-ui → launcher-core → launcher-common` (sin ciclos).

| Módulo | Plugin Gradle | Rol | Depende de |
|---|---|---|---|
| `launcher-common` | `java-library` + `kotlin` | Modelos de datos compartidos, `Result` y jerarquía de excepciones. Sin lógica de negocio. | Jackson (API), SLF4J |
| `launcher-core` | `java-library` + `kotlin` | Lógica de negocio: detección de Java, configuración, manifiestos, descarga de mods, armado/lanzado del proceso de Minecraft y auto-actualización. | `:launcher-common`, Jackson, SLF4J, Logback, BouncyCastle |
| `launcher-ui` | `application` + `kotlin` | Interfaz JavaFX (`Application`), controlador único de navegación, FXML y temas CSS. | `:launcher-core`, JavaFX, Ikonli, FormsFX, Guava |

### `launcher-common` — modelos y utilidades

Paquete raíz `com.neonlauncher.common`:

- `model/LauncherConfig.kt` — `LauncherConfig` (config global del launcher) y los enums
  `UpdateChannel` (`STABLE`/`BETA`/`DEV`) y `Theme` (`DARK`/`LIGHT`/`SYSTEM`).
- `model/Manifest.kt` — `Manifest` (versión de MC/loader, librerías, mods, args, config de server),
  `LoaderType` (`FORGE`/`FABRIC`/`QUILT`/`VANILLA`), `Library`, `ExtractRules`,
  **`ModEntry` (tipo top-level)**, `ModSide`, `ServerConfig`.
- `model/Profile.kt` — `ProfileContainer` (perfiles + perfil seleccionado), `Profile` (cuenta,
  Java, memoria, ventana, mods habilitados) y `MicrosoftAccount` (tokens + datos de sesión).
- `util/Result.kt` — `sealed class Result<out T>` con `Success`/`Failure` y combinadores
  `map`/`flatMap`/`fold`/`onSuccess`/`onFailure`.
- `exception/LauncherException.kt` — base `LauncherException` + `AuthException`,
  `ManifestException`, `ModDownloadException`, `HashMismatchException`, `LaunchException`,
  `ConfigException`, `UpdateException`, `JavaNotFoundException`, `NetworkException`.

### `launcher-core` — lógica

Paquete raíz `com.neonlauncher.core`:

| Archivo | Clases | Responsabilidad |
|---|---|---|
| `java/JavaManager.kt` | `JavaManager`, `JavaRuntime`, `JavaVersion` | Detecta JDKs (`JAVA_HOME`, `PATH` y rutas típicas por SO) y parsea versión/vendor. |
| `config/ConfigManager.kt` | `ConfigManager` | Lee/escribe `launcher-config.json` y `profile.json` (Jackson; escritura vía archivo temporal) y resuelve el directorio base por SO. |
| `manifest/ManifestManager.kt` | `ManifestManager`, `ManifestFetchResult`, `ManifestDiffCalculator` | Carga/guarda manifiesto local y lo descarga con `ETag`/`304`; calcula el diff de mods. |
| `mods/ModDownloader.kt` | `ModDownloader`, `DownloadResult`, `ProgressUpdate` | Descarga concurrente de mods con verificación de hash SHA-256 y reporte de progreso. |
| `launch/MinecraftLauncher.kt` | `MinecraftLauncher`, `LaunchConfig` | Arma el comando y el classpath, prepara los *natives* y lanza el proceso de Minecraft. |
| `update/AutoUpdater.kt` | `AutoUpdater`, `ReleaseInfo`, `UpdateCheckResult` | Consulta releases en GitHub, descarga el instalador y (opcionalmente) lo verifica y ejecuta. |

### `launcher-ui` — interfaz

Paquete raíz `com.neonlauncher.ui`:

- `LauncherApp.kt` — `class LauncherApp : Application`. Punto de entrada JavaFX: fija título/tamaño
  mínimo, carga `main.fxml`, aplica `main.css`, coloca el ícono y expone `main(...)`.
- `controller/MainController.kt` — `MainController`. Único controlador: navegación entre vistas
  (login/home/mods/profile/settings), carga de config y perfiles vía `ConfigManager`, y aplicación
  de tema.

Recursos (`src/main/resources`):

- `fxml/`: `main.fxml` (barra de navegación + área de contenido + barra de estado; único FXML con
  `fx:controller`), y `login.fxml`, `home.fxml`, `mods.fxml`, `profile.fxml`, `settings.fxml`.
- `css/`: `main.css` + `css/themes/{dark,light,system}.css`.
- `images/icon.png`.

---

## Estructura del proyecto

```
NeonLauncher/
├── build.gradle.kts            # plugin Kotlin 1.9.24 (apply false) + config común de subprojects
├── settings.gradle.kts         # incluye launcher-common, launcher-core, launcher-ui
├── gradlew / gradlew.bat       # wrapper (Gradle 8.5)
├── gradle/wrapper/             # gradle-wrapper.jar + gradle-wrapper.properties
├── launcher-common/            # 5 archivos .kt
├── launcher-core/              # 6 archivos .kt
└── launcher-ui/                # 2 archivos .kt + 6 .fxml + 4 .css + 1 .png
```

---

## Requisitos

- **JDK 17 o superior** (recomendado **JDK 21**, que es lo que usa JavaFX 21 y lo que asume el
  modelo de datos). El código utiliza APIs de Java 11+ (p. ej. `Files.readString`), por lo que
  **Java 8 no alcanza**.
- **Gradle** no hace falta instalarlo: el wrapper (`gradlew` / `gradlew.bat`) baja Gradle 8.5.
- No hay *toolchain* de Java configurado en los `build.gradle.kts`, así que **el JDK usado es el
  que esté en `JAVA_HOME`**. Si en el `PATH` hay un Java 8 (caso de la máquina de referencia) hay
  que fijar `JAVA_HOME` a un JDK 21 antes de invocar el wrapper.

---

## Compilación y corrida

> ⚠️ **Actualmente los siguientes comandos fallan**: el proyecto no compila (ver
> [Estado de desarrollo](#estado-de-desarrollo)). Quedan documentados como el procedimiento
> previsto una vez resueltos los bloqueantes.

Windows (PowerShell):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"   # ajustar a tu JDK 17+/21
.\gradlew.bat build                 # compila los tres módulos
.\gradlew.bat :launcher-ui:run      # arranca la aplicación
```

Linux / macOS:

```bash
export JAVA_HOME=/ruta/a/jdk-21
./gradlew build
./gradlew :launcher-ui:run
```

La tarea `:launcher-ui:run` agrega los `--add-exports`/`--add-opens` necesarios para JavaFX
(`javafx.graphics`, `javafx.controls`). Las dependencias de JavaFX, Jackson, Ikonli, etc. se bajan
de Maven Central (no hay que instalarlas a mano).

---

## Estado de desarrollo

Documentado por verificación real el **2026-10-12** (se ejecutó
`gradlew --no-daemon :launcher-ui:classes` con **JDK 21**; ver más abajo). Resumen honesto:

### 🟢 Qué existe

- Estructura multi-módulo correcta y cadena de dependencias sin ciclos.
- Modelos de datos completos (`LauncherConfig`, `Manifest`, `Profile`, `MicrosoftAccount`, …)
  y utilidades (`Result`, jerarquía de excepciones).
- Esqueleto de la lógica de `launcher-core` (detección de Java, config, manifiestos, descarga de
  mods, armado de comando, auto-update) y esqueleto de la UI JavaFX (arranque, navegación, FXML,
  temas).

### 🔴 Qué no funciona todavía

1. **No compila.** Verificado con Gradle + JDK 21:
   - `launcher-common`: `Result.kt:22` — *"Type parameter T is declared as 'out' but occurs in 'in'
     position"* (incompatibilidad entre la varianza `out T` y el parámetro `default: T` de
     `getOrElse`).
   - `launcher-core` (falla en cadena): import inutilizable `import com.neonlauncher.common.util.Result.runCatching`
     (*Unresolved reference*), y múltiples `Type mismatch: kotlin.Result<…> but …Result<…> was expected`
     (se mezcla el `Result` propio con `kotlin.Result`).
   - `launcher-core`: `Unresolved reference: okhttp3` — tres archivos usan **OkHttp**, que **no
     está declarado** en ningún `build.gradle.kts`.
   - `launcher-core`: `ConfigManager` está declarado dos veces en el mismo paquete (`class` + `object`).
   - `launcher-core`: `Manifest.ModEntry` no existe (el tipo real es `ModEntry`, top-level).
   - `launcher-core`: `ManifestManager.kt` usa `SerializationFeature` sin importarlo.
   - `MinecraftLauncher.kt` encadena errores de tipos (`File`/`Path`, `resolve`, `toFile`).
2. **La UI no arrancaría** aunque compilara: el `application { mainClass }` apunta a
   `com.neonlauncher.LauncherApp`, pero la clase real es **`com.neonlauncher.ui.LauncherApp`**.
3. **FXML con problemas**: `main.fxml` usa `<StatusBar>` (no existe en JavaFX), `mods.fxml`
   referencia un `ModActionCellFactory` inexistente y `profile.fxml`/`settings.fxml` usan
   `Spinner initialValue` (propiedad inexistente). Además, login/home/mods/profile/settings no
   declaran `fx:controller`, por lo que su lógica **no está conectada**.
4. **Temas inoperantes**: `main.css` y `themes/*.css` usan variables CSS (`var(--x)`), que el motor
   CSS de JavaFX **no soporta**; el cambio dark/light/system no tiene efecto.
5. **Sin tests**: los `src/test` no existen (aunque la raíz ya configura JUnit 5 + AssertJ).
6. **Dependencias declaradas y no usadas**: BouncyCastle (`launcher-core`), FormsFX, Guava y
   `javafx-web` (`launcher-ui`); Jackson duplicado en `core`.
7. **Sin autenticación Microsoft implementada**: el modelo `MicrosoftAccount`, la excepción
   `AuthException` y el lugar donde se usaría el token existen, pero **no hay flujo de login/refresh**
   en `launcher-core`.

### Verificación realizada

- Comando: `.\gradlew.bat --no-daemon :launcher-ui:classes` con
  `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`.
- Resultado: **BUILD FAILED**, deteniéndose en `:launcher-common:compileKotlin` y, para el detalle de
  `launcher-core`, repitiendo la corrida sobre una **copia temporal** (el árbol real no se modificó;
  los `build/` y `.gradle/` generados por la verificación fueron eliminados).

### Dónde está cada detalle

Un inventario más extenso, con líneas y evidencia, está en:

- [`AUDITORIA.md`](AUDITORIA.md) — auditoría técnica (dependencias, riesgos, bloqueantes).
- [`PROPUESTAS.md`](PROPUESTAS.md) — roadmap priorizado de mejoras.

---

## Configuración de usuario

`ConfigManager` resuelve el directorio base por sistema operativo y escribe ahí dos archivos JSON:

| SO | Directorio |
|---|---|
| Windows | `%APPDATA%\NeonLauncher` |
| macOS | `~/Library/Application Support/NeonLauncher` |
| Linux | `~/.neonlauncher` |

- `launcher-config.json` — `LauncherConfig` (canal de update, URLs de manifiesto/mods, tema,
  idioma, hilos de descarga, verificación de hashes, …).
- `profile.json` — `ProfileContainer` (perfiles y perfil seleccionado).

Si no existen, se crean con valores por defecto al primer arranque. Las URLs por defecto
(`api.example.com`, `cdn.example.com`) son *placeholders*.

---

## Notas

- Los `build/` y `.gradle/` son cachés regenerables; se pueden borrar sin problema.
- Esta carpeta **no** es un repositorio Git (`git` no está inicializado aquí) y no hay CI/`.gitignore`.
