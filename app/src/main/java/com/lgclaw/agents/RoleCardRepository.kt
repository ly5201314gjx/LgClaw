package com.lgclaw.agents

import com.lgclaw.storage.dao.RoleCardDao
import com.lgclaw.storage.entities.RoleCardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class RoleCardRepository(
    private val dao: RoleCardDao
) {
    suspend fun listCards(): List<RoleCardEntity> = withContext(Dispatchers.IO) {
        dao.listCards()
    }

    suspend fun listEnabledCards(): List<RoleCardEntity> = withContext(Dispatchers.IO) {
        dao.listEnabledCards()
    }

    suspend fun getCard(id: String): RoleCardEntity? = withContext(Dispatchers.IO) {
        dao.getCard(id.trim())
    }

    suspend fun createOrUpdate(
        id: String?,
        name: String,
        avatarSymbol: String,
        description: String,
        persona: String,
        speakingStyle: String,
        boundaries: String,
        scenario: String,
        exampleDialog: String,
        enabled: Boolean = true
    ): RoleCardEntity = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifBlank { throw IllegalArgumentException("角色卡名称不能为空") }
        val cleanPersona = persona.trim().ifBlank { throw IllegalArgumentException("角色设定不能为空") }
        require(cleanPersona.length >= 12) { "角色设定太短，请至少写清身份、性格或行为方式" }
        val now = System.currentTimeMillis()
        val cleanId = id?.trim()?.ifBlank { null } ?: "role_${slug(cleanName)}_${UUID.randomUUID().toString().take(8)}"
        val old = dao.getCard(cleanId)
        val card = RoleCardEntity(
            id = cleanId,
            name = cleanName.take(60),
            avatarSymbol = avatarSymbol.trim().ifBlank { "角" }.take(4),
            description = description.trim().take(300),
            persona = cleanPersona.take(4000),
            speakingStyle = speakingStyle.trim().take(1600),
            boundaries = boundaries.trim().take(1600),
            scenario = scenario.trim().take(1600),
            exampleDialog = exampleDialog.trim().take(2400),
            enabled = enabled,
            createdAt = old?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsert(card)
        card
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id.trim(), enabled, System.currentTimeMillis())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id.trim())
    }

    fun buildPrompt(card: RoleCardEntity): String = buildString {
        appendLine("## Active Role Card")
        appendLine("Name: ${card.name}")
        if (card.description.isNotBlank()) appendLine("Description: ${card.description}")
        appendLine()
        appendLine("Role persona:")
        appendLine(card.persona.trim())
        if (card.speakingStyle.isNotBlank()) {
            appendLine()
            appendLine("Speaking style:")
            appendLine(card.speakingStyle.trim())
        }
        if (card.scenario.isNotBlank()) {
            appendLine()
            appendLine("Current scene and relationship:")
            appendLine(card.scenario.trim())
        }
        if (card.boundaries.isNotBlank()) {
            appendLine()
            appendLine("Boundaries:")
            appendLine(card.boundaries.trim())
        }
        if (card.exampleDialog.isNotBlank()) {
            appendLine()
            appendLine("Example dialogue:")
            appendLine(card.exampleDialog.trim())
        }
        appendLine()
        appendLine("When this role card is active, answer in character by default. Keep the voice natural, concrete, and emotionally present. Do not mention that you are an AI, a model, a prompt, or roleplaying unless the user directly asks about system behavior. Avoid generic assistant phrasing and keep continuity with the user's latest message.")
    }.trim()

    private fun slug(raw: String): String = raw.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9\\p{IsHan}]+"), "-")
        .trim('-')
        .ifBlank { "card" }
        .take(40)
}
