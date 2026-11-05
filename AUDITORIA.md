> ⚠️ **Documento histórico (versión 1.x).** Describe el código anterior a la reescritura 2.0 y se
> conserva como registro de los problemas encontrados. **No** refleja el estado actual: ver
> [`README.md`](README.md).

# AUDITORÍA TÉCNICA — NeonLauncher

> Entregable de `TAREA-002-auditoria` (worker). **Auditoría de solo lectura**: no se modificó
> ningún archivo fuente ni de build. El único archivo creado es este `AUDITORIA.md`.

- **Fecha:** 2026-10-12
- **Alcance:** `D:\NeonLABS\I+D-Neon\apps\NeonLauncher` (raíz + módulos `launcher-common`,
  `launcher-core`, `launcher-ui`).
- **Método:** lectura completa de los 13 archivos Kotlin, los 6 FXML, los 4 CSS, los 4 archivos
  de build (`settings.gradle.kts` + raíz + 3 módulos) y el wrapper. Búsquedas dirigidas para
  verificar uso real de dependencias. Se ejecutó **solo** `gradlew --version` (ver §7); **no** se
  corrió compilación ni tarea de build.
- **Trabajo concurrente:** en la misma carpeta otro worker generó `PROPUESTAS.md`
  (`TAREA-003-propuestas`) a las 15:26 del 2026-10-12. No fue tocado. Varios hallazgos coinciden.

---

## 1. Resumen ejecutivo

El proyecto está **estructuralmente completo** (modelos, lógica core y UI FXML/CSS) pero en el
estado actual **no compila** y, aun compilando, **la UI no arrancaría**. Los bloqueantes son de
configuración y de referencias, no de diseño:

1. 🔴 **`launcher-core` no declara OkHttp**, pero 3 archivos usan `okhttp3.*` → no compila.
2. 🔴 **`ConfigManager` colisiona**: `class ConfigManager` y `object ConfigManager` en el mismo
   paquete → "conflicting declarations".
3. 🔴 **`Manifest.ModEntry` no existe**: `ModEntry` es top-level; se referencia como anidado → no compila.
4. 🔴 **`mainClass` apunta a una clase inexistente** (`com.neonlauncher.LauncherApp`; la real es
   `com.neonlauncher.ui.LauncherApp`) → `:launcher-ui:run` falla.
5. 🔴 **`main.css` y los temas usan `var(--x)`**: JavaFX CSS no soporta variables CSS → el sistema
   de temas (dark/light/system) es inoperante.
6. 🔴 **FXML inválido**: `main.fxml` usa `<StatusBar>` (no existe en JavaFX) y `mods.fxml`
   referencia `<ModActionCellFactory/>` inexistente y `PropertyValueFactory`/`CheckBoxTableCell`
   sin import.

Además: **cero tests**, dependencias declaradas y no usadas (BouncyCastle, FormsFX, Guava,
`javafx-web`), versiones con atraso menor, y riesgo de arrancar Gradle con Java 8 si no se fija
`JAVA_HOME`.

---

## 2. Inventario del proyecto

```
NeonLauncher/
├── build.gradle.kts            # raíz: plugin Kotlin 1.9.24 (apply false), config común subprojects
├── settings.gradle.kts         # 3 módulos incluidos
├── gradlew / gradlew.bat       # wrapper
├── gradle/wrapper/
│   ├── gradle-wrapper.jar          (43.462 bytes)
│   └── gradle-wrapper.properties   → gradle-8.5-bin.zip
├── README.md
├── PROPUESTAS.md               # creado por TAREA-003 (worker concurrente)
├── launcher-common/            # 5 .kt — modelos + utilidades
├── launcher-core/              # 6 .kt — lógica (java/config/manifest/mods/launch/update)
└── launcher-ui/                # 2 .kt + 6 FXML + 4 CSS + 1 png
```

- **Total Kotlin:** 13 archivos. **FXML:** 6. **CSS:** 4. **Imágenes:** 1 (`images/icon.png`).
- **No hay directorios `src/test`** en ningún módulo. **No hay** CI, `.gitignore`, LICENSE ni
  repositorio `.git` dentro de la carpeta.

### 2.1 Módulos

| Módulo | Plugin | Rol | Depende de |
|---|---|---|---|
| `launcher-common` | `java-library` + `kotlin` | Modelos y utilidades compartidas | Jackson (API), SLF4J |
| `launcher-core` | `java-library` + `kotlin` | Lógica de negocio/red/proceso | `:launcher-common`, Jackson, SLF4J, Logback, BouncyCastle |
| `launcher-ui` | `application` + `kotlin` | UI JavaFX + temas | `:launcher-core`, JavaFX 21, Ikonli, FormsFX, Guava |

Cadena de dependencias: `launcher-ui → launcher-core → launcher-common`. Correcta y sin ciclos.

---

## 3. Dependencias

### 3.1 Raíz (`build.gradle.kts`)

| Dependencia | Versión | Ámbito |
|---|---|---|
| `org.jetbrains.kotlin:kotlin-gradle-plugin` (via `kotlin("jvm")`) | 1.9.24 | apply false |
| `org.junit:junit-bom` | 5.11.0 | `testImplementation` (subprojects) |
| `org.junit.jupiter:junit-jupiter` | (BOM) | `testImplementation` |
| `org.assertj:assertj-core` | 3.25.3 | `testImplementation` |

Config común: `useJUnitPlatform()`, encoding UTF-8, `-Xlint:unchecked,deprecation,-parameters`.

### 3.2 `launcher-common`

| Dependencia | Versión | Tipo | ¿Usada? |
|---|---|---|---|
| `jackson-annotations` | 2.17.1 | `api` | ✅ (`@JsonProperty`, `@JsonIgnoreProperties`) |
| `jackson-databind` | 2.17.1 | `api` | ✅ (expuesta a core) |
| `jackson-datatype-jsr310` | 2.17.1 | `api` | ✅ (`Instant`) |
| `jackson-module-kotlin` | 2.17.1 | `api` | ✅ |
| `slf4j-api` | 2.0.13 | `implementation` | ⚠️ declarada; **no se usa** en las 5 clases de common |

### 3.3 `launcher-core`

| Dependencia | Versión | ¿Usada? |
|---|---|---|
| `project(":launcher-common")` | — | ✅ |
| `jackson-databind` | 2.17.1 | ✅ (ObjectMapper) — **duplicada** (ya llega por `api` de common) |
| `jackson-datatype-jsr310` | 2.17.1 | ✅ — duplicada |
| `jackson-module-kotlin` | 2.17.1 | ✅ — duplicada |
| `slf4j-api` | 2.0.13 | ✅ |
| `logback-classic` | 1.5.6 | ✅ (binding) |
| `org.bouncycastle:bcprov-jdk18on` | 1.78.1 | ❌ **NO usada** (ningún import `org.bouncycastle.*`) |
| `okhttp3` (OkHttp) | — | ⚠️ **USADA pero NO declarada** → bloqueante de compilación |

### 3.4 `launcher-ui`

| Dependencia | Versión | ¿Usada? |
|---|---|---|
| `project(":launcher-core")` | — | ✅ |
| `javafx-controls` | 21.0.6 | ✅ |
| `javafx-fxml` | 21.0.6 | ✅ |
| `javafx-graphics` | 21.0.6 | ✅ (implícita) |
| `javafx-web` | 21.0.6 | ❌ sin `WebView`/`javafx.scene.web` en el código |
| `ikonli-javafx` | 12.3.1 | ✅ (`FontIcon`) |
| `ikonli-materialdesign2` | 12.3.1 | ✅ (`MaterialDesignM`) |
| `com.dlsc.formsfx:formsfx-core` | 11.2.1 | ❌ **NO usada** |
| `guava` | 33.0.0-jre | ❌ **NO usada** |

### 3.5 Wrapper / entorno

| Ítem | Valor |
|---|---|
| Gradle (wrapper) | **8.5** (`gradle-8.5-bin.zip`) |
| `distributionSha256Sum` | ❌ ausente (hygiene de supply-chain) |
| `java` en PATH | **1.8.0_501 (Java 8)** ⚠️ |
| `JAVA_HOME` | **unset** ⚠️ |
| JDK real disponible | `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` (javac 21.0.11) |
| Caché `.gradle/wrapper/dists` | `gradle-8.10.2-all`, `gradle-8.5-bin` |

---

## 4. Inventario de clases por módulo

### 4.1 `launcher-common` (5 archivos)

| Archivo / paquete | Tipo | Responsabilidad |
|---|---|---|
| `common/util/Result.kt` | `sealed class Result<out T>` | `Success`/`Failure` + `map/flatMap/fold/runCatching`. Sombra a `kotlin.Result` (intencional, pero puede confundir imports). |
| `common/exception/LauncherException.kt` | 1 base + 9 excepciones | `AuthException`, `ManifestException`, `ModDownloadException`, `HashMismatchException`, `LaunchException`, `ConfigException`, `UpdateException`, `JavaNotFoundException`, `NetworkException`. |
| `common/model/LauncherConfig.kt` | `data class` + 2 enums | Config global; `UpdateChannel`, `Theme`. Defaults con URLs placeholder. |
| `common/model/Manifest.kt` | `data class` + enums + 4 modelos | `Manifest`, `LoaderType`, `Library`, `ExtractRules`, `ModEntry`, `ModSide`, `ServerConfig`. `ModEntry` es **top-level**. |
| `common/model/Profile.kt` | `data class` ×2 | `ProfileContainer`, `Profile`, `MicrosoftAccount` (tokens en claro). |

### 4.2 `launcher-core` (6 archivos)

| Archivo / paquete | Tipo | Responsabilidad | Observación |
|---|---|---|---|
| `core/java/JavaManager.kt` | `JavaManager`, `JavaRuntime`, `JavaVersion` | Detección de JDKs (JAVA_HOME, PATH, rutas por SO) y parseo de versión/vendor | Parser de versión con 3ª regex laxa (puede capturar cualquier número). |
| `core/config/ConfigManager.kt` | **`class` + `object` con el mismo nombre** | Carga/guarda `launcher-config.json` y `profile.json` (Jackson, escritura "atómica") | 🔴 conflicto de declaración; `writeAtomic` no crea el directorio base. |
| `core/manifest/ManifestManager.kt` | `ManifestManager`, `ManifestFetchResult`, `ManifestDiffCalculator` | Descarga remota con ETag/304, cache local, diff de mods | 🔴 usa OkHttp no declarado; 🔴 `SerializationFeature` sin import; 🔴 `Manifest.ModEntry`. |
| `core/mods/ModDownloader.kt` | `ModDownloader`, `DownloadResult`, `ProgressUpdate` | Descarga concurrente de mods, verificación SHA-256, progreso | 🔴 usa OkHttp no declarado; `Response` import sin uso. |
| `core/launch/MinecraftLauncher.kt` | `MinecraftLauncher`, `LaunchConfig` | Arma comando, classpath, natives; lanza el proceso | Varios defectos de armado (ver §5). |
| `core/update/AutoUpdater.kt` | `AutoUpdater`, `ReleaseInfo`, `UpdateCheckResult`, `GitHubRelease/Asset` | Chequeo GitHub Releases, descarga y ejecución de instalador | URL hardcodeada, `sha256=""` (verificación muerta). |

### 4.3 `launcher-ui` (2 archivos + recursos)

| Archivo / paquete | Tipo | Responsabilidad |
|---|---|---|
| `ui/LauncherApp.kt` | `class LauncherApp : Application` | Arranque JavaFX, carga `main.fxml`, icono, `stop()`. |
| `ui/controller/MainController.kt` | `class MainController` | Navegación entre vistas, carga config/perfiles, aplica tema. **Único controller**. |

**Recursos FXML (6):** `main.fxml` (toolbar + nav + content + status), `login.fxml`, `home.fxml`,
`mods.fxml`, `profile.fxml`, `settings.fxml`. Solo `main.fxml` declara `fx:controller`
(`MainController`); el resto **no tiene controller**.

**Recursos CSS (4):** `main.css` (428 líneas) + `themes/{dark,light,system}.css`.

---

## 5. Riesgos y obsolescencias detectadas

### 🔴 Bloqueantes de compilación (`launcher-core`)

| ID | Hallazgo | Evidencia |
|---|---|---|
| **B1** | **OkHttp usado pero no declarado.** 3 archivos importan `okhttp3.*` y reciben `OkHttpClient`; ningún `build.gradle.kts` declara OkHttp. | `ManifestManager.kt:12-14,24`; `ModDownloader.kt:8-10,23`; `AutoUpdater.kt:10-11,39`; `launcher-core/build.gradle.kts` (sin okhttp). |
| **B2** | **`ConfigManager` declarado dos veces** (`class` y `object`) en el mismo paquete → "conflicting declarations". El `getDefaultBaseDir()` que usa `MainController` (`MainController.kt:37`) debe pasar a `companion object`. | `ConfigManager.kt:19` (class) y `:75-84` (object). |
| **B3** | **`Manifest.ModEntry` no existe.** `ModEntry` es top-level en `Manifest.kt:53`; se referencia como si estuviera anidado en `Manifest`. | `ModDownloader.kt:45,59`; `ManifestManager.kt:85-88,95-97`. |
| **B4** | **`SerializationFeature` sin import** en `ManifestManager` → referencia no resuelta. | `ManifestManager.kt:32` (falta `import ...SerializationFeature`). |

> Con B1–B4 el módulo `launcher-core` no compila y, por dependencia, `launcher-ui` tampoco.

### 🔴 Bloqueantes de ejecución / UI

| ID | Hallazgo | Evidencia |
|---|---|---|
| **R1** | **`mainClass` inexistente.** Apunta a `com.neonlauncher.LauncherApp`, la clase real es `com.neonlauncher.ui.LauncherApp` → `:launcher-ui:run` falla con `ClassNotFoundException`. El README repite el valor erróneo. | `launcher-ui/build.gradle.kts:22` vs `LauncherApp.kt:1`; `README.md:22`. |
| **R2** | **`<StatusBar>` no es una clase JavaFX** → `LoadException` al cargar `main.fxml` (falla en el arranque). | `main.fxml:26-28`. |
| **R3** | **`mods.fxml` inválido**: `<ModActionCellFactory/>` no existe en ningún lado y `PropertyValueFactory`/`CheckBoxTableCell` se usan sin `<?import javafx.scene.control.cell.*?>` (el import por defecto de FXML no incluye `control.cell`). | `mods.fxml:32-46,49`. |
| **R4** | **`Spinner` con `initialValue`** (propiedad inexistente en `javafx.scene.control.Spinner`) → falla al cargar `profile.fxml`/`settings.fxml`. Lo correcto es `valueFactory`. | `profile.fxml:43,46`; `settings.fxml:40`. |
| **R5** | **UI muerta**: las vistas login/home/mods/profile/settings no tienen `fx:controller` y solo existe `MainController`; los `fx:id` (playButton, microsoftLoginBtn, syncBtn, saveSettingsBtn, etc.) no quedan cableados a ningún manejador. | 5 FXML sin `fx:controller`; único controller = `MainController.kt`. |
| **R6** | **Manejo de error insuficiente**: `LauncherApp.start` captura solo `IOException`, pero los fallos de FXMLLoader son `RuntimeException` (`LoadException`) → cierre/stacktrace no controlado. | `LauncherApp.kt:40-43`. |

### 🔴 Estilos / temas

| ID | Hallazgo | Evidencia |
|---|---|---|
| **S1** | **`var(--x)` no existe en JavaFX CSS.** `main.css` contiene **80** referencias `var(--...)` y los 3 temas solo definen custom properties `--x` dentro de `.root`. JavaFX no soporta custom properties ni `var()`, así que esas declaraciones se ignoran (con warnings) y **el cambio de tema dark/light/system no tiene efecto visual**. También `:root` (usado en `main.css:12`) no es un selector válido de JavaFX (se usa `.root`). | `main.css` (80 matches `var(--`); `themes/dark.css:2-29`, `light.css`, `system.css`. |
| **S2** | El diseño de temas está pensado con semántica web (variables CSS + `:root` + `derive()`), **incompatible** con el motor CSS de JavaFX. Requiere migrar a `-fx-` properties por tema (o generar CSS por tema en build time). | Idem S1. |

### 🟠 Build / entorno

| ID | Hallazgo | Evidencia |
|---|---|---|
| **G1** | **Sin Java toolchain**: no hay `org.gradle.java.home` ni `java.toolchain`. El `java` del PATH es **Java 8** y `JAVA_HOME` está **vacío**; Gradle correría sobre Java 8 → no puede construir JavaFX 21 / target 21. El README exige JDK 17+ pero nada lo fuerza. Hay JDK 21 en `Eclipse Adoptium`. | entorno + `build.gradle.kts` (sin toolchain). |
| **G2** | **Dependencias de test sin tests**: JUnit 5.11.0 + AssertJ 3.25.3 configurados en la raíz, pero **no existe ningún `src/test`**. | 0 directorios de test. |
| **G3** | **Gradle 8.5 atrasado**: en la propia caché ya hay `gradle-8.10.2-all`. Además falta `distributionSha256Sum` en el wrapper. | `gradle-wrapper.properties`; cache de dists. |
| **G4** | **Sin version catalog** (`gradle/libs.versions.toml`); versiones repetidas y hardcodeadas (Jackson 2.17.1 en common y core). Sin `.gitignore`/CI/VCS. | build files. |

### 🟡 Dependencias y seguridad

| ID | Hallazgo | Evidencia |
|---|---|---|
| **D1** | **BouncyCastle declarado y no usado** en `launcher-core`. | `launcher-core/build.gradle.kts:16`; 0 imports `org.bouncycastle.*`. |
| **D2** | **FormsFX, Guava y `javafx-web` declarados y no usados** en `launcher-ui`. | `launcher-ui/build.gradle.kts:11,16,18`; 0 usos en `.kt`/`.fxml`. |
| **D3** | **Dependencias de Jackson duplicadas** en core (ya expuestas por `api` en common). | `launcher-core/build.gradle.kts:9-11`. |
| **D4** | **Tokens de Microsoft sin cifrar**: `MicrosoftAccount.accessToken/refreshToken` se serializan en claro en `profile.json`. BouncyCastle estaba pensado para esto, pero no se usa. | `Profile.kt:32-45`; `ConfigManager` (JSON plano). |
| **D5** | **URLs placeholder**: config apunta a `api.example.com`/`cdn.example.com`; `AutoUpdater` apunta a un repo inexistente `neonlabs/NeonLauncher`, y asigna `sha256=""` → la verificación de integridad del instalador nunca se activa. | `LauncherConfig.kt:10-11`; `AutoUpdater.kt:51,73-81,117-123`. |
| **D6** | **Versiones con atraso menor**: Kotlin 1.9.24, Jackson 2.17.1, JavaFX 21.0.6, Gradle 8.5 (todas de 2024). No son obsolescencias críticas. | build files. |

### 🟡 Calidad / lógica (no bloqueantes)

- **`MinecraftLauncher.buildCommand`**: agrega `customJvmArgs`/`extraJvmArgs` **después** del main
  class (`:114-116`) → se interpretan como args del juego, no de la JVM. `--server`/`--port` se
  añaden siempre (`:107-111`) sin opción de conexión directa. `buildClasspath` ignora
  `Profile.enabledMods` (`:140`). Los natives se resuelven sin usar el classifier del maven coord
  ni `ExtractRules.exclude` (`:152-162,192-196`).
- **Log del juego en memoria**: `Thread(process.inputStream::readAllBytes)` acumula todo el log y
  lo emite recién al terminar (`MinecraftLauncher.kt:44-48`); hilo sin nombre ni daemon.
- **`JavaManager.parseVersionOutput`**: la 3ª regex (`:148`) acepta cualquier número del texto →
  riesgo de versión mal parseada.
- **`ConfigManager.writeAtomic`**: no crea el directorio base antes de escribir; la primera
  ejecución puede fallar si `%APPDATA%\NeonLauncher` no existe.
- **Imports muertos**: `Response` (ManifestManager/ModDownloader), `Duration`/`Instant`/`TimeUnit`
  (ManifestManager), etc.
- **`Result` propio** sombrea `kotlin.Result`: funciona, pero genera ambigüedad de imports.

---

## 6. Mapa de impacto

```
launcher-common   ✅ coherente (solo SLF4J sin uso)
launcher-core     🔴 NO COMPILA  (B1 OkHttp, B2 ConfigManager, B3 ModEntry, B4 SerializationFeature)
launcher-ui       🔴 NO ARRANCA  (R1 mainClass, R2/R3/R4 FXML, S1 temas)
```

El proyecto **no se puede ejecutar tal cual está** (`gradlew :launcher-ui:run`): primero falla la
compilación de `launcher-core`; resueltos los B*, fallarían R1 y luego R2/R3/R4 en runtime.

---

## 7. Estado de Gradle / verificación ejecutada

- **Sí se ejecutó** (única corrida, sin descargas ni builds):
  `gradlew.bat --version` con `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`.
  - Resultado: **Gradle 8.5**, **JVM 21.0.11 (Eclipse Adoptium)**, Kotlin embebido 1.9.20, Windows 11 amd64.
  - Tiempo: inmediato (< 15 s), **sin errores de wrapper**. `gradlew.bat` devolvió **exit code 1**
    pese a imprimir la versión (aparente particularidad del script en PowerShell; no afecta).
  - No se crearon carpetas `build/` ni `.gradle/` en el proyecto.
- **NO se corrió** `compileKotlin`/`build`/`run`. Motivo: requeriría **descargar dependencias**
  ausentes en la caché local (JavaFX, Ikonli, FormsFX, OkHttp, Logback 1.5.6, BouncyCastle 1.78.1)
  — y la tarea pide expresamente no instalar nada extra y mantener el modo solo-lectura. Los
  bloqueantes B1–B4 se determinaron por análisis estático y son deterministas.

---

## 8. Recomendaciones priorizadas

1. **P0 — Desbloquear compilación** de `launcher-core`: declarar OkHttp
   (`com.squareup.okhttp3:okhttp`), unificar `ConfigManager` (mover `getDefaultBaseDir` a
   `companion object`), cambiar `Manifest.ModEntry` → `ModEntry`, importar `SerializationFeature`.
2. **P0 — Arreglar arranque UI**: `mainClass` → `com.neonlauncher.ui.LauncherApp`; reemplazar
   `<StatusBar>` por un `HBox`/`ToolBar`; crear/importar `ModActionCellFactory` y los imports
   `javafx.scene.control.cell.*`; cambiar `Spinner initialValue` por `valueFactory`.
3. **P1 — Sistema de temas**: migrar de `var(--x)` a propiedades `-fx-` por tema (o generar CSS por
   tema), o quedará inoperante.
4. **P1 — Fijar toolchain/`JAVA_HOME`**: declarar `java.toolchain { languageVersion = JavaLanguageVersion.of(21) }`
   (o `org.gradle.java.home`) y validar JDK 17+/21 en el arranque.
5. **P2 — Limpieza de dependencias**: quitar BouncyCastle, FormsFX, Guava, `javafx-web` y SLF4J de
   common si no se usan; deduplicar Jackson en core; introducir version catalog.
6. **P2 — Tests**: crear `src/test` en `launcher-common`/`launcher-core` para `Result`,
   `parseVersionOutput`, `compareVersions`, `calculateDiff`, `resolveLibraryPath`.
7. **P3 — Endurecer red/seguridad**: `OkHttpClient` centralizado con timeouts; verificación real de
   SHA-256 en `AutoUpdater`; cifrar/aislar tokens de `MicrosoftAccount`.
8. **P3 — Wrapper**: subir a Gradle 8.10+ (ya en caché) y agregar `distributionSha256Sum`.

---

## 9. Criterios de aceptación (TAREA-002)

- [x] `AUDITORIA.md` en la raíz con inventario por módulo, dependencias clave y riesgos/obsolescencias.
- [x] Ningún archivo fuente modificado (único archivo creado: `AUDITORIA.md`).
- [x] Se documenta qué se corrió de Gradle (`--version`, OK, sin descargas) y por qué no se hizo build.
