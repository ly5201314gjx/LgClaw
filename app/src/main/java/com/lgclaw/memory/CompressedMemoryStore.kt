package com.lgclaw.memory

import android.content.Context
import com.lgclaw.config.AppStoragePaths
import com.lgclaw.storage.entities.MessageEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.math.abs

class CompressedMemoryStore(context: Context) {
    private val dir: File = File(AppStoragePaths.memoryDir(context), "compressed").apply { mkdirs() }
    private val indexFile = File(dir, "compressed_memory_index.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class CompressedMemoryRecord(
        val id: String,
        val sessionId: String,
        val createdAt: Long,
        val algorithm: String,
        val summary: String,
        val originalChars: Int,
        val compressedBytes: Int,
        val messageCount: Int,
        val firstMessageAt: Long,
        val lastMessageAt: Long,
        val fileName: String
    )

    fun list(sessionId: String? = null): List<CompressedMemoryRecord> {
        val sid = sessionId?.trim().orEmpty()
        return readIndex()
            .asSequence()
            .filter { sid.isBlank() || it.sessionId == sid }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    fun buildContextSummary(sessionId: String, maxRecords: Int = 8): String {
        val records = list(sessionId).take(maxRecords)
        if (records.isEmpty()) return ""
        return records.joinToString("\n\n") { record ->
            "[${record.id}] ${formatTime(record.createdAt)} ${record.algorithm} ${record.originalChars} chars -> ${record.compressedBytes} bytes\n${record.summary}"
        }
    }

    fun estimateK(messages: List<MessageEntity>): Double {
        val chars = messages.sumOf { it.content.length + it.role.length + 8 }
        return chars / 1000.0
    }

    fun estimateEffectiveK(sessionId: String, messages: List<MessageEntity>): Double {
        val records = list(sessionId)
        if (records.isEmpty()) return estimateK(messages)
        val lastCompressedAt = records.maxOfOrNull { it.lastMessageAt } ?: 0L
        val activeChars = messages
            .filter { it.createdAt > lastCompressedAt }
            .sumOf { it.content.length + it.role.length + 8 }
        val compressedSummaryChars = records.sumOf {
            it.summary.length + it.id.length + it.algorithm.length + 48
        }
        return (activeChars + compressedSummaryChars) / 1000.0
    }

    fun compressIfNeeded(sessionId: String, messages: List<MessageEntity>, thresholdK: Int): CompressedMemoryRecord? {
        if (!CompressionPolicy.shouldCompress(estimateEffectiveK(sessionId, messages), thresholdK)) return null
        return compressNow(
            sessionId = sessionId,
            messages = messages,
            keepRecentMessages = 6,
            minCandidates = 2
        )
    }

    fun compressNow(
        sessionId: String,
        messages: List<MessageEntity>,
        keepRecentMessages: Int = 2,
        minCandidates: Int = 1
    ): CompressedMemoryRecord? {
        val existingLast = list(sessionId).maxOfOrNull { it.lastMessageAt } ?: 0L
        val selection = CompressionPolicy.selectCandidates(
            messages = messages,
            lastCompressedAt = existingLast,
            keepRecentMessages = keepRecentMessages,
            minCandidates = minCandidates
        )
        if (selection.candidates.isEmpty()) return null
        return compress(sessionId, selection.candidates)
    }

    fun compress(sessionId: String, messages: List<MessageEntity>): CompressedMemoryRecord? {
        val useful = messages.filter { it.content.isNotBlank() }
        if (useful.isEmpty()) return null
        val transcript = useful.joinToString("\n") { message ->
            val role = if (message.role == "internal_user") "user" else message.role
            "[${formatTime(message.createdAt)}] ${role.uppercase(Locale.US)}: ${message.content.trim()}"
        }
        val compressed = gzip(transcript)
        val id = "cm_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_${UUID.randomUUID().toString().take(8)}"
        val fileName = "$id.txt.gz"
        File(dir, fileName).writeBytes(compressed)
        val record = CompressedMemoryRecord(
            id = id,
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            algorithm = ALGORITHM_NAME,
            summary = summarize(useful),
            originalChars = transcript.length,
            compressedBytes = compressed.size,
            messageCount = useful.size,
            firstMessageAt = useful.first().createdAt,
            lastMessageAt = useful.last().createdAt,
            fileName = fileName
        )
        val next = readIndex().filterNot { it.id == record.id } + record
        writeIndex(next)
        return record
    }

    private fun summarize(messages: List<MessageEntity>): String {
        val turns = messages.mapNotNull { message ->
            val clean = message.content.trim().replace(Regex("\\s+"), " ")
            if (clean.isBlank()) null else MemoryTurn(message.role, clean, message.createdAt)
        }
        if (turns.isEmpty()) return "本段没有可压缩的文本内容。"
        val sentences = turns.flatMapIndexed { turnIndex, turn ->
            splitSentences(turn.content).mapIndexed { sentenceIndex, sentence ->
                MemorySentence(
                    text = sentence,
                    role = turn.role,
                    turnIndex = turnIndex,
                    sentenceIndex = sentenceIndex,
                    tokens = tokenize(sentence)
                )
            }
        }.filter { it.tokens.isNotEmpty() }
        val ranked = rankSentences(sentences)
        val selected = ranked
            .take((sentences.size / 4).coerceIn(4, 10))
            .sortedWith(compareBy<MemorySentence> { it.turnIndex }.thenBy { it.sentenceIndex })
        val keywords = turns.joinToString(" ") { it.content }
            .let(::tokenize)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(16)
            .joinToString("、") { it.key }
        val userGoals = turns.filter { it.role == "user" || it.role == "internal_user" }
            .takeLast(4)
            .map { it.content.take(120) }
        val assistantActions = turns.filter { it.role == "assistant" }
            .takeLast(4)
            .map { it.content.take(120) }
        return buildString {
            appendLine("压缩算法：Codex 风格本地摘要压缩（TextRank 句子评分 + 关键词抽取 + GZIP 原文归档）。")
            appendLine("压缩范围：${turns.size} 条消息，${formatTime(turns.first().createdAt)} 至 ${formatTime(turns.last().createdAt)}。")
            if (keywords.isNotBlank()) appendLine("关键词：$keywords。")
            if (userGoals.isNotEmpty()) {
                appendLine("用户意图：")
                userGoals.forEach { appendLine("- $it") }
            }
            if (assistantActions.isNotEmpty()) {
                appendLine("已处理内容：")
                assistantActions.forEach { appendLine("- $it") }
            }
            if (selected.isNotEmpty()) {
                appendLine("高权重摘要：")
                selected.forEach { sentence ->
                    val role = if (sentence.role == "internal_user") "user" else sentence.role
                    appendLine("- ${role}: ${sentence.text.take(260)}")
                }
            }
        }.trim()
    }

    private fun splitSentences(text: String): List<String> {
        return text
            .split(Regex("(?<=[。！？.!?])\\s+|\\n+|(?<=[。！？.!?])"))
            .map { it.trim() }
            .filter { it.length >= 8 }
            .ifEmpty { listOf(text.take(420)) }
    }

    private fun tokenize(text: String): List<String> {
        return Regex("[\\p{L}\\p{N}]{2,}")
            .findAll(text.lowercase(Locale.US))
            .map { it.value }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toList()
    }

    private fun rankSentences(sentences: List<MemorySentence>): List<MemorySentence> {
        if (sentences.size <= 2) return sentences
        val scores = DoubleArray(sentences.size) { 1.0 }
        repeat(14) {
            val next = DoubleArray(sentences.size) { 0.15 }
            for (i in sentences.indices) {
                var incoming = 0.0
                for (j in sentences.indices) {
                    if (i == j) continue
                    val sim = sentenceSimilarity(sentences[i].tokens, sentences[j].tokens)
                    if (sim > 0.0) incoming += sim * scores[j]
                }
                val positionBoost = when {
                    i < 3 -> 0.18
                    i >= sentences.lastIndex - 2 -> 0.12
                    else -> 0.0
                }
                val roleBoost = if (sentences[i].role == "user" || sentences[i].role == "internal_user") 0.08 else 0.0
                next[i] += 0.85 * incoming + positionBoost + roleBoost
            }
            for (i in scores.indices) scores[i] = next[i]
        }
        return sentences.indices
            .sortedWith(compareByDescending<Int> { scores[it] }.thenBy { abs(sentences[it].turnIndex) })
            .map { sentences[it] }
    }

    private fun sentenceSimilarity(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val setA = a.toSet()
        val setB = b.toSet()
        val overlap = setA.count { it in setB }.toDouble()
        if (overlap <= 0.0) return 0.0
        return overlap / (setA.size + setB.size - overlap)
    }

    private fun gzip(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(text.toByteArray(Charsets.UTF_8))
        }
        return output.toByteArray()
    }

    private fun readIndex(): List<CompressedMemoryRecord> {
        if (!indexFile.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<CompressedMemoryRecord>>(indexFile.readText(Charsets.UTF_8)) }
            .getOrDefault(emptyList())
    }

    private fun writeIndex(records: List<CompressedMemoryRecord>) {
        indexFile.parentFile?.mkdirs()
        indexFile.writeText(json.encodeToString(ListSerializer(CompressedMemoryRecord.serializer()), records.distinctBy { it.id }), Charsets.UTF_8)
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
    }

    companion object {
        private const val ALGORITHM_NAME = "codex-textrank-gzip-v1"
        private val STOP_WORDS = setOf(
            "the", "and", "for", "are", "you", "that", "this", "with", "from", "have", "was", "were", "will", "can",
            "but", "not", "your", "all", "any", "use", "using", "about", "into", "then", "than", "a", "an", "to", "of",
            "is", "in", "on", "it", "as", "or", "be", "by", "at", "we", "i", "me", "my", "our",
            "这个", "那个", "然后", "就是", "需要", "可以", "进行", "功能", "一个", "没有", "不要", "以及",
            "如果", "但是", "当前", "用户", "助手", "对话", "内容", "已经", "还是", "因为", "所以", "现在"
        )
    }

    private data class MemoryTurn(val role: String, val content: String, val createdAt: Long)
    private data class MemorySentence(
        val text: String,
        val role: String,
        val turnIndex: Int,
        val sentenceIndex: Int,
        val tokens: List<String>
    )
}
