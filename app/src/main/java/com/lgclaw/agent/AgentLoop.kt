package com.lgclaw.agent

import android.util.Log
import com.lgclaw.agents.AgentRepository
import com.lgclaw.config.AppLimits
import com.lgclaw.memory.MemoryStore
import com.lgclaw.memory.CompressedMemoryStore
import com.lgclaw.providers.ChatMessage
import com.lgclaw.providers.LlmResponse
import com.lgclaw.providers.LlmProvider
import com.lgclaw.providers.LlmUsage
import com.lgclaw.providers.ToolCall
import com.lgclaw.providers.ToolSpec
import com.lgclaw.skills.SkillsLoader
import com.lgclaw.storage.MessageRepository
import com.lgclaw.templates.TemplateStore
import com.lgclaw.tools.ToolRegistry
import com.lgclaw.tools.ToolResult
import com.lgclaw.trace.TraceNoteExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.InterruptedIOException

class AgentLoop(
    private val repository: MessageRepository,
    private val contextBuilder: ContextBuilder,
    private val toolCallParser: ToolCallParser,
    private val toolRegistry: ToolRegistry,
    private val refreshRuntimeTools: (() -> Unit)? = null,
    private val llmProviderFactory: () -> LlmProvider,
    private val memoryStore: MemoryStore,
    private val compressedMemoryStore: CompressedMemoryStore,
    private val memoryConsolidator: MemoryConsolidator,
    private val skillsLoader: SkillsLoader,
    private val templateStore: TemplateStore?,
    private val agentRepository: AgentRepository? = null,
    private val processLogger: ((String) -> Unit)? = null,
    private val usageReporter: ((LlmUsage) -> Unit)? = null,
    private val traceReporter: ((String, String, String) -> Unit)? = null,
    private val maxRoundsProvider: () -> Int = { AppLimits.DEFAULT_MAX_TOOL_ROUNDS },
    private val toolResultMaxCharsProvider: () -> Int = { AppLimits.DEFAULT_TOOL_RESULT_MAX_CHARS },
    private val memoryWindowProvider: () -> Int = { AppLimits.DEFAULT_MEMORY_CONSOLIDATION_WINDOW },
    private val compressionThresholdKProvider: () -> Int = { AppLimits.DEFAULT_COMPRESSION_THRESHOLD_K },
    private val maxContextMessagesProvider: () -> Int = { AppLimits.DEFAULT_CONTEXT_MESSAGES }
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val consolidationLocks = mutableMapOf<String, Mutex>()
    private val consolidationJobs = mutableMapOf<String, Job>()
    private val consolidationGuard = Any()

    suspend fun run(
        sessionId: String,
        newUserText: String,
        blockedTools: Set<String> = emptySet(),
        onUserMessageAppended: (suspend (Long) -> Unit)? = null,
        inputRole: String = "user",
        appendInputMessage: Boolean = true
    ) = withContext(sessionContext(sessionId)) {
        var cancelled = false
        try {
            val normalizedRole = inputRole.trim().ifBlank { "user" }
            reportTrace(sessionId, "准备上下文", "正在整理历史消息、技能、工具和记忆。")
            if (appendInputMessage) {
                val appendedUserMessageId = repository.appendMessage(
                    sessionId = sessionId,
                    role = normalizedRole,
                    content = newUserText
                )
                onUserMessageAppended?.invoke(appendedUserMessageId)
                logInfo("input message appended; session=$sessionId, role=$normalizedRole, chars=${newUserText.length}")
            } else {
                logInfo("input message already appended; session=$sessionId, role=$normalizedRole, chars=${newUserText.length}")
            }

            val recentUserContext = repository.getMessages(sessionId)
                .asReversed()
                .filter { it.role == "user" || it.role == "internal_user" }
                .take(4)
                .asReversed()
                .joinToString("\n") { it.content }
                .ifBlank { newUserText }
            val runtimeAgentContext = agentRepository?.buildRuntimeContext(sessionId)
            val alwaysSkillNames = skillsLoader.getAlwaysSkills()
            val selectedSkillNames = skillsLoader.selectSkillsForInput(recentUserContext)
            val activeSkillNames = (alwaysSkillNames + selectedSkillNames + runtimeAgentContext?.defaultSkills.orEmpty()).distinct()
            val activeSkillsContent = skillsLoader.loadSkillsForContext(activeSkillNames)
            val skillsSummary = skillsLoader.buildSkillsSummary()
            val systemPolicyTemplate = buildBootstrapPolicyTemplate()
            val maxRounds = maxRoundsProvider()
                .coerceIn(AppLimits.MIN_MAX_TOOL_ROUNDS, AppLimits.MAX_MAX_TOOL_ROUNDS)
            val toolResultMaxChars = toolResultMaxCharsProvider()
                .coerceIn(AppLimits.MIN_TOOL_RESULT_MAX_CHARS, AppLimits.MAX_TOOL_RESULT_MAX_CHARS)
            if (activeSkillNames.isNotEmpty()) {
                logInfo("active skills selected: ${activeSkillNames.joinToString(",")}")
                reportTrace(sessionId, "读取技能", "本轮启用：${activeSkillNames.joinToString("、").take(160)}")
            }
            refreshRuntimeTools?.invoke()
            val initialToolSpec = toolRegistry.toToolSpecList()
            reportTrace(sessionId, "刷新工具", "已准备 ${initialToolSpec.size} 个可用工具，正在判断是否需要调用。")
            val executionPlanContext = createExecutionPlanContext(
                sessionId = sessionId,
                userTask = newUserText,
                recentUserContext = recentUserContext,
                activeSkillNames = activeSkillNames,
                toolSpec = initialToolSpec
            )

            for (round in 1..maxRounds) {
                refreshRuntimeTools?.invoke()
                logInfo("round $round start")
                val toolSpec = toolRegistry.toToolSpecList()
                reportTrace(sessionId, "请求模型", "第 $round 轮正在组织上下文，可用工具 ${toolSpec.size} 个。")

                val turn = requestNonStreamTurn(
                    sessionId = sessionId,
                    toolSpec = toolSpec,
                    activeSkillsContent = activeSkillsContent,
                    skillsSummary = skillsSummary,
                    systemPolicyTemplate = systemPolicyTemplate,
                    agentProfileContext = runtimeAgentContext?.prompt.orEmpty(),
                    executionPlanContext = executionPlanContext
                ) ?: run {
                    logWarn("non-stream turn failed; stop loop")
                    return@withContext
                }

                val parsedToolCalls = turn.parsedToolCalls
                reportTrace(sessionId, "解析工具", if (parsedToolCalls.isEmpty()) "模型没有继续请求工具。" else "模型请求 ${parsedToolCalls.size} 个工具调用。")
                if (parsedToolCalls.isEmpty()) {
                    logInfo("no tool calls; end loop")
                    reportTrace(sessionId, "模型输出", "本轮没有继续调用工具。")
                    return@withContext
                }

                for (call in parsedToolCalls) {
                    logInfo("tool call: ${call.name}, id=${call.id}")
                    reportTrace(sessionId, "使用工具", "准备执行 ${call.name}，等待返回结果。")
                    val result = if (blockedTools.contains(call.name)) {
                        ToolResult(
                            toolCallId = call.id,
                            content = "Tool '${call.name}' is disabled in this execution context.",
                            isError = true
                        )
                    } else {
                        toolRegistry.execute(call)
                    }
                    reportTrace(sessionId, "读取结果", "${call.name} 已返回，正在整理公开摘要。")
                    val safeContent = truncateToolResult(result.content, toolResultMaxChars)
                    val publicNotes = TraceNoteExtractor.extractPublicNotes(safeContent)
                    publicNotes.forEach { note ->
                        reportTrace(sessionId, "工具思考", note)
                    }
                    val resultNote = publicNotes.firstOrNull()
                    val resultSummary = resultNote ?: "${call.name} · ${TraceNoteExtractor.cleanTraceText(safeContent, 80)}"
                    reportTrace(sessionId, "工具结果", resultSummary)
                    repository.appendToolMessage(
                        sessionId = sessionId,
                        content = safeContent,
                        toolResultJson = json.encodeToString(
                            StoredToolResult(
                                toolCallId = result.toolCallId,
                                content = safeContent,
                                isError = result.isError,
                                metadata = result.metadata
                            )
                        )
                    )
                    logInfo(
                        "tool result appended: ${call.name}, isError=${result.isError}, chars=${safeContent.length}"
                    )
                }
            }
            repository.appendAssistantMessage(sessionId, "Stopped after reaching max rounds ($maxRounds).")
            logWarn("reached max rounds: $maxRounds")
        } catch (ce: CancellationException) {
            cancelled = true
            logInfo("agent loop cancelled by user")
            throw ce
        } catch (t: Throwable) {
            logError("round failed", t)
            val userVisibleFailure = formatUserVisibleFailure(t)
            reportTrace(sessionId, "执行失败", userVisibleFailure)
            repository.appendAssistantMessage(sessionId, userVisibleFailure)
            return@withContext
        } finally {
            if (!cancelled) {
                scheduleBackgroundConsolidation(sessionId)
            }
        }
    }
    fun close() {
        backgroundScope.cancel()
        synchronized(consolidationGuard) {
            consolidationJobs.clear()
            consolidationLocks.clear()
        }
    }

    private suspend fun createExecutionPlanContext(
        sessionId: String,
        userTask: String,
        recentUserContext: String,
        activeSkillNames: List<String>,
        toolSpec: List<ToolSpec>
    ): String {
        if (userTask.length < 18 && !looksLikeToolTask(userTask)) return ""
        reportTrace(sessionId, "生成计划", "正在分析任务、技能、工具和终端调用顺序")
        val toolSummary = toolSpec
            .take(80)
            .joinToString("\n") { "- ${it.name}: ${it.description.take(120)}" }
        val skillSummary = activeSkillNames.joinToString("、").ifBlank { "无" }
        val response = runCatching {
            llmProviderFactory().chat(
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = """
                            你是 LGClaw 的任务调度器，采用 Codex 风格的计划-执行-验证闭环。
                            只输出简洁可执行计划，不输出隐藏思维链，不直接回答用户。
                            计划必须包含：目标、步骤、要用的技能、要用的工具、是否需要终端、完成检查。
                            终端是后台工作器，用户继续发送的消息仍属于 Agent 对话；需要运行代码、测试、构建、读取项目或安装依赖时优先调度 terminal_exec。
                            如果终端输出提示缺少 Python/npm/uv 包，允许自动安装必要依赖并重试一次，但要保持命令可解释、范围最小。
                            如果任务很简单，输出“无需工具，直接回答”。
                        """.trimIndent()
                    ),
                    ChatMessage(
                        role = "user",
                        content = """
                            用户当前任务：
                            $userTask

                            最近上下文：
                            $recentUserContext

                            已选技能：
                            $skillSummary

                            可用工具：
                            $toolSummary
                        """.trimIndent()
                    )
                ),
                toolsSpec = emptyList()
            )
        }.onFailure { error ->
            logWarn("execution plan generation failed: ${error.message ?: error.javaClass.simpleName}")
            reportTrace(sessionId, "计划降级", "计划生成失败，将直接进入工具循环")
        }.getOrNull() ?: return ""
        response.usage?.let { usageReporter?.invoke(it) }
        val plan = response.assistant.content.trim().take(4000)
        if (plan.isBlank()) return ""
        reportTrace(sessionId, "计划就绪", plan.lines().firstOrNull().orEmpty().take(100))
        return plan
    }

    private fun looksLikeToolTask(text: String): Boolean {
        val normalized = text.lowercase()
        return listOf(
            "运行", "终端", "代码", "文件", "搜索", "工具", "技能",
            "node", "npm", "python", "git", "ssh", "uv", "build", "test"
        ).any { normalized.contains(it) }
    }
    private suspend fun requestNonStreamTurn(
        sessionId: String,
        toolSpec: List<ToolSpec>,
        activeSkillsContent: String,
        skillsSummary: String,
        systemPolicyTemplate: String?,
        agentProfileContext: String,
        executionPlanContext: String
    ): AssistantTurn? {
        val history = repository.getMessages(sessionId)
        val longTermMemory = memoryStore.readLongTerm()
        val compressedMemorySummary = compressedMemoryStore.buildContextSummary(sessionId)
        val compressedMemoryCutoffAt = compressedMemoryStore.list(sessionId).maxOfOrNull { it.lastMessageAt } ?: 0L
        val llmMessages = contextBuilder.build(
            sessionId = sessionId,
            messages = history,
            maxHistoryMessages = maxContextMessagesProvider().coerceIn(
                AppLimits.MIN_CONTEXT_MESSAGES,
                AppLimits.MAX_CONTEXT_MESSAGES
            ),
            longTermMemory = longTermMemory,
            compressedMemorySummary = compressedMemorySummary,
            compressedMemoryCutoffAt = compressedMemoryCutoffAt,
            activeSkillsContent = activeSkillsContent,
            skillsSummary = skillsSummary,
            systemPolicyTemplate = systemPolicyTemplate,
            agentProfileContext = agentProfileContext,
            executionPlanContext = executionPlanContext
        )
        val initialResponse = try {
            llmProviderFactory().chat(llmMessages, toolSpec)
        } catch (t: Throwable) {
            if (t is InterruptedIOException || (t.message?.contains("timeout", ignoreCase = true) == true)) {
                throw IllegalStateException(
                    "LLM request timed out (configured timeout reached). " +
                        "You can increase Runtime timeout settings and retry.",
                    t
                )
            }
            throw t
        }
        val response = recoverEmptyAssistantResponseIfNeeded(
            sessionId = sessionId,
            llmMessages = llmMessages,
            toolSpec = toolSpec,
            initial = initialResponse
        )
        response.usage?.let { usageReporter?.invoke(it) }
        val parsedToolCalls = toolCallParser.parse(response)
        val toolCallJson = if (parsedToolCalls.isNotEmpty()) {
            json.encodeToString(parsedToolCalls)
        } else {
            null
        }
        logInfo(
            "llm response received; assistantChars=${response.assistant.content.length}, toolCalls=${parsedToolCalls.size}"
        )

        val rawContent = response.assistant.content
        TraceNoteExtractor.extractAssistantNote(rawContent)?.let { note ->
            reportTrace(sessionId, "模型计划", note)
        }
        if (rawContent.isBlank() && parsedToolCalls.isEmpty()) {
            val message = buildEmptyRecoveryFallback(sessionId)
            repository.appendAssistantMessage(sessionId, message)
            reportTrace(sessionId, "空响应恢复", "自动续跑后仍为空，已写入非空进度说明，避免对话停在空白响应。")
            logWarn("empty assistant response without tool call after recovery")
            return AssistantTurn(
                parsedToolCalls = emptyList()
            )
        }

        val persistedContent = when {
            rawContent.isNotBlank() -> rawContent
            parsedToolCalls.isNotEmpty() -> "[tool call]"
            else -> ""
        }

        repository.appendAssistantMessage(
            sessionId = sessionId,
            content = persistedContent,
            toolCallJson = toolCallJson
        )
        logInfo(
            "assistant saved; chars=${persistedContent.length}, toolCalls=${parsedToolCalls.size}"
        )
        return AssistantTurn(parsedToolCalls = parsedToolCalls)
    }

    private fun truncateToolResult(raw: String, maxChars: Int): String {
        if (raw.length <= maxChars) return raw
        return raw.take(maxChars) + "\n...[truncated]"
    }

    private fun reportTrace(sessionId: String, title: String, detail: String) {
        traceReporter?.invoke(sessionId, title, detail)
    }

    private fun buildBootstrapPolicyTemplate(): String? {
        val store = templateStore ?: return null
        val sections = mutableListOf<String>()
        for (name in BOOTSTRAP_TEMPLATE_FILES) {
            val content = when (name) {
                "AGENT.md" -> store.loadTemplate("AGENT.md") ?: store.loadTemplate("system_prompt.md")
                else -> store.loadTemplate(name)
            }?.trim().orEmpty()
            if (content.isBlank()) continue
            sections += "## $name\n\n$content"
        }
        if (sections.isEmpty()) return null
        return sections.joinToString("\n\n---\n\n")
    }

    private fun scheduleBackgroundConsolidation(sessionId: String) {
        val existing = synchronized(consolidationGuard) { consolidationJobs[sessionId] }
        if (existing?.isActive == true) return

        val job = backgroundScope.launch {
            val lock = synchronized(consolidationGuard) {
                consolidationLocks.getOrPut(sessionId) { Mutex() }
            }
            lock.withLock {
                val memoryWindow = normalizedMemoryWindow()
                val allMessages = repository.getMessages(sessionId)
                compressedMemoryStore.compressIfNeeded(sessionId, allMessages, compressionThresholdKProvider())?.let { record ->
                    logInfo("compressed memory saved: ${record.id}, algorithm=${record.algorithm}, original=${record.originalChars}, compressed=${record.compressedBytes}")
                }
                val safeLast = memoryStore.getLastConsolidatedIndex(sessionId).coerceIn(0, allMessages.size)
                val unconsolidated = allMessages.size - safeLast
                if (unconsolidated < memoryWindow) return@withLock
                val hasPending = memoryConsolidator.hasPendingConsolidation(sessionId, memoryWindow)
                if (!hasPending) return@withLock
                when (val result = memoryConsolidator.consolidateIfNeeded(sessionId, memoryWindow)) {
                    is MemoryConsolidator.ConsolidationResult.Updated -> {
                        logInfo("memory consolidation updated long-term memory")
                    }

                    is MemoryConsolidator.ConsolidationResult.Failed -> {
                        logWarn("memory consolidation failed: ${result.reason}")
                    }

                    is MemoryConsolidator.ConsolidationResult.Noop -> Unit
                }
            }
        }

        synchronized(consolidationGuard) {
            consolidationJobs[sessionId] = job
        }
        job.invokeOnCompletion {
            synchronized(consolidationGuard) {
                if (consolidationJobs[sessionId] === job) {
                    consolidationJobs.remove(sessionId)
                }
            }
        }
    }

    private fun normalizedMemoryWindow(): Int {
        return memoryWindowProvider().coerceIn(
            AppLimits.MIN_MEMORY_CONSOLIDATION_WINDOW,
            AppLimits.MAX_MEMORY_CONSOLIDATION_WINDOW
        )
    }

    private suspend fun recoverEmptyAssistantResponseIfNeeded(
        sessionId: String,
        llmMessages: List<ChatMessage>,
        toolSpec: List<ToolSpec>,
        initial: LlmResponse
    ): LlmResponse {
        if (initial.assistant.content.isNotBlank() || initial.assistant.toolCalls.isNotEmpty()) {
            return initial
        }
        val recoveryPrompts = listOf(
            "上一轮模型返回了空响应。请不要留空：如果任务还没完成，请继续完成；如果已经完成，请用中文给出清晰结果、已完成事项和下一步建议。",
            "仍然没有收到可用正文。请基于当前上下文和已有工具结果继续完成任务。必须返回非空中文回复；不要只返回空白、不要等待用户再次发送。"
        )
        var last = initial
        recoveryPrompts.forEachIndexed { index, prompt ->
            reportTrace(sessionId, "空响应恢复", "模型返回空内容，正在第 ${index + 1} 次自动续跑。")
            val recoveryMessages = llmMessages + ChatMessage(role = "user", content = prompt)
            last = runCatching {
                llmProviderFactory().chat(recoveryMessages, toolSpec)
            }.onFailure { error ->
                reportTrace(sessionId, "空响应恢复失败", error.message ?: error.javaClass.simpleName)
                logWarn("empty response recovery failed: ${error.message ?: error.javaClass.simpleName}")
            }.getOrElse { last }
            if (last.assistant.content.isNotBlank() || last.assistant.toolCalls.isNotEmpty()) {
                return last
            }
        }
        return last
    }

    private suspend fun buildEmptyRecoveryFallback(sessionId: String): String {
        val recent = repository.getMessages(sessionId)
            .asReversed()
            .asSequence()
            .filter { it.role == "tool" || it.role == "assistant" }
            .map { TraceNoteExtractor.cleanTraceText(it.content, 120) }
            .filter { it.isNotBlank() && it != "[tool call]" }
            .take(3)
            .toList()
            .asReversed()
        return buildString {
            append("模型这轮没有返回正文，我已经自动续跑过，但服务端仍然给了空内容。")
            append("\n\n")
            append("当前任务没有丢失，我已保留上下文和工具结果；本轮可见进度如下：")
            if (recent.isEmpty()) {
                append("\n- 已完成上下文整理与调度检查。")
            } else {
                recent.forEach { append("\n- ").append(it.take(160)) }
            }
            append("\n\n")
            append("我不会再让对话栏显示空响应；下一轮会继续从这些上下文接着完成。")
        }
    }

    private fun formatUserVisibleFailure(t: Throwable): String {
        val raw = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        val lower = raw.lowercase()
        return when {
            "http 401" in lower || "authentication error" in lower || "auth_error" in lower || "认证失败" in lower -> {
                "模型认证没有通过（HTTP 401），我已经停止本轮模型请求并保留过程记录。请在模型控制台检查 API Key、账号连接状态和 Base URL；如果该服务要求先 connect() 到 query engine，请先完成连接后重试。原始信息：$raw"
            }
            "http 404" in lower || "404 page not found" in lower -> {
                "这次模型请求命中了不存在的接口地址（HTTP 404）。请在模型控制台确认 Base URL 不要同时写错协议路径，例如 OpenAI 兼容接口通常是 `/v1/chat/completions` 或只填到 `/v1`。原始信息：$raw"
            }
            "unexpected json token" in lower && "data:" in lower -> {
                "模型返回了流式 `data:` 包装内容，但当前请求按普通 JSON 解析失败。请重试这条消息。原始信息：$raw"
            }
            "timed out" in lower || "timeout" in lower -> {
                "任务超时了。终端下载、安装依赖、构建测试类任务会使用更长的自适应超时，并在超时后自动清理进程，避免卡住后续对话。可以直接继续发消息或让我重试。原始信息：$raw"
            }
            else -> "执行时遇到问题：$raw"
        }
    }

    @kotlinx.serialization.Serializable
    private data class StoredToolResult(
        val toolCallId: String,
        val content: String,
        val isError: Boolean,
        val metadata: JsonObject? = null
    )

    private data class AssistantTurn(
        val parsedToolCalls: List<ToolCall>
    )

    private fun logInfo(message: String) {
        Log.d(TAG, message)
        processLogger?.invoke(message)
    }

    private fun logWarn(message: String) {
        Log.w(TAG, message)
        processLogger?.invoke("WARN: $message")
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val suffix = throwable?.message?.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty()
        processLogger?.invoke("ERROR: $message$suffix")
    }

    companion object {
        private val sessionIdThreadLocal = ThreadLocal<String?>()

        fun currentSessionId(): String? = sessionIdThreadLocal.get()

        private fun sessionContext(sessionId: String) = sessionIdThreadLocal.asContextElement(sessionId)
        private const val TAG = "AgentLoop"
        private val BOOTSTRAP_TEMPLATE_FILES = listOf(
            "AGENT.md",
            "SOUL.md",
            "USER.md",
            "TOOLS.md"
        )
    }
}






