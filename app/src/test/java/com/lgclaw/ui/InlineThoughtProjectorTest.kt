package com.lgclaw.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineThoughtProjectorTest {

    @Test
    fun `trace message collapses into a single aggregated thought card`() {
        val messages = listOf(
            UiMessage(1, "user", "do it", 100),
            UiMessage(
                id = -1077,
                role = "trace",
                content = "Reading files",
                createdAt = 110,
                traceItems = listOf(
                    step("t1", 110, "terminal_exec", "Reading foo.txt"),
                    step("t2", 115, "terminal_exec", "Reading foo.txt"),
                    step("t3", 120, "tool", "Editing foo.txt"),
                    step("t4", 125, "plan", "Deciding next step")
                ),
                traceRunning = true,
                traceAnchorMessageId = 1
            ),
            UiMessage(2, "assistant", "done", 130)
        )

        val items = InlineThoughtProjector.project(messages)

        // One thought card for the whole turn, never one-per-step.
        assertEquals(3, items.size)
        val thought = items[1] as ChatTimelineItem.Thought
        assertEquals(3, thought.thought.totalSteps) // t2 dedups into t1
        assertEquals(3, thought.thought.visibleSteps.size)
        assertEquals(0, thought.thought.hiddenStepCount)
        assertTrue(thought.thought.running)
        // Source breakdown is sorted by frequency.
        val sources = thought.thought.sourceBreakdown.map { it.first }
        assertEquals(listOf("terminal_exec", "tool", "plan"), sources)
    }

    @Test
    fun `trace with more than max visible steps hides the tail`() {
        val items = (0 until (InlineThoughtProjector.MAX_STORED_STEPS + 4)).map { i ->
            step("t$i", 1000L + i, "tool", "step $i")
        }
        val messages = listOf(
            UiMessage(1, "user", "go", 900),
            UiMessage(
                id = -2000,
                role = "trace",
                content = "",
                createdAt = 1000,
                traceItems = items,
                traceRunning = false,
                traceAnchorMessageId = 1
            )
        )

        val projected = InlineThoughtProjector.project(messages)

        val thought = (projected.single { it is ChatTimelineItem.Thought } as ChatTimelineItem.Thought).thought
        assertEquals(InlineThoughtProjector.MAX_STORED_STEPS, thought.totalSteps)
        assertEquals(InlineThoughtProjector.MAX_VISIBLE_STEPS, thought.visibleSteps.size)
        assertEquals(
            InlineThoughtProjector.MAX_STORED_STEPS - InlineThoughtProjector.MAX_VISIBLE_STEPS,
            thought.hiddenStepCount
        )
        // Visible steps are the most recent ones.
        assertEquals("step ${InlineThoughtProjector.MAX_STORED_STEPS + 3}", thought.visibleSteps.last().detail)
    }

    @Test
    fun `tool messages do not become separate thought cards`() {
        val messages = listOf(
            UiMessage(
                id = 42,
                role = "tool",
                content = """
                    name=web_search
                    think:
                    I should cross-check two sources before answering.
                """.trimIndent(),
                createdAt = 200
            )
        )

        val projected = InlineThoughtProjector.project(messages)

        assertEquals(1, projected.size)
        assertTrue(projected[0] is ChatTimelineItem.Message)
        assertNull((projected[0] as ChatTimelineItem.Message).message.role.let { if (it == "tool") null else it })
    }

    @Test
    fun `regular messages stay regular timeline items`() {
        val messages = listOf(
            UiMessage(1, "user", "hello", 100),
            UiMessage(2, "assistant", "hi", 110)
        )

        val projected = InlineThoughtProjector.project(messages)

        assertEquals(listOf("message:1", "message:2"), projected.map { it.stableKey })
    }

    @Test
    fun `running flag is taken from the trace message not inferred per step`() {
        val messages = listOf(
            UiMessage(1, "user", "go", 100),
            UiMessage(
                id = -1,
                role = "trace",
                content = "",
                createdAt = 110,
                traceItems = listOf(
                    step("a", 110, "tool", "x"),
                    step("b", 120, "tool", "y")
                ),
                traceRunning = false,
                traceAnchorMessageId = 1
            )
        )

        val projected = InlineThoughtProjector.project(messages)
        val thought = (projected.single { it is ChatTimelineItem.Thought } as ChatTimelineItem.Thought).thought

        assertFalse(thought.running)
    }

    @Test
    fun `empty trace message does not produce a thought card`() {
        val messages = listOf(
            UiMessage(1, "user", "hi", 100),
            UiMessage(
                id = -1,
                role = "trace",
                content = "",
                createdAt = 110,
                traceItems = emptyList(),
                traceRunning = false,
                traceAnchorMessageId = 1
            )
        )

        val projected = InlineThoughtProjector.project(messages)
        assertEquals(2, projected.size)
        assertNotNull(projected.firstOrNull { it is ChatTimelineItem.Message && it.message.role == "trace" })
    }

    private fun step(id: String, at: Long, source: String, detail: String) = UiInlineTrace(
        id = id,
        sessionId = "local",
        title = source,
        detail = detail,
        createdAt = at,
        sourceType = source,
        sourceName = source
    )
}
