package com.lgclaw.terminal

data class TerminalOutputLine(
    val sessionId: String,
    val stream: String,
    val text: String,
    val createdAt: Long
)

data class TerminalWorkspaceInfo(
    val sessionId: String,
    val path: String,
    val lastUsedAt: Long
)

data class TerminalExecutionRequest(
    val sessionId: String,
    val command: String,
    val timeoutMs: Long = 60_000L,
    val cwd: String? = null,
    val label: String = ""
)

data class TerminalExecutionResult(
    val sessionId: String,
    val jobId: String,
    val command: String,
    val exitCode: Int,
    val output: String,
    val startedAt: Long,
    val finishedAt: Long,
    val workingDirectory: String,
    val usedToolchain: Boolean,
    val error: String? = null
)

data class TerminalToolchainStatus(
    val ready: Boolean,
    val shellPath: String,
    val installedExecutables: Set<String>,
    val missingExecutables: Set<String>,
    val toolchainRoot: String,
    val lastError: String = "",
    val missingLibraries: Set<String> = emptySet()
)

data class TerminalRuntimeState(
    val ready: Boolean = false,
    val terminalModeSessions: Set<String> = emptySet(),
    val overlayPermissionGranted: Boolean = false,
    val installing: Boolean = false,
    val installProgress: Float = 0f,
    val installMessage: String = "",
    val toolchainRoot: String = "",
    val shellPath: String = "",
    val installedExecutables: Set<String> = emptySet(),
    val missingExecutables: Set<String> = emptySet(),
    val missingLibraries: Set<String> = emptySet(),
    val workspaces: List<TerminalWorkspaceInfo> = emptyList(),
    val activeSessionId: String = "",
    val activeCommand: String = "",
    val activeWorkspace: String = "",
    val activeJobId: String = "",
    val lastExitCode: Int? = null,
    val lastError: String = "",
    val recentOutput: List<TerminalOutputLine> = emptyList(),
    val lastResult: TerminalExecutionResult? = null
)
