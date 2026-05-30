package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novel_analyses",
    indices = [Index(value = ["projectId", "createdAt"])]
)
data class NovelAnalysisEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val kind: String,
    val title: String,
    val summary: String,
    val payloadJson: String,
    val createdAt: Long
)