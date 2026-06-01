package com.lgclaw.attachments

import android.content.Context
import android.util.Base64
import com.lgclaw.tools.Tool
import com.lgclaw.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun createAttachmentToolSet(context: Context): List<Tool> {
    val bridge = AttachmentBridge(context)
    return listOf(
        AttachmentReadTextTool(bridge),
        AttachmentPreviewTool(bridge),
        AttachmentSaveTextTool(bridge),
        AttachmentSaveBase64Tool(bridge),
        AttachmentListTool(bridge)
    )
}

private class AttachmentReadTextTool(private val bridge: AttachmentBridge) : Tool {
    override val name: String = "attachment_read_text"
    override val description: String =
        "Read a user-uploaded or app-saved text attachment by attachment_id. Supports text, json, csv, md, xml and html."
    override val jsonSchema: JsonObject = schema(
        required = "[\"attachment_id\"]",
        properties = """
        {
          "attachment_id":{"type":"string"},
          "max_chars":{"type":"integer","minimum":0}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = toolJson.decodeFromString<ReadTextArgs>(argumentsJson)
        runCatching {
            bridge.readText(args.attachment_id, args.max_chars ?: 12_000)
        }.fold(
            onSuccess = { text ->
                ToolResult("", text, false, buildJsonObject {
                    put("action", "attachment_read_text")
                    put("attachment_id", args.attachment_id)
                    put("chars", text.length)
                })
            },
            onFailure = { t ->
                ToolResult("", "附件读取失败：${t.message ?: t.javaClass.simpleName}", true, buildJsonObject {
                    put("action", "attachment_read_text")
                    put("attachment_id", args.attachment_id)
                    put("error", t.message ?: t.javaClass.simpleName)
                })
            }
        )
    }

    @Serializable
    private data class ReadTextArgs(val attachment_id: String, val max_chars: Int? = null)
}

private class AttachmentPreviewTool(private val bridge: AttachmentBridge) : Tool {
    override val name: String = "attachment_preview"
    override val description: String =
        "Get a lightweight preview of an attachment: image thumbnail uri, text excerpt, or file metadata."
    override val jsonSchema: JsonObject = schema(
        required = "[\"attachment_id\"]",
        properties = """{"attachment_id":{"type":"string"}}"""
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = toolJson.decodeFromString<PreviewArgs>(argumentsJson)
        runCatching { bridge.generatePreview(args.attachment_id) }.fold(
            onSuccess = { preview ->
                ToolResult("", toolJson.encodeToString(preview), false, buildJsonObject {
                    put("action", "attachment_preview")
                    put("attachment_id", preview.attachmentId)
                    put("kind", preview.kind)
                    put("mime_type", preview.mimeType)
                    put("file_name", preview.fileName)
                    put("size_bytes", preview.sizeBytes)
                    preview.thumbnailUri?.let { put("uri", it) }
                })
            },
            onFailure = { t ->
                ToolResult("", "附件预览失败：${t.message ?: t.javaClass.simpleName}", true, buildJsonObject {
                    put("action", "attachment_preview")
                    put("attachment_id", args.attachment_id)
                    put("error", t.message ?: t.javaClass.simpleName)
                })
            }
        )
    }

    @Serializable
    private data class PreviewArgs(val attachment_id: String)
}

private class AttachmentSaveTextTool(private val bridge: AttachmentBridge) : Tool {
    override val name: String = "attachment_save_text"
    override val description: String =
        "Save generated text as a local chat attachment that the user can open or share."
    override val jsonSchema: JsonObject = schema(
        required = "[\"file_name\",\"text\"]",
        properties = """
        {
          "file_name":{"type":"string"},
          "mime_type":{"type":"string"},
          "text":{"type":"string"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = toolJson.decodeFromString<SaveTextArgs>(argumentsJson)
        val mime = args.mime_type?.takeIf { it.isNotBlank() } ?: "text/plain"
        val ref = bridge.saveBytes(args.file_name, mime, args.text.toByteArray(Charsets.UTF_8))
        savedResult(ref, "文本附件已保存：${ref.fileName}")
    }

    @Serializable
    private data class SaveTextArgs(val file_name: String, val mime_type: String? = null, val text: String)
}

private class AttachmentSaveBase64Tool(private val bridge: AttachmentBridge) : Tool {
    override val name: String = "attachment_save_base64"
    override val description: String =
        "Save generated binary data as a local chat attachment from base64 content."
    override val jsonSchema: JsonObject = schema(
        required = "[\"file_name\",\"mime_type\",\"base64\"]",
        properties = """
        {
          "file_name":{"type":"string"},
          "mime_type":{"type":"string"},
          "base64":{"type":"string"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = toolJson.decodeFromString<SaveBase64Args>(argumentsJson)
        val bytes = Base64.decode(args.base64, Base64.DEFAULT)
        val ref = bridge.saveBytes(args.file_name, args.mime_type, bytes)
        savedResult(ref, "附件已保存：${ref.fileName}")
    }

    @Serializable
    private data class SaveBase64Args(val file_name: String, val mime_type: String, val base64: String)
}

private class AttachmentListTool(private val bridge: AttachmentBridge) : Tool {
    override val name: String = "attachment_list"
    override val description: String = "List recent local attachments available to this app."
    override val jsonSchema: JsonObject = schema(
        required = "[]",
        properties = """{"limit":{"type":"integer","minimum":1}}"""
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = toolJson.decodeFromString<ListArgs>(argumentsJson)
        val refs = bridge.listRefs(args.limit ?: 50)
        ToolResult("", toolJson.encodeToString(refs), false, buildJsonObject {
            put("action", "attachment_list")
            put("count", refs.size)
        })
    }

    @Serializable
    private data class ListArgs(val limit: Int? = null)
}

private fun savedResult(ref: AttachmentRef, message: String): ToolResult {
    return ToolResult("", message, false, buildJsonObject {
        put("action", "attachment_save")
        put("attachment_id", ref.id)
        put("path", ref.path)
        put("file_name", ref.fileName)
        put("mime_type", ref.mimeType)
        put("size_bytes", ref.sizeBytes)
        put("kind", ref.kind)
    })
}

private fun schema(required: String, properties: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("required", Json.parseToJsonElement(required))
    put("properties", Json.parseToJsonElement(properties.trimIndent()))
}

private val toolJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}
