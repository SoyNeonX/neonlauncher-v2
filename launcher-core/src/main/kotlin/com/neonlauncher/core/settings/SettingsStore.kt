package com.neonlauncher.core.settings

import com.neonlauncher.common.model.LauncherSettings
import com.neonlauncher.common.util.Json
import com.neonlauncher.common.util.Paths2
import java.nio.file.Files
import java.nio.file.Path

/** Persistencia de la configuración del launcher. Nunca lanza: ante cualquier error usa defaults. */
class SettingsStore(val directory: Path = Paths2.launcherDataDir()) {

    val file: Path get() = directory.resolve("settings.json")

    fun load(): LauncherSettings = runCatching {
        if (!Files.isRegularFile(file)) LauncherSettings()
        else Json.read(file, LauncherSettings::class.java)
    }.getOrElse { LauncherSettings() }

    fun save(settings: LauncherSettings): Result<Unit> = runCatching {
        Files.createDirectories(directory)
        Json.write(file, settings)
    }
}
