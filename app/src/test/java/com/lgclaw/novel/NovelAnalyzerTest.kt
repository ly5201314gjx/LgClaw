package com.lgclaw.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelAnalyzerTest {
    @Test
    fun `extractKeywords returns repeated story terms`() {
        val keywords = NovelAnalyzer.extractKeywords("林岚走进旧城。林岚发现星门。星门在雨夜打开。", 5)

        assertTrue(keywords.contains("林岚"))
        assertTrue(keywords.contains("星门"))
    }

    @Test
    fun `analyzeChapter creates non blank local summary`() {
        val analysis = NovelAnalyzer.analyzeChapter(
            "第一章。林岚在雨夜回到旧城。她发现父亲留下的星门钥匙。钥匙唤醒了沉睡的守门人。守门人警告她不要相信城主。"
        )

        assertTrue(analysis.summary.isNotBlank())
        assertTrue(analysis.keywords.isNotEmpty())
    }

    @Test
    fun `analyzeRelations weights character cooccurrence evidence`() {
        val relations = NovelAnalyzer.analyzeRelations(
            content = "林岚看见周砚站在门口。周砚把钥匙交给林岚。城主独自离开。",
            characterNames = listOf("林岚", "周砚", "城主")
        )

        val main = relations.firstOrNull { it.fromName == "林岚" && it.toName == "周砚" || it.fromName == "周砚" && it.toName == "林岚" }
        assertTrue(main != null)
        assertEquals(2.0, main!!.weight, 0.01)
        assertTrue(main.evidence.isNotEmpty())
    }
}