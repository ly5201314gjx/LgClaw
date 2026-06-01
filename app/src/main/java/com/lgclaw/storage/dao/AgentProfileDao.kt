package com.lgclaw.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lgclaw.storage.entities.AgentProfileEntity
import com.lgclaw.storage.entities.SessionAgentBindingEntity

@Dao
interface AgentProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: AgentProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(profiles: List<AgentProfileEntity>)

    @Query("SELECT * FROM agent_profiles ORDER BY enabled DESC, updatedAt DESC, name COLLATE NOCASE ASC")
    suspend fun listProfiles(): List<AgentProfileEntity>

    @Query("SELECT * FROM agent_profiles WHERE enabled = 1 ORDER BY updatedAt DESC, name COLLATE NOCASE ASC")
    suspend fun listEnabledProfiles(): List<AgentProfileEntity>

    @Query("SELECT * FROM agent_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfile(id: String): AgentProfileEntity?

    @Query("UPDATE agent_profiles SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setProfileEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE agent_profiles SET avatarPresetKey = :presetKey, avatarImagePath = :imagePath, avatarCropJson = :cropJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProfileAvatar(id: String, presetKey: String, imagePath: String, cropJson: String, updatedAt: Long)

    @Query("DELETE FROM agent_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("SELECT COUNT(*) FROM session_agent_bindings WHERE agentId = :agentId")
    suspend fun countBindingsForAgent(agentId: String): Int

    @Query("UPDATE session_agent_bindings SET agentId = NULL, activeNovelProjectId = NULL, updatedAt = :updatedAt WHERE agentId = :agentId")
    suspend fun clearAgentBindings(agentId: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBinding(binding: SessionAgentBindingEntity)

    @Query("SELECT * FROM session_agent_bindings WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBinding(sessionId: String): SessionAgentBindingEntity?

    @Query("SELECT * FROM session_agent_bindings")
    suspend fun listBindings(): List<SessionAgentBindingEntity>

    @Query("DELETE FROM session_agent_bindings WHERE sessionId = :sessionId")
    suspend fun clearBinding(sessionId: String)
}
