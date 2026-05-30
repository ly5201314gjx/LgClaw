package com.lgclaw.ui

import com.lgclaw.config.AppSession
import com.lgclaw.providers.ProviderCatalog
import com.lgclaw.providers.ProviderProtocol
import com.lgclaw.config.AppLimits


data class UiManagedSkill(
    val name: String,
    val description: String,
    val enabled: Boolean,
    val source: String,
    val path: String,
    val updatedAt: Long
)

data class UiDynamicTool(
    val name: String,
    val description: String,
    val prompt: String,
    val enabled: Boolean,
    val updatedAt: Long
)

data class UiCompressedMemory(
    val id: String,
    val sessionId: String,
    val createdAt: Long,
    val algorithm: String,
    val summary: String,
    val originalChars: Int,
    val compressedBytes: Int,
    val messageCount: Int
)

data class UiAgentProfile(
    val id: String,
    val name: String,
    val type: String,
    val description: String,
    val systemPrompt: String,
    val enabled: Boolean,
    val defaultSkills: List<String>,
    val dynamicTools: List<String>,
    val updatedAt: Long
)

data class UiSessionAgentBinding(
    val sessionId: String,
    val agentId: String?,
    val activeNovelProjectId: String?,
    val activeRoleCardId: String?
)

data class UiRoleCard(
    val id: String,
    val name: String,
    val avatarSymbol: String,
    val description: String,
    val persona: String,
    val speakingStyle: String,
    val boundaries: String,
    val scenario: String,
    val exampleDialog: String,
    val enabled: Boolean,
    val updatedAt: Long
)

data class UiNovelProject(
    val id: String,
    val title: String,
    val genre: String,
    val styleGuide: String,
    val premise: String,
    val updatedAt: Long
)

data class UiNovelChapter(
    val id: String,
    val projectId: String,
    val chapterIndex: Int,
    val title: String,
    val summary: String,
    val content: String,
    val keywords: List<String>,
    val updatedAt: Long
)

data class UiNovelCharacter(
    val id: String,
    val projectId: String,
    val name: String,
    val aliases: List<String>,
    val goal: String,
    val secret: String,
    val arc: String,
    val notes: String,
    val updatedAt: Long
)

data class UiNovelRelation(
    val fromName: String,
    val toName: String,
    val label: String,
    val weight: Double,
    val evidence: List<String>
)

data class UiNovelWorldNote(
    val id: String,
    val projectId: String,
    val category: String,
    val title: String,
    val content: String,
    val updatedAt: Long
)

data class UiNovelAnalysis(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String,
    val createdAt: Long
)

enum class UiPlanModeLevel(val label: String) {
    Off("关闭"),
    Quick("快速计划"),
    Standard("标准计划"),
    Deep("深度计划"),
    Codex("Codex 调度")
}

enum class UiBubbleStyle(val key: String, val label: String) {
    Native("native", "原生气泡"),
    Frosted("frosted", "毛玻璃"),
    Water("water", "水玻璃");

    companion object {
        fun fromKey(key: String): UiBubbleStyle =
            values().firstOrNull { it.key == key } ?: Native
    }
}

enum class UiFontFamilyChoice(val key: String, val label: String) {
    System("system", "系统默认"),
    Serif("serif", "衬线字体"),
    Mono("mono", "等宽字体"),
    Rounded("rounded", "圆润字体");

    companion object {
        fun fromKey(key: String): UiFontFamilyChoice =
            values().firstOrNull { it.key == key } ?: System
    }
}

data class UiPendingPlan(
    val id: String,
    val sessionId: String,
    val originalTask: String,
    val mode: UiPlanModeLevel,
    val planText: String,
    val createdAt: Long,
    val additions: String = ""
)
/**
 * Single immutable view state consumed by the main chat UI.
 */
data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val pendingAttachments: List<UiPendingAttachment> = emptyList(),
    val isGenerating: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val userDisplayName: String = "",
    val agentDisplayName: String = "LGClaw",
    val onboardingUserDisplayName: String = "",
    val onboardingAgentDisplayName: String = "LGClaw",
    val sessions: List<UiSessionSummary> = listOf(
        UiSessionSummary(
            id = AppSession.LOCAL_SESSION_ID,
            title = AppSession.LOCAL_SESSION_TITLE,
            isLocal = true
        )
    ),
    val currentSessionId: String = AppSession.LOCAL_SESSION_ID,
    val currentSessionTitle: String = AppSession.LOCAL_SESSION_TITLE,
    val settingsProviderConfigs: List<UiProviderConfig> = emptyList(),
    val settingsEditingProviderConfigId: String = "",
    val settingsProvider: String = AppLimits.DEFAULT_PROVIDER,
    val settingsProviderCustomName: String = "",
    val settingsProviderProtocol: ProviderProtocol = ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER),
    val settingsBaseUrl: String = ProviderCatalog.defaultBaseUrl(
        AppLimits.DEFAULT_PROVIDER,
        ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER)
    ),
    val settingsModel: String = ProviderCatalog.defaultModel(
        AppLimits.DEFAULT_PROVIDER,
        ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER)
    ),
    val settingsApiKey: String = "",
    val settingsEquippedModels: List<String> = emptyList(),
    val settingsDiscoveredModels: List<String> = emptyList(),
    val settingsModelFetching: Boolean = false,
    val settingsMaxToolRounds: String = AppLimits.DEFAULT_MAX_TOOL_ROUNDS.toString(),
    val settingsToolResultMaxChars: String = AppLimits.DEFAULT_TOOL_RESULT_MAX_CHARS.toString(),
    val settingsMemoryConsolidationWindow: String = AppLimits.DEFAULT_MEMORY_CONSOLIDATION_WINDOW.toString(),
    val settingsCompressionThresholdK: String = AppLimits.DEFAULT_COMPRESSION_THRESHOLD_K.toString(),
    val settingsLlmCallTimeoutSeconds: String = AppLimits.DEFAULT_LLM_CALL_TIMEOUT_SECONDS.toString(),
    val settingsLlmConnectTimeoutSeconds: String = AppLimits.DEFAULT_LLM_CONNECT_TIMEOUT_SECONDS.toString(),
    val settingsLlmReadTimeoutSeconds: String = AppLimits.DEFAULT_LLM_READ_TIMEOUT_SECONDS.toString(),
    val settingsDefaultToolTimeoutSeconds: String = AppLimits.DEFAULT_TOOL_TIMEOUT_SECONDS.toString(),
    val settingsContextMessages: String = AppLimits.DEFAULT_CONTEXT_MESSAGES.toString(),
    val settingsToolArgsPreviewMaxChars: String = AppLimits.DEFAULT_TOOL_ARGS_PREVIEW_MAX_CHARS.toString(),
    val settingsTokenInput: Long = 0L,
    val settingsTokenOutput: Long = 0L,
    val settingsTokenTotal: Long = 0L,
    val settingsTokenCachedInput: Long = 0L,
    val settingsTokenRequests: Long = 0L,
    val settingsCronEnabled: Boolean = false,
    val settingsCronMinEveryMs: String = AppLimits.DEFAULT_CRON_MIN_EVERY_MS.toString(),
    val settingsCronMaxJobs: String = AppLimits.DEFAULT_CRON_MAX_JOBS.toString(),
    val settingsCronJobs: List<UiCronJob> = emptyList(),
    val settingsCronJobsLoading: Boolean = false,
    val settingsCronLogs: String = "",
    val settingsAgentLogs: String = "",
    val settingsHeartbeatEnabled: Boolean = false,
    val settingsHeartbeatIntervalSeconds: String = AppLimits.DEFAULT_HEARTBEAT_INTERVAL_SECONDS.toString(),
    val settingsGatewayEnabled: Boolean = false,
    val settingsUseChinese: Boolean = false,
    val settingsDarkTheme: Boolean = false,
    val themeTextColorHex: String = "",
    val themeFontFamily: String = UiFontFamilyChoice.System.key,
    val themeBubbleStyle: String = UiBubbleStyle.Native.key,
    val chatBackgroundPath: String = "",
    val chatBackgroundOpacity: Float = 0.18f,
    val chatBackgroundBlur: Float = 0f,
    val chatBackgroundGlass: Float = 0.18f,
    val drawerBackgroundPath: String = "",
    val drawerBackgroundOpacity: Float = 0.22f,
    val drawerBackgroundBlur: Float = 0f,
    val drawerBackgroundGlass: Float = 0.22f,
    val alwaysOnEnabled: Boolean = false,
    val alwaysOnKeepScreenAwake: Boolean = false,
    val alwaysOnServiceRunning: Boolean = false,
    val alwaysOnNotificationActive: Boolean = false,
    val alwaysOnGatewayRunning: Boolean = false,
    val alwaysOnNetworkConnected: Boolean = false,
    val alwaysOnCharging: Boolean = false,
    val alwaysOnBatteryOptimizationIgnored: Boolean = false,
    val alwaysOnExactAlarmAllowed: Boolean = false,
    val alwaysOnActiveAdapterCount: Int = 0,
    val alwaysOnStartedAtMs: Long = 0L,
    val alwaysOnLastError: String = "",
    val settingsTelegramBotToken: String = "",
    val settingsTelegramAllowedChatId: String = "",
    val settingsDiscordWebhookUrl: String = "",
    val settingsConnectedChannels: List<UiConnectedChannelSummary> = emptyList(),
    val settingsDiscordGatewayStatus: String = "",
    val settingsSlackGatewayStatus: String = "",
    val settingsFeishuGatewayStatus: String = "",
    val settingsEmailGatewayStatus: String = "",
    val settingsWeComGatewayStatus: String = "",
    val settingsMcpEnabled: Boolean = false,
    val settingsMcpServerName: String = AppLimits.DEFAULT_MCP_HTTP_SERVER_NAME,
    val settingsMcpServerUrl: String = "",
    val settingsMcpAuthToken: String = "",
    val settingsMcpToolTimeoutSeconds: String = AppLimits.DEFAULT_MCP_HTTP_TOOL_TIMEOUT_SECONDS.toString(),
    val settingsMcpServers: List<UiMcpServerConfig> = emptyList(),
    val settingsHeartbeatDoc: String = "",
    val settingsProviderTesting: Boolean = false,
    val settingsUpdateChecking: Boolean = false,
    val settingsUpdateAvailable: Boolean = false,
    val settingsUpdatePromptVisible: Boolean = false,
    val settingsUpdateNoticeVisible: Boolean = false,
    val settingsUpdateNoticeTitle: String = "",
    val settingsUpdateNoticeMessage: String = "",
    val settingsUpdateNoticeActionLabel: String = "",
    val settingsUpdateNoticeActionUrl: String = "",
    val settingsCurrentVersion: String = "",
    val settingsLatestVersion: String = "",
    val settingsUpdateReleaseUrl: String = "",
    val settingsUpdateDownloadUrl: String = "",
    val sessionBindingTelegramDiscovering: Boolean = false,
    val sessionBindingTelegramDiscoveryAttempted: Boolean = false,
    val sessionBindingTelegramCandidates: List<UiTelegramChatCandidate> = emptyList(),
    val sessionBindingTelegramInfo: String? = null,
    val sessionBindingFeishuDiscovering: Boolean = false,
    val sessionBindingFeishuDiscoveryAttempted: Boolean = false,
    val sessionBindingFeishuCandidates: List<UiFeishuChatCandidate> = emptyList(),
    val sessionBindingFeishuInfo: String? = null,
    val sessionBindingEmailDiscovering: Boolean = false,
    val sessionBindingEmailDiscoveryAttempted: Boolean = false,
    val sessionBindingEmailCandidates: List<UiEmailSenderCandidate> = emptyList(),
    val sessionBindingEmailInfo: String? = null,
    val sessionBindingWeComDiscovering: Boolean = false,
    val sessionBindingWeComDiscoveryAttempted: Boolean = false,
    val sessionBindingWeComCandidates: List<UiWeComChatCandidate> = emptyList(),
    val sessionBindingWeComInfo: String? = null,
    val settingsSaving: Boolean = false,
    val currentConversationK: Double = 0.0,
    val skills: List<UiManagedSkill> = emptyList(),
    val dynamicTools: List<UiDynamicTool> = emptyList(),
    val compressedMemories: List<UiCompressedMemory> = emptyList(),
    val agentProfiles: List<UiAgentProfile> = emptyList(),
    val currentAgentBinding: UiSessionAgentBinding? = null,
    val currentAgentName: String = "",
    val roleCards: List<UiRoleCard> = emptyList(),
    val activeRoleCardId: String = "",
    val currentRoleCardName: String = "",
    val novelProjects: List<UiNovelProject> = emptyList(),
    val activeNovelProjectId: String = "",
    val novelChapters: List<UiNovelChapter> = emptyList(),
    val novelCharacters: List<UiNovelCharacter> = emptyList(),
    val novelRelations: List<UiNovelRelation> = emptyList(),
    val novelWorldNotes: List<UiNovelWorldNote> = emptyList(),
    val novelAnalyses: List<UiNovelAnalysis> = emptyList(),
    val chatSearchQuery: String = "",
    val chatSearchResultIds: List<Long> = emptyList(),
    val chatSearchCurrentIndex: Int = 0,
    val planModeLevel: UiPlanModeLevel = UiPlanModeLevel.Off,
    val pendingPlan: UiPendingPlan? = null,
    val isPlanning: Boolean = false,
    val settingsInfo: String? = null
)




