package com.lgclaw.terminal

import android.content.Context
import android.os.Build
import android.system.Os
import com.lgclaw.config.AppStoragePaths
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

class TerminalToolchainManager(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val bootstrapTools = listOf("sh", "bash", "pkg", "apt", "tar")
    private val developerTools = listOf("node", "npm", "python", "pip", "git", "ssh", "uv")
    private val requiredTools = bootstrapTools + developerTools
    private val bootstrapVersion = "lgclaw-terminal-v7-rootfs"
    private val rootfsBundleName = "rootfs.zip"
    private val offlineDebBundleName = "offline-debs.zip"
    private val oldTermuxRoot = "/data/data/com.termux"
    private val lgclawRoot: String get() = "/data/data/${appContext.packageName}"
    private val oldTermuxPrefix = "/data/data/com.termux/files/usr"
    private val termuxPrefix: File get() = AppStoragePaths.terminalPrefixDir(appContext)
    private val termuxHome: File get() = AppStoragePaths.terminalTermuxHomeDir(appContext)

    fun ensureBaseDirs() {
        AppStoragePaths.terminalDir(appContext)
        termuxPrefix
        termuxHome
        AppStoragePaths.terminalWorkspacesDir(appContext)
        AppStoragePaths.terminalBinDir(appContext)
        AppStoragePaths.terminalLogsDir(appContext)
        AppStoragePaths.terminalCacheDir(appContext)
        ensureAndroidRuntimeDirs()
    }

    fun ensureLayout(): TerminalToolchainStatus {
        ensureBaseDirs()
        if (!extractBundledRootfsIfPresent()) {
            extractBundledToolchainIfPresent()
        }
        return inspect()
    }

    fun inspect(): TerminalToolchainStatus {
        val shell = resolveExecutable("sh")
            ?: resolveExecutable("bash")
            ?: "/system/bin/sh".takeIf { File(it).exists() }
            ?: ""
        val installed = requiredTools.mapNotNull { tool ->
            tool.takeIf { resolveExecutable(it) != null }
        }.toSet()
        val missing = requiredTools.toSet() - installed
        val missingBootstrap = bootstrapTools.toSet() - installed
        return TerminalToolchainStatus(
            ready = shell.isNotBlank() && missingBootstrap.isEmpty(),
            shellPath = shell,
            installedExecutables = installed,
            missingExecutables = missing,
            toolchainRoot = termuxPrefix.absolutePath,
            lastError = when {
                shell.isBlank() -> "????????????????????? shell"
                missingBootstrap.isNotEmpty() -> "???????????????????????????${missingBootstrap.joinToString("???")}"
                else -> ""
            }
        )
    }

    fun workspaceDir(sessionId: String): File {
        val safe = sessionId
            .trim()
            .ifBlank { "local" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
        return File(AppStoragePaths.terminalWorkspacesDir(appContext), safe).apply { mkdirs() }
    }

    fun buildEnvironment(workspace: File): Map<String, String> {
        val prefix = termuxPrefix.absolutePath
        val home = termuxHome.absolutePath
        val binDirs = listOf(
            File(prefix, "bin"),
            File(prefix, "bin/applets"),
            AppStoragePaths.terminalBinDir(appContext),
            File(home, ".local/bin")
        ).map { it.absolutePath }
        val path = (binDirs + listOf("/system/bin", "/system/xbin"))
            .distinct()
            .joinToString(File.pathSeparator)
        return mapOf(
            "PREFIX" to prefix,
            "TERMUX_PREFIX" to prefix,
            "HOME" to home,
            "PWD" to workspace.absolutePath,
            "TMPDIR" to AppStoragePaths.terminalCacheDir(appContext).absolutePath,
            "PATH" to path,
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "ANDROID_ROOT" to "/system",
            "ANDROID_DATA" to "/data",
            "LD_LIBRARY_PATH" to listOf(File(prefix, "lib").absolutePath, "/system/lib64", "/system/lib")
                .joinToString(File.pathSeparator),
            "SSL_CERT_FILE" to File(prefix, "etc/tls/cert.pem").absolutePath,
            "SSL_CERT_DIR" to File(prefix, "etc/tls/certs").absolutePath,
            "LGCLAW_TERMINAL" to "1",
            "LGCLAW_WORKSPACE" to workspace.absolutePath,
            // Python performance knobs. These are read by CPython at
            // start and cut cold-start time for matplotlib/numpy/pandas
            // noticeably because no .pyc files are written on first run.
            "PYTHONDONTWRITEBYTECODE" to "1",
            "PYTHONOPTIMIZE" to "1",
            "PYTHONIOENCODING" to "utf-8",
            "PYTHONUTF8" to "1",
            "PYTHONUNBUFFERED" to "1",
            "PYTHONNOUSERSITE" to "1",
            // uv reads UV_CONFIG_FILE for its TOML; point it at the
            // bundle so offline wheels are picked up without flags.
            "UV_CONFIG_FILE" to File(prefix, "etc/uv/uv.toml").absolutePath,
            "PIP_CONFIG_FILE" to File(prefix, "etc/pip.conf").absolutePath
        )
    }

    fun corePackageInstallCommand(): String {
        val offlineDir = prepareOfflineDebBundleDir()
        if (offlineDir != null) {
            return offlinePackageInstallCommand(offlineDir)
        }
        return """
            set -e
            export DEBIAN_FRONTEND=noninteractive
            pkg update -y || {
              sed -i 's#https://packages-cf.termux.dev/apt/termux-main/#https://packages.termux.dev/apt/termux-main/#g' ${'$'}PREFIX/etc/apt/sources.list || true
              apt update -y
            }
            (pkg install -y nodejs python git openssh uv || pkg install -y nodejs-lts python git openssh)
            if ! command -v uv >/dev/null 2>&1; then
              pkg install -y uv || true
            fi
            if ! command -v uv >/dev/null 2>&1 && command -v python >/dev/null 2>&1; then
              python -m pip install --user -U uv || true
            fi
            command -v node >/dev/null 2>&1 && node -v || true
            command -v npm >/dev/null 2>&1 && npm -v || true
            command -v python >/dev/null 2>&1 && python --version || true
            command -v pip >/dev/null 2>&1 && pip --version || true
            command -v git >/dev/null 2>&1 && git --version || true
            command -v ssh >/dev/null 2>&1 && ssh -V || true
            command -v uv >/dev/null 2>&1 && uv --version || true
        """.trimIndent()
    }

    fun finalizePackageInstall() {
        ensureAndroidRuntimeDirs()
        patchPrefixInRegularFiles(termuxPrefix)
        markExecutableTree(termuxPrefix)
        File(termuxPrefix, ".lgclaw_offline_ready").writeText(
            "version=$bootstrapVersion\ncreated_at=${System.currentTimeMillis()}\n",
            Charsets.UTF_8
        )
    }

    fun resolveExecutable(name: String): String? {
        val candidates = listOf(
            File(termuxPrefix, "bin/$name"),
            File(AppStoragePaths.terminalBinDir(appContext), name),
            File(termuxHome, ".local/bin/$name"),
            File(AppStoragePaths.terminalHomeDir(appContext), ".local/bin/$name"),
            File("/system/bin/$name"),
            File("/system/xbin/$name")
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }

    fun listWorkspaces(): List<TerminalWorkspaceInfo> {
        return AppStoragePaths.terminalWorkspacesDir(appContext)
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .sortedByDescending { it.lastModified() }
            .map {
                TerminalWorkspaceInfo(
                    sessionId = it.name,
                    path = it.absolutePath,
                    lastUsedAt = it.lastModified().coerceAtLeast(0L)
                )
            }
    }

    private fun extractBundledToolchainIfPresent() {
        val abi = if (Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }) {
            "arm64-v8a"
        } else {
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase(Locale.US)
        }
        val assetPath = "terminal/$abi/toolchain.zip"
        val target = termuxPrefix
        val marker = File(target, ".lgclaw_bootstrap_$abi")
        if (marker.exists() && marker.readTextOrEmpty().contains(bootstrapVersion)) return
        val assets = runCatching { appContext.assets.list("terminal/$abi").orEmpty().toList() }
            .getOrDefault(emptyList())
        if (!assets.contains("toolchain.zip")) return

        var symlinkSpec = ""
        runCatching {
            resetBrokenBootstrap(target)
            appContext.assets.open(assetPath).use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val rawName = entry.name.trim()
                        if (rawName.isBlank()) {
                            zip.closeEntry()
                            continue
                        }
                        if (rawName == "SYMLINKS.txt") {
                            symlinkSpec = zip.readBytes().toString(Charsets.UTF_8)
                            zip.closeEntry()
                            continue
                        }
                        val outFile = File(target, rawName)
                        val canonicalTarget = target.canonicalFile
                        val canonicalOut = outFile.canonicalFile
                        if (!canonicalOut.path.startsWith(canonicalTarget.path)) {
                            zip.closeEntry()
                            continue
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                            if (shouldBeExecutable(outFile)) {
                                outFile.setExecutable(true, false)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            restoreSymlinks(target, symlinkSpec)
            ensureCriticalEntrypoints(target)
            markExecutableTree(target)
            marker.writeText(
                "version=$bootstrapVersion\nabi=$abi\nprefix=${target.absolutePath}\ncreated_at=${System.currentTimeMillis()}\n",
                Charsets.UTF_8
            )
        }.onFailure { error ->
            File(target, ".lgclaw_bootstrap_error").writeText(
                error.stackTraceToString(),
                Charsets.UTF_8
            )
        }
    }

    private fun extractBundledRootfsIfPresent(): Boolean {
        val abi = if (Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }) {
            "arm64-v8a"
        } else {
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase(Locale.US)
        }
        val assetPath = "terminal/$abi/$rootfsBundleName"
        val target = termuxPrefix
        val marker = File(target, ".lgclaw_rootfs_$abi")
        val assets = runCatching { appContext.assets.list("terminal/$abi").orEmpty().toList() }
            .getOrDefault(emptyList())
        if (!assets.contains(rootfsBundleName)) return false
        if (marker.exists() && marker.readTextOrEmpty().contains(bootstrapVersion)) return true

        var symlinkSpec = ""
        return runCatching {
            resetBrokenBootstrap(target)
            appContext.assets.open(assetPath).use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val rawName = entry.name.trim()
                        if (rawName.isBlank()) {
                            zip.closeEntry()
                            continue
                        }
                        if (rawName == "SYMLINKS.txt") {
                            symlinkSpec = zip.readBytes().toString(Charsets.UTF_8)
                            zip.closeEntry()
                            continue
                        }
                        val outFile = File(target, rawName)
                        val canonicalTarget = target.canonicalFile
                        val canonicalOut = outFile.canonicalFile
                        if (!canonicalOut.path.startsWith(canonicalTarget.path)) {
                            zip.closeEntry()
                            continue
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                            if (shouldBeExecutable(outFile)) {
                                outFile.setExecutable(true, false)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            restoreSymlinks(target, symlinkSpec)
            ensureCriticalEntrypoints(target)
            markExecutableTree(target)
            marker.writeText(
                "version=$bootstrapVersion\nabi=$abi\nprefix=${target.absolutePath}\ncreated_at=${System.currentTimeMillis()}\n",
                Charsets.UTF_8
            )
            true
        }.getOrElse { error ->
            File(target, ".lgclaw_rootfs_error").writeText(error.stackTraceToString(), Charsets.UTF_8)
            false
        }
    }

    private fun prepareOfflineDebBundleDir(): File? {
        val abi = if (Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }) {
            "arm64-v8a"
        } else {
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase(Locale.US)
        }
        val assetPath = "terminal/$abi/$offlineDebBundleName"
        val assets = runCatching { appContext.assets.list("terminal/$abi").orEmpty().toList() }
            .getOrDefault(emptyList())
        if (!assets.contains(offlineDebBundleName)) return null
        val target = File(AppStoragePaths.terminalCacheDir(appContext), "offline-debs-$bootstrapVersion")
        val marker = File(target, ".ready")
        if (marker.exists()) return target
        runCatching { target.deleteRecursively() }
        target.mkdirs()
        runCatching {
            appContext.assets.open(assetPath).use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val rawName = entry.name.trim()
                        if (rawName.isBlank() || entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        val safeName = rawName.substringAfterLast('/').substringAfterLast('\\')
                        if (!safeName.endsWith(".deb") && safeName != "manifest.json") {
                            zip.closeEntry()
                            continue
                        }
                        val outFile = File(target, safeName)
                        FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                        zip.closeEntry()
                    }
                }
            }
            marker.writeText(System.currentTimeMillis().toString(), Charsets.UTF_8)
        }.onFailure {
            runCatching { target.deleteRecursively() }
            return null
        }
        return target
    }

    private fun offlinePackageInstallCommand(packageDir: File): String {
        val debPath = packageDir.absolutePath
        return """
            set -e
            export DEBIAN_FRONTEND=noninteractive
            export DPKG_COLORS=never
            export LGCLAW_OFFLINE_DEBS="$debPath"
            cd "${'$'}PREFIX"
            echo "???????????? LGClaw ????????????????????????..."
            dpkg --force-confnew --force-overwrite -i "${'$'}LGCLAW_OFFLINE_DEBS"/*.deb || {
              dpkg --configure -a || true
              dpkg --force-confnew --force-overwrite -i "${'$'}LGCLAW_OFFLINE_DEBS"/*.deb
            }
            dpkg --configure -a
            command -v node >/dev/null 2>&1 && node -v
            command -v npm >/dev/null 2>&1 && npm -v
            command -v python >/dev/null 2>&1 && python --version
            command -v pip >/dev/null 2>&1 && pip --version
            command -v git >/dev/null 2>&1 && git --version
            command -v ssh >/dev/null 2>&1 && ssh -V || true
            command -v uv >/dev/null 2>&1 && uv --version
        """.trimIndent()
    }

    private fun restoreSymlinks(target: File, symlinkSpec: String) {
        if (symlinkSpec.isBlank()) return
        symlinkSpec.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val separator = symlinkSeparator(line) ?: return@forEach
                val targetText = line.substringBefore(separator)
                    .replace(oldTermuxRoot, lgclawRoot)
                    .replace(oldTermuxPrefix, termuxPrefix.absolutePath)
                    .trim()
                val linkText = line.substringAfter(separator).trim()
                if (targetText.isBlank() || linkText.isBlank()) return@forEach
                val linkFile = File(target, linkText.removePrefix("./"))
                val canonicalTarget = target.canonicalFile
                val canonicalLink = runCatching { linkFile.canonicalFile }.getOrNull() ?: return@forEach
                if (!canonicalLink.path.startsWith(canonicalTarget.path)) return@forEach
                linkFile.parentFile?.mkdirs()
                runCatching { linkFile.delete() }
                runCatching { Os.symlink(targetText, linkFile.absolutePath) }.onFailure {
                    createSymlinkFallback(targetText, linkFile)
                }
            }
    }

    private fun symlinkSeparator(line: String): String? {
        return listOf("???", "???", "->", "=>").firstOrNull { line.contains(it) }
    }

    private fun ensureCriticalEntrypoints(target: File) {
        val bin = File(target, "bin").apply { mkdirs() }
        ensureSymlinkOrCopy("dash", File(bin, "sh"))
        ensureSymlinkOrCopy("python3.13", File(bin, "python"))
        ensureSymlinkOrCopy("python3.13", File(bin, "python3"))
        ensureSymlinkOrCopy("../libexec/git-core/git", File(bin, "git"))
        ensureShellWrapper(
            linkFile = File(bin, "npm"),
            command = "\"${File(bin, "node").absolutePath}\" \"${File(target, "lib/node_modules/npm/bin/npm-cli.js").absolutePath}\" \"\$@\""
        )
        ensureShellWrapper(
            linkFile = File(bin, "npx"),
            command = "\"${File(bin, "node").absolutePath}\" \"${File(target, "lib/node_modules/npm/bin/npx-cli.js").absolutePath}\" \"\$@\""
        )
        ensureShellWrapper(
            linkFile = File(bin, "pip"),
            command = "\"${File(bin, "python").absolutePath}\" -m pip \"\$@\""
        )
        ensureShellWrapper(
            linkFile = File(bin, "pip3"),
            command = "\"${File(bin, "python").absolutePath}\" -m pip \"\$@\""
        )
    }

    private fun ensureSymlinkOrCopy(linkTarget: String, linkFile: File) {
        if (linkFile.exists()) {
            linkFile.setExecutable(true, false)
            return
        }
        linkFile.parentFile?.mkdirs()
        runCatching { Os.symlink(linkTarget, linkFile.absolutePath) }.onFailure {
            createSymlinkFallback(linkTarget, linkFile)
        }
        if (linkFile.exists()) {
            linkFile.setExecutable(true, false)
        }
    }

    private fun ensureShellWrapper(linkFile: File, command: String) {
        if (linkFile.exists()) {
            linkFile.setExecutable(true, false)
            return
        }
        val shell = File(termuxPrefix, "bin/sh")
        linkFile.parentFile?.mkdirs()
        linkFile.writeText(
            "#!${shell.absolutePath}\nexec $command\n",
            Charsets.UTF_8
        )
        linkFile.setExecutable(true, false)
    }

    private fun createSymlinkFallback(targetText: String, linkFile: File) {
        val targetFile = if (targetText.startsWith("/")) File(targetText) else File(linkFile.parentFile, targetText)
        if (targetFile.exists() && targetFile.isFile) {
            runCatching {
                targetFile.copyTo(linkFile, overwrite = true)
                linkFile.setExecutable(targetFile.canExecute(), false)
            }
        }
    }

    private fun patchPrefixInRegularFiles(root: File) {
        val oldBytes = oldTermuxPrefix.toByteArray(Charsets.UTF_8)
        val newBytes = termuxPrefix.absolutePath.toByteArray(Charsets.UTF_8)
        val oldRootBytes = oldTermuxRoot.toByteArray(Charsets.UTF_8)
        val newRootBytes = lgclawRoot.toByteArray(Charsets.UTF_8)
        val replacements = listOfNotNull(
            (oldRootBytes to newRootBytes).takeIf { oldRootBytes.size == newRootBytes.size },
            (oldBytes to newBytes).takeIf { oldBytes.size == newBytes.size }
        )
        if (replacements.isEmpty()) return
        root.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".lgclaw_bootstrap") && it.length() in 1..64L * 1024L * 1024L }
            .forEach { file ->
                runCatching {
                    var original = file.readBytes()
                    var patched = original
                    replacements.forEach { (oldValue, newValue) ->
                        patched = replaceBytes(patched, oldValue, newValue)
                    }
                    if (patched !== original) {
                        file.writeBytes(patched)
                    }
                }
            }
    }

    private fun replaceBytes(source: ByteArray, needle: ByteArray, replacement: ByteArray): ByteArray {
        var index = indexOf(source, needle, 0)
        if (index < 0) return source
        val output = source.copyOf()
        while (index >= 0) {
            replacement.copyInto(output, destinationOffset = index)
            index = indexOf(output, needle, index + replacement.size)
        }
        return output
    }

    private fun indexOf(source: ByteArray, needle: ByteArray, startIndex: Int): Int {
        if (needle.isEmpty() || source.size < needle.size) return -1
        var i = startIndex.coerceAtLeast(0)
        while (i <= source.size - needle.size) {
            var j = 0
            while (j < needle.size && source[i + j] == needle[j]) j++
            if (j == needle.size) return i
            i++
        }
        return -1
    }

    private fun markExecutableTree(root: File) {
        root.walkTopDown()
            .filter { it.isFile && shouldBeExecutable(it) }
            .forEach { file -> runCatching { file.setExecutable(true, false) } }
    }

    private fun shouldBeExecutable(file: File): Boolean {
        val relative = runCatching { file.relativeTo(termuxPrefix).invariantSeparatorsPath }.getOrDefault(file.path)
        return relative.startsWith("bin/") ||
            relative.startsWith("libexec/") ||
            relative.startsWith("lib/apt/") ||
            relative.startsWith("lib/dpkg/") ||
            relative.endsWith(".sh")
    }

    private fun ensureAndroidRuntimeDirs() {
        listOf(
            File(lgclawRoot, "cache"),
            File(lgclawRoot, "cache/apt"),
            File(lgclawRoot, "cache/apt/archives"),
            File(lgclawRoot, "cache/apt/archives/partial"),
            File(termuxPrefix, "var/lib/apt/lists/partial"),
            File(termuxPrefix, "var/cache/apt/archives/partial"),
            File(termuxPrefix, "tmp")
        ).forEach { runCatching { it.mkdirs() } }
    }

    private fun resetBrokenBootstrap(target: File) {
        if (!target.exists()) return
        target.listFiles().orEmpty().forEach { child ->
            runCatching { child.deleteRecursively() }
        }
        target.mkdirs()
        ensureAndroidRuntimeDirs()
    }

    private fun File.readTextOrEmpty(): String =
        runCatching { readText(Charsets.UTF_8) }.getOrDefault("")
}
