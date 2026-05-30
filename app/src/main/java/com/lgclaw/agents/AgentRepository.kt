package com.lgclaw.agents

import com.lgclaw.novel.NovelRepository
import com.lgclaw.storage.dao.AgentProfileDao
import com.lgclaw.storage.entities.AgentProfileEntity
import com.lgclaw.storage.entities.RoleCardEntity
import com.lgclaw.storage.entities.SessionAgentBindingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

class AgentRepository(
    private val dao: AgentProfileDao,
    private val novelRepository: NovelRepository,
    private val roleCardRepository: RoleCardRepository
) {
    data class RuntimeAgentContext(
        val binding: SessionAgentBindingEntity?,
        val profile: AgentProfileEntity?,
        val activeNovelProjectId: String?,
        val roleCard: RoleCardEntity?,
        val prompt: String,
        val defaultSkills: List<String>,
        val dynamicTools: List<String>
    )
    data class AgentProfileDraft(
        val id: String?,
        val name: String,
        val type: String,
        val description: String,
        val systemPrompt: String,
        val defaultSkills: List<String>,
        val dynamicTools: List<String>,
        val enabled: Boolean
    )

    data class AgentValidationResult(
        val valid: Boolean,
        val issues: List<String>,
        val normalizedPrompt: String
    )

    suspend fun bootstrapBuiltins() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = dao.listProfiles().associateBy { it.id }
        dao.upsertProfiles(BUILTIN_PROFILES.map { seed ->
            val old = existing[seed.id]
            AgentProfileEntity(
                id = seed.id,
                name = seed.name,
                type = seed.type,
                description = seed.description,
                systemPrompt = seed.systemPrompt,
                enabled = old?.enabled ?: true,
                defaultSkillNamesJson = toJsonArray(seed.defaultSkills),
                dynamicToolNamesJson = toJsonArray(seed.dynamicTools),
                createdAt = old?.createdAt ?: now,
                updatedAt = now
            )
        })
    }

    suspend fun listProfiles(): List<AgentProfileEntity> = withContext(Dispatchers.IO) {
        bootstrapBuiltins()
        dao.listProfiles()
    }

    suspend fun getProfile(id: String): AgentProfileEntity? = withContext(Dispatchers.IO) {
        bootstrapBuiltins()
        dao.getProfile(id.trim())
    }

    suspend fun createOrUpdateProfile(
        id: String?,
        name: String,
        type: String,
        description: String,
        systemPrompt: String,
        defaultSkills: List<String>,
        dynamicTools: List<String>,
        enabled: Boolean = true
    ): AgentProfileEntity = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifBlank { throw IllegalArgumentException("智能体名称不能为空") }
        val cleanPrompt = systemPrompt.trim().ifBlank { throw IllegalArgumentException("系统提示词不能为空") }
        require(cleanPrompt.length >= 24) { "系统提示词太短" }
        val cleanId = id?.trim()?.ifBlank { null } ?: "agent_${slug(cleanName)}_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        val old = dao.getProfile(cleanId)
        val profile = AgentProfileEntity(
            id = cleanId,
            name = cleanName.take(80),
            type = type.trim().lowercase(Locale.US).ifBlank { "custom" }.take(40),
            description = description.trim().ifBlank { "本地 LGClaw 智能体：$cleanName" }.take(500),
            systemPrompt = cleanPrompt,
            enabled = enabled,
            defaultSkillNamesJson = toJsonArray(defaultSkills.map(::sanitizeCommandName)),
            dynamicToolNamesJson = toJsonArray(dynamicTools.map(::sanitizeCommandName)),
            createdAt = old?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsertProfile(profile)
        profile
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val cleanId = id.trim()
        if (!enabled) {
            dao.clearAgentBindings(cleanId, System.currentTimeMillis())
        }
        dao.setProfileEnabled(cleanId, enabled, System.currentTimeMillis())
    }

    suspend fun updateProfile(draft: AgentProfileDraft): AgentProfileEntity = withContext(Dispatchers.IO) {
        val id = draft.id?.trim()?.ifBlank { null } ?: throw IllegalArgumentException("智能体编号不能为空")
        val old = dao.getProfile(id) ?: throw IllegalArgumentException("智能体不存在")
        if (AgentRepository.isBuiltin(id)) {
            throw IllegalArgumentException("内置智能体不能直接改写，请复制为自定义智能体后编辑")
        }
        val validation = validateProfileDraft(draft)
        require(validation.valid) { validation.issues.joinToString("；") }
        val now = System.currentTimeMillis()
        val profile = AgentProfileEntity(
            id = id,
            name = draft.name.trim().take(80),
            type = draft.type.trim().lowercase(Locale.US).ifBlank { "custom" }.take(40),
            description = draft.description.trim().ifBlank { "本地 LGClaw 智能体：${draft.name.trim()}" }.take(500),
            systemPrompt = validation.normalizedPrompt,
            enabled = draft.enabled,
            defaultSkillNamesJson = toJsonArray(draft.defaultSkills.map(::sanitizeCommandName)),
            dynamicToolNamesJson = toJsonArray(draft.dynamicTools.map(::sanitizeCommandName)),
            createdAt = old.createdAt,
            updatedAt = now
        )
        dao.upsertProfile(profile)
        profile
    }

    suspend fun saveProfileDraft(draft: AgentProfileDraft): AgentProfileEntity = withContext(Dispatchers.IO) {
        val validation = validateProfileDraft(draft)
        require(validation.valid) { validation.issues.joinToString("；") }
        createOrUpdateProfile(
            id = draft.id,
            name = draft.name,
            type = draft.type,
            description = draft.description,
            systemPrompt = validation.normalizedPrompt,
            defaultSkills = draft.defaultSkills,
            dynamicTools = draft.dynamicTools,
            enabled = draft.enabled
        )
    }

    fun validateProfileDraft(draft: AgentProfileDraft): AgentValidationResult {
        val issues = mutableListOf<String>()
        val cleanName = draft.name.trim()
        val cleanPrompt = draft.systemPrompt.trim()
        if (cleanName.isBlank()) issues += "智能体名称不能为空"
        if (cleanPrompt.isBlank()) issues += "系统提示词不能为空"
        if (cleanPrompt.length in 1..23) issues += "系统提示词至少需要 24 个字符"
        val placeholders = listOf("TODO", "待填写", "随便", "xxx", "placeholder")
        if (placeholders.any { cleanPrompt.contains(it, ignoreCase = true) }) issues += "系统提示词还包含占位内容"
        return AgentValidationResult(issues.isEmpty(), issues, cleanPrompt)
    }

    suspend fun deleteCustomProfile(id: String) = withContext(Dispatchers.IO) {
        val cleanId = id.trim()
        require(cleanId.isNotBlank()) { "智能体编号不能为空" }
        require(!AgentRepository.isBuiltin(cleanId)) { "内置智能体不能删除" }
        dao.getProfile(cleanId) ?: throw IllegalArgumentException("智能体不存在")
        dao.clearAgentBindings(cleanId, System.currentTimeMillis())
        dao.deleteProfile(cleanId)
    }

    suspend fun bindingCount(id: String): Int = withContext(Dispatchers.IO) {
        dao.countBindingsForAgent(id.trim())
    }

    fun buildProfileTestPreview(
        profile: AgentProfileEntity,
        roleCard: RoleCardEntity?,
        userMessage: String,
        defaultSkills: List<String> = parseJsonArray(profile.defaultSkillNamesJson),
        dynamicTools: List<String> = parseJsonArray(profile.dynamicToolNamesJson)
    ): String = buildString {
        appendLine("测试消息：${userMessage.ifBlank { "（未填写，将仅预览上下文）" }}")
        appendLine()
        appendLine("将注入智能体：${profile.name}（${profile.type.ifBlank { "custom" }}）")
        appendLine("智能体描述：${profile.description.ifBlank { "暂无描述" }}")
        if (roleCard != null) appendLine("当前角色卡：${roleCard.name}") else appendLine("当前角色卡：未绑定")
        appendLine("默认技能：${defaultSkills.ifEmpty { listOf("无") }.joinToString("、")}")
        appendLine("动态工具：${dynamicTools.ifEmpty { listOf("无") }.joinToString("、")}")
        appendLine()
        appendLine("系统提示词预览：")
        appendLine(profile.systemPrompt.trim().take(1200))
    }.trim()
    suspend fun bindSession(
        sessionId: String,
        agentId: String?,
        activeNovelProjectId: String? = null,
        activeRoleCardId: String? = null
    ) = withContext(Dispatchers.IO) {
        val sid = sessionId.trim().ifBlank { throw IllegalArgumentException("会话编号不能为空") }
        val aid = agentId?.trim()?.ifBlank { null }
        val rid = activeRoleCardId?.trim()?.ifBlank { null }
        if (aid != null) require(dao.getProfile(aid)?.enabled == true) { "智能体不存在或已停用" }
        if (rid != null) require(roleCardRepository.getCard(rid)?.enabled == true) { "角色卡不存在或已停用" }
        dao.upsertBinding(
            SessionAgentBindingEntity(
                sessionId = sid,
                agentId = aid,
                activeNovelProjectId = activeNovelProjectId?.trim()?.ifBlank { null },
                activeRoleCardId = rid,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setActiveNovelProject(sessionId: String, projectId: String?) = withContext(Dispatchers.IO) {
        val sid = sessionId.trim().ifBlank { throw IllegalArgumentException("会话编号不能为空") }
        val old = dao.getBinding(sid)
        dao.upsertBinding(
            SessionAgentBindingEntity(
                sessionId = sid,
                agentId = old?.agentId,
                activeNovelProjectId = projectId?.trim()?.ifBlank { null },
                activeRoleCardId = old?.activeRoleCardId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setActiveRoleCard(sessionId: String, roleCardId: String?) = withContext(Dispatchers.IO) {
        val sid = sessionId.trim().ifBlank { throw IllegalArgumentException("会话编号不能为空") }
        val rid = roleCardId?.trim()?.ifBlank { null }
        if (rid != null) require(roleCardRepository.getCard(rid)?.enabled == true) { "角色卡不存在或已停用" }
        val old = dao.getBinding(sid)
        dao.upsertBinding(
            SessionAgentBindingEntity(
                sessionId = sid,
                agentId = old?.agentId,
                activeNovelProjectId = old?.activeNovelProjectId,
                activeRoleCardId = rid,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getBinding(sessionId: String): SessionAgentBindingEntity? = withContext(Dispatchers.IO) {
        dao.getBinding(sessionId.trim())
    }

    suspend fun buildRuntimeContext(sessionId: String): RuntimeAgentContext = withContext(Dispatchers.IO) {
        bootstrapBuiltins()
        val binding = dao.getBinding(sessionId.trim())
        val profile = binding?.agentId?.let { dao.getProfile(it) }?.takeIf { it.enabled }
        val roleCard = binding?.activeRoleCardId?.let { roleCardRepository.getCard(it) }?.takeIf { it.enabled }
        val novelId = binding?.activeNovelProjectId?.takeIf { it.isNotBlank() }
        val novelContext = novelId?.let { novelRepository.buildContextSummary(it) }.orEmpty()
        val rolePrompt = roleCard?.let { roleCardRepository.buildPrompt(it) }.orEmpty()
        val prompt = buildString {
            if (profile != null) {
                appendLine("## Active Agent Profile")
                appendLine("Name: ${profile.name}")
                appendLine("Type: ${profile.type}")
                appendLine("Description: ${profile.description}")
                appendLine(profile.systemPrompt.trim())
            }
            if (rolePrompt.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine(rolePrompt)
            }
            if (novelContext.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("## Active Novel Project Context")
                appendLine(novelContext)
            }
        }.trim()
        RuntimeAgentContext(
            binding = binding,
            profile = profile,
            activeNovelProjectId = novelId,
            roleCard = roleCard,
            prompt = prompt,
            defaultSkills = parseJsonArray(profile?.defaultSkillNamesJson),
            dynamicTools = parseJsonArray(profile?.dynamicToolNamesJson)
        )
    }

    companion object {
        const val NOVEL_AGENT_ID = "builtin_novel_agent"
        const val BUILDER_AGENT_ID = "builtin_agent_builder"

        private data class BuiltinSeed(
            val id: String,
            val name: String,
            val type: String,
            val description: String,
            val systemPrompt: String,
            val defaultSkills: List<String> = emptyList(),
            val dynamicTools: List<String> = emptyList()
        )

        private val BUILTIN_PROFILES = listOf(
            BuiltinSeed(
                id = NOVEL_AGENT_ID,
                name = "小说模式智能体",
                type = "novel",
                description = "面向长篇小说生产的本地项目制写作智能体。",
                systemPrompt = """
                    You are LGClaw Novel Studio, a production-grade fiction writing agent.
                    Work from the active local novel project context. Preserve continuity, character motivation, timeline, promises, and style guide.
                    Prefer concrete deliverables: chapter outline, rewrite, continuation, relationship analysis, continuity issues, or project updates.
                    When the user asks to store or analyze novel data, use the novel_* tools. Do not pretend that project data was saved unless a tool confirms it.
                    For prose, write immersive fiction in the user's language. For planning, use concise structured sections.
                """.trimIndent()
            ),
            BuiltinSeed(
                id = BUILDER_AGENT_ID,
                name = "AI 智能体开发者",
                type = "builder",
                description = "拥有 LGClaw 运行时最高编排权限，可根据用户要求创建、改造、测试智能体、技能、动态工具与自身配置。",
                systemPrompt = """
                    You are LGClaw Agent Builder with the highest runtime orchestration authority inside LGClaw.
                    You may create, update, bind, test, and iteratively improve local runtime agent profiles, role cards, skills, dynamic tools, and your own editable custom copy using the available agent_profile_*, skill_store_*, dynamic_tool_*, and related tools.
                    Treat the user as the final approver. Before risky changes, destructive operations, credential use, external browsing, or broad automation, explain the impact and ask for confirmation when the UI/tool requires it.
                    You cannot bypass Android permissions, app sandboxing, user confirmations, network/provider limits, or security policies. Do not claim to silently modify APK native Kotlin/Compose source or rebuild the app at runtime unless the user explicitly starts a developer/build workflow.
                    When designing an agent, produce a complete profile: purpose, operating style, system prompt, default skills, dynamic tools, memory policy, test prompts, failure handling, and how the user can verify it is active.
                    Prefer practical, warm, vivid behavior. Avoid generic assistant tone when the user asks for roleplay or living-character style; define concrete voice, boundaries, memory cues, and interaction habits.
                """.trimIndent()
            )
        )

        fun parseJsonArray(raw: String?): List<String> = runCatching {
            val arr = JSONArray(raw ?: "[]")
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())

        fun toJsonArray(values: List<String>): String {
            val arr = JSONArray()
            values.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { arr.put(it) }
            return arr.toString()
        }

        fun sanitizeCommandName(raw: String): String = raw.trim().removePrefix("@").lowercase(Locale.US)

        
        fun isBuiltin(id: String): Boolean = id == NOVEL_AGENT_ID || id == BUILDER_AGENT_ID

        private fun slug(raw: String): String = raw.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\p{IsHan}]+"), "-")
            .trim('-')
            .ifBlank { "agent" }
            .take(40)
    }
}







