package com.lgclaw.novel

import com.lgclaw.storage.dao.NovelDao
import com.lgclaw.storage.entities.NovelAnalysisEntity
import com.lgclaw.storage.entities.NovelChapterEntity
import com.lgclaw.storage.entities.NovelCharacterEntity
import com.lgclaw.storage.entities.NovelProjectEntity
import com.lgclaw.storage.entities.NovelRelationEntity
import com.lgclaw.storage.entities.NovelWorldNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NovelRepository(private val dao: NovelDao) {
    suspend fun createProject(title: String, genre: String, styleGuide: String, premise: String): NovelProjectEntity = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { throw IllegalArgumentException("novel title is required") }
        val now = System.currentTimeMillis()
        val project = NovelProjectEntity(
            id = "novel_${UUID.randomUUID().toString().take(12)}",
            title = cleanTitle.take(120),
            genre = genre.trim().take(80),
            styleGuide = styleGuide.trim().take(4000),
            premise = premise.trim().take(4000),
            createdAt = now,
            updatedAt = now
        )
        dao.upsertProject(project)
        project
    }

    suspend fun listProjects(): List<NovelProjectEntity> = withContext(Dispatchers.IO) { dao.listProjects() }

    suspend fun getProject(projectId: String): NovelProjectEntity? = withContext(Dispatchers.IO) { dao.getProject(projectId.trim()) }

    suspend fun updateProject(
        projectId: String,
        title: String,
        genre: String,
        styleGuide: String,
        premise: String
    ): NovelProjectEntity = withContext(Dispatchers.IO) {
        val old = dao.getProject(projectId.trim()) ?: throw IllegalArgumentException("novel project not found")
        val cleanTitle = title.trim().ifBlank { throw IllegalArgumentException("novel title is required") }
        val updated = old.copy(
            title = cleanTitle.take(120),
            genre = genre.trim().take(80),
            styleGuide = styleGuide.trim().take(4000),
            premise = premise.trim().take(4000),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertProject(updated)
        updated
    }

    suspend fun deleteProjectCascade(projectId: String) = withContext(Dispatchers.IO) {
        val cleanId = projectId.trim().ifBlank { throw IllegalArgumentException("novel project id is required") }
        dao.deleteRelations(cleanId)
        dao.deleteProjectChapters(cleanId)
        dao.deleteProjectCharacters(cleanId)
        dao.deleteProjectWorldNotes(cleanId)
        dao.deleteProjectAnalyses(cleanId)
        dao.deleteProject(cleanId)
    }

    suspend fun upsertChapter(
        projectId: String,
        chapterIndex: Int,
        title: String,
        content: String,
        summary: String? = null
    ): NovelChapterEntity = withContext(Dispatchers.IO) {
        val project = dao.getProject(projectId.trim()) ?: throw IllegalArgumentException("novel project not found")
        val cleanContent = content.trim().ifBlank { throw IllegalArgumentException("chapter content is required") }
        val existing = dao.getChapterByIndex(project.id, chapterIndex)
        val analysis = NovelAnalyzer.analyzeChapter(cleanContent)
        val now = System.currentTimeMillis()
        val chapter = NovelChapterEntity(
            id = existing?.id ?: "chapter_${UUID.randomUUID().toString().take(12)}",
            projectId = project.id,
            chapterIndex = chapterIndex.coerceAtLeast(1),
            title = title.trim().ifBlank { "Chapter ${chapterIndex.coerceAtLeast(1)}" }.take(160),
            content = cleanContent,
            summary = summary?.trim()?.ifBlank { null } ?: analysis.summary,
            keywordsJson = jsonArray(analysis.keywords),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsertChapter(chapter)
        dao.upsertProject(project.copy(updatedAt = now))
        chapter
    }

    suspend fun listChapters(projectId: String): List<NovelChapterEntity> = withContext(Dispatchers.IO) { dao.listChapters(projectId.trim()) }

    suspend fun deleteChapter(chapterId: String) = withContext(Dispatchers.IO) {
        val cleanId = chapterId.trim().ifBlank { throw IllegalArgumentException("chapter id is required") }
        dao.deleteChapter(cleanId)
    }

    suspend fun upsertCharacter(
        projectId: String,
        name: String,
        aliases: List<String>,
        goal: String,
        secret: String,
        arc: String,
        notes: String
    ): NovelCharacterEntity = withContext(Dispatchers.IO) {
        val project = dao.getProject(projectId.trim()) ?: throw IllegalArgumentException("novel project not found")
        val cleanName = name.trim().ifBlank { throw IllegalArgumentException("character name is required") }
        val existing = dao.listCharacters(project.id).firstOrNull { it.name == cleanName }
        val now = System.currentTimeMillis()
        val character = NovelCharacterEntity(
            id = existing?.id ?: "char_${UUID.randomUUID().toString().take(12)}",
            projectId = project.id,
            name = cleanName.take(100),
            aliasesJson = jsonArray(aliases.map { it.trim() }.filter { it.isNotBlank() }.distinct()),
            goal = goal.trim().take(2000),
            secret = secret.trim().take(2000),
            arc = arc.trim().take(3000),
            notes = notes.trim().take(4000),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsertCharacter(character)
        character
    }

    suspend fun listCharacters(projectId: String): List<NovelCharacterEntity> = withContext(Dispatchers.IO) { dao.listCharacters(projectId.trim()) }

    suspend fun deleteCharacter(characterId: String) = withContext(Dispatchers.IO) {
        val cleanId = characterId.trim().ifBlank { throw IllegalArgumentException("character id is required") }
        dao.deleteRelationsForCharacter(cleanId)
        dao.deleteCharacter(cleanId)
    }

    suspend fun upsertWorldNote(
        projectId: String,
        category: String,
        title: String,
        content: String,
        noteId: String? = null
    ): NovelWorldNoteEntity = withContext(Dispatchers.IO) {
        val project = dao.getProject(projectId.trim()) ?: throw IllegalArgumentException("novel project not found")
        val now = System.currentTimeMillis()
        val old = noteId?.trim()?.ifBlank { null }?.let { id -> dao.listWorldNotes(project.id).firstOrNull { it.id == id } }
        val note = NovelWorldNoteEntity(
            id = old?.id ?: "world_${UUID.randomUUID().toString().take(12)}",
            projectId = project.id,
            category = category.trim().ifBlank { "通用" }.take(80),
            title = title.trim().ifBlank { "世界观笔记" }.take(160),
            content = content.trim().ifBlank { throw IllegalArgumentException("world note content is required") },
            createdAt = old?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsertWorldNote(note)
        note
    }

    suspend fun listWorldNotes(projectId: String): List<NovelWorldNoteEntity> = withContext(Dispatchers.IO) { dao.listWorldNotes(projectId.trim()) }

    suspend fun deleteWorldNote(noteId: String) = withContext(Dispatchers.IO) {
        val cleanId = noteId.trim().ifBlank { throw IllegalArgumentException("world note id is required") }
        dao.deleteWorldNote(cleanId)
    }

    suspend fun summarizeProject(projectId: String): NovelAnalysisEntity = withContext(Dispatchers.IO) {
        val project = dao.getProject(projectId.trim()) ?: throw IllegalArgumentException("novel project not found")
        val chapters = dao.listChapters(project.id)
        val source = chapters.joinToString("\n\n") { "${it.title}\n${it.summary.ifBlank { it.content.take(1200) }}" }
        val analysis = NovelAnalyzer.analyzeChapter(source.ifBlank { project.premise }, maxSentences = 8)
        val payload = JSONObject()
            .put("chapterCount", chapters.size)
            .put("keywords", JSONArray(analysis.keywords))
            .toString()
        val record = NovelAnalysisEntity(
            id = "analysis_${UUID.randomUUID().toString().take(12)}",
            projectId = project.id,
            kind = "summary",
            title = "项目摘要",
            summary = analysis.summary,
            payloadJson = payload,
            createdAt = System.currentTimeMillis()
        )
        dao.upsertAnalysis(record)
        record
    }

    suspend fun analyzeRelations(projectId: String): List<NovelRelationEntity> = withContext(Dispatchers.IO) {
        val project = dao.getProject(projectId.trim()) ?: throw IllegalArgumentException("novel project not found")
        val characters = dao.listCharacters(project.id)
        val chapters = dao.listChapters(project.id)
        val byName = characters.associateBy { it.name }
        val candidates = NovelAnalyzer.analyzeRelations(
            content = chapters.joinToString("\n") { it.content },
            characterNames = characters.flatMap { listOf(it.name) + parseArray(it.aliasesJson) }.distinct()
        )
        val relations = candidates.mapNotNull { candidate ->
            val from = resolveCharacter(candidate.fromName, characters) ?: return@mapNotNull null
            val to = resolveCharacter(candidate.toName, characters) ?: return@mapNotNull null
            if (from.id == to.id) return@mapNotNull null
            val a = if (from.id <= to.id) from else to
            val b = if (from.id <= to.id) to else from
            NovelRelationEntity(
                id = "rel_${project.id}_${a.id}_${b.id}_cooccur".take(180),
                projectId = project.id,
                fromCharacterId = a.id,
                toCharacterId = b.id,
                label = "cooccur",
                weight = candidate.weight,
                evidenceJson = jsonArray(candidate.evidence),
                updatedAt = System.currentTimeMillis()
            )
        }.distinctBy { it.id }
        dao.deleteRelations(project.id)
        dao.upsertRelations(relations)
        val payload = JSONObject()
            .put("relationCount", relations.size)
            .put("characterCount", byName.size)
            .toString()
        dao.upsertAnalysis(
            NovelAnalysisEntity(
                id = "analysis_${UUID.randomUUID().toString().take(12)}",
                projectId = project.id,
                kind = "relations",
                title = "人物关系图谱",
                summary = "已从 ${chapters.size} 个章节中分析出 ${relations.size} 条人物关系边。",
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
        )
        relations
    }

    suspend fun listRelations(projectId: String): List<NovelRelationEntity> = withContext(Dispatchers.IO) { dao.listRelations(projectId.trim()) }

    suspend fun listAnalyses(projectId: String): List<NovelAnalysisEntity> = withContext(Dispatchers.IO) { dao.listAnalyses(projectId.trim()) }

    suspend fun buildContextSummary(projectId: String): String = withContext(Dispatchers.IO) {
        val project = dao.getProject(projectId.trim()) ?: return@withContext ""
        val chapters = dao.listChapters(project.id)
        val characters = dao.listCharacters(project.id)
        val relations = dao.listRelations(project.id).take(12)
        val notes = dao.listWorldNotes(project.id).take(10)
        val analyses = dao.listAnalyses(project.id).take(5)
        buildString {
            appendLine("Project: ${project.title}")
            if (project.genre.isNotBlank()) appendLine("Genre: ${project.genre}")
            if (project.styleGuide.isNotBlank()) appendLine("Style guide: ${project.styleGuide.take(900)}")
            if (project.premise.isNotBlank()) appendLine("Premise: ${project.premise.take(900)}")
            if (chapters.isNotEmpty()) {
                appendLine("\nChapters:")
                chapters.takeLast(12).forEach { appendLine("- ${it.chapterIndex}. ${it.title}: ${it.summary.take(360)}") }
            }
            if (characters.isNotEmpty()) {
                appendLine("\nCharacters:")
                characters.take(24).forEach { appendLine("- ${it.name}: goal=${it.goal.take(120)} arc=${it.arc.take(160)}") }
            }
            if (relations.isNotEmpty()) {
                val characterById = characters.associateBy { it.id }
                appendLine("\nRelations:")
                relations.forEach { rel ->
                    appendLine("- ${characterById[rel.fromCharacterId]?.name ?: rel.fromCharacterId} -> ${characterById[rel.toCharacterId]?.name ?: rel.toCharacterId}: ${rel.label} weight=${"%.1f".format(rel.weight)}")
                }
            }
            if (notes.isNotEmpty()) {
                appendLine("\nWorld notes:")
                notes.forEach { appendLine("- [${it.category}] ${it.title}: ${it.content.take(240)}") }
            }
            if (analyses.isNotEmpty()) {
                appendLine("\nRecent analyses:")
                analyses.forEach { appendLine("- ${it.kind}: ${it.summary.take(260)}") }
            }
        }.trim()
    }

    private fun resolveCharacter(name: String, characters: List<NovelCharacterEntity>): NovelCharacterEntity? {
        return characters.firstOrNull { it.name.equals(name, ignoreCase = true) || parseArray(it.aliasesJson).any { alias -> alias.equals(name, ignoreCase = true) } }
    }

    companion object {
        fun jsonArray(values: List<String>): String {
            val arr = JSONArray()
            values.filter { it.isNotBlank() }.distinct().forEach { arr.put(it) }
            return arr.toString()
        }

        fun parseArray(raw: String): List<String> = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }
}