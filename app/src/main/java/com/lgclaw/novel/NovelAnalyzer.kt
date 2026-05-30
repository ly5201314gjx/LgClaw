package com.lgclaw.novel

import java.util.Locale
import kotlin.math.abs

object NovelAnalyzer {
    data class ChapterAnalysis(val summary: String, val keywords: List<String>)
    data class RelationCandidate(val fromName: String, val toName: String, val weight: Double, val evidence: List<String>)

    fun analyzeChapter(content: String, maxSentences: Int = 5): ChapterAnalysis {
        val sentences = splitSentences(content).filter { it.length >= 4 }
        val keywords = extractKeywords(content, 16)
        if (sentences.isEmpty()) return ChapterAnalysis(summary = content.trim().take(260), keywords = keywords)
        val scores = textRank(sentences)
        val selected = sentences.indices
            .sortedByDescending { scores[it] }
            .take(maxSentences.coerceIn(1, 8))
            .sorted()
            .map { sentences[it] }
        return ChapterAnalysis(summary = selected.joinToString(" ").take(1600), keywords = keywords)
    }

    fun extractKeywords(text: String, limit: Int = 12): List<String> {
        val normalized = text.lowercase(Locale.US)
        val tokens = mutableListOf<String>()
        Regex("[a-z0-9_]{2,}").findAll(normalized)
            .map { it.value.trim() }
            .filter { it !in stopWords }
            .forEach { tokens += it }
        Regex("[\\p{IsHan}]{2,}").findAll(text).forEach { match ->
            val run = match.value
            for (size in 2..4) {
                if (run.length >= size) {
                    for (i in 0..run.length - size) {
                        val gram = run.substring(i, i + size)
                        if (gram !in stopWords) tokens += gram
                    }
                }
            }
        }
        return tokens.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length }.thenBy { it.key })
            .take(limit.coerceAtLeast(1))
            .map { it.key }
    }

    fun analyzeRelations(content: String, characterNames: List<String>): List<RelationCandidate> {
        val names = characterNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (names.size < 2) return emptyList()
        val evidence = mutableMapOf<Pair<String, String>, MutableList<String>>()
        splitSentences(content).forEach { sentence ->
            val present = names.filter { sentence.contains(it, ignoreCase = true) }
            if (present.size >= 2) {
                for (i in 0 until present.lastIndex) {
                    for (j in i + 1 until present.size) {
                        val pair = orderedPair(present[i], present[j])
                        evidence.getOrPut(pair) { mutableListOf() }.add(sentence.take(240))
                    }
                }
            }
        }
        return evidence.map { (pair, lines) ->
            RelationCandidate(
                fromName = pair.first,
                toName = pair.second,
                weight = (lines.size * 1.0).coerceAtMost(99.0),
                evidence = lines.distinct().take(8)
            )
        }.sortedByDescending { it.weight }
    }

    fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        text.replace("\r", "\n").forEach { ch ->
            if (ch == '\n') {
                flushSentence(current, result)
            } else {
                current.append(ch)
                if (isSentenceEnd(ch)) flushSentence(current, result)
            }
        }
        flushSentence(current, result)
        return result.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun flushSentence(current: StringBuilder, result: MutableList<String>) {
        val value = current.toString().trim()
        if (value.isNotBlank()) result += value
        current.clear()
    }

    private fun isSentenceEnd(ch: Char): Boolean {
        return ch == '.' || ch == '!' || ch == '?' || ch == ';' ||
            ch == '\u3002' || ch == '\uff01' || ch == '\uff1f' || ch == '\uff1b'
    }

    private fun textRank(sentences: List<String>): DoubleArray {
        val words = sentences.map { extractKeywords(it, 80).toSet() }
        val n = sentences.size
        val scores = DoubleArray(n) { 1.0 }
        repeat(24) {
            val next = DoubleArray(n) { 0.15 }
            for (i in 0 until n) {
                for (j in 0 until n) {
                    if (i == j) continue
                    val sim = similarity(words[i], words[j])
                    if (sim > 0.0) next[i] += 0.85 * sim * scores[j]
                }
            }
            for (i in 0 until n) scores[i] = next[i]
        }
        return scores
    }

    private fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val overlap = a.count { it in b }
        return overlap.toDouble() / (abs(a.size - b.size) + a.size + b.size).coerceAtLeast(1)
    }

    private fun orderedPair(a: String, b: String): Pair<String, String> = if (a <= b) a to b else b to a

    private val stopWords = setOf(
        "the", "and", "for", "that", "this", "with", "from", "you", "your", "was", "were", "are", "will",
        "have", "has", "but", "not", "into", "then", "than",
        "\u4e00\u4e2a", "\u4e00\u79cd", "\u8fd9\u4e2a", "\u90a3\u4e2a", "\u4ed6\u4eec", "\u5979\u4eec", "\u6211\u4eec", "\u4f60\u4eec",
        "\u5c31\u662f", "\u6ca1\u6709", "\u5df2\u7ecf", "\u56e0\u4e3a", "\u6240\u4ee5", "\u4f46\u662f", "\u5982\u679c", "\u53ea\u662f", "\u81ea\u5df1", "\u4ec0\u4e48", "\u65f6\u5019"
    )
}