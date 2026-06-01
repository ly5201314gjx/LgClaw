package com.lgclaw.agent

import android.util.Log
import com.lgclaw.agents.AgentRepository
import com.lgclaw.config.AppLimits
import com.lgclaw.memory.MemoryStore
import com.lgclaw.memory.CompressedMemoryStore
import com.lgclaw.providers.ChatMessage
import com.lgclaw.providers.LlmProvider
import com.lgclaw.providers.LlmUsage
import com.lgclaw.providers.ToolCall
import com.lgclaw.providers.ToolSpec
import com.lgclaw.skills.SkillsLoader
import com.lgclaw.storage.MessageRepository
import com.lgclaw.templates.TemplateStore
import com.lgclaw.tools.ToolRegistry
import com.lgclaw.tools.ToolResult
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
                reportTrace(sessionId, "读取技能", activeSkillNames.joinToString("、").take(120))
            }
            refreshRuntimeTools?.invoke()
            val initialToolSpec = toolRegistry.toToolSpecList()
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
                reportTrace(sessionId, "请求模型", "第 $round 轮，工具数=${toolRegistry.toToolSpecList().size}")
                val toolSpec = toolRegistry.toToolSpecList()

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
                if (parsedToolCalls.isEmpty()) {
                    logInfo("no tool calls; end loop")
                    reportTrace(sessionId, "模型输出", "本轮没有继续调用工具。")
                    return@withContext
                }

                for (call in parsedToolCalls) {
                    logInfo("tool call: ${call.name}, id=${call.id}")
                    reportTrace(sessionId, "使用工具", call.name)
                    val result = if (blockedTools.contains(call.name)) {
                        ToolResult(
                            toolCallId = call.id,
                            content = "Tool '${call.name}' is disabled in this execution context.",
                            isError = true
                        )
                    } else {
                        toolRegistry.execute(call)
                    }
                    val safeContent = truncateToolResult(result.content, toolResultMaxChars)
                    reportTrace(sessionId, "工具结果", "${call.name} · ${safeContent.take(80)}")
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
            reportTrace(sessionId, "执行失败", t.message ?: t.javaClass.simpleName)
            repository.appendAssistantMessage(
                sessionId,
                "Error: ${t.message ?: t.javaClass.simpleName}"
            )
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
        val response = try {
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
        if (rawContent.isBlank() && parsedToolCalls.isEmpty()) {
            repository.appendAssistantMessage(sessionId, "[Error] Empty assistant response.")
            logWarn("empty assistant response without tool call")
            return null
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




