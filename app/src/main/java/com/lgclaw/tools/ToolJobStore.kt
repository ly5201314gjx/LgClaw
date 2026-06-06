package com.lgclaw.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ToolJobStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, ToolJobSnapshot>()

    fun start(
        toolName: String,
        argumentsJson: String,
        timeoutMs: Long,
        runner: suspend () -> ToolResult
    ): ToolJobSnapshot {
        val now = System.currentTimeMillis()
        val id = "job_${now}_${UUID.randomUUID().toString().take(8)}"
        val initial = ToolJobSnapshot(
            id = id,
            toolName = toolName,
            status = ToolJobStatus.Running,
            startedAt = now,
            updatedAt = now,
            timeoutMs = timeoutMs,
            argumentsPreview = argumentsJson.take(500)
        )
        jobs[id] = initial
        scope.launch {
            val result = runCatching {
                withTimeout(timeoutMs.coerceAtLeast(1_000L)) {
                    withContext(Dispatchers.Default) { runner() }
                }
            }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    ToolResult(
                        toolCallId = "",
                        content = if (error is TimeoutCancellationException) {
                            "Background tool '$toolName' timed out after ${timeoutMs / 1000}s."
                        } else {
                            "Background tool '$toolName' failed: ${error.message ?: error.javaClass.simpleName}"
                        },
                        isError = true,
                        metadata = buildJsonObject {
                            put("error", error.javaClass.simpleName)
                        }
                    )
                }
            )
            val finished = System.currentTimeMillis()
            jobs[id] = initial.copy(
                status = if (result.isError) ToolJobStatus.Failed else ToolJobStatus.Completed,
                updatedAt = finished,
                finishedAt = finished,
                resultContent = result.content,
                resultIsError = result.isError,
                resultMetadata = result.metadata
            )
        }
        trimOldJobs()
        return initial
    }

    fun get(id: String): ToolJobSnapshot? = jobs[id.trim()]

    fun listRecent(limit: Int = 10): List<ToolJobSnapshot> =
        jobs.values.sortedByDescending { it.updatedAt }.take(limit.coerceIn(1, 50))

    private fun trimOldJobs() {
        val overflow = jobs.size - MAX_JOBS
        if (overflow <= 0) return
        jobs.values
            .sortedBy { it.updatedAt }
            .take(overflow)
            .forEach { jobs.remove(it.id) }
    }

    private const val MAX_JOBS = 80
}

enum class ToolJobStatus {
    Running,
    Completed,
    Failed;

    val wireName: String get() = name.lowercase(Locale.US)
}

data class ToolJobSnapshot(
    val id: String,
    val toolName: String,
    val status: ToolJobStatus,
    val startedAt: Long,
    val updatedAt: Long,
    val timeoutMs: Long,
    val argumentsPreview: String = "",
    val finishedAt: Long? = null,
    val resultContent: String = "",
    val resultIsError: Boolean = false,
    val resultMetadata: JsonObject? = null
)
