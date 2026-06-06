package com.lgclaw.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ToolJobStatusTool : Tool {
    override val name: String = "tool_job_status"
    override val description: String =
        "Check a background tool job started by LGClaw. Use this after a long MCP/deep-research/crawl tool returns a job_id."
    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "job_id": {"type": "string", "description": "Background job id returned by a long tool."},
                  "recent": {"type": "boolean", "description": "When true, list recent background jobs instead."}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult {
        val args = runCatching { Json.decodeFromString<Args>(argumentsJson) }.getOrDefault(Args())
        if (args.recent == true || args.jobId.isNullOrBlank()) {
            val recent = ToolJobStore.listRecent()
            return ToolResult(
                toolCallId = "",
                content = if (recent.isEmpty()) {
                    "No background tool jobs."
                } else {
                    recent.joinToString("\n") { job ->
                        "- ${job.id} ${job.toolName} status=${job.status.wireName} updated=${job.updatedAt}"
                    }
                },
                isError = false,
                metadata = buildJsonObject {
                    put("count", recent.size)
                    put("jobs", buildJsonArray {
                        recent.forEach { add(jobMetadata(it)) }
                    })
                }
            )
        }
        val job = ToolJobStore.get(args.jobId) ?: return ToolResult(
            toolCallId = "",
            content = "Background tool job not found: ${args.jobId}",
            isError = true,
            metadata = buildJsonObject {
                put("job_id", args.jobId)
                put("status", "not_found")
            }
        )
        return ToolResult(
            toolCallId = "",
            content = formatJob(job),
            isError = job.status == ToolJobStatus.Failed,
            metadata = jobMetadata(job)
        )
    }

    private fun formatJob(job: ToolJobSnapshot): String = buildString {
        appendLine("job_id: ${job.id}")
        appendLine("tool: ${job.toolName}")
        appendLine("status: ${job.status.wireName}")
        appendLine("started_at: ${job.startedAt}")
        appendLine("updated_at: ${job.updatedAt}")
        if (job.finishedAt != null) appendLine("finished_at: ${job.finishedAt}")
        if (job.status == ToolJobStatus.Running) {
            appendLine("result: still running; call tool_job_status again in a later turn.")
        } else {
            appendLine("result:")
            appendLine(job.resultContent.ifBlank { "(no output)" })
        }
    }.trim()

    private fun jobMetadata(job: ToolJobSnapshot): JsonObject = buildJsonObject {
        put("job_id", job.id)
        put("tool_name", job.toolName)
        put("status", job.status.wireName)
        put("started_at", job.startedAt)
        put("updated_at", job.updatedAt)
        put("timeout_ms", job.timeoutMs)
        put("done", job.status != ToolJobStatus.Running)
        put("is_error", job.resultIsError)
    }

    @Serializable
    private data class Args(
        @kotlinx.serialization.SerialName("job_id")
        val jobId: String? = null,
        val recent: Boolean? = null
    )
}
