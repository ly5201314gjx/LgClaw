package com.lgclaw.tools

import com.lgclaw.providers.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryBackgroundJobTest {
    @Test
    fun `long MCP research tool returns immediately as background job`() = runBlocking {
        val registry = ToolRegistry(
            initialTools = mapOf(
                "mcp_default_deep_research" to object : Tool, TimedTool {
                    override val name: String = "mcp_default_deep_research"
                    override val description: String = "deep research"
                    override val jsonSchema: JsonObject = buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", true)
                        put("required", Json.parseToJsonElement("[]"))
                        put("properties", Json.parseToJsonElement("{}"))
                    }
                    override val timeoutMs: Long = 300_000L

                    override suspend fun run(argumentsJson: String): ToolResult {
                        return ToolResult("", "done", false)
                    }
                }
            )
        )

        val result = registry.execute(
            ToolCall(
                id = "call_1",
                name = "mcp_default_deep_research",
                argumentsJson = "{}"
            )
        )

        val debug = "content=${result.content}; metadata=${result.metadata}"
        assertFalse(debug, result.isError)
        assertTrue(debug, result.content.contains("Background tool job started"))
        assertTrue(debug, result.metadata.toString().contains("job_id"))
        // The job may complete immediately on a fast test runner; the important
        // behavior is that execute() did not wait for a foreground tool result.
        assertTrue(debug, result.metadata.toString().contains("background"))
    }
}
