package com.neonlauncher.ui

import com.neonlauncher.common.util.AppInfo
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ProgressBar
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

/** Pantalla del device code de Microsoft. */
internal class LoginView(private val app: NeonApp) : AppView {

    private val codeLabel = Ui.label("· · ·", "code-value")
    private val statusLabel = Ui.label("", "hint")
    private val spinner = ProgressBar()
    private val copyButton = Button("Copiar código")
    private val openButton = Button("Abrir microsoft.com/link")
    private val backButton = Button("Volver")
    private val currentCode = arrayOfNulls<String>(1)

    override val node: VBox = build()

    override fun refresh() {
        val state = app.loginState
        when (state) {
            is LoginState.Code -> {
                currentCode[0] = state.code.userCode
                codeLabel.text = state.code.userCode
                statusLabel.text = "Abrí microsoft.com/link e ingresá este código."
                spinner.isVisible = true
                spinner.isManaged = true
                copyButton.isDisable = false
                openButton.isDisable = false
                backButton.text = "Cancelar"
            }

            is LoginState.Status -> {
                statusLabel.text = state.text
                spinner.isVisible = true
                spinner.isManaged = true
                copyButton.isDisable = currentCode[0] == null
                openButton.isDisable = false
                backButton.text = "Cancelar"
            }

            is LoginState.Failed -> {
                codeLabel.text = "✕"
                statusLabel.text = state.message
                spinner.isVisible = false
                spinner.isManaged = false
                copyButton.isDisable = true
                openButton.isDisable = true
                backButton.text = "Volver"
            }

            LoginState.Idle -> {
                codeLabel.text = "· · ·"
                statusLabel.text = "Pidiéndole un código a Microsoft…"
                spinner.isVisible = true
                spinner.isManaged = true
                copyButton.isDisable = true
                openButton.isDisable = true
                backButton.text = "Cancelar"
            }
        }
    }

    private fun build(): VBox {
        copyButton.styleClass.add("ghost-btn")
        copyButton.setOnAction { currentCode[0]?.let { copyToClipboard(it) } }

        openButton.styleClass.add("ghost-btn")
        openButton.setOnAction { openUrl("https://microsoft.com/link") }

        backButton.styleClass.add("link-btn")
        backButton.setOnAction { app.cancelLogin() }

        spinner.styleClass.add("launch-progress")
        spinner.progress = ProgressBar.INDETERMINATE_PROGRESS
        spinner.maxWidth = 260.0

        val card = VBox(
            16.0,
            Ui.section("CUENTA DE MICROSOFT"),
            Ui.label("Iniciá sesión para jugar online", "tile-name"),
            Ui.label(
                "Se va a abrir el sitio de Microsoft. Ingresá el código de abajo y autorizá el acceso; " +
                    "esta ventana se actualiza sola cuando termina.",
                "subtitle"
            ).apply { isWrapText = true; maxWidth = 420.0 },
            codeLabel,
            HBox(10.0, copyButton, openButton).apply { alignment = Pos.CENTER_LEFT },
            spinner,
            statusLabel.apply { isWrapText = true; maxWidth = 420.0 },
            HBox(10.0, backButton).apply { alignment = Pos.CENTER_LEFT }
        ).apply {
            styleClass.add("card")
            maxWidth = 480.0
            alignment = Pos.TOP_LEFT
        }

        val note = Ui.label(
            "NeonLauncher ${AppInfo.VERSION} no ve tu contraseña: la autorización pasa en el navegador de Microsoft.",
            "hint"
        )

        return VBox(0.0).apply {
            styleClass.add("content")
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
            alignment = Pos.CENTER
            padding = javafx.geometry.Insets(10.0)
            children.addAll(
                VBox(14.0, card, note).apply {
                    alignment = Pos.CENTER
                    maxWidth = 520.0
                }
            )
        }
    }
}
