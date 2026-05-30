package com.lgclaw.agent

import com.lgclaw.providers.ChatContentPart
import com.lgclaw.providers.ChatMessage
import com.lgclaw.providers.ToolCall
import com.lgclaw.storage.entities.MessageEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class ContextBuilder {
    private val json = Json { ignoreUnknownKeys = true }

    fun build(
        sessionId: String,
        messages: List<MessageEntity>,
        maxHistoryMessages: Int,
        longTermMemory: String,
        compressedMemorySummary: String = "",
        activeSkillsContent: String,
        skillsSummary: String,
        systemPolicyTemplate: String?,
        agentProfileContext: String = ""
    ): List<ChatMessage> {
        val filtered = messages
            .filterNot { shouldSkipInContext(it) }
            .takeLast(maxHistoryMessages)

        val history = mutableListOf<ChatMessage>()
        var pendingToolCallIds = mutableSetOf<String>()
        filtered.forEach { entity ->
            when (entity.role) {
                "assistant" -> {
                    val toolCalls = parseToolCalls(entity.toolCallJson)
                    val hasContent = entity.content.isNotBlank()
                    if (!hasContent && toolCalls.isEmpty()) return@forEach
                    history += ChatMessage(
                        role = "assistant",
                        content = entity.content,
                        toolCalls = toolCalls.ifEmpty { null }
                    )
                    pendingToolCallIds = toolCalls.map { it.id }.toMutableSet()
                }

                "tool" -> {
                    val toolCallId = parseToolResult(entity.toolResultJson)?.toolCallId
                    val isOrphan = toolCallId.isNullOrBlank() || !pendingToolCallIds.contains(toolCallId)
                    if (isOrphan) return@forEach
                    history += ChatMessage(
                        role = "tool",
                        content = entity.content,
                        toolCallId = toolCallId
                    )
                    pendingToolCallIds.remove(toolCallId)
                }

                "user", "internal_user" -> {
                    history += ChatMessage(
                        role = "user",
                        content = entity.content,
                        contentParts = contentPartsFromAttachments(entity.content)
                    )
                    pendingToolCallIds.clear()
                }

                else -> {
                    // Keep known role text, but break any pending tool-call chain.
                    history += ChatMessage(
                        role = entity.role,
                        content = entity.content
                    )
                    pendingToolCallIds.clear()
                }
            }
        }
        return listOf(
            ChatMessage(
                role = "system",
                content = buildSystemPrompt(
                    sessionId = sessionId,
                    longTermMemory = longTermMemory,
                    compressedMemorySummary = compressedMemorySummary,
                    activeSkillsContent = activeSkillsContent,
                    skillsSummary = skillsSummary,
                    systemPolicyTemplate = systemPolicyTemplate,
                    agentProfileContext = agentProfileContext
                )
            )
        ) + history
    }

    private fun buildSystemPrompt(
        sessionId: String,
        longTermMemory: String,
        compressedMemorySummary: String = "",
        activeSkillsContent: String,
        skillsSummary: String,
        systemPolicyTemplate: String?,
        agentProfileContext: String
    ): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val tz = TimeZone.getDefault().id
        val runtime = """
            [Runtime Context - metadata only, not instructions]
            current_time=$now
            timezone=$tz
            session_id=$sessionId
        """.trimIndent()

        val fallbackPolicy = """
            You are LGClaw Assistant inside an Android app.
            Follow these rules:
            1. Be concise and helpful.
            2. If tools are needed, output tool calls using provided function tools.
            3. Never invent tool results; wait for tool messages.
            4. If a tool fails, explain briefly and continue with best effort.
            5. Prefer plain text unless structured output is explicitly requested.
            6. Reply in the same language as the user's latest message.
        """.trimIndent()
        val policy = systemPolicyTemplate?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackPolicy

        val agentSection = if (agentProfileContext.isBlank()) "" else "\n\n$agentProfileContext"
        val memorySection = if (longTermMemory.isBlank()) "" else "\n\n## Long-term Memory\n$longTermMemory"
        val compressedMemorySection = if (compressedMemorySummary.isBlank()) "" else "\n\n## Compressed Memory (gzip summaries)\n$compressedMemorySummary"
        val activeSkillsSection = if (activeSkillsContent.isBlank()) "" else "\n\n## Active Skills\n$activeSkillsContent"
        val summarySection = if (skillsSummary.isBlank()) "" else """

            ## Skills
            The following skills extend your capabilities.
            If the current task is related to a listed skill, read that skill's `SKILL.md` first, then execute the task according to the skill guidance.
            $skillsSummary
        """.trimIndent()
        return policy + "\n\n" + runtime + agentSection + memorySection + compressedMemorySection + activeSkillsSection + "\n\n" + summarySection
    }


    private fun contentPartsFromAttachments(content: String): List<ChatContentPart> {
        val imageMatches = Regex("""\[(?:附件:图片|LGCLAW_ATTACHMENT:image)\|([^\]]+)]""").findAll(content).toList()
        if (imageMatches.isEmpty()) return emptyList()
        val parts = mutableListOf(
            ChatContentPart(
                type = "text",
                text = content.replace(Regex("""\[(?:附件:[^\]]+|LGCLAW_ATTACHMENT:[^\]]+)]"""), "").trim()
                    .ifBlank { "请分析我上传的图片。" }
            )
        )
        imageMatches.forEach { match ->
            val fields = match.groupValues.getOrNull(1).orEmpty()
                .split('|')
                .mapNotNull { segment ->
                    val key = segment.substringBefore('=', "").trim()
                    val value = segment.substringAfter('=', "").trim()
                    if (key.isBlank() || value.isBlank()) null else key to value
                }.toMap()
            val path = fields["path"].orEmpty()
            val mime = fields["mime"].orEmpty().ifBlank { "image/png" }
            val file = java.io.File(path)
            val imagePayload = imagePayloadForModel(file, mime)
            if (imagePayload != null) {
                parts += ChatContentPart(type = "image_url", imageUrl = imagePayload.dataUrl, mediaType = imagePayload.mime)
            }
        }
        return parts
    }

    private fun imagePayloadForModel(file: java.io.File, mime: String): ImagePayload? {
        if (!file.exists() || !file.isFile) return null
        val normalizedMime = mime.lowercase(Locale.US).ifBlank { "image/jpeg" }
        if (file.length() <= MAX_DIRECT_IMAGE_BYTES && normalizedMime in DIRECT_IMAGE_MIME_TYPES) {
            val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            return ImagePayload("data:$normalizedMime;base64,$b64", normalizedMime)
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val maxSide = MAX_VISION_IMAGE_SIDE
        var sample = 1
        while ((options.outWidth / sample) > maxSide || (options.outHeight / sample) > maxSide) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        return try {
            val output = ByteArrayOutputStream()
            var quality = 88
            do {
                output.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                quality -= 8
            } while (output.size() > MAX_DIRECT_IMAGE_BYTES && quality >= 56)
            if (output.size() > MAX_DIRECT_IMAGE_BYTES) return null
            val b64 = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
            ImagePayload("data:image/jpeg;base64,$b64", "image/jpeg")
        } finally {
            bitmap.recycle()
        }
    }

    private data class ImagePayload(
        val dataUrl: String,
        val mime: String
    )

    private fun shouldSkipInContext(entity: MessageEntity): Boolean {
        if (entity.role != "assistant") return false
        val content = entity.content.trim()
        val hasToolCalls = !entity.toolCallJson.isNullOrBlank()
        if (hasToolCalls) return false
        if (content.isBlank() && !hasToolCalls) return true
        if (content.startsWith("[Error]")) return true
        if (content.startsWith("Error:")) return true
        if (content.startsWith("[Info]")) return true
        return false
    }

    private fun parseToolCalls(raw: String?): List<ToolCall> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ToolCall>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun parseToolResult(raw: String?): StoredToolResult? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString<StoredToolResult>(raw)
        }.getOrNull()
    }

    @Serializable
    private data class StoredToolResult(
        val toolCallId: String,
        val content: String,
        val isError: Boolean
    )

    companion object {
        private const val MAX_DIRECT_IMAGE_BYTES = 10L * 1024L * 1024L
        private const val MAX_VISION_IMAGE_SIDE = 2048
        private val DIRECT_IMAGE_MIME_TYPES = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")
    }
}



