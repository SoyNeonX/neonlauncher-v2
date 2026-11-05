package com.neonlauncher.ui

import com.neonlauncher.common.model.Instance
import com.neonlauncher.common.model.InstanceOrigin
import com.neonlauncher.common.model.LauncherSettings
import com.neonlauncher.common.model.PlayerProfile
import com.neonlauncher.common.model.StoredAccount
import com.neonlauncher.common.util.AppInfo
import com.neonlauncher.common.util.Paths2
import com.neonlauncher.core.auth.MicrosoftAuth
import com.neonlauncher.core.auth.MsSession
import com.neonlauncher.core.download.MojangCatalog
import com.neonlauncher.core.download.RemoteVersion
import com.neonlauncher.core.download.VersionInstaller
import com.neonlauncher.core.game.GameInstallation
import com.neonlauncher.core.game.GameLauncher
import com.neonlauncher.core.game.Identity
import com.neonlauncher.core.game.InstallationScanner
import com.neonlauncher.core.game.LaunchPlan
import com.neonlauncher.core.runtime.HardwareInfo
import com.neonlauncher.core.runtime.JavaFinder
import com.neonlauncher.core.runtime.JavaInstaller
import com.neonlauncher.core.runtime.JavaRuntime
import com.neonlauncher.core.runtime.SystemProbe
import com.neonlauncher.core.settings.SettingsStore
import com.neonlauncher.core.update.LauncherUpdate
import com.neonlauncher.core.update.UpdateChecker
import javafx.animation.PauseTransition
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.DialogPane
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.image.Image
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.Circle
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.Duration
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import javax.imageio.ImageIO

/** Segundos entre el click en JUGAR y el arranque real del juego. */
private const val LAUNCH_DELAY_SECONDS = 5.0

/**
 * Ventana principal. Frameless: la barra de título, arrastrar y los botones son propios.
 */
class NeonApp : Application() {

    internal val screen = LogBuffer()
    internal lateinit var stage: Stage
    internal lateinit var scene: Scene
    internal val navGroup = ToggleGroup()
    internal val navButtons = LinkedHashMap<View, ToggleButton>()

    internal var settings: LauncherSettings = LauncherSettings()
        private set
    internal var mcDir: Path = Paths2.defaultMinecraftDir()
        private set
    internal var installations: List<GameInstallation> = emptyList()
        private set
    internal var selected: GameInstallation? = null
        private set
    internal var instances: List<Instance> = emptyList()
        private set
    internal var selectedInstance: Instance? = null
        private set
    internal var javaRuntimes: List<JavaRuntime> = emptyList()
        private set
    internal var javaRuntime: JavaRuntime? = null
        private set
    internal var phase: Phase = Phase.LOADING
    internal var gameProcess: Process? = null
        private set
    internal var loginState: LoginState = LoginState.Idle
    internal val auth = MicrosoftAuth()
    internal var remoteVersions: List<RemoteVersion> = emptyList()
        private set
    internal var catalogError: String? = null
        private set
    internal var installingVersion: String? = null
        private set
    internal var installProgress: Double = 0.0
        private set
    internal var installStatus: String = ""
        private set

    private var loginCancelled = false

    private val store = SettingsStore()
    private val javaFinder = JavaFinder()
    private val contentArea = StackPane()
    private val statusLabel = Ui.label("Iniciando…", "status-text")
    private val javaStatus = Ui.label("", "status-text")
    private val consoleArea = TextArea()
    private val consoleDrawer = VBox()
    private lateinit var titleBar: TitleBar
    private lateinit var rail: Rail
    private lateinit var statusBar: HBox
    private lateinit var startup: StartupOverlay
    private lateinit var launchOverlay: LaunchOverlay
    private val backdropHolder = StackPane()
    private var launchStartedAt = 0L
    private var launchTimerDone = false
    private var pendingPlan: LaunchPlan? = null
    internal var hardware: HardwareInfo? = null
        private set
    private var pendingUpdate: LauncherUpdate? = null
    private var updateDeclined = false
    private var scanner = InstallationScanner(Paths2.defaultMinecraftDir())
    private var launcher = GameLauncher(Paths2.defaultMinecraftDir())
    private var activeView: AppView? = null
    private var autoplayDone = false
    private var firstRun = false
    private val verbose: Boolean = System.getenv("NEON_VERBOSE") != null

    private val worker = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "neon-worker").apply { isDaemon = true }
    }

    override fun start(stage: Stage) {
        this.stage = stage
        firstRun = !java.nio.file.Files.isRegularFile(store.file)
        settings = store.load()
        openMinecraftDir(resolveGameDirectory(settings))

        contentArea.styleClass.add("content")
        HBox.setMargin(contentArea, Insets(22.0, 18.0, 16.0, 30.0))

        titleBar = TitleBar(this)
        rail = Rail(this)
        statusBar = buildStatusBar()
        launchOverlay = LaunchOverlay()

        val row = HBox(0.0, rail, contentArea).apply {
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }
        HBox.setHgrow(contentArea, Priority.ALWAYS)

        val column = VBox(0.0, titleBar, row, buildConsoleDrawer(), statusBar).apply {
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }
        VBox.setVgrow(row, Priority.ALWAYS)

        backdropHolder.children.setAll(Backdrop.layer(theme = currentTheme()))

        val root = StackPane().apply {
            styleClass.add("window")
            children.addAll(backdropHolder, column, launchOverlay)
        }

        startup = StartupOverlay()
        StackPane.setMargin(startup, Insets(40.0, 0.0, 0.0, 0.0))
        root.children.add(startup)

        scene = Scene(root, 1180.0, 740.0)
        Skin.apply(scene)

        stage.initStyle(StageStyle.UNDECORATED)
        stage.isResizable = false
        stage.scene = scene
        stage.title = "NeonLauncher"
        stage.icons.addAll(loadIcon())
        stage.show()
        stage.centerOnScreen()

        screen.listener = { line -> if (consoleDrawer.isVisible) appendConsole(line) }

        val needsOnboarding = !settings.onboarded && settings.microsoft == null
        showView(
            screenshotView()
                ?: if (System.getenv("NEON_ONBOARDING") != null || needsOnboarding) View.ONBOARDING else View.PLAY
        )
        if (System.getenv("NEON_CONSOLE") != null) toggleConsole()
        if (System.getenv("NEON_LOGIN_TEST") != null) startMicrosoftLogin()
        runStartup()
        maybeScreenshot()
    }

    override fun stop() {
        worker.shutdownNow()
        super.stop()
    }

    internal fun close() {
        Platform.exit()
    }

    // ---------------------------------------------------------------- layout

    private fun buildConsoleDrawer(): VBox {
        consoleArea.styleClass.add("console")
        consoleArea.isEditable = false
        consoleArea.isWrapText = false
        VBox.setVgrow(consoleArea, Priority.ALWAYS)

        consoleDrawer.styleClass.add("console-drawer")
        consoleDrawer.children.addAll(
            HBox(
                8.0,
                Ui.section("CONSOLA"),
                Ui.grow(),
                linkButton("limpiar") {
                    screen.clear()
                    consoleArea.clear()
                },
                linkButton("cerrar") { toggleConsole() }
            ).apply { alignment = Pos.CENTER_LEFT },
            consoleArea
        )
        consoleDrawer.prefHeight = 180.0
        consoleDrawer.isVisible = false
        consoleDrawer.isManaged = false
        return consoleDrawer
    }

    private fun buildStatusBar(): HBox {
        val dot = Circle(3.5).apply { styleClass.add("status-dot") }
        return HBox(
            9.0,
            dot,
            statusLabel,
            Ui.grow(),
            linkButton("consola") { toggleConsole() },
            Ui.label("v${AppInfo.VERSION}", "status-text"),
            javaStatus
        ).apply {
            styleClass.add("status-bar")
            alignment = Pos.CENTER_LEFT
        }
    }

    private fun linkButton(text: String, action: () -> Unit): Label = Ui.label(text, "link-btn").apply {
        setOnMouseClicked { action() }
    }

    internal fun showView(view: View) {
        val fullBleed = view == View.ONBOARDING
        if (fullBleed) {
            rail.isVisible = false
            rail.isManaged = false
            statusBar.isVisible = false
            statusBar.isManaged = false
            HBox.setMargin(contentArea, Insets(24.0, 30.0, 20.0, 30.0))
        } else {
            rail.isVisible = true
            rail.isManaged = true
            statusBar.isVisible = true
            statusBar.isManaged = true
            HBox.setMargin(contentArea, Insets(22.0, 26.0, 16.0, 22.0))
        }

        val next: AppView = when (view) {
            View.PLAY -> PlayView(this)
            View.INSTANCES -> InstancesView(this)
            View.SETTINGS -> SettingsView(this)
            View.LOGIN -> LoginView(this)
            View.ONBOARDING -> OnboardingView(this)
            View.VERSIONS -> VersionsView(this)
        }
        activeView = next
        contentArea.children.setAll(next.node)
        Transitions.slideIn(next.node)
        next.refresh()
        if (!fullBleed) rail.refresh()
        if (view == View.VERSIONS) loadCatalog()
    }

    // ---------------------------------------------------------------- catálogo

    internal fun loadCatalog() {
        if (remoteVersions.isNotEmpty()) return
        catalogError = null
        worker.submit {
            val result = runCatching { MojangCatalog().versions() }
            Platform.runLater {
                result.onSuccess { versions ->
                    remoteVersions = versions
                    activeView?.refresh()
                }.onFailure { error ->
                    catalogError = "No se pudo leer el catálogo: ${error.message}"
                    activeView?.refresh()
                }
            }
        }
    }

    internal fun installVersion(remote: RemoteVersion) {
        if (installingVersion != null) return
        installingVersion = remote.id
        installProgress = 0.0
        installStatus = "Empezando…"
        activeView?.refresh()

        worker.submit {
            val result = VersionInstaller(mcDir).install(
                remote,
                onStatus = { text ->
                    Platform.runLater {
                        installStatus = text
                        activeView?.refresh()
                    }
                },
                onProgress = { fraction ->
                    Platform.runLater {
                        installProgress = fraction
                        activeView?.refresh()
                    }
                }
            )

            Platform.runLater {
                result.onSuccess {
                    installingVersion = null
                    setStatus("${remote.id} instalada")
                    reloadEnvironment { showView(View.PLAY) }
                }.onFailure { error ->
                    installingVersion = null
                    installStatus = error.message ?: "Error desconocido"
                    setStatus("No se pudo instalar ${remote.id}")
                    activeView?.refresh()
                }
            }
        }
    }

    internal fun setStatus(text: String) {
        statusLabel.text = text
    }

    internal fun toggleConsole() {
        val show = !consoleDrawer.isVisible
        consoleDrawer.isVisible = show
        consoleDrawer.isManaged = show
        if (show) {
            consoleArea.text = screen.text()
            consoleArea.scrollTop = Double.MAX_VALUE
        }
    }

    private fun appendConsole(line: String) {
        if (consoleArea.length > 150_000) {
            consoleArea.text = screen.text()
        } else {
            consoleArea.appendText(if (consoleArea.length == 0) line else "\n$line")
        }
        consoleArea.scrollTop = Double.MAX_VALUE
    }

    // ---------------------------------------------------------------- estado

    internal fun reloadEnvironment(after: (() -> Unit)? = null) {
        phase = if (installations.isEmpty()) Phase.LOADING else phase
        activeView?.refresh()
        worker.submit {
            val runtimes = javaFinder.detect(settings.javaPath)
            val found = scanner.scan()
                .filter { settings.showSnapshots || it.type != "snapshot" }
            Platform.runLater {
                applyEnvironment(runtimes, found)
                after?.invoke()
            }
        }
    }

    private fun applyEnvironment(runtimes: List<JavaRuntime>, found: List<GameInstallation>) {
        javaRuntimes = runtimes
        installations = found

        ensureInstances(found)

        val forced = System.getenv("NEON_VERSION")
        val wanted = forced?.let { id -> instances.firstOrNull { it.versionId == id } }
            ?: instances.firstOrNull { it.id == settings.selectedInstanceId }
            ?: instances.firstOrNull()

        applyInstance(wanted, found)
        phase = if (found.isEmpty()) Phase.NO_GAME else Phase.READY
        javaStatus.text = javaRuntime?.label ?: "Java no encontrado"
        setStatus(
            when {
                found.isEmpty() -> "No encontré versiones de Minecraft instaladas"
                selectedInstance != null -> "${selectedInstance?.name} lista para jugar"
                else -> "Creá una instancia para empezar"
            }
        )
        if (verbose) println("neon: ${runtimes.size} runtime(s), ${found.size} versión(es), ${instances.size} instancia(s)")
        activeView?.refresh()
        rail.refresh()
        refreshBackdrop()
        if (System.getenv("NEON_AUTOPLAY") != null && !autoplayDone) {
            autoplayDone = true
            onPlayPressed()
        }
    }

    /**
     * Migración y mantenimiento de instancias: la del estudio se crea sola, y si el jugador no
     * tiene ninguna propia se le genera una por versión instalada.
     */
    private fun ensureInstances(found: List<GameInstallation>) {
        var list = settings.instances
        val bestJava = javaRuntimes.maxByOrNull { it.major }?.major ?: 21

        if (list.none { it.isStudio }) {
            val best = found.firstOrNull { it.isVanilla && it.javaMajor <= bestJava } ?: found.firstOrNull()
            if (best != null) {
                list = listOf(
                    Instance(
                        id = "studio-neonworld",
                        name = "NeonWorld VI",
                        versionId = best.id,
                        origin = InstanceOrigin.STUDIO,
                        icon = "\uD83C\uDF05",
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                ) + list
            }
        }

        if (list.none { !it.isStudio } && found.isNotEmpty()) {
            list = list + found.take(12).map { game ->
                Instance(
                    id = "inst-${game.id}",
                    name = game.id,
                    versionId = game.id,
                    icon = "",
                    createdAtEpochMs = System.currentTimeMillis()
                )
            }
        }

        if (list != settings.instances) {
            settings = settings.copy(instances = list)
            store.save(settings)
        }
        instances = list
    }

    private fun applyInstance(instance: Instance?, found: List<GameInstallation>) {
        selectedInstance = instance
        selected = instance?.let { i -> found.firstOrNull { it.id == i.versionId } }
        javaRuntime = javaFinder.pick(selected?.javaMajor ?: 21, javaRuntimes)

        if (instance != null && settings.selectedInstanceId != instance.id) {
            settings = settings.copy(selectedInstanceId = instance.id, lastVersion = instance.versionId)
            store.save(settings)
        }
    }

    internal fun versionFor(instance: Instance?): GameInstallation? =
        instance?.let { i -> installations.firstOrNull { it.id == i.versionId } }

    internal fun selectInstance(instance: Instance) {
        applyInstance(instance, installations)
        javaStatus.text = javaRuntime?.label ?: "Java no encontrado"
        setStatus("${instance.name} seleccionada")
        refreshBackdrop()
        if (activeView !is PlayView) showView(View.PLAY) else {
            activeView?.refresh()
            rail.refresh()
        }
    }

    internal fun createInstance(versionId: String): Instance? {
        val game = installations.firstOrNull { it.id == versionId } ?: return null
        val baseName = game.id
        var name = baseName
        var index = 2
        while (instances.any { it.name.equals(name, ignoreCase = true) }) {
            name = "$baseName ($index)"
            index++
        }

        val created = Instance(
            id = "inst-${java.util.UUID.randomUUID().toString().take(8)}",
            name = name,
            versionId = game.id,
            origin = InstanceOrigin.USER,
            icon = "\u25A3",
            createdAtEpochMs = System.currentTimeMillis()
        )
        instances = instances + created
        settings = settings.copy(instances = instances)
        store.save(settings)
        selectInstance(created)
        return created
    }

    internal fun updateInstance(updated: Instance) {
        instances = instances.map { if (it.id == updated.id) updated else it }
        settings = settings.copy(instances = instances)
        store.save(settings)
        if (selectedInstance?.id == updated.id) {
            selectedInstance = updated
            refreshBackdrop()
        }
        rail.refresh()
        activeView?.refresh()
        setStatus("${updated.name} actualizada")
    }

    internal fun removeInstance(instance: Instance) {
        if (instance.isStudio) {
            setStatus("Las instancias del estudio no se pueden borrar")
            return
        }
        instances = instances.filter { it.id != instance.id }
        settings = settings.copy(instances = instances)
        store.save(settings)

        if (selectedInstance?.id == instance.id) {
            applyInstance(instances.firstOrNull(), installations)
            refreshBackdrop()
        }
        rail.refresh()
        activeView?.refresh()
        setStatus("${instance.name} borrada")
    }

    // ---------------------------------------------------------------- arranque

    private fun runStartup() {
        startup.setStatus("Buscando actualizaciones…", "NeonLauncher v${AppInfo.VERSION}")
        startup.setStep(0)

        worker.submit {
            val update = runCatching { UpdateChecker.check(AppInfo.VERSION) }.getOrNull()
            if (update != null) {
                pendingUpdate = update
                Platform.runLater {
                    startup.setStatus(
                        "Actualización ${update.version} disponible",
                        "Se descarga al cerrar el launcher"
                    )
                }
                Thread.sleep(900)
            } else {
                Thread.sleep(450)
            }

            hardware = SystemProbe.detect()
            Platform.runLater {
                startup.setStep(1)
                val info = hardware
                startup.setStatus(
                    "Revisando tu equipo…",
                    if (info != null) "${info.ramLabel} de RAM · ${info.cores} núcleos · ${info.osArch}" else ""
                )
            }
            Thread.sleep(350)

            Platform.runLater { startup.setStatus("Detectando Java…", ""); startup.setStep(2) }
            val runtimes = javaFinder.detect(settings.javaPath)

            Platform.runLater { startup.setStatus("Leyendo tus instalaciones…", ""); startup.setStep(3) }
            val found = scanner.scan()
                .filter { settings.showSnapshots || it.type != "snapshot" }

            Platform.runLater {
                applyEnvironment(runtimes, found)
                applyRecommendedMemory()
                startup.hide {
                    if (pendingUpdate != null) askForUpdate() else askForJavaIfNeeded()
                }
            }
        }
    }

    /** En el primer arranque ajusta la RAM según el equipo. */
    private fun applyRecommendedMemory() {
        if (!firstRun) return
        val info = hardware ?: return
        val recommended = SystemProbe.recommendedMemoryMb(info)
        settings = settings.copy(memoryMb = recommended)
        store.save(settings)
        setStatus("Memoria sugerida para tu equipo: $recommended MB")
    }

    /** Si hay una release nueva en GitHub, ofrece bajarla y ejecutar el instalador. */
    private fun askForUpdate() {
        val update = pendingUpdate ?: return

        val alert = Alert(AlertType.CONFIRMATION).apply {
            initOwner(stage)
            title = "Actualización disponible"
            headerText = "NeonLauncher ${update.version} ya está para bajar"
            contentText = update.notes.take(700).ifBlank { "Hay una versión nueva publicada en GitHub." } +
                "\n\nSe descarga el instalador y se abre solo. El launcher se cierra para que pueda reemplazarse."
        }
        alert.buttonTypes.setAll(
            ButtonType("Descargar e instalar", ButtonBar.ButtonData.OK_DONE),
            ButtonType("Después", ButtonBar.ButtonData.CANCEL_CLOSE)
        )
        styleDialog(alert.dialogPane)

        val chosen = alert.showAndWait().orElse(null)
        if (chosen != null && chosen.buttonData == ButtonBar.ButtonData.OK_DONE) {
            installUpdate(update)
        } else {
            updateDeclined = true
            setStatus("Actualización ${update.version} pendiente · NeonWorld VI no va a arrancar hasta actualizar")
            activeView?.refresh()
        }
    }

    private fun installUpdate(update: LauncherUpdate) {
        startup.isVisible = true
        startup.isManaged = true
        startup.opacity = 1.0
        startup.setStatus("Descargando NeonLauncher ${update.version}…", "0%")

        worker.submit {
            val target = Paths.get(System.getProperty("java.io.tmpdir"), "NeonLauncher-${update.version}-Setup.exe")

            val result = UpdateChecker.download(update, target) { fraction ->
                Platform.runLater {
                    startup.setStatus(
                        "Descargando NeonLauncher ${update.version}…",
                        "${(fraction * 100).toInt()}%"
                    )
                }
            }

            Platform.runLater {
                result.onSuccess { file ->
                    startup.setStatus("Abriendo el instalador…", "El launcher se cierra solo")
                    UpdateChecker.launchInstaller(file)
                    PauseTransition(Duration.millis(800.0)).apply {
                        setOnFinished { Platform.exit() }
                    }.play()
                }.onFailure { error ->
                    startup.setStatus("No se pudo actualizar", error.message ?: "")
                    PauseTransition(Duration.millis(2600.0)).apply {
                        setOnFinished { startup.hide() }
                    }.play()
                }
            }
        }
    }

    private fun askForJavaIfNeeded() {
        val required = selected?.javaMajor ?: 21
        val java = javaRuntime
        if (java != null && java.major >= required) return

        val alert = Alert(AlertType.CONFIRMATION).apply {
            initOwner(stage)
            title = "Falta Java"
            headerText =
                if (java == null) "No encontramos ningún Java instalado"
                else "Tenés Java ${java.major} y esta versión pide Java $required"
            contentText = "NeonLauncher puede descargar un JDK $required de Adoptium (unos 180 MB) " +
                "y dejarlo configurado. ¿Lo hacemos ahora?"
        }
        alert.buttonTypes.setAll(
            ButtonType("Descargar", ButtonBar.ButtonData.OK_DONE),
            ButtonType("Ahora no", ButtonBar.ButtonData.CANCEL_CLOSE)
        )
        styleDialog(alert.dialogPane)

        val chosen = alert.showAndWait().orElse(null)
        if (chosen != null && chosen.buttonData == ButtonBar.ButtonData.OK_DONE) {
            installJava(required)
        }
    }

    internal fun installJava(major: Int) {
        startup.isVisible = true
        startup.isManaged = true
        startup.opacity = 1.0
        startup.setStatus("Descargando Java $major…", "0%")

        worker.submit {
            val result = JavaInstaller.install(major) { percent ->
                Platform.runLater { startup.setStatus("Descargando Java $major…", "$percent%") }
            }

            Platform.runLater {
                result.onSuccess { home ->
                    val executable = home.resolve("bin").resolve(Paths2.javaExecutable())
                    settings = settings.copy(javaPath = executable.toString())
                    store.save(settings)
                    reloadEnvironment {
                        startup.setStatus("Java listo", home.fileName.toString())
                        PauseTransition(javafx.util.Duration.millis(900.0)).apply {
                            setOnFinished { startup.hide() }
                        }.play()
                    }
                }.onFailure { error ->
                    startup.setStatus("No se pudo descargar Java", error.message ?: "")
                    PauseTransition(javafx.util.Duration.millis(2600.0)).apply {
                        setOnFinished { startup.hide() }
                    }.play()
                }
            }
        }
    }

    private fun styleDialog(pane: DialogPane) {
        runCatching {
            Skin::class.java.getResource("/css/app.css")?.let { pane.stylesheets.add(it.toExternalForm()) }
        }
        pane.style = "-fx-background-color: #150e24;"
    }

    private fun pickDefault(
        found: List<GameInstallation>,
        runtimes: List<JavaRuntime>,
        lastVersion: String?
    ): GameInstallation? {
        val bestJava = runtimes.maxByOrNull { it.major }?.major ?: 21
        return found.firstOrNull { it.id == lastVersion }
            ?: found.firstOrNull { it.isVanilla && it.javaMajor <= bestJava }
            ?: found.firstOrNull { it.javaMajor <= bestJava }
            ?: found.firstOrNull()
    }

    /**
     * Las instancias del estudio viven en su propia carpeta (`instances/<id>/game`), así los
     * mods y configs que baje NeonShy Studios no tocan el `.minecraft` del jugador.
     */
    internal fun gameDirFor(instance: Instance): Path =
        if (instance.isStudio) {
            Paths2.launcherDataDir().resolve("instances").resolve(instance.id).resolve("game")
        } else {
            resolveGameDirectory(settings)
        }

    /** Borra los archivos de una versión del `.minecraft` (no la instancia). */
    internal fun deleteVersion(installation: GameInstallation) {
        if (installingVersion != null || phase == Phase.LAUNCHING) return

        val dependents = installations.filter { it.baseId == installation.id && it.id != installation.id }
        if (dependents.isNotEmpty()) {
            setStatus("No se puede borrar ${installation.id}: ${dependents.first().id} depende de ella")
            return
        }

        worker.submit {
            val result = runCatching {
                val dir = mcDir.resolve("versions").resolve(installation.id)
                if (!java.nio.file.Files.isDirectory(dir)) error("La carpeta no existe")
                java.nio.file.Files.walk(dir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.deleteIfExists(it) }
                }
            }
            Platform.runLater {
                result.onSuccess {
                    setStatus("${installation.id} eliminada")
                    reloadEnvironment()
                }.onFailure { error ->
                    setStatus("No se pudo borrar ${installation.id}: ${error.message}")
                    activeView?.refresh()
                }
            }
        }
    }

    internal fun repairInstance(installation: GameInstallation) {
        if (installingVersion != null) return
        installingVersion = installation.id
        installProgress = 0.0
        installStatus = "Verificando archivos…"
        activeView?.refresh()

        worker.submit {
            val result = VersionInstaller(mcDir).repair(
                installation.id,
                onStatus = { text ->
                    Platform.runLater {
                        installStatus = text
                        activeView?.refresh()
                    }
                },
                onProgress = { fraction ->
                    Platform.runLater {
                        installProgress = fraction
                        activeView?.refresh()
                    }
                }
            )

            Platform.runLater {
                result.onSuccess {
                    installingVersion = null
                    setStatus("${installation.id} verificada")
                    reloadEnvironment()
                }.onFailure { error ->
                    installingVersion = null
                    installStatus = error.message ?: "Error"
                    setStatus("No se pudo reparar ${installation.id}")
                    activeView?.refresh()
                }
            }
        }
    }

    internal fun changeSkin() {
        val account = settings.microsoft ?: run {
            setStatus("Iniciá sesión con Microsoft para cambiar la skin")
            return
        }

        val chooser = javafx.stage.FileChooser().apply {
            title = "Elegí tu skin (PNG de 64×64)"
            extensionFilters.add(javafx.stage.FileChooser.ExtensionFilter("Skin PNG", "*.png"))
        }
        val file = chooser.showOpenDialog(stage) ?: return

        setStatus("Subiendo skin…")
        worker.submit {
            val result = auth.uploadSkin(account.accessToken, file.toPath(), slim = false)
            Platform.runLater {
                result.onSuccess { setStatus("Skin actualizada · puede tardar en verse") }
                    .onFailure { setStatus("No se pudo subir la skin: ${it.message}") }
            }
        }
    }

    internal fun isStudioSelected(): Boolean = selectedInstance?.isStudio == true

    /** Hay update publicada y el jugador dijo "después": las instancias del estudio no arrancan. */
    internal fun updateBlocked(): Boolean = pendingUpdate != null && updateDeclined

    internal fun pendingUpdateVersion(): String? = pendingUpdate?.version

    internal fun currentTheme(): InstanceTheme =
        Themes.forInstance(selectedInstance?.versionId ?: selected?.id, isStudioSelected())

    internal fun refreshBackdrop() {
        val image = Artwork.backgroundFor(selectedInstance?.versionId ?: selected?.id, isStudioSelected())
        backdropHolder.children.setAll(Backdrop.layer(theme = currentTheme(), image = image))
        Transitions.fadeIn(backdropHolder, 320.0, 0.88)
    }

    internal fun completeOnboarding(name: String) {
        settings = settings.copy(username = name.trim().ifBlank { "Player" }, onboarded = true)
        store.save(settings)
        rail.refresh()
        showView(View.PLAY)
        setStatus("Listo, ${settings.username}")
    }

    internal fun showAccountMenu(owner: javafx.scene.Node) {
        val menu = javafx.scene.control.ContextMenu()
        menu.styleClass.add("account-menu")
        val account = settings.microsoft
        if (account != null) {
            val header = javafx.scene.control.MenuItem("${account.name} · Microsoft")
            header.isDisable = true
            menu.items.addAll(
                header,
                javafx.scene.control.SeparatorMenuItem(),
                javafx.scene.control.MenuItem("Cambiar skin…").apply {
                    setOnAction { changeSkin() }
                },
                javafx.scene.control.MenuItem("Cerrar sesión").apply { setOnAction { logout() } }
            )
        } else {
            val header = javafx.scene.control.MenuItem("${settings.username} · offline")
            header.isDisable = true
            menu.items.addAll(
                header,
                javafx.scene.control.SeparatorMenuItem(),
                javafx.scene.control.MenuItem("Iniciar sesión con Microsoft").apply {
                    setOnAction { startMicrosoftLogin() }
                },
                javafx.scene.control.MenuItem("Cambiar nombre en Ajustes").apply {
                    setOnAction { showView(View.SETTINGS) }
                }
            )
        }
        menu.show(owner, javafx.geometry.Side.TOP, 0.0, -8.0)
    }

    internal fun updateUsername(name: String) {
        val clean = name.trim().ifBlank { "Player" }
        if (clean == settings.username) return
        settings = settings.copy(username = clean)
        store.save(settings)
        rail.refresh()
    }

    internal fun applySettings(updated: LauncherSettings) {
        val normalized = updated.copy(
            username = updated.username.trim().ifBlank { "Player" },
            memoryMb = updated.memoryMb.coerceIn(512, 32768),
            windowWidth = updated.windowWidth.coerceIn(640, 7680),
            windowHeight = updated.windowHeight.coerceIn(480, 4320)
        )
        val directoryChanged = normalized.gameDirectory != settings.gameDirectory
        val javaChanged = normalized.javaPath != settings.javaPath

        settings = normalized
        store.save(settings)
        rail.refresh()

        if (directoryChanged) {
            openMinecraftDir(resolveGameDirectory(settings))
            selected = null
        }
        if (directoryChanged || javaChanged) {
            setStatus("Configuración guardada, releyendo…")
            reloadEnvironment()
        } else {
            setStatus("Configuración guardada")
            activeView?.refresh()
        }
    }

    private fun openMinecraftDir(directory: Path) {
        mcDir = directory
        scanner = InstallationScanner(directory)
        launcher = GameLauncher(directory)
    }

    private fun resolveGameDirectory(settings: LauncherSettings): Path =
        settings.gameDirectory?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Paths.get(it) }.getOrNull() }
            ?: Paths2.defaultMinecraftDir()

    // ---------------------------------------------------------------- launch

    internal fun onPlayPressed() {
        if (phase == Phase.LAUNCHING) return
        val running = gameProcess
        if (running != null && running.isAlive) {
            stopGame()
            return
        }

        val instance = selectedInstance ?: run {
            setStatus("No hay ninguna instancia seleccionada")
            return
        }
        val installation = versionFor(instance) ?: run {
            setStatus("${instance.name} usa ${instance.versionId}, que no está instalada")
            return
        }
        if (instance.isStudio && pendingUpdate != null && updateDeclined) {
            setStatus("Actualizá NeonLauncher para jugar en ${instance.name}")
            return
        }
        val java = javaRuntime ?: run {
            setStatus("No encontré ningún Java instalado")
            return
        }
        if (java.major < installation.javaMajor) {
            setStatus("${installation.id} necesita Java ${installation.javaMajor}+ (tenés ${java.major})")
            return
        }
        if (settings.microsoft == null && !PlayerProfile.isValidName(settings.username)) {
            setStatus("Nombre inválido: 3 a 16 caracteres (letras, números o _)")
            return
        }

        phase = Phase.LAUNCHING
        setStatus("Preparando ${installation.id}…")
        screen.append("» Preparando ${installation.id} con ${java.label}")
        launchStartedAt = System.currentTimeMillis()
        launchTimerDone = false
        pendingPlan = null
        launchOverlay.begin(
            instanceName = instance.name,
            subtitleText = "Buscando Java y armando el classpath…",
            seconds = LAUNCH_DELAY_SECONDS
        ) {
            launchTimerDone = true
            pendingPlan?.let { plan ->
                pendingPlan = null
                launchOverlay.finish()
                startGame(plan)
            }
        }
        activeView?.refresh()

        worker.submit {
            val prepared = runCatching {
                val identity = resolveIdentity().getOrThrow()
                val memory = instance.memoryMb.takeIf { it >= 1024 } ?: settings.memoryMb
                launcher.buildPlan(
                    installation,
                    settings.copy(memoryMb = memory),
                    java,
                    identity,
                    gameDirFor(instance)
                )
            }
            Platform.runLater {
                val plan = prepared.getOrNull()
                if (plan == null) {
                    phase = Phase.READY
                    launchOverlay.finish()
                    val reason = prepared.exceptionOrNull()?.message ?: "error desconocido"
                    setStatus("No se pudo preparar el lanzamiento")
                    screen.append("✗ $reason")
                    activeView?.refresh()
                    return@runLater
                }
                if (plan.missingLibraries.isNotEmpty()) {
                    phase = Phase.READY
                    launchOverlay.finish()
                    setStatus("Faltan ${plan.missingLibraries.size} librerías para ${installation.id}")
                    screen.append("✗ Faltan librerías: " + plan.missingLibraries.take(10).joinToString(", "))
                    activeView?.refresh()
                    return@runLater
                }
                beginLaunch(plan)
            }
        }
    }

    /** Espera a que se cumplan los 5 s desde el click, sin importar cuánto tardó el armado. */
    private fun beginLaunch(plan: LaunchPlan) {
        if (launchTimerDone) {
            launchOverlay.finish()
            startGame(plan)
            return
        }
        pendingPlan = plan
        launchOverlay.setStatus("Todo listo, arrancando en unos segundos…")
    }

    private fun startGame(plan: LaunchPlan) {
        val process = runCatching {
            launcher.start(
                plan,
                onLine = { line ->
                    if (verbose) println(line)
                    Platform.runLater { screen.append(line) }
                },
                onExit = { code -> Platform.runLater { onGameExit(code) } }
            )
        }.getOrNull()

        if (process == null) {
            phase = Phase.READY
            setStatus("No se pudo iniciar el proceso de Minecraft")
            activeView?.refresh()
            return
        }

        gameProcess = process
        phase = Phase.RUNNING
        setStatus("Minecraft está corriendo")
        screen.append("» Proceso iniciado (pid ${process.pid()})")
        activeView?.refresh()
    }

    internal fun stopGame() {
        val process = gameProcess ?: return
        screen.append("» Cerrando Minecraft…")
        process.destroy()
        gameProcess = null
        phase = Phase.READY
        setStatus("Minecraft cerrado")
        activeView?.refresh()
    }

    private fun onGameExit(code: Int) {
        gameProcess = null
        phase = if (installations.isEmpty()) Phase.NO_GAME else Phase.READY
        screen.append("» Minecraft terminó (código $code)")
        setStatus(if (code == 0) "Minecraft cerró" else "Minecraft cerró con errores (código $code)")
        activeView?.refresh()
    }

    // ---------------------------------------------------------------- cuenta

    internal fun currentPlayerName(): String =
        settings.microsoft?.name?.takeIf { it.isNotBlank() } ?: settings.username

    internal fun startMicrosoftLogin() {
        loginCancelled = false
        loginState = LoginState.Idle
        showView(View.LOGIN)

        worker.submit {
            val result = auth.login(
                onCode = { code ->
                    Platform.runLater {
                        loginState = LoginState.Code(code)
                        activeView?.refresh()
                    }
                },
                onStatus = { text ->
                    Platform.runLater {
                        loginState = LoginState.Status(text)
                        activeView?.refresh()
                    }
                },
                isCancelled = { loginCancelled }
            )

            Platform.runLater {
                result.onSuccess { session ->
                    settings = settings.copy(microsoft = session.toStored(), onboarded = true)
                    store.save(settings)
                    setStatus("Sesión iniciada como ${session.name}")
                    rail.refresh()
                    showView(View.PLAY)
                }.onFailure { error ->
                    loginState = LoginState.Failed(error.message ?: "No se pudo iniciar sesión")
                    activeView?.refresh()
                }
            }
        }
    }

    internal fun cancelLogin() {
        loginCancelled = true
        showView(View.PLAY)
    }

    internal fun logout() {
        settings = settings.copy(microsoft = null)
        store.save(settings)
        rail.refresh()
        setStatus("Sesión cerrada · modo offline")
        activeView?.refresh()
    }

    private fun resolveIdentity(): Result<Identity> {
        val account = settings.microsoft ?: return Result.success(offlineIdentity())
        if (!account.isExpiringSoon()) return Result.success(microsoftIdentity(account))

        val refreshed = auth.refresh(account.refreshToken)
        val session = refreshed.getOrElse {
            return Result.failure(IllegalStateException("Tu sesión de Microsoft expiró, volvé a iniciar sesión"))
        }
        val stored = session.toStored()
        Platform.runLater {
            settings = settings.copy(microsoft = stored)
            store.save(settings)
        }
        return Result.success(microsoftIdentity(stored))
    }

    private fun offlineIdentity(): Identity {
        val profile = PlayerProfile.offline(settings.username.ifBlank { "Player" })
        return Identity(profile.name, profile.uuid.toString(), "0", "legacy")
    }

    private fun microsoftIdentity(account: StoredAccount): Identity {
        val name = account.name.ifBlank { settings.username.ifBlank { "Player" } }
        val uuid = account.uuid.ifBlank { PlayerProfile.offline(name).uuid.toString() }
        return Identity(name, uuid, account.accessToken, "msa")
    }

    private fun MsSession.toStored(): StoredAccount =
        StoredAccount(name, uuid, accessToken, refreshToken, expiresAt.toEpochMilli())

    // ---------------------------------------------------------------- misc

    private fun loadIcon(): List<Image> = runCatching {
        javaClass.getResourceAsStream("/images/icon.png")?.use { Image(it) }?.let { listOf(it) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun screenshotView(): View? =
        (System.getProperty("neon.view") ?: System.getenv("NEON_VIEW"))
            ?.let { raw -> runCatching { View.valueOf(raw.uppercase()) }.getOrNull() }

    private fun maybeScreenshot() {
        val target = System.getProperty("neon.screenshot") ?: System.getenv("NEON_SCREENSHOT") ?: return
        val delay = (System.getProperty("neon.delay") ?: System.getenv("NEON_DELAY"))
            ?.toLongOrNull() ?: 1600L
        PauseTransition(Duration.millis(delay.toDouble())).apply {
            setOnFinished {
                runCatching {
                    val image = scene.snapshot(null)
                    val output = Paths.get(target)
                    output.parent?.let { Files.createDirectories(it) }
                    val width = image.width.toInt()
                    val height = image.height.toInt()
                    val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    val reader = image.pixelReader
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            buffered.setRGB(x, y, reader.getArgb(x, y))
                        }
                    }
                    ImageIO.write(buffered, "png", output.toFile())
                    println("screenshot -> $output")
                }
                Platform.exit()
            }
        }.play()
    }
}
