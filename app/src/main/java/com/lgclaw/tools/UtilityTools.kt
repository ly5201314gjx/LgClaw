package com.lgclaw.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import kotlin.math.absoluteValue

fun createUtilityToolSet(): List<Tool> = listOf(
    UtilityTextStatsTool(),
    UtilityTextTransformTool(),
    UtilityJsonTool(),
    UtilityEncodingTool(),
    UtilityHashTool(),
    UtilityTimeTool(),
    UtilityRandomTool(),
    UtilityChecklistTool()
)

private val utilityJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

private fun schema(required: String, propertiesJson: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("required", Json.parseToJsonElement(required))
    put("properties", Json.parseToJsonElement(propertiesJson.trimIndent()))
}

private class UtilityTextStatsTool : Tool {
    override val name: String = "utility_text_stats"
    override val description: String =
        "Analyze text locally: character count, word count, line count, byte count, reading time, and top terms."

    override val jsonSchema: JsonObject = schema(
        "[\"text\"]",
        """
        {
          "text":{"type":"string","description":"Text to analyze"},
          "top_terms":{"type":"integer","description":"Number of frequent terms to include, 0-20"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = utilityJson.decodeFromString<StatsArgs>(argumentsJson)
        val text = args.text
        val lines = if (text.isEmpty()) 0 else text.lines().size
        val words = WORD_REGEX.findAll(text).map { it.value }.toList()
        val topN = (args.top_terms ?: 10).coerceIn(0, 20)
        val topTerms = words.asSequence()
            .map { it.lowercase(Locale.US) }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(topN)

        val content = buildString {
            appendLine("characters=${text.length}")
            appendLine("characters_no_spaces=${text.count { !it.isWhitespace() }}")
            appendLine("words=${words.size}")
            appendLine("lines=$lines")
            appendLine("bytes_utf8=${text.toByteArray(Charsets.UTF_8).size}")
            appendLine("estimated_reading_minutes=${((words.size + 199) / 200).coerceAtLeast(1)}")
            if (topTerms.isNotEmpty()) {
                appendLine()
                appendLine("top_terms:")
                topTerms.forEach { appendLine("- ${it.key}: ${it.value}") }
            }
        }.trim()
        ok(content)
    }

    @Serializable
    private data class StatsArgs(val text: String, val top_terms: Int? = null)
}

private class UtilityTextTransformTool : Tool {
    override val name: String = "utility_text_transform"
    override val description: String =
        "Transform text locally: trim, uppercase, lowercase, title_case, slug, dedupe_lines, sort_lines, reverse_lines, remove_empty_lines."

    override val jsonSchema: JsonObject = schema(
        "[\"text\",\"action\"]",
        """
        {
          "text":{"type":"string"},
          "action":{"type":"string","enum":["trim","uppercase","lowercase","title_case","slug","dedupe_lines","sort_lines","reverse_lines","remove_empty_lines"]},
          "case_sensitive":{"type":"boolean","description":"For dedupe_lines and sort_lines"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = utilityJson.decodeFromString<TransformArgs>(argumentsJson)
        val transformed = when (args.action.lowercase(Locale.US)) {
            "trim" -> args.text.trim()
            "uppercase" -> args.text.uppercase(Locale.getDefault())
            "lowercase" -> args.text.lowercase(Locale.getDefault())
            "title_case" -> args.text.splitToSequence(Regex("(\\s+)"))
                .joinToString("") { part ->
                    if (part.isBlank()) part else part.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                    }
                }
            "slug" -> args.text.toSlug()
            "dedupe_lines" -> args.text.lines().dedupe(args.case_sensitive ?: false).joinToString("\n")
            "sort_lines" -> args.text.lines().let { lines ->
                if (args.case_sensitive == true) lines.sorted() else lines.sortedBy { it.lowercase(Locale.getDefault()) }
            }.joinToString("\n")
            "reverse_lines" -> args.text.lines().asReversed().joinToString("\n")
            "remove_empty_lines" -> args.text.lines().filter { it.isNotBlank() }.joinToString("\n")
            else -> return@withContext err("utility_text_transform failed: unsupported action ${args.action}")
        }
        ok(transformed)
    }

    @Serializable
    private data class TransformArgs(
        val text: String,
        val action: String,
        val case_sensitive: Boolean? = null
    )
}

private class UtilityJsonTool : Tool {
    override val name: String = "utility_json"
    override val description: String =
        "Validate, format, minify, or extract a simple dotted path from JSON locally."

    override val jsonSchema: JsonObject = schema(
        "[\"json\",\"action\"]",
        """
        {
          "json":{"type":"string","description":"JSON text"},
          "action":{"type":"string","enum":["validate","format","minify","extract_path"]},
          "path":{"type":"string","description":"Simple path like user.name or items.0.title"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = utilityJson.decodeFromString<JsonArgs>(argumentsJson)
        val element = runCatching { utilityJson.parseToJsonElement(args.json) }.getOrElse { t ->
            return@withContext err("utility_json failed: invalid JSON: ${t.message}")
        }
        when (args.action.lowercase(Locale.US)) {
            "validate" -> ok("valid=true\ntype=${element.typeName()}")
            "format" -> ok(utilityJson.encodeToString(JsonElement.serializer(), element))
            "minify" -> ok(Json.encodeToString(JsonElement.serializer(), element))
            "extract_path" -> {
                val path = args.path?.trim().orEmpty()
                if (path.isBlank()) return@withContext err("utility_json failed: path is required for extract_path")
                val found = element.extractPath(path)
                    ?: return@withContext err("utility_json failed: path not found: $path")
                ok(Json.encodeToString(JsonElement.serializer(), found))
            }
            else -> err("utility_json failed: unsupported action ${args.action}")
        }
    }

    @Serializable
    private data class JsonArgs(val json: String, val action: String, val path: String? = null)
}

private class UtilityEncodingTool : Tool {
    override val name: String = "utility_encoding"
    override val description: String =
        "Encode and decode text locally: url_encode, url_decode, base64_encode, base64_decode, html_escape, html_unescape."

    override val jsonSchema: JsonObject = schema(
        "[\"text\",\"action\"]",
        """
        {
          "text":{"type":"string"},
          "action":{"type":"string","enum":["url_encode","url_decode","base64_encode","base64_decode","html_escape","html_unescape"]}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = utilityJson.decodeFromString<EncodingArgs>(argumentsJson)
        val output = runCatching {
            when (args.action.lowercase(Locale.US)) {
                "url_encode" -> URLEncoder.encode(args.text, "UTF-8")
                "url_decode" -> URLDecoder.decode(args.text, "UTF-8")
                "base64_encode" -> Base64.getEncoder().encodeToString(args.text.toByteArray(Charsets.UTF_8))
                "base64_decode" -> String(Base64.getDecoder().decode(args.text), Charsets.UTF_8)
                "html_escape" -> args.text.htmlEscape()
                "html_unescape" -> args.text.htmlUnescape()
                else -> return@withContext err("utility_encoding failed: unsupported action ${args.action}")
            }
        }.getOrElse { t -> return@withContext err("utility_encoding failed: ${t.message}") }
        ok(output)
    }

    @Serializable
    private data class EncodingArgs(val text: String, val action: String)
}

private class UtilityHashTool : Tool {
    override val name: String = "utility_hash"
    override val description: String = "Hash text locally with SHA-256, SHA-1, MD5, or SHA-512."

    override val jsonSchema: JsonObject = schema(
        "[\"text\",\"algorithm\"]",
        """
        {
          "text":{"type":"string"},
          "algorithm":{"type":"string","enum":["sha256","sha1","md5","sha512"]}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = utilityJson.decodeFromString<HashArgs>(argumentsJson)
        val algorithm = when (args.algorithm.lowercase(Locale.US)) {
            "sha256" -> "SHA-256"
            "sha1" -> "SHA-1"
            "md5" -> "MD5"
            "sha512" -> "SHA-512"
            else -> return@withContext err("utility_hash failed: unsupported algorithm ${args.algorithm}")
        }
        val digest = MessageDigest.getInstance(algorithm)
            .digest(args.text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        ok("algorithm=$algorithm\nhash=$digest")
    }

    @Serializable
    private data class HashArgs(val text: String, val algorithm: String)
}

private class UtilityTimeTool : Tool {
    override val name: String = "utility_time"
    override val description: String =
        "Work with time locally: now, add duration, or diff two ISO-8601 timestamps."

    override val jsonSchema: JsonObject = schema(
        "[\"action\"]",
        """
        {
          "action":{"type":"string","enum":["now","add","diff"]},
          "time":{"type":"string","description":"ISO-8601 timestamp for add, e.g. 2026-05-29T20:00:00+08:00"},
          "start":{"type":"string","description":"ISO-8601 timestamp for diff"},
          "end":{"type":"string","description":"ISO-8601 timestamp for diff"},
          "amount":{"type":"integer","description":"Amount for add"},
          "unit":{"type":"string","enum":["minutes","hours","days","weeks"]},
          "zone":{"type":"string","description":"Timezone for now, e.g. Asia/Shanghai"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = utilityJson.decodeFromString<TimeArgs>(argumentsJson)
        when (args.action.lowercase(Locale.US)) {
            "now" -> {
                val zone = safeZone(args.zone)
                val now = ZonedDateTime.now(zone)
                ok("iso=${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}\nepoch_ms=${now.toInstant().toEpochMilli()}\nzone=${zone.id}")
            }
            "add" -> {
                val base = parseTime(args.time ?: return@withContext err("utility_time failed: time is required for add"))
                val amount = args.amount ?: return@withContext err("utility_time failed: amount is required for add")
                val result = when (args.unit ?: "minutes") {
                    "minutes" -> base.plusMinutes(amount.toLong())
                    "hours" -> base.plusHours(amount.toLong())
                    "days" -> base.plusDays(amount.toLong())
                    "weeks" -> base.plusWeeks(amount.toLong())
                    else -> return@withContext err("utility_time failed: unsupported unit ${args.unit}")
                }
                ok("iso=${result.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}\nepoch_ms=${result.toInstant().toEpochMilli()}")
            }
            "diff" -> {
                val start = parseTime(args.start ?: return@withContext err("utility_time failed: start is required for diff"))
                val end = parseTime(args.end ?: return@withContext err("utility_time failed: end is required for diff"))
                val duration = Duration.between(start.toInstant(), end.toInstant())
                val seconds = duration.seconds
                ok(buildString {
                    appendLine("seconds=$seconds")
                    appendLine("minutes=${seconds / 60}")
                    appendLine("hours=${seconds / 3600}")
                    appendLine("days=${seconds / 86400}")
                    appendLine("absolute_seconds=${seconds.absoluteValue}")
                }.trim())
            }
            else -> err("utility_time failed: unsupported action ${args.action}")
        }
    }

    private fun parseTime(value: String): ZonedDateTime {
        return runCatching { ZonedDateTime.parse(value) }
            .getOrElse { Instant.parse(value).atZone(ZoneId.systemDefault()) }
    }

    private fun safeZone(zone: String?): ZoneId {
        return zone?.trim()?.takeIf { it.isNotBlank() }?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
    }

    @Serializable
    private data class TimeArgs(
        val action: String,
        val time: String? = null,
        val start: String? = null,
        val end: String? = null,
        val amount: Int? = null,
        val unit: String? = null,
        val zone: String? = null
    )
}

private class UtilityRandomTool : Tool {
    override val name: String = "utility_random"
    override val description: String = "Generate random IDs, numeric codes, passwords, or UUID-like hex strings locally."

    override val jsonSchema: JsonObject = schema(
        "[\"kind\"]",
        """
        {
          "kind":{"type":"string","enum":["uuid","hex","numeric_code","password"]},
          "length":{"type":"integer","description":"Length for hex, numeric_code, or password. 4-128"},
          "count":{"type":"integer","description":"Number of values. 1-20"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = utilityJson.decodeFromString<RandomArgs>(argumentsJson)
        val count = (args.count ?: 1).coerceIn(1, 20)
        val length = (args.length ?: defaultLength(args.kind)).coerceIn(4, 128)
        val values = List(count) {
            when (args.kind.lowercase(Locale.US)) {
                "uuid" -> randomUuidLike()
                "hex" -> randomHex(length)
                "numeric_code" -> randomFrom(NUMBERS, length)
                "password" -> randomFrom(PASSWORD_CHARS, length)
                else -> return@withContext err("utility_random failed: unsupported kind ${args.kind}")
            }
        }
        ok(values.joinToString("\n"))
    }

    private fun defaultLength(kind: String): Int = when (kind.lowercase(Locale.US)) {
        "numeric_code" -> 6
        "password" -> 18
        "hex" -> 32
        else -> 32
    }

    private fun randomHex(length: Int): String = randomFrom(HEX, length)

    private fun randomUuidLike(): String {
        val raw = randomHex(32)
        return listOf(raw.substring(0, 8), raw.substring(8, 12), raw.substring(12, 16), raw.substring(16, 20), raw.substring(20)).joinToString("-")
    }

    private fun randomFrom(chars: String, length: Int): String = buildString {
        repeat(length) { append(chars[RANDOM.nextInt(chars.length)]) }
    }

    @Serializable
    private data class RandomArgs(val kind: String, val length: Int? = null, val count: Int? = null)

    companion object {
        private val RANDOM = SecureRandom()
        private const val HEX = "0123456789abcdef"
        private const val NUMBERS = "0123456789"
        private const val PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*_-+="
    }
}

private class UtilityChecklistTool : Tool {
    override val name: String = "utility_checklist"
    override val description: String =
        "Turn newline-separated tasks into a clean Markdown checklist, numbered list, or comma-separated list."

    override val jsonSchema: JsonObject = schema(
        "[\"items\"]",
        """
        {
          "items":{"type":"string","description":"One task per line"},
          "format":{"type":"string","enum":["markdown_checklist","numbered","bullets","comma"]},
          "dedupe":{"type":"boolean"}
        }
        """
    )

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Default) {
        val args = utilityJson.decodeFromString<ChecklistArgs>(argumentsJson)
        var items = args.items.lines().map { it.trim().trimStart('-', '*').trim() }.filter { it.isNotBlank() }
        if (args.dedupe != false) items = items.dedupe(caseSensitive = false)
        val output = when (args.format ?: "markdown_checklist") {
            "markdown_checklist" -> items.joinToString("\n") { "- [ ] $it" }
            "numbered" -> items.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
            "bullets" -> items.joinToString("\n") { "- $it" }
            "comma" -> items.joinToString(", ")
            else -> return@withContext err("utility_checklist failed: unsupported format ${args.format}")
        }
        ok(output)
    }

    @Serializable
    private data class ChecklistArgs(val items: String, val format: String? = null, val dedupe: Boolean? = null)
}

private fun ok(content: String): ToolResult = ToolResult(toolCallId = "", content = content, isError = false)

private fun err(content: String): ToolResult = ToolResult(toolCallId = "", content = content, isError = true)

private fun List<String>.dedupe(caseSensitive: Boolean): List<String> {
    val seen = linkedSetOf<String>()
    val output = mutableListOf<String>()
    for (line in this) {
        val key = if (caseSensitive) line else line.lowercase(Locale.getDefault())
        if (seen.add(key)) output += line
    }
    return output
}

private fun String.toSlug(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.US)
    return normalized
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}

private fun String.htmlEscape(): String = buildString {
    this@htmlEscape.forEach { ch ->
        append(
            when (ch) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> ch.toString()
            }
        )
    }
}

private fun String.htmlUnescape(): String = this
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")

private fun JsonElement.typeName(): String = when (this) {
    is JsonObject -> "object"
    is kotlinx.serialization.json.JsonArray -> "array"
    is JsonPrimitive -> when {
        isString -> "string"
        content == "true" || content == "false" -> "boolean"
        content.toDoubleOrNull() != null -> "number"
        else -> "primitive"
    }
    else -> "unknown"
}

private fun JsonElement.extractPath(path: String): JsonElement? {
    var current: JsonElement = this
    for (part in path.split('.').filter { it.isNotBlank() }) {
        current = when {
            current is JsonObject -> current[part] ?: return null
            current is kotlinx.serialization.json.JsonArray -> current.getOrNull(part.toIntOrNull() ?: return null) ?: return null
            else -> return null
        }
    }
    return current
}

private val WORD_REGEX = Regex("[\\p{L}\\p{N}_'-]+")

private val STOP_WORDS = setOf(
    "the", "and", "for", "with", "that", "this", "from", "you", "your", "are", "was", "were",
    "has", "have", "had", "not", "but", "can", "will", "into", "about", "they", "their", "our"
)
