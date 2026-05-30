package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novel_world_notes",
    indices = [Index(value = ["projectId", "category"])]
)
data class NovelWorldNoteEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val category: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)