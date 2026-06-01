package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_profiles")
data class AgentProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val description: String,
    val systemPrompt: String,
    val enabled: Boolean,
    val defaultSkillNamesJson: String,
    val dynamicToolNamesJson: String,
    val avatarPresetKey: String = "",
    val avatarImagePath: String = "",
    val avatarCropJson: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
