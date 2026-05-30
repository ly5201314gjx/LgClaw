package com.lgclaw.ui

import android.app.Application
import com.lgclaw.agent.AgentLogStore
import com.lgclaw.agents.AgentRepository
import com.lgclaw.agents.RoleCardRepository
import com.lgclaw.config.AppStoragePaths
import com.lgclaw.config.ConfigStore
import com.lgclaw.cron.CronLogStore
import com.lgclaw.cron.CronRepository
import com.lgclaw.cron.CronService
import com.lgclaw.memory.MemoryStore
import com.lgclaw.memory.CompressedMemoryStore
import com.lgclaw.novel.NovelRepository
import com.lgclaw.skills.SkillStore
import com.lgclaw.tools.DynamicToolStore
import com.lgclaw.providers.ProviderResolutionStore
import com.lgclaw.storage.AppDatabase
import com.lgclaw.storage.MessageRepository
import com.lgclaw.storage.SessionRepository
import com.lgclaw.templates.TemplateStore
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Internal dependency container for [ChatViewModel].
 *
 * Phase 1 keeps hand-wired construction but makes runtime dependencies explicit.
 */
internal class ChatViewModelEnvironment(app: Application) {
    val storageMigration: Unit = AppStoragePaths.migrateLegacyLayout(app)
    val database: AppDatabase = AppDatabase.getInstance(app)
    val messageRepository: MessageRepository = MessageRepository(database.messageDao())
    val sessionRepository: SessionRepository =
        SessionRepository(database.sessionDao(), database.messageDao())
    val cronRepository: CronRepository = CronRepository(database.cronJobDao())
    val novelRepository: NovelRepository = NovelRepository(database.novelDao())
    val roleCardRepository: RoleCardRepository = RoleCardRepository(database.roleCardDao())
    val agentRepository: AgentRepository = AgentRepository(database.agentProfileDao(), novelRepository, roleCardRepository)
    val cronService: CronService = CronService(app, cronRepository)
    val cronLogStore: CronLogStore = CronLogStore(app)
    val agentLogStore: AgentLogStore = AgentLogStore(app)
    val configStore: ConfigStore = ConfigStore(app)
    val providerResolutionStore: ProviderResolutionStore = ProviderResolutionStore(app)
    val memoryStore: MemoryStore = MemoryStore(app)
    val compressedMemoryStore: CompressedMemoryStore = CompressedMemoryStore(app)
    val skillStore: SkillStore = SkillStore(app)
    val dynamicToolStore: DynamicToolStore = DynamicToolStore(app)
    val templateStore: TemplateStore = TemplateStore(app)
    val heartbeatDocFile: File = AppStoragePaths.heartbeatDocFile(app)
    val uiJson: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    val telegramDiscoveryClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    val updateCheckClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}


