package com.lgclaw.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per orchestrator run, identified by [runId]. [payloadJson] carries
 * the original plan steps so a stale or interrupted run can be reconstructed
 * after process restart.
 */
@Entity(tableName = "orchestrator_runs")
data class OrchestratorRunEntity(
    @PrimaryKey val runId: String,
    val sessionId: String,
    val planMode: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val payloadJson: String
)
