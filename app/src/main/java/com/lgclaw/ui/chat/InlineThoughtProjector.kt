package com.lgclaw.ui

/**
 * Aggregated inline "thinking" view for a single assistant turn.
 *
 * The previous implementation expanded every trace item into its own
 * full-width card, which flooded the transcript (up to 24 stacked cards
 * per turn) and made long agent runs unreadable. This redesign follows
 * the "think chain folding" pattern used in other mobile agent apps:
 * one compact summary card per turn, with the actual step list hidden
 * behind a single click.
 */
data class UiInlineThought(
    val id: String,
    val anchorMessageId: Long,
    val running: Boolean,
    val createdAt: Long,
    /** Total number of trace steps in this turn. */
    val totalSteps: Int,
    /** Steps the user can see inline when the card is expanded. */
    val visibleSteps: List<UiInlineTrace>,
    /** How many further steps exist beyond [visibleSteps]. */
    val hiddenStepCount: Int,
    /** Source name -> count, for the compact summary header. */
    val sourceBreakdown: List<Pair<String, Int>>,
    /** Earliest step (used as the collapsed subtitle). */
    val firstStepDetail: String,
    /** Latest step (used as the collapsed subtitle). */
    val latestStepDetail: String,
    val firstStepAt: Long,
    val latestStepAt: Long
) {
    val hasHiddenSteps: Boolean get() = hiddenStepCount > 0
    val collapsedLabel: String
        get() = if (running) {
            if (totalSteps == 0) "正在思考..." else "思考中 ($totalSteps)"
        } else {
            "思考 ($totalSteps)"
        }
}

sealed class ChatTimelineItem {
    data class Message(val message: UiMessage) : ChatTimelineItem()
    data class Thought(val thought: UiInlineThought) : ChatTimelineItem()

    val stableKey: String
        get() = when (this) {
            is Message -> "message:${message.id}"
            is Thought -> "thought:${thought.id}"
        }
}

object InlineThoughtProjector {
    /** Maximum number of trace steps shown inline when the card is expanded. */
    const val MAX_VISIBLE_STEPS = 5
    /** Maximum number of trace steps retained per turn. */
    const val MAX_STORED_STEPS = 8
    private const val MAX_INLINE_DETAIL_CHARS = 140

    fun project(messages: List<UiMessage>): List<ChatTimelineItem> = buildList {
        messages.forEach { message ->
            if (message.role == "trace" && message.traceItems.isNotEmpty()) {
                add(ChatTimelineItem.Thought(buildAggregatedThought(message)))
                return@forEach
            }
            // Tool messages and tool public notes are rendered inside the
            // tool bubble itself. We deliberately do NOT also project them
            // as thought cards here - doing so double-renders the same
            // content and floods the transcript.
            add(ChatTimelineItem.Message(message))
        }
    }

    private fun buildAggregatedThought(message: UiMessage): UiInlineThought {
        val rawItems = message.traceItems.takeLast(MAX_STORED_STEPS)
        val items = rawItems
            .map { it.normalizeForSummary() }
            .dedupAdjacent()
        val total = items.size
        val visible = items.takeLast(MAX_VISIBLE_STEPS)
        val hidden = (total - visible.size).coerceAtLeast(0)
        val breakdown = items.asSequence()
            .groupingBy { stepSourceLabel(it) }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
        val first = items.firstOrNull()
        val last = items.lastOrNull()
        return UiInlineThought(
            id = "trace:${message.id}",
            anchorMessageId = message.traceAnchorMessageId ?: message.id,
            running = message.traceRunning,
            createdAt = last?.createdAt ?: message.createdAt,
            totalSteps = total,
            visibleSteps = visible,
            hiddenStepCount = hidden,
            sourceBreakdown = breakdown,
            firstStepDetail = first?.detail?.let(::truncate) ?: "",
            latestStepDetail = last?.detail?.let(::truncate) ?: "",
            firstStepAt = first?.createdAt ?: message.createdAt,
            latestStepAt = last?.createdAt ?: message.createdAt
        )
    }

    private fun stepSourceLabel(trace: UiInlineTrace): String {
        val name = trace.sourceName.trim()
        if (name.isNotBlank() && name != trace.sourceType) return name
        val type = trace.sourceType.trim()
        return type.ifBlank { "思考" }
    }

    private fun truncate(text: String): String {
        val normalized = text.replace("\n", " ").trim()
        return if (normalized.length <= MAX_INLINE_DETAIL_CHARS) {
            normalized
        } else {
            normalized.take(MAX_INLINE_DETAIL_CHARS - 1).trimEnd() + "..."
        }
    }

    private fun UiInlineTrace.normalizeForSummary(): UiInlineTrace {
        val compact = detail.replace(Regex("\\s+"), " ").trim()
        val capped = if (compact.length > 480) compact.take(479) + "..." else compact
        return copy(detail = capped, rawPreview = rawPreview.take(240))
    }

    private fun List<UiInlineTrace>.dedupAdjacent(): List<UiInlineTrace> {
        if (isEmpty()) return this
        val out = ArrayList<UiInlineTrace>(size)
        for (item in this) {
            val last = out.lastOrNull()
            if (last != null && last.detail == item.detail && last.sourceName == item.sourceName) {
                out[out.size - 1] = last.copy(createdAt = maxOf(last.createdAt, item.createdAt))
            } else {
                out += item
            }
        }
        return out
    }
}
