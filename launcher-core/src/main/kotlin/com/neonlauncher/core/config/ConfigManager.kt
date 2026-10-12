package com.neonlauncher.core.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.neonlauncher.common.exception.ConfigException
import com.neonlauncher.common.model.LauncherConfig
import com.neonlauncher.common.model.ProfileContainer
import com.neonlauncher.common.util.Result
import com.neonlauncher.common.util.Result.runCatching
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ConfigManager(private val baseDir: File) {
    private val logger = LoggerFactory.getLogger(ConfigManager::class.java)

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule())
        registerModule(JavaTimeModule())
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.INDENT_OUTPUT, true)
    }

    private val configFile: File = File(baseDir, "launcher-config.json")
    private val profileFile: File = File(baseDir, "profile.json")

    fun loadConfig(): Result<LauncherConfig> = runCatching {
        if (!configFile.exists()) {
            logger.info("Config file not found, creating default")
            val defaultConfig = LauncherConfig()
            saveConfig(defaultConfig)
            return@runCatching defaultConfig
        }
        val json = Files.readString(configFile.toPath())
        objectMapper.readValue<LauncherConfig>(json)
    }.onFailure { logger.error("Failed to load config", it) }

    fun saveConfig(config: LauncherConfig): Result<Unit> = runCatching {
        val json = objectMapper.writeValueAsString(config)
        writeAtomic(configFile, json)
    }.onFailure { logger.error("Failed to save config", it) }

    fun loadProfiles(): Result<ProfileContainer> = runCatching {
        if (!profileFile.exists()) {
            logger.info("Profile file not found, creating default")
            val defaultContainer = ProfileContainer()
            saveProfiles(defaultContainer)
            return@runCatching defaultContainer
        }
        val json = Files.readString(profileFile.toPath())
        objectMapper.readValue<ProfileContainer>(json)
    }.onFailure { logger.error("Failed to load profiles", it) }

    fun saveProfiles(container: ProfileContainer): Result<Unit> = runCatching {
        val json = objectMapper.writeValueAsString(container)
        writeAtomic(profileFile, json)
    }.onFailure { logger.error("Failed to save profiles", it) }

    fun getConfigFile(): File = configFile
    fun getProfileFile(): File = profileFile

    private fun writeAtomic(file: File, content: String) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        Files.writeString(tempFile.toPath(), content)
        Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

object ConfigManager {
    fun getDefaultBaseDir(): File {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> File(System.getenv("APPDATA"), "NeonLauncher")
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/NeonLauncher")
            else -> File(System.getProperty("user.home"), ".neonlauncher")
        }
    }
}