package com.lgclaw.agent

import com.lgclaw.storage.entities.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContextBuilderVisionTest {

    @Test
    fun build_includesTextFallbackWhenImageFileIsMissing() {
        val messages = listOf(
            MessageEntity(
                id = 1,
                sessionId = "s",
                role = "user",
                content = """
                    看这张图
                    [LGCLAW_ATTACHMENT:image|id=abc|name=test.jpg|mime=image/jpeg|path=C:/missing/test.jpg|size=123]
                """.trimIndent(),
                createdAt = 1
            )
        )

        val built = ContextBuilder().build(
            sessionId = "s",
            messages = messages,
            maxHistoryMessages = 8,
            longTermMemory = "",
            activeSkillsContent = "",
            skillsSummary = "",
            systemPolicyTemplate = null
        )

        val user = built.last()
        assertEquals("user", user.role)
        assertEquals(1, user.contentParts.size)
        assertEquals("text", user.contentParts.first().type)
        assertTrue(user.contentParts.first().text.orEmpty().contains("看这张图"))
    }

    @Test
    fun build_keepsDocumentMarkersOutOfVisionParts() {
        val tmp = File.createTempFile("lgclaw-test", ".txt").apply {
            writeText("not image")
            deleteOnExit()
        }
        val messages = listOf(
            MessageEntity(
                id = 2,
                sessionId = "s",
                role = "user",
                content = "[LGCLAW_ATTACHMENT:document|id=doc|name=a.txt|mime=text/plain|path=${tmp.absolutePath}|size=${tmp.length()}]",
                createdAt = 2
            )
        )

        val built = ContextBuilder().build(
            sessionId = "s",
            messages = messages,
            maxHistoryMessages = 8,
            longTermMemory = "",
            activeSkillsContent = "",
            skillsSummary = "",
            systemPolicyTemplate = null
        )

        assertTrue(built.last().contentParts.isEmpty())
    }

    @Test
    fun build_excludesMessagesCoveredByCompressedMemoryCutoff() {
        val messages = listOf(
            MessageEntity(
                id = 1,
                sessionId = "s",
                role = "user",
                content = "已经写进压缩摘要的旧需求",
                createdAt = 100
            ),
            MessageEntity(
                id = 2,
                sessionId = "s",
                role = "assistant",
                content = "已经写进压缩摘要的旧回答",
                createdAt = 200
            ),
            MessageEntity(
                id = 3,
                sessionId = "s",
                role = "user",
                content = "压缩之后的新问题",
                createdAt = 300
            )
        )

        val built = ContextBuilder().build(
            sessionId = "s",
            messages = messages,
            maxHistoryMessages = 8,
            longTermMemory = "",
            compressedMemorySummary = "[cm_test] 旧需求摘要",
            compressedMemoryCutoffAt = 200,
            activeSkillsContent = "",
            skillsSummary = "",
            systemPolicyTemplate = null
        )

        assertEquals(2, built.size)
        assertTrue(built.first().content.contains("旧需求摘要"))
        assertEquals("压缩之后的新问题", built.last().content)
    }
}
