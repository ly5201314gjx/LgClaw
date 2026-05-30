package com.lgclaw.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun createDynamicToolManagementSet(store: DynamicToolStore): List<Tool> = listOf(
    DynamicToolListTool(store),
    DynamicToolCreateTool(store),
    DynamicToolEnableTool(store)
)

fun createDynamicPromptTools(store: DynamicToolStore): List<Tool> = store.enabledTools().map { DynamicPromptTool(it) }

private val dynamicJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

private fun dynamicSchema(required: String, propertiesJson: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", true)
    put("required", Json.parseToJsonElement(required))
    put("properties", Json.parseToJsonElement(propertiesJson.trimIndent()))
}

private class DynamicPromptTool(private val spec: DynamicToolStore.DynamicToolSpec) : Tool {
    override val name: String = spec.name
    override val description: String = spec.description
    override val jsonSchema: JsonObject = dynamicSchema(
        "[]",
        """
        {
          "input":{"type":"string","description":"Optional task-specific input for this dynamic workflow tool"},
          "context":{"type":"string","description":"Optional extra context or constraints"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = runCatching { dynamicJson.decodeFromString<DynamicRunArgs>(argumentsJson) }.getOrDefault(DynamicRunArgs())
        val content = buildString {
            appendLine("Dynamic tool: ${spec.name}")
            appendLine("Description: ${spec.description}")
            appendLine()
            appendLine("Workflow prompt:")
            appendLine(spec.prompt.trim())
            if (!args.input.isNullOrBlank() || !args.context.isNullOrBlank()) {
                appendLine()
                appendLine("Invocation arguments:")
                if (!args.input.isNullOrBlank()) appendLine("input: ${args.input}")
                if (!args.context.isNullOrBlank()) appendLine("context: ${args.context}")
            }
            appendLine()
            append("Follow this workflow exactly and continue the task using the available conversation context and tools.")
        }
        ToolResult(toolCallId = "", content = content, isError = false)
    }

    @Serializable
    private data class DynamicRunArgs(val input: String? = null, val context: String? = null)
}

private class DynamicToolListTool(private val store: DynamicToolStore) : Tool {
    override val name: String = "dynamic_tool_list"
    override val description: String = "List locally installed dynamic LGClaw workflow tools and whether they are enabled."
    override val jsonSchema: JsonObject = dynamicSchema("[]", "{}")

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val tools = store.list()
        val content = if (tools.isEmpty()) {
            "No dynamic tools installed. Use dynamic_tool_create to add one."
        } else {
            tools.joinToString("\n") { tool ->
                "- ${tool.name} enabled=${tool.enabled} updated=${tool.updatedAt}: ${tool.description}"
            }
        }
        ToolResult(toolCallId = "", content = content, isError = false)
    }
}

private class DynamicToolCreateTool(private val store: DynamicToolStore) : Tool {
    override val name: String = "dynamic_tool_create"
    override val description: String = "Create or update a local dynamic workflow tool. The tool becomes callable immediately after runtime reload or the next turn."
    override val jsonSchema: JsonObject = dynamicSchema(
        "[\"name\",\"prompt\"]",
        """
        {
          "name":{"type":"string","description":"Short command name. It will be stored as dyn_name."},
          "description":{"type":"string"},
          "prompt":{"type":"string","description":"Workflow instructions the AI should follow when this tool is called."},
          "enabled":{"type":"boolean"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = dynamicJson.decodeFromString<CreateArgs>(argumentsJson)
        val tool = store.upsert(
            name = args.name,
            description = args.description.orEmpty(),
            prompt = args.prompt,
            enabled = args.enabled ?: true
        )
        ToolResult(
            toolCallId = "",
            content = "Dynamic tool saved: ${tool.name}. enabled=${tool.enabled}. It is available from the tool panel and will be registered for AI calls on the next runtime refresh/turn.",
            isError = false
        )
    }

    @Serializable
    private data class CreateArgs(
        val name: String,
        val description: String? = null,
        val prompt: String,
        val enabled: Boolean? = null
    )
}

private class DynamicToolEnableTool(private val store: DynamicToolStore) : Tool {
    override val name: String = "dynamic_tool_enable"
    override val description: String = "Enable or disable a local dynamic workflow tool."
    override val jsonSchema: JsonObject = dynamicSchema(
        "[\"name\",\"enabled\"]",
        """
        {
          "name":{"type":"string"},
          "enabled":{"type":"boolean"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = dynamicJson.decodeFromString<EnableArgs>(argumentsJson)
        val tool = store.setEnabled(args.name, args.enabled)
        ToolResult(toolCallId = "", content = "Dynamic tool ${tool.name} enabled=${tool.enabled}.", isError = false)
    }

    @Serializable
    private data class EnableArgs(val name: String, val enabled: Boolean)
}