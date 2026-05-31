package com.lgclaw.memory

import com.lgclaw.storage.entities.MessageEntity

object CompressionPolicy {
    data class CandidateSelection(
        val candidates: List<MessageEntity>,
        val usefulMessageCount: Int,
        val keptRecentCount: Int,
        val reason: String = ""
    )

    fun shouldCompress(effectiveK: Double, thresholdK: Int): Boolean {
        val safeThreshold = thresholdK.coerceIn(0, 1000)
        return safeThreshold <= 0 || effectiveK >= safeThreshold
    }

    fun selectCandidates(
        messages: List<MessageEntity>,
        lastCompressedAt: Long,
        keepRecentMessages: Int,
        minCandidates: Int = 1
    ): CandidateSelection {
        val useful = messages
            .filter { it.content.isNotBlank() && it.role != "tool" && it.createdAt > lastCompressedAt }
            .sortedBy { it.createdAt }
        if (useful.isEmpty()) {
            return CandidateSelection(emptyList(), 0, 0, "没有新的可压缩消息")
        }

        val safeMin = minCandidates.coerceAtLeast(1)
        val requestedKeep = keepRecentMessages.coerceAtLeast(0)
        val maxKeep = (useful.size - safeMin).coerceAtLeast(0)
        val actualKeep = requestedKeep.coerceAtMost(maxKeep)
        val candidates = if (actualKeep > 0) useful.dropLast(actualKeep) else useful
        if (candidates.size < safeMin) {
            return CandidateSelection(
                candidates = emptyList(),
                usefulMessageCount = useful.size,
                keptRecentCount = actualKeep,
                reason = "新的可压缩消息不足"
            )
        }
        return CandidateSelection(
            candidates = candidates,
            usefulMessageCount = useful.size,
            keptRecentCount = actualKeep
        )
    }
}
