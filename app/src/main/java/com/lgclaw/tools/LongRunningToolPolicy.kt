package com.lgclaw.tools

import java.util.Locale

object LongRunningToolPolicy {
    private const val BACKGROUND_TIMEOUT_THRESHOLD_MS = 120_000L

    private val longRunningNameHints = listOf(
        "deep_research",
        "research",
        "crawl",
        "crawler",
        "scrape",
        "harvest",
        "benchmark",
        "backtest",
        "agent",
        "browser"
    )

    private val synchronousToolPrefixes = listOf(
        "terminal_"
    )

    fun shouldRunInBackground(toolName: String, timeoutMs: Long): Boolean {
        val normalized = toolName.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return false
        if (synchronousToolPrefixes.any { normalized.startsWith(it) }) return false
        if (normalized == "tool_job_status") return false
        if (timeoutMs > BACKGROUND_TIMEOUT_THRESHOLD_MS && normalized.startsWith("mcp_")) return true
        return normalized.startsWith("mcp_") && longRunningNameHints.any { hint -> normalized.contains(hint) }
    }
}
