package com.lgclaw.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lgclaw.storage.entities.RoleCardEntity

@Dao
interface RoleCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: RoleCardEntity)

    @Query("SELECT * FROM role_cards ORDER BY enabled DESC, updatedAt DESC, name COLLATE NOCASE ASC")
    suspend fun listCards(): List<RoleCardEntity>

    @Query("SELECT * FROM role_cards WHERE enabled = 1 ORDER BY updatedAt DESC, name COLLATE NOCASE ASC")
    suspend fun listEnabledCards(): List<RoleCardEntity>

    @Query("SELECT * FROM role_cards WHERE id = :id LIMIT 1")
    suspend fun getCard(id: String): RoleCardEntity?

    @Query("UPDATE role_cards SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE role_cards SET avatarPresetKey = :presetKey, avatarImagePath = :imagePath, avatarCropJson = :cropJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAvatar(id: String, presetKey: String, imagePath: String, cropJson: String, updatedAt: Long)

    @Query("DELETE FROM role_cards WHERE id = :id")
    suspend fun delete(id: String)
}
