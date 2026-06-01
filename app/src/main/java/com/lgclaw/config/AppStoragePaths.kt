package com.lgclaw.config

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AppStoragePaths {
    private const val ROOT_DIR = "lgclaw"
    private const val STORAGE_DIR = "storage"
    private const val MEMORY_DIR = "memory"
    private const val SKILLS_DIR = "skills"
    private const val TEMPLATES_DIR = "templates"
    private const val TOOLS_DIR = "tools"
    private const val TERMINAL_DIR = "terminal"
    private const val TERMINAL_HOME_DIR = "home"
    private const val TERMINAL_WORKSPACES_DIR = "workspaces"
    private const val TERMINAL_BIN_DIR = "bin"
    private const val TERMINAL_LOGS_DIR = "logs"
    private const val TERMINAL_CACHE_DIR = "cache"
    private const val DOCS_DIR = "docs"
    private const val LOGS_DIR = "logs"
    private const val AVATARS_DIR = "avatars"
    private const val CRON_LOG_FILE = "cron.log"
    private const val AGENT_LOG_FILE = "agent.log"

    fun appRoot(context: Context): File = File(context.filesDir, ROOT_DIR).apply { mkdirs() }

    fun storageRoot(context: Context): File = File(appRoot(context), STORAGE_DIR).apply { mkdirs() }

    fun workspaceRoot(context: Context): File = storageRoot(context)

    fun memoryDir(context: Context): File = File(storageRoot(context), MEMORY_DIR).apply { mkdirs() }

    fun skillsDir(context: Context): File = File(storageRoot(context), SKILLS_DIR).apply { mkdirs() }

    fun templatesDir(context: Context): File = File(storageRoot(context), TEMPLATES_DIR).apply { mkdirs() }

    fun toolsDir(context: Context): File = File(storageRoot(context), TOOLS_DIR).apply { mkdirs() }

    fun terminalDir(context: Context): File = File(storageRoot(context), TERMINAL_DIR).apply { mkdirs() }

    fun terminalPrefixDir(context: Context): File =
        File("/data/data/${context.packageName}/files/usr").apply { mkdirs() }

    fun terminalTermuxHomeDir(context: Context): File =
        File("/data/data/${context.packageName}/files/home").apply { mkdirs() }

    fun terminalHomeDir(context: Context): File = File(terminalDir(context), TERMINAL_HOME_DIR).apply { mkdirs() }

    fun terminalWorkspacesDir(context: Context): File = File(terminalDir(context), TERMINAL_WORKSPACES_DIR).apply { mkdirs() }

    fun terminalBinDir(context: Context): File = File(terminalDir(context), TERMINAL_BIN_DIR).apply { mkdirs() }

    fun terminalLogsDir(context: Context): File = File(terminalDir(context), TERMINAL_LOGS_DIR).apply { mkdirs() }

    fun terminalCacheDir(context: Context): File = File(terminalDir(context), TERMINAL_CACHE_DIR).apply { mkdirs() }

    fun docsDir(context: Context): File = File(storageRoot(context), DOCS_DIR).apply { mkdirs() }

    fun logsDir(context: Context): File = File(storageRoot(context), LOGS_DIR).apply { mkdirs() }

    fun avatarsDir(context: Context): File = File(storageRoot(context), AVATARS_DIR).apply { mkdirs() }

    fun heartbeatDocFile(context: Context): File = File(docsDir(context), HeartbeatDoc.FILE_NAME)

    fun cronLogFile(context: Context): File = File(logsDir(context), CRON_LOG_FILE)

    fun agentLogFile(context: Context): File = File(logsDir(context), AGENT_LOG_FILE)

    fun migrateLegacyLayout(context: Context) {
        val appRoot = appRoot(context)
        val workspace = storageRoot(context)

        migrateDirectoryCandidates(
            candidates = listOf(
                File(context.filesDir, MEMORY_DIR),
                File(appRoot, MEMORY_DIR)
            ),
            target = memoryDir(context)
        )
        migrateDirectoryCandidates(
            candidates = listOf(
                File(context.filesDir, SKILLS_DIR),
                File(appRoot, SKILLS_DIR)
            ),
            target = skillsDir(context)
        )
        migrateDirectoryCandidates(
            candidates = listOf(
                File(context.filesDir, TEMPLATES_DIR),
                File(appRoot, TEMPLATES_DIR)
            ),
            target = templatesDir(context)
        )
        migrateDirectoryCandidates(
            candidates = listOf(
                File(context.filesDir, TOOLS_DIR),
                File(appRoot, TOOLS_DIR)
            ),
            target = toolsDir(context)
        )

        moveFileIfNeeded(
            from = File(context.filesDir, HeartbeatDoc.FILE_NAME),
            to = heartbeatDocFile(context)
        )
        moveFileIfNeeded(
            from = File(appRoot, HeartbeatDoc.FILE_NAME),
            to = heartbeatDocFile(context)
        )
        moveFileIfNeeded(
            from = File(workspace, HeartbeatDoc.FILE_NAME),
            to = heartbeatDocFile(context)
        )

        moveFileIfNeeded(
            from = File(appRoot, "cron/$CRON_LOG_FILE"),
            to = cronLogFile(context),
            mergeIfTargetExists = true
        )
    }

    private fun migrateDirectoryCandidates(candidates: List<File>, target: File) {
        target.mkdirs()
        val targetCanonical = runCatching { target.canonicalFile }.getOrNull()
        candidates.forEach { source ->
            if (!source.exists() || !source.isDirectory) return@forEach
            val sourceCanonical = runCatching { source.canonicalFile }.getOrNull()
            if (targetCanonical != null && sourceCanonical == targetCanonical) return@forEach
            moveDirectoryContentsIfNeeded(source, target)
        }
    }

    private fun moveDirectoryContentsIfNeeded(from: File, to: File) {
        to.mkdirs()
        from.listFiles().orEmpty().forEach { child ->
            val target = File(to, child.name)
            if (target.exists()) {
                if (child.isDirectory && target.isDirectory) {
                    moveDirectoryContentsIfNeeded(child, target)
                    runCatching { child.deleteRecursively() }
                }
                return@forEach
            }
            if (!child.renameTo(target)) {
                if (child.isDirectory) {
                    copyDirectory(child, target)
                } else {
                    copyFile(child, target)
                }
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun moveFileIfNeeded(from: File, to: File, mergeIfTargetExists: Boolean = false) {
        if (!from.exists() || !from.isFile) return
        to.parentFile?.mkdirs()
        val sameFile = runCatching { from.canonicalFile == to.canonicalFile }.getOrDefault(false)
        if (sameFile) return

        if (!to.exists()) {
            if (!from.renameTo(to)) {
                copyFile(from, to)
                runCatching { from.delete() }
            }
            return
        }

        if (mergeIfTargetExists) {
            runCatching {
                val existing = to.readText(Charsets.UTF_8)
                val incoming = from.readText(Charsets.UTF_8)
                val merged = buildString {
                    append(existing)
                    if (existing.isNotEmpty() && !existing.endsWith("\n")) append("\n")
                    append(incoming)
                }
                to.writeText(merged, Charsets.UTF_8)
            }
            runCatching { from.delete() }
        }
    }

    private fun copyFile(from: File, to: File) {
        to.parentFile?.mkdirs()
        FileInputStream(from).use { input ->
            FileOutputStream(to).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun copyDirectory(from: File, to: File) {
        if (!from.exists() || !from.isDirectory) return
        to.mkdirs()
        from.listFiles().orEmpty().forEach { child ->
            val target = File(to, child.name)
            if (child.isDirectory) copyDirectory(child, target) else copyFile(child, target)
        }
    }
}
