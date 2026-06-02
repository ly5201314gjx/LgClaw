package com.lgclaw.trace

import java.util.Locale

object TraceNoteExtractor {
    private const val MAX_VISIBLE_NOTE_CHARS = 1200
    private const val MAX_TRACE_CHARS = 1200
    private const val MAX_RAW_PREVIEW_CHARS = 1800

    private val mojibakeHints = listOf(
        "\uFFFD", "\u9420", "\u7ED7", "\u7487", "\u59AF",
        "\u5BB8", "\u93B6", "\u7F01", "\u95BA", "\u9286"
    )
    private val markdownNoise = Regex("[`*#>]+")
    private val controlChars = Regex("[\\p{Cntrl}&&[^\\n\\t]]")
    private val horizontalWhitespace = Regex("[ \\t\\x0B\\f\\r]+")
    private val publicNoteMarker = Regex("(?i)\\b(?:assistant_note|think|thought|note)\\s*[:\\uFF1A]")
    private val nextSectionMarker = Regex(
        "(?i)^(?:tool call|tool result|arguments|status|name=|call_id=|result|metadata|stdout|stderr|assistant_note\\s*[:\\uFF1A]|think\\s*[:\\uFF1A]|thought\\s*[:\\uFF1A]|note\\s*[:\\uFF1A]).*$"
    )

    fun extractPublicNotes(text: String?): List<String> {
        return extractPublicNotes(text, MAX_VISIBLE_NOTE_CHARS)
    }

    fun extractPublicNotesFull(text: String?): List<String> {
        return extractPublicNotes(text, MAX_TRACE_CHARS * 8)
    }

    private fun extractPublicNotes(text: String?, maxChars: Int): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val matches = publicNoteMarker.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val rawChunk = text.substring(start, end)
            val note = rawChunk
                .lineSequence()
                .takeWhile { line ->
                    val trimmed = line.trim()
                    trimmed.isBlank() || !trimmed.matches(nextSectionMarker)
                }
                .joinToString("\n")
            cleanTraceText(note, maxChars).takeIf { it.isNotBlank() }
        }.distinct()
    }

    fun extractAssistantNote(text: String?): String? {
        return extractPublicNotes(text).joinToString("\n").takeIf { it.isNotBlank() }
    }

    fun extractAssistantNoteFull(text: String?): String? {
        return extractPublicNotesFull(text).joinToString("\n").takeIf { it.isNotBlank() }
    }

    fun cleanTraceText(text: String?, maxChars: Int = MAX_TRACE_CHARS): String {
        if (text.isNullOrBlank()) return ""
        val normalized = text
            .replace("\r", "\n")
            .replace(Regex("```[\\s\\S]*?```")) { match ->
                match.value
                    .replace(Regex("(?i)```[a-z0-9_-]*"), "")
                    .replace("```", "")
            }
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.matches(publicNoteMarker) }
            .joinToString("\n")
            .replace(controlChars, "")
            .replace(markdownNoise, "")
            .replace(horizontalWhitespace, " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        val withoutMojibake = if (looksMojibake(normalized)) "" else normalized
        return withoutMojibake.take(maxChars).trim()
    }

    fun rawPreview(text: String?): String {
        return cleanTraceText(text, MAX_RAW_PREVIEW_CHARS)
    }

    fun displayDetail(title: String, detail: String, fallback: String = "正在处理任务"): String {
        val note = extractAssistantNote(detail)
        if (!note.isNullOrBlank()) return note
        val cleanedDetail = cleanTraceText(detail)
        if (cleanedDetail.isNotBlank()) return cleanedDetail
        val cleanedTitle = cleanTraceText(title, 80)
        return cleanedTitle.ifBlank { fallback }
    }

    fun sourceType(title: String, detail: String): String {
        val text = "${title.lowercase(Locale.US)} ${detail.lowercase(Locale.US)}"
        return when {
            "terminal_exec" in text || "terminal" in text || "终端" in text -> "终端"
            "skill" in text || "技能" in text -> "技能"
            "tool" in text || "工具" in text -> "工具"
            "model" in text || "模型" in text || "请求" in text -> "模型"
            "plan" in text || "计划" in text -> "计划"
            else -> "状态"
        }
    }

    fun sourceName(title: String, detail: String): String {
        val cleanedTitle = cleanTraceText(title, 80)
        val cleanedDetail = cleanTraceText(detail, 180)
        return when {
            cleanedTitle.startsWith("使用工具") -> cleanedDetail.substringBefore("，").substringBefore(" ").ifBlank { "工具" }
            cleanedTitle.startsWith("工具结果") -> cleanedDetail.substringBefore(" ").substringBefore("·").ifBlank { "工具" }
            cleanedTitle.startsWith("工具思考") -> "公开说明"
            cleanedTitle.startsWith("读取技能") -> cleanedDetail.ifBlank { "技能" }
            cleanedTitle.startsWith("终端") -> cleanedDetail.ifBlank { "终端" }
            cleanedTitle.isNotBlank() -> cleanedTitle
            else -> sourceType(title, detail)
        }
    }

    private fun looksMojibake(text: String): Boolean {
        if (text.isBlank()) return false
        val hitCount = mojibakeHints.count { it in text }
        if (hitCount >= 3) return true
        val cjkCount = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val suspicious = text.count {
            it == '\uFFFD' || it == '\u20AC' || it == '\u2122' || it == '\u0153'
        }
        return cjkCount > 10 && suspicious >= 2
    }
}
