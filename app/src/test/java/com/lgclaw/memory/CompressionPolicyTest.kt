package com.lgclaw.memory

import com.lgclaw.storage.entities.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPolicyTest {
    @Test
    fun zeroThresholdMeansImmediateCompressionAllowed() {
        assertTrue(CompressionPolicy.shouldCompress(effectiveK = 0.1, thresholdK = 0))
    }

    @Test
    fun thresholdUsesEffectiveK() {
        assertFalse(CompressionPolicy.shouldCompress(effectiveK = 7.9, thresholdK = 8))
        assertTrue(CompressionPolicy.shouldCompress(effectiveK = 8.0, thresholdK = 8))
    }

    @Test
    fun candidateSelectionKeepsRecentMessagesWithoutDroppingEverything() {
        val messages = (1L..10L).map { index ->
            MessageEntity(
                id = index,
                sessionId = "s1",
                role = if (index % 2L == 0L) "assistant" else "user",
                content = "这是一条需要参与压缩的消息 $index",
                createdAt = index
            )
        }

        val selection = CompressionPolicy.selectCandidates(
            messages = messages,
            lastCompressedAt = 0L,
            keepRecentMessages = 6,
            minCandidates = 2
        )

        assertEquals(4, selection.candidates.size)
        assertEquals(6, selection.keptRecentCount)
        assertEquals(1L, selection.candidates.first().createdAt)
        assertEquals(4L, selection.candidates.last().createdAt)
    }

    @Test
    fun candidateSelectionAllowsManualCompressionOfSmallSession() {
        val messages = listOf(
            MessageEntity(id = 1L, sessionId = "s1", role = "user", content = "短会话也可以主动压缩", createdAt = 1L)
        )

        val selection = CompressionPolicy.selectCandidates(
            messages = messages,
            lastCompressedAt = 0L,
            keepRecentMessages = 2,
            minCandidates = 1
        )

        assertEquals(1, selection.candidates.size)
        assertEquals(0, selection.keptRecentCount)
    }

    @Test
    fun candidateSelectionSkipsAlreadyCompressedAndToolMessages() {
        val messages = listOf(
            MessageEntity(id = 1L, sessionId = "s1", role = "user", content = "旧消息", createdAt = 1L),
            MessageEntity(id = 2L, sessionId = "s1", role = "tool", content = "工具结果", createdAt = 2L),
            MessageEntity(id = 3L, sessionId = "s1", role = "assistant", content = "新消息", createdAt = 3L)
        )

        val selection = CompressionPolicy.selectCandidates(
            messages = messages,
            lastCompressedAt = 1L,
            keepRecentMessages = 0,
            minCandidates = 1
        )

        assertEquals(1, selection.candidates.size)
        assertEquals(3L, selection.candidates.single().createdAt)
    }
}
