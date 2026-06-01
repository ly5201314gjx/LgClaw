package com.lgclaw.terminal

import android.content.Context
import android.provider.Settings
import com.lgclaw.config.AppSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
            refreshToolchainStatus()
            refreshOverlayPermission(appContext)
            refreshWorkspaces()
        }
    }

    fun refreshOverlayPermission(context: Context) {
        val granted = Settings.canDrawOverlays(context)
        _state.update { it.copy(overlayPermissionGranted = granted) }
    }

    suspend fun refreshToolchainStatus(): TerminalToolchainStatus = withContext(Dispatchers.IO) {
        val appContext = requireContext()
        val manager = TerminalToolchainManager(appContext)
        val status = manager.ensureLayout()
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
        val appContext = requireContext()
        val manager = TerminalToolchainManager(appContext)
        manager.ensureLayout()
        val shell = manager.resolveExecutable("sh")
            ?: manager.resolveExecutable("bash")
            ?: "/system/bin/sh"
        val workspace = request.cwd?.trim()?.takeIf { it.isNotBlank() }?.let(::File)
            ?: manager.workspaceDir(sid)
        workspace.mkdirs()
        val startedAt = System.currentTimeMillis()
        val jobId = "terminal_${startedAt}_${UUID.randomUUID().toString().take(8)}"
        val outputBuffer = StringBuilder()
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(shell, "-lc", request.command).apply {
                environment().putAll(manager.buildEnvironment(workspace))
                directory(workspace)
                redirectErrorStream(false)
            }.start()
        }

        activeProcesses[sid] = process
        activeSessions[sid] = jobId
        pushStateForRunning(sid, jobId, request.command, workspace.absolutePath)

        val stdout = scope.launch(Dispatchers.IO) {
            readStream(sid, "stdout", process.inputStream, outputBuffer)
        }
        val stderr = scope.launch(Dispatchers.IO) {
            readStream(sid, "stderr", process.errorStream, outputBuffer)
        }

        val exitCode = try {
            withTimeout(request.timeoutMs.coerceAtLeast(1_000L)) {
                withContext(Dispatchers.IO) { process.waitFor() }
            }
        } catch (_: Throwable) {
            process.destroy()
            -1
        }

        stdout.join()
        stderr.join()
        activeProcesses.remove(sid)
        activeSessions.remove(sid)
        val finishedAt = System.currentTimeMillis()
        val resultText = outputBuffer.toString().trim()
        val status = manager.inspect()
        val result = TerminalExecutionResult(
            sessionId = sid,
            jobId = jobId,
            command = request.command,
            exitCode = exitCode,
            output = resultText,
            startedAt = startedAt,
            finishedAt = finishedAt,
            workingDirectory = workspace.absolutePath,
            usedToolchain = shell != "/system/bin/sh" || status.missingExecutables.isEmpty(),
            error = if (exitCode == 0) null else "命令退出码 $exitCode"
        )
        appendResult(result)
        refreshToolchainStatus()
        refreshWorkspaces()
        return result
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
        val status = manager.ensureLayout()
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
        val shell = status.shellPath.ifBlank { manager.resolveExecutable("sh") ?: "/system/bin/sh" }
        val startedAt = System.currentTimeMillis()
        val jobId = "terminal_install_$startedAt"
        val outputBuffer = StringBuilder()
        val process = runCatching {
            withContext(Dispatchers.IO) {
                ProcessBuilder(shell, "-lc", manager.corePackageInstallCommand()).apply {
                    environment().putAll(manager.buildEnvironment(workspace))
                    directory(workspace)
                    redirectErrorStream(false)
                }.start()
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
}
