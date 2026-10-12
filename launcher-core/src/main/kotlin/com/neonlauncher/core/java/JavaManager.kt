package com.neonlauncher.core.java

import com.neonlauncher.common.exception.JavaNotFoundException
import com.neonlauncher.common.util.Result
import com.neonlauncher.common.util.Result.runCatching
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern

data class JavaRuntime(
    val path: File,
    val version: JavaVersion,
    val vendor: String,
    val isSystem: Boolean = false
) {
    fun getExecutable(): File = File(path, if (isWindows()) "bin/java.exe" else "bin/java")
    fun getJavaHome(): File = path

    companion object {
        private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
    }
}

data class JavaVersion(
    val major: Int,
    val minor: Int = 0,
    val patch: Int = 0,
    val build: String? = null,
    val vendor: String = ""
) : Comparable<JavaVersion> {
    override fun compareTo(other: JavaVersion): Int {
        return when {
            major != other.major -> major.compareTo(other.major)
            minor != other.minor -> minor.compareTo(other.minor)
            patch != other.patch -> patch.compareTo(other.patch)
            else -> 0
        }
    }

    override fun toString(): String = "$major.$minor.$patch${build?.let { "+$it" } ?: ""}"
}

class JavaManager {
    private val logger = LoggerFactory.getLogger(JavaManager::class.java)

    fun detectAll(): List<JavaRuntime> {
        val runtimes = mutableListOf<JavaRuntime>()

        // 1. JAVA_HOME
        System.getenv("JAVA_HOME")?.let { dir ->
            File(dir).let { if (it.exists()) runCatching { parseJavaVersion(it) }.getOrNull()?.let { runtimes.add(it) } }
        }

        // 2. System paths
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        pathDirs.forEach { dir ->
            val javaExe = File(dir, if (isWindows()) "java.exe" else "java")
            if (javaExe.exists() && javaExe.canExecute()) {
                runCatching { parseJavaVersion(javaExe.parentFile.parentFile) }.getOrNull()?.let { rt ->
                    if (!runtimes.any { it.path == rt.path }) runtimes.add(rt)
                }
            }
        }

        // 3. Platform-specific locations
        detectPlatformSpecific().forEach { rt ->
            if (!runtimes.any { it.path == rt.path }) runtimes.add(rt)
        }

        return runtimes.sortedByDescending { it.version }
    }

    private fun detectPlatformSpecific(): List<JavaRuntime> {
        val runtimes = mutableListOf<JavaRuntime>()
        val os = System.getProperty("os.name").lowercase()

        when {
            os.contains("win") -> {
                // Windows registry and common install paths
                listOf(
                    "C:/Program Files/Java",
                    "C:/Program Files/Eclipse Adoptium",
                    "C:/Program Files/Microsoft/jdk",
                    "C:/Program Files/Amazon Corretto"
                ).forEach { base ->
                    File(base).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                        runCatching { parseJavaVersion(dir) }.getOrNull()?.let { runtimes.add(it) }
                    }
                }
            }
            os.contains("mac") -> {
                // macOS standard locations
                listOf(
                    "/Library/Java/JavaVirtualMachines",
                    "/System/Library/Java/JavaVirtualMachines",
                    File(System.getProperty("user.home"), ".sdkman/candidates/java").absolutePath
                ).forEach { base ->
                    File(base).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                        runCatching { parseJavaVersion(dir) }.getOrNull()?.let { runtimes.add(it) }
                    }
                }
            }
            else -> {
                // Linux standard locations
                listOf(
                    "/usr/lib/jvm",
                    "/usr/java",
                    "/opt/java",
                    File(System.getProperty("user.home"), ".sdkman/candidates/java").absolutePath
                ).forEach { base ->
                    File(base).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                        runCatching { parseJavaVersion(dir) }.getOrNull()?.let { runtimes.add(it) }
                    }
                }
            }
        }

        return runtimes
    }

    fun findSuitable(minVersion: Int = 21): Result<JavaRuntime> = runCatching {
        val runtimes = detectAll().filter { it.version.major >= minVersion }
        if (runtimes.isEmpty()) {
            throw JavaNotFoundException("No Java $minVersion+ runtime found. Please install Java $minVersion or higher.")
        }
        runtimes.maxByOrNull { it.version }!!
    }

    fun parseJavaVersion(javaHome: File): JavaRuntime {
        val executable = File(javaHome, if (isWindows()) "bin/java.exe" else "bin/java")
        if (!executable.exists()) throw JavaNotFoundException("Java executable not found in $javaHome")

        val process = ProcessBuilder(executable.absolutePath, "-version").redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().decodeToString()
        process.waitFor()

        val version = parseVersionOutput(output)
        val vendor = parseVendor(output)

        return JavaRuntime(javaHome, version, vendor)
    }

    private fun parseVersionOutput(output: String): JavaVersion {
        val patterns = listOf(
            Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[._-](\\w+))?\""),
            Pattern.compile("openjdk version \"(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[._-](\\w+))?\""),
            Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[._-](\\w+))?")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(output)
            if (matcher.find()) {
                val major = matcher.group(1)?.toIntOrNull() ?: 0
                val minor = matcher.group(2)?.toIntOrNull() ?: 0
                val patch = matcher.group(3)?.toIntOrNull() ?: 0
                val build = matcher.group(4)
                return JavaVersion(major, minor, patch, build)
            }
        }

        return JavaVersion(0)
    }

    private fun parseVendor(output: String): String {
        return when {
            output.contains("Eclipse Adoptium") || output.contains("AdoptOpenJDK") -> "Eclipse Adoptium"
            output.contains("Microsoft") -> "Microsoft"
            output.contains("Amazon Corretto") -> "Amazon"
            output.contains("Azul") -> "Azul Systems"
            output.contains("Oracle") -> "Oracle"
            output.contains("OpenJDK") -> "OpenJDK"
            else -> "Unknown"
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}