package com.neonlauncher.core.runtime

import java.lang.management.ManagementFactory

data class HardwareInfo(
    val totalRamMb: Long,
    val cores: Int,
    val osName: String,
    val osArch: String
) {
    val ramLabel: String
        get() = if (totalRamMb > 0) "${Math.round(totalRamMb / 1024.0)} GB" else "desconocida"
}

/** Datos de la máquina para recomendar memoria y avisar de limitaciones. */
object SystemProbe {

    fun detect(): HardwareInfo {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        val total = runCatching {
            // com.sun.management.OperatingSystemMXBean, por reflexión para no depender del módulo
            bean.javaClass.getMethod("getTotalMemorySize").invoke(bean) as Long
        }.getOrDefault(0L)

        return HardwareInfo(
            totalRamMb = total / (1024 * 1024),
            cores = Runtime.getRuntime().availableProcessors(),
            osName = System.getProperty("os.name"),
            osArch = System.getProperty("os.arch")
        )
    }

    /**
     * Regla simple y conservadora: nunca más de la mitad de la RAM, nunca más de 8 GB,
     * y 4 GB como base razonable para mods.
     */
    fun recommendedMemoryMb(info: HardwareInfo): Int {
        if (info.totalRamMb <= 0) return 4096
        val bySize = when {
            info.totalRamMb >= 24 * 1024 -> 8192
            info.totalRamMb >= 16 * 1024 -> 6144
            info.totalRamMb >= 8 * 1024 -> 4096
            info.totalRamMb >= 6 * 1024 -> 3072
            else -> 2048
        }
        val half = (info.totalRamMb / 2).toInt()
        return bySize.coerceAtMost(half).coerceAtLeast(1536)
    }
}
