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
    val avatarPresetKey: String = "",
    val avatarImagePath: String = "",
    val avatarCropJson: String = "",
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
    val avatarPresetKey: String = "",
    val avatarImagePath: String = "",
    val avatarCropJson: String = "",
    val description: String,
    val persona: String,
    val speakingStyle: String,
    val boundaries: String,
    val scenario: String,
    val exampleDialog: String,
    val enabled: Boolean,
    val updatedAt: Long
)

data class UiAvatarInfo(
    val presetKey: String = "",
    val imagePath: String = "",
    val cropJson: String = "",
    val fallbackSymbol: String = ""
)

data class UiInlineTrace(
    val id: String,
    val sessionId: String,
    val title: String,
    val detail: String,
    val createdAt: Long,
    val sourceType: String = "状态",
    val sourceName: String = "状态",
    val rawPreview: String = ""
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
    Water("water", "水玻璃"),
    None("none", "无气泡");

    companion object {
        fun fromKey(key: String): UiBubbleStyle =
            values().firstOrNull { it.key == key } ?: Native
    }
}

enum class UiFontFamilyChoice(val key: String, val label: String) {
    System("system", "系统默认"),
    Sans("sans", "清爽无衬线"),
    Serif("serif", "阅读衬线"),
    Mono("mono", "代码等宽"),
    Custom("custom", "自定义字体");

    companion object {
        fun fromKey(key: String): UiFontFamilyChoice =
            values().firstOrNull { it.key == key } ?: when (key) {
                "rounded" -> Sans
                else -> System
            }
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

data class UiCompressionProgress(
    val running: Boolean = false,
    val manual: Boolean = false,
    val progress: Float = 0f,
    val stage: String = "",
    val path: String = ""
)

data class UiTerminalLine(
    val stream: String,
    val text: String,
    val createdAt: Long
)

data class UiTerminalRuntimeState(
    val enabled: Boolean = false,
    val ready: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val installing: Boolean = false,
    val installProgress: Float = 0f,
    val installMessage: String = "",
    val toolchainRoot: String = "",
    val shellPath: String = "",
    val installedExecutables: List<String> = emptyList(),
    val missingExecutables: List<String> = emptyList(),
    val activeCommand: String = "",
    val activeWorkspace: String = "",
    val activeJobId: String = "",
    val lastExitCode: Int? = null,
    val lastError: String = "",
    val recentOutput: List<UiTerminalLine> = emptyList()
)

/**
 * Single immutable view state consumed by the main chat UI.
 */
data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val pendingAttachments: List<UiPendingAttachment> = emptyList(),
    val isGenerating: Boolean = false,
    val activeTraceAnchorMessageId: Long? = null,
    val traceRunning: Boolean = false,
    val traceSessionId: String = "",
    val traceCompletedAt: Long = 0L,
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
    val themePreset: String = "obsidian_glass",
    val themeTextColorHex: String = "",
    val themeFontFamily: String = UiFontFamilyChoice.System.key,
    val themeBubbleStyle: String = UiBubbleStyle.Native.key,
    val themeUserBubbleColorHex: String = "#BFD8FF",
    val themeAssistantBubbleColorHex: String = "#F7FAFF",
    val themeToolBubbleColorHex: String = "#DDF7EC",
    val themeBubbleOpacity: Float = 0.78f,
    val themeBubbleCornerRadius: Float = 18f,
    val themeBubbleBorderAlpha: Float = 0.42f,
    val themeBubbleHighlightAlpha: Float = 0.38f,
    val themeBubbleShadowAlpha: Float = 0.18f,
    val themeBubbleGlassStrength: Float = 0.62f,
    val themeMessageFontSizeSp: Float = 14f,
    val themeMessageLineHeightMultiplier: Float = 1.18f,
    val themeCustomFontPath: String = "",
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
    val compressionProgress: UiCompressionProgress = UiCompressionProgress(),
    val terminalRuntime: UiTerminalRuntimeState = UiTerminalRuntimeState(),
    val skills: List<UiManagedSkill> = emptyList(),
    val dynamicTools: List<UiDynamicTool> = emptyList(),
    val compressedMemories: List<UiCompressedMemory> = emptyList(),
    val agentProfiles: List<UiAgentProfile> = emptyList(),
    val currentAgentBinding: UiSessionAgentBinding? = null,
    val currentAgentName: String = "",
    val roleCards: List<UiRoleCard> = emptyList(),
    val activeRoleCardId: String = "",
    val currentRoleCardName: String = "",
    val currentAgentAvatar: UiAvatarInfo = UiAvatarInfo(),
    val currentRoleCardAvatar: UiAvatarInfo = UiAvatarInfo(),
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
    val inlineTraces: List<UiInlineTrace> = emptyList(),
    val settingsInfo: String? = null
)




