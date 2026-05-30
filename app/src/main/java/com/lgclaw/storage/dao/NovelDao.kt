package com.lgclaw.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lgclaw.storage.entities.NovelAnalysisEntity
import com.lgclaw.storage.entities.NovelChapterEntity
import com.lgclaw.storage.entities.NovelCharacterEntity
import com.lgclaw.storage.entities.NovelProjectEntity
import com.lgclaw.storage.entities.NovelRelationEntity
import com.lgclaw.storage.entities.NovelWorldNoteEntity

@Dao
interface NovelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: NovelProjectEntity)

    @Query("SELECT * FROM novel_projects ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun listProjects(): List<NovelProjectEntity>

    @Query("SELECT * FROM novel_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProject(projectId: String): NovelProjectEntity?

    @Query("DELETE FROM novel_projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapter(chapter: NovelChapterEntity)

    @Query("SELECT * FROM novel_chapters WHERE projectId = :projectId ORDER BY chapterIndex ASC, createdAt ASC")
    suspend fun listChapters(projectId: String): List<NovelChapterEntity>

    @Query("SELECT * FROM novel_chapters WHERE id = :chapterId LIMIT 1")
    suspend fun getChapter(chapterId: String): NovelChapterEntity?

    @Query("SELECT * FROM novel_chapters WHERE projectId = :projectId AND chapterIndex = :chapterIndex LIMIT 1")
    suspend fun getChapterByIndex(projectId: String, chapterIndex: Int): NovelChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacter(character: NovelCharacterEntity)

    @Query("SELECT * FROM novel_characters WHERE projectId = :projectId ORDER BY name COLLATE NOCASE ASC")
    suspend fun listCharacters(projectId: String): List<NovelCharacterEntity>

    @Query("SELECT * FROM novel_characters WHERE id = :characterId LIMIT 1")
    suspend fun getCharacter(characterId: String): NovelCharacterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelation(relation: NovelRelationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelations(relations: List<NovelRelationEntity>)

    @Query("SELECT * FROM novel_relations WHERE projectId = :projectId ORDER BY weight DESC, updatedAt DESC")
    suspend fun listRelations(projectId: String): List<NovelRelationEntity>

    @Query("DELETE FROM novel_relations WHERE projectId = :projectId")
    suspend fun deleteRelations(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorldNote(note: NovelWorldNoteEntity)

    @Query("SELECT * FROM novel_world_notes WHERE projectId = :projectId ORDER BY category COLLATE NOCASE ASC, updatedAt DESC")
    suspend fun listWorldNotes(projectId: String): List<NovelWorldNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysis(analysis: NovelAnalysisEntity)

    @Query("SELECT * FROM novel_analyses WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun listAnalyses(projectId: String): List<NovelAnalysisEntity>

    @Query("DELETE FROM novel_chapters WHERE projectId = :projectId")
    suspend fun deleteProjectChapters(projectId: String)

    @Query("DELETE FROM novel_chapters WHERE id = :chapterId")
    suspend fun deleteChapter(chapterId: String)

    @Query("DELETE FROM novel_characters WHERE projectId = :projectId")
    suspend fun deleteProjectCharacters(projectId: String)

    @Query("DELETE FROM novel_characters WHERE id = :characterId")
    suspend fun deleteCharacter(characterId: String)

    @Query("DELETE FROM novel_relations WHERE fromCharacterId = :characterId OR toCharacterId = :characterId")
    suspend fun deleteRelationsForCharacter(characterId: String)

    @Query("DELETE FROM novel_world_notes WHERE projectId = :projectId")
    suspend fun deleteProjectWorldNotes(projectId: String)

    @Query("DELETE FROM novel_world_notes WHERE id = :noteId")
    suspend fun deleteWorldNote(noteId: String)

    @Query("DELETE FROM novel_analyses WHERE projectId = :projectId")
    suspend fun deleteProjectAnalyses(projectId: String)
}