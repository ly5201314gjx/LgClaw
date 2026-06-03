package com.lgclaw.engine.intent

import com.lgclaw.providers.ToolCall
import java.util.UUID

/**
 * Intent resolver for converting tool calls to task nodes
 */
class IntentResolver {
    
    fun fromToolCalls(calls: List<ToolCall>): List<TaskNode> = calls.map { call ->
        when {
            call.name.startsWith("skill_") -> TaskNode.Skill(
                id = UUID.randomUUID().toString(),
                skillName = call.name.removePrefix("skill_")
            )
            call.name == "terminal_exec" -> TaskNode.Terminal(
                id = UUID.randomUUID().toString(),
                command = extractCommandArg(call.argumentsJson)
            )
            else -> TaskNode.Tool(
                id = UUID.randomUUID().toString(),
                toolName = call.name,
                argumentsJson = call.argumentsJson
            )
        }
    }
    
    private fun extractCommandArg(argumentsJson: String): String {
        val trimmed = argumentsJson.trim()
        if (trimmed.isEmpty()) return ""
        val cmdKey = Regex("\"command\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = cmdKey.find(trimmed)
        if (match != null) {
            return match.groupValues[1]
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }
        return trimmed
    }
}