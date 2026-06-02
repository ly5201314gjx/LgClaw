package com.lgclaw.attachments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.lgclaw.config.AppSession
import com.lgclaw.storage.MessageRepository
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

fun createAttachmentToolSet(
    context: Context,
    currentSessionIdProvider: () -> String = { AppSession.LOCAL_SESSION_ID },
    messageRepository: MessageRepository? = null
): List<Tool> {
    val appContext = context.applicationContext
    val bridge = AttachmentBridge(appContext)
    return listOf(
        AttachmentImportUriTool(bridge),
        AttachmentImportPathTool(bridge),
        AttachmentReadTextTool(bridge),
        AttachmentPreviewTool(bridge),
        AttachmentSaveTextTool(bridge),
        AttachmentSaveBase64Tool(bridge),
        AttachmentSendTool(bridge, currentSessionIdProvider, messageRepository),
        AttachmentOpenWithTool(appContext, bridge),
        AttachmentListTool(bridge)
    )
}

private class AttachmentImportUriTool(private val bridge: AttachmentBridge) : Tool {
    override val name: String = "attachment_import_uri"
    override val description: String =
        "Import an Android content:// attachment into LGClaw private attachment storage."
    override val jsonSchema: JsonObject = schema(
        required = "[\"uri\"]",
        properties = """
        {
          "uri":{"type":"string"},
          "display_name":{"type":"string"},
          "mime_type":{"type":"string"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = toolJson.decodeFromString<ImportUriArgs>(argumentsJson)
        val ref = bridge.importFromUri(
            uri = Uri.parse(args.uri),
            displayNameOverride = args.display_name,
            mimeTypeOverride = args.mime_type
        )
        savedResult(ref, "附件已导入：${ref.displayName}", "attachment_import_uri")
    }

    @Serializable
    private data class ImportUriArgs(
        val uri: String,
        val display_name: String? = null,
        val mime_type: String? = null
    )
}

private class AttachmentImportPathTool(private val bridge: AttachmentBridge) : Tool {
    override val name: String = "attachment_import_path"
    override val description: String =
        "Import an app-private or locally accessible file path into LGClaw private attachment storage."
    override val jsonSchema: JsonObject = schema(
        required = "[\"path\"]",
        properties = """
        {
          "path":{"type":"string"},
          "display_name":{"type":"string"},
          "mime_type":{"type":"string"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = toolJson.decodeFromString<ImportPathArgs>(argumentsJson)
        val ref = bridge.importFromFile(
            sourcePath = args.path,
            displayNameOverride = args.display_name,
            mimeTypeOverride = args.mime_type
        )
        savedResult(ref, "本地文件已导入：${ref.displayName}", "attachment_import_path")
    }

    @Serializable
    private data class ImportPathArgs(
        val path: String,
        val display_name: String? = null,
        val mime_type: String? = null
    )
}

private class AttachmentReadTextTool(private val bridge: AttachmentBridge) : Tool {
    override val name: String = "attachment_read_text"
    override val description: String =
        "Read a text attachment by attachment_id. Supports text, json, csv, md, xml and html."
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
        "Get a lightweight preview of an attachment: image uri, text excerpt, or file metadata."
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
                    put("preview_type", preview.previewType)
                    put("mime_type", preview.mimeType)
                    put("display_name", preview.displayName)
                    put("size", preview.size)
                    put("uri", preview.contentUri)
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
        "Save generated text as a local chat attachment that the user can open or send."
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
        savedResult(ref, "文本附件已保存：${ref.displayName}", "attachment_save_text")
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
        savedResult(ref, "附件已保存：${ref.displayName}", "attachment_save_base64")
    }

    @Serializable
    private data class SaveBase64Args(val file_name: String, val mime_type: String, val base64: String)
}

private class AttachmentSendTool(
    private val bridge: AttachmentBridge,
    private val currentSessionIdProvider: () -> String,
    private val messageRepository: MessageRepository?
) : Tool {
    override val name: String = "attachment_send"
    override val description: String =
        "Show an imported attachment inside the current LGClaw chat session as a visible assistant message. For remote channels, call the message tool with media=[local_path or content_uri] after saving/importing the attachment."
    override val jsonSchema: JsonObject = schema(
        required = "[\"attachment_id\"]",
        properties = """
        {
          "attachment_id":{"type":"string"},
          "session_id":{"type":"string"},
          "caption":{"type":"string"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val repo = messageRepository ?: return@withContext ToolResult(
            "", "当前运行时没有可写入的聊天会话仓库。", true,
            buildJsonObject { put("error", "message_repository_unavailable") }
        )
        val args = toolJson.decodeFromString<SendArgs>(argumentsJson)
        val ref = bridge.getRef(args.attachment_id)
        val sessionId = args.session_id?.takeIf { it.isNotBlank() }
            ?: currentSessionIdProvider().ifBlank { AppSession.LOCAL_SESSION_ID }
        val caption = args.caption?.takeIf { it.isNotBlank() } ?: "已发送附件：${ref.displayName}"
        val content = buildString {
            appendLine(caption)
            appendLine()
            appendLine(bridge.markerFor(ref))
        }.trim()
        repo.appendAssistantMessage(sessionId, content)
        ToolResult("", "附件已发送到当前聊天：${ref.displayName}\n如需发送到外部渠道，请调用 message 工具并传 media=[\"${ref.localUri}\"]。", false, buildJsonObject {
            put("action", "attachment_send")
            put("attachment_id", ref.id)
            put("display_name", ref.displayName)
            put("mime_type", ref.mimeType)
            put("local_path", ref.localUri)
            put("content_uri", ref.contentUri)
            put("message_media_hint", ref.localUri)
        })
    }

    @Serializable
    private data class SendArgs(
        val attachment_id: String,
        val session_id: String? = null,
        val caption: String? = null
    )
}

private class AttachmentOpenWithTool(
    private val context: Context,
    private val bridge: AttachmentBridge
) : Tool {
    override val name: String = "attachment_open_with"
    override val description: String = "Open an attachment with Android system apps."
    override val jsonSchema: JsonObject = schema(
        required = "[\"attachment_id\"]",
        properties = """{"attachment_id":{"type":"string"}}"""
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = toolJson.decodeFromString<OpenArgs>(argumentsJson)
        val ref = bridge.getRef(args.attachment_id)
        val uri = bridge.getShareUri(ref.id)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ref.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "打开附件")) }.fold(
            onSuccess = { savedResult(ref, "已请求系统应用打开附件：${ref.displayName}", "attachment_open_with") },
            onFailure = { t ->
                ToolResult("", "打开附件失败：${t.message ?: t.javaClass.simpleName}", true, buildJsonObject {
                    put("action", "attachment_open_with")
                    put("attachment_id", ref.id)
                    put("error", t.message ?: t.javaClass.simpleName)
                })
            }
        )
    }

    @Serializable
    private data class OpenArgs(val attachment_id: String)
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

private fun savedResult(ref: AttachmentRef, message: String, action: String): ToolResult {
    return ToolResult("", message, false, buildJsonObject {
        put("action", action)
        put("attachment_id", ref.id)
        put("display_name", ref.displayName)
        put("file_name", ref.displayName)
        put("mime_type", ref.mimeType)
        put("size", ref.size)
        put("size_bytes", ref.size)
        put("local_uri", ref.localUri)
        put("content_uri", ref.contentUri)
        put("path", ref.localUri)
        put("uri", ref.contentUri)
        put("preview_type", ref.previewType)
        put("kind", ref.previewType)
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
