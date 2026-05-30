package com.lgclaw.tools

import android.content.Context
import com.lgclaw.config.AppStoragePaths
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

class DynamicToolStore(context: Context) {
    private val dir: File = File(AppStoragePaths.toolsDir(context), "dynamic").apply { mkdirs() }
    private val indexFile = File(dir, "dynamic_tools.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class DynamicToolSpec(
        val name: String,
        val description: String,
        val prompt: String,
        val enabled: Boolean = true,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    fun list(): List<DynamicToolSpec> = readAll().sortedWith(
        compareByDescending<DynamicToolSpec> { it.enabled }.thenBy { it.name.lowercase(Locale.US) }
    )

    fun enabledTools(): List<DynamicToolSpec> = list().filter { it.enabled }

    fun upsert(name: String, description: String, prompt: String, enabled: Boolean = true): DynamicToolSpec {
        val normalized = sanitizeName(name)
        require(normalized.isNotBlank()) { "tool name is required" }
        val cleanPrompt = prompt.trim().ifBlank { throw IllegalArgumentException("tool prompt is required") }
        val current = readAll().toMutableList()
        val existing = current.firstOrNull { it.name == normalized }
        val next = DynamicToolSpec(
            name = normalized,
            description = description.trim().ifBlank { "User-created LGClaw workflow tool: $normalized" },
            prompt = cleanPrompt,
            enabled = enabled,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        current.removeAll { it.name == normalized }
        current += next
        writeAll(current)
        return next
    }

    fun setEnabled(name: String, enabled: Boolean): DynamicToolSpec {
        val normalized = sanitizeName(name)
        val current = readAll().toMutableList()
        val index = current.indexOfFirst { it.name == normalized }
        require(index >= 0) { "tool not found: $normalized" }
        val next = current[index].copy(enabled = enabled, updatedAt = System.currentTimeMillis())
        current[index] = next
        writeAll(current)
        return next
    }

    fun get(name: String): DynamicToolSpec? = readAll().firstOrNull { it.name == sanitizeName(name) }

    private fun readAll(): List<DynamicToolSpec> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<DynamicToolSpec>>(indexFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    private fun writeAll(items: List<DynamicToolSpec>) {
        indexFile.parentFile?.mkdirs()
        indexFile.writeText(json.encodeToString(ListSerializer(DynamicToolSpec.serializer()), items.distinctBy { it.name }), Charsets.UTF_8)
    }

    companion object {
        const val DYNAMIC_TOOL_PREFIX = "dyn_"

        fun sanitizeName(raw: String): String {
            val base = raw.trim()
                .removePrefix("@")
                .removePrefix(DYNAMIC_TOOL_PREFIX)
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9_]+"), "_")
                .trim('_')
            return if (base.isBlank()) "" else DYNAMIC_TOOL_PREFIX + base
        }
    }
}