package com.lgclaw.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPanelsTest {
    @Test
    fun skillDisplayDescription_translatesKnownBuiltins() {
        assertEquals(
            "管理本地长期记忆与压缩记忆，帮助 AI 保留重要上下文。",
            skillDisplayDescription("memory", "Long-term memory management")
        )
    }

    @Test
    fun skillDisplayDescription_keepsChineseCustomDescription() {
        assertEquals("自定义中文技能", skillDisplayDescription("custom", "自定义中文技能"))
    }

    @Test
    fun skillDisplayDescription_wrapsEnglishFallbackInChinese() {
        val text = skillDisplayDescription("custom", "Review code changes")
        assertTrue(text.startsWith("本地技能："))
    }
}