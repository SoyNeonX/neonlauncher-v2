package com.neonlauncher.ui.controller

import com.neonlauncher.common.model.LauncherConfig
import com.neonlauncher.common.model.ProfileContainer
import com.neonlauncher.core.config.ConfigManager
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import org.slf4j.LoggerFactory
import java.io.IOException

class MainController {
    private val logger = LoggerFactory.getLogger(MainController::class.java)
    private lateinit var configManager: ConfigManager
    private var launcherConfig: LauncherConfig = LauncherConfig()
    private var profileContainer: ProfileContainer = ProfileContainer()

    @FXML private lateinit var rootPane: BorderPane
    @FXML private lateinit var contentArea: StackPane
    @FXML private lateinit var navLogin: ToggleButton
    @FXML private lateinit var navHome: ToggleButton
    @FXML private lateinit var navMods: ToggleButton
    @FXML private lateinit var navProfile: ToggleButton
    @FXML private lateinit var navSettings: ToggleButton
    @FXML private lateinit var statusLabel: Label
    @FXML private lateinit var versionLabel: Label

    private val navButtons = mutableListOf<ToggleButton>()

    fun initialize(stage: javafx.stage.Stage) {
        configManager = ConfigManager(ConfigManager.getDefaultBaseDir())
        loadConfig()
        setupNavigation()
        loadView("home")
        updateUI()
    }

    private fun loadConfig() {
        configManager.loadConfig().fold(
            onSuccess = { launcherConfig = it },
            onFailure = { logger.warn("Using default config", it) }
        )
        configManager.loadProfiles().fold(
            onSuccess = { profileContainer = it },
            onFailure = { logger.warn("Using default profiles", it) }
        )
    }

    private fun setupNavigation() {
        navButtons.addAll(listOf(navLogin, navHome, navMods, navProfile, navSettings))

        navButtons.forEach { btn ->
            btn.setOnAction { switchView(btn) }
        }

        // Set icons
        navLogin.graphic = FontIcon(MaterialDesignM.ACCOUNT_CIRCLE)
        navHome.graphic = FontIcon(MaterialDesignM.HOME)
        navMods.graphic = FontIcon(MaterialDesignM.PUZZLE)
        navProfile.graphic = FontIcon(MaterialDesignM.ACCOUNT)
        navSettings.graphic = FontIcon(MaterialDesignM.COG)

        navButtons.forEach { it.graphic?.apply { iconSize = 24.0 } }
    }

    private fun switchView(selectedButton: ToggleButton) {
        navButtons.forEach { btn ->
            btn.selectedProperty().set(btn == selectedButton)
        }

        val viewName = when (selectedButton) {
            navLogin -> "login"
            navHome -> "home"
            navMods -> "mods"
            navProfile -> "profile"
            navSettings -> "settings"
            else -> "home"
        }

        loadView(viewName)
    }

    private fun loadView(viewName: String) {
        try {
            val loader = FXMLLoader(javaClass.getResource("/fxml/${viewName}.fxml"))
            val view: Parent = loader.load()
            contentArea.children.setAll(view)
            statusLabel.text = "Vista: $viewName"
        } catch (e: IOException) {
            logger.error("Failed to load view: $viewName", e)
            statusLabel.text = "Error cargando vista"
        }
    }

    private fun updateUI() {
        versionLabel.text = "NeonLauncher v${launcherConfig.launcherVersion}"
        applyTheme(launcherConfig.theme)
    }

    private fun applyTheme(theme: LauncherConfig.Theme) {
        val stylesheet = when (theme) {
            LauncherConfig.Theme.DARK -> "/css/themes/dark.css"
            LauncherConfig.Theme.LIGHT -> "/css/themes/light.css"
            LauncherConfig.Theme.SYSTEM -> "/css/themes/system.css"
        }
        rootPane.scene?.stylesheets?.clear()
        rootPane.scene?.stylesheets?.add(javaClass.getResource("/css/main.css")?.toExternalForm() ?: "")
        rootPane.scene?.stylesheets?.add(javaClass.getResource(stylesheet)?.toExternalForm() ?: "")
    }
}