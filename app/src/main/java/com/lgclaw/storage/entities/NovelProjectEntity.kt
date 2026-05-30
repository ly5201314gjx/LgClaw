package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novel_projects")
data class NovelProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val genre: String,
    val styleGuide: String,
    val premise: String,
    val createdAt: Long,
    val updatedAt: Long
)