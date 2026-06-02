package com.lgclaw.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceNoteExtractorTest {
    @Test
    fun `extractAssistantNote returns full clean visible note`() {
        val raw = """
            Tool Call
            name=terminal_exec
            assistant_note:
            **Connected. Kernel is Android 14 6.1.141. The cat command lacked permission, so I will inspect other signals next.**
        """.trimIndent()

        assertEquals(
            "Connected. Kernel is Android 14 6.1.141. The cat command lacked permission, so I will inspect other signals next.",
            TraceNoteExtractor.extractAssistantNote(raw)
        )
    }

    @Test
    fun `extractAssistantNote supports fullwidth colon`() {
        val raw = "assistant_note：I will inspect the directory first."
        assertEquals("I will inspect the directory first.", TraceNoteExtractor.extractAssistantNote(raw))
    }

    @Test
    fun `extractPublicNotes accepts multiple public markers`() {
        val raw = """
            think:
            First read the directory, then organize the checklist.
            note:
            After finding the entry point, run tests.
        """.trimIndent()

        assertEquals(
            listOf(
                "First read the directory, then organize the checklist.",
                "After finding the entry point, run tests."
            ),
            TraceNoteExtractor.extractPublicNotes(raw)
        )
    }

    @Test
    fun `extractAssistantNote keeps long note visible`() {
        val sentence = "Connected. Kernel is Android 14 6.1.141. The cat command lacked permission, so I will inspect other signals next. "
        val raw = "assistant_note:\n" + List(12) { sentence }.joinToString("")

        val extracted = TraceNoteExtractor.extractAssistantNote(raw).orEmpty()

        assertTrue(extracted.length > 900)
        assertTrue(extracted.startsWith("Connected. Kernel is Android 14"))
    }

    @Test
    fun `extractAssistantNote stops before next tool section`() {
        val raw = """
            think:
            First read the directory, then organize the checklist.
            Tool Result
            status=ok
        """.trimIndent()

        assertEquals(
            "First read the directory, then organize the checklist.",
            TraceNoteExtractor.extractAssistantNote(raw)
        )
    }

    @Test
    fun `cleanTraceText keeps commands paths and versions`() {
        val raw = "### **Run `python -m pytest app/tests`, path D:/plamclaw, version Android 14 6.1.141**"
        assertEquals(
            "Run python -m pytest app/tests, path D:/plamclaw, version Android 14 6.1.141",
            TraceNoteExtractor.cleanTraceText(raw)
        )
    }

    @Test
    fun `sourceType does not return mojibake labels`() {
        assertEquals("状态", TraceNoteExtractor.sourceType("garbled text", ""))
    }

    @Test
    fun `sourceType classifies common trace sources`() {
        assertEquals("终端", TraceNoteExtractor.sourceType("use tool", "terminal_exec"))
        assertEquals("技能", TraceNoteExtractor.sourceType("read skill", "writer"))
        assertEquals("工具", TraceNoteExtractor.sourceType("use tool", "web_search"))
        assertEquals("模型", TraceNoteExtractor.sourceType("request model", "round 1"))
        assertEquals("计划", TraceNoteExtractor.sourceType("generate plan", "steps"))
    }

    @Test
    fun `extractAssistantNote returns null when note is absent`() {
        assertNull(TraceNoteExtractor.extractAssistantNote("Tool Result\nstatus=ok"))
    }

    @Test
    fun `displayDetail falls back to readable title`() {
        assertTrue(TraceNoteExtractor.displayDetail("use tool", "").contains("use tool"))
    }
}
