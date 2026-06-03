package com.lgclaw.engine.intent

import kotlinx.serialization.Serializable

/**
 * Task node definitions for engine layer
 */
@Serializable
sealed class TaskNode {
    abstract val id: String
    abstract val kind: NodeKind
    abstract val deps: List<String>
    abstract val timeoutMs: Long
    
    @Serializable
    data class Skill(
        override val id: String,
        val skillName: String,
        override val deps: List<String> = emptyList(),
        override val timeoutMs: Long = 30_000L
    ) : TaskNode() {
        override val kind: NodeKind = NodeKind.SKILL
    }
    
    @Serializable
    data class Tool(
        override val id: String,
        val toolName: String,
        val argumentsJson: String,
        override val deps: List<String> = emptyList(),
        override val timeoutMs: Long = 60_000L
    ) : TaskNode() {
        override val kind: NodeKind = NodeKind.TOOL
    }
    
    @Serializable
    data class Terminal(
        override val id: String,
        val command: String,
        override val deps: List<String> = emptyList(),
        override val timeoutMs: Long = 120_000L
    ) : TaskNode() {
        override val kind: NodeKind = NodeKind.TERMINAL
    }
    
    @Serializable
    data class LlmChat(
        override val id: String,
        val purpose: String = "main",
        override val deps: List<String> = emptyList(),
        override val timeoutMs: Long = 90_000L
    ) : TaskNode() {
        override val kind: NodeKind = NodeKind.LLM
    }
}

@Serializable
enum class NodeKind { SKILL, TOOL, TERMINAL, LLM }

@Serializable
enum class NodeStatus { Pending, Running, Succeeded, Failed, Cancelled, Skipped }

data class NodeResult(
    val nodeId: String,
    val status: NodeStatus,
    val payload: String = "",
    val error: String? = null,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L
)