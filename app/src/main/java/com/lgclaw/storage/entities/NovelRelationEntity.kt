package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novel_relations",
    indices = [Index(value = ["projectId", "fromCharacterId", "toCharacterId", "label"], unique = true)]
)
data class NovelRelationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val fromCharacterId: String,
    val toCharacterId: String,
    val label: String,
    val weight: Double,
    val evidenceJson: String,
    val updatedAt: Long
)