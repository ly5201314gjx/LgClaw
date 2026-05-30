package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novel_characters",
    indices = [Index(value = ["projectId", "name"], unique = true)]
)
data class NovelCharacterEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val aliasesJson: String,
    val goal: String,
    val secret: String,
    val arc: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)