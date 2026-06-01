package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "role_cards")
data class RoleCardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarSymbol: String,
    val description: String,
    val persona: String,
    val speakingStyle: String,
    val boundaries: String,
    val scenario: String,
    val exampleDialog: String,
    val avatarPresetKey: String = "",
    val avatarImagePath: String = "",
    val avatarCropJson: String = "",
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
