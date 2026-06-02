package com.lgclaw.ui

/**
 * Presentation-only models used by the chat transcript and media preview UI.
 */
data class UiMessage(
    val id: Long,
    val role: String,
    val content: String,
    val createdAt: Long,
    val isCollapsible: Boolean = false,
    val expandedContent: String? = null,
    val attachments: List<UiMediaAttachment> = emptyList(),
    val traceItems: List<UiInlineTrace> = emptyList(),
    val traceRunning: Boolean = false,
    val traceAnchorMessageId: Long? = null
)

data class UiMediaAttachment(
    val reference: String,
    val kind: UiMediaKind,
    val label: String,
    val mimeType: String = "",
    val fileId: String = "",
    val sizeBytes: Long = 0L,
    val previewText: String = "",
    val contentUri: String = ""
)

enum class UiMediaKind {
    Image,
    Video,
    Audio,
    Document
}

data class UiPendingAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val path: String,
    val sizeBytes: Long,
    val kind: UiMediaKind,
    val textPreview: String = ""
) {
    val marker: String
        get() = "[LGCLAW_ATTACHMENT:${if (kind == UiMediaKind.Image) "image" else "document"}|id=$id|name=$name|mime=$mimeType|path=$path|size=$sizeBytes]"
}
