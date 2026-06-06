package com.lgclaw.terminal

import java.io.File

object TerminalRuntimeHealth {
    private val bashRequiredLibraries = listOf("libreadline.so.8")

    fun inspect(prefix: File): TerminalHealthReport {
        val bin = File(prefix, "bin")
        val lib = File(prefix, "lib")
        val bash = File(bin, "bash")
        if (!bash.exists()) return TerminalHealthReport()

        val missing = bashRequiredLibraries.filter { required ->
            !File(lib, required).exists()
        }
        return TerminalHealthReport(
            missingLibraries = missing,
            errorMessage = if (missing.isEmpty()) {
                ""
            } else {
                "Embedded bash is missing required libraries: ${missing.joinToString(", ")}. " +
                    "Command launch was stopped early to avoid a long timeout. " +
                    "Reinstall the APK or rebuild rootfs/offline-debs with the missing libraries."
            }
        )
    }

    fun repair(prefix: File): TerminalHealthReport {
        val lib = File(prefix, "lib")
        bashRequiredLibraries.forEach { required ->
            val target = File(lib, required)
            if (!target.exists()) {
                val compatible = lib.listFiles().orEmpty().firstOrNull { file ->
                    file.isFile && file.name.startsWith(required.removeSuffix(".8"))
                }
                if (compatible != null) {
                    runCatching { compatible.copyTo(target, overwrite = true) }
                }
            }
        }
        return inspect(prefix)
    }
}

data class TerminalHealthReport(
    val missingLibraries: List<String> = emptyList(),
    val errorMessage: String = ""
) {
    val healthy: Boolean get() = missingLibraries.isEmpty()
}
