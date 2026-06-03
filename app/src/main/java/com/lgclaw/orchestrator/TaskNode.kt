package com.lgclaw.orchestrator

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class TaskNode(
    val id: String,
    val kind: NodeKind,
    val deps: Set<String> = emptySet(),
    val input: JsonObject = JsonObject(emptyMap()),
    val label: String = id,
    val attempt: Int = 0,
    val maxAttempts: Int = 2,
    val timeoutMs: Long = TaskNodeDefaults.DEFAULT_TIMEOUT_MS,
    val metadata: Map<String, String> = emptyMap()
) {
    fun withAttempt(next: Int): TaskNode = copy(attempt = next.coerceAtLeast(0))
    fun shortReason(): String = label.ifBlank { id }
}

@Serializable
enum class NodeKind {
    LlmCall,
    ToolCall,
    SkillCall,
    TerminalCall,
    Aggregate
}

@Serializable
enum class NodeState {
    Pending,
    Ready,
    Running,
    Paused,
    Completed,
    Failed,
    Cancelled
}

@Serializable
data class NodeStatus(
    val id: String,
    val state: NodeState,
    val progress: Float = 0f,
    val attempt: Int = 0,
    val lastError: String = ""
) {
    val isTerminal: Boolean
        get() = state == NodeState.Completed || state == NodeState.Failed || state == NodeState.Cancelled
}

object TaskNodeDefaults {
    const val DEFAULT_TIMEOUT_MS: Long = 30_000L
    fun emptyJson(): JsonObject = JsonObject(mapOf())
}