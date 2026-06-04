package com.lgclaw.terminal

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.lgclaw.config.AppSession
import com.lgclaw.config.AppStoragePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TerminalController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TerminalRuntimeState())
    val state: StateFlow<TerminalRuntimeState> = _state.asStateFlow()

    private val outputMutex = Any()
    private val activeProcesses = ConcurrentHashMap<String, Process>()
    private val activeSessions = ConcurrentHashMap<String, String>()
    private val sessionOutputBuffers = ConcurrentHashMap<String, ArrayDeque<TerminalOutputLine>>()
    private val developerToolNames = setOf("node", "npm", "python", "pip", "git", "ssh", "uv")
    private val layoutMutex = Mutex()

    @Volatile
    private var context: Context? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var installingCorePackages = false

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        this.context = appContext
        if (!initialized) initialized = true
        scope.launch {
            runCatching {
                refreshToolchainStatus(prepareIfMissing = false)
                refreshOverlayPermission(appContext)
                refreshWorkspaces()
                bootstrapPythonPackages(appContext)
            }.onFailure { error ->
                _state.update {
                    it.copy(lastError = "终端初始化失败：${error.message ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    fun refreshOverlayPermission(context: Context) {
        val granted = Settings.canDrawOverlays(context)
        _state.update { it.copy(overlayPermissionGranted = granted) }
    }

    suspend fun refreshToolchainStatus(prepareIfMissing: Boolean = true): TerminalToolchainStatus = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = requireContext()
            val manager = TerminalToolchainManager(appContext)
            val status = if (prepareIfMissing) {
                layoutMutex.withLock { manager.ensureLayout() }
            } else {
                manager.ensureBaseDirs()
                manager.inspect()
            }
            _state.update {
                it.copy(
                    ready = status.ready,
                    toolchainRoot = status.toolchainRoot,
                    shellPath = status.shellPath,
                    installedExecutables = status.installedExecutables,
                    missingExecutables = status.missingExecutables,
                    lastError = status.lastError
                )
            }
            status
        }.getOrElse { error ->
            val appContext = context
            val toolchainRoot = appContext?.let { AppStoragePaths.terminalPrefixDir(it).absolutePath }.orEmpty()
            val fallback = TerminalToolchainStatus(
                ready = false,
                shellPath = "",
                installedExecutables = emptySet(),
                missingExecutables = developerToolNames + bootstrapMissingFallback(),
                toolchainRoot = toolchainRoot,
                lastError = "终端初始化失败：${error.message ?: error.javaClass.simpleName}"
            )
            _state.update {
                it.copy(
                    ready = false,
                    toolchainRoot = fallback.toolchainRoot,
                    shellPath = "",
                    installedExecutables = emptySet(),
                    missingExecutables = fallback.missingExecutables,
                    lastError = fallback.lastError
                )
            }
            fallback
        }
    }

    fun setTerminalMode(sessionId: String, enabled: Boolean) {
        val sid = normalizeSessionId(sessionId)
        _state.update { current ->
            val nextSessions = current.terminalModeSessions.toMutableSet().apply {
                if (enabled) add(sid) else remove(sid)
            }
            current.copy(
                terminalModeSessions = nextSessions,
                activeSessionId = current.activeSessionId.takeIf { it.isNotBlank() } ?: sid
            )
        }
        appendStatusLine(sid, if (enabled) "终端模式已开启" else "终端模式已退出")
    }

    fun toggleTerminalMode(sessionId: String): Boolean {
        val sid = normalizeSessionId(sessionId)
        val enabled = !isTerminalModeEnabled(sid)
        setTerminalMode(sid, enabled)
        return enabled
    }

    fun isTerminalModeEnabled(sessionId: String): Boolean {
        return _state.value.terminalModeSessions.contains(normalizeSessionId(sessionId))
    }


    /**
     * Extract bundled wheels + site-packages, write pip.conf, and report
     * the result into the runtime state. Idempotent; safe to call on
     * every app launch. Runs on Dispatchers.IO and never blocks chat UI.
     */
    private suspend fun bootstrapPythonPackages(appContext: Context, force: Boolean = false) = coroutineScope {
        val manager = TerminalPackageManager(appContext)
        val report = runCatching { manager.bootstrap(force) }.getOrElse {
            TerminalPackageManager.BootstrapReport(
                issues = listOf("bootstrap crashed: " + it.javaClass.simpleName + ": " + (it.message ?: ""))
            )
        }
        if (report.wheelsCached > 0 || report.sitePackagesExtracted || report.pycFilesCompiled > 0) {
            Log.i(
                "LGClaw.Terminal",
                "PackageManager bootstrap: " +
                    "wheels=" + report.wheelsCached +
                    " site-packages=" + report.sitePackagesExtracted +
                    " pyc=" + report.pycFilesCompiled +
                    " python=" + report.pythonVersion +
                    " pip=" + report.pipVersion +
                    " uv=" + report.uvVersion
            )
        }
        if (report.issues.isNotEmpty()) {
            Log.w("LGClaw.Terminal", "PackageManager issues: " + report.issues.joinToString("; "))
            _state.update { current ->
                current.copy(lastError = report.issues.joinToString("; ").ifBlank { current.lastError })
            }
        }
    }

    /**
     * Public hook: install a Python package using the local wheel cache
     * and binary-only pip config. Returns a structured result the UI
     * shows inline.
     */
    suspend fun installPythonPackage(spec: String): TerminalPackageManager.InstallResult {
        val appContext = requireContext()
        return TerminalPackageManager(appContext).installPackage(spec)
    }

    /**
     * Public hook: heuristic check whether a Python package is already
     * present in the bundled site-packages directory.
     */
    fun isPythonPackageInstalled(name: String): Boolean {
        val appContext = context ?: return false
        return TerminalPackageManager(appContext).isPackageInstalled(name)
    }

    /** Public hook: human-readable description of the bootstrap state. */
    suspend fun describePackageManager(): String {
        val appContext = requireContext()
        return TerminalPackageManager(appContext).describe()
    }

    /**
     * Public hook: re-run the package-manager bootstrap. With
     * `force = true` we re-extract site-packages and recompile .pyc
     * files; useful when the wheels cache is updated in a new APK.
     */
    suspend fun rebakePythonOfflineEnvironment(force: Boolean): TerminalPackageManager.BootstrapReport {
        val appContext = requireContext()
        return withContext(Dispatchers.IO) {
            TerminalPackageManager(appContext).bootstrap(force)
        }
    }

    /** Public hook: list every wheel-installed Python distribution. */
    suspend fun listInstalledPythonPackages(): List<String> {
        val appContext = requireContext()
        return withContext(Dispatchers.IO) {
            TerminalPackageManager(appContext).listInstalledPackages()
        }
    }

    /** Public hook: cheap import probe that survives ABI mismatches. */
    suspend fun probePythonPackage(spec: String): String {
        val appContext = requireContext()
        return withContext(Dispatchers.IO) {
            TerminalPackageManager(appContext).probeImport(spec)
        }
    }
    fun refreshWorkspaces() {
        val appContext = context ?: return
        val manager = TerminalToolchainManager(appContext)
        _state.update { it.copy(workspaces = manager.listWorkspaces()) }
    }

    fun activeWorkspace(sessionId: String): File {
        val appContext = requireContext()
        return TerminalToolchainManager(appContext).workspaceDir(normalizeSessionId(sessionId))
    }

    suspend fun runCommand(request: TerminalExecutionRequest): TerminalExecutionResult {
        val sid = normalizeSessionId(request.sessionId)
        if (!isTerminalModeEnabled(sid)) {
            setTerminalMode(sid, true)
        }
        val startedAt = System.currentTimeMillis()
        val jobId = "terminal_${startedAt}_${UUID.randomUUID().toString().take(8)}"
        val outputBuffer = StringBuilder()
        val appContext = requireContext()
        val manager = TerminalToolchainManager(appContext)
        return runCatching {
            appendStatusLine(sid, "正在准备终端环境")
            _state.update {
                it.copy(
                    installing = true,
                    installProgress = 0.08f,
                    installMessage = "正在检查内嵌 Linux 运行时"
                )
            }
            val layoutStatus = layoutMutex.withLock {
                _state.update {
                    it.copy(
                        installing = true,
                        installProgress = 0.35f,
                        installMessage = "首次使用正在解包终端环境，请稍等"
                    )
                }
                manager.ensureLayout()
            }
            _state.update {
                it.copy(
                    ready = layoutStatus.ready,
                    toolchainRoot = layoutStatus.toolchainRoot,
                    shellPath = layoutStatus.shellPath,
                    installedExecutables = layoutStatus.installedExecutables,
                    missingExecutables = layoutStatus.missingExecutables,
                    installing = false,
                    installProgress = 0f,
                    installMessage = ""
                )
            }
            val workspace = request.cwd?.trim()?.takeIf { it.isNotBlank() }?.let(::File)
                ?: manager.workspaceDir(sid)
            workspace.mkdirs()
            val launch = withContext(Dispatchers.IO) {
                launchShellProcess(
                    manager = manager,
                    workspace = workspace,
                    command = request.command
                )
            }

            activeProcesses[sid] = launch.process
            activeSessions[sid] = jobId
            pushStateForRunning(sid, jobId, request.command, workspace.absolutePath)
            if (launch.shellPath == "/system/bin/sh") {
                appendStatusLine(sid, "内嵌 shell 被系统拦截，已临时切换到系统 shell；如 node/python/git 不可用，请安装本版 APK 后重新开启终端")
            }

            val stdout = scope.launch(Dispatchers.IO) {
                readStream(sid, "stdout", launch.process.inputStream, outputBuffer)
            }
            val stderr = scope.launch(Dispatchers.IO) {
                readStream(sid, "stderr", launch.process.errorStream, outputBuffer)
            }

            val effectiveTimeoutMs = adaptiveCommandTimeoutMs(request.command, request.timeoutMs)
            var commandTimedOut = false
            val exitCode = try {
                withTimeout(effectiveTimeoutMs) {
                    withContext(Dispatchers.IO) { launch.process.waitFor() }
                }
            } catch (_: Throwable) {
                commandTimedOut = true
                launch.process.destroy()
                runCatching { launch.process.destroyForcibly() }
                -1
            }

            stdout.join()
            stderr.join()
            activeProcesses.remove(sid)
            activeSessions.remove(sid)
            val finishedAt = System.currentTimeMillis()
            val resultText = outputBuffer.toString().trim()
            val status = runCatching { manager.inspect() }.getOrNull()
            val usedToolchain = launch.shellPath != "/system/bin/sh" && status?.missingExecutables?.isEmpty() != false
            val timeoutMessage = if (commandTimedOut) {
                "终端任务超过 ${effectiveTimeoutMs / 1000} 秒已自动停止。若是 pip/npm/uv 下载大包，请换镜像、分步安装，或提高 timeout_ms 后重试。"
            } else {
                null
            }
            if (timeoutMessage != null) appendStatusLine(sid, timeoutMessage)
            val result = TerminalExecutionResult(
                sessionId = sid,
                jobId = jobId,
                command = request.command,
                exitCode = exitCode,
                output = listOf(resultText, timeoutMessage)
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString("\n"),
                startedAt = startedAt,
                finishedAt = finishedAt,
                workingDirectory = manager.workspaceDir(sid).absolutePath,
                usedToolchain = usedToolchain,
                // If we had to fall back to /system/bin/sh, the command still ran,
                // but developer toolchain access may be partial.
                error = if (exitCode == 0) null else "命令退出码 $exitCode"
            )
            appendResult(result)
            runCatching { refreshToolchainStatus() }
            refreshWorkspaces()
            _state.update { it.copy(installing = false, installProgress = 0f, installMessage = "") }
            result
        }.getOrElse { error ->
            activeProcesses.remove(sid)?.let { runCatching { it.destroy() } }
            activeSessions.remove(sid)
            val message = "终端执行失败：${error.message ?: error.javaClass.simpleName}"
            appendStatusLine(sid, message)
            val result = TerminalExecutionResult(
                sessionId = sid,
                jobId = jobId,
                command = request.command,
                exitCode = -1,
                output = message,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                workingDirectory = context?.let { TerminalToolchainManager(it).workspaceDir(sid).absolutePath }.orEmpty(),
                usedToolchain = false,
                error = message
            )
            appendResult(result)
            _state.update { it.copy(installing = false, installProgress = 0f, installMessage = "") }
            result
        }
    }

    fun cancelActive(sessionId: String): Boolean {
        val sid = normalizeSessionId(sessionId)
        val process = activeProcesses.remove(sid) ?: return false
        activeSessions.remove(sid)
        runCatching { process.destroy() }
        appendStatusLine(sid, "已取消当前终端任务")
        _state.update {
            it.copy(
                activeJobId = "",
                activeCommand = "",
                activeWorkspace = "",
                installing = false,
                installMessage = ""
            )
        }
        installingCorePackages = false
        return true
    }

    fun installDeveloperTools(sessionId: String) {
        val sid = normalizeSessionId(sessionId)
        if (!isTerminalModeEnabled(sid)) {
            setTerminalMode(sid, true)
        }
        scope.launch { ensureDeveloperToolsForSession(sid, force = true) }
    }

    fun forceClose(sessionId: String) {
        val sid = normalizeSessionId(sessionId)
        activeProcesses.remove(sid)?.let { process -> runCatching { process.destroy() } }
        activeSessions.remove(sid)
        installingCorePackages = false
        synchronized(outputMutex) {
            sessionOutputBuffers.remove(sid)
        }
        _state.update { current ->
            current.copy(
                terminalModeSessions = current.terminalModeSessions - sid,
                activeSessionId = "",
                activeCommand = "",
                activeWorkspace = "",
                activeJobId = "",
                installing = false,
                installProgress = 0f,
                installMessage = "",
                recentOutput = emptyList(),
                lastError = ""
            )
        }
    }

    fun clearSessionOutput(sessionId: String) {
        val sid = normalizeSessionId(sessionId)
        synchronized(outputMutex) {
            sessionOutputBuffers.remove(sid)
        }
        _state.update { state ->
            if (state.activeSessionId == sid) {
                state.copy(recentOutput = emptyList())
            } else state
        }
    }

    fun destroy() {
        activeProcesses.values.forEach { process ->
            runCatching { process.destroy() }
        }
        activeProcesses.clear()
        activeSessions.clear()
        scope.cancel()
    }

    private suspend fun ensureDeveloperToolsForSession(sessionId: String, force: Boolean = false) {
        if (installingCorePackages) return
        val appContext = context ?: return
        val manager = TerminalToolchainManager(appContext)
        val status = layoutMutex.withLock { manager.ensureLayout() }
        val missingDeveloperTools = status.missingExecutables.intersect(developerToolNames)
        if (missingDeveloperTools.isEmpty() && !force) return
        if (missingDeveloperTools.isEmpty()) {
            appendStatusLine(sessionId, "开发工具已经就绪")
            return
        }

        installingCorePackages = true
        _state.update {
            it.copy(
                installing = true,
                installProgress = 0.1f,
                installMessage = "正在准备内嵌开发工具：${missingDeveloperTools.joinToString("、")}"
            )
        }
        appendStatusLine(sessionId, "正在安装 APK 内置离线 Linux 开发环境，首次解包需要一点时间")

        val workspace = manager.workspaceDir(sessionId)
        val startedAt = System.currentTimeMillis()
        val jobId = "terminal_install_$startedAt"
        val outputBuffer = StringBuilder()
        val launch = runCatching {
            withContext(Dispatchers.IO) {
                launchShellProcess(
                    manager = manager,
                    workspace = workspace,
                    command = manager.corePackageInstallCommand()
                )
            }
        }.getOrElse { error ->
            installingCorePackages = false
            appendStatusLine(sessionId, "开发工具自动安装启动失败：${error.message ?: error.javaClass.simpleName}")
            _state.update { current ->
                current.copy(
                    installing = false,
                    installProgress = 0f,
                    installMessage = "",
                    lastError = "开发工具自动安装启动失败"
                )
            }
            return
        }
        val process = launch.process

        activeProcesses[sessionId] = process
        activeSessions[sessionId] = jobId
        pushStateForRunning(sessionId, jobId, "初始化内嵌开发工具", workspace.absolutePath)

        val stdout = scope.launch(Dispatchers.IO) { readStream(sessionId, "stdout", process.inputStream, outputBuffer) }
        val stderr = scope.launch(Dispatchers.IO) { readStream(sessionId, "stderr", process.errorStream, outputBuffer) }
        val exitCode = try {
            withTimeout(600_000L) {
                withContext(Dispatchers.IO) { process.waitFor() }
            }
        } catch (_: Throwable) {
            process.destroy()
            -1
        }
        stdout.join()
        stderr.join()
        activeProcesses.remove(sessionId)
        activeSessions.remove(sessionId)
        manager.finalizePackageInstall()

        val nextStatus = refreshToolchainStatus()
        val stillMissing = nextStatus.missingExecutables.intersect(developerToolNames)
        appendStatusLine(
            sessionId,
            if (exitCode == 0 && stillMissing.isEmpty()) {
                "内嵌开发工具已就绪：node、npm、python、pip、git、ssh、uv"
            } else {
                "开发工具安装未完全完成，缺少：${stillMissing.joinToString("、").ifBlank { "无" }}；退出码：$exitCode"
            }
        )
        _state.update {
            it.copy(
                installing = false,
                installProgress = if (stillMissing.isEmpty()) 1f else 0f,
                installMessage = if (stillMissing.isEmpty()) "开发工具已就绪" else "开发工具未完全安装",
                activeCommand = "",
                activeJobId = "",
                lastExitCode = exitCode,
                lastError = if (exitCode == 0 && stillMissing.isEmpty()) "" else "开发工具未完全安装"
            )
        }
        installingCorePackages = false
        refreshWorkspaces()
    }

    private suspend fun readStream(
        sessionId: String,
        streamName: String,
        inputStream: java.io.InputStream,
        buffer: StringBuilder
    ) {
        runCatching {
            BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                lines.forEach { line ->
                    val text = line.trimEnd()
                    if (text.isBlank()) return@forEach
                    synchronized(buffer) {
                        buffer.appendLine("[$streamName] $text")
                    }
                    appendOutputLine(sessionId, streamName, text)
                }
            }
        }
    }

    private fun appendOutputLine(sessionId: String, streamName: String, text: String) {
        val line = TerminalOutputLine(
            sessionId = sessionId,
            stream = streamName,
            text = text,
            createdAt = System.currentTimeMillis()
        )
        synchronized(outputMutex) {
            val deque = sessionOutputBuffers.getOrPut(sessionId) { ArrayDeque() }
            deque.addLast(line)
            while (deque.size > 120) {
                deque.removeFirst()
            }
            val snapshot = deque.toList()
            _state.update { current ->
                if (current.activeSessionId == sessionId || current.activeSessionId.isBlank()) {
                    current.copy(recentOutput = snapshot)
                } else current
            }
        }
    }

    private fun appendStatusLine(sessionId: String, text: String) {
        appendOutputLine(sessionId, "status", text)
        _state.update { current ->
            current.copy(lastError = if (text.contains("错误") || text.contains("失败")) text else current.lastError)
        }
    }

    private fun appendResult(result: TerminalExecutionResult) {
        _state.update { current ->
            current.copy(
                activeSessionId = result.sessionId,
                activeCommand = "",
                activeWorkspace = result.workingDirectory,
                activeJobId = "",
                lastExitCode = result.exitCode,
                lastError = result.error.orEmpty(),
                lastResult = result,
                recentOutput = sessionOutputBuffers[result.sessionId]?.toList().orEmpty()
            )
        }
    }

    private fun pushStateForRunning(sessionId: String, jobId: String, command: String, workspace: String) {
        _state.update { current ->
            current.copy(
                activeSessionId = sessionId,
                activeCommand = command,
                activeWorkspace = workspace,
                activeJobId = jobId,
                lastExitCode = null,
                lastError = "",
                recentOutput = sessionOutputBuffers[sessionId]?.toList().orEmpty()
            )
        }
    }

    private fun requireContext(): Context {
        return context ?: throw IllegalStateException("TerminalController is not initialized")
    }

    private fun normalizeSessionId(sessionId: String): String {
        return sessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
    }

    private fun bootstrapMissingFallback(): Set<String> {
        return setOf("sh", "bash", "pkg", "apt", "tar")
    }

    private data class ShellLaunch(
        val process: Process,
        val shellPath: String
    )

    private fun launchShellProcess(
        manager: TerminalToolchainManager,
        workspace: File,
        command: String
    ): ShellLaunch {
        val candidates = listOfNotNull(
            manager.resolveExecutable("sh"),
            manager.resolveExecutable("bash"),
            "/system/bin/sh".takeIf { File(it).exists() }
        ).distinct()
        var lastError: IOException? = null
        for (shell in candidates) {
            try {
                val process = ProcessBuilder(shell, "-lc", command).apply {
                    environment().putAll(manager.buildEnvironment(workspace))
                    directory(workspace)
                    redirectErrorStream(false)
                }.start()
                return ShellLaunch(process, shell)
            } catch (error: IOException) {
                lastError = error
                val message = error.message.orEmpty()
                val permissionDenied = message.contains("Permission denied", ignoreCase = true) ||
                    message.contains("error=13", ignoreCase = true)
                if (!permissionDenied && shell == candidates.last()) {
                    throw error
                }
            }
        }
        throw lastError ?: IOException("无法启动任何可用的 shell")
    }

    private fun adaptiveCommandTimeoutMs(command: String, requestedTimeoutMs: Long): Long {
        val normalized = command.lowercase()
        val looksLongRunning = LONG_RUNNING_COMMAND_HINTS.any { normalized.contains(it) }
        val baseline = when {
            requestedTimeoutMs > 0L -> requestedTimeoutMs
            looksLongRunning -> 600_000L
            else -> 180_000L
        }
        val minimum = if (looksLongRunning) 300_000L else 30_000L
        return baseline.coerceAtLeast(minimum).coerceAtMost(600_000L)
    }

    private val LONG_RUNNING_COMMAND_HINTS = listOf(
        "pip install",
        "python -m pip",
        "uv pip install",
        "npm install",
        "npm i",
        "pnpm install",
        "yarn install",
        "gradlew",
        "assemble",
        "test",
        "git clone",
        "curl ",
        "wget "
    )
}
