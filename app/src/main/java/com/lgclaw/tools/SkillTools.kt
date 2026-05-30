package com.lgclaw.tools

import com.lgclaw.skills.SkillStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun createSkillManagementToolSet(store: SkillStore): List<Tool> = listOf(
    SkillListTool(store),
    SkillCreateTool(store),
    SkillImportTool(store),
    SkillEnableTool(store)
)

private val skillToolJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

private fun skillSchema(required: String, propertiesJson: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("required", Json.parseToJsonElement(required))
    put("properties", Json.parseToJsonElement(propertiesJson.trimIndent()))
}

private class SkillListTool(private val store: SkillStore) : Tool {
    override val name: String = "skill_store_list"
    override val description: String = "List installed LGClaw/OpenClaw-compatible skills and enabled state."
    override val jsonSchema: JsonObject = skillSchema("[]", "{}")

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val skills = store.list()
        val content = if (skills.isEmpty()) {
            "No skills installed. Use skill_store_create or skill_store_import."
        } else {
            skills.joinToString("\n") { skill ->
                "- @${skill.name} enabled=${skill.enabled} source=${skill.source}: ${skill.description}"
            }
        }
        ToolResult(toolCallId = "", content = content, isError = false)
    }
}

private class SkillCreateTool(private val store: SkillStore) : Tool {
    override val name: String = "skill_store_create"
    override val description: String = "Create a local skill as SKILL.md. It can be invoked by text matching or @skill-name."
    override val jsonSchema: JsonObject = skillSchema(
        "[\"name\",\"body\"]",
        """
        {
          "name":{"type":"string","description":"Skill command name, for example openclaw"},
          "description":{"type":"string"},
          "body":{"type":"string","description":"Skill instructions to embed into AI context"},
          "enabled":{"type":"boolean"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = skillToolJson.decodeFromString<CreateArgs>(argumentsJson)
        val skill = store.createOrUpdate(args.name, args.description.orEmpty(), args.body, args.enabled ?: true)
        ToolResult(
            toolCallId = "",
            content = "Skill saved: @${skill.name}. enabled=${skill.enabled}. Path: ${skill.path}",
            isError = false
        )
    }

    @Serializable
    private data class CreateArgs(
        val name: String,
        val description: String? = null,
        val body: String,
        val enabled: Boolean? = null
    )
}

private class SkillImportTool(private val store: SkillStore) : Tool {
    override val name: String = "skill_store_import"
    override val description: String = "Import OpenClaw/LGClaw compatible skill markdown into the local skill store."
    override val jsonSchema: JsonObject = skillSchema(
        "[\"name\",\"markdown\"]",
        """
        {
          "name":{"type":"string"},
          "markdown":{"type":"string","description":"Full SKILL.md content or compatible markdown"},
          "enabled":{"type":"boolean"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = skillToolJson.decodeFromString<ImportArgs>(argumentsJson)
        val skill = store.importSkill(args.name, args.markdown, args.enabled ?: true)
        ToolResult(toolCallId = "", content = "Skill imported: @${skill.name}. enabled=${skill.enabled}.", isError = false)
    }

    @Serializable
    private data class ImportArgs(val name: String, val markdown: String, val enabled: Boolean? = null)
}

private class SkillEnableTool(private val store: SkillStore) : Tool {
    override val name: String = "skill_store_enable"
    override val description: String = "Enable or disable a local/builtin skill. Disabled skills are hidden from automatic and @ invocation."
    override val jsonSchema: JsonObject = skillSchema(
        "[\"name\",\"enabled\"]",
        """
        {
          "name":{"type":"string"},
          "enabled":{"type":"boolean"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = skillToolJson.decodeFromString<EnableArgs>(argumentsJson)
        store.setEnabled(args.name, args.enabled)
        ToolResult(toolCallId = "", content = "Skill @${SkillStore.sanitizeSkillName(args.name)} enabled=${args.enabled}.", isError = false)
    }

    @Serializable
    private data class EnableArgs(val name: String, val enabled: Boolean)
}