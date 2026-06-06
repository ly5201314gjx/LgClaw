package com.lgclaw.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongRunningToolPolicyTest {
    @Test
    fun `deep research MCP tools run in background`() {
        assertTrue(LongRunningToolPolicy.shouldRunInBackground("mcp_research_deep_research", 300_000L))
        assertTrue(LongRunningToolPolicy.shouldRunInBackground("mcp_default_web_crawl", 180_000L))
    }

    @Test
    fun `regular short tools stay synchronous`() {
        assertFalse(LongRunningToolPolicy.shouldRunInBackground("web_search", 60_000L))
        assertFalse(LongRunningToolPolicy.shouldRunInBackground("memory_read", 60_000L))
    }

    @Test
    fun `terminal tools stay synchronous because they stream into terminal runtime state`() {
        assertFalse(LongRunningToolPolicy.shouldRunInBackground("terminal_exec", 660_000L))
        assertFalse(LongRunningToolPolicy.shouldRunInBackground("terminal_python_install", 600_000L))
    }
}
