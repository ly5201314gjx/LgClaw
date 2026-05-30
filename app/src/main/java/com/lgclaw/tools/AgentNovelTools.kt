package com.lgclaw.tools

import com.lgclaw.agents.AgentRepository
import com.lgclaw.novel.NovelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray

fun createAgentManagementToolSet(
    agentRepository: AgentRepository,
    currentSessionIdProvider: () -> String
): List<Tool> = listOf(
    AgentProfileListTool(agentRepository),
    AgentProfileCreateTool(agentRepository),
    AgentProfileBindSessionTool(agentRepository, currentSessionIdProvider)
)

fun createNovelToolSet(
    novelRepository: NovelRepository,
    agentRepository: AgentRepository,
    currentSessionIdProvider: () -> String
): List<Tool> = listOf(
    NovelProjectCreateTool(novelRepository, agentRepository, currentSessionIdProvider),
    NovelChapterUpsertTool(novelRepository),
    NovelCharacterUpsertTool(novelRepository),
    NovelRelationAnalyzeTool(novelRepository),
    NovelSummaryCreateTool(novelRepository),
    NovelContinuityCheckTool(novelRepository)
)

private val agentToolJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

private fun schema(required: String, propertiesJson: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("required", Json.parseToJsonElement(required))
    put("properties", Json.parseToJsonElement(propertiesJson.trimIndent()))
}

private class AgentProfileListTool(private val repo: AgentRepository) : Tool {
    override val name = "agent_profile_list"
    override val description = "List local LGClaw runtime agent profiles and built-in agents."
    override val jsonSchema = schema("[]", "{}")
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val profiles = repo.listProfiles()
        val content = if (profiles.isEmpty()) "No agent profiles." else profiles.joinToString("\n") {
            "- ${it.id} enabled=${it.enabled} type=${it.type}: ${it.name} - ${it.description}"
        }
        ToolResult("", content, false)
    }
}

private class AgentProfileCreateTool(private val repo: AgentRepository) : Tool {
    override val name = "agent_profile_create"
    override val description = "Create or update a local runtime agent profile with system prompt, default skills, and dynamic tools."
    override val jsonSchema = schema(
        "[\"name\",\"systemPrompt\"]",
        """
        {
          "id":{"type":"string"},
          "name":{"type":"string"},
          "type":{"type":"string"},
          "description":{"type":"string"},
          "systemPrompt":{"type":"string"},
          "defaultSkills":{"type":"array","items":{"type":"string"}},
          "dynamicTools":{"type":"array","items":{"type":"string"}},
          "enabled":{"type":"boolean"}
        }
        """
    )
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = agentToolJson.decodeFromString<CreateAgentArgs>(argumentsJson)
        val profile = repo.createOrUpdateProfile(
            id = args.id,
            name = args.name,
            type = args.type.orEmpty(),
            description = args.description.orEmpty(),
            systemPrompt = args.systemPrompt,
            defaultSkills = args.defaultSkills.orEmpty(),
            dynamicTools = args.dynamicTools.orEmpty(),
            enabled = args.enabled ?: true
        )
        ToolResult("", "Agent profile saved: ${profile.id} (${profile.name}).", false)
    }
    @Serializable private data class CreateAgentArgs(
        val id: String? = null,
        val name: String,
        val type: String? = null,
        val description: String? = null,
        val systemPrompt: String,
        val defaultSkills: List<String>? = null,
        val dynamicTools: List<String>? = null,
        val enabled: Boolean? = null
    )
}

private class AgentProfileBindSessionTool(
    private val repo: AgentRepository,
    private val currentSessionIdProvider: () -> String
) : Tool {
    override val name = "agent_profile_bind_session"
    override val description = "Bind an agent profile and optional active novel project to a session. Defaults to the current session."
    override val jsonSchema = schema(
        "[]",
        """
        {
          "sessionId":{"type":"string"},
          "agentId":{"type":"string"},
          "activeNovelProjectId":{"type":"string"}
        }
        """
    )
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = agentToolJson.decodeFromString<BindArgs>(argumentsJson)
        val sessionId = args.sessionId?.trim()?.ifBlank { null } ?: currentSessionIdProvider()
        repo.bindSession(sessionId, args.agentId, args.activeNovelProjectId)
        ToolResult("", "Session $sessionId bound to agent=${args.agentId.orEmpty()} novel=${args.activeNovelProjectId.orEmpty()}.", false)
    }
    @Serializable private data class BindArgs(val sessionId: String? = null, val agentId: String? = null, val activeNovelProjectId: String? = null)
}

private class NovelProjectCreateTool(
    private val repo: NovelRepository,
    private val agentRepo: AgentRepository,
    private val currentSessionIdProvider: () -> String
) : Tool {
    override val name = "novel_project_create"
    override val description = "Create a local novel project and make it active for the current session."
    override val jsonSchema = schema(
        "[\"title\"]",
        """
        {"title":{"type":"string"},"genre":{"type":"string"},"styleGuide":{"type":"string"},"premise":{"type":"string"},"bindCurrentSession":{"type":"boolean"}}
        """
    )
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = agentToolJson.decodeFromString<ProjectArgs>(argumentsJson)
        val project = repo.createProject(args.title, args.genre.orEmpty(), args.styleGuide.orEmpty(), args.premise.orEmpty())
        if (args.bindCurrentSession != false) {
            agentRepo.bindSession(currentSessionIdProvider(), AgentRepository.NOVEL_AGENT_ID, project.id)
        }
        ToolResult("", "Novel project created: ${project.id} (${project.title}).", false)
    }
    @Serializable private data class ProjectArgs(val title: String, val genre: String? = null, val styleGuide: String? = null, val premise: String? = null, val bindCurrentSession: Boolean? = true)
}

private class NovelChapterUpsertTool(private val repo: NovelRepository) : Tool {
    override val name = "novel_chapter_upsert"
    override val description = "Create or update a chapter in a local novel project. TextRank summary and keywords are generated locally."
    override val jsonSchema = schema(
        "[\"projectId\",\"chapterIndex\",\"content\"]",
        """
        {"projectId":{"type":"string"},"chapterIndex":{"type":"integer"},"title":{"type":"string"},"content":{"type":"string"},"summary":{"type":"string"}}
        """
    )
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = agentToolJson.decodeFromString<ChapterArgs>(argumentsJson)
        val chapter = repo.upsertChapter(args.projectId, args.chapterIndex, args.title.orEmpty(), args.content, args.summary)
        ToolResult("", "Chapter saved: ${chapter.projectId} #${chapter.chapterIndex} ${chapter.title}. Summary chars=${chapter.summary.length}.", false)
    }
    @Serializable private data class ChapterArgs(val projectId: String, val chapterIndex: Int, val title: String? = null, val content: String, val summary: String? = null)
}

private class NovelCharacterUpsertTool(private val repo: NovelRepository) : Tool {
    override val name = "novel_character_upsert"
    override val description = "Create or update a character profile in a local novel project."
    override val jsonSchema = schema(
        "[\"projectId\",\"name\"]",
        """
        {"projectId":{"type":"string"},"name":{"type":"string"},"aliases":{"type":"array","items":{"type":"string"}},"goal":{"type":"string"},"secret":{"type":"string"},"arc":{"type":"string"},"notes":{"type":"string"}}
        """
    )
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = agentToolJson.decodeFromString<CharacterArgs>(argumentsJson)
        val character = repo.upsertCharacter(args.projectId, args.name, args.aliases.orEmpty(), args.goal.orEmpty(), args.secret.orEmpty(), args.arc.orEmpty(), args.notes.orEmpty())
        ToolResult("", "Character saved: ${character.name} (${character.id}).", false)
    }
    @Serializable private data class CharacterArgs(val projectId: String, val name: String, val aliases: List<String>? = null, val goal: String? = null, val secret: String? = null, val arc: String? = null, val notes: String? = null)
}

private class NovelRelationAnalyzeTool(private val repo: NovelRepository) : Tool {
    override val name = "novel_relation_analyze"
    override val description = "Analyze character co-occurrence relation graph for a novel project using local chapter text."
    override val jsonSchema = schema("[\"projectId\"]", "{\"projectId\":{\"type\":\"string\"}}")
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = agentToolJson.decodeFromString<ProjectIdArgs>(argumentsJson)
        val relations = repo.analyzeRelations(args.projectId)
        ToolResult("", "Relation graph updated with ${relations.size} edges.", false)
    }
}

private class NovelSummaryCreateTool(private val repo: NovelRepository) : Tool {
    override val name = "novel_summary_create"
    override val description = "Create a local TextRank project summary from all chapter summaries and content."
    override val jsonSchema = schema("[\"projectId\"]", "{\"projectId\":{\"type\":\"string\"}}")
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = agentToolJson.decodeFromString<ProjectIdArgs>(argumentsJson)
        val analysis = repo.summarizeProject(args.projectId)
        ToolResult("", "Summary saved: ${analysis.id}\n${analysis.summary}", false)
    }
}

private class NovelContinuityCheckTool(private val repo: NovelRepository) : Tool {
    override val name = "novel_continuity_check"
    override val description = "Build a local continuity check report from project summaries, character arcs, world notes, and relation graph."
    override val jsonSchema = schema("[\"projectId\"]", "{\"projectId\":{\"type\":\"string\"}}")
    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = agentToolJson.decodeFromString<ProjectIdArgs>(argumentsJson)
        val context = repo.buildContextSummary(args.projectId)
        val lines = context.lines().filter { it.contains("goal=") || it.contains("arc=") || it.contains("Relations:") || it.contains("World notes:") }
        val issues = JSONArray()
        if (!context.contains("Chapters:")) issues.put("No chapters are stored yet; continuity cannot be checked deeply.")
        if (!context.contains("Characters:")) issues.put("No character profiles are stored yet.")
        if (!context.contains("Relations:")) issues.put("Relation graph has not been analyzed yet; run novel_relation_analyze.")
        val report = buildString {
            appendLine("Continuity report for ${args.projectId}")
            appendLine("Signals checked: ${lines.size}")
            if (issues.length() == 0) appendLine("No structural continuity gaps detected by local checks.") else {
                for (i in 0 until issues.length()) appendLine("- ${issues.optString(i)}")
            }
            appendLine()
            append(context.take(2500))
        }
        ToolResult("", report, false)
    }
}

@Serializable private data class ProjectIdArgs(val projectId: String)