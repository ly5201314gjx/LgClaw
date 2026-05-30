package com.lgclaw.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lgclaw.storage.dao.AgentProfileDao
import com.lgclaw.storage.dao.CronJobDao
import com.lgclaw.storage.dao.MessageDao
import com.lgclaw.storage.dao.SessionDao
import com.lgclaw.storage.dao.NovelDao
import com.lgclaw.storage.dao.RoleCardDao
import com.lgclaw.storage.entities.AgentProfileEntity
import com.lgclaw.storage.entities.CronJobEntity
import com.lgclaw.storage.entities.MessageEntity
import com.lgclaw.storage.entities.NovelAnalysisEntity
import com.lgclaw.storage.entities.NovelChapterEntity
import com.lgclaw.storage.entities.NovelCharacterEntity
import com.lgclaw.storage.entities.NovelProjectEntity
import com.lgclaw.storage.entities.NovelRelationEntity
import com.lgclaw.storage.entities.NovelWorldNoteEntity
import com.lgclaw.storage.entities.RoleCardEntity
import com.lgclaw.storage.entities.SessionAgentBindingEntity
import com.lgclaw.storage.entities.SessionEntity
import java.util.UUID

@Database(
    entities = [
        MessageEntity::class,
        SessionEntity::class,
        CronJobEntity::class,
        AgentProfileEntity::class,
        SessionAgentBindingEntity::class,
        NovelProjectEntity::class,
        NovelChapterEntity::class,
        NovelCharacterEntity::class,
        NovelRelationEntity::class,
        NovelWorldNoteEntity::class,
        NovelAnalysisEntity::class,
        RoleCardEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao
    abstract fun cronJobDao(): CronJobDao
    abstract fun agentProfileDao(): AgentProfileDao
    abstract fun novelDao(): NovelDao
    abstract fun roleCardDao(): RoleCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lgclaw.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                val fallbackId = "default-" + UUID.randomUUID().toString()
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO sessions (id, title, createdAt, updatedAt)
                    SELECT
                        sessionId,
                        sessionId,
                        MIN(createdAt),
                        MAX(createdAt)
                    FROM messages
                    GROUP BY sessionId
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO sessions (id, title, createdAt, updatedAt)
                    VALUES (
                        '$fallbackId',
                        'Chat',
                        COALESCE((SELECT MIN(createdAt) FROM messages), strftime('%s','now') * 1000),
                        COALESCE((SELECT MAX(createdAt) FROM messages), strftime('%s','now') * 1000)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cron_jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        scheduleKind TEXT NOT NULL,
                        scheduleAtMs INTEGER,
                        scheduleEveryMs INTEGER,
                        scheduleExpr TEXT,
                        scheduleTz TEXT,
                        payloadKind TEXT NOT NULL,
                        payloadMessage TEXT NOT NULL,
                        payloadDeliver INTEGER NOT NULL,
                        payloadChannel TEXT,
                        payloadTo TEXT,
                        payloadSessionId TEXT,
                        nextRunAtMs INTEGER,
                        lastRunAtMs INTEGER,
                        lastStatus TEXT,
                        lastError TEXT,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL,
                        deleteAfterRun INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        description TEXT NOT NULL,
                        systemPrompt TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        defaultSkillNamesJson TEXT NOT NULL,
                        dynamicToolNamesJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS session_agent_bindings (
                        sessionId TEXT NOT NULL PRIMARY KEY,
                        agentId TEXT,
                        activeNovelProjectId TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novel_projects (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        genre TEXT NOT NULL,
                        styleGuide TEXT NOT NULL,
                        premise TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novel_chapters (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        keywordsJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_novel_chapters_projectId_chapterIndex ON novel_chapters(projectId, chapterIndex)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novel_characters (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        aliasesJson TEXT NOT NULL,
                        goal TEXT NOT NULL,
                        secret TEXT NOT NULL,
                        arc TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_novel_characters_projectId_name ON novel_characters(projectId, name)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novel_relations (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        fromCharacterId TEXT NOT NULL,
                        toCharacterId TEXT NOT NULL,
                        label TEXT NOT NULL,
                        weight REAL NOT NULL,
                        evidenceJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_novel_relations_projectId_fromCharacterId_toCharacterId_label ON novel_relations(projectId, fromCharacterId, toCharacterId, label)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novel_world_notes (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        category TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_novel_world_notes_projectId_category ON novel_world_notes(projectId, category)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novel_analyses (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        title TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_novel_analyses_projectId_createdAt ON novel_analyses(projectId, createdAt)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS role_cards (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        avatarSymbol TEXT NOT NULL,
                        description TEXT NOT NULL,
                        persona TEXT NOT NULL,
                        speakingStyle TEXT NOT NULL,
                        boundaries TEXT NOT NULL,
                        scenario TEXT NOT NULL,
                        exampleDialog TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                val columns = db.query("PRAGMA table_info(session_agent_bindings)").use { cursor ->
                    buildSet {
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                }
                if ("activeRoleCardId" !in columns) {
                    db.execSQL("ALTER TABLE session_agent_bindings ADD COLUMN activeRoleCardId TEXT")
                }
            }
        }
    }
}

