package com.lgclaw.tools

import android.content.Context
import com.lgclaw.agents.AgentRepository
import com.lgclaw.novel.NovelRepository
import com.lgclaw.config.AppStoragePaths
import com.lgclaw.config.CronConfig
import com.lgclaw.cron.CronService
import com.lgclaw.memory.MemoryStore
import com.lgclaw.skills.SkillStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun createToolRegistry(
    context: Context,
    cronService: CronService?,
    memoryStore: MemoryStore = MemoryStore(context),
    agentRepository: AgentRepository? = null,
    novelRepository: NovelRepository? = null,
    currentSessionIdProvider: () -> String = { "local" },
    onSetCronEnabled: (suspend (Boolean) -> Unit)? = null,
    onUpdateCronConfig: (suspend (CronConfigUpdate) -> CronConfig)? = null,
    defaultTimeoutMsProvider: () -> Long = { 60_000L }
): ToolRegistry {
    val tools = buildCoreTools(context, memoryStore, agentRepository, novelRepository, currentSessionIdProvider)
    if (cronService != null) {
        tools += createCronToolSet(cronService, onSetCronEnabled, onUpdateCronConfig)
    }
    return ToolRegistry(
        initialTools = tools.associateBy { it.name },
        timeoutMsProvider = defaultTimeoutMsProvider
    )
}

private fun buildCoreTools(
    context: Context,
    memoryStore: MemoryStore,
    agentRepository: AgentRepository?,
    novelRepository: NovelRepository?,
    currentSessionIdProvider: () -> String
): MutableList<Tool> {
    val skillStore = SkillStore(context)
    val dynamicToolStore = DynamicToolStore(context)
    val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    return mutableListOf<Tool>(
        MessageTool()
    ).apply {
        addAll(createAndroidDeviceToolSet(context))
        addAll(createAndroidMediaToolSet(context))
        addAll(createAndroidBluetoothToolSet(context))
        addAll(createAndroidPersonalToolSet(context))
        addAll(createWebToolSet(client, context))
        addAll(createSummarizeToolSet(context, client))
        addAll(createWeatherToolSet(client))
        addAll(createUtilityToolSet())
        addAll(createSkillManagementToolSet(skillStore))
        if (agentRepository != null) addAll(createAgentManagementToolSet(agentRepository, currentSessionIdProvider))
        if (agentRepository != null && novelRepository != null) addAll(createNovelToolSet(novelRepository, agentRepository, currentSessionIdProvider))
        addAll(createDynamicToolManagementSet(dynamicToolStore))
        addAll(createDynamicPromptTools(dynamicToolStore))
        addAll(createFileToolSet(context, AppStoragePaths.storageRoot(context)))
        addAll(createMemoryToolSet(memoryStore, currentSessionIdProvider))
    }
}

