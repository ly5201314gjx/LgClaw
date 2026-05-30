package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novel_chapters",
    indices = [Index(value = ["projectId", "chapterIndex"], unique = true)]
)
data class NovelChapterEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val chapterIndex: Int,
    val title: String,
    val content: String,
    val summary: String,
    val keywordsJson: String,
    val createdAt: Long,
    val updatedAt: Long
)