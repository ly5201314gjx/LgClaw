package com.lgclaw.tools

import com.lgclaw.terminal.TerminalController
import com.lgclaw.terminal.TerminalExecutionRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

fun createTerminalToolSet(
    terminalController: TerminalController,
    currentSessionIdProvider: () -> String
): List<Tool> {
    return listOf(
        TerminalExecTool(terminalController, currentSessionIdProvider),
        TerminalCancelTool(terminalController, currentSessionIdProvider),
        TerminalStatusTool(terminalController),
        TerminalWorkspaceListTool(terminalController),
        TerminalPythonExecTool(terminalController, currentSessionIdProvider),
        TerminalPythonInstallTool(terminalController, currentSessionIdProvider),
        TerminalPythonCheckTool(terminalController, currentSessionIdProvider),
        TerminalPythonListTool(terminalController, currentSessionIdProvider)
    )
}

private class TerminalExecTool(
    private val terminalController: TerminalController,
    private val currentSessionIdProvider: () -> String
) : Tool, TimedTool {
    override val name: String = "terminal_exec"
    override val timeoutMs: Long = 660_000L
    override val description: String =
        "Run a shell command, script, or test in the current session's independent workspace. " +
        "Suitable for node/npm, python/pip/uv, git, ssh, builds, tests, file operations, and code review. " +
        "The terminal is a back-end worker; user chat input still belongs to the Agent, not the terminal. " +
        "When you hit missing pip/npm/uv dependencies, you can run `python -m pip install` or " +
        "`uv pip install` or `npm install` automatically, then retry; after execution, you must " +
        "read the exit code and output to continue."

    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", Json.parseToJsonElement("""["command"]"""))
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "command": {"type": "string", "description": "Shell command, script, or test command to execute."},
                  "timeout_ms": {"type": "integer", "minimum": 1000, "maximum": 600000, "description": "Optional timeout. Downloads, installs, builds, and tests recommend 300000-600000."},
                  "cwd": {"type": "string", "description": "Optional working directory, defaults to the current session workspace."},
                  "session_id": {"type": "string", "description": "Optional session ID, defaults to the current session."}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val args = runCatching { Json.decodeFromString<ExecArgs>(argumentsJson) }.getOrElse {
            return ToolResult(
                toolCallId = "",
                content = "terminal exec failed: invalid arguments: ${it.message ?: it.javaClass.simpleName}",
                isError = true
            )
        }
        val command = args.command.trim()
        if (command.isBlank()) {
            return ToolResult(toolCallId = "", content = "terminal exec failed: command cannot be empty", isError = true)
        }
        val sessionId = args.sessionId?.trim().orEmpty().ifBlank { currentSessionIdProvider() }
        val timeoutMs = adaptiveTerminalTimeoutMs(command, args.timeoutMs)
        val result = terminalController.runCommand(
            TerminalExecutionRequest(
                sessionId = sessionId,
                command = command,
                timeoutMs = timeoutMs,
                cwd = args.cwd?.trim()?.ifBlank { null },
                label = "agent"
            )
        )
        return ToolResult(
            toolCallId = "",
            content = buildString {
                appendLine("session: ${result.sessionId}")
                appendLine("workspace: ${result.workingDirectory}")
                appendLine("exit code: ${result.exitCode}")
                if (result.output.isNotBlank()) {
                    appendLine("output:")
                    appendLine(result.output)
                }
                result.error?.let { appendLine("note: $it") }
            }.trim(),
            isError = result.exitCode != 0,
            metadata = buildJsonObject {
                put("job_id", result.jobId)
                put("exit_code", result.exitCode)
                put("timeout_ms", timeoutMs)
                put("working_directory", result.workingDirectory)
                put("session_id", result.sessionId)
            }
        )
    }

    private fun adaptiveTerminalTimeoutMs(command: String, requested: Int?): Long {
        val explicit = requested?.toLong()?.takeIf { it > 0 }
        val normalized = command.lowercase()
        val looksLongRunning = LONG_RUNNING_COMMAND_HINTS.any { normalized.contains(it) }
        val baseline = when {
            explicit != null -> explicit
            looksLongRunning -> 600_000L
            else -> 180_000L
        }
        return baseline.coerceIn(1_000L, 600_000L)
    }

    @Serializable
    private data class ExecArgs(
        val command: String,
        @SerialName("timeout_ms")
        val timeoutMs: Int? = null,
        val cwd: String? = null,
        @SerialName("session_id")
        val sessionId: String? = null
    )

    companion object {
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
}
private class TerminalCancelTool(
    private val terminalController: TerminalController,
    private val currentSessionIdProvider: () -> String
) : Tool {
    override val name: String = "terminal_cancel"
    override val description: String = "Cancel the terminal task currently running in the current session."
    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("properties", Json.parseToJsonElement("""{"session_id":{"type":"string"}}"""))
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val args = runCatching { Json.decodeFromString<CancelArgs>(argumentsJson) }.getOrElse {
            return ToolResult(toolCallId = "", content = "terminal cancel failed: invalid arguments", isError = true)
        }
        val sessionId = args.sessionId?.trim().orEmpty().ifBlank { currentSessionIdProvider() }
        val cancelled = terminalController.cancelActive(sessionId)
        return ToolResult(
            toolCallId = "",
            content = if (cancelled) {
                "Cancelled terminal task for session $sessionId"
            } else {
                "No terminal task is currently running in the current session."
            },
            isError = !cancelled
        )
    }

    @Serializable
    private data class CancelArgs(
        @SerialName("session_id")
        val sessionId: String? = null
    )
}
private class TerminalStatusTool(
    private val terminalController: TerminalController
) : Tool {
    override val name: String = "terminal_status"
    override val description: String = "View terminal mode, toolchain availability, current command, recent output, missing components, and overlay permission."
    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val state = terminalController.state.value
        return ToolResult(
            toolCallId = "",
            content = buildString {
                appendLine("terminal mode sessions: ${state.terminalModeSessions.joinToString(", ").ifBlank { "none" }}")
                appendLine("toolchain root: ${state.toolchainRoot.ifBlank { "not initialized" }}")
                appendLine("shell: ${state.shellPath.ifBlank { "not found" }}")
                appendLine("installed: ${state.installedExecutables.joinToString(", ").ifBlank { "none" }}")
                appendLine("missing: ${state.missingExecutables.joinToString(", ").ifBlank { "none" }}")
                appendLine("overlay permission: ${if (state.overlayPermissionGranted) "granted" else "not granted"}")
                appendLine("recent output:")
                state.recentOutput.takeLast(12).forEach { line ->
                    appendLine("[${line.stream}] ${line.text}")
                }
            }.trim(),
            isError = false,
            metadata = buildJsonObject {
                put("ready", state.ready)
                put("installing", state.installing)
                put("install_message", state.installMessage)
                put("overlay_permission", state.overlayPermissionGranted)
                put("active_session_id", state.activeSessionId)
                put("active_command", state.activeCommand)
                put("active_workspace", state.activeWorkspace)
            }
        )
    }
}
private class TerminalWorkspaceListTool(
    private val terminalController: TerminalController
) : Tool {
    override val name: String = "terminal_workspace_list"
    override val description: String = "List the current terminal session workspaces."
    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val workspaces = terminalController.state.value.workspaces
        return ToolResult(
            toolCallId = "",
            content = buildString {
                appendLine("workspaces:")
                if (workspaces.isEmpty()) {
                    appendLine("(no workspaces yet)")
                } else {
                    workspaces.forEachIndexed { index, workspace ->
                        appendLine("${index + 1}. ${workspace.sessionId} -> ${workspace.path}")
                    }
                }
            }.trim(),
            isError = false
        )
    }
}
private class TerminalPythonExecTool(
    private val terminalController: TerminalController,
    private val currentSessionIdProvider: () -> String
) : Tool, TimedTool {
    override val name: String = "terminal_python_exec"
    override val timeoutMs: Long = 300_000L
    override val description: String =
        "Run an inline Python script in the embedded toolchain. The bundled site-packages " +
        "already covers requests, matplotlib, pyecharts, numpy, pandas, scipy, lxml, " +
        "cryptography, pillow, sympy and more, so no pip download is needed for those."

    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", Json.parseToJsonElement("""["script"]"""))
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "script": {"type": "string", "description": "Inline Python source."},
                  "args": {"type": "array", "items": {"type": "string"}, "description": "Optional argv."},
                  "timeout_ms": {"type": "integer", "minimum": 1000, "maximum": 600000},
                  "session_id": {"type": "string"}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val args = runCatching { Json.decodeFromString<PythonExecArgs>(argumentsJson) }.getOrElse {
            return ToolResult(toolCallId = "", content = "Python execution failed: ${it.message}", isError = true)
        }
        val sessionId = args.sessionId?.trim().orEmpty().ifBlank { currentSessionIdProvider() }
        val script = args.script
        if (script.isBlank()) {
            return ToolResult(toolCallId = "", content = "Python execution failed: script is empty", isError = true)
        }
        val shellCmd = buildString {
            append("python3 -")
            args.args.forEach { append(' ').append(shellQuote(it)) }
        }
        val fullCommand = shellCmd + "\n" + script
        val result = terminalController.runCommand(
            TerminalExecutionRequest(
                sessionId = sessionId,
                command = fullCommand,
                timeoutMs = args.timeoutMs?.toLong() ?: 180_000L,
                label = "python-exec"
            )
        )
        return ToolResult(
            toolCallId = "",
            content = buildString {
                appendLine("Exit code: ${result.exitCode}")
                if (result.output.isNotBlank()) appendLine(result.output)
                if (result.error != null) appendLine("Error: ${result.error}")
            }.trim(),
            isError = result.exitCode != 0,
            metadata = buildJsonObject {
                put("engine", "python3")
                put("exit_code", result.exitCode)
                put("elapsed_ms", result.finishedAt - result.startedAt)
                put("session_id", sessionId)
            }
        )
    }

    private fun shellQuote(text: String): String = "'" + text.replace("'", "'\''") + "'"

    @Serializable
    private data class PythonExecArgs(
        val script: String,
        val args: List<String> = emptyList(),
        @SerialName("timeout_ms")
        val timeoutMs: Int? = null,
        @SerialName("session_id")
        val sessionId: String? = null
    )
}

/**
 * `terminal_python_install` is the structured counterpart to
 * `terminal_exec "pip install ..."`. It uses uv by default (much
 * faster than pip) and falls back to pip if needed.
 */
private class TerminalPythonInstallTool(
    private val terminalController: TerminalController,
    private val currentSessionIdProvider: () -> String
) : Tool, TimedTool {
    override val name: String = "terminal_python_install"
    override val timeoutMs: Long = 600_000L
    override val description: String =
        "Install a Python package into the embedded toolchain. Uses uv by default; falls " +
        "back to pip. The wheels cache is searched first; PyPI is contacted only as a last " +
        "resort. To see what is already bundled, call terminal_python_list first."

    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", Json.parseToJsonElement("""["spec"]"""))
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "spec": {"type": "string", "description": "Package spec, e.g. 'requests', 'numpy>=2.0'."},
                  "session_id": {"type": "string"}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val args = runCatching { Json.decodeFromString<PythonInstallArgs>(argumentsJson) }.getOrElse {
            return ToolResult(toolCallId = "", content = "Python install failed: ${it.message}", isError = true)
        }
        val spec = args.spec.trim()
        if (spec.isBlank()) {
            return ToolResult(toolCallId = "", content = "Python install failed: spec is empty", isError = true)
        }
        val install = terminalController.installPythonPackage(spec)
        val body = buildString {
            appendLine("Engine: ${install.engine}")
            appendLine("Success: ${install.success}")
            if (install.output.isNotBlank()) appendLine(install.output)
            if (install.reason.isNotBlank()) appendLine("Reason: ${install.reason}")
        }.trim()
        return ToolResult(
            toolCallId = "",
            content = body,
            isError = !install.success,
            metadata = buildJsonObject {
                put("engine", install.engine)
                put("success", install.success)
            }
        )
    }

    @Serializable
    private data class PythonInstallArgs(
        val spec: String,
        @SerialName("session_id")
        val sessionId: String? = null
    )
}

/**
 * `terminal_python_check` answers "is this Python package importable
 * right now?" by running `import <name>` inside the embedded
 * interpreter. The cheapest way to debug "ModuleNotFoundError" without
 * trial-and-error pip installs.
 */
private class TerminalPythonCheckTool(
    private val terminalController: TerminalController,
    private val currentSessionIdProvider: () -> String
) : Tool, TimedTool {
    override val name: String = "terminal_python_check"
    override val timeoutMs: Long = 60_000L
    override val description: String =
        "Check whether a Python package is installed and importable in the embedded toolchain. " +
        "Returns the package's __version__ if importable, or 'missing'/'import-failed' otherwise."

    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", Json.parseToJsonElement("""["name"]"""))
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "name": {"type": "string", "description": "Importable module name."},
                  "session_id": {"type": "string"}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val args = runCatching { Json.decodeFromString<PythonCheckArgs>(argumentsJson) }.getOrElse {
            return ToolResult(toolCallId = "", content = "Python check failed: ${it.message}", isError = true)
        }
        val name = args.name.trim()
        if (name.isBlank()) {
            return ToolResult(toolCallId = "", content = "Python check failed: name is empty", isError = true)
        }
        val probe = terminalController.probePythonPackage(name)
        val ok = probe != "missing" && probe != "import-failed"
        return ToolResult(
            toolCallId = "",
            content = "$name: $probe",
            isError = !ok,
            metadata = buildJsonObject {
                put("module", name)
                put("status", probe)
                put("installed", ok)
            }
        )
    }

    @Serializable
    private data class PythonCheckArgs(
        val name: String,
        @SerialName("session_id")
        val sessionId: String? = null
    )
}

/**
 * `terminal_python_list` enumerates the Python distributions that are
 * already pre-installed in the bundled site-packages directory.
 */
private class TerminalPythonListTool(
    private val terminalController: TerminalController,
    private val currentSessionIdProvider: () -> String
) : Tool {
    override val name: String = "terminal_python_list"
    override val description: String =
        "List every Python distribution that is pre-installed in the embedded toolchain."
    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val packages = terminalController.listInstalledPythonPackages()
        return ToolResult(
            toolCallId = "",
            content = buildString {
                if (packages.isEmpty()) {
                    appendLine("(no Python packages installed yet; run terminal_python_install)")
                } else {
                    appendLine("Pre-installed Python packages (${packages.size}):")
                    packages.forEach { appendLine("  - $it") }
                }
            }.trim(),
            isError = false,
            metadata = buildJsonObject { put("count", packages.size) }
        )
    }
}
