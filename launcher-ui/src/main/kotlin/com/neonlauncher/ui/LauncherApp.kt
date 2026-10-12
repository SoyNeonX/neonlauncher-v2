package com.neonlauncher.ui

import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.io.IOException

class LauncherApp : Application() {
    private val logger = LoggerFactory.getLogger(LauncherApp::class.java)
    private var primaryStage: Stage? = null

    override fun start(stage: Stage) {
        primaryStage = stage
        stage.title = "NeonLauncher"
        stage.minWidth = 900.0
        stage.minHeight = 600.0

        try {
            val loader = FXMLLoader(javaClass.getResource("/fxml/main.fxml"))
            val root: Parent = loader.load()

            val scene = Scene(root)
            scene.stylesheets.add(javaClass.getResource("/css/main.css")?.toExternalForm() ?: "")
            
            // Set icon
            javaClass.getResource("/images/icon.png")?.let { stage.getIcons().add(Image(it.toExternalForm())) }

            stage.scene = scene
            stage.show()

            // Initialize controller after stage is set
            val controller = loader.getController<MainController>()
            controller.initialize(stage)

        } catch (e: IOException) {
            logger.error("Failed to load main FXML", e)
            Platform.exit()
        }
    }

    override fun stop() {
        logger.info("Application shutting down")
        super.stop()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(LauncherApp::class.java, *args)
        }
    }
}