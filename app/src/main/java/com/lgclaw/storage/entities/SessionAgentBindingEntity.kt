package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_agent_bindings")
data class SessionAgentBindingEntity(
    @PrimaryKey val sessionId: String,
    val agentId: String?,
    val activeNovelProjectId: String?,
    val activeRoleCardId: String?,
    val updatedAt: Long
)
