package com.lgclaw.tools

import com.lgclaw.terminal.TerminalController
import com.lgclaw.terminal.TerminalExecutionRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun createTerminalToolSet(
    terminalController: TerminalController,
    currentSessionIdProvider: () -> String
): List<Tool> {
    return listOf(
        TerminalExecTool(terminalController, currentSessionIdProvider),
        TerminalCancelTool(terminalController, currentSessionIdProvider),
        TerminalStatusTool(terminalController),
        TerminalWorkspaceListTool(terminalController)
    )
}

private class TerminalExecTool(
    private val terminalController: TerminalController,
    private val currentSessionIdProvider: () -> String
) : Tool {
    override val name: String = "terminal_exec"
    override val description: String =
        "在当前会话的独立工作区静默执行命令或代码。适合运行 node/npm、python/pip/uv、git、ssh、构建、测试、文件处理和代码检查。把终端当作后台工作器：用户聊天输入仍然属于 Agent，不属于终端。遇到缺少 pip/npm/uv 依赖时，可以先用 python -m pip install、uv pip install 或 npm install 自动安装，再重试一次；执行后必须读取退出码和输出再继续。"

    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", Json.parseToJsonElement("""["command"]"""))
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "command": {"type": "string", "description": "要执行的 shell 命令、脚本或测试命令"},
                  "timeout_ms": {"type": "integer", "minimum": 1000, "maximum": 600000},
                  "cwd": {"type": "string", "description": "可选工作目录，默认当前会话工作区"},
                  "session_id": {"type": "string", "description": "可选会话 ID，默认当前会话"}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val args = runCatching { Json.decodeFromString<ExecArgs>(argumentsJson) }.getOrElse {
            return ToolResult(
                toolCallId = "",
                content = "终端执行失败：参数格式不正确，${it.message ?: it.javaClass.simpleName}",
                isError = true
            )
        }
        val command = args.command.trim()
        if (command.isBlank()) {
            return ToolResult(toolCallId = "", content = "终端执行失败：命令不能为空", isError = true)
        }
        val sessionId = args.sessionId?.trim().orEmpty().ifBlank { currentSessionIdProvider() }
        val result = terminalController.runCommand(
            TerminalExecutionRequest(
                sessionId = sessionId,
                command = command,
                timeoutMs = args.timeoutMs?.toLong()?.coerceIn(1_000L, 600_000L) ?: 120_000L,
                cwd = args.cwd?.trim()?.ifBlank { null },
                label = "agent"
            )
        )
        return ToolResult(
            toolCallId = "",
            content = buildString {
                appendLine("会话：${result.sessionId}")
                appendLine("工作区：${result.workingDirectory}")
                appendLine("退出码：${result.exitCode}")
                if (result.output.isNotBlank()) {
                    appendLine("输出：")
                    appendLine(result.output)
                }
                result.error?.let { appendLine("提示：$it") }
            }.trim(),
            isError = result.exitCode != 0,
            metadata = buildJsonObject {
                put("job_id", result.jobId)
                put("exit_code", result.exitCode)
                put("working_directory", result.workingDirectory)
                put("session_id", result.sessionId)
            }
        )
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
}

private class TerminalCancelTool(
    private val terminalController: TerminalController,
    private val currentSessionIdProvider: () -> String
) : Tool {
    override val name: String = "terminal_cancel"
    override val description: String = "取消当前会话正在运行的终端任务。"
    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("properties", Json.parseToJsonElement("""{"session_id":{"type":"string"}}"""))
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val args = runCatching { Json.decodeFromString<CancelArgs>(argumentsJson) }.getOrElse {
            return ToolResult(toolCallId = "", content = "终端取消失败：参数格式不正确", isError = true)
        }
        val sessionId = args.sessionId?.trim().orEmpty().ifBlank { currentSessionIdProvider() }
        val cancelled = terminalController.cancelActive(sessionId)
        return ToolResult(
            toolCallId = "",
            content = if (cancelled) {
                "已取消会话 $sessionId 的终端任务"
            } else {
                "当前会话没有正在运行的终端任务"
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
    override val description: String = "查看终端模式、工具链可用性、当前命令、最近输出、缺失组件和悬浮窗权限。"
    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val state = terminalController.state.value
        return ToolResult(
            toolCallId = "",
            content = buildString {
                appendLine("终端模式会话：${state.terminalModeSessions.joinToString(", ").ifBlank { "无" }}")
                appendLine("工具链目录：${state.toolchainRoot.ifBlank { "未初始化" }}")
                appendLine("Shell：${state.shellPath.ifBlank { "未找到" }}")
                appendLine("已安装：${state.installedExecutables.joinToString(", ").ifBlank { "无" }}")
                appendLine("缺少：${state.missingExecutables.joinToString(", ").ifBlank { "无" }}")
                appendLine("悬浮窗权限：${if (state.overlayPermissionGranted) "已授权" else "未授权"}")
                appendLine("最近输出：")
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
    override val description: String = "列出当前终端会话工作区。"
    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val workspaces = terminalController.state.value.workspaces
        return ToolResult(
            toolCallId = "",
            content = buildString {
                appendLine("工作区列表：")
                if (workspaces.isEmpty()) {
                    appendLine("暂无工作区")
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
