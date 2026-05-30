package com.lgclaw.skills

import android.content.Context
import com.lgclaw.config.AppStoragePaths
import org.json.JSONArray
import java.io.File
import java.util.Locale

class SkillStore(context: Context) {
    private val appContext = context.applicationContext
    private val skillsDir: File = AppStoragePaths.skillsDir(appContext).apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("lgclaw_skills", Context.MODE_PRIVATE)

    data class ManagedSkill(
        val name: String,
        val description: String,
        val enabled: Boolean,
        val source: String,
        val path: String,
        val updatedAt: Long
    )

    fun list(): List<ManagedSkill> {
        val loader = SkillsLoader(appContext)
        return loader.listSkills(filterUnavailable = false)
            .map { info ->
                ManagedSkill(
                    name = info.name,
                    description = loader.getSkillDescriptionPublic(info.name),
                    enabled = isEnabled(info.name),
                    source = info.source,
                    path = info.path,
                    updatedAt = if (info.path.startsWith("asset://")) 0L else File(info.path).lastModified()
                )
            }
            .sortedWith(compareByDescending<ManagedSkill> { it.enabled }.thenBy { it.name.lowercase(Locale.US) })
    }

    fun createOrUpdate(name: String, description: String, body: String, enabled: Boolean = true): ManagedSkill {
        val normalized = sanitizeSkillName(name)
        require(normalized.isNotBlank()) { "skill name is required" }
        val cleanDescription = description.trim().ifBlank { "LGClaw user skill $normalized" }
        val cleanBody = body.trim().ifBlank {
            "Use this skill when the user invokes @$normalized or asks for: $cleanDescription. Follow the user's request, ask for missing inputs only when necessary, and prefer concrete actions."
        }
        val content = buildString {
            appendLine("---")
            appendLine("name: $normalized")
            appendLine("description: ${cleanDescription.replace("\n", " ")}")
            appendLine("metadata: {\"lgclaw\":{\"user_created\":true}}")
            appendLine("---")
            appendLine()
            appendLine("# $normalized")
            appendLine()
            appendLine(cleanBody)
        }
        val dir = File(skillsDir, normalized).apply { mkdirs() }
        val file = File(dir, "SKILL.md")
        file.writeText(content, Charsets.UTF_8)
        setEnabled(normalized, enabled)
        return ManagedSkill(normalized, cleanDescription, enabled, "workspace", file.absolutePath, file.lastModified())
    }

    fun importSkill(name: String, markdown: String, enabled: Boolean = true): ManagedSkill {
        val normalized = sanitizeSkillName(name)
        require(normalized.isNotBlank()) { "skill name is required" }
        val content = markdown.trim().ifBlank { throw IllegalArgumentException("skill markdown is blank") }
        val finalContent = if (content.startsWith("---")) content else buildString {
            appendLine("---")
            appendLine("name: $normalized")
            appendLine("description: Imported OpenClaw/LGClaw compatible skill")
            appendLine("---")
            appendLine()
            appendLine(content)
        }
        val dir = File(skillsDir, normalized).apply { mkdirs() }
        val file = File(dir, "SKILL.md")
        file.writeText(finalContent.trimEnd() + "\n", Charsets.UTF_8)
        setEnabled(normalized, enabled)
        return ManagedSkill(normalized, readDescription(finalContent).ifBlank { normalized }, enabled, "workspace", file.absolutePath, file.lastModified())
    }

    fun setEnabled(name: String, enabled: Boolean) {
        val normalized = sanitizeSkillName(name)
        if (normalized.isBlank()) return
        val disabled = disabledSkills().toMutableSet()
        if (enabled) disabled.remove(normalized) else disabled.add(normalized)
        prefs.edit().putString(KEY_DISABLED, JSONArray(disabled.sorted()).toString()).apply()
    }


    fun delete(name: String): Boolean {
        val normalized = sanitizeSkillName(name)
        require(normalized.isNotBlank()) { "技能名称不能为空" }
        val dir = File(skillsDir, normalized).canonicalFile
        val root = skillsDir.canonicalFile
        require(dir.path.startsWith(root.path + File.separator)) { "只能删除本地技能" }
        require(dir.exists()) { "本地技能不存在或已被删除" }
        val skillFile = File(dir, "SKILL.md")
        require(skillFile.exists()) { "只能删除本地技能目录" }
        val deleted = dir.deleteRecursively()
        if (deleted) {
            val disabled = disabledSkills().toMutableSet()
            disabled.remove(normalized)
            prefs.edit().putString(KEY_DISABLED, JSONArray(disabled.sorted()).toString()).apply()
        }
        return deleted
    }
    fun isEnabled(name: String): Boolean = sanitizeSkillName(name) !in disabledSkills()

    fun disabledSkills(): Set<String> {
        val raw = prefs.getString(KEY_DISABLED, "[]").orEmpty()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val value = sanitizeSkillName(arr.optString(i))
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptySet())
    }

    fun commandNames(): List<String> = list().filter { it.enabled }.map { it.name }

    private fun readDescription(content: String): String {
        if (!content.startsWith("---")) return ""
        val end = content.indexOf("\n---", startIndex = 3)
        if (end <= 0) return ""
        return content.substring(3, end)
            .lineSequence()
            .firstOrNull { it.trim().startsWith("description:") }
            ?.substringAfter(':')
            ?.trim()
            ?.trim('"', '\'')
            .orEmpty()
    }

    companion object {
        private const val KEY_DISABLED = "disabled_skills_json"

        fun sanitizeSkillName(raw: String): String {
            return raw.trim()
                .removePrefix("@")
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-', '.', '_')
        }
    }
}
