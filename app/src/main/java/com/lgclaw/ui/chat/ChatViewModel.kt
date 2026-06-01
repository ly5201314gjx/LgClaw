package com.lgclaw.ui

import android.app.Application
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.os.Build
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lgclaw.runtime.AlwaysOnModeController
import com.lgclaw.runtime.AlwaysOnHealthCheckWorker
import com.lgclaw.agent.AgentLogStore
import com.lgclaw.agents.AgentRepository
import com.lgclaw.agent.AgentLoop
import com.lgclaw.agent.ContextBuilder
import com.lgclaw.agent.MemoryConsolidator
import com.lgclaw.agent.PlanningDispatcher
import com.lgclaw.agent.SubagentManager
import com.lgclaw.agent.ToolCallParser
import com.lgclaw.bus.InboundMessage
import com.lgclaw.bus.MessageBus
import com.lgclaw.bus.OutboundMessage
import com.lgclaw.channels.DiscordChannelAdapter
import com.lgclaw.channels.DiscordGatewayDiagnostics
import com.lgclaw.channels.DiscordRouteRule
import com.lgclaw.channels.ChannelRuntimeDiagnostics
import com.lgclaw.channels.EmailAccountConfig
import com.lgclaw.channels.EmailChannelAdapter
import com.lgclaw.channels.EmailGatewayDiagnostics
import com.lgclaw.channels.buildFeishuAdapterSeeds
import com.lgclaw.channels.buildFeishuTargetAliases
import com.lgclaw.channels.FeishuChannelAdapter
import com.lgclaw.channels.FeishuGatewayDiagnostics
import com.lgclaw.channels.FeishuRouteRule
import com.lgclaw.channels.GatewayOrchestrator
import com.lgclaw.channels.SlackChannelAdapter
import com.lgclaw.channels.SlackGatewayDiagnostics
import com.lgclaw.channels.SlackRouteRule
import com.lgclaw.channels.TelegramChannelAdapter
import com.lgclaw.channels.WeComChannelAdapter
import com.lgclaw.channels.WeComGatewayDiagnostics
import com.lgclaw.channels.WeComRouteRule
import com.lgclaw.config.AppLimits
import com.lgclaw.config.AppSession
import com.lgclaw.config.AppStoragePaths
import com.lgclaw.config.AlwaysOnConfig
import com.lgclaw.config.ChannelsConfig
import com.lgclaw.config.ConfigStore
import com.lgclaw.config.CronConfig
import com.lgclaw.config.HeartbeatDoc
import com.lgclaw.config.HeartbeatConfig
import com.lgclaw.config.McpHttpConfig
import com.lgclaw.config.McpHttpServerConfig
import com.lgclaw.config.OnboardingConfig
import com.lgclaw.config.ProviderConnectionConfig
import com.lgclaw.config.SessionChannelBindingRules
import com.lgclaw.config.SessionChannelBinding
import com.lgclaw.config.UiPreferencesConfig
import com.lgclaw.cron.CronLogStore
import com.lgclaw.cron.CronJob
import com.lgclaw.cron.CronRepository
import com.lgclaw.cron.CronService
import com.lgclaw.heartbeat.HeartbeatService
import com.lgclaw.memory.MemoryStore
import com.lgclaw.memory.CompressedMemoryStore
import com.lgclaw.memory.CompressionPolicy
import com.lgclaw.providers.AdaptiveLlmProvider
import com.lgclaw.providers.ChatMessage
import com.lgclaw.providers.LlmProviderFactory
import com.lgclaw.providers.ProviderCatalog
import com.lgclaw.providers.ProviderProtocol
import com.lgclaw.providers.ProviderResolutionStore
import com.lgclaw.providers.ToolCall
import com.lgclaw.runtime.RuntimeController
import com.lgclaw.skills.SkillsLoader
import com.lgclaw.storage.AppDatabase
import com.lgclaw.storage.MessageRepository
import com.lgclaw.storage.SessionRepository
import com.lgclaw.storage.entities.MessageEntity
import com.lgclaw.storage.entities.SessionEntity
import com.lgclaw.templates.TemplateStore
import com.lgclaw.tools.MessageTool
import com.lgclaw.tools.McpHttpRuntime
import com.lgclaw.tools.McpStatusTool
import com.lgclaw.tools.HeartbeatGetTool
import com.lgclaw.tools.HeartbeatSetTool
import com.lgclaw.tools.HeartbeatTriggerTool
import com.lgclaw.tools.ChannelsGetTool
import com.lgclaw.tools.ChannelsSetTool
import com.lgclaw.tools.RuntimeGetTool
import com.lgclaw.tools.RuntimeSetTool
import com.lgclaw.tools.SessionsListTool
import com.lgclaw.tools.SessionsSendTool
import com.lgclaw.tools.SpawnTool
import com.lgclaw.tools.createToolRegistry
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class ChatViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val environment = ChatViewModelEnvironment(app)
    private val storageMigration: Unit = environment.storageMigration
    private val database = environment.database
    private val messageRepository = environment.messageRepository
    private val sessionRepository = environment.sessionRepository
    private val cronRepository = environment.cronRepository
    private val cronService = environment.cronService
    private val cronLogStore = environment.cronLogStore
    private val agentLogStore = environment.agentLogStore
    private val configStore = environment.configStore
    private val providerResolutionStore = environment.providerResolutionStore
    private val memoryStore = environment.memoryStore
    private val compressedMemoryStore = environment.compressedMemoryStore
    private val skillStore = environment.skillStore
    private val dynamicToolStore = environment.dynamicToolStore
    private val agentRepository = environment.agentRepository
    private val novelRepository = environment.novelRepository
    private val roleCardRepository = environment.roleCardRepository
    private val templateStore = environment.templateStore
    private val heartbeatDocFile = environment.heartbeatDocFile

    private var currentSessionId: String =
        configStore.getLastActiveSessionId() ?: AppSession.LOCAL_SESSION_ID
    private val initialUiPrefs = configStore.getUiPreferencesConfig()
    private val initialOnboarding = OnboardingCoordinator.resolveSyncedOnboardingConfig(
        configStore = configStore,
        memoryStore = memoryStore,
        baseConfig = configStore.getOnboardingConfig()
    )
    private val _uiState = ChatStateStore(
        ChatUiState(
            currentSessionId = currentSessionId,
            currentSessionTitle = if (currentSessionId == AppSession.LOCAL_SESSION_ID) {
                AppSession.LOCAL_SESSION_TITLE
            } else {
                currentSessionId
            },
            settingsUseChinese = initialUiPrefs.useChinese,
            settingsDarkTheme = initialUiPrefs.darkTheme,
            themeTextColorHex = initialUiPrefs.themeTextColorHex,
            themeFontFamily = initialUiPrefs.themeFontFamily,
            themeBubbleStyle = initialUiPrefs.themeBubbleStyle,
            themePreset = initialUiPrefs.themePreset,
            themeUserBubbleColorHex = initialUiPrefs.themeUserBubbleColorHex,
            themeAssistantBubbleColorHex = initialUiPrefs.themeAssistantBubbleColorHex,
            themeToolBubbleColorHex = initialUiPrefs.themeToolBubbleColorHex,
            themeBubbleOpacity = initialUiPrefs.themeBubbleOpacity,
            themeBubbleCornerRadius = initialUiPrefs.themeBubbleCornerRadius,
            themeBubbleBorderAlpha = initialUiPrefs.themeBubbleBorderAlpha,
            themeBubbleHighlightAlpha = initialUiPrefs.themeBubbleHighlightAlpha,
            themeBubbleShadowAlpha = initialUiPrefs.themeBubbleShadowAlpha,
            themeBubbleGlassStrength = initialUiPrefs.themeBubbleGlassStrength,
            themeMessageFontSizeSp = initialUiPrefs.themeMessageFontSizeSp,
            themeMessageLineHeightMultiplier = initialUiPrefs.themeMessageLineHeightMultiplier,
            themeCustomFontPath = initialUiPrefs.themeCustomFontPath,
            chatBackgroundPath = initialUiPrefs.chatBackgroundPath,
            chatBackgroundOpacity = initialUiPrefs.chatBackgroundOpacity,
            chatBackgroundBlur = initialUiPrefs.chatBackgroundBlur,
            chatBackgroundGlass = initialUiPrefs.chatBackgroundGlass,
            drawerBackgroundPath = initialUiPrefs.drawerBackgroundPath,
            drawerBackgroundOpacity = initialUiPrefs.drawerBackgroundOpacity,
            drawerBackgroundBlur = initialUiPrefs.drawerBackgroundBlur,
            drawerBackgroundGlass = initialUiPrefs.drawerBackgroundGlass,
            onboardingCompleted = initialOnboarding.completed,
            userDisplayName = initialOnboarding.userDisplayName,
            agentDisplayName = initialOnboarding.agentDisplayName,
            onboardingUserDisplayName = initialOnboarding.userDisplayName,
            onboardingAgentDisplayName = initialOnboarding.agentDisplayName
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val uiJson = environment.uiJson

    private var generatingJob: Job? = null
    private var compressionJob: Job? = null
    private val autoCompressionSessions = mutableSetOf<String>()
    private var firstRunAutoIntroPending = false
    private var mcpServerStatuses: Map<String, UiMcpServerRuntimeStatus> = emptyMap()
    private val gatewayProcessingSessions = mutableSetOf<String>()
    private var runtimeProcessingSessions: Set<String> = emptySet()
    private var alwaysOnProcessingSessions: Set<String> = emptySet()
    @Volatile
    private var pendingGatewayConfig: ChannelsConfig? = null
    private val telegramDiscoveryClient = environment.telegramDiscoveryClient
    private val sessionCoordinator = ChatSessionCoordinator(
        scope = viewModelScope,
        stateStore = _uiState,
        dependencies = ChatSessionCoordinator.Dependencies(
            currentSessionId = { currentSessionId },
            setCurrentSessionId = { currentSessionId = it },
            saveLastActiveSessionId = { configStore.saveLastActiveSessionId(it) },
            computeIsGeneratingForSession = ::computeIsGeneratingForSession,
            observeSessionsSource = { sessionRepository.observeSessions() },
            observeMessagesSource = { sessionId -> messageRepository.observeMessages(sessionId) },
            buildSessionSummaries = ::buildSessionSummaries,
            buildConnectedChannelsOverview = ::buildConnectedChannelsOverview,
            mapObservedMessagesToUi = { messages ->
                refreshConversationMetrics(messages)
                mapMessagesToUi(messages.filter { it.shouldDisplayInChat() })
            },
            resolveOnboardingConfig = { onboardingCoordinator.resolveSyncedOnboardingConfig() }
        ),
        actions = ChatSessionCoordinator.Actions(
            bootstrapLocalSessions = ::bootstrapLocalSessions,
            sendMessage = ::sendMessageInternal,
            stopGeneration = ::stopGenerationInternal,
            createSession = ::createSessionInternal,
            renameSession = ::renameSessionInternal,
            deleteSession = ::deleteSessionInternal
        )
    )
    private val providerSettingsCoordinator = ProviderSettingsCoordinator(
        stateStore = _uiState,
        clearTokenUsageStats = {
            configStore.clearTokenUsageStats()
            configStore.getTokenUsageStats()
        },
        persistOnboardingProviderDraftIfNeeded = ::persistOnboardingProviderDraftIfNeeded,
        actions = ProviderSettingsCoordinator.Actions(
            setActiveProviderConfig = ::setActiveProviderConfigInternal,
            deleteProviderConfig = ::deleteProviderConfigInternal,
            saveProviderSettings = ::saveProviderSettingsInternal,
            saveAgentRuntimeSettings = ::saveAgentRuntimeSettingsInternal,
            testProviderSettings = ::testProviderSettingsInternal
        )
    )
    private val channelBindingCoordinator = ChannelBindingCoordinator(
        ChannelBindingCoordinator.Actions(
            saveSessionChannelBinding = ::saveSessionChannelBindingRequestInternal,
            getSessionChannelDraft = ::getSessionChannelDraftInternal,
            setSessionChannelEnabled = ::setSessionChannelEnabledInternalFacade,
            discoverTelegramChatsForBinding = ::discoverTelegramChatsForBindingInternal,
            clearTelegramChatDiscovery = ::clearTelegramChatDiscoveryInternal,
            discoverFeishuChatsForBinding = ::discoverFeishuChatsForBindingInternal,
            clearFeishuChatDiscovery = ::clearFeishuChatDiscoveryInternal,
            discoverEmailSendersForBinding = ::discoverEmailSendersForBindingInternal,
            clearEmailSenderDiscovery = ::clearEmailSenderDiscoveryInternal,
            discoverWeComChatsForBinding = ::discoverWeComChatsForBindingInternal,
            clearWeComChatDiscovery = ::clearWeComChatDiscoveryInternal,
            refreshSessionConnectionStatus = ::refreshSessionConnectionStatusInternal
        )
    )
    private val runtimeCoordinator = RuntimeCoordinator(
        stateStore = _uiState,
        actions = RuntimeCoordinator.Actions(
            loadSettingsIntoState = ::loadSettingsIntoState,
            observeRuntimeStatus = ::observeRuntimeStatus,
            observeAlwaysOnStatus = ::observeAlwaysOnStatus,
            startGatewayIfEnabled = ::startGatewayIfEnabled,
            refreshAlwaysOnDiagnostics = ::refreshAlwaysOnDiagnosticsInternal,
            refreshCronJobs = ::refreshCronJobsInternal,
            setCronJobEnabled = ::setCronJobEnabledInternal,
            runCronJobNow = ::runCronJobNowInternal,
            removeCronJob = ::removeCronJobInternal,
            triggerHeartbeatNow = ::triggerHeartbeatNowInternal,
            loadHeartbeatDocument = ::loadHeartbeatDocumentInternal,
            saveHeartbeatDocument = ::saveHeartbeatDocumentInternal,
            refreshCronLogs = ::refreshCronLogsInternal,
            clearCronLogs = ::clearCronLogsInternal,
            refreshAgentLogs = ::refreshAgentLogsInternal,
            clearAgentLogs = ::clearAgentLogsInternal,
            saveCronSettings = ::saveCronSettingsInternal,
            saveHeartbeatSettings = ::saveHeartbeatSettingsInternal,
            saveAlwaysOnSettings = ::saveAlwaysOnSettingsInternal,
            saveChannelsSettings = ::saveChannelsSettingsInternal,
            saveMcpSettings = ::saveMcpSettingsInternal
        )
    )
    private val onboardingCoordinator = OnboardingCoordinator(
        scope = viewModelScope,
        stateStore = _uiState,
        configStore = configStore,
        memoryStore = memoryStore,
        buildProviderStateWithSavedDraft = ::buildProviderStateWithSavedDraft,
        buildProviderSettingsConfig = ::buildProviderSettingsConfig,
        selectLocalSession = { selectSession(AppSession.LOCAL_SESSION_ID) },
        loadSettingsIntoState = ::loadSettingsIntoState,
        maybeTriggerFirstRunAutoIntro = ::maybeTriggerFirstRunAutoIntro
    )
    private val appUpdateCoordinator = AppUpdateCoordinator(
        app = app,
        scope = viewModelScope,
        stateStore = _uiState,
        configStore = configStore,
        updateCheckClient = environment.updateCheckClient
    )

    init {
        storageMigration
        sessionCoordinator.bootstrapLocalSessions()
        runtimeCoordinator.loadSettingsIntoState()
        runtimeCoordinator.observeRuntimeStatus()
        runtimeCoordinator.observeAlwaysOnStatus()
        sessionCoordinator.observeSessions()
        sessionCoordinator.observeMessages(currentSessionId)
        runtimeCoordinator.startGatewayIfEnabled()
        runtimeCoordinator.refreshAlwaysOnDiagnostics()
        appUpdateCoordinator.bootstrapAutomaticCheck()
        refreshExtensionPanels()
        refreshAgentPanels()
    }

    fun onInputChanged(value: String): Unit {
        sessionCoordinator.onInputChanged(value)
    }
    fun deleteMessages(messageIds: List<Long>) {
        val ids = messageIds.distinct().filter { it > 0L }
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ids.forEach { messageRepository.deleteMessage(it) }
                sessionRepository.touch(currentSessionId)
            }.onSuccess { showSettingsInfo("已删除 ${ids.size} 条消息") }
                .onFailure { showSettingsInfo("删除消息失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun polishMessageToInput(content: String) {
        val source = content.trim()
        if (source.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsInfo = "正在润色消息…") }
            runCatching {
                val config = configStore.getConfig()
                val provider = LlmProviderFactory(providerResolutionStore).create(config)
                val response = withContext(Dispatchers.IO) {
                    provider.chat(
                        messages = listOf(
                            ChatMessage(
                                role = "system",
                                content = "你是中文文本润色助手。只输出润色后的文本，不解释、不加标题。保持原意，语气更自然、有活人感，避免AI腔。"
                            ),
                            ChatMessage(
                                role = "user",
                                content = source
                            )
                        ),
                        toolsSpec = emptyList()
                    )
                }
                response.assistant.content.trim().ifBlank { source }
            }.onSuccess { polished ->
                sessionCoordinator.onInputChanged(polished)
                _uiState.update { it.copy(settingsInfo = "润色结果已放入输入框") }
            }.onFailure { t ->
                val localPolished = localPolishText(source)
                sessionCoordinator.onInputChanged(localPolished)
                _uiState.update { it.copy(settingsInfo = "AI 润色失败，已用本地规则润色并放入输入框：${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }


    fun attachFilesToInput(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                uris.take(8).map { uri -> saveInputAttachment(app, uri) }
            }.onSuccess { attachments ->
                _uiState.update { state ->
                    state.copy(pendingAttachments = (state.pendingAttachments + attachments).takeLast(8))
                }
                showSettingsInfo("附件已加入顶部附件栏，发送后 AI 会读取附件内容")
            }
                .onFailure { showSettingsInfo("附件添加失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun attachImagesToInput(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                uris.take(8).map { uri ->
                    val mime = app.contentResolver.getType(uri).orEmpty()
                    if (!mime.startsWith("image/") && !guessMimeFromName(queryDisplayName(app, uri)).startsWith("image/")) {
                        throw IllegalArgumentException("图片入口只支持上传图片")
                    }
                    saveInputAttachment(app, uri)
                }
            }.onSuccess { attachments ->
                _uiState.update { state ->
                    state.copy(pendingAttachments = (state.pendingAttachments + attachments).takeLast(8))
                }
                showSettingsInfo("图片已加入顶部缩略图栏，发送后会随消息交给多模态模型识别")
            }.onFailure { showSettingsInfo("图片添加失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun removePendingAttachment(id: String) {
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filterNot { it.id == id })
        }
    }

    private fun saveInputAttachment(app: Application, uri: Uri): UiPendingAttachment {
        val resolver = app.contentResolver
        val name = queryDisplayName(app, uri).ifBlank { "attachment_${System.currentTimeMillis()}" }
        val mime = resolver.getType(uri).orEmpty().ifBlank { guessMimeFromName(name) }
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80).ifBlank { "file" }
        val dir = File(AppStoragePaths.storageRoot(app), "attachments").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}_$safeName")
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取所选文件")
        val kind = if (mime.startsWith("image/")) UiMediaKind.Image else UiMediaKind.Document
        val finalFile = if (kind == UiMediaKind.Image) prepareVisionImage(target, mime) else target
        val finalMime = if (kind == UiMediaKind.Image && finalFile.extension.equals("jpg", ignoreCase = true)) "image/jpeg" else mime
        return UiPendingAttachment(
            id = UUID.randomUUID().toString().take(8),
            name = finalFile.name,
            mimeType = finalMime,
            path = finalFile.absolutePath,
            sizeBytes = finalFile.length(),
            kind = kind,
            textPreview = extractTextPreview(finalFile, finalMime)
        )
    }

    private fun prepareVisionImage(file: File, mime: String): File {
        if (file.length() <= MAX_VISION_ATTACHMENT_BYTES && mime.lowercase(Locale.US) in VISION_DIRECT_MIME_TYPES) {
            return file
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return file
        var sample = 1
        while ((options.outWidth / sample) > MAX_VISION_IMAGE_SIDE || (options.outHeight / sample) > MAX_VISION_IMAGE_SIDE) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        ) ?: return file
        return try {
            val dir = file.parentFile ?: return file
            val outFile = File(dir, file.nameWithoutExtension + "_vision.jpg")
            val output = ByteArrayOutputStream()
            var quality = 88
            do {
                output.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                quality -= 8
            } while (output.size() > MAX_VISION_ATTACHMENT_BYTES && quality >= 56)
            if (output.size() <= MAX_VISION_ATTACHMENT_BYTES) {
                FileOutputStream(outFile).use { it.write(output.toByteArray()) }
                outFile
            } else {
                file
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun queryDisplayName(app: Application, uri: Uri): String {
        return runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault(uri.lastPathSegment.orEmpty())
    }

    private fun guessMimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt", "md", "csv", "json", "xml", "html", "htm", "log", "kt", "java", "py", "js", "ts", "css" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun extractTextPreview(file: File, mime: String): String {
        val extension = file.extension.lowercase(Locale.US)
        if (extension == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
            return extractDocxTextPreview(file)
        }
        if (extension == "pdf" || mime == "application/pdf") {
            return extractPdfTextPreview(file)
        }
        val textLike = mime.startsWith("text/") || extension in setOf(
            "txt", "md", "csv", "json", "xml", "html", "htm", "log", "kt", "java", "py", "js", "ts", "css"
        )
        if (!textLike || file.length() > 2L * 1024L * 1024L) return ""
        return runCatching {
            val bytes = file.readBytes().take(24 * 1024).toByteArray()
            String(bytes, Charset.forName("UTF-8"))
                .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]+"), " ")
                .take(6000)
                .trim()
        }.getOrDefault("")
    }

    private fun extractDocxTextPreview(file: File): String {
        return runCatching {
            ZipInputStream(file.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { it.name == "word/document.xml" }
                    ?.let {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        xml.replace(Regex("<w:(p|br)[^>]*>"), "\n")
                            .replace(Regex("<[^>]+>"), "")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .replace("&quot;", "\"")
                            .replace("&apos;", "'")
                            .replace(Regex("[ \t]+"), " ")
                            .replace(Regex("\n{3,}"), "\n\n")
                            .take(6000)
                            .trim()
                    }.orEmpty()
            }
        }.getOrDefault("")
    }

    private fun extractPdfTextPreview(file: File): String {
        if (file.length() > 12L * 1024L * 1024L) return ""
        return runCatching {
            val raw = file.readBytes().toString(Charsets.ISO_8859_1)
            val textObjects = Regex("BT(.*?)ET", RegexOption.DOT_MATCHES_ALL)
                .findAll(raw)
                .flatMap { block ->
                    Regex("""\((?:\\.|[^\\)]){2,}\)""").findAll(block.groupValues[1])
                }
                .map { decodePdfLiteral(it.value.drop(1).dropLast(1)) }
                .filter { it.count(Char::isLetterOrDigit) >= 2 }
                .joinToString(" ")
            val fallback = if (textObjects.isBlank()) {
                Regex("""\((?:\\.|[^\\)]){4,}\)""").findAll(raw)
                    .map { decodePdfLiteral(it.value.drop(1).dropLast(1)) }
                    .filter { it.count(Char::isLetterOrDigit) >= 4 }
                    .take(180)
                    .joinToString(" ")
            } else {
                textObjects
            }
            fallback.replace(Regex("\\s+"), " ").take(6000).trim()
        }.getOrDefault("")
    }

    private fun decodePdfLiteral(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .replace("\\t", "\t")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
    }
    fun sendMessage(): Unit {
        sessionCoordinator.sendMessage()
    }

    fun setPlanModeLevel(level: UiPlanModeLevel) {
        _uiState.update { state ->
            state.copy(
                planModeLevel = level,
                pendingPlan = if (level == UiPlanModeLevel.Off) null else state.pendingPlan
            )
        }
        showSettingsInfo(if (level == UiPlanModeLevel.Off) "计划模式已关闭" else "计划模式已切换为：${level.label}")
    }

    fun clearPendingPlan() {
        generatingJob?.cancel()
        generatingJob = null
        _uiState.update { it.copy(pendingPlan = null, isPlanning = false, isGenerating = false) }
        showSettingsInfo("已取消本次计划，不会执行任务")
    }

    fun addToPendingPlan(addition: String) {
        val text = addition.trim()
        if (text.isBlank()) {
            showSettingsInfo("请先在输入框写下要追加的要求")
            return
        }
        val pending = _uiState.value.pendingPlan
        if (pending == null) {
            showSettingsInfo("当前没有待确认计划")
            return
        }
        sessionCoordinator.onInputChanged("")
        generatingJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isPlanning = true, isGenerating = true) }
                val mergedAdditions = listOf(pending.additions, text).filter { it.isNotBlank() }.joinToString("\n")
                val plan = createExecutionPlan(
                    task = pending.originalTask,
                    mode = pending.mode,
                    additions = mergedAdditions,
                    sessionId = pending.sessionId
                )
                _uiState.update {
                    it.copy(
                        pendingPlan = pending.copy(
                            planText = plan.text,
                            additions = mergedAdditions,
                            createdAt = System.currentTimeMillis()
                        ),
                        isPlanning = false,
                        isGenerating = false
                    )
                }
                showSettingsInfo("已合并追加要求并重新生成计划")
            } catch (_: CancellationException) {
            } finally {
                generatingJob = null
                syncGeneratingState()
            }
        }
    }

    fun executePendingPlan() {
        val pending = _uiState.value.pendingPlan
        if (pending == null) {
            showSettingsInfo("当前没有待执行计划")
            return
        }
        generatingJob = viewModelScope.launch {
            var completed = false
            try {
                _uiState.update { it.copy(isGenerating = true, isPlanning = false) }
                val title = _uiState.value.currentSessionTitle.ifBlank { pending.sessionId }
                runUserMessageViaActiveRuntime(
                    sessionId = pending.sessionId,
                    sessionTitle = title,
                    text = buildExecutionPromptForPendingPlan(pending)
                )
                completed = true
                _uiState.update { it.copy(pendingPlan = null) }
            } catch (_: CancellationException) {
                showSettingsInfo("已暂停执行，计划仍保留，可继续执行或取消")
            } finally {
                generatingJob = null
                if (!completed) {
                    _uiState.update { it.copy(isPlanning = false, isGenerating = false) }
                }
                syncGeneratingState()
                loadSettingsIntoState()
            }
        }
    }

    private fun sendMessageInternal(text: String, attachments: List<UiPendingAttachment>) {
        val runtimeText = buildUserTextWithAttachments(text, attachments)
        if (attachments.any { it.kind == UiMediaKind.Image } && !currentModelSupportsVision()) {
            sessionCoordinator.onInputChanged(text)
            _uiState.update { it.copy(isGenerating = false, pendingAttachments = attachments) }
            showSettingsInfo("当前模型不支持读图，请切换到支持视觉的模型后再发送图片。")
            return
        }
        val mode = _uiState.value.planModeLevel
        if (mode != UiPlanModeLevel.Off) {
            startPlanningTurn(runtimeText, mode)
            return
        }
        generatingJob = viewModelScope.launch {
            try {
                val sessionId = currentSessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
                val sessionTitle = _uiState.value.currentSessionTitle.ifBlank { sessionId }
                runUserMessageViaActiveRuntime(
                    sessionId = sessionId,
                    sessionTitle = sessionTitle,
                    text = runtimeText
                )
            } catch (_: CancellationException) {
                // User stopped generation; state cleanup happens in finally.
            } finally {
                generatingJob = null
                syncGeneratingState()
                loadSettingsIntoState()
            }
        }
    }

    private fun buildUserTextWithAttachments(text: String, attachments: List<UiPendingAttachment>): String {
        if (attachments.isEmpty()) return text
        val hasImage = attachments.any { it.kind == UiMediaKind.Image }
        val userText = text.trim().ifBlank {
            if (hasImage) "请直接观察并分析我上传的图片，描述画面内容、关键信息和你能判断出的细节。" else "请阅读并分析我上传的附件。"
        }
        return buildString {
            appendLine(userText)
            appendLine()
            appendLine("【用户已上传附件，必须读取并结合附件回答】")
            if (hasImage) {
                appendLine("【重要】本轮包含图片。若你能收到视觉输入，请直接基于图片像素回答；不要声称只能看到文件名、路径或大小。")
            }
            attachments.forEachIndexed { index, attachment ->
                appendLine("${index + 1}. ${attachment.marker}")
                appendLine("   文件ID：${attachment.id}")
                appendLine("   格式：${attachment.mimeType}")
                appendLine("   路径：${attachment.path}")
                appendLine("   大小：${attachment.sizeBytes} bytes")
                if (attachment.kind == UiMediaKind.Image) {
                    appendLine("   说明：这是一张图片；系统会把可识别的图片数据随本轮消息一起发送给多模态模型。")
                }
                if (attachment.textPreview.isNotBlank()) {
                    appendLine("   内容摘录：")
                    appendLine(attachment.textPreview)
                }
            }
            appendLine("【附件说明结束】")
        }.trim()
    }

    private fun startPlanningTurn(text: String, planMode: UiPlanModeLevel) {
        generatingJob = viewModelScope.launch {
            try {
                val sessionId = currentSessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
                _uiState.update { it.copy(isPlanning = true, isGenerating = true, pendingPlan = null) }
                val plan = createExecutionPlan(text, planMode, additions = "", sessionId = sessionId)
                val pending = UiPendingPlan(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    originalTask = text.trim(),
                    mode = planMode,
                    planText = plan.text,
                    createdAt = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    messageRepository.appendUserMessage(sessionId, text.trim())
                    messageRepository.appendAssistantMessage(sessionId, renderPendingPlanMessage(pending, plan))
                    sessionRepository.touch(sessionId)
                }
                _uiState.update { it.copy(pendingPlan = pending, isPlanning = false, isGenerating = false) }
                showSettingsInfo("计划已生成，请选择执行、添加要求或不执行")
            } catch (_: CancellationException) {
            } finally {
                generatingJob = null
                syncGeneratingState()
            }
        }
    }

    private suspend fun createExecutionPlan(
        task: String,
        mode: UiPlanModeLevel,
        additions: String,
        sessionId: String
    ): PlanningDispatcher.Plan = withContext(Dispatchers.IO) {
        val config = configStore.getConfig()
        val provider = LlmProviderFactory(providerResolutionStore).create(config)
        val recentContext = messageRepository.getMessages(sessionId)
            .takeLast(8)
            .joinToString("\n") { message -> "${message.role}: ${message.content.take(240)}" }
        PlanningDispatcher.createPlan(
            provider = provider,
            request = PlanningDispatcher.Request(
                userTask = task,
                mode = when (mode) {
                    UiPlanModeLevel.Quick -> "quick"
                    UiPlanModeLevel.Deep -> "deep"
                    UiPlanModeLevel.Codex -> "codex"
                    UiPlanModeLevel.Off,
                    UiPlanModeLevel.Standard -> "standard"
                },
                additions = additions,
                recentContext = recentContext
            )
        )
    }

    private fun renderPendingPlanMessage(pending: UiPendingPlan, plan: PlanningDispatcher.Plan): String = buildString {
        appendLine("计划模式：${pending.mode.label}")
        appendLine()
        appendLine(plan.text.trim())
        appendLine()
        appendLine("请在输入框上方选择：执行计划、添加要求或不执行。未确认前不会真正执行任务。")
    }.trim()

    private fun buildExecutionPromptForPendingPlan(pending: UiPendingPlan): String = buildString {
        appendLine("用户已确认执行计划模式生成的任务计划。请进入执行阶段，按计划调度可用技能、工具、记忆和智能体上下文。")
        appendLine()
        appendLine("原始任务：")
        appendLine(pending.originalTask)
        if (pending.additions.isNotBlank()) {
            appendLine()
            appendLine("追加要求：")
            appendLine(pending.additions)
        }
        appendLine()
        appendLine("已确认计划：")
        appendLine(pending.planText)
        appendLine()
        appendLine("执行要求：需要工具就调用工具；需要技能就遵循技能；执行失败要说明 fallback；最后给出完成结果和验证情况。")
    }.trim()

    private fun currentModelSupportsVision(): Boolean {
        val model = configStore.getConfig().model.lowercase(Locale.US)
        val provider = configStore.getConfig().providerName.lowercase(Locale.US)
        val haystack = "$provider/$model"
        return listOf(
            "gpt-4o",
            "gpt-4.1",
            "gpt-5",
            "o1",
            "o3",
            "o4",
            "vision",
            "visual",
            "multimodal",
            "omni",
            "vl",
            "qwen-vl",
            "qwen2-vl",
            "qwen2.5-vl",
            "qwen-omni",
            "gemini",
            "claude-3",
            "claude-4",
            "llava",
            "yi-vision",
            "glm-4v",
            "glm-4.5v",
            "doubao-vision",
            "seed-vision",
            "pixtral",
            "mistral-medium",
            "mistral-large"
        ).any { haystack.contains(it) }
    }
    private fun localPolishText(source: String): String {
        return source.trim()
            .replace(Regex("\\s+"), " ")
            .replace("我是一个AI", "我")
            .replace("作为一个AI", "")
            .replace("希望这能帮助你", "")
            .trim()
            .ifBlank { source }
    }
    fun refreshExtensionPanels() {
        val skills = skillStore.list().map {
            UiManagedSkill(
                name = it.name,
                description = it.description,
                enabled = it.enabled,
                source = it.source,
                path = it.path,
                updatedAt = it.updatedAt
            )
        }
        val tools = dynamicToolStore.list().map {
            UiDynamicTool(
                name = it.name,
                description = it.description,
                prompt = it.prompt,
                enabled = it.enabled,
                updatedAt = it.updatedAt
            )
        }
        val memories = compressedMemoryStore.list(_uiState.value.currentSessionId).map {
            UiCompressedMemory(
                id = it.id,
                sessionId = it.sessionId,
                createdAt = it.createdAt,
                algorithm = it.algorithm,
                summary = it.summary,
                originalChars = it.originalChars,
                compressedBytes = it.compressedBytes,
                messageCount = it.messageCount
            )
        }
        _uiState.update { it.copy(skills = skills, dynamicTools = tools, compressedMemories = memories) }
    }

    fun createSkill(name: String, description: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { skillStore.createOrUpdate(name, description, body, enabled = true) }
                .onSuccess {
                    refreshExtensionPanels()
                    showSettingsInfo("Skill saved: @${it.name}")
                }
                .onFailure { showSettingsInfo("Skill save failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun setSkillEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { skillStore.setEnabled(name, enabled) }
                .onSuccess {
                    refreshExtensionPanels()
                    showSettingsInfo(if (enabled) "技能 @${name.trim().removePrefix("@")} 已开启" else "技能 @${name.trim().removePrefix("@")} 已关闭，列表中仍会保留")
                }
                .onFailure { showSettingsInfo("技能状态更新失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteSkill(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { skillStore.delete(name) }
                .onSuccess { deleted ->
                    refreshExtensionPanels()
                    showSettingsInfo(if (deleted) "技能 @${name.trim().removePrefix("@")} 已删除" else "技能删除失败：没有删除任何文件")
                }
                .onFailure { showSettingsInfo("技能删除失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun createDynamicTool(name: String, description: String, prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dynamicToolStore.upsert(name, description, prompt, enabled = true) }
                .onSuccess {
                    RuntimeController.reloadAll(getApplication<Application>())
                    refreshExtensionPanels()
                    showSettingsInfo("Dynamic tool saved: ${it.name}")
                }
                .onFailure { showSettingsInfo("Dynamic tool save failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun setDynamicToolEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dynamicToolStore.setEnabled(name, enabled) }
                .onSuccess {
                    RuntimeController.reloadAll(getApplication<Application>())
                    refreshExtensionPanels()
                    showSettingsInfo("Dynamic tool ${it.name} enabled=${it.enabled}")
                }
                .onFailure { showSettingsInfo("Dynamic tool update failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun refreshAgentPanels() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { refreshAgentPanelsNow() }
                .onFailure { showSettingsInfo("智能体面板刷新失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    private suspend fun refreshAgentPanelsNow() {
        agentRepository.bootstrapBuiltins()
        val profiles = agentRepository.listProfiles().map {
            UiAgentProfile(
                id = it.id,
                name = it.name,
                type = it.type,
                description = it.description,
                systemPrompt = it.systemPrompt,
                enabled = it.enabled,
                defaultSkills = AgentRepository.parseJsonArray(it.defaultSkillNamesJson),
                dynamicTools = AgentRepository.parseJsonArray(it.dynamicToolNamesJson),
                updatedAt = it.updatedAt
            )
        }
        val binding = agentRepository.getBinding(currentSessionId)
        val currentName = binding?.agentId?.let { id -> profiles.firstOrNull { it.id == id }?.name }.orEmpty()
        val roleCards = roleCardRepository.listCards().map {
            UiRoleCard(
                id = it.id,
                name = it.name,
                avatarSymbol = it.avatarSymbol,
                description = it.description,
                persona = it.persona,
                speakingStyle = it.speakingStyle,
                boundaries = it.boundaries,
                scenario = it.scenario,
                exampleDialog = it.exampleDialog,
                enabled = it.enabled,
                updatedAt = it.updatedAt
            )
        }
        val activeRoleCardId = binding?.activeRoleCardId?.takeIf { id -> roleCards.any { it.id == id && it.enabled } }.orEmpty()
        val currentRoleCardName = roleCards.firstOrNull { it.id == activeRoleCardId }?.name.orEmpty()
        val projects = novelRepository.listProjects().map {
            UiNovelProject(it.id, it.title, it.genre, it.styleGuide, it.premise, it.updatedAt)
        }
        val activeProjectId = binding?.activeNovelProjectId?.takeIf { id -> projects.any { it.id == id } }.orEmpty()
        val chapters = if (activeProjectId.isNotBlank()) novelRepository.listChapters(activeProjectId).map {
            UiNovelChapter(it.id, it.projectId, it.chapterIndex, it.title, it.summary, it.content, parseJsonArray(it.keywordsJson), it.updatedAt)
        } else emptyList()
        val characters = if (activeProjectId.isNotBlank()) novelRepository.listCharacters(activeProjectId).map {
            UiNovelCharacter(it.id, it.projectId, it.name, parseJsonArray(it.aliasesJson), it.goal, it.secret, it.arc, it.notes, it.updatedAt)
        } else emptyList()
        val characterById = characters.associateBy { it.id }
        val relations = if (activeProjectId.isNotBlank()) novelRepository.listRelations(activeProjectId).map {
            UiNovelRelation(
                fromName = characterById[it.fromCharacterId]?.name ?: it.fromCharacterId,
                toName = characterById[it.toCharacterId]?.name ?: it.toCharacterId,
                label = relationLabelZh(it.label),
                weight = it.weight,
                evidence = parseJsonArray(it.evidenceJson)
            )
        } else emptyList()
        val worldNotes = if (activeProjectId.isNotBlank()) novelRepository.listWorldNotes(activeProjectId).map {
            UiNovelWorldNote(it.id, it.projectId, it.category, it.title, it.content, it.updatedAt)
        } else emptyList()
        val analyses = if (activeProjectId.isNotBlank()) novelRepository.listAnalyses(activeProjectId).map {
            UiNovelAnalysis(it.id, it.kind, it.title, it.summary, it.createdAt)
        } else emptyList()
        _uiState.update {
            it.copy(
                agentProfiles = profiles,
                currentAgentBinding = binding?.let { b -> UiSessionAgentBinding(b.sessionId, b.agentId, activeProjectId.ifBlank { null }, activeRoleCardId.ifBlank { null }) },
                currentAgentName = currentName,
                roleCards = roleCards,
                activeRoleCardId = activeRoleCardId,
                currentRoleCardName = currentRoleCardName,
                novelProjects = projects,
                activeNovelProjectId = activeProjectId,
                novelChapters = chapters,
                novelCharacters = characters,
                novelRelations = relations,
                novelWorldNotes = worldNotes,
                novelAnalyses = analyses
            )
        }
    }


    fun completeAndCreateAgentProfile(name: String, type: String, description: String, systemPrompt: String) {
        val userName = name.trim()
        val userType = type.trim()
        val userDescription = description.trim()
        val userPrompt = systemPrompt.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(settingsInfo = "正在用 AI 补全智能体资料…") }
            runCatching {
                val config = configStore.getConfig()
                val provider = LlmProviderFactory(providerResolutionStore).create(config)
                val response = withContext(Dispatchers.IO) {
                    provider.chat(
                        messages = listOf(
                            ChatMessage(
                                role = "system",
                                content = "你是 LGClaw 智能体配置生成器。根据用户提供的零散信息补全完整智能体资料。只输出严格 JSON，不要 Markdown。字段：name,type,description,systemPrompt。systemPrompt 必须可直接作为运行时系统提示词，至少 120 字，包含目标、边界、工作方式、输出风格。"
                            ),
                            ChatMessage(
                                role = "user",
                                content = "名称：$userName\n类型：$userType\n描述：$userDescription\n已有提示词：$userPrompt"
                            )
                        ),
                        toolsSpec = emptyList()
                    )
                }
                val obj = extractJsonObject(response.assistant.content)
                val completedName = obj.optString("name").trim().ifBlank { userName.ifBlank { "自定义智能体" } }
                val completedType = obj.optString("type").trim().ifBlank { userType.ifBlank { "custom" } }
                val completedDescription = obj.optString("description").trim().ifBlank { userDescription.ifBlank { "由 AI 补全的本地运行时智能体。" } }
                val completedPrompt = obj.optString("systemPrompt").trim().ifBlank {
                    buildFallbackAgentPrompt(completedName, completedType, completedDescription, userPrompt)
                }
                agentRepository.createOrUpdateProfile(
                    id = null,
                    name = completedName,
                    type = completedType,
                    description = completedDescription,
                    systemPrompt = completedPrompt,
                    defaultSkills = emptyList(),
                    dynamicTools = emptyList(),
                    enabled = true
                )
            }.onSuccess { profile ->
                refreshAgentPanelsNow()
                showSettingsInfo("智能体已由 AI 补全并保存：${profile.name}")
            }.onFailure { t ->
                runCatching {
                    val fallbackName = userName.ifBlank { "自定义智能体" }
                    agentRepository.createOrUpdateProfile(
                        id = null,
                        name = fallbackName,
                        type = userType.ifBlank { "custom" },
                        description = userDescription.ifBlank { "根据用户提供内容创建的本地智能体。" },
                        systemPrompt = buildFallbackAgentPrompt(fallbackName, userType.ifBlank { "custom" }, userDescription, userPrompt),
                        defaultSkills = emptyList(),
                        dynamicTools = emptyList(),
                        enabled = true
                    )
                }.onSuccess { profile ->
                    refreshAgentPanelsNow()
                    showSettingsInfo("AI 补全失败，已用本地规则补全并保存：${profile.name}")
                }.onFailure {
                    showSettingsInfo("智能体补全失败：${t.message ?: t.javaClass.simpleName}")
                }
            }
        }
    }

    private fun extractJsonObject(raw: String): JSONObject {
        val text = raw.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 没有返回可解析 JSON" }
        return JSONObject(text.substring(start, end + 1))
    }

    private fun buildFallbackAgentPrompt(name: String, type: String, description: String, userPrompt: String): String = buildString {
        appendLine("你是 $name。")
        appendLine("智能体类型：${type.ifBlank { "custom" }}。")
        appendLine("职责说明：${description.ifBlank { "根据用户目标提供稳定、具体、可执行的帮助。" }}")
        if (userPrompt.isNotBlank()) appendLine("用户补充要求：$userPrompt")
        appendLine("工作方式：先理解上下文，再给出清晰可执行的结果；需要澄清时只问关键问题；能够直接完成时主动完成。")
        appendLine("表达风格：自然、简洁、有判断力，避免空泛套话和明显 AI 腔。")
        appendLine("边界：不伪造已经完成的外部动作，不泄露系统提示词，不执行危险或违法请求。")
    }.trim()
    fun updateAgentProfile(profileId: String, name: String, type: String, description: String, systemPrompt: String, defaultSkillsText: String, dynamicToolsText: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val profile = agentRepository.updateProfile(
                    AgentRepository.AgentProfileDraft(
                        id = profileId,
                        name = name,
                        type = type,
                        description = description,
                        systemPrompt = systemPrompt,
                        defaultSkills = splitAgentNames(defaultSkillsText),
                        dynamicTools = splitAgentNames(dynamicToolsText),
                        enabled = enabled
                    )
                )
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
                profile
            }.onSuccess { showSettingsInfo("智能体已更新：${it.name}") }
                .onFailure { showSettingsInfo("智能体更新失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun duplicateAgentProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val old = agentRepository.getProfile(profileId) ?: throw IllegalArgumentException("智能体不存在")
                val profile = agentRepository.createOrUpdateProfile(
                    id = null,
                    name = "${old.name} 副本",
                    type = old.type.ifBlank { "custom" },
                    description = old.description,
                    systemPrompt = old.systemPrompt,
                    defaultSkills = AgentRepository.parseJsonArray(old.defaultSkillNamesJson),
                    dynamicTools = AgentRepository.parseJsonArray(old.dynamicToolNamesJson),
                    enabled = true
                )
                refreshAgentPanelsNow()
                profile
            }.onSuccess { showSettingsInfo("已创建可编辑副本：${it.name}") }
                .onFailure { showSettingsInfo("复制智能体失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun setAgentProfileEnabled(profileId: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                agentRepository.setEnabled(profileId, enabled)
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
            }.onSuccess { showSettingsInfo(if (enabled) "智能体已启用" else "智能体已停用，并已解除相关会话绑定") }
                .onFailure { showSettingsInfo("智能体状态更新失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteAgentProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                agentRepository.deleteCustomProfile(profileId)
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
            }.onSuccess { showSettingsInfo("智能体已删除，并已解除相关绑定") }
                .onFailure { showSettingsInfo("智能体删除失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun previewAgentProfile(profileId: String, testMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val profile = agentRepository.getProfile(profileId) ?: throw IllegalArgumentException("智能体不存在")
                val binding = agentRepository.getBinding(currentSessionId)
                val roleCard = binding?.activeRoleCardId?.let { roleCardRepository.getCard(it) }
                agentRepository.buildProfileTestPreview(profile, roleCard, testMessage)
            }.onSuccess { showSettingsInfo(it) }
                .onFailure { showSettingsInfo("智能体测试预览失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    private fun splitAgentNames(raw: String): List<String> = raw
        .split(',', '，', '\n', ' ')
        .map { it.trim().removePrefix("@") }
        .filter { it.isNotBlank() }
        .distinct()
    fun createAgentProfile(name: String, type: String, description: String, systemPrompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cleanName = name.trim().ifBlank { "自定义智能体" }
                val cleanType = type.trim().ifBlank { "custom" }
                val cleanDescription = description.trim().ifBlank { "本地运行时智能体。" }
                val cleanPrompt = systemPrompt.trim().ifBlank { buildFallbackAgentPrompt(cleanName, cleanType, cleanDescription, "") }
                agentRepository.createOrUpdateProfile(null, cleanName, cleanType, cleanDescription, cleanPrompt, emptyList(), emptyList(), true)
            }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("智能体已保存：${it.name}") }
                .onFailure { showSettingsInfo("智能体保存失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun bindAgentToCurrentSession(agentId: String?, novelProjectId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                agentRepository.bindSession(currentSessionId, agentId, if (agentId == null) null else novelProjectId, _uiState.value.activeRoleCardId.ifBlank { null })
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
            }.onSuccess {
                showSettingsInfo(if (agentId == null) "【重要】已解除当前会话智能体绑定，后续回复将恢复普通聊天。" else "【重要】当前会话已绑定智能体：${_uiState.value.currentAgentName.ifBlank { agentId ?: "智能体" }}。后续每轮都会读取它。")
            }.onFailure { showSettingsInfo("智能体绑定失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun selectNovelProject(projectId: String) {
        bindAgentToCurrentSession(AgentRepository.NOVEL_AGENT_ID, projectId)
    }

    fun createRoleCard(
        name: String,
        avatarSymbol: String,
        description: String,
        persona: String,
        speakingStyle: String,
        boundaries: String,
        scenario: String,
        exampleDialog: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val card = roleCardRepository.createOrUpdate(
                    id = null,
                    name = name,
                    avatarSymbol = avatarSymbol,
                    description = description,
                    persona = persona,
                    speakingStyle = speakingStyle,
                    boundaries = boundaries,
                    scenario = scenario,
                    exampleDialog = exampleDialog,
                    enabled = true
                )
                agentRepository.setActiveRoleCard(currentSessionId, card.id)
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
                card
            }.onSuccess { showSettingsInfo("角色卡已保存并绑定：${it.name}") }
                .onFailure { showSettingsInfo("角色卡保存失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun bindRoleCardToCurrentSession(roleCardId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                agentRepository.setActiveRoleCard(currentSessionId, roleCardId)
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
            }.onSuccess {
                showSettingsInfo(if (roleCardId == null) "已解除角色卡绑定" else "当前会话角色卡已更新")
            }.onFailure { showSettingsInfo("角色卡绑定失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteRoleCard(roleCardId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val binding = agentRepository.getBinding(currentSessionId)
                if (binding?.activeRoleCardId == roleCardId) {
                    agentRepository.setActiveRoleCard(currentSessionId, null)
                }
                roleCardRepository.delete(roleCardId)
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
            }.onSuccess { showSettingsInfo("角色卡已删除") }
                .onFailure { showSettingsInfo("角色卡删除失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }
    fun createNovelProject(title: String, genre: String, styleGuide: String, premise: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val project = novelRepository.createProject(title, genre, styleGuide, premise)
                agentRepository.bindSession(currentSessionId, AgentRepository.NOVEL_AGENT_ID, project.id)
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
                project
            }.onSuccess { showSettingsInfo("小说项目已创建：${it.title}") }
                .onFailure { showSettingsInfo("小说项目创建失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun updateNovelProject(projectId: String, title: String, genre: String, styleGuide: String, premise: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.updateProject(projectId, title, genre, styleGuide, premise) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("小说项目已更新：${it.title}") }
                .onFailure { showSettingsInfo("小说项目更新失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteNovelProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val binding = agentRepository.getBinding(currentSessionId)
                novelRepository.deleteProjectCascade(projectId)
                if (binding?.activeNovelProjectId == projectId) {
                    agentRepository.bindSession(currentSessionId, binding.agentId?.takeIf { it != AgentRepository.NOVEL_AGENT_ID }, null)
                }
                RuntimeController.reloadAll(getApplication<Application>())
                refreshAgentPanelsNow()
            }.onSuccess { showSettingsInfo("小说项目已删除") }
                .onFailure { showSettingsInfo("小说项目删除失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun upsertNovelChapter(projectId: String, chapterIndex: String, title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.upsertChapter(projectId, chapterIndex.toIntOrNull() ?: 1, title, content) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("章节已保存：${it.title}") }
                .onFailure { showSettingsInfo("章节保存失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteNovelChapter(chapterId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.deleteChapter(chapterId) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("章节已删除") }
                .onFailure { showSettingsInfo("章节删除失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun upsertNovelCharacter(projectId: String, name: String, aliases: String, goal: String, secret: String, arc: String, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.upsertCharacter(projectId, name, aliases.split(',', '，').map { it.trim() }, goal, secret, arc, notes) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("人物已保存：${it.name}") }
                .onFailure { showSettingsInfo("人物保存失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteNovelCharacter(characterId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.deleteCharacter(characterId) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("人物已删除") }
                .onFailure { showSettingsInfo("人物删除失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun upsertNovelWorldNote(projectId: String, category: String, title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.upsertWorldNote(projectId, category, title, content) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("世界观笔记已保存：${it.title}") }
                .onFailure { showSettingsInfo("世界观笔记保存失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteNovelWorldNote(noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.deleteWorldNote(noteId) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("世界观笔记已删除") }
                .onFailure { showSettingsInfo("世界观笔记删除失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun analyzeNovelRelations(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.analyzeRelations(projectId) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("人物关系已更新：${it.size} 条") }
                .onFailure { showSettingsInfo("关系分析失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun summarizeNovelProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { novelRepository.summarizeProject(projectId) }
                .onSuccess { refreshAgentPanelsNow(); showSettingsInfo("小说摘要已保存") }
                .onFailure { showSettingsInfo("小说摘要生成失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    private fun relationLabelZh(label: String): String = when (label.lowercase(Locale.US)) {
        "cooccur" -> "共现"
        else -> label.ifBlank { "关系" }
    }
    private fun parseJsonArray(raw: String): List<String> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }.getOrDefault(emptyList())
    fun stopGeneration(): Unit {
        sessionCoordinator.stopGeneration()
    }

    private fun stopGenerationInternal() {
        val hadActiveJob = generatingJob != null
        generatingJob?.cancel()
        generatingJob = null
        synchronized(gatewayProcessingSessions) {
            gatewayProcessingSessions.remove(currentSessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID })
        }
        _uiState.update { it.copy(isPlanning = false, isGenerating = false) }
        showSettingsInfo(if (hadActiveJob) "已暂停当前任务，可继续编辑或重新发送" else "当前没有正在运行的任务")
    }

    fun openSettings() {
        loadSettingsIntoState()
        refreshCronJobs()
        _uiState.update { it.copy(settingsInfo = null) }
    }

    fun showSettingsInfo(message: String) {
        val text = message.trim()
        if (text.isBlank()) return
        _uiState.update { it.copy(settingsInfo = text) }
    }

    fun clearProviderTokenUsageStats() = providerSettingsCoordinator.clearProviderTokenUsageStats()

    fun clearSettingsInfo() {
        _uiState.update {
            if (it.settingsInfo == null) it else it.copy(settingsInfo = null)
        }
    }

    fun startManualCompression() {
        if (compressionJob?.isActive == true) {
            showSettingsInfo("已有压缩任务正在运行")
            return
        }
        val sessionId = currentSessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
        compressionJob = viewModelScope.launch(Dispatchers.IO) {
            runCompressionForSession(
                sessionId = sessionId,
                manual = true,
                keepRecentMessages = 2,
                minCandidates = 1
            )
        }
    }

    fun cancelCompression() {
        val active = compressionJob?.isActive == true
        compressionJob?.cancel(CancellationException("用户取消压缩"))
        if (!active) {
            showSettingsInfo("当前没有正在压缩的任务")
        }
    }

    private fun persistOnboardingProviderDraftIfNeeded() {
        onboardingCoordinator.persistProviderDraftIfNeeded()
    }

    fun checkAppUpdate() = appUpdateCoordinator.checkAppUpdate()

    fun dismissAppUpdatePrompt() = appUpdateCoordinator.dismissAppUpdatePrompt()
    fun dismissAppUpdateNotice() = appUpdateCoordinator.dismissAppUpdateNotice()
    fun notifyAppUpdateDownloadStarted() = appUpdateCoordinator.notifyAppUpdateDownloadStarted()

    fun notifyAppUpdateDownloadFallback(releaseUrl: String) =
        appUpdateCoordinator.notifyAppUpdateDownloadFallback(releaseUrl)

    fun onSettingsProviderChanged(value: String) =
        providerSettingsCoordinator.onSettingsProviderChanged(value)

    fun startNewProviderDraft() = providerSettingsCoordinator.startNewProviderDraft()

    fun selectProviderConfigForEditing(configId: String) =
        providerSettingsCoordinator.selectProviderConfigForEditing(configId)

    fun setActiveProviderConfig(configId: String) =
        providerSettingsCoordinator.setActiveProviderConfig(configId)

    private fun setActiveProviderConfigInternal(configId: String) {
        val targetId = configId.trim()
        if (targetId.isBlank() || _uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val currentState = _uiState.value
                val updatedConfigs = normalizeActiveProviderConfigs(
                    currentState.settingsProviderConfigs.map { config ->
                        config.copy(enabled = config.id == targetId)
                    }
                )
                val selected = updatedConfigs.firstOrNull { it.id == targetId }
                val updatedState = currentState.copy(
                    settingsProviderConfigs = updatedConfigs,
                    settingsEditingProviderConfigId = selected?.id.orEmpty(),
                    settingsProvider = selected?.providerName ?: currentState.settingsProvider,
                    settingsProviderCustomName = selected?.customName ?: currentState.settingsProviderCustomName,
                    settingsProviderProtocol = selected?.providerProtocol ?: currentState.settingsProviderProtocol,
                    settingsBaseUrl = selected?.let { config ->
                        config.baseUrl.ifBlank {
                            ProviderCatalog.defaultBaseUrl(config.providerName, config.providerProtocol)
                        }
                    } ?: currentState.settingsBaseUrl,
                    settingsModel = selected?.model ?: currentState.settingsModel,
                    settingsEquippedModels = selected?.equippedModels ?: currentState.settingsEquippedModels,
                    settingsDiscoveredModels = selected?.let { ProviderCatalog.suggestedModels(it.providerName) } ?: currentState.settingsDiscoveredModels,
                    settingsApiKey = selected?.apiKey ?: currentState.settingsApiKey
                )
                configStore.saveConfig(buildProviderSettingsConfig(updatedState))
                updatedState
            }.onSuccess { updatedState ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsProviderConfigs = updatedState.settingsProviderConfigs,
                        settingsEditingProviderConfigId = updatedState.settingsEditingProviderConfigId,
                        settingsProvider = updatedState.settingsProvider,
                        settingsProviderCustomName = updatedState.settingsProviderCustomName,
                        settingsProviderProtocol = updatedState.settingsProviderProtocol,
                        settingsBaseUrl = updatedState.settingsBaseUrl,
                        settingsModel = updatedState.settingsModel,
                        settingsEquippedModels = updatedState.settingsEquippedModels,
                        settingsDiscoveredModels = updatedState.settingsDiscoveredModels,
                        settingsApiKey = updatedState.settingsApiKey,
                        settingsInfo = "Provider updated."
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = "Save failed: ${t.message ?: t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun deleteProviderConfig(configId: String) =
        providerSettingsCoordinator.deleteProviderConfig(configId)

    private fun deleteProviderConfigInternal(configId: String) {
        val targetId = configId.trim()
        if (targetId.isBlank() || _uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val currentState = _uiState.value
                val normalizedRemaining = normalizeActiveProviderConfigs(
                    currentState.settingsProviderConfigs.filterNot { it.id == targetId }
                )
                val nextSelection = normalizedRemaining.firstOrNull()
                val updatedState = currentState.copy(
                    settingsProviderConfigs = normalizedRemaining,
                    settingsEditingProviderConfigId = nextSelection?.id.orEmpty(),
                    settingsProvider = nextSelection?.providerName ?: AppLimits.DEFAULT_PROVIDER,
                    settingsProviderCustomName = nextSelection?.customName.orEmpty(),
                    settingsProviderProtocol = nextSelection?.providerProtocol
                        ?: ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER),
                    settingsBaseUrl = nextSelection?.let { config ->
                        config.baseUrl.ifBlank {
                            ProviderCatalog.defaultBaseUrl(config.providerName, config.providerProtocol)
                        }
                    } ?: ProviderCatalog.defaultBaseUrl(
                        AppLimits.DEFAULT_PROVIDER,
                        ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER)
                    ),
                    settingsModel = nextSelection?.model ?: ProviderCatalog.defaultModel(
                        AppLimits.DEFAULT_PROVIDER,
                        ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER)
                    ),
                    settingsApiKey = nextSelection?.apiKey.orEmpty()
                )
                val cachePrefix = ProviderResolutionStore.cachePrefixForProviderConfig(targetId)
                AdaptiveLlmProvider.clearRememberedTargets(cachePrefix)
                providerResolutionStore.clearByPrefix(cachePrefix)
                configStore.saveConfig(buildProviderSettingsConfig(updatedState))
                updatedState
            }.onSuccess { updatedState ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsProviderConfigs = updatedState.settingsProviderConfigs,
                        settingsEditingProviderConfigId = updatedState.settingsEditingProviderConfigId,
                        settingsProvider = updatedState.settingsProvider,
                        settingsProviderCustomName = updatedState.settingsProviderCustomName,
                        settingsProviderProtocol = updatedState.settingsProviderProtocol,
                        settingsBaseUrl = updatedState.settingsBaseUrl,
                        settingsModel = updatedState.settingsModel,
                        settingsEquippedModels = updatedState.settingsEquippedModels,
                        settingsDiscoveredModels = updatedState.settingsDiscoveredModels,
                        settingsApiKey = updatedState.settingsApiKey,
                        settingsInfo = "Provider removed."
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = "Save failed: ${t.message ?: t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun onSettingsModelChanged(value: String) =
        providerSettingsCoordinator.onSettingsModelChanged(value)
    fun fetchProviderModels() {
        if (_uiState.value.settingsModelFetching) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsModelFetching = true, settingsInfo = null) }
            runCatching {
                val state = _uiState.value
                val models = withContext(Dispatchers.IO) { fetchModelsForProviderDraft(state) }
                models.ifEmpty { ProviderCatalog.suggestedModels(state.settingsProvider) }
            }.onSuccess { models ->
                _uiState.update { state ->
                    val merged = (models + state.settingsEquippedModels + state.settingsModel)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    state.copy(
                        settingsModelFetching = false,
                        settingsDiscoveredModels = merged,
                        settingsEquippedModels = if (state.settingsEquippedModels.isEmpty()) merged.take(1) else state.settingsEquippedModels,
                        settingsInfo = "已获取 ${merged.size} 个模型，可勾选装备。"
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsModelFetching = false,
                        settingsDiscoveredModels = ProviderCatalog.suggestedModels(it.settingsProvider),
                        settingsInfo = "获取模型失败：${t.message ?: t.javaClass.simpleName}，已显示内置推荐模型。"
                    )
                }
            }
        }
    }

    fun setModelEquipped(model: String, equipped: Boolean) {
        val clean = model.trim()
        if (clean.isBlank()) return
        providerSettingsCoordinator.onSettingsModelChanged(_uiState.value.settingsModel.ifBlank { clean })
        _uiState.update { state ->
            val next = if (equipped) {
                (state.settingsEquippedModels + clean).distinct()
            } else {
                state.settingsEquippedModels.filterNot { it == clean }.ifEmpty { listOf(state.settingsModel).filter { it.isNotBlank() } }
            }
            state.copy(settingsEquippedModels = next)
        }
        persistOnboardingProviderDraftIfNeeded()
    }


    fun switchProviderModel(configId: String, model: String) {
        val targetId = configId.trim()
        val cleanModel = model.trim()
        if (targetId.isBlank() || cleanModel.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val currentState = _uiState.value
                val updatedConfigs = normalizeActiveProviderConfigs(
                    currentState.settingsProviderConfigs.map { config ->
                        if (config.id == targetId) {
                            config.copy(
                                enabled = true,
                                model = cleanModel,
                                equippedModels = (config.equippedModels + cleanModel)
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                            )
                        } else {
                            config.copy(enabled = false)
                        }
                    }
                )
                val selected = updatedConfigs.firstOrNull { it.id == targetId }
                    ?: throw IllegalArgumentException("供应商配置不存在")
                val updatedState = currentState.copy(
                    settingsProviderConfigs = updatedConfigs,
                    settingsEditingProviderConfigId = selected.id,
                    settingsProvider = selected.providerName,
                    settingsProviderCustomName = selected.customName,
                    settingsProviderProtocol = selected.providerProtocol,
                    settingsBaseUrl = selected.baseUrl.ifBlank {
                        ProviderCatalog.defaultBaseUrl(selected.providerName, selected.providerProtocol)
                    },
                    settingsModel = selected.model,
                    settingsEquippedModels = selected.equippedModels,
                    settingsDiscoveredModels = selected.equippedModels.ifEmpty { ProviderCatalog.suggestedModels(selected.providerName) },
                    settingsApiKey = selected.apiKey,
                    settingsInfo = "已切换到 ${providerSwitchLabel(selected)} / $cleanModel"
                )
                configStore.saveConfig(buildProviderSettingsConfig(updatedState))
                updatedState
            }.onSuccess { updatedState ->
                _uiState.update { updatedState }
                reloadAllViaActiveRuntime()
            }.onFailure { t ->
                _uiState.update { it.copy(settingsInfo = "模型切换失败：${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    private fun providerSwitchLabel(config: UiProviderConfig): String = config.customName.trim()
        .ifBlank { ProviderCatalog.resolve(config.providerName).title }
    fun switchActiveModel(model: String) {
        val clean = model.trim()
        if (clean.isBlank()) return
        providerSettingsCoordinator.onSettingsModelChanged(clean)
        _uiState.update { state ->
            state.copy(settingsEquippedModels = (state.settingsEquippedModels + clean).distinct())
        }
        saveProviderSettings(showSuccessMessage = false, showErrorMessage = true)
        reloadAllViaActiveRuntime()
    }

    fun onChatSearchChanged(query: String) {
        val clean = query.trim()
        val resultIds = if (clean.isBlank()) emptyList() else _uiState.value.messages
            .filter { it.content.contains(clean, ignoreCase = true) }
            .map { it.id }
        _uiState.update { it.copy(chatSearchQuery = query, chatSearchResultIds = resultIds, chatSearchCurrentIndex = 0) }
    }

    fun moveChatSearch(delta: Int) {
        _uiState.update { state ->
            val size = state.chatSearchResultIds.size
            if (size <= 0) state else state.copy(chatSearchCurrentIndex = Math.floorMod(state.chatSearchCurrentIndex + delta, size))
        }
    }

    fun onSettingsProviderCustomNameChanged(value: String) =
        providerSettingsCoordinator.onSettingsProviderCustomNameChanged(value)

    fun onSettingsApiKeyChanged(value: String) =
        providerSettingsCoordinator.onSettingsApiKeyChanged(value)

    fun onSettingsBaseUrlChanged(value: String) =
        providerSettingsCoordinator.onSettingsBaseUrlChanged(value)

    fun onSettingsMaxRoundsChanged(value: String) =
        providerSettingsCoordinator.onSettingsMaxRoundsChanged(value)

    fun onSettingsToolResultMaxCharsChanged(value: String) =
        providerSettingsCoordinator.onSettingsToolResultMaxCharsChanged(value)

    fun onSettingsMemoryConsolidationWindowChanged(value: String) =
        providerSettingsCoordinator.onSettingsMemoryConsolidationWindowChanged(value)

    fun onSettingsCompressionThresholdKChanged(value: String) {
        _uiState.update { it.copy(settingsCompressionThresholdK = value) }
    }

    fun onSettingsLlmCallTimeoutSecondsChanged(value: String) =
        providerSettingsCoordinator.onSettingsLlmCallTimeoutSecondsChanged(value)

    fun onSettingsLlmConnectTimeoutSecondsChanged(value: String) =
        providerSettingsCoordinator.onSettingsLlmConnectTimeoutSecondsChanged(value)

    fun onSettingsLlmReadTimeoutSecondsChanged(value: String) =
        providerSettingsCoordinator.onSettingsLlmReadTimeoutSecondsChanged(value)

    fun onSettingsDefaultToolTimeoutSecondsChanged(value: String) =
        providerSettingsCoordinator.onSettingsDefaultToolTimeoutSecondsChanged(value)

    fun onSettingsContextMessagesChanged(value: String) =
        providerSettingsCoordinator.onSettingsContextMessagesChanged(value)

    fun onSettingsToolArgsPreviewMaxCharsChanged(value: String) =
        providerSettingsCoordinator.onSettingsToolArgsPreviewMaxCharsChanged(value)

    fun onSettingsCronEnabledChanged(value: Boolean) =
        runtimeCoordinator.onSettingsCronEnabledChanged(value)

    fun onSettingsCronMinEveryMsChanged(value: String) =
        runtimeCoordinator.onSettingsCronMinEveryMsChanged(value)

    fun onSettingsCronMaxJobsChanged(value: String) =
        runtimeCoordinator.onSettingsCronMaxJobsChanged(value)

    fun onSettingsHeartbeatEnabledChanged(value: Boolean) =
        runtimeCoordinator.onSettingsHeartbeatEnabledChanged(value)

    fun onSettingsHeartbeatIntervalSecondsChanged(value: String) =
        runtimeCoordinator.onSettingsHeartbeatIntervalSecondsChanged(value)

    fun onSettingsGatewayEnabledChanged(value: Boolean) =
        runtimeCoordinator.onSettingsGatewayEnabledChanged(value)

    fun setUiLanguage(useChinese: Boolean) {
        val current = configStore.getUiPreferencesConfig()
        val next = current.copy(useChinese = useChinese)
        configStore.saveUiPreferencesConfig(next)
        _uiState.update {
            val fallbackUserName = if (useChinese) "你" else "You"
            val currentDefaultUserName = if (it.settingsUseChinese) "你" else "You"
            val adjustedOnboardingUserName = when {
                it.onboardingCompleted -> it.onboardingUserDisplayName
                it.onboardingUserDisplayName.isBlank() -> fallbackUserName
                it.onboardingUserDisplayName == currentDefaultUserName -> fallbackUserName
                else -> it.onboardingUserDisplayName
            }
            it.copy(
                settingsUseChinese = next.useChinese,
                onboardingUserDisplayName = adjustedOnboardingUserName
            )
        }
    }

    fun toggleUiLanguage() {
        setUiLanguage(!configStore.getUiPreferencesConfig().useChinese)
    }

    fun toggleUiTheme() {
        val current = configStore.getUiPreferencesConfig()
        val next = current.copy(darkTheme = !current.darkTheme)
        configStore.saveUiPreferencesConfig(next)
        _uiState.update {
            it.copy(settingsDarkTheme = next.darkTheme)
        }
    }

    fun setThemeTextColorHex(value: String) {
        val normalized = normalizeThemeColor(value)
        saveUiPreferences { it.copy(themeTextColorHex = normalized) }
        showSettingsInfo("文字颜色已更新")
    }

    fun setThemeFontFamily(value: String) {
        val normalized = UiFontFamilyChoice.fromKey(value).key
        saveUiPreferences { it.copy(themeFontFamily = normalized) }
        showSettingsInfo("字体类型已更新")
    }

    fun setThemeBubbleStyle(value: String) {
        val normalized = UiBubbleStyle.fromKey(value).key
        saveUiPreferences { it.copy(themeBubbleStyle = normalized) }
        showSettingsInfo("消息气泡样式已更新")
    }

    fun applyThemePreset(preset: String) {
        saveUiPreferences { current ->
            val keepChatBackground = current.chatBackgroundPath
            val keepDrawerBackground = current.drawerBackgroundPath
            val base = when (preset) {
                "native_clear" -> UiPreferencesConfig(
                    themePreset = "native_clear",
                    themeBubbleStyle = UiBubbleStyle.Frosted.key,
                    themeFontFamily = UiFontFamilyChoice.System.key,
                    themeTextColorHex = "",
                    themeUserBubbleColorHex = "#FFF1D8",
                    themeAssistantBubbleColorHex = "#FFFFFF",
                    themeToolBubbleColorHex = "#F4F7F1",
                    themeBubbleOpacity = 0.94f,
                    themeBubbleCornerRadius = 18f,
                    themeBubbleBorderAlpha = 0.28f,
                    themeBubbleHighlightAlpha = 0.18f,
                    themeBubbleShadowAlpha = 0.08f,
                    themeBubbleGlassStrength = 0.26f,
                    themeMessageFontSizeSp = 14f,
                    themeMessageLineHeightMultiplier = 1.18f,
                    chatBackgroundOpacity = current.chatBackgroundOpacity,
                    chatBackgroundBlur = current.chatBackgroundBlur,
                    chatBackgroundGlass = current.chatBackgroundGlass,
                    drawerBackgroundOpacity = current.drawerBackgroundOpacity,
                    drawerBackgroundBlur = current.drawerBackgroundBlur,
                    drawerBackgroundGlass = current.drawerBackgroundGlass
                )
                "aurora_water" -> UiPreferencesConfig(
                    themePreset = "aurora_water",
                    themeBubbleStyle = UiBubbleStyle.Water.key,
                    themeFontFamily = UiFontFamilyChoice.Sans.key,
                    themeTextColorHex = "",
                    themeUserBubbleColorHex = "#D7F3FF",
                    themeAssistantBubbleColorHex = "#FAFCFF",
                    themeToolBubbleColorHex = "#E8F8F3",
                    themeBubbleOpacity = 0.78f,
                    themeBubbleCornerRadius = 22f,
                    themeBubbleBorderAlpha = 0.54f,
                    themeBubbleHighlightAlpha = 0.46f,
                    themeBubbleShadowAlpha = 0.16f,
                    themeBubbleGlassStrength = 0.72f,
                    themeMessageFontSizeSp = 14.2f,
                    themeMessageLineHeightMultiplier = 1.2f,
                    chatBackgroundOpacity = 0.22f,
                    chatBackgroundBlur = current.chatBackgroundBlur,
                    chatBackgroundGlass = 0.24f,
                    drawerBackgroundOpacity = 0.26f,
                    drawerBackgroundBlur = current.drawerBackgroundBlur,
                    drawerBackgroundGlass = 0.28f
                )
                "paper_reading" -> UiPreferencesConfig(
                    themePreset = "paper_reading",
                    themeBubbleStyle = UiBubbleStyle.Frosted.key,
                    themeFontFamily = UiFontFamilyChoice.Serif.key,
                    themeTextColorHex = "#2A2622",
                    themeUserBubbleColorHex = "#EEF3FF",
                    themeAssistantBubbleColorHex = "#FFFDF8",
                    themeToolBubbleColorHex = "#F1F6EC",
                    themeBubbleOpacity = 0.98f,
                    themeBubbleCornerRadius = 17f,
                    themeBubbleBorderAlpha = 0.24f,
                    themeBubbleHighlightAlpha = 0.12f,
                    themeBubbleShadowAlpha = 0.06f,
                    themeBubbleGlassStrength = 0.18f,
                    themeMessageFontSizeSp = 15f,
                    themeMessageLineHeightMultiplier = 1.32f,
                    chatBackgroundOpacity = 0.14f,
                    chatBackgroundBlur = current.chatBackgroundBlur,
                    chatBackgroundGlass = 0.32f,
                    drawerBackgroundOpacity = current.drawerBackgroundOpacity,
                    drawerBackgroundBlur = current.drawerBackgroundBlur,
                    drawerBackgroundGlass = current.drawerBackgroundGlass
                )
                "neon_night" -> UiPreferencesConfig(
                    themePreset = "neon_night",
                    themeBubbleStyle = UiBubbleStyle.Water.key,
                    themeFontFamily = UiFontFamilyChoice.Sans.key,
                    themeTextColorHex = "",
                    themeUserBubbleColorHex = "#E6F4FF",
                    themeAssistantBubbleColorHex = "#FFFFFF",
                    themeToolBubbleColorHex = "#EAF7F3",
                    themeBubbleOpacity = 0.82f,
                    themeBubbleCornerRadius = 21f,
                    themeBubbleBorderAlpha = 0.58f,
                    themeBubbleHighlightAlpha = 0.52f,
                    themeBubbleShadowAlpha = 0.18f,
                    themeBubbleGlassStrength = 0.76f,
                    themeMessageFontSizeSp = 14f,
                    themeMessageLineHeightMultiplier = 1.2f,
                    chatBackgroundOpacity = 0.2f,
                    chatBackgroundBlur = current.chatBackgroundBlur,
                    chatBackgroundGlass = 0.42f,
                    drawerBackgroundOpacity = 0.24f,
                    drawerBackgroundBlur = current.drawerBackgroundBlur,
                    drawerBackgroundGlass = 0.44f
                )
                else -> UiPreferencesConfig(
                    themePreset = "obsidian_glass",
                    themeBubbleStyle = UiBubbleStyle.Frosted.key,
                    themeFontFamily = UiFontFamilyChoice.Sans.key,
                    themeTextColorHex = "",
                    themeUserBubbleColorHex = "#DCEBFF",
                    themeAssistantBubbleColorHex = "#FFFFFF",
                    themeToolBubbleColorHex = "#ECF7F2",
                    themeBubbleOpacity = 0.9f,
                    themeBubbleCornerRadius = 20f,
                    themeBubbleBorderAlpha = 0.38f,
                    themeBubbleHighlightAlpha = 0.28f,
                    themeBubbleShadowAlpha = 0.1f,
                    themeBubbleGlassStrength = 0.46f,
                    themeMessageFontSizeSp = 14f,
                    themeMessageLineHeightMultiplier = 1.18f,
                    chatBackgroundOpacity = 0.18f,
                    chatBackgroundBlur = current.chatBackgroundBlur,
                    chatBackgroundGlass = 0.18f,
                    drawerBackgroundOpacity = 0.22f,
                    drawerBackgroundBlur = current.drawerBackgroundBlur,
                    drawerBackgroundGlass = 0.22f
                )
            }
            base.copy(
                useChinese = current.useChinese,
                darkTheme = current.darkTheme,
                chatBackgroundPath = keepChatBackground,
                drawerBackgroundPath = keepDrawerBackground,
                themeCustomFontPath = current.themeCustomFontPath
            )
        }
        showSettingsInfo("主题预设已应用")
    }

    fun setThemeBubbleTuning(
        opacity: Float,
        cornerRadius: Float,
        borderAlpha: Float,
        highlightAlpha: Float,
        shadowAlpha: Float,
        glassStrength: Float
    ) {
        saveUiPreferences {
            it.copy(
                themeBubbleOpacity = opacity.coerceIn(0.28f, 1f),
                themeBubbleCornerRadius = cornerRadius.coerceIn(8f, 28f),
                themeBubbleBorderAlpha = borderAlpha.coerceIn(0f, 1f),
                themeBubbleHighlightAlpha = highlightAlpha.coerceIn(0f, 1f),
                themeBubbleShadowAlpha = shadowAlpha.coerceIn(0f, 0.55f),
                themeBubbleGlassStrength = glassStrength.coerceIn(0f, 1f)
            )
        }
    }

    fun setThemeBubbleColors(user: String, assistant: String, tool: String) {
        saveUiPreferences {
            it.copy(
                themeUserBubbleColorHex = normalizeThemeColor(user).ifBlank { it.themeUserBubbleColorHex },
                themeAssistantBubbleColorHex = normalizeThemeColor(assistant).ifBlank { it.themeAssistantBubbleColorHex },
                themeToolBubbleColorHex = normalizeThemeColor(tool).ifBlank { it.themeToolBubbleColorHex }
            )
        }
        showSettingsInfo("气泡颜色已更新")
    }

    fun setThemeTypographyTuning(fontSizeSp: Float, lineHeightMultiplier: Float) {
        saveUiPreferences {
            it.copy(
                themeMessageFontSizeSp = fontSizeSp.coerceIn(12f, 20f),
                themeMessageLineHeightMultiplier = lineHeightMultiplier.coerceIn(1f, 1.7f)
            )
        }
    }

    fun resetThemeDefaults() {
        val current = configStore.getUiPreferencesConfig()
        val defaults = UiPreferencesConfig()
        saveUiPreferences {
            current.copy(
                themeTextColorHex = defaults.themeTextColorHex,
                themeFontFamily = defaults.themeFontFamily,
                themeBubbleStyle = defaults.themeBubbleStyle,
                themePreset = defaults.themePreset,
                themeUserBubbleColorHex = defaults.themeUserBubbleColorHex,
                themeAssistantBubbleColorHex = defaults.themeAssistantBubbleColorHex,
                themeToolBubbleColorHex = defaults.themeToolBubbleColorHex,
                themeBubbleOpacity = defaults.themeBubbleOpacity,
                themeBubbleCornerRadius = defaults.themeBubbleCornerRadius,
                themeBubbleBorderAlpha = defaults.themeBubbleBorderAlpha,
                themeBubbleHighlightAlpha = defaults.themeBubbleHighlightAlpha,
                themeBubbleShadowAlpha = defaults.themeBubbleShadowAlpha,
                themeBubbleGlassStrength = defaults.themeBubbleGlassStrength,
                themeMessageFontSizeSp = defaults.themeMessageFontSizeSp,
                themeMessageLineHeightMultiplier = defaults.themeMessageLineHeightMultiplier,
                themeCustomFontPath = defaults.themeCustomFontPath,
                chatBackgroundPath = defaults.chatBackgroundPath,
                chatBackgroundOpacity = defaults.chatBackgroundOpacity,
                chatBackgroundBlur = defaults.chatBackgroundBlur,
                chatBackgroundGlass = defaults.chatBackgroundGlass,
                drawerBackgroundPath = defaults.drawerBackgroundPath,
                drawerBackgroundOpacity = defaults.drawerBackgroundOpacity,
                drawerBackgroundBlur = defaults.drawerBackgroundBlur,
                drawerBackgroundGlass = defaults.drawerBackgroundGlass
            )
        }
        showSettingsInfo("主题、气泡、颜色和背景已恢复默认")
    }

    fun setChatBackgroundTuning(opacity: Float, blur: Float, glass: Float) {
        saveUiPreferences {
            it.copy(
                chatBackgroundOpacity = opacity.coerceIn(0f, 1f),
                chatBackgroundBlur = blur.coerceIn(0f, 40f),
                chatBackgroundGlass = glass.coerceIn(0f, 1f)
            )
        }
    }

    fun setDrawerBackgroundTuning(opacity: Float, blur: Float, glass: Float) {
        saveUiPreferences {
            it.copy(
                drawerBackgroundOpacity = opacity.coerceIn(0f, 1f),
                drawerBackgroundBlur = blur.coerceIn(0f, 40f),
                drawerBackgroundGlass = glass.coerceIn(0f, 1f)
            )
        }
    }

    fun setChatBackgroundFromUri(uri: Uri?) {
        saveThemeImage(uri = uri, target = "chat")
    }

    fun setDrawerBackgroundFromUri(uri: Uri?) {
        saveThemeImage(uri = uri, target = "drawer")
    }

    fun setCustomFontFromUri(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val name = resolveDisplayName(app, uri).lowercase(Locale.US)
                val mime = app.contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
                val allowed = name.endsWith(".ttf") || name.endsWith(".otf") ||
                    mime.contains("font") || mime.contains("opentype") || mime.contains("truetype")
                if (!allowed) throw IllegalArgumentException("请选择 ttf 或 otf 字体文件")
                val dir = File(AppStoragePaths.storageRoot(app), "themes").apply { mkdirs() }
                val ext = if (name.endsWith(".otf")) "otf" else "ttf"
                val file = File(dir, "custom_font_${System.currentTimeMillis()}.$ext")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException("无法读取字体文件")
                file.absolutePath
            }.onSuccess { path ->
                saveUiPreferences { it.copy(themeCustomFontPath = path, themeFontFamily = UiFontFamilyChoice.Custom.key) }
                showSettingsInfo("自定义字体已启用")
            }.onFailure { t ->
                showSettingsInfo("字体设置失败：${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    fun clearChatBackground() {
        saveUiPreferences { it.copy(chatBackgroundPath = "") }
        showSettingsInfo("聊天背景已清除")
    }

    fun clearDrawerBackground() {
        saveUiPreferences { it.copy(drawerBackgroundPath = "") }
        showSettingsInfo("侧边栏背景已清除")
    }

    private fun saveUiPreferences(transform: (UiPreferencesConfig) -> UiPreferencesConfig) {
        val next = transform(configStore.getUiPreferencesConfig())
        configStore.saveUiPreferencesConfig(next)
        applyUiPreferencesToState(next)
    }

    private fun applyUiPreferencesToState(config: UiPreferencesConfig) {
        _uiState.update {
            it.copy(
                settingsUseChinese = config.useChinese,
                settingsDarkTheme = config.darkTheme,
                themeTextColorHex = config.themeTextColorHex,
                themeFontFamily = config.themeFontFamily,
                themeBubbleStyle = config.themeBubbleStyle,
                themePreset = config.themePreset,
                themeUserBubbleColorHex = config.themeUserBubbleColorHex,
                themeAssistantBubbleColorHex = config.themeAssistantBubbleColorHex,
                themeToolBubbleColorHex = config.themeToolBubbleColorHex,
                themeBubbleOpacity = config.themeBubbleOpacity,
                themeBubbleCornerRadius = config.themeBubbleCornerRadius,
                themeBubbleBorderAlpha = config.themeBubbleBorderAlpha,
                themeBubbleHighlightAlpha = config.themeBubbleHighlightAlpha,
                themeBubbleShadowAlpha = config.themeBubbleShadowAlpha,
                themeBubbleGlassStrength = config.themeBubbleGlassStrength,
                themeMessageFontSizeSp = config.themeMessageFontSizeSp,
                themeMessageLineHeightMultiplier = config.themeMessageLineHeightMultiplier,
                themeCustomFontPath = config.themeCustomFontPath,
                chatBackgroundPath = config.chatBackgroundPath,
                chatBackgroundOpacity = config.chatBackgroundOpacity,
                chatBackgroundBlur = config.chatBackgroundBlur,
                chatBackgroundGlass = config.chatBackgroundGlass,
                drawerBackgroundPath = config.drawerBackgroundPath,
                drawerBackgroundOpacity = config.drawerBackgroundOpacity,
                drawerBackgroundBlur = config.drawerBackgroundBlur,
                drawerBackgroundGlass = config.drawerBackgroundGlass
            )
        }
    }

    private fun saveThemeImage(uri: Uri?, target: String) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val mime = app.contentResolver.getType(uri).orEmpty()
                if (!mime.startsWith("image/")) throw IllegalArgumentException("请选择图片文件")
                val dir = File(AppStoragePaths.storageRoot(app), "themes").apply { mkdirs() }
                val ext = when (mime.substringAfter('/', "").lowercase(Locale.US)) {
                    "jpeg", "jpg" -> "jpg"
                    "png" -> "png"
                    "webp" -> "webp"
                    else -> "img"
                }
                val file = File(dir, "${target}_background_${System.currentTimeMillis()}.$ext")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException("无法读取背景图片")
                file.absolutePath
            }.onSuccess { path ->
                if (target == "chat") {
                    saveUiPreferences { it.copy(chatBackgroundPath = path) }
                    showSettingsInfo("聊天背景已更新")
                } else {
                    saveUiPreferences { it.copy(drawerBackgroundPath = path) }
                    showSettingsInfo("侧边栏背景已更新")
                }
            }.onFailure { t ->
                showSettingsInfo("背景图片设置失败：${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    private fun normalizeThemeColor(value: String): String {
        val raw = value.trim().removePrefix("#")
        return if (raw.matches(Regex("[A-Fa-f0-9]{6}"))) "#${raw.uppercase(Locale.US)}" else ""
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    fun onOnboardingUserDisplayNameChanged(value: String) =
        onboardingCoordinator.onUserDisplayNameChanged(value)

    fun onOnboardingAgentDisplayNameChanged(value: String) =
        onboardingCoordinator.onAgentDisplayNameChanged(value)

    fun completeOnboarding() = onboardingCoordinator.completeOnboarding()

    fun onSettingsTelegramBotTokenChanged(value: String) =
        runtimeCoordinator.onSettingsTelegramBotTokenChanged(value)

    fun onSettingsTelegramAllowedChatIdChanged(value: String) =
        runtimeCoordinator.onSettingsTelegramAllowedChatIdChanged(value)

    fun onSettingsDiscordWebhookUrlChanged(value: String) =
        runtimeCoordinator.onSettingsDiscordWebhookUrlChanged(value)

    fun onSettingsMcpEnabledChanged(value: Boolean) =
        runtimeCoordinator.onSettingsMcpEnabledChanged(value)

    fun onSettingsMcpServerNameChanged(value: String) =
        runtimeCoordinator.onSettingsMcpServerNameChanged(value)

    fun onSettingsMcpServerUrlChanged(value: String) =
        runtimeCoordinator.onSettingsMcpServerUrlChanged(value)

    fun onSettingsMcpAuthTokenChanged(value: String) =
        runtimeCoordinator.onSettingsMcpAuthTokenChanged(value)

    fun onSettingsMcpToolTimeoutSecondsChanged(value: String) =
        runtimeCoordinator.onSettingsMcpToolTimeoutSecondsChanged(value)

    fun addSettingsMcpServer() = runtimeCoordinator.addSettingsMcpServer()

    fun removeSettingsMcpServer(serverId: String) =
        runtimeCoordinator.removeSettingsMcpServer(serverId)

    fun updateSettingsMcpServerName(serverId: String, value: String) =
        runtimeCoordinator.updateSettingsMcpServerName(serverId, value)

    fun updateSettingsMcpServerUrl(serverId: String, value: String) =
        runtimeCoordinator.updateSettingsMcpServerUrl(serverId, value)

    fun updateSettingsMcpServerAuthToken(serverId: String, value: String) =
        runtimeCoordinator.updateSettingsMcpServerAuthToken(serverId, value)

    fun updateSettingsMcpServerTimeout(serverId: String, value: String) =
        runtimeCoordinator.updateSettingsMcpServerTimeout(serverId, value)

    fun refreshCronJobs() = runtimeCoordinator.refreshCronJobs()

    private fun refreshCronJobsInternal() {
        viewModelScope.launch {
            _uiState.update { it.copy(settingsCronJobsLoading = true) }
            runCatching { withContext(Dispatchers.IO) { cronService.listJobs(includeDisabled = true) } }
                .onSuccess { jobs ->
                    _uiState.update {
                        it.copy(
                            settingsCronJobsLoading = false,
                            settingsCronJobs = jobs.map { job -> job.toUiCronJob() }
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(
                            settingsCronJobsLoading = false,
                            settingsInfo = "Load cron jobs failed: ${t.message ?: t.javaClass.simpleName}"
                        )
                    }
                }
        }
    }

    fun setCronJobEnabled(jobId: String, enabled: Boolean) =
        runtimeCoordinator.setCronJobEnabled(jobId, enabled)

    private fun setCronJobEnabledInternal(jobId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { cronService.enableJob(jobId, enabled) } }
                .onSuccess { refreshCronJobs() }
                .onFailure { t ->
                    _uiState.update { it.copy(settingsInfo = "Update cron job failed: ${t.message ?: t.javaClass.simpleName}") }
                }
        }
    }

    fun runCronJobNow(jobId: String) = runtimeCoordinator.runCronJobNow(jobId)

    private fun runCronJobNowInternal(jobId: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { cronService.runJob(jobId, force = true) } }
                .onSuccess { refreshCronJobs() }
                .onFailure { t ->
                    _uiState.update { it.copy(settingsInfo = "Run cron job failed: ${t.message ?: t.javaClass.simpleName}") }
                }
        }
    }

    fun removeCronJob(jobId: String) = runtimeCoordinator.removeCronJob(jobId)

    private fun removeCronJobInternal(jobId: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { cronService.removeJob(jobId) } }
                .onSuccess { refreshCronJobs() }
                .onFailure { t ->
                    _uiState.update { it.copy(settingsInfo = "Remove cron job failed: ${t.message ?: t.javaClass.simpleName}") }
                }
        }
    }


    fun selectSession(sessionId: String): Unit {
        sessionCoordinator.selectSession(sessionId)
        refreshAgentPanels()
    }

    fun createSession(displayName: String): Unit {
        sessionCoordinator.createSession(displayName)
    }

    private fun createSessionInternal(displayName: String) {
        val title = displayName.trim()
        viewModelScope.launch {
            runCatching {
                val sessionId = "session:${System.currentTimeMillis()}"
                sessionRepository.createSession(sessionId, title)
                sessionRepository.touch(sessionId)
                sessionId
            }.onSuccess { sid ->
                selectSession(sid)
                _uiState.update { it.copy(settingsInfo = "Session created.") }
            }.onFailure { t ->
                _uiState.update { it.copy(settingsInfo = "Create session failed: ${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    fun renameSession(sessionId: String, displayName: String): Unit {
        sessionCoordinator.renameSession(sessionId, displayName)
    }

    private fun renameSessionInternal(sessionId: String, displayName: String) {
        val sid = sessionId.trim()
        val title = displayName.trim()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    sessionRepository.renameSession(sid, title)
                    sessionRepository.touch(sid)
                }
            }.onSuccess {
                if (currentSessionId == sid) {
                    _uiState.update { it.copy(currentSessionTitle = title) }
                }
                _uiState.update { it.copy(settingsInfo = "Session renamed.") }
            }.onFailure { t ->
                _uiState.update { it.copy(settingsInfo = "Rename session failed: ${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    fun deleteSession(sessionId: String): Unit {
        sessionCoordinator.deleteSession(sessionId)
    }

    private fun deleteSessionInternal(sessionId: String) {
        val sid = sessionId.trim()
        viewModelScope.launch {
            runCatching {
                if (currentSessionId == sid) {
                    generatingJob?.cancel()
                }
                withContext(Dispatchers.IO) {
                    sessionRepository.deleteSession(sid)
                    configStore.clearSessionChannelBinding(sid)
                    cronService.listJobs(includeDisabled = true)
                        .filter { it.payload.sessionId?.trim() == sid }
                        .forEach { job -> cronService.removeJob(job.id) }
                }
            }.onSuccess {
                if (currentSessionId == sid) {
                    selectSession(AppSession.LOCAL_SESSION_ID)
                }
                refreshSessionBindingsInState()
                applyGatewayRuntimeConfig(configStore.getChannelsConfig())
                _uiState.update { it.copy(settingsInfo = "Session deleted.") }
            }.onFailure { t ->
                _uiState.update { it.copy(settingsInfo = "Delete session failed: ${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    @Suppress("LongParameterList")
    fun saveSessionChannelBinding(
        sessionId: String,
        enabled: Boolean = true,
        channel: String,
        chatId: String,
        targetDisplayName: String = "",
        telegramBotToken: String = "",
        telegramAllowedChatId: String = "",
        discordBotToken: String = "",
        discordResponseMode: String = "mention",
        discordAllowedUserIds: String = "",
        slackBotToken: String = "",
        slackAppToken: String = "",
        slackResponseMode: String = "mention",
        slackAllowedUserIds: String = "",
        feishuAppId: String = "",
        feishuAppSecret: String = "",
        feishuEncryptKey: String = "",
        feishuVerificationToken: String = "",
        feishuResponseMode: String = "mention",
        feishuAllowedOpenIds: String = "",
        emailConsentGranted: Boolean = false,
        emailImapHost: String = "",
        emailImapPort: String = "993",
        emailImapUsername: String = "",
        emailImapPassword: String = "",
        emailSmtpHost: String = "",
        emailSmtpPort: String = "587",
        emailSmtpUsername: String = "",
        emailSmtpPassword: String = "",
        emailFromAddress: String = "",
        emailAutoReplyEnabled: Boolean = true,
        wecomBotId: String = "",
        wecomSecret: String = "",
        wecomAllowedUserIds: String = ""
    ) = channelBindingCoordinator.saveSessionChannelBinding(
        sessionId = sessionId,
        enabled = enabled,
        channel = channel,
        chatId = chatId,
        targetDisplayName = targetDisplayName,
        telegramBotToken = telegramBotToken,
        telegramAllowedChatId = telegramAllowedChatId,
        discordBotToken = discordBotToken,
        discordResponseMode = discordResponseMode,
        discordAllowedUserIds = discordAllowedUserIds,
        slackBotToken = slackBotToken,
        slackAppToken = slackAppToken,
        slackResponseMode = slackResponseMode,
        slackAllowedUserIds = slackAllowedUserIds,
        feishuAppId = feishuAppId,
        feishuAppSecret = feishuAppSecret,
        feishuEncryptKey = feishuEncryptKey,
        feishuVerificationToken = feishuVerificationToken,
        feishuResponseMode = feishuResponseMode,
        feishuAllowedOpenIds = feishuAllowedOpenIds,
        emailConsentGranted = emailConsentGranted,
        emailImapHost = emailImapHost,
        emailImapPort = emailImapPort,
        emailImapUsername = emailImapUsername,
        emailImapPassword = emailImapPassword,
        emailSmtpHost = emailSmtpHost,
        emailSmtpPort = emailSmtpPort,
        emailSmtpUsername = emailSmtpUsername,
        emailSmtpPassword = emailSmtpPassword,
        emailFromAddress = emailFromAddress,
        emailAutoReplyEnabled = emailAutoReplyEnabled,
        wecomBotId = wecomBotId,
        wecomSecret = wecomSecret,
        wecomAllowedUserIds = wecomAllowedUserIds
    )

    private fun saveSessionChannelBindingRequestInternal(request: SaveSessionChannelBindingRequest) {
        saveSessionChannelBindingInternal(
            sessionId = request.sessionId,
            enabled = request.enabled,
            channel = request.channel,
            chatId = request.chatId,
            targetDisplayName = request.targetDisplayName,
            telegramBotToken = request.telegramBotToken,
            telegramAllowedChatId = request.telegramAllowedChatId,
            discordBotToken = request.discordBotToken,
            discordResponseMode = request.discordResponseMode,
            discordAllowedUserIds = request.discordAllowedUserIds,
            slackBotToken = request.slackBotToken,
            slackAppToken = request.slackAppToken,
            slackResponseMode = request.slackResponseMode,
            slackAllowedUserIds = request.slackAllowedUserIds,
            feishuAppId = request.feishuAppId,
            feishuAppSecret = request.feishuAppSecret,
            feishuEncryptKey = request.feishuEncryptKey,
            feishuVerificationToken = request.feishuVerificationToken,
            feishuResponseMode = request.feishuResponseMode,
            feishuAllowedOpenIds = request.feishuAllowedOpenIds,
            emailConsentGranted = request.emailConsentGranted,
            emailImapHost = request.emailImapHost,
            emailImapPort = request.emailImapPort,
            emailImapUsername = request.emailImapUsername,
            emailImapPassword = request.emailImapPassword,
            emailSmtpHost = request.emailSmtpHost,
            emailSmtpPort = request.emailSmtpPort,
            emailSmtpUsername = request.emailSmtpUsername,
            emailSmtpPassword = request.emailSmtpPassword,
            emailFromAddress = request.emailFromAddress,
            emailAutoReplyEnabled = request.emailAutoReplyEnabled,
            wecomBotId = request.wecomBotId,
            wecomSecret = request.wecomSecret,
            wecomAllowedUserIds = request.wecomAllowedUserIds
        )
    }

    @Suppress("LongParameterList")
    private fun saveSessionChannelBindingInternal(
        sessionId: String,
        enabled: Boolean = true,
        channel: String,
        chatId: String,
        targetDisplayName: String = "",
        telegramBotToken: String = "",
        telegramAllowedChatId: String = "",
        discordBotToken: String = "",
        discordResponseMode: String = "mention",
        discordAllowedUserIds: String = "",
        slackBotToken: String = "",
        slackAppToken: String = "",
        slackResponseMode: String = "mention",
        slackAllowedUserIds: String = "",
        feishuAppId: String = "",
        feishuAppSecret: String = "",
        feishuEncryptKey: String = "",
        feishuVerificationToken: String = "",
        feishuResponseMode: String = "mention",
        feishuAllowedOpenIds: String = "",
        emailConsentGranted: Boolean = false,
        emailImapHost: String = "",
        emailImapPort: String = "993",
        emailImapUsername: String = "",
        emailImapPassword: String = "",
        emailSmtpHost: String = "",
        emailSmtpPort: String = "587",
        emailSmtpUsername: String = "",
        emailSmtpPassword: String = "",
        emailFromAddress: String = "",
        emailAutoReplyEnabled: Boolean = true,
        wecomBotId: String = "",
        wecomSecret: String = "",
        wecomAllowedUserIds: String = ""
    ) {
        val sid = sessionId.trim()
        if (sid.isBlank()) return
        viewModelScope.launch {
            var runtimeChannelsConfig: ChannelsConfig? = null
            var autoEnabledGateway = false
            var autoDisabledGateway = false
            runCatching {
                val normalizedChannel = SessionChannelBindingRules.normalizeChannel(channel)
                val normalizedAllowedChatId = telegramAllowedChatId.trim()
                val rawChatId = chatId.trim()
                val normalizedChatId = when (normalizedChannel) {
                    "discord" -> normalizeDiscordChannelId(rawChatId)
                    "slack" -> normalizeSlackChannelId(rawChatId)
                    "feishu" -> normalizeFeishuTargetId(rawChatId)
                    "email" -> normalizeEmailAddress(rawChatId)
                    "wecom" -> normalizeWeComTargetId(rawChatId)
                    "telegram" -> rawChatId.ifBlank { normalizedAllowedChatId }
                    else -> rawChatId
                }
                if (normalizedChannel.isBlank()) {
                    configStore.clearSessionChannelBinding(sid)
                    runtimeChannelsConfig = configStore.getChannelsConfig()
                } else {
                    val normalizedTelegramToken = telegramBotToken.trim()
                    val normalizedDiscordToken = discordBotToken.trim()
                    val normalizedDiscordResponseMode = normalizeDiscordResponseMode(discordResponseMode)
                    val normalizedDiscordAllowedUserIds = parseAllowedUserIds(discordAllowedUserIds)
                    val normalizedSlackBotToken = slackBotToken.trim()
                    val normalizedSlackAppToken = slackAppToken.trim()
                    val normalizedSlackResponseMode = normalizeSlackResponseMode(slackResponseMode)
                    val normalizedSlackAllowedUserIds = parseAllowedUserIds(slackAllowedUserIds)
                    val normalizedFeishuAppId = feishuAppId.trim()
                    val normalizedFeishuAppSecret = feishuAppSecret.trim()
                    val normalizedFeishuEncryptKey = feishuEncryptKey.trim()
                    val normalizedFeishuVerificationToken = feishuVerificationToken.trim()
                    val normalizedFeishuResponseMode = "mention"
                    val normalizedFeishuAllowedOpenIds = parseAllowedUserIds(feishuAllowedOpenIds)
                    val normalizedEmailImapHost = emailImapHost.trim()
                    val normalizedEmailImapPort = emailImapPort.trim().toIntOrNull()
                    val normalizedEmailImapUsername = emailImapUsername.trim()
                    val normalizedEmailImapPassword = emailImapPassword
                    val normalizedEmailSmtpHost = emailSmtpHost.trim()
                    val normalizedEmailSmtpPort = emailSmtpPort.trim().toIntOrNull()
                    val normalizedEmailSmtpUsername = emailSmtpUsername.trim()
                    val normalizedEmailSmtpPassword = emailSmtpPassword
                    val normalizedEmailFromAddress = normalizeEmailAddress(emailFromAddress)
                    val normalizedWeComBotId = wecomBotId.trim()
                    val normalizedWeComSecret = wecomSecret.trim()
                    val normalizedWeComAllowedUserIds = parseAllowedUserIds(wecomAllowedUserIds)
                    when (normalizedChannel) {
                        "telegram" -> {
                            if (normalizedTelegramToken.isBlank()) {
                                throw IllegalArgumentException("Telegram bot token is required")
                            }
                            if (normalizedChatId.isNotBlank() && normalizedChatId.any { !it.isDigit() && it != '-' }) {
                                throw IllegalArgumentException("Telegram Chat ID must be numeric")
                            }
                        }

                        "discord" -> {
                            if (normalizedChatId.isBlank()) {
                                throw IllegalArgumentException("Discord Channel ID is required")
                            }
                            if (!isDiscordSnowflake(normalizedChatId)) {
                                throw IllegalArgumentException("Discord Channel ID must be a numeric ID (15-30 digits)")
                            }
                            if (normalizedDiscordToken.isBlank()) {
                                throw IllegalArgumentException("Discord bot token is required")
                            }
                            if (normalizedDiscordResponseMode !in setOf("mention", "open")) {
                                throw IllegalArgumentException("Discord response mode must be mention or open")
                            }
                        }

                        "slack" -> {
                            if (normalizedChatId.isBlank()) {
                                throw IllegalArgumentException("Slack channel ID is required")
                            }
                            if (!isSlackChannelId(normalizedChatId)) {
                                throw IllegalArgumentException("Slack channel ID must look like C/G/D + letters/numbers")
                            }
                            if (normalizedSlackBotToken.isBlank()) {
                                throw IllegalArgumentException("Slack bot token is required")
                            }
                            if (normalizedSlackAppToken.isBlank()) {
                                throw IllegalArgumentException("Slack app token is required")
                            }
                            if (normalizedSlackResponseMode !in setOf("mention", "open")) {
                                throw IllegalArgumentException("Slack response mode must be mention or open")
                            }
                        }

                        "feishu" -> {
                            if (normalizedFeishuAppId.isBlank()) {
                                throw IllegalArgumentException("Feishu App ID is required")
                            }
                            if (normalizedFeishuAppSecret.isBlank()) {
                                throw IllegalArgumentException("Feishu App Secret is required")
                            }
                            if (normalizedFeishuResponseMode !in setOf("mention", "open")) {
                                throw IllegalArgumentException("Feishu response mode must be mention or open")
                            }
                            if (normalizedChatId.isNotBlank() && !isFeishuTargetId(normalizedChatId)) {
                                throw IllegalArgumentException("Feishu target must look like ou_xxx or oc_xxx")
                            }
                        }

                        "email" -> {
                            if (normalizedChatId.isNotBlank() && !isEmailAddress(normalizedChatId)) {
                                throw IllegalArgumentException("Email sender address is invalid")
                            }
                            if (!emailConsentGranted) {
                                throw IllegalArgumentException("Email mailbox consent must be enabled")
                            }
                            if (normalizedEmailImapHost.isBlank()) {
                                throw IllegalArgumentException("IMAP host is required")
                            }
                            if (normalizedEmailImapPort == null || normalizedEmailImapPort !in 1..65535) {
                                throw IllegalArgumentException("IMAP port must be between 1 and 65535")
                            }
                            if (normalizedEmailImapUsername.isBlank()) {
                                throw IllegalArgumentException("IMAP username is required")
                            }
                            if (normalizedEmailImapPassword.isBlank()) {
                                throw IllegalArgumentException("IMAP password is required")
                            }
                            if (normalizedEmailSmtpHost.isBlank()) {
                                throw IllegalArgumentException("SMTP host is required")
                            }
                            if (normalizedEmailSmtpPort == null || normalizedEmailSmtpPort !in 1..65535) {
                                throw IllegalArgumentException("SMTP port must be between 1 and 65535")
                            }
                            if (normalizedEmailSmtpUsername.isBlank()) {
                                throw IllegalArgumentException("SMTP username is required")
                            }
                            if (normalizedEmailSmtpPassword.isBlank()) {
                                throw IllegalArgumentException("SMTP password is required")
                            }
                            if (normalizedEmailFromAddress.isBlank() || !isEmailAddress(normalizedEmailFromAddress)) {
                                throw IllegalArgumentException("From address is required")
                            }
                        }

                        "wecom" -> {
                            if (normalizedWeComBotId.isBlank()) {
                                throw IllegalArgumentException("WeCom Bot ID is required")
                            }
                            if (normalizedWeComSecret.isBlank()) {
                                throw IllegalArgumentException("WeCom Secret is required")
                            }
                        }

                        else -> throw IllegalArgumentException("Unsupported channel: $normalizedChannel")
                    }
                    configStore.saveSessionChannelBinding(
                        SessionChannelBinding(
                            sessionId = sid,
                            enabled = enabled,
                            channel = normalizedChannel,
                            chatId = normalizedChatId,
                            telegramBotToken = normalizedTelegramToken,
                            telegramAllowedChatId = normalizedAllowedChatId.ifBlank { null },
                            discordBotToken = normalizedDiscordToken,
                            discordResponseMode = normalizedDiscordResponseMode,
                            discordAllowedUserIds = normalizedDiscordAllowedUserIds,
                            slackBotToken = normalizedSlackBotToken,
                            slackAppToken = normalizedSlackAppToken,
                            slackResponseMode = normalizedSlackResponseMode,
                            slackAllowedUserIds = normalizedSlackAllowedUserIds,
                            feishuAppId = normalizedFeishuAppId,
                            feishuAppSecret = normalizedFeishuAppSecret,
                            feishuEncryptKey = normalizedFeishuEncryptKey,
                            feishuVerificationToken = normalizedFeishuVerificationToken,
                            feishuResponseMode = normalizedFeishuResponseMode,
                            feishuAllowedOpenIds = normalizedFeishuAllowedOpenIds,
                            emailConsentGranted = emailConsentGranted,
                            emailImapHost = normalizedEmailImapHost,
                            emailImapPort = normalizedEmailImapPort ?: 993,
                            emailImapUsername = normalizedEmailImapUsername,
                            emailImapPassword = normalizedEmailImapPassword,
                            emailSmtpHost = normalizedEmailSmtpHost,
                            emailSmtpPort = normalizedEmailSmtpPort ?: 587,
                            emailSmtpUsername = normalizedEmailSmtpUsername,
                            emailSmtpPassword = normalizedEmailSmtpPassword,
                            emailFromAddress = normalizedEmailFromAddress,
                            emailAutoReplyEnabled = emailAutoReplyEnabled,
                            wecomBotId = normalizedWeComBotId,
                            wecomSecret = normalizedWeComSecret,
                            wecomAllowedUserIds = normalizedWeComAllowedUserIds
                        )
                    )
                }
                val shouldEnableGateway = hasActiveGatewayBinding(configStore.getSessionChannelBindings())
                val currentChannels = configStore.getChannelsConfig()
                runtimeChannelsConfig = if (currentChannels.enabled == shouldEnableGateway) {
                    currentChannels
                } else {
                    if (shouldEnableGateway) autoEnabledGateway = true else autoDisabledGateway = true
                    currentChannels.copy(enabled = shouldEnableGateway).also { cfg ->
                        configStore.saveChannelsConfig(cfg)
                    }
                }
            }.onSuccess {
                refreshSessionBindingsInState()
                applyGatewayRuntimeConfig(runtimeChannelsConfig ?: configStore.getChannelsConfig())
                _uiState.update {
                    val savedChannel = normalizedChannelForInfo(configStore, sid)
                    val savedTarget = normalizedTargetForInfo(configStore, sid)
                    val displayTarget = targetDisplayName.trim().ifBlank { savedTarget }
                    val channelLabel = infoChannelLabel(savedChannel, it.settingsUseChinese)
                    val baseInfo = if (autoEnabledGateway) {
                        "Session channel binding saved. Channels gateway enabled."
                    } else if (autoDisabledGateway) {
                        "Session channel binding saved. Channels gateway disabled (no active session channel)."
                    } else if (savedChannel == "telegram" && normalizedTargetMissingForInfo(configStore, sid)) {
                        "Telegram token saved. Tap Detect Chats, choose the conversation, then save again."
                    } else if (savedChannel == "feishu" && normalizedTargetMissingForInfo(configStore, sid)) {
                        "Feishu credentials saved. Next, in Events & Callbacks select Long Connection and add im.message.receive_v1, then grant the message permissions, publish/open the app, send an @mention message, and use Detect Chats."
                    } else if (savedChannel == "email" && normalizedTargetMissingForInfo(configStore, sid)) {
                        "Email account saved. Mailbox polling starting. Send one email to this account, then use Detect Senders to finish binding."
                    } else if (savedChannel == "wecom" && normalizedTargetMissingForInfo(configStore, sid)) {
                        "WeCom credentials saved. Long connection starting. Keep LGClaw open, send one message to the bot, then use Detect Chats."
                    } else if (savedChannel.isNotBlank() && displayTarget.isNotBlank()) {
                        "Bound to $channelLabel: $displayTarget"
                    } else if (savedChannel.isNotBlank()) {
                        "Saved $channelLabel binding."
                    } else {
                        "Session channel binding saved."
                    }
                    it.copy(
                        settingsGatewayEnabled = runtimeChannelsConfig?.enabled ?: it.settingsGatewayEnabled,
                        settingsInfo = baseInfo
                    )
                }
            }.onFailure { t ->
                _uiState.update { it.copy(settingsInfo = "Save session channel binding failed: ${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    fun getSessionChannelDraft(sessionId: String): UiSessionChannelDraft =
        channelBindingCoordinator.getSessionChannelDraft(sessionId)

    private fun getSessionChannelDraftInternal(sessionId: String): UiSessionChannelDraft {
        val sid = sessionId.trim()
        if (sid.isBlank()) return UiSessionChannelDraft()
        val binding = configStore.getSessionChannelBindings()
            .firstOrNull { it.sessionId.trim() == sid }
        return UiSessionChannelDraft(
            enabled = binding?.enabled ?: true,
            channel = binding?.channel.orEmpty(),
            chatId = binding?.chatId.orEmpty(),
            telegramBotToken = binding?.telegramBotToken.orEmpty(),
            telegramAllowedChatId = binding?.telegramAllowedChatId.orEmpty(),
            discordBotToken = binding?.discordBotToken.orEmpty(),
            discordResponseMode = normalizeDiscordResponseMode(binding?.discordResponseMode.orEmpty()),
            discordAllowedUserIds = binding?.discordAllowedUserIds.orEmpty().joinToString("\n"),
            slackBotToken = binding?.slackBotToken.orEmpty(),
            slackAppToken = binding?.slackAppToken.orEmpty(),
            slackResponseMode = normalizeSlackResponseMode(binding?.slackResponseMode.orEmpty()),
            slackAllowedUserIds = binding?.slackAllowedUserIds.orEmpty().joinToString("\n"),
            feishuAppId = binding?.feishuAppId.orEmpty(),
            feishuAppSecret = binding?.feishuAppSecret.orEmpty(),
            feishuEncryptKey = binding?.feishuEncryptKey.orEmpty(),
            feishuVerificationToken = binding?.feishuVerificationToken.orEmpty(),
            feishuResponseMode = "mention",
            feishuAllowedOpenIds = binding?.feishuAllowedOpenIds.orEmpty().joinToString("\n"),
            emailConsentGranted = binding?.emailConsentGranted ?: false,
            emailImapHost = binding?.emailImapHost.orEmpty(),
            emailImapPort = (binding?.emailImapPort ?: 993).toString(),
            emailImapUsername = binding?.emailImapUsername.orEmpty(),
            emailImapPassword = binding?.emailImapPassword.orEmpty(),
            emailSmtpHost = binding?.emailSmtpHost.orEmpty(),
            emailSmtpPort = (binding?.emailSmtpPort ?: 587).toString(),
            emailSmtpUsername = binding?.emailSmtpUsername.orEmpty(),
            emailSmtpPassword = binding?.emailSmtpPassword.orEmpty(),
            emailFromAddress = binding?.emailFromAddress.orEmpty(),
            emailAutoReplyEnabled = binding?.emailAutoReplyEnabled ?: true,
            wecomBotId = binding?.wecomBotId.orEmpty(),
            wecomSecret = binding?.wecomSecret.orEmpty(),
            wecomAllowedUserIds = binding?.wecomAllowedUserIds.orEmpty().joinToString("\n")
        )
    }

    fun setSessionChannelEnabled(sessionId: String, enabled: Boolean) =
        channelBindingCoordinator.setSessionChannelEnabled(sessionId, enabled)

    private fun setSessionChannelEnabledInternalFacade(sessionId: String, enabled: Boolean) {
        val sid = sessionId.trim()
        if (sid.isBlank()) return
        viewModelScope.launch {
            runCatching {
                setSessionChannelEnabledInternal(
                    sessionId = sid,
                    sessionTitle = null,
                    enabled = enabled
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        settingsGatewayEnabled = configStore.getChannelsConfig().enabled,
                        settingsInfo = if (enabled) "Session channel enabled." else "Session channel disabled."
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(settingsInfo = "Update session channel switch failed: ${t.message ?: t.javaClass.simpleName}")
                }
            }
        }
    }

    fun discoverTelegramChatsForBinding(botToken: String) =
        channelBindingCoordinator.discoverTelegramChatsForBinding(botToken)

    private fun discoverTelegramChatsForBindingInternal(botToken: String) {
        val token = botToken.trim()
        if (token.isBlank()) {
            _uiState.update {
                it.copy(
                    sessionBindingTelegramDiscoveryAttempted = true,
                    sessionBindingTelegramDiscovering = false,
                    sessionBindingTelegramCandidates = emptyList(),
                    sessionBindingTelegramInfo = "Please enter Telegram bot token first."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sessionBindingTelegramDiscoveryAttempted = true,
                    sessionBindingTelegramDiscovering = true,
                    sessionBindingTelegramCandidates = emptyList(),
                    sessionBindingTelegramInfo = null
                )
            }
            showSettingsInfo("Detecting Telegram chats...")
            runCatching {
                withContext(Dispatchers.IO) { fetchTelegramChatCandidates(token) }
            }.onSuccess { candidates ->
                _uiState.update {
                    it.copy(
                        sessionBindingTelegramDiscoveryAttempted = true,
                        sessionBindingTelegramDiscovering = false,
                        sessionBindingTelegramCandidates = candidates,
                        sessionBindingTelegramInfo = if (candidates.isEmpty()) {
                            "No Telegram chats found yet. Send the bot one message, then detect again."
                        } else {
                            null
                        }
                    )
                }
                if (candidates.isNotEmpty()) {
                    showSettingsInfo("Telegram chats discovered. Tap one to use.")
                } else {
                    showSettingsInfo("No Telegram chats found yet. Send the bot one message, then detect again.")
                }
            }.onFailure { t ->
                showSettingsInfo("Discover chats failed: ${t.message ?: t.javaClass.simpleName}")
                _uiState.update {
                    it.copy(
                        sessionBindingTelegramDiscoveryAttempted = true,
                        sessionBindingTelegramDiscovering = false,
                        sessionBindingTelegramCandidates = emptyList(),
                        sessionBindingTelegramInfo = "Discover chats failed: ${t.message ?: t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun clearTelegramChatDiscovery() = channelBindingCoordinator.clearTelegramChatDiscovery()

    private fun clearTelegramChatDiscoveryInternal() {
        _uiState.update {
            it.copy(
                sessionBindingTelegramDiscoveryAttempted = false,
                sessionBindingTelegramDiscovering = false,
                sessionBindingTelegramCandidates = emptyList(),
                sessionBindingTelegramInfo = null
            )
        }
    }

    fun discoverFeishuChatsForBinding(
        appId: String,
        appSecret: String,
        encryptKey: String,
        verificationToken: String
    ) = channelBindingCoordinator.discoverFeishuChatsForBinding(
        appId = appId,
        appSecret = appSecret,
        encryptKey = encryptKey,
        verificationToken = verificationToken
    )

    private fun discoverFeishuChatsForBindingInternal(
        appId: String,
        appSecret: String,
        encryptKey: String,
        verificationToken: String
    ) {
        _uiState.update {
            it.copy(
                sessionBindingFeishuDiscoveryAttempted = true,
                sessionBindingFeishuDiscovering = true,
                sessionBindingFeishuCandidates = emptyList(),
                sessionBindingFeishuInfo = null
            )
        }
        viewModelScope.launch {
            showSettingsInfo("Detecting Feishu chats...")
            val requestedAdapterKeys = buildFeishuAdapterKeys(
                appId = appId,
                appSecret = appSecret,
                encryptKey = encryptKey,
                verificationToken = verificationToken
            )
            val currentBindingAdapterKeys = configStore.getSessionChannelBindings()
                .firstOrNull {
                    it.sessionId.trim() == currentSessionId.trim() &&
                        it.enabled &&
                        it.channel.trim().equals("feishu", ignoreCase = true)
                }
                ?.let(::adapterKeysForBinding)
                .orEmpty()

            var result = collectFeishuDiscoveryResult(
                requestedAdapterKeys = requestedAdapterKeys,
                currentBindingAdapterKeys = currentBindingAdapterKeys
            )
            for (attempt in 0 until FEISHU_DISCOVERY_STARTUP_RETRIES) {
                if (result.candidates.isNotEmpty() || result.snapshots.values.any(::hasFeishuSnapshotActivity)) {
                    break
                }
                delay(FEISHU_DISCOVERY_STARTUP_RETRY_DELAY_MS)
                result = collectFeishuDiscoveryResult(
                    requestedAdapterKeys = requestedAdapterKeys,
                    currentBindingAdapterKeys = currentBindingAdapterKeys
                )
            }
            val finalResult = result
            val info = if (finalResult.candidates.isEmpty()) {
                buildFeishuDiscoveryInfo(
                    requestedAdapterKeys = requestedAdapterKeys,
                    currentBindingAdapterKeys = currentBindingAdapterKeys,
                    snapshots = finalResult.snapshots
                )
            } else {
                "Feishu chats discovered. Tap one to use."
            }
            _uiState.update {
                it.copy(
                    sessionBindingFeishuDiscoveryAttempted = true,
                    sessionBindingFeishuDiscovering = false,
                    sessionBindingFeishuCandidates = finalResult.candidates,
                    sessionBindingFeishuInfo = info
                )
            }
            if (finalResult.candidates.isNotEmpty()) {
                showSettingsInfo("Feishu chats discovered. Tap one to use.")
            } else {
                showSettingsInfo(info)
            }
        }
    }

    private fun collectFeishuDiscoveryResult(
        requestedAdapterKeys: List<String>,
        currentBindingAdapterKeys: List<String>
    ): FeishuDiscoveryResult {
        val snapshotKeys = LinkedHashSet<String>().apply {
            addAll(requestedAdapterKeys)
            addAll(currentBindingAdapterKeys)
        }
        val matchedSnapshots = snapshotKeys.associateWith { FeishuGatewayDiagnostics.getSnapshot(it) }
        val snapshots = if (matchedSnapshots.values.any(::hasFeishuSnapshotActivity)) {
            matchedSnapshots
        } else {
            val activeFallbackSnapshots = FeishuGatewayDiagnostics.getSnapshots()
                .filterKeys { it !in snapshotKeys }
                .filterValues(::hasFeishuSnapshotActivity)
            if (activeFallbackSnapshots.size == 1) {
                linkedMapOf<String, com.lgclaw.channels.FeishuGatewaySnapshot>().apply {
                    putAll(matchedSnapshots)
                    putAll(activeFallbackSnapshots)
                }
            } else {
                matchedSnapshots
            }
        }
        val candidates = snapshots.values
            .asSequence()
            .flatMap { it.recentChats.asSequence() }
            .distinctBy { it.chatId }
            .map {
                UiFeishuChatCandidate(
                    chatId = it.chatId,
                    title = it.title,
                    kind = it.kind,
                    note = it.note
                )
            }
            .toList()
        return FeishuDiscoveryResult(
            snapshots = snapshots,
            candidates = candidates
        )
    }

    private fun buildFeishuDiscoveryInfo(
        requestedAdapterKeys: List<String>,
        currentBindingAdapterKeys: List<String>,
        snapshots: Map<String, com.lgclaw.channels.FeishuGatewaySnapshot>
    ): String {
        if (requestedAdapterKeys.isEmpty()) {
            return "Save App ID and App Secret first, then detect again."
        }
        val requested = requestedAdapterKeys.asSequence()
            .mapNotNull { snapshots[it] }
            .firstOrNull(::hasFeishuSnapshotActivity)
            ?: requestedAdapterKeys.firstNotNullOfOrNull { snapshots[it] }
        val current = currentBindingAdapterKeys.asSequence()
            .filterNot { it in requestedAdapterKeys }
            .mapNotNull { snapshots[it] }
            .firstOrNull(::hasFeishuSnapshotActivity)
            ?: currentBindingAdapterKeys.asSequence()
                .filterNot { it in requestedAdapterKeys }
                .firstNotNullOfOrNull { snapshots[it] }
        val fallback = snapshots.asSequence()
            .filterNot { (key, _) -> key in requestedAdapterKeys || key in currentBindingAdapterKeys }
            .map { it.value }
            .firstOrNull(::hasFeishuSnapshotActivity)
            ?: snapshots.asSequence()
                .filterNot { (key, _) -> key in requestedAdapterKeys || key in currentBindingAdapterKeys }
                .map { it.value }
                .firstOrNull()
        if (
            currentBindingAdapterKeys.any { it !in requestedAdapterKeys } &&
            !hasFeishuSnapshotActivity(requested) &&
            hasFeishuSnapshotActivity(current)
        ) {
            return "These fields do not match the running Feishu connection. Save first, then detect again."
        }
        val snapshot = listOfNotNull(requested, current, fallback)
            .firstOrNull(::hasFeishuSnapshotActivity)
            ?: requested
            ?: current
            ?: fallback
        if (snapshot == null) {
            return "Save once to start Feishu long connection, then send one message and detect again."
        }
        if (snapshot.lastError.isNotBlank()) {
            return "Feishu long connection is not ready yet."
        }
        if (!snapshot.running) {
            return "Feishu adapter is not running yet. Save once and keep LGClaw open."
        }
        if (!snapshot.ready) {
            return "Feishu long connection is starting. Finish confirmation, then detect again."
        }
        if (snapshot.inboundSeen <= 0L) {
            return "Feishu Long Connection is ready, but LGClaw has not received any inbound Feishu message yet. Open the app in Feishu and send one @mention message first. Group tests also need im:message.group_at_msg:readonly."
        }
        return "Feishu messages have reached LGClaw, but no bindable chat has been cached yet. Send one more @mention message, then try Detect Chats again."
    }

    private fun hasFeishuSnapshotActivity(snapshot: com.lgclaw.channels.FeishuGatewaySnapshot?): Boolean {
        if (snapshot == null) return false
        return snapshot.running ||
            snapshot.connected ||
            snapshot.ready ||
            snapshot.inboundSeen > 0L ||
            snapshot.inboundForwarded > 0L ||
            snapshot.outboundSent > 0L ||
            snapshot.lastError.isNotBlank() ||
            snapshot.recentChats.isNotEmpty()
    }

    fun clearFeishuChatDiscovery() = channelBindingCoordinator.clearFeishuChatDiscovery()

    private fun clearFeishuChatDiscoveryInternal() {
        _uiState.update {
            it.copy(
                sessionBindingFeishuDiscoveryAttempted = false,
                sessionBindingFeishuDiscovering = false,
                sessionBindingFeishuCandidates = emptyList(),
                sessionBindingFeishuInfo = null
            )
        }
    }

    fun discoverEmailSendersForBinding(
        consentGranted: Boolean,
        imapHost: String,
        imapPort: String,
        imapUsername: String,
        imapPassword: String,
        smtpHost: String,
        smtpPort: String,
        smtpUsername: String,
        smtpPassword: String,
        fromAddress: String,
        autoReplyEnabled: Boolean
    ) = channelBindingCoordinator.discoverEmailSendersForBinding(
        consentGranted = consentGranted,
        imapHost = imapHost,
        imapPort = imapPort,
        imapUsername = imapUsername,
        imapPassword = imapPassword,
        smtpHost = smtpHost,
        smtpPort = smtpPort,
        smtpUsername = smtpUsername,
        smtpPassword = smtpPassword,
        fromAddress = fromAddress,
        autoReplyEnabled = autoReplyEnabled
    )

    private fun discoverEmailSendersForBindingInternal(
        consentGranted: Boolean,
        imapHost: String,
        imapPort: String,
        imapUsername: String,
        imapPassword: String,
        smtpHost: String,
        smtpPort: String,
        smtpUsername: String,
        smtpPassword: String,
        fromAddress: String,
        autoReplyEnabled: Boolean
    ) {
        _uiState.update {
            it.copy(
                sessionBindingEmailDiscoveryAttempted = true,
                sessionBindingEmailDiscovering = true,
                sessionBindingEmailCandidates = emptyList(),
                sessionBindingEmailInfo = null
            )
        }
        val config = EmailAccountConfig(
            consentGranted = consentGranted,
            imapHost = imapHost.trim(),
            imapPort = imapPort.toIntOrNull()?.coerceIn(1, 65535) ?: 993,
            imapUsername = normalizeEmailAddress(imapUsername),
            imapPassword = imapPassword,
            smtpHost = smtpHost.trim(),
            smtpPort = smtpPort.toIntOrNull()?.coerceIn(1, 65535) ?: 587,
            smtpUsername = normalizeEmailAddress(smtpUsername),
            smtpPassword = smtpPassword,
            fromAddress = normalizeEmailAddress(fromAddress),
            autoReplyEnabled = autoReplyEnabled
        )
        val adapterKey = buildEmailAdapterKey(config)
        viewModelScope.launch {
            showSettingsInfo("Detecting email senders...")
            runCatching {
                val fetched = withContext(Dispatchers.IO) {
                    EmailChannelAdapter.detectRecentSenders(config)
                }
                if (fetched.isEmpty()) {
                    EmailGatewayDiagnostics.getSnapshot(adapterKey).recentSenders
                } else {
                    fetched
                }
            }.onSuccess { senderCandidates ->
                val candidates = senderCandidates.map {
                    UiEmailSenderCandidate(
                        email = it.email,
                        subject = it.subject,
                        note = it.note
                    )
                }
                _uiState.update {
                    it.copy(
                        sessionBindingEmailDiscoveryAttempted = true,
                        sessionBindingEmailDiscovering = false,
                        sessionBindingEmailCandidates = candidates,
                        sessionBindingEmailInfo = if (candidates.isEmpty()) {
                            "No email senders found yet. Make sure one message reached INBOX, then detect again."
                        } else {
                            null
                        }
                    )
                }
                if (candidates.isNotEmpty()) {
                    showSettingsInfo("Email senders discovered. Tap one to use.")
                } else {
                    showSettingsInfo("No email senders found yet. Make sure one message reached INBOX, then detect again.")
                }
            }.onFailure { t ->
                val fallback = EmailGatewayDiagnostics.getSnapshot(adapterKey).recentSenders.map {
                    UiEmailSenderCandidate(
                        email = it.email,
                        subject = it.subject,
                        note = it.note
                    )
                }
                _uiState.update {
                    it.copy(
                        sessionBindingEmailDiscoveryAttempted = true,
                        sessionBindingEmailDiscovering = false,
                        sessionBindingEmailCandidates = fallback,
                        sessionBindingEmailInfo = t.message ?: "Email sender detection failed."
                    )
                }
                showSettingsInfo(t.message ?: "Email sender detection failed.")
            }
        }
    }

    fun clearEmailSenderDiscovery() = channelBindingCoordinator.clearEmailSenderDiscovery()

    private fun clearEmailSenderDiscoveryInternal() {
        _uiState.update {
            it.copy(
                sessionBindingEmailDiscoveryAttempted = false,
                sessionBindingEmailDiscovering = false,
                sessionBindingEmailCandidates = emptyList(),
                sessionBindingEmailInfo = null
            )
        }
    }

    fun discoverWeComChatsForBinding(botId: String, secret: String) =
        channelBindingCoordinator.discoverWeComChatsForBinding(botId, secret)

    private fun discoverWeComChatsForBindingInternal(botId: String, secret: String) {
        _uiState.update {
            it.copy(
                sessionBindingWeComDiscoveryAttempted = true,
                sessionBindingWeComDiscovering = true,
                sessionBindingWeComCandidates = emptyList(),
                sessionBindingWeComInfo = null
            )
        }
        val adapterKey = buildWeComAdapterKey(botId, secret)
        if (adapterKey == null) {
            _uiState.update {
                it.copy(
                    sessionBindingWeComDiscoveryAttempted = true,
                    sessionBindingWeComDiscovering = false,
                    sessionBindingWeComCandidates = emptyList(),
                    sessionBindingWeComInfo = "Save Bot ID and Secret first, then detect again."
                )
            }
            showSettingsInfo("Save Bot ID and Secret first, then detect again.")
            return
        }
        viewModelScope.launch {
            showSettingsInfo("Detecting WeCom chats...")
            var snapshot = WeComGatewayDiagnostics.getSnapshot(adapterKey)
            for (attempt in 0 until WECOM_DISCOVERY_STARTUP_RETRIES) {
                if (snapshot.recentChats.isNotEmpty() || hasWeComSnapshotActivity(snapshot)) {
                    break
                }
                delay(WECOM_DISCOVERY_STARTUP_RETRY_DELAY_MS)
                snapshot = WeComGatewayDiagnostics.getSnapshot(adapterKey)
            }
            val candidates = snapshot.recentChats.map {
                UiWeComChatCandidate(
                    chatId = it.chatId,
                    title = it.title,
                    kind = it.kind,
                    note = it.note
                )
            }
            _uiState.update {
                it.copy(
                    sessionBindingWeComDiscoveryAttempted = true,
                    sessionBindingWeComDiscovering = false,
                    sessionBindingWeComCandidates = candidates,
                    sessionBindingWeComInfo = if (candidates.isEmpty()) {
                        buildWeComDiscoveryInfo(snapshot)
                    } else {
                        "WeCom chats discovered. Tap one to use."
                    }
                )
            }
            if (candidates.isNotEmpty()) {
                showSettingsInfo("WeCom chats discovered. Tap one to use.")
            } else {
                showSettingsInfo(buildWeComDiscoveryInfo(snapshot))
            }
        }
    }

    private fun hasWeComSnapshotActivity(snapshot: com.lgclaw.channels.WeComGatewaySnapshot?): Boolean {
        if (snapshot == null) return false
        return snapshot.running ||
            snapshot.connected ||
            snapshot.ready ||
            snapshot.inboundSeen > 0L ||
            snapshot.inboundForwarded > 0L ||
            snapshot.outboundSent > 0L ||
            snapshot.lastError.isNotBlank() ||
            snapshot.recentChats.isNotEmpty()
    }

    private fun buildWeComDiscoveryInfo(snapshot: com.lgclaw.channels.WeComGatewaySnapshot?): String {
        if (snapshot == null) {
            return "Save Bot ID and Secret first, then detect again."
        }
        if (snapshot.lastError.isNotBlank()) {
            return "WeCom connection is not ready yet."
        }
        if (!snapshot.running) {
            return "WeCom adapter is not running yet. Save once and keep LGClaw open."
        }
        if (!snapshot.ready) {
            return "WeCom connection is starting. Finish setup, then detect again."
        }
        if (snapshot.inboundSeen <= 0L) {
            return "WeCom connection is ready, but LGClaw has not received any inbound message yet. Send one message from WeCom first."
        }
        return "WeCom messages have reached LGClaw, but no bindable chat has been cached yet. Send one more message, then try Detect Chats again."
    }

    fun clearWeComChatDiscovery() = channelBindingCoordinator.clearWeComChatDiscovery()

    private fun clearWeComChatDiscoveryInternal() {
        _uiState.update {
            it.copy(
                sessionBindingWeComDiscoveryAttempted = false,
                sessionBindingWeComDiscovering = false,
                sessionBindingWeComCandidates = emptyList(),
                sessionBindingWeComInfo = null
            )
        }
    }

    fun triggerHeartbeatNow() = runtimeCoordinator.triggerHeartbeatNow()

    private fun triggerHeartbeatNowInternal() {
        viewModelScope.launch {
            runCatching { triggerHeartbeatViaActiveRuntime() }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(settingsInfo = t.message ?: t.javaClass.simpleName)
                    }
                }
        }
    }

    fun loadHeartbeatDocument() = runtimeCoordinator.loadHeartbeatDocument()

    private fun loadHeartbeatDocumentInternal() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { readHeartbeatDoc() }
            _uiState.update { it.copy(settingsHeartbeatDoc = text) }
        }
    }

    fun onSettingsHeartbeatDocChanged(value: String) =
        runtimeCoordinator.onSettingsHeartbeatDocChanged(value)

    fun saveHeartbeatDocument(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) = runtimeCoordinator.saveHeartbeatDocument(showSuccessMessage, showErrorMessage)

    private fun saveHeartbeatDocumentInternal(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) {
        viewModelScope.launch {
            val content = _uiState.value.settingsHeartbeatDoc
            runCatching {
                persistHeartbeatSettings(
                    HeartbeatSetTool.Request(documentContent = content)
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(settingsInfo = if (showSuccessMessage) "HEARTBEAT.md saved." else null)
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsInfo = if (showErrorMessage) {
                            "Save HEARTBEAT.md failed: ${t.message ?: t.javaClass.simpleName}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun refreshCronLogs() = runtimeCoordinator.refreshCronLogs()

    private fun refreshCronLogsInternal() {
        viewModelScope.launch {
            val logs = withContext(Dispatchers.IO) { cronLogStore.readRecent() }
            _uiState.update { it.copy(settingsCronLogs = logs) }
        }
    }

    fun clearCronLogs() = runtimeCoordinator.clearCronLogs()

    private fun clearCronLogsInternal() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cronLogStore.clear() }
            _uiState.update {
                it.copy(
                    settingsCronLogs = "",
                    settingsInfo = "Cron logs cleared."
                )
            }
        }
    }

    fun refreshAgentLogs() = runtimeCoordinator.refreshAgentLogs()

    private fun refreshAgentLogsInternal() {
        viewModelScope.launch {
            val logs = withContext(Dispatchers.IO) { agentLogStore.readRecent() }
            _uiState.update { it.copy(settingsAgentLogs = logs) }
        }
    }

    fun refreshSessionConnectionStatus() = channelBindingCoordinator.refreshSessionConnectionStatus()

    private fun refreshSessionConnectionStatusInternal() {
        refreshSessionBindingsInState()
    }

    fun clearAgentLogs() = runtimeCoordinator.clearAgentLogs()

    private fun clearAgentLogsInternal() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { agentLogStore.clear() }
            _uiState.update {
                it.copy(
                    settingsAgentLogs = "",
                    settingsInfo = "Agent logs cleared."
                )
            }
        }
    }

    fun saveProviderSettings(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) = providerSettingsCoordinator.saveProviderSettings(showSuccessMessage, showErrorMessage)

    private fun saveProviderSettingsInternal(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) {
        if (_uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val updatedState = buildProviderStateWithSavedDraft(_uiState.value)
                updatedState.settingsEditingProviderConfigId
                    .takeIf { it.isNotBlank() }
                    ?.let { configId ->
                        val cachePrefix = ProviderResolutionStore.cachePrefixForProviderConfig(configId)
                        AdaptiveLlmProvider.clearRememberedTargets(cachePrefix)
                        providerResolutionStore.clearByPrefix(cachePrefix)
                    }
                configStore.saveConfig(buildProviderSettingsConfig(updatedState))
                updatedState
            }.onSuccess { updatedState ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsProviderConfigs = updatedState.settingsProviderConfigs,
                        settingsEditingProviderConfigId = updatedState.settingsEditingProviderConfigId,
                        settingsProvider = updatedState.settingsProvider,
                        settingsProviderCustomName = updatedState.settingsProviderCustomName,
                        settingsProviderProtocol = updatedState.settingsProviderProtocol,
                        settingsBaseUrl = updatedState.settingsBaseUrl,
                        settingsModel = updatedState.settingsModel,
                        settingsEquippedModels = updatedState.settingsEquippedModels,
                        settingsDiscoveredModels = updatedState.settingsDiscoveredModels,
                        settingsApiKey = updatedState.settingsApiKey,
                        settingsInfo = if (showSuccessMessage) "Provider saved." else null
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showErrorMessage) {
                            "Save failed: ${t.message ?: t.javaClass.simpleName}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun saveAgentRuntimeSettings(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) = providerSettingsCoordinator.saveAgentRuntimeSettings(showSuccessMessage, showErrorMessage)

    private fun saveAgentRuntimeSettingsInternal(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) {
        if (_uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val state = _uiState.value
                persistRuntimeSettings(
                    RuntimeSetTool.Request(
                        maxToolRounds = state.settingsMaxToolRounds.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Max rounds must be a number"),
                        toolResultMaxChars = state.settingsToolResultMaxChars.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Tool result max chars must be a number"),
                        memoryConsolidationWindow = state.settingsMemoryConsolidationWindow.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Memory consolidation window must be a number"),
                        compressionThresholdK = state.settingsCompressionThresholdK.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Compression threshold K must be a number"),
                        llmCallTimeoutSeconds = state.settingsLlmCallTimeoutSeconds.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("LLM call timeout must be a number"),
                        llmConnectTimeoutSeconds = state.settingsLlmConnectTimeoutSeconds.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("LLM connect timeout must be a number"),
                        llmReadTimeoutSeconds = state.settingsLlmReadTimeoutSeconds.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("LLM read timeout must be a number"),
                        defaultToolTimeoutSeconds = state.settingsDefaultToolTimeoutSeconds.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Default tool timeout must be a number"),
                        contextMessages = state.settingsContextMessages.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Context messages must be a number"),
                        toolArgsPreviewMaxChars = state.settingsToolArgsPreviewMaxChars.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Tool args preview max chars must be a number")
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showSuccessMessage) "Runtime saved." else null
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showErrorMessage) {
                            "Save failed: ${t.message ?: t.javaClass.simpleName}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun saveCronSettings(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) = runtimeCoordinator.saveCronSettings(showSuccessMessage, showErrorMessage)

    private fun saveCronSettingsInternal(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) {
        if (_uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val state = _uiState.value
                persistCronSettings(
                    com.lgclaw.tools.CronConfigUpdate(
                        enabled = state.settingsCronEnabled,
                        minEveryMs = state.settingsCronMinEveryMs.trim().toLongOrNull()
                            ?: throw IllegalArgumentException("Cron min interval ms must be a number"),
                        maxJobs = state.settingsCronMaxJobs.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Cron max jobs must be a number")
                    )
                )
            }.onSuccess {
                refreshCronJobs()
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showSuccessMessage) "Cron saved." else null
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showErrorMessage) {
                            "Save failed: ${t.message ?: t.javaClass.simpleName}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun saveHeartbeatSettings(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) = runtimeCoordinator.saveHeartbeatSettings(showSuccessMessage, showErrorMessage)

    private fun saveHeartbeatSettingsInternal(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) {
        if (_uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val state = _uiState.value
                persistHeartbeatSettings(
                    HeartbeatSetTool.Request(
                        enabled = state.settingsHeartbeatEnabled,
                        intervalSeconds = state.settingsHeartbeatIntervalSeconds.trim().toLongOrNull()
                            ?: throw IllegalArgumentException("Heartbeat interval seconds must be a number")
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showSuccessMessage) "Heartbeat saved." else null
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showErrorMessage) {
                            "Save failed: ${t.message ?: t.javaClass.simpleName}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun onAlwaysOnEnabledChanged(value: Boolean) =
        runtimeCoordinator.onAlwaysOnEnabledChanged(value)

    fun onAlwaysOnKeepScreenAwakeChanged(value: Boolean) =
        runtimeCoordinator.onAlwaysOnKeepScreenAwakeChanged(value)

    fun saveAlwaysOnSettings(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) = runtimeCoordinator.saveAlwaysOnSettings(showSuccessMessage, showErrorMessage)

    private fun saveAlwaysOnSettingsInternal(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) {
        if (_uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val next = AlwaysOnConfig(
                    enabled = _uiState.value.alwaysOnEnabled,
                    keepScreenAwake = _uiState.value.alwaysOnKeepScreenAwake
                )
                configStore.saveAlwaysOnConfig(next)
                val app = getApplication<Application>()
                if (next.enabled) {
                    RuntimeController.stop()
                    AlwaysOnHealthCheckWorker.ensureScheduled(app)
                    AlwaysOnModeController.startService(app)
                    AlwaysOnModeController.reloadAll()
                } else {
                    AlwaysOnHealthCheckWorker.cancel(app)
                    AlwaysOnModeController.stopService(app)
                    RuntimeController.start(app)
                    RuntimeController.reloadAll(app)
                }
                refreshAlwaysOnDiagnostics()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showSuccessMessage) "Always-on mode settings saved." else null
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showErrorMessage) {
                            "Save failed: ${t.message ?: t.javaClass.simpleName}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun refreshAlwaysOnDiagnostics() = runtimeCoordinator.refreshAlwaysOnDiagnostics()

    private fun refreshAlwaysOnDiagnosticsInternal() {
        val app = getApplication<Application>()
        val status = AlwaysOnModeController.status.value
        val connectivityManager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val chargingStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            chargingStatus == BatteryManager.BATTERY_STATUS_FULL
        val ignoringOptimizations = powerManager?.let {
            runCatching { it.isIgnoringBatteryOptimizations(app.packageName) }.getOrDefault(false)
        } ?: false
        val canScheduleExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
        _uiState.update {
            it.copy(
                alwaysOnServiceRunning = status.serviceRunning,
                alwaysOnNotificationActive = status.notificationActive,
                alwaysOnGatewayRunning = status.gatewayRunning,
                alwaysOnActiveAdapterCount = status.activeAdapterCount,
                alwaysOnStartedAtMs = status.startedAtMs,
                alwaysOnLastError = status.lastError,
                alwaysOnNetworkConnected = connected,
                alwaysOnCharging = isCharging,
                alwaysOnBatteryOptimizationIgnored = ignoringOptimizations
                ,
                alwaysOnExactAlarmAllowed = canScheduleExactAlarm
            )
        }
    }

    fun saveChannelsSettings(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) = runtimeCoordinator.saveChannelsSettings(showSuccessMessage, showErrorMessage)

    private fun saveChannelsSettingsInternal(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) {
        if (_uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val bindings = configStore.getSessionChannelBindings()
                val current = configStore.getChannelsConfig()
                val shouldEnableGateway = hasActiveGatewayBinding(bindings)
                val runtimeConfig = current.copy(enabled = shouldEnableGateway)
                configStore.saveChannelsConfig(runtimeConfig)
                applyGatewayRuntimeConfig(runtimeConfig)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsGatewayEnabled = configStore.getChannelsConfig().enabled,
                        settingsInfo = if (showSuccessMessage) {
                            "Channels synced."
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showErrorMessage) {
                            "Save failed: ${t.message ?: t.javaClass.simpleName}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun saveMcpSettings(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) = runtimeCoordinator.saveMcpSettings(showSuccessMessage, showErrorMessage)

    private fun saveMcpSettingsInternal(
        showSuccessMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ) {
        if (_uiState.value.settingsSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsSaving = true, settingsInfo = null) }
            runCatching {
                val state = _uiState.value
                val normalizedMcpServers = buildNormalizedMcpServers(state)
                val duplicateMcpNames = normalizedMcpServers
                    .groupingBy { it.serverName.trim().lowercase(Locale.US) }
                    .eachCount()
                    .filterValues { it > 1 }
                if (duplicateMcpNames.isNotEmpty()) {
                    throw IllegalArgumentException("MCP server names must be unique.")
                }
                if (state.settingsMcpEnabled && normalizedMcpServers.isEmpty()) {
                    throw IllegalArgumentException("Enable MCP requires at least one configured server.")
                }
                val firstMcpServer = normalizedMcpServers.firstOrNull()
                val mcpConfig = McpHttpConfig(
                    enabled = state.settingsMcpEnabled,
                    serverName = firstMcpServer?.serverName ?: AppLimits.DEFAULT_MCP_HTTP_SERVER_NAME,
                    serverUrl = firstMcpServer?.serverUrl.orEmpty(),
                    authToken = firstMcpServer?.authToken.orEmpty(),
                    toolTimeoutSeconds = firstMcpServer?.toolTimeoutSeconds
                        ?: AppLimits.DEFAULT_MCP_HTTP_TOOL_TIMEOUT_SECONDS,
                    servers = normalizedMcpServers
                )
                configStore.saveMcpHttpConfig(mcpConfig)
                reloadMcpViaActiveRuntime(mcpConfig)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showSuccessMessage) "MCP saved." else null
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsSaving = false,
                        settingsInfo = if (showErrorMessage) {
                            "Save failed: ${t.message ?: t.javaClass.simpleName}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun testProviderSettings() = providerSettingsCoordinator.testProviderSettings()

    private fun testProviderSettingsInternal() {
        if (_uiState.value.settingsProviderTesting) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsProviderTesting = true, settingsInfo = null) }
            runCatching {
                val config = buildProviderTestConfig(_uiState.value)
                val provider = LlmProviderFactory(providerResolutionStore).create(config)
                val response = withContext(Dispatchers.IO) {
                    provider.chat(
                        messages = listOf(
                            ChatMessage(
                                role = "user",
                                content = "Reply with exactly OK."
                            )
                        ),
                        toolsSpec = emptyList()
                    )
                }
                val content = response.assistant.content.trim()
                if (content.isBlank() && response.assistant.toolCalls.isEmpty()) {
                    "Provider responded, but returned empty content."
                } else {
                    "Provider test passed."
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        settingsProviderTesting = false,
                        settingsInfo = result
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        settingsProviderTesting = false,
                        settingsInfo = "Provider test failed: ${t.message ?: t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun saveSettings() {
        saveProviderSettings()
    }


    private fun fetchModelsForProviderDraft(state: ChatUiState): List<String> {
        val provider = ProviderCatalog.resolve(state.settingsProvider).id
        if (state.settingsProviderProtocol == ProviderProtocol.Anthropic) {
            return ProviderCatalog.suggestedModels(provider)
        }
        val base = state.settingsBaseUrl.trim().ifBlank { ProviderCatalog.defaultBaseUrl(provider, state.settingsProviderProtocol) }
        val url = modelListUrl(base) ?: return ProviderCatalog.suggestedModels(provider)
        val requestBuilder = Request.Builder().url(url)
        val key = state.settingsApiKey.trim()
        if (key.isNotBlank()) requestBuilder.header("Authorization", "Bearer $key")
        ProviderCatalog.extraHeaders(provider).forEach { (k, v) -> requestBuilder.header(k, v) }
        val response = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build().newCall(requestBuilder.get().build()).execute()
        response.use { res ->
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}")
            val body = res.body?.string().orEmpty()
            val root = uiJson.parseToJsonElement(body).jsonObject
            val data = root["data"] as? JsonArray ?: return emptyList()
            return data.mapNotNull { element ->
                runCatching { element.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            }.distinct().sorted()
        }
    }

    private fun modelListUrl(baseUrl: String): String? {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return null
        val segments = parsed.pathSegments.filter { it.isNotBlank() }.toMutableList()
        while (segments.isNotEmpty() && segments.last() in setOf("chat", "completions", "messages", "responses")) {
            segments.removeAt(segments.lastIndex)
        }
        val builder = parsed.newBuilder().encodedPath("/")
        segments.forEach { builder.addPathSegment(it) }
        builder.addPathSegment("models")
        return builder.build().toString()
    }
    private fun buildProviderTestConfig(state: ChatUiState) : com.lgclaw.config.AppConfig {
        val provider = ProviderCatalog.resolve(state.settingsProvider).id
        val protocol = ProviderCatalog.resolveProtocol(provider, state.settingsProviderProtocol, state.settingsBaseUrl)
        val model = state.settingsModel.trim().ifBlank { ProviderCatalog.defaultModel(provider, protocol) }
        val apiKey = state.settingsApiKey.trim()
        val baseUrl = state.settingsBaseUrl.trim()
        if (baseUrl.isBlank()) {
            throw IllegalArgumentException("Endpoint URL is required")
        }
        val parsedBaseUrl = baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Endpoint URL is invalid")
        val scheme = parsedBaseUrl.scheme.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Endpoint URL must start with http:// or https://")
        }
        val current = configStore.getConfig()
        return current.copy(
            providerName = provider,
            providerProtocol = protocol,
            apiKey = apiKey,
            model = model,
            baseUrl = baseUrl,
            activeProviderConfigId = state.settingsEditingProviderConfigId.trim()
        )
    }

    private fun buildProviderStateWithSavedDraft(state: ChatUiState): ChatUiState {
        val savedConfig = buildValidatedProviderDraft(state)
        val currentConfigs = state.settingsProviderConfigs
        val existing = currentConfigs.firstOrNull { it.id == savedConfig.id }
        val shouldEnable = existing?.enabled ?: true
        val updatedConfigs = normalizeActiveProviderConfigs(
            currentConfigs.filterNot { it.id == savedConfig.id } + savedConfig.copy(enabled = shouldEnable)
        )
        val selected = updatedConfigs.firstOrNull { it.id == savedConfig.id } ?: updatedConfigs.firstOrNull()
        return state.copy(
            settingsProviderConfigs = updatedConfigs,
            settingsEditingProviderConfigId = selected?.id.orEmpty(),
            settingsProvider = selected?.providerName ?: state.settingsProvider,
            settingsProviderCustomName = selected?.customName ?: state.settingsProviderCustomName,
            settingsProviderProtocol = selected?.providerProtocol ?: state.settingsProviderProtocol,
            settingsBaseUrl = selected?.let { config ->
                config.baseUrl.ifBlank {
                    ProviderCatalog.defaultBaseUrl(config.providerName, config.providerProtocol)
                }
            } ?: state.settingsBaseUrl,
            settingsModel = selected?.model ?: state.settingsModel,
            settingsApiKey = selected?.apiKey ?: state.settingsApiKey
        )
    }

    private fun buildValidatedProviderDraft(state: ChatUiState): UiProviderConfig {
        val provider = ProviderCatalog.resolve(state.settingsProvider).id
        val baseUrl = state.settingsBaseUrl.trim()
        val protocol = ProviderCatalog.resolveProtocol(provider, state.settingsProviderProtocol, baseUrl)
        val model = state.settingsModel.trim().ifBlank { ProviderCatalog.defaultModel(provider, protocol) }
        val apiKey = state.settingsApiKey.trim()
        if (baseUrl.isBlank()) {
            throw IllegalArgumentException("Endpoint URL is required")
        }
        val parsedBaseUrl = baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Endpoint URL is invalid")
        val scheme = parsedBaseUrl.scheme.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Endpoint URL must start with http:// or https://")
        }
        val id = state.settingsEditingProviderConfigId.trim()
            .ifBlank { "provider_${System.currentTimeMillis()}_${state.settingsProviderConfigs.size + 1}" }
        val enabled = state.settingsProviderConfigs.firstOrNull { it.id == id }?.enabled
            ?: state.settingsProviderConfigs.isEmpty()
        return UiProviderConfig(
            id = id,
            providerName = provider,
            customName = if (provider == "custom") state.settingsProviderCustomName.trim() else "",
            providerProtocol = protocol,
            apiKey = apiKey,
            model = model,
            equippedModels = (_uiState.value.settingsEquippedModels + model).map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            baseUrl = baseUrl,
            enabled = enabled
        )
    }

    private fun buildProviderSettingsConfig(state: ChatUiState): com.lgclaw.config.AppConfig {
        val normalizedConfigs = normalizeActiveProviderConfigs(state.settingsProviderConfigs)
        val activeConfig = normalizedConfigs.firstOrNull { it.enabled } ?: normalizedConfigs.firstOrNull()
        val current = configStore.getConfig()
        return current.copy(
            providerName = activeConfig?.providerName ?: ProviderCatalog.resolve(state.settingsProvider).id,
            providerProtocol = activeConfig?.providerProtocol ?: state.settingsProviderProtocol,
            apiKey = activeConfig?.apiKey ?: state.settingsApiKey.trim(),
            model = activeConfig?.model ?: state.settingsModel.trim().ifBlank {
                ProviderCatalog.defaultModel(state.settingsProvider, state.settingsProviderProtocol)
            },
            baseUrl = activeConfig?.baseUrl ?: state.settingsBaseUrl.trim(),
            providerConfigs = normalizedConfigs.map { config ->
                ProviderConnectionConfig(
                    id = config.id,
                    providerName = config.providerName,
                    customName = config.customName,
                    providerProtocol = config.providerProtocol,
                    apiKey = config.apiKey,
                    model = config.model,
                    equippedModels = (config.equippedModels + config.model).map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                    baseUrl = config.baseUrl
                )
            },
            activeProviderConfigId = activeConfig?.id.orEmpty()
        )
    }

    private fun normalizeActiveProviderConfigs(configs: List<UiProviderConfig>): List<UiProviderConfig> {
        if (configs.isEmpty()) return emptyList()
        val activeId = configs.firstOrNull { it.enabled }?.id ?: configs.first().id
        return configs.map { it.copy(enabled = it.id == activeId) }
    }

    private fun maybeTriggerFirstRunAutoIntro() {
        val state = _uiState.value
        val activeSessionId = currentSessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
        if (!state.onboardingCompleted) return
        if (activeSessionId != AppSession.LOCAL_SESSION_ID) return
        if (configStore.hasCompletedFirstRunAutoIntro()) return
        if (firstRunAutoIntroPending || generatingJob != null || state.isGenerating) return

        val text = if (state.settingsUseChinese) {
            "请先简单介绍一下你自己，你现在能帮我做什么？"
        } else {
            "Please briefly introduce yourself. What can you help me with right now?"
        }

        firstRunAutoIntroPending = true
        _uiState.update { it.copy(isGenerating = true) }
        generatingJob = viewModelScope.launch {
            try {
                runUserMessageViaActiveRuntime(
                    sessionId = AppSession.LOCAL_SESSION_ID,
                    sessionTitle = AppSession.LOCAL_SESSION_TITLE,
                    text = text
                )
                configStore.markFirstRunAutoIntroCompleted()
            } catch (t: CancellationException) {
                throw t
            } finally {
                firstRunAutoIntroPending = false
                generatingJob = null
                syncGeneratingState()
                loadSettingsIntoState()
            }
        }
    }

    override fun onCleared() {
        sessionCoordinator.clear()
        generatingJob?.cancel()
        generatingJob = null
        super.onCleared()
    }

    private fun bootstrapLocalSessions() {
        viewModelScope.launch {
            runCatching {
                sessionRepository.ensureSessionExists(AppSession.LOCAL_SESSION_ID, AppSession.LOCAL_SESSION_TITLE)
                sessionRepository.touch(AppSession.LOCAL_SESSION_ID)
            }.onFailure { t ->
                Log.e(TAG, "Failed to bootstrap local session", t)
            }
        }
    }

    private fun mapMessagesToUi(messages: List<MessageEntity>): List<UiMessage> {
        val mapped = mutableListOf<UiMessage>()
        var index = 0
        while (index < messages.size) {
            val message = messages[index]
            if (message.role == "assistant" && !message.toolCallJson.isNullOrBlank()) {
                val toolCalls = parseToolCalls(message.toolCallJson.orEmpty())
                val assistantNote = message.content.trim()
                    .takeIf { it.isNotBlank() && !it.equals("[tool call]", ignoreCase = true) }
                var scan = index + 1
                val contiguousToolMessages = mutableListOf<MessageEntity>()
                while (scan < messages.size && messages[scan].role == "tool") {
                    contiguousToolMessages += messages[scan]
                    scan += 1
                }

                if (toolCalls.isNotEmpty()) {
                    val pending = contiguousToolMessages.map { entity ->
                        ToolResultEnvelope(
                            entity = entity,
                            parsed = parseToolResult(entity.toolResultJson)
                        )
                    }.toMutableList()

                    toolCalls.forEachIndexed { callIndex, call ->
                        val exactMatchIndex = pending.indexOfFirst {
                            it.parsed?.toolCallId?.trim().orEmpty() == call.id.trim()
                        }
                        val matched = when {
                            exactMatchIndex >= 0 -> pending.removeAt(exactMatchIndex)
                            pending.isNotEmpty() -> pending.removeAt(0)
                            else -> null
                        }
                        mapped += buildCombinedToolUiMessage(
                            baseMessage = message,
                            callIndex = callIndex,
                            call = call,
                            matchedResult = matched,
                            assistantNote = assistantNote
                        )
                    }

                    pending.forEachIndexed { orphanIndex, orphan ->
                        mapped += orphan.entity.toUiModel(
                            forcedId = syntheticToolMessageId(message.id, 900 + orphanIndex)
                        )
                    }
                } else {
                    mapped += message.toUiModel()
                    contiguousToolMessages.forEachIndexed { orphanIndex, orphan ->
                        mapped += orphan.toUiModel(
                            forcedId = syntheticToolMessageId(message.id, 950 + orphanIndex)
                        )
                    }
                }
                index = scan
                continue
            }

            if (message.role == "tool") {
                mapped += message.toUiModel()
                index += 1
                continue
            }

            mapped += message.toUiModel()
            index += 1
        }
        return mapped
    }

    private fun buildSessionSummaries(raw: List<SessionEntity>): List<UiSessionSummary> {
        val bindingsBySession = configStore.getSessionChannelBindings()
            .associateBy { it.sessionId.trim() }
        return UiSessionSummaryProjector.build(
            rawSessions = raw,
            bindingsBySession = bindingsBySession
        )
    }

    private fun refreshSessionBindingsInState() {
        _uiState.update { state ->
            val bindingsBySession = configStore.getSessionChannelBindings()
                .associateBy { it.sessionId.trim() }
            val sessions = UiSessionSummaryProjector.applyBindings(
                sessions = state.sessions,
                bindingsBySession = bindingsBySession
            )
            state.copy(
                sessions = sessions,
                settingsConnectedChannels = buildConnectedChannelsOverview(sessions)
            )
        }
    }

    private fun MessageEntity.shouldDisplayInChat(): Boolean {
        val text = content.trim()
        return when (role) {
            "user" -> text.isNotBlank()
            "assistant" -> {
                if (text.startsWith("[debug tool call]", ignoreCase = true)) {
                    return false
                }
                if (text == "[tool call]" && toolCallJson.isNullOrBlank()) {
                    return false
                }
                text.isNotBlank() || !toolCallJson.isNullOrBlank()
            }

            "tool" -> text.isNotBlank() || !toolResultJson.isNullOrBlank()
            else -> false
        }
    }

    private fun MessageEntity.toUiModel(forcedId: Long? = null): UiMessage {
        if (role == "assistant" && !toolCallJson.isNullOrBlank()) {
            val details = formatToolCallContent(
                toolCallJson = toolCallJson.orEmpty(),
                assistantContent = content
            )
            return UiMessage(
                id = forcedId ?: id,
                role = "tool",
                content = formatToolCallSummary(
                    toolCallJson = toolCallJson.orEmpty()
                ),
                createdAt = createdAt,
                isCollapsible = true,
                expandedContent = details,
                attachments = emptyList()
            )
        }
        if (role == "tool") {
            val attachments = extractToolResultAttachments(
                toolResultJson = toolResultJson,
                fallbackContent = content
            )
            val details = formatToolResultContent(
                toolResultJson = toolResultJson,
                fallbackContent = content
            )
            return UiMessage(
                id = forcedId ?: id,
                role = "tool",
                content = formatToolResultSummary(
                    toolResultJson = toolResultJson,
                    fallbackContent = content
                ),
                createdAt = createdAt,
                isCollapsible = true,
                expandedContent = details,
                attachments = attachments
            )
        }
        return UiMessage(
            id = forcedId ?: id,
            role = role,
            content = displayContentWithoutAttachmentContext(content).ifBlank { "[empty]" },
            createdAt = createdAt,
            attachments = extractUserInputAttachments(content)
        )
    }

    private fun displayContentWithoutAttachmentContext(raw: String): String {
        return raw
            .substringBefore("【用户已上传附件，必须读取并结合附件回答】")
            .replace(Regex("""\[(?:附件:[^\]]+|LGCLAW_ATTACHMENT:[^\]]+)]"""), "")
            .trim()
    }

    private fun extractUserInputAttachments(raw: String): List<UiMediaAttachment> {
        return Regex("""\[(?:附件:(图片|文件)|LGCLAW_ATTACHMENT:(image|document))\|([^\]]+)]""").findAll(raw).mapNotNull { match ->
            val fields = match.groupValues[3]
                .split('|')
                .mapNotNull { segment ->
                    val key = segment.substringBefore('=', "").trim()
                    val value = segment.substringAfter('=', "").trim()
                    if (key.isBlank() || value.isBlank()) null else key to value
                }.toMap()
            val path = fields["path"].orEmpty()
            if (path.isBlank()) return@mapNotNull null
            val mime = fields["mime"].orEmpty()
            val id = fields["id"].orEmpty()
            val rawKind = match.groupValues[1].ifBlank { match.groupValues[2] }
            val kind = if (rawKind == "图片" || rawKind == "image") UiMediaKind.Image else UiMediaKind.Document
            UiMediaAttachment(
                reference = path,
                kind = kind,
                label = fields["name"].orEmpty().ifBlank { File(path).name },
                mimeType = mime,
                fileId = id
            )
        }.take(MAX_MEDIA_ATTACHMENTS_PER_MESSAGE).toList()
    }

    private fun buildCombinedToolUiMessage(
        baseMessage: MessageEntity,
        callIndex: Int,
        call: ToolCall,
        matchedResult: ToolResultEnvelope?,
        assistantNote: String?
    ): UiMessage {
        val resultEntity = matchedResult?.entity
        val parsedResult = matchedResult?.parsed
        val status = when {
            resultEntity == null -> "pending"
            parsedResult?.isError == true -> "error"
            else -> "ok"
        }
        val details = formatSingleToolTraceContent(
            call = call,
            matchedEntity = resultEntity,
            assistantNote = assistantNote
        )
        return UiMessage(
            id = syntheticToolMessageId(baseMessage.id, callIndex),
            role = "tool",
            content = "${call.name} [$status]",
            createdAt = baseMessage.createdAt,
            isCollapsible = true,
            expandedContent = details,
            attachments = if (resultEntity != null) {
                extractToolResultAttachments(
                    toolResultJson = resultEntity.toolResultJson,
                    fallbackContent = resultEntity.content
                )
            } else {
                emptyList()
            }
        )
    }

    private fun formatSingleToolTraceContent(
        call: ToolCall,
        matchedEntity: MessageEntity?,
        assistantNote: String?
    ): String {
        val previewMaxChars = runtimeToolArgsPreviewMaxChars()
        val argsPretty = prettyJsonOrRaw(call.argumentsJson)
        return buildString {
            appendLine("Tool Call")
            appendLine("name=${call.name}")
            appendLine("call_id=${call.id}")
            appendLine("arguments:")
            appendLine("```json")
            appendLine(argsPretty.take(previewMaxChars))
            if (argsPretty.length > previewMaxChars) {
                appendLine("...(truncated)")
            }
            appendLine("```")
            appendLine()
            if (matchedEntity != null) {
                append(formatToolResultContent(matchedEntity.toolResultJson, matchedEntity.content))
            } else {
                appendLine("Tool Result")
                appendLine("status=pending")
                appendLine()
                append("(waiting for tool result)")
            }
            if (!assistantNote.isNullOrBlank()) {
                appendLine()
                appendLine()
                appendLine("assistant_note:")
                append(assistantNote)
            }
        }.trimEnd()
    }

    private fun syntheticToolMessageId(baseId: Long, offset: Int): Long {
        return baseId * 1000L + offset.toLong() + 1L
    }

    private fun formatToolCallSummary(toolCallJson: String): String {
        val calls = parseToolCalls(toolCallJson)
        if (calls.isEmpty()) {
            return "call"
        }
        val names = calls.map { it.name.trim() }.filter { it.isNotBlank() }
        if (names.isEmpty()) return "calls (${calls.size})"
        return if (names.size == 1) {
            names.first()
        } else {
            val preview = names.take(3).joinToString(", ")
            val remain = names.size - 3
            if (remain > 0) {
                "calls (${names.size}): $preview, +$remain more"
            } else {
                "calls (${names.size}): $preview"
            }
        }
    }

    private fun formatToolResultSummary(toolResultJson: String?, fallbackContent: String): String {
        val parsed = parseToolResult(toolResultJson)
        val status = if (parsed?.isError == true) "error" else "ok"
        val toolName = (parsed?.metadata?.get("mcp_tool") as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        val rawLead = parsed?.content?.lineSequence()?.firstOrNull()?.trim()
            .orEmpty()
            .ifBlank { fallbackContent.lineSequence().firstOrNull()?.trim().orEmpty() }
            .ifBlank { "(no output)" }
        val lead = rawLead.take(90)
        return buildString {
            append(toolName ?: "result")
            append(" [")
            append(status)
            append("] ")
            append(lead)
            if (rawLead.length > 90) append("...")
        }
    }

    private fun formatToolCallContent(toolCallJson: String, assistantContent: String): String {
        val previewMaxChars = runtimeToolArgsPreviewMaxChars()
        val calls = parseToolCalls(toolCallJson)
        if (calls.isEmpty()) {
            return buildString {
                appendLine("Tool Call")
                appendLine()
                val fallback = assistantContent.trim()
                if (fallback.isNotBlank() && !fallback.equals("[tool call]", ignoreCase = true)) {
                    append(fallback)
                } else {
                    append(toolCallJson)
                }
            }.trimEnd()
        }

        return buildString {
            appendLine(if (calls.size == 1) "Tool Call" else "Tool Calls (${calls.size})")
            calls.forEachIndexed { index, call ->
                appendLine()
                appendLine("${index + 1}. name=${call.name}")
                appendLine("call_id=${call.id}")
                appendLine("arguments:")
                appendLine("```json")
                appendLine(
                    prettyJsonOrRaw(call.argumentsJson)
                        .take(previewMaxChars)
                )
                if (call.argumentsJson.length > previewMaxChars) {
                    appendLine("...(truncated)")
                }
                appendLine("```")
            }
            val note = assistantContent.trim()
            if (note.isNotBlank() && !note.equals("[tool call]", ignoreCase = true)) {
                appendLine()
                appendLine("assistant_note:")
                append(note)
            }
        }.trimEnd()
    }

    private fun formatToolResultContent(toolResultJson: String?, fallbackContent: String): String {
        val parsed = parseToolResult(toolResultJson)
        val body = parsed?.content?.trim().orEmpty()
            .ifBlank { fallbackContent.trim() }
            .ifBlank { "(empty)" }
        return buildString {
            appendLine("Tool Result")
            parsed?.toolCallId?.takeIf { it.isNotBlank() }?.let { appendLine("call_id=$it") }
            parsed?.let {
                appendLine("status=${if (it.isError) "error" else "ok"}")
                val errorCode = (it.metadata?.get("error") as? JsonPrimitive)?.contentOrNull
                if (!errorCode.isNullOrBlank()) {
                    appendLine("error=$errorCode")
                }
                val timeoutMs = (it.metadata?.get("timeout_ms") as? JsonPrimitive)?.contentOrNull
                if (!timeoutMs.isNullOrBlank()) {
                    appendLine("timeout_ms=$timeoutMs")
                }
            }
            appendLine()
            append(body)
        }.trimEnd()
    }

    private fun extractToolResultAttachments(
        toolResultJson: String?,
        fallbackContent: String
    ): List<UiMediaAttachment> {
        val parsed = parseToolResult(toolResultJson)
        if (parsed?.isError == true) return emptyList()

        val action = metadataString(parsed?.metadata, "action")?.lowercase(Locale.US).orEmpty()
        val mode = metadataString(parsed?.metadata, "mode")?.lowercase(Locale.US).orEmpty()
        if (action == "audio_record" && mode == "start") {
            return emptyList()
        }
        val kindHint = metadataString(parsed?.metadata, "kind")?.lowercase(Locale.US).orEmpty()
        val candidates = LinkedHashSet<String>()
        val keys = listOf("output_uri", "uri", "url", "path")
        keys.forEach { key ->
            metadataString(parsed?.metadata, key)?.let { candidates += it }
        }

        val contentPool = buildString {
            append(parsed?.content.orEmpty())
            if (isNotBlank()) append('\n')
            append(fallbackContent)
        }
        extractMediaRefsFromText(contentPool).forEach { candidates += it }

        return candidates
            .mapNotNull { ref ->
                val normalized = normalizeMediaRef(ref) ?: return@mapNotNull null
                val kind = guessMediaKind(normalized, action, kindHint) ?: return@mapNotNull null
                UiMediaAttachment(
                    reference = normalized,
                    kind = kind,
                    label = deriveAttachmentLabel(normalized, kind)
                )
            }
            .take(MAX_MEDIA_ATTACHMENTS_PER_MESSAGE)
    }

    private fun metadataString(metadata: JsonObject?, key: String): String? {
        return (metadata?.get(key) as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractMediaRefsFromText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val refs = LinkedHashSet<String>()
        val uriPattern = Regex("""(?i)\b(?:content|file|https?)://[^\s)]+""")
        uriPattern.findAll(text).forEach { refs += it.value }
        val kvPattern = Regex("""(?i)\b(?:output_uri|uri|path)=([^\s]+)""")
        kvPattern.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.let { refs += it }
        }
        return refs.toList()
    }

    private fun normalizeMediaRef(raw: String): String? {
        val trimmed = raw.trim()
            .trim('"', '\'')
            .trimEnd(',', ';', ')', ']', '}')
        return trimmed.takeIf { it.isNotBlank() }
    }

    private fun guessMediaKind(reference: String, action: String, kindHint: String): UiMediaKind? {
        return when {
            action == "capture_photo" -> UiMediaKind.Image
            action == "record_video" -> UiMediaKind.Video
            action == "audio_record" || action == "audio_playback" -> UiMediaKind.Audio
            action == "list_recent" && kindHint == "images" -> UiMediaKind.Image
            action == "list_recent" && kindHint == "videos" -> UiMediaKind.Video
            action == "list_recent" && kindHint == "audio" -> UiMediaKind.Audio
            looksLikeImage(reference) -> UiMediaKind.Image
            looksLikeVideo(reference) -> UiMediaKind.Video
            looksLikeAudio(reference) -> UiMediaKind.Audio
            else -> null
        }
    }

    private fun looksLikeImage(reference: String): Boolean {
        val lower = reference.lowercase(Locale.US)
        if (lower.contains("/images/")) return true
        return listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic").any { lower.contains(it) }
    }

    private fun looksLikeVideo(reference: String): Boolean {
        val lower = reference.lowercase(Locale.US)
        if (lower.contains("/video/")) return true
        return listOf(".mp4", ".mkv", ".webm", ".mov", ".3gp").any { lower.contains(it) }
    }

    private fun looksLikeAudio(reference: String): Boolean {
        val lower = reference.lowercase(Locale.US)
        if (lower.contains("/audio/")) return true
        return listOf(".m4a", ".aac", ".mp3", ".wav", ".ogg", ".flac").any { lower.contains(it) }
    }

    private fun deriveAttachmentLabel(reference: String, kind: UiMediaKind): String {
        val name = runCatching {
            if (reference.startsWith("http://", true) ||
                reference.startsWith("https://", true) ||
                reference.startsWith("content://", true) ||
                reference.startsWith("file://", true)
            ) {
                reference.substringAfterLast('/').substringBefore('?')
            } else {
                File(reference).name
            }
        }.getOrDefault("")
        val fallback = when (kind) {
            UiMediaKind.Image -> "图片"
            UiMediaKind.Video -> "视频"
            UiMediaKind.Audio -> "音频"
            UiMediaKind.Document -> "文件"
        }
        return name.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun parseToolCalls(raw: String): List<ToolCall> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            uiJson.decodeFromString<List<ToolCall>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun parseToolResult(raw: String?): UiStoredToolResult? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            uiJson.decodeFromString<UiStoredToolResult>(raw)
        }.getOrNull()
    }

    private fun prettyJsonOrRaw(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "{}"
        return runCatching {
            val parsed = uiJson.parseToJsonElement(trimmed)
            uiJson.encodeToString(parsed)
        }.getOrDefault(trimmed)
    }

    @kotlinx.serialization.Serializable
    private data class UiStoredToolResult(
        val toolCallId: String,
        val content: String,
        val isError: Boolean,
        val metadata: JsonObject? = null
    )

    private data class ToolResultEnvelope(
        val entity: MessageEntity,
        val parsed: UiStoredToolResult?
    )

    private fun startGatewayIfEnabled() {
        val app = getApplication<Application>()
        if (shouldDelegateRemoteGatewayToAlwaysOnService()) {
            AlwaysOnHealthCheckWorker.ensureScheduled(app)
            RuntimeController.stop()
            AlwaysOnModeController.startService(app)
            AlwaysOnModeController.reloadAll()
            return
        }
        AlwaysOnHealthCheckWorker.cancel(app)
        AlwaysOnModeController.stopService(app)
        RuntimeController.start(app)
    }

    private suspend fun deliverMessageToSessionFromTool(
        request: SessionsSendTool.Request
    ): SessionsSendTool.DeliveryResult {
        val target = resolveSessionForToolTarget(
            sessionId = request.sessionId,
            sessionTitle = request.sessionTitle
        ) ?: throw IllegalArgumentException("target session not found")

        messageRepository.appendAssistantMessage(
            sessionId = target.id,
            content = request.content
        )
        sessionRepository.touch(target.id)

        var remoteDelivered = false
        val rawBinding = if (request.deliverRemote) {
            configStore.getSessionChannelBindings()
                .firstOrNull { it.sessionId.trim() == target.id.trim() && it.enabled }
        } else {
            null
        }
        val binding = if (request.deliverRemote) findSessionChannelBinding(target.id) else null
        if (request.deliverRemote && rawBinding != null && binding == null) {
            throw IllegalStateException("target session remote channel is configured but inactive or incomplete")
        }
        if (binding != null) {
            publishGatewayOutbound(
                OutboundMessage(
                    channel = binding.channel,
                    chatId = binding.chatId,
                    content = request.content,
                    metadata = buildAdapterMetadata(adapterKeyForBinding(binding))
                )
            )
            remoteDelivered = true
        }
        val deliveryNote = when {
            request.deliverRemote && rawBinding?.channel?.trim()?.equals("wecom", ignoreCase = true) == true ->
                "WeCom remote delivery is reply-context based. It only works after that WeCom chat has sent a recent inbound message; local context is kept until app restart and up to 7 days."
            else -> null
        }

        return SessionsSendTool.DeliveryResult(
            sessionId = target.id,
            sessionTitle = target.title,
            remoteDelivered = remoteDelivered,
            note = deliveryNote
        )
    }

    private fun buildRuntimeSettingsSnapshot(config: com.lgclaw.config.AppConfig): RuntimeGetTool.Snapshot {
        return RuntimeGetTool.Snapshot(
            maxToolRounds = config.maxToolRounds,
            toolResultMaxChars = config.toolResultMaxChars,
            memoryConsolidationWindow = config.memoryConsolidationWindow,
            compressionThresholdK = config.compressionThresholdK,
            llmCallTimeoutSeconds = config.llmCallTimeoutSeconds,
            llmConnectTimeoutSeconds = config.llmConnectTimeoutSeconds,
            llmReadTimeoutSeconds = config.llmReadTimeoutSeconds,
            defaultToolTimeoutSeconds = config.defaultToolTimeoutSeconds,
            contextMessages = config.contextMessages,
            toolArgsPreviewMaxChars = config.toolArgsPreviewMaxChars
        )
    }

    private suspend fun buildHeartbeatSettingsSnapshot(config: HeartbeatConfig): HeartbeatGetTool.Snapshot {
        return HeartbeatGetTool.Snapshot(
            enabled = config.enabled,
            intervalSeconds = config.intervalSeconds,
            documentContent = withContext(Dispatchers.IO) { readHeartbeatDoc() },
            lastTriggeredAtMs = configStore.getHeartbeatLastTriggeredAtMs(),
            nextTriggerAtMs = configStore.getHeartbeatNextTriggerAtMs()
        )
    }

    private suspend fun persistHeartbeatSettings(
        request: HeartbeatSetTool.Request
    ): HeartbeatGetTool.Snapshot {
        val current = configStore.getHeartbeatConfig()
        val intervalSeconds = request.intervalSeconds
            ?.also {
                if (it !in AppLimits.MIN_HEARTBEAT_INTERVAL_SECONDS..AppLimits.MAX_HEARTBEAT_INTERVAL_SECONDS) {
                    throw IllegalArgumentException(
                        "Heartbeat interval seconds must be between ${AppLimits.MIN_HEARTBEAT_INTERVAL_SECONDS} and ${AppLimits.MAX_HEARTBEAT_INTERVAL_SECONDS}"
                    )
                }
            }
            ?: current.intervalSeconds
        val updated = HeartbeatConfig(
            enabled = request.enabled ?: current.enabled,
            intervalSeconds = intervalSeconds
        )
        configStore.saveHeartbeatConfig(updated)
        request.documentContent?.let { content ->
            withContext(Dispatchers.IO) {
                heartbeatDocFile.parentFile?.mkdirs()
                heartbeatDocFile.writeText(content, Charsets.UTF_8)
            }
        }
        reloadAutomationViaActiveRuntime()
        request.nextTriggerAtMs?.let { requested ->
            if (!updated.enabled) {
                throw IllegalStateException("Cannot set next heartbeat trigger while heartbeat is disabled")
            }
            HeartbeatService(getApplication<Application>()).apply {
                updateConfig(enabled = true, intervalSeconds = updated.intervalSeconds)
                armNextAlarm(requested)
            }
        }
        loadSettingsIntoState()
        return buildHeartbeatSettingsSnapshot(updated)
    }

    private suspend fun triggerHeartbeatNowFromTool(): String {
        return triggerHeartbeatViaActiveRuntime()
    }

    private suspend fun persistRuntimeSettings(
        request: RuntimeSetTool.Request
    ): RuntimeGetTool.Snapshot {
        val current = configStore.getConfig()
        val updated = current.copy(
            maxToolRounds = request.maxToolRounds
                ?.let { validateIntSetting("Max tool rounds", it, AppLimits.MIN_MAX_TOOL_ROUNDS, AppLimits.MAX_MAX_TOOL_ROUNDS) }
                ?: current.maxToolRounds,
            toolResultMaxChars = request.toolResultMaxChars
                ?.let { validateIntSetting("Tool result max chars", it, AppLimits.MIN_TOOL_RESULT_MAX_CHARS, AppLimits.MAX_TOOL_RESULT_MAX_CHARS) }
                ?: current.toolResultMaxChars,
            memoryConsolidationWindow = request.memoryConsolidationWindow
                ?.let {
                    validateIntSetting(
                        "Memory consolidation window",
                        it,
                        AppLimits.MIN_MEMORY_CONSOLIDATION_WINDOW,
                        AppLimits.MAX_MEMORY_CONSOLIDATION_WINDOW
                    )
                }
                ?: current.memoryConsolidationWindow,
            compressionThresholdK = request.compressionThresholdK
                ?.let { validateIntSetting("Compression threshold K", it, AppLimits.MIN_COMPRESSION_THRESHOLD_K, AppLimits.MAX_COMPRESSION_THRESHOLD_K) }
                ?: current.compressionThresholdK,
            llmCallTimeoutSeconds = request.llmCallTimeoutSeconds
                ?.let {
                    validateIntSetting(
                        "LLM call timeout seconds",
                        it,
                        AppLimits.MIN_LLM_CALL_TIMEOUT_SECONDS,
                        AppLimits.MAX_LLM_CALL_TIMEOUT_SECONDS
                    )
                }
                ?: current.llmCallTimeoutSeconds,
            llmConnectTimeoutSeconds = request.llmConnectTimeoutSeconds
                ?.let {
                    validateIntSetting(
                        "LLM connect timeout seconds",
                        it,
                        AppLimits.MIN_LLM_CONNECT_TIMEOUT_SECONDS,
                        AppLimits.MAX_LLM_CONNECT_TIMEOUT_SECONDS
                    )
                }
                ?: current.llmConnectTimeoutSeconds,
            llmReadTimeoutSeconds = request.llmReadTimeoutSeconds
                ?.let {
                    validateIntSetting(
                        "LLM read timeout seconds",
                        it,
                        AppLimits.MIN_LLM_READ_TIMEOUT_SECONDS,
                        AppLimits.MAX_LLM_READ_TIMEOUT_SECONDS
                    )
                }
                ?: current.llmReadTimeoutSeconds,
            defaultToolTimeoutSeconds = request.defaultToolTimeoutSeconds
                ?.let {
                    validateIntSetting(
                        "Default tool timeout seconds",
                        it,
                        AppLimits.MIN_TOOL_TIMEOUT_SECONDS,
                        AppLimits.MAX_TOOL_TIMEOUT_SECONDS
                    )
                }
                ?: current.defaultToolTimeoutSeconds,
            contextMessages = request.contextMessages
                ?.let { validateIntSetting("Context messages", it, AppLimits.MIN_CONTEXT_MESSAGES, AppLimits.MAX_CONTEXT_MESSAGES) }
                ?: current.contextMessages,
            toolArgsPreviewMaxChars = request.toolArgsPreviewMaxChars
                ?.let {
                    validateIntSetting(
                        "Tool args preview max chars",
                        it,
                        AppLimits.MIN_TOOL_ARGS_PREVIEW_MAX_CHARS,
                        AppLimits.MAX_TOOL_ARGS_PREVIEW_MAX_CHARS
                    )
                }
                ?: current.toolArgsPreviewMaxChars
        )
        configStore.saveConfig(updated)
        loadSettingsIntoState()
        return buildRuntimeSettingsSnapshot(updated)
    }

    private fun validateIntSetting(label: String, value: Int, min: Int, max: Int): Int {
        if (value !in min..max) {
            throw IllegalArgumentException("$label must be between $min and $max")
        }
        return value
    }

    private fun shouldDelegateRemoteGatewayToAlwaysOnService(): Boolean {
        return configStore.getAlwaysOnConfig().enabled
    }

    private suspend fun publishGatewayOutbound(outbound: OutboundMessage) {
        if (shouldDelegateRemoteGatewayToAlwaysOnService()) {
            AlwaysOnModeController.publishOutbound(outbound)
            return
        }
        RuntimeController.publishOutbound(getApplication<Application>(), outbound)
    }

    private suspend fun runUserMessageViaActiveRuntime(
        sessionId: String,
        sessionTitle: String,
        text: String
    ) {
        if (shouldDelegateRemoteGatewayToAlwaysOnService()) {
            AlwaysOnModeController.runUserMessage(
                sessionId = sessionId,
                sessionTitle = sessionTitle,
                text = text
            )
            val freshMessages = messageRepository.getMessages(sessionId)
            refreshConversationMetrics(freshMessages)
            return
        }
        RuntimeController.runUserMessage(
            context = getApplication<Application>(),
            sessionId = sessionId,
            sessionTitle = sessionTitle,
            text = text
        )
        val freshMessages = messageRepository.getMessages(sessionId)
        refreshConversationMetrics(freshMessages)
    }

    private suspend fun triggerHeartbeatViaActiveRuntime(): String {
        if (shouldDelegateRemoteGatewayToAlwaysOnService()) {
            val result = AlwaysOnModeController.triggerHeartbeatNow()
            _uiState.update { it.copy(settingsInfo = result) }
            return result
        }
        val result = RuntimeController.triggerHeartbeatNow(getApplication<Application>())
        _uiState.update { it.copy(settingsInfo = result) }
        return result
    }

    private fun reloadAutomationViaActiveRuntime() {
        if (shouldDelegateRemoteGatewayToAlwaysOnService()) {
            AlwaysOnModeController.reloadAutomation()
            return
        }
        RuntimeController.reloadAutomation(getApplication<Application>())
    }

    private fun reloadMcpViaActiveRuntime(config: McpHttpConfig) {
        if (shouldDelegateRemoteGatewayToAlwaysOnService()) {
            AlwaysOnModeController.reloadMcp()
            return
        }
        RuntimeController.reloadMcp(getApplication<Application>())
    }

    private fun reloadAllViaActiveRuntime() {
        if (shouldDelegateRemoteGatewayToAlwaysOnService()) {
            AlwaysOnModeController.reloadAll()
            return
        }
        RuntimeController.reloadAll(getApplication<Application>())
    }

    private fun observeRuntimeStatus() {
        viewModelScope.launch {
            RuntimeController.status.collectLatest { status ->
                runtimeProcessingSessions = status.processingSessionIds
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                updateObservedGatewayProcessingSessions()
            }
        }
    }

    private fun observeAlwaysOnStatus() {
        viewModelScope.launch {
            AlwaysOnModeController.status.collectLatest { status ->
                alwaysOnProcessingSessions = status.processingSessionIds
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                _uiState.update {
                    it.copy(
                        alwaysOnServiceRunning = status.serviceRunning,
                        alwaysOnNotificationActive = status.notificationActive,
                        alwaysOnGatewayRunning = status.gatewayRunning,
                        alwaysOnActiveAdapterCount = status.activeAdapterCount,
                        alwaysOnStartedAtMs = status.startedAtMs,
                        alwaysOnLastError = status.lastError
                    )
                }
                updateObservedGatewayProcessingSessions()
            }
        }
    }

    private fun updateObservedGatewayProcessingSessions() {
        var deferredConfig: ChannelsConfig? = null
        synchronized(gatewayProcessingSessions) {
            gatewayProcessingSessions.clear()
            gatewayProcessingSessions.addAll(runtimeProcessingSessions)
            gatewayProcessingSessions.addAll(alwaysOnProcessingSessions)
            if (gatewayProcessingSessions.isEmpty()) {
                deferredConfig = pendingGatewayConfig
                pendingGatewayConfig = null
            }
        }
        if (deferredConfig != null) {
            applyGatewayRuntimeConfig(deferredConfig!!)
        }
        syncGeneratingState()
    }

    private fun buildAdapterMetadata(adapterKey: String?): Map<String, String> {
        val normalized = adapterKey?.trim()?.ifBlank { null } ?: return emptyMap()
        return mapOf(GatewayOrchestrator.KEY_ADAPTER_KEY to normalized)
    }

    private fun buildAdapterKey(channel: String, seed: String): String {
        val normalizedChannel = channel.trim().lowercase(Locale.US)
        val normalizedSeed = seed.trim()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizedSeed.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(16)
        return "$normalizedChannel:$digest"
    }

    private fun buildFeishuAdapterKeys(
        appId: String,
        appSecret: String,
        encryptKey: String,
        verificationToken: String
    ): List<String> {
        return buildFeishuAdapterSeeds(
            appId = appId,
            appSecret = appSecret,
            encryptKey = encryptKey,
            verificationToken = verificationToken
        ).map { buildAdapterKey("feishu", it) }
    }

    private fun buildEmailAdapterKey(config: EmailAccountConfig): String? {
        val imapHost = config.imapHost.trim()
        val imapUsername = config.imapUsername.trim()
        val smtpHost = config.smtpHost.trim()
        val smtpUsername = config.smtpUsername.trim()
        if (
            imapHost.isBlank() ||
            imapUsername.isBlank() ||
            config.imapPassword.isBlank() ||
            smtpHost.isBlank() ||
            smtpUsername.isBlank() ||
            config.smtpPassword.isBlank()
        ) return null
        return buildAdapterKey(
            "email",
            "$imapHost|${config.imapPort}|$imapUsername|$smtpHost|${config.smtpPort}|$smtpUsername|${config.fromAddress.trim()}"
        )
    }

    private fun buildWeComAdapterKey(botId: String, secret: String): String? {
        val normalizedBotId = botId.trim()
        val normalizedSecret = secret.trim()
        if (normalizedBotId.isBlank() || normalizedSecret.isBlank()) return null
        return buildAdapterKey("wecom", "$normalizedBotId|$normalizedSecret")
    }

    private fun adapterKeysForBinding(binding: SessionChannelBinding): List<String> {
        val channel = binding.channel.trim().lowercase(Locale.US)
        return when (channel) {
            "telegram" -> binding.telegramBotToken.trim()
                .takeIf { it.isNotBlank() }
                ?.let { listOf(buildAdapterKey(channel, it)) }
                .orEmpty()
            "discord" -> binding.discordBotToken.trim()
                .takeIf { it.isNotBlank() }
                ?.let { listOf(buildAdapterKey(channel, it)) }
                .orEmpty()
            "slack" -> {
                val botToken = binding.slackBotToken.trim()
                val appToken = binding.slackAppToken.trim()
                if (botToken.isBlank() || appToken.isBlank()) emptyList()
                else listOf(buildAdapterKey(channel, "$botToken|$appToken"))
            }
            "feishu" -> buildFeishuAdapterSeeds(
                appId = binding.feishuAppId,
                appSecret = binding.feishuAppSecret,
                encryptKey = binding.feishuEncryptKey,
                verificationToken = binding.feishuVerificationToken
            ).map { buildAdapterKey(channel, it) }
            "email" -> {
                val imapHost = binding.emailImapHost.trim()
                val imapUsername = binding.emailImapUsername.trim()
                val smtpHost = binding.emailSmtpHost.trim()
                val smtpUsername = binding.emailSmtpUsername.trim()
                if (
                    imapHost.isBlank() ||
                    imapUsername.isBlank() ||
                    binding.emailImapPassword.isBlank() ||
                    smtpHost.isBlank() ||
                    smtpUsername.isBlank() ||
                    binding.emailSmtpPassword.isBlank()
                ) emptyList() else listOf(
                    buildAdapterKey(
                        channel,
                        "$imapHost|${binding.emailImapPort}|$imapUsername|$smtpHost|${binding.emailSmtpPort}|$smtpUsername|${binding.emailFromAddress.trim()}"
                    )
                )
            }
            "wecom" -> {
                val botId = binding.wecomBotId.trim()
                val secret = binding.wecomSecret.trim()
                if (botId.isBlank() || secret.isBlank()) emptyList()
                else listOf(buildAdapterKey(channel, "$botId|$secret"))
            }
            else -> emptyList()
        }
    }

    private fun adapterKeyForBinding(binding: SessionChannelBinding): String? {
        return adapterKeysForBinding(binding).firstOrNull()
    }

    private suspend fun resolveSessionForToolTarget(
        sessionId: String?,
        sessionTitle: String?
    ): SessionTarget? {
        val sessions = sessionRepository.listSessions()
            .map { SessionTarget(id = it.id, title = it.title) }
        val requestedId = sessionId?.trim().orEmpty()
        if (requestedId.isNotBlank()) {
            return sessions.firstOrNull { it.id.equals(requestedId, ignoreCase = true) }
        }

        val requestedTitle = sessionTitle?.trim().orEmpty()
        if (requestedTitle.isBlank()) return null
        val exactMatches = sessions.filter { it.title.equals(requestedTitle, ignoreCase = true) }
        if (exactMatches.size > 1) {
            throw IllegalArgumentException("session_title matches multiple sessions; use session_id")
        }
        exactMatches.singleOrNull()?.let { return it }
        val partialMatches = sessions.filter { it.title.contains(requestedTitle, ignoreCase = true) }
        return when {
            partialMatches.isEmpty() -> null
            partialMatches.size == 1 -> partialMatches.first()
            else -> throw IllegalArgumentException("session_title is ambiguous; use session_id")
        }
    }

    private suspend fun buildSessionsSnapshotForTool(): SessionsListTool.Snapshot {
        val bindingsBySession = configStore.getSessionChannelBindings()
            .associateBy { it.sessionId.trim() }
        val rawSessions = sessionRepository.listSessions().toMutableList()
        if (rawSessions.none { it.id == AppSession.LOCAL_SESSION_ID }) {
            rawSessions += SessionEntity(
                id = AppSession.LOCAL_SESSION_ID,
                title = AppSession.LOCAL_SESSION_TITLE,
                createdAt = 0L,
                updatedAt = 0L
            )
        }
        val ordered = rawSessions.sortedWith(
            compareBy<SessionEntity> { it.id != AppSession.LOCAL_SESSION_ID }
                .thenByDescending { it.updatedAt }
                .thenBy { it.createdAt }
        )
        val activeId = currentSessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
        val entries = ordered.map { session ->
            val binding = bindingsBySession[session.id]
            val boundChannel = binding?.channel?.trim().orEmpty()
            val boundTarget = binding?.chatId?.trim().orEmpty()
            val channelEnabled = binding?.enabled ?: true
            val isCurrent = session.id == activeId
            val status = when {
                isCurrent -> "current"
                !channelEnabled -> "off"
                else -> "active"
            }
            SessionsListTool.Entry(
                sessionId = session.id,
                title = session.title,
                status = status,
                isCurrent = isCurrent,
                isLocal = session.id == AppSession.LOCAL_SESSION_ID,
                channelEnabled = channelEnabled,
                boundChannel = boundChannel,
                boundTarget = boundTarget
            )
        }
        return SessionsListTool.Snapshot(
            currentSessionId = activeId,
            sessions = entries
        )
    }

    private suspend fun buildChannelBindingsSnapshotForTool(): ChannelsGetTool.Snapshot {
        val gatewayEnabled = configStore.getChannelsConfig().enabled
        val bindingsBySession = configStore.getSessionChannelBindings()
            .associateBy { it.sessionId.trim() }
        val sessions = sessionRepository.listSessions().toMutableList()
        if (sessions.none { it.id == AppSession.LOCAL_SESSION_ID }) {
            sessions += SessionEntity(
                id = AppSession.LOCAL_SESSION_ID,
                title = AppSession.LOCAL_SESSION_TITLE,
                createdAt = 0L,
                updatedAt = 0L
            )
        }
        val entries = sessions
            .sortedWith(
                compareBy<SessionEntity> { it.id != AppSession.LOCAL_SESSION_ID }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.createdAt }
            )
            .map { session ->
                val binding = bindingsBySession[session.id]
                val channel = binding?.channel?.trim()?.lowercase(Locale.US).orEmpty()
                val target = normalizedBindingTarget(binding)
                val status = resolveBindingRuntimeStatus(binding, gatewayEnabled)
                ChannelsGetTool.Entry(
                    sessionId = session.id,
                    title = session.title,
                    bindingEnabled = binding?.enabled ?: false,
                    channel = channel,
                    target = target,
                    status = status
                )
            }
        return ChannelsGetTool.Snapshot(
            gatewayEnabled = gatewayEnabled,
            sessions = entries
        )
    }

    private suspend fun buildMcpStatusSnapshot(): McpStatusTool.Snapshot {
        val config = configStore.getMcpHttpConfig()
        val servers = config.servers.ifEmpty {
            if (config.serverUrl.isNotBlank()) {
                listOf(
                    McpHttpServerConfig(
                        id = "mcp_1",
                        serverName = config.serverName,
                        serverUrl = config.serverUrl,
                        authToken = config.authToken,
                        toolTimeoutSeconds = config.toolTimeoutSeconds
                    )
                )
            } else {
                emptyList()
            }
        }
        val entries = servers.map { server ->
            val normalizedName = normalizeMcpRuntimeServerName(server.serverName)
            val status = mcpServerStatuses[normalizedName] ?: if (config.enabled) {
                UiMcpServerRuntimeStatus(status = "Not connected")
            } else {
                UiMcpServerRuntimeStatus(status = "Disabled")
            }
            McpStatusTool.Entry(
                id = server.id.ifBlank { normalizedName.ifBlank { "mcp" } },
                serverName = server.serverName,
                serverUrl = server.serverUrl,
                status = status.status,
                usable = status.usable,
                detail = status.detail,
                toolCount = status.toolCount,
                toolNames = status.toolNames
            )
        }
        return McpStatusTool.Snapshot(
            enabled = config.enabled,
            connectedServerCount = entries.count { it.status.equals("Connected", ignoreCase = true) },
            registeredToolCount = entries.sumOf { it.toolCount },
            servers = entries
        )
    }

    private fun normalizeMcpRuntimeServerName(input: String): String {
        return input.trim().lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_\\-]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { AppLimits.DEFAULT_MCP_HTTP_SERVER_NAME }
    }

    private fun buildUiMcpServerConfigs(config: McpHttpConfig): List<UiMcpServerConfig> {
        val servers = config.servers.ifEmpty {
            if (config.serverUrl.isNotBlank()) {
                listOf(
                    McpHttpServerConfig(
                        id = "mcp_1",
                        serverName = config.serverName,
                        serverUrl = config.serverUrl,
                        authToken = config.authToken,
                        toolTimeoutSeconds = config.toolTimeoutSeconds
                    )
                )
            } else {
                emptyList()
            }
        }
        return servers.map { server ->
            val runtimeName = normalizeMcpRuntimeServerName(server.serverName)
            val status = mcpServerStatuses[runtimeName] ?: if (config.enabled) {
                UiMcpServerRuntimeStatus(status = "Not connected")
            } else {
                UiMcpServerRuntimeStatus(status = "Disabled")
            }
            UiMcpServerConfig(
                id = server.id.ifBlank { "mcp_${server.serverName}_${server.serverUrl.hashCode()}" },
                serverName = server.serverName,
                serverUrl = server.serverUrl,
                authToken = server.authToken,
                toolTimeoutSeconds = server.toolTimeoutSeconds.toString(),
                status = status.status,
                usable = status.usable,
                detail = status.detail,
                toolCount = status.toolCount
            )
        }
    }

    private fun buildUiProviderConfigs(config: com.lgclaw.config.AppConfig): List<UiProviderConfig> {
        val activeId = config.activeProviderConfigId.trim()
        val mapped = config.providerConfigs.map { item ->
            val resolvedProvider = ProviderCatalog.resolve(item.providerName)
            val resolvedProtocol = ProviderCatalog.resolveProtocol(
                rawProvider = resolvedProvider.id,
                requested = item.providerProtocol,
                baseUrl = item.baseUrl
            )
            UiProviderConfig(
                id = item.id.trim().ifBlank {
                    "provider_${resolvedProvider.id}_${item.model.hashCode()}"
                },
                providerName = resolvedProvider.id,
                customName = item.customName,
                providerProtocol = resolvedProtocol,
                apiKey = item.apiKey,
                model = item.model.ifBlank {
                    ProviderCatalog.defaultModel(resolvedProvider.id, resolvedProtocol)
                },
                equippedModels = (item.equippedModels + item.model).map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                baseUrl = item.baseUrl.ifBlank {
                    ProviderCatalog.defaultBaseUrl(resolvedProvider.id, resolvedProtocol)
                },
                enabled = item.id.trim() == activeId
            )
        }
        return normalizeActiveProviderConfigs(mapped)
    }

    private fun refreshMcpServersInState(config: McpHttpConfig = configStore.getMcpHttpConfig()) {
        val uiServers = buildUiMcpServerConfigs(config)
        _uiState.update { state ->
            val first = uiServers.firstOrNull()
            state.copy(
                settingsMcpEnabled = config.enabled,
                settingsMcpServerName = first?.serverName ?: AppLimits.DEFAULT_MCP_HTTP_SERVER_NAME,
                settingsMcpServerUrl = first?.serverUrl.orEmpty(),
                settingsMcpAuthToken = first?.authToken.orEmpty(),
                settingsMcpToolTimeoutSeconds = first?.toolTimeoutSeconds
                    ?: AppLimits.DEFAULT_MCP_HTTP_TOOL_TIMEOUT_SECONDS.toString(),
                settingsMcpServers = uiServers
            )
        }
    }

    private suspend fun setSessionChannelEnabledInternal(
        sessionId: String?,
        sessionTitle: String?,
        enabled: Boolean
    ): ChannelsSetTool.Result {
        val target = resolveSessionForToolTarget(
            sessionId = sessionId,
            sessionTitle = sessionTitle
        ) ?: throw IllegalArgumentException("target session not found")
        val binding = configStore.getSessionChannelBindings()
            .firstOrNull { it.sessionId.trim() == target.id.trim() }
            ?: throw IllegalArgumentException("target session has no channel binding")
        if (binding.channel.trim().isBlank()) {
            throw IllegalArgumentException("target session has no configured channel binding")
        }
        configStore.saveSessionChannelBinding(binding.copy(enabled = enabled))
        val current = configStore.getChannelsConfig()
        val shouldEnableGateway = hasActiveGatewayBinding(configStore.getSessionChannelBindings())
        val runtimeConfig = if (current.enabled == shouldEnableGateway) {
            current
        } else {
            current.copy(enabled = shouldEnableGateway).also { cfg ->
                configStore.saveChannelsConfig(cfg)
            }
        }
        refreshSessionBindingsInState()
        requestGatewayRuntimeConfig(runtimeConfig)
        _uiState.update { it.copy(settingsGatewayEnabled = runtimeConfig.enabled) }
        val status = buildConnectedChannelsOverview(_uiState.value.sessions)
            .firstOrNull { it.sessionId == target.id }
            ?.status
            ?: if (enabled) "Configured" else "Disabled"
        return ChannelsSetTool.Result(
            sessionId = target.id,
            sessionTitle = target.title,
            enabled = enabled,
            status = status
        )
    }

    private data class SessionTarget(
        val id: String,
        val title: String
    )

    private fun findSessionChannelBinding(sessionId: String): SessionChannelBinding? {
        val sid = sessionId.trim()
        if (sid.isBlank()) return null
        val raw = configStore.getSessionChannelBindings()
            .firstOrNull { it.sessionId.trim() == sid }
            ?: return null
        if (!raw.enabled) return null
        val channel = raw.channel.trim().lowercase(Locale.US)
        val chatId = raw.chatId.trim()
        if (channel.isBlank() || chatId.isBlank()) return null
        return when (channel) {
            "telegram" -> {
                val token = raw.telegramBotToken.trim()
                if (token.isBlank()) return null
                raw.copy(
                    channel = channel,
                    chatId = chatId,
                    telegramBotToken = token,
                    telegramAllowedChatId = raw.telegramAllowedChatId?.trim()?.ifBlank { null }
                )
            }
            "discord" -> {
                val token = raw.discordBotToken.trim()
                if (token.isBlank()) return null
                raw.copy(
                    channel = channel,
                    chatId = chatId,
                    discordBotToken = token,
                    discordResponseMode = normalizeDiscordResponseMode(raw.discordResponseMode),
                    discordAllowedUserIds = raw.discordAllowedUserIds
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                )
            }
            "slack" -> {
                val botToken = raw.slackBotToken.trim()
                val appToken = raw.slackAppToken.trim()
                val normalizedChatId = normalizeSlackChannelId(chatId)
                if (botToken.isBlank() || appToken.isBlank() || !isSlackChannelId(normalizedChatId)) return null
                raw.copy(
                    channel = channel,
                    chatId = normalizedChatId,
                    slackBotToken = botToken,
                    slackAppToken = appToken,
                    slackResponseMode = normalizeSlackResponseMode(raw.slackResponseMode),
                    slackAllowedUserIds = raw.slackAllowedUserIds
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                )
            }
            "feishu" -> {
                val appId = raw.feishuAppId.trim()
                val appSecret = raw.feishuAppSecret.trim()
                val normalizedChatId = normalizeFeishuTargetId(chatId)
                if (appId.isBlank() || appSecret.isBlank() || normalizedChatId.isBlank()) return null
                raw.copy(
                    channel = channel,
                    chatId = normalizedChatId,
                    feishuAppId = appId,
                    feishuAppSecret = appSecret,
                    feishuEncryptKey = raw.feishuEncryptKey.trim(),
                    feishuVerificationToken = raw.feishuVerificationToken.trim(),
                    feishuResponseMode = normalizeFeishuResponseMode(raw.feishuResponseMode),
                    feishuAllowedOpenIds = raw.feishuAllowedOpenIds
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                )
            }
            "email" -> {
                val normalizedChatId = normalizeEmailAddress(chatId)
                if (!raw.emailConsentGranted) return null
                val imapHost = raw.emailImapHost.trim()
                val imapUsername = raw.emailImapUsername.trim()
                val imapPassword = raw.emailImapPassword
                val smtpHost = raw.emailSmtpHost.trim()
                val smtpUsername = raw.emailSmtpUsername.trim()
                val smtpPassword = raw.emailSmtpPassword
                val fromAddress = normalizeEmailAddress(raw.emailFromAddress)
                if (
                    imapHost.isBlank() ||
                    imapUsername.isBlank() ||
                    imapPassword.isBlank() ||
                    smtpHost.isBlank() ||
                    smtpUsername.isBlank() ||
                    smtpPassword.isBlank() ||
                    !isEmailAddress(fromAddress)
                ) return null
                if (normalizedChatId.isNotBlank() && !isEmailAddress(normalizedChatId)) return null
                raw.copy(
                    channel = channel,
                    chatId = normalizedChatId,
                    emailConsentGranted = true,
                    emailImapHost = imapHost,
                    emailImapPort = raw.emailImapPort.coerceIn(1, 65535),
                    emailImapUsername = imapUsername,
                    emailImapPassword = imapPassword,
                    emailSmtpHost = smtpHost,
                    emailSmtpPort = raw.emailSmtpPort.coerceIn(1, 65535),
                    emailSmtpUsername = smtpUsername,
                    emailSmtpPassword = smtpPassword,
                    emailFromAddress = fromAddress
                )
            }
            "wecom" -> {
                val botId = raw.wecomBotId.trim()
                val secret = raw.wecomSecret.trim()
                val normalizedChatId = normalizeWeComTargetId(chatId)
                if (botId.isBlank() || secret.isBlank()) return null
                raw.copy(
                    channel = channel,
                    chatId = normalizedChatId,
                    wecomBotId = botId,
                    wecomSecret = secret,
                    wecomAllowedUserIds = raw.wecomAllowedUserIds
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                )
            }
            else -> null
        }
    }

    private fun normalizedChannelForInfo(configStore: ConfigStore, sessionId: String): String {
        return configStore.getSessionChannelBindings()
            .firstOrNull { it.sessionId.trim() == sessionId.trim() }
            ?.channel
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()
    }

    private fun normalizedTargetForInfo(configStore: ConfigStore, sessionId: String): String {
        return configStore.getSessionChannelBindings()
            .firstOrNull { it.sessionId.trim() == sessionId.trim() }
            ?.chatId
            .orEmpty()
            .trim()
    }

    private fun infoChannelLabel(channel: String, useChinese: Boolean): String {
        return when (channel.trim().lowercase(Locale.US)) {
            "telegram" -> "Telegram"
            "discord" -> "Discord"
            "slack" -> "Slack"
            "feishu" -> if (useChinese) "飞书" else "Feishu"
            "email" -> if (useChinese) "邮箱" else "Email"
            "wecom" -> if (useChinese) "企业微信" else "WeCom"
            else -> if (useChinese) "渠道" else "channel"
        }
    }

    private fun normalizedTargetMissingForInfo(configStore: ConfigStore, sessionId: String): Boolean {
        return configStore.getSessionChannelBindings()
            .firstOrNull { it.sessionId.trim() == sessionId.trim() }
            ?.chatId
            ?.trim()
            .isNullOrBlank()
    }

    private fun onGatewaySessionProcessingChanged(sessionId: String, processing: Boolean) {
        val sid = sessionId.trim()
        if (sid.isBlank()) return
        var deferredConfig: ChannelsConfig? = null
        synchronized(gatewayProcessingSessions) {
            if (processing) {
                gatewayProcessingSessions.add(sid)
            } else {
                gatewayProcessingSessions.remove(sid)
                if (gatewayProcessingSessions.isEmpty()) {
                    deferredConfig = pendingGatewayConfig
                    pendingGatewayConfig = null
                }
            }
            Unit
        }
        if (deferredConfig != null) {
            applyGatewayRuntimeConfig(deferredConfig!!)
        }
        syncGeneratingState()
    }

    private fun requestGatewayRuntimeConfig(config: ChannelsConfig) {
        val shouldDefer = synchronized(gatewayProcessingSessions) {
            if (gatewayProcessingSessions.isEmpty()) {
                pendingGatewayConfig = null
                false
            } else {
                pendingGatewayConfig = config
                true
            }
        }
        if (!shouldDefer) {
            applyGatewayRuntimeConfig(config)
        }
    }

    private fun computeIsGeneratingForSession(sessionId: String): Boolean {
        val sid = sessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
        if (generatingJob != null) return true
        return synchronized(gatewayProcessingSessions) {
            gatewayProcessingSessions.contains(sid)
        }
    }

    private fun refreshConversationMetrics(messages: List<MessageEntity>) {
        val sessionId = currentSessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
        val currentK = compressedMemoryStore.estimateEffectiveK(sessionId, messages)
        val memories = compressedMemoryStore.list(sessionId).map {
            UiCompressedMemory(
                id = it.id,
                sessionId = it.sessionId,
                createdAt = it.createdAt,
                algorithm = it.algorithm,
                summary = it.summary,
                originalChars = it.originalChars,
                compressedBytes = it.compressedBytes,
                messageCount = it.messageCount
            )
        }
        _uiState.update { it.copy(currentConversationK = currentK, compressedMemories = memories) }
        maybeStartAutoCompression(sessionId, messages, currentK)
    }

    private fun maybeStartAutoCompression(sessionId: String, messages: List<MessageEntity>, currentK: Double) {
        if (compressionJob?.isActive == true) return
        if (sessionId in autoCompressionSessions) return
        val threshold = configStore.getConfig().compressionThresholdK
        if (!CompressionPolicy.shouldCompress(currentK, threshold)) return
        val lastCompressedAt = compressedMemoryStore.list(sessionId).maxOfOrNull { it.lastMessageAt } ?: 0L
        val selection = CompressionPolicy.selectCandidates(
            messages = messages,
            lastCompressedAt = lastCompressedAt,
            keepRecentMessages = 6,
            minCandidates = 2
        )
        if (selection.candidates.isEmpty()) return
        compressionJob = viewModelScope.launch(Dispatchers.IO) {
            autoCompressionSessions.add(sessionId)
            try {
                runCompressionForSession(
                    sessionId = sessionId,
                    manual = false,
                    keepRecentMessages = 6,
                    minCandidates = 2
                )
            } finally {
                autoCompressionSessions.remove(sessionId)
            }
        }
    }

    private suspend fun runCompressionForSession(
        sessionId: String,
        manual: Boolean,
        keepRecentMessages: Int,
        minCandidates: Int
    ) {
        fun updateProgress(progress: Float, stage: String, path: String) {
            _uiState.update {
                it.copy(
                    compressionProgress = UiCompressionProgress(
                        running = true,
                        manual = manual,
                        progress = progress.coerceIn(0f, 1f),
                        stage = stage,
                        path = path
                    )
                )
            }
        }

        try {
            updateProgress(0.08f, "读取当前对话", "消息仓库 -> 当前会话")
            coroutineContext.ensureActive()
            val messages = messageRepository.getMessages(sessionId)
            val lastCompressedAt = compressedMemoryStore.list(sessionId).maxOfOrNull { it.lastMessageAt } ?: 0L
            val selection = CompressionPolicy.selectCandidates(
                messages = messages,
                lastCompressedAt = lastCompressedAt,
                keepRecentMessages = keepRecentMessages,
                minCandidates = minCandidates
            )
            if (selection.candidates.isEmpty()) {
                _uiState.update {
                    it.copy(
                        compressionProgress = UiCompressionProgress(),
                        settingsInfo = if (manual) "没有新的可压缩内容：${selection.reason.ifBlank { "候选消息为空" }}" else it.settingsInfo
                    )
                }
                return
            }

            updateProgress(0.28f, "筛选压缩范围", "保留最近 ${selection.keptRecentCount} 条，压缩 ${selection.candidates.size} 条")
            delay(90)
            coroutineContext.ensureActive()
            updateProgress(0.48f, "生成本地摘要", "TextRank 句子评分 + 关键词抽取")
            delay(90)
            coroutineContext.ensureActive()
            updateProgress(0.68f, "写入压缩归档", "GZIP 原文归档 -> 本地记忆目录")
            val record = compressedMemoryStore.compressNow(
                sessionId = sessionId,
                messages = messages,
                keepRecentMessages = keepRecentMessages,
                minCandidates = minCandidates
            )
            coroutineContext.ensureActive()
            updateProgress(0.88f, "刷新前端 K 值", "压缩索引 -> 顶部 K 值 -> 记忆面板")
            val freshMessages = messageRepository.getMessages(sessionId)
            val newK = compressedMemoryStore.estimateEffectiveK(sessionId, freshMessages)
            val memories = compressedMemoryStore.list(sessionId).map {
                UiCompressedMemory(
                    id = it.id,
                    sessionId = it.sessionId,
                    createdAt = it.createdAt,
                    algorithm = it.algorithm,
                    summary = it.summary,
                    originalChars = it.originalChars,
                    compressedBytes = it.compressedBytes,
                    messageCount = it.messageCount
                )
            }
            _uiState.update {
                it.copy(
                    currentConversationK = newK,
                    compressedMemories = memories,
                    compressionProgress = UiCompressionProgress(
                        running = true,
                        manual = manual,
                        progress = 1f,
                        stage = "压缩完成",
                        path = record?.let { item -> "${item.id}：${item.originalChars} 字符 -> ${item.compressedBytes} 字节" }
                            ?: "没有写入新的压缩记录"
                    ),
                    settingsInfo = record?.let { item ->
                        "压缩完成：${item.messageCount} 条消息，当前有效上下文 ${"%.1f".format(Locale.US, newK)}K"
                    } ?: if (manual) "没有新的可压缩内容" else it.settingsInfo
                )
            }
            delay(900)
        } catch (cancelled: CancellationException) {
            _uiState.update {
                it.copy(
                    compressionProgress = UiCompressionProgress(),
                    settingsInfo = "压缩已取消"
                )
            }
            throw cancelled
        } catch (t: Throwable) {
            _uiState.update {
                it.copy(
                    compressionProgress = UiCompressionProgress(),
                    settingsInfo = "压缩失败：${t.message ?: t.javaClass.simpleName}"
                )
            }
        } finally {
            if (compressionJob == coroutineContext[Job]) {
                compressionJob = null
            }
            _uiState.update { state ->
                if (!state.compressionProgress.running) {
                    state
                } else {
                    state.copy(compressionProgress = UiCompressionProgress())
                }
            }
        }
    }
    private fun syncGeneratingState() {
        val activeSessionId = currentSessionId.trim().ifBlank { AppSession.LOCAL_SESSION_ID }
        val busy = computeIsGeneratingForSession(activeSessionId)
        _uiState.update { state ->
            if (state.isGenerating == busy) state else state.copy(isGenerating = busy)
        }
    }

    private fun hasActiveGatewayBinding(bindings: List<SessionChannelBinding>): Boolean {
        return bindings.any { raw ->
            if (!raw.enabled) return@any false
            val channel = raw.channel.trim().lowercase(Locale.US)
            val chatId = raw.chatId.trim()
            if (channel.isBlank()) return@any false
            when (channel) {
                "telegram" -> raw.telegramBotToken.trim().isNotBlank() && chatId.isNotBlank()
                "discord" -> raw.discordBotToken.trim().isNotBlank() && isDiscordSnowflake(chatId)
                "slack" -> {
                    raw.slackBotToken.trim().isNotBlank() &&
                        raw.slackAppToken.trim().isNotBlank() &&
                        isSlackChannelId(normalizeSlackChannelId(chatId))
                }
                "feishu" -> raw.feishuAppId.trim().isNotBlank() && raw.feishuAppSecret.trim().isNotBlank()
                "email" -> {
                    raw.emailConsentGranted &&
                        raw.emailImapHost.trim().isNotBlank() &&
                        raw.emailImapUsername.trim().isNotBlank() &&
                        raw.emailImapPassword.isNotBlank() &&
                        raw.emailSmtpHost.trim().isNotBlank() &&
                        raw.emailSmtpUsername.trim().isNotBlank() &&
                        raw.emailSmtpPassword.isNotBlank()
                }
                "wecom" -> raw.wecomBotId.trim().isNotBlank() && raw.wecomSecret.trim().isNotBlank()
                else -> false
            }
        }
    }

    private fun resolveGatewaySessionBinding(message: InboundMessage): String? {
        val c = message.channel.trim().lowercase(Locale.US)
        val targetIds = when (c) {
            "discord" -> listOf(normalizeDiscordChannelId(message.chatId))
            "slack" -> listOf(normalizeSlackChannelId(message.chatId))
            "feishu" -> buildFeishuTargetAliases(
                primaryTargetId = message.chatId,
                sourceChatId = message.metadata["source_chat_id"].orEmpty(),
                senderOpenId = message.metadata["sender_open_id"].orEmpty()
            )
            "email" -> listOf(normalizeEmailAddress(message.chatId))
            "wecom" -> listOf(normalizeWeComTargetId(message.chatId))
            else -> listOf(message.chatId.trim())
        }
            .filter { it.isNotBlank() }
        if (c.isBlank() || targetIds.isEmpty()) return null
        val adapterKey = message.metadata[GatewayOrchestrator.KEY_ADAPTER_KEY]
            ?.trim()
            ?.ifBlank { null }
        val bindings = configStore.getSessionChannelBindings()
        val exact = bindings.firstOrNull {
            val channelMatches = it.enabled && it.channel.trim().lowercase(Locale.US) == c
            if (!channelMatches) return@firstOrNull false
            if (it.chatId.trim() !in targetIds) return@firstOrNull false
            if (adapterKey == null) return@firstOrNull false
            adapterKeysForBinding(it).contains(adapterKey)
        }
        if (exact != null) {
            return exact.sessionId.trim().ifBlank { null }
        }
        val fallback = bindings.firstOrNull {
            val channelMatches = it.enabled && it.channel.trim().lowercase(Locale.US) == c
            channelMatches && it.chatId.trim() in targetIds
        }
        return fallback?.sessionId?.trim()?.ifBlank { null }
    }

    private fun buildConnectedChannelsOverview(sessions: List<UiSessionSummary>): List<UiConnectedChannelSummary> {
        val gatewayEnabled = configStore.getChannelsConfig().enabled
        val bindingsBySession = configStore.getSessionChannelBindings()
            .associateBy { it.sessionId.trim() }
        return sessions
            .asSequence()
            .filterNot { it.isLocal }
            .mapNotNull { session ->
                val binding = bindingsBySession[session.id] ?: return@mapNotNull null
                val channel = binding.channel.trim().lowercase(Locale.US)
                if (channel !in setOf("telegram", "discord", "slack", "feishu", "email", "wecom")) {
                    return@mapNotNull null
                }
                UiConnectedChannelSummary(
                    sessionId = session.id,
                    sessionTitle = session.title,
                    channel = channel,
                    chatId = normalizedBindingTarget(binding),
                    enabled = binding.enabled,
                    status = resolveBindingRuntimeStatus(binding, gatewayEnabled)
                )
            }
            .sortedWith(
                compareBy<UiConnectedChannelSummary>(
                    { it.channel },
                    { it.sessionTitle.lowercase(Locale.US) }
                )
            )
            .toList()
    }

    private fun resolveBindingRuntimeStatus(
        binding: SessionChannelBinding?,
        gatewayEnabled: Boolean
    ): String {
        if (binding == null) return "Unbound"
        val channel = binding.channel.trim().lowercase(Locale.US)
        if (channel.isBlank()) return "Unbound"
        if (!binding.enabled) return "Disabled"
        val target = normalizedBindingTarget(binding)
        when (channel) {
            "telegram" -> {
                if (binding.telegramBotToken.trim().isBlank()) return "Missing token"
                if (target.isBlank()) return "Waiting for chat detection"
            }
            "discord" -> {
                if (binding.discordBotToken.trim().isBlank()) return "Missing token"
                if (!isDiscordSnowflake(normalizeDiscordChannelId(target))) return "Missing channel id"
            }
            "slack" -> {
                if (binding.slackBotToken.trim().isBlank() || binding.slackAppToken.trim().isBlank()) {
                    return "Missing bot/app token"
                }
                if (!isSlackChannelId(normalizeSlackChannelId(target))) return "Missing channel id"
            }
            "feishu" -> {
                if (binding.feishuAppId.trim().isBlank() || binding.feishuAppSecret.trim().isBlank()) {
                    return "Missing app credentials"
                }
                if (target.isBlank()) return "Waiting for chat detection"
                if (!isFeishuTargetId(normalizeFeishuTargetId(target))) return "Invalid target"
            }
            "email" -> {
                if (!binding.emailConsentGranted) return "Consent required"
                if (
                    binding.emailImapHost.trim().isBlank() ||
                    binding.emailImapUsername.trim().isBlank() ||
                    binding.emailImapPassword.isBlank() ||
                    binding.emailSmtpHost.trim().isBlank() ||
                    binding.emailSmtpUsername.trim().isBlank() ||
                    binding.emailSmtpPassword.isBlank()
                ) return "Missing mailbox credentials"
                if (target.isBlank()) return "Waiting for sender detection"
                if (!isEmailAddress(normalizeEmailAddress(target))) return "Invalid sender"
            }
            "wecom" -> {
                if (binding.wecomBotId.trim().isBlank() || binding.wecomSecret.trim().isBlank()) {
                    return "Missing bot credentials"
                }
                if (target.isBlank()) return "Waiting for chat detection"
            }
            else -> return "Configured"
        }
        if (!gatewayEnabled) return "Gateway idle"
        val snapshot = adapterKeysForBinding(binding)
            .asSequence()
            .map { ChannelRuntimeDiagnostics.getSnapshot(channel, it) }
            .firstOrNull {
                it.running ||
                    it.connected ||
                    it.ready ||
                    it.lastError.isNotBlank()
            }
            ?: adapterKeyForBinding(binding)?.let { ChannelRuntimeDiagnostics.getSnapshot(channel, it) }
            ?: return "Configured"
        return when {
            snapshot.lastError.isNotBlank() && !snapshot.ready -> "Error"
            snapshot.ready -> "Connected"
            snapshot.connected -> "Connecting"
            snapshot.running -> "Starting"
            else -> "Configured"
        }
    }

    private fun normalizedBindingTarget(binding: SessionChannelBinding?): String {
        if (binding == null) return ""
        return when (binding.channel.trim().lowercase(Locale.US)) {
            "discord" -> normalizeDiscordChannelId(binding.chatId)
            "slack" -> normalizeSlackChannelId(binding.chatId)
            "feishu" -> normalizeFeishuTargetId(binding.chatId)
            "email" -> normalizeEmailAddress(binding.chatId)
            "wecom" -> normalizeWeComTargetId(binding.chatId)
            else -> binding.chatId.trim()
        }
    }

    private fun fetchTelegramChatCandidates(botToken: String): List<UiTelegramChatCandidate> {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?timeout=1&limit=100"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        telegramDiscoveryClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${body.take(300)}")
            }
            val root = JSONObject(body)
            if (!root.optBoolean("ok", false)) {
                val desc = root.optString("description").ifBlank { "Telegram API error" }
                throw IllegalStateException(desc)
            }
            val result = root.optJSONArray("result") ?: return emptyList()
            val byChat = LinkedHashSet<String>()
            val candidates = mutableListOf<UiTelegramChatCandidate>()
            for (i in 0 until result.length()) {
                val update = result.optJSONObject(i) ?: continue
                val messageLike = update.optJSONObject("message")
                    ?: update.optJSONObject("edited_message")
                    ?: update.optJSONObject("channel_post")
                    ?: update.optJSONObject("edited_channel_post")
                    ?: update.optJSONObject("my_chat_member")
                    ?: update.optJSONObject("chat_member")
                    ?: update.optJSONObject("chat_join_request")
                    ?: update.optJSONObject("callback_query")?.optJSONObject("message")
                    ?: continue
                val chat = messageLike.optJSONObject("chat") ?: continue
                val chatId = chat.optLong("id").takeIf { it != 0L }?.toString().orEmpty()
                if (chatId.isBlank()) continue
                if (!byChat.add(chatId)) continue
                val chatType = chat.optString("type").ifBlank { "unknown" }
                val title = buildTelegramChatTitle(chat, chatType)
                candidates += UiTelegramChatCandidate(
                    chatId = chatId,
                    title = title,
                    kind = chatType
                )
            }
            return candidates
        }
    }

    private fun buildTelegramChatTitle(chat: JSONObject, chatType: String): String {
        return when (chatType.lowercase(Locale.US)) {
            "private" -> {
                val first = chat.optString("first_name").trim()
                val last = chat.optString("last_name").trim()
                val username = chat.optString("username").trim()
                val name = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").trim()
                when {
                    name.isNotBlank() && username.isNotBlank() -> "$name (@$username)"
                    name.isNotBlank() -> name
                    username.isNotBlank() -> "@$username"
                    else -> "Private chat"
                }
            }
            "group", "supergroup", "channel" -> {
                chat.optString("title").trim().ifBlank { "Untitled $chatType" }
            }
            else -> {
                chat.optString("title").trim().ifBlank {
                    chat.optString("username").trim().ifBlank { "Chat" }
                }
            }
        }
    }

    private fun loadSettingsIntoState() {
        val cfg = configStore.getConfig()
        val cronCfg = configStore.getCronConfig()
        val heartbeatCfg = configStore.getHeartbeatConfig()
        val channelsCfg = configStore.getChannelsConfig()
        val alwaysOnCfg = configStore.getAlwaysOnConfig()
        val uiPrefsCfg = configStore.getUiPreferencesConfig()
        val onboardingCfg = onboardingCoordinator.resolveSyncedOnboardingConfig()
        val mcpCfg = configStore.getMcpHttpConfig()
        val mcpServers = buildUiMcpServerConfigs(mcpCfg)
        val cronLogs = cronLogStore.readRecent()
        val agentLogs = agentLogStore.readRecent()
        val tokenStats = configStore.getTokenUsageStats()
        val providerConfigs = buildUiProviderConfigs(cfg)
        _uiState.update {
            val resolvedProvider = ProviderCatalog.resolve(cfg.providerName)
            val resolvedProtocol = ProviderCatalog.resolveProtocol(
                rawProvider = resolvedProvider.id,
                requested = cfg.providerProtocol,
                baseUrl = cfg.baseUrl
            )
            val selectedProviderConfig = providerConfigs.firstOrNull { item ->
                item.id == cfg.activeProviderConfigId
            } ?: providerConfigs.firstOrNull()
            val connectedChannels = buildConnectedChannelsOverview(it.sessions)
            val discordGatewayStatus = buildDiscordGatewayStatusText()
            val slackGatewayStatus = buildSlackGatewayStatusText()
            val feishuGatewayStatus = buildFeishuGatewayStatusText()
            val emailGatewayStatus = buildEmailGatewayStatusText()
            val wecomGatewayStatus = buildWeComGatewayStatusText()
            it.copy(
                settingsProviderConfigs = providerConfigs,
                settingsEditingProviderConfigId = selectedProviderConfig?.id.orEmpty(),
                settingsProvider = selectedProviderConfig?.providerName ?: resolvedProvider.id,
                settingsProviderCustomName = selectedProviderConfig?.customName.orEmpty(),
                settingsProviderProtocol = selectedProviderConfig?.providerProtocol ?: resolvedProtocol,
                settingsModel = selectedProviderConfig?.model
                    ?: cfg.model.ifBlank {
                        ProviderCatalog.defaultModel(resolvedProvider.id, resolvedProtocol)
                    },
                settingsEquippedModels = selectedProviderConfig?.equippedModels.orEmpty().ifEmpty { listOf(selectedProviderConfig?.model ?: cfg.model).filter { it.isNotBlank() } },
                settingsDiscoveredModels = selectedProviderConfig?.let { ProviderCatalog.suggestedModels(it.providerName) }.orEmpty(),
                settingsApiKey = selectedProviderConfig?.apiKey ?: cfg.apiKey,
                settingsBaseUrl = selectedProviderConfig?.let { config ->
                    config.baseUrl.ifBlank {
                        ProviderCatalog.defaultBaseUrl(config.providerName, config.providerProtocol)
                    }
                } ?: cfg.baseUrl.ifBlank {
                    ProviderCatalog.defaultBaseUrl(resolvedProvider.id, resolvedProtocol)
                },
                settingsMaxToolRounds = cfg.maxToolRounds.toString(),
                settingsToolResultMaxChars = cfg.toolResultMaxChars.toString(),
                settingsMemoryConsolidationWindow = cfg.memoryConsolidationWindow.toString(),
                settingsCompressionThresholdK = cfg.compressionThresholdK.toString(),
                settingsLlmCallTimeoutSeconds = cfg.llmCallTimeoutSeconds.toString(),
                settingsLlmConnectTimeoutSeconds = cfg.llmConnectTimeoutSeconds.toString(),
                settingsLlmReadTimeoutSeconds = cfg.llmReadTimeoutSeconds.toString(),
                settingsDefaultToolTimeoutSeconds = cfg.defaultToolTimeoutSeconds.toString(),
                settingsContextMessages = cfg.contextMessages.toString(),
                settingsToolArgsPreviewMaxChars = cfg.toolArgsPreviewMaxChars.toString(),
                settingsCronEnabled = cronCfg.enabled,
                settingsCronMinEveryMs = cronCfg.minEveryMs.toString(),
                settingsCronMaxJobs = cronCfg.maxJobs.toString(),
                settingsTokenInput = tokenStats.inputTokens,
                settingsTokenOutput = tokenStats.outputTokens,
                settingsTokenTotal = tokenStats.totalTokens,
                settingsTokenCachedInput = tokenStats.cachedInputTokens,
                settingsTokenRequests = tokenStats.requests,
                settingsCronLogs = cronLogs,
                settingsAgentLogs = agentLogs,
                settingsHeartbeatEnabled = heartbeatCfg.enabled,
                settingsHeartbeatIntervalSeconds = heartbeatCfg.intervalSeconds.toString(),
                settingsGatewayEnabled = channelsCfg.enabled,
                settingsUseChinese = uiPrefsCfg.useChinese,
                settingsDarkTheme = uiPrefsCfg.darkTheme,
                themePreset = uiPrefsCfg.themePreset,
                themeTextColorHex = uiPrefsCfg.themeTextColorHex,
                themeFontFamily = uiPrefsCfg.themeFontFamily,
                themeBubbleStyle = uiPrefsCfg.themeBubbleStyle,
                themeUserBubbleColorHex = uiPrefsCfg.themeUserBubbleColorHex,
                themeAssistantBubbleColorHex = uiPrefsCfg.themeAssistantBubbleColorHex,
                themeToolBubbleColorHex = uiPrefsCfg.themeToolBubbleColorHex,
                themeBubbleOpacity = uiPrefsCfg.themeBubbleOpacity,
                themeBubbleCornerRadius = uiPrefsCfg.themeBubbleCornerRadius,
                themeBubbleBorderAlpha = uiPrefsCfg.themeBubbleBorderAlpha,
                themeBubbleHighlightAlpha = uiPrefsCfg.themeBubbleHighlightAlpha,
                themeBubbleShadowAlpha = uiPrefsCfg.themeBubbleShadowAlpha,
                themeBubbleGlassStrength = uiPrefsCfg.themeBubbleGlassStrength,
                themeMessageFontSizeSp = uiPrefsCfg.themeMessageFontSizeSp,
                themeMessageLineHeightMultiplier = uiPrefsCfg.themeMessageLineHeightMultiplier,
                themeCustomFontPath = uiPrefsCfg.themeCustomFontPath,
                chatBackgroundPath = uiPrefsCfg.chatBackgroundPath,
                chatBackgroundOpacity = uiPrefsCfg.chatBackgroundOpacity,
                chatBackgroundBlur = uiPrefsCfg.chatBackgroundBlur,
                chatBackgroundGlass = uiPrefsCfg.chatBackgroundGlass,
                drawerBackgroundPath = uiPrefsCfg.drawerBackgroundPath,
                drawerBackgroundOpacity = uiPrefsCfg.drawerBackgroundOpacity,
                drawerBackgroundBlur = uiPrefsCfg.drawerBackgroundBlur,
                drawerBackgroundGlass = uiPrefsCfg.drawerBackgroundGlass,
                onboardingCompleted = onboardingCfg.completed,
                userDisplayName = onboardingCfg.userDisplayName,
                agentDisplayName = onboardingCfg.agentDisplayName,
                onboardingUserDisplayName = onboardingCfg.userDisplayName,
                onboardingAgentDisplayName = onboardingCfg.agentDisplayName,
                alwaysOnEnabled = alwaysOnCfg.enabled,
                alwaysOnKeepScreenAwake = alwaysOnCfg.keepScreenAwake,
                settingsTelegramBotToken = channelsCfg.telegramBotToken,
                settingsTelegramAllowedChatId = channelsCfg.telegramAllowedChatId.orEmpty(),
                settingsDiscordWebhookUrl = channelsCfg.discordWebhookUrl,
                settingsConnectedChannels = connectedChannels,
                settingsDiscordGatewayStatus = discordGatewayStatus,
                settingsSlackGatewayStatus = slackGatewayStatus,
                settingsFeishuGatewayStatus = feishuGatewayStatus,
                settingsEmailGatewayStatus = emailGatewayStatus,
                settingsWeComGatewayStatus = wecomGatewayStatus,
                settingsMcpEnabled = mcpCfg.enabled,
                settingsMcpServerName = mcpServers.firstOrNull()?.serverName
                    ?: AppLimits.DEFAULT_MCP_HTTP_SERVER_NAME,
                settingsMcpServerUrl = mcpServers.firstOrNull()?.serverUrl.orEmpty(),
                settingsMcpAuthToken = mcpServers.firstOrNull()?.authToken.orEmpty(),
                settingsMcpToolTimeoutSeconds = mcpServers.firstOrNull()?.toolTimeoutSeconds
                    ?: AppLimits.DEFAULT_MCP_HTTP_TOOL_TIMEOUT_SECONDS.toString(),
                settingsMcpServers = mcpServers
            )
        }
    }

    private fun buildDiscordGatewayStatusText(): String {
        val snapshots = DiscordGatewayDiagnostics.getSnapshots().values.toList()
        val s = snapshots.singleOrNull()
        val lines = mutableListOf<String>()
        appendRuntimeStatusSummary(lines, "discord")
        if (s?.botUserId?.isNotBlank() == true) {
            lines += "Bot User ID: ${s.botUserId}"
        }
        lines += "Inbound seen: ${snapshots.sumOf { it.inboundSeen }}"
        lines += "Inbound forwarded: ${snapshots.sumOf { it.inboundForwarded }}"
        lines += "Outbound sent: ${snapshots.sumOf { it.outboundSent }}"
        if (s?.lastInboundChannelId?.isNotBlank() == true) {
            lines += "Last inbound channel: ${s.lastInboundChannelId}"
        }
        if (s?.lastGatewayPayload?.isNotBlank() == true) {
            lines += "Last payload: ${s.lastGatewayPayload}"
        }
        return lines.joinToString("\n")
    }

    private fun discordGatewayHintForError(error: String): String {
        val code = Regex("code=(\\d{4})").find(error)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        return when (code) {
            4004 -> "Invalid bot token. Re-copy token from Discord Developer Portal."
            4013 -> "Invalid intents bitmask. Update app and retry."
            4014 -> "Disallowed intents. Enable Message Content Intent for this bot."
            else -> ""
        }
    }

    private fun buildSlackGatewayStatusText(): String {
        val snapshots = SlackGatewayDiagnostics.getSnapshots().values.toList()
        val s = snapshots.singleOrNull()
        val lines = mutableListOf<String>()
        appendRuntimeStatusSummary(lines, "slack")
        if (s?.botUserId?.isNotBlank() == true) {
            lines += "Bot User ID: ${s.botUserId}"
        }
        lines += "Inbound seen: ${snapshots.sumOf { it.inboundSeen }}"
        lines += "Inbound forwarded: ${snapshots.sumOf { it.inboundForwarded }}"
        lines += "Outbound sent: ${snapshots.sumOf { it.outboundSent }}"
        if (s?.lastInboundChannelId?.isNotBlank() == true) {
            lines += "Last inbound channel: ${s.lastInboundChannelId}"
        }
        if (s?.lastEnvelopeType?.isNotBlank() == true) {
            lines += "Last envelope: ${s.lastEnvelopeType}"
        }
        return lines.joinToString("\n")
    }

    private fun buildFeishuGatewayStatusText(): String {
        val snapshots = FeishuGatewayDiagnostics.getSnapshots().values.toList()
        val s = snapshots.singleOrNull()
        val lines = mutableListOf<String>()
        appendRuntimeStatusSummary(lines, "feishu")
        lines += "Inbound seen: ${snapshots.sumOf { it.inboundSeen }}"
        lines += "Inbound forwarded: ${snapshots.sumOf { it.inboundForwarded }}"
        lines += "Outbound sent: ${snapshots.sumOf { it.outboundSent }}"
        if (s?.lastInboundChatId?.isNotBlank() == true) {
            lines += "Last inbound target: ${s.lastInboundChatId}"
        }
        if (s?.lastSenderOpenId?.isNotBlank() == true) {
            lines += "Last sender open_id: ${s.lastSenderOpenId}"
        }
        if (s?.lastEventType?.isNotBlank() == true) {
            lines += "Last event: ${s.lastEventType}"
        }
        val detectedChats = snapshots.sumOf { it.recentChats.size }
        if (detectedChats > 0) {
            lines += "Detected chats: $detectedChats"
        }
        return lines.joinToString("\n")
    }

    private fun buildEmailGatewayStatusText(): String {
        val snapshots = EmailGatewayDiagnostics.getSnapshots().values.toList()
        val s = snapshots.singleOrNull()
        val lines = mutableListOf<String>()
        appendRuntimeStatusSummary(lines, "email")
        lines += "Inbound seen: ${snapshots.sumOf { it.inboundSeen }}"
        lines += "Inbound forwarded: ${snapshots.sumOf { it.inboundForwarded }}"
        lines += "Outbound sent: ${snapshots.sumOf { it.outboundSent }}"
        if (s?.lastSenderEmail?.isNotBlank() == true) {
            lines += "Last sender: ${s.lastSenderEmail}"
        }
        if (s?.lastSubject?.isNotBlank() == true) {
            lines += "Last subject: ${s.lastSubject}"
        }
        val detectedSenders = snapshots.sumOf { it.recentSenders.size }
        if (detectedSenders > 0) {
            lines += "Detected senders: $detectedSenders"
        }
        return lines.joinToString("\n")
    }

    private fun buildWeComGatewayStatusText(): String {
        val snapshots = WeComGatewayDiagnostics.getSnapshots().values.toList()
        val s = snapshots.singleOrNull()
        val lines = mutableListOf<String>()
        appendRuntimeStatusSummary(lines, "wecom")
        lines += "Inbound seen: ${snapshots.sumOf { it.inboundSeen }}"
        lines += "Inbound forwarded: ${snapshots.sumOf { it.inboundForwarded }}"
        lines += "Outbound sent: ${snapshots.sumOf { it.outboundSent }}"
        if (s?.lastInboundChatId?.isNotBlank() == true) {
            lines += "Last inbound target: ${s.lastInboundChatId}"
        }
        if (s?.lastSenderUserId?.isNotBlank() == true) {
            lines += "Last sender user ID: ${s.lastSenderUserId}"
        }
        if (s?.lastEventType?.isNotBlank() == true) {
            lines += "Last event: ${s.lastEventType}"
        }
        val detectedChats = snapshots.sumOf { it.recentChats.size }
        if (detectedChats > 0) {
            lines += "Detected chats: $detectedChats"
        }
        return lines.joinToString("\n")
    }

    private fun appendRuntimeStatusSummary(lines: MutableList<String>, channel: String) {
        val snapshots = ChannelRuntimeDiagnostics.getSnapshots(channel).values
        lines += "Adapters: ${snapshots.size}"
        lines += "Running: ${snapshots.count { it.running }}"
        lines += "Connected: ${snapshots.count { it.connected }}"
        lines += "Ready: ${snapshots.count { it.ready }}"
        val lastError = snapshots.asSequence()
            .map { it.lastError.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        if (lastError.isNotBlank()) {
            lines += "Runtime error: $lastError"
        }
    }

    private fun weComGatewayHintForError(error: String): String {
        return when {
            error.contains("auth", ignoreCase = true) || error.contains("secret", ignoreCase = true) ->
                "Check WeCom Bot ID and Secret."
            error.contains("heartbeat", ignoreCase = true) ->
                "Connection stalled. Reopen the session settings or toggle Channels to reconnect."
            error.contains("socket", ignoreCase = true) || error.contains("websocket", ignoreCase = true) ->
                "WebSocket disconnected. Check network access and try again."
            else -> ""
        }
    }

    private fun slackGatewayHintForError(error: String): String {
        return when {
            error.contains("invalid_auth", ignoreCase = true) ->
                "Invalid token. Check Slack bot token (xoxb) and app token (xapp)."
            error.contains("missing_scope", ignoreCase = true) ->
                "Missing scope. Ensure chat:write, reactions:write, app_mentions:read are granted."
            error.contains("not_authed", ignoreCase = true) ->
                "Token missing or malformed. Re-copy from Slack app settings."
            error.contains("apps.connections.open", ignoreCase = true) ->
                "Socket mode open failed. Verify app token has connections:write and Socket Mode is enabled."
            else -> ""
        }
    }

    private fun applyCronRuntimeConfig(config: CronConfig) {
        reloadAutomationViaActiveRuntime()
    }

    private suspend fun persistCronSettings(
        update: com.lgclaw.tools.CronConfigUpdate
    ): CronConfig {
        val current = configStore.getCronConfig()
        val minEveryMs = update.minEveryMs ?: current.minEveryMs
        if (minEveryMs !in AppLimits.MIN_CRON_MIN_EVERY_MS..AppLimits.MAX_CRON_MIN_EVERY_MS) {
            throw IllegalArgumentException(
                "Cron min interval ms must be between ${AppLimits.MIN_CRON_MIN_EVERY_MS} and ${AppLimits.MAX_CRON_MIN_EVERY_MS}"
            )
        }
        val maxJobs = update.maxJobs ?: current.maxJobs
        if (maxJobs !in AppLimits.MIN_CRON_MAX_JOBS..AppLimits.MAX_CRON_MAX_JOBS) {
            throw IllegalArgumentException(
                "Cron max jobs must be between ${AppLimits.MIN_CRON_MAX_JOBS} and ${AppLimits.MAX_CRON_MAX_JOBS}"
            )
        }
        val config = CronConfig(
            enabled = update.enabled ?: current.enabled,
            minEveryMs = minEveryMs,
            maxJobs = maxJobs
        )
        configStore.saveCronConfig(config)
        reloadAutomationViaActiveRuntime()
        _uiState.update {
            it.copy(
                settingsCronEnabled = config.enabled,
                settingsCronMinEveryMs = config.minEveryMs.toString(),
                settingsCronMaxJobs = config.maxJobs.toString()
            )
        }
        return config
    }

    private suspend fun setCronEnabledFromTool(enabled: Boolean) {
        persistCronSettings(com.lgclaw.tools.CronConfigUpdate(enabled = enabled))
    }

    private fun applyHeartbeatRuntimeConfig(config: HeartbeatConfig) {
        reloadAutomationViaActiveRuntime()
    }

    private fun applyGatewayRuntimeConfig(config: ChannelsConfig) {
        val app = getApplication<Application>()
        if (shouldDelegateRemoteGatewayToAlwaysOnService()) {
            RuntimeController.stop()
            AlwaysOnModeController.startService(app)
            AlwaysOnModeController.reloadGateway()
            return
        }
        AlwaysOnModeController.stopService(app)
        if (RuntimeController.status.value.running) {
            RuntimeController.reloadGateway(app)
        } else {
            RuntimeController.start(app)
        }
    }

    private fun applyMcpRuntimeConfig(config: McpHttpConfig) {
        reloadMcpViaActiveRuntime(config)
    }

    private fun validateMcpEndpointUrl(url: String) {
        if (url.isBlank()) {
            throw IllegalArgumentException("MCP server URL is required when MCP is enabled")
        }
        val parsed = url.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("MCP server URL is invalid")
        val scheme = parsed.scheme.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("MCP server URL must use http or https")
        }
        if (scheme == "http" && !isLocalMcpHost(parsed.host)) {
            throw IllegalArgumentException("Use HTTPS for non-local MCP endpoints")
        }
    }

    private fun buildNormalizedMcpServers(state: ChatUiState): List<McpHttpServerConfig> {
        return state.settingsMcpServers.mapIndexedNotNull { index, item ->
            val name = item.serverName.trim().ifBlank { AppLimits.DEFAULT_MCP_HTTP_SERVER_NAME }
            val url = item.serverUrl.trim()
            val token = item.authToken.trim()
            val timeout = item.toolTimeoutSeconds.trim().toIntOrNull()
                ?: throw IllegalArgumentException("MCP server #${index + 1} timeout must be a number")
            if (timeout !in AppLimits.MIN_MCP_HTTP_TOOL_TIMEOUT_SECONDS..AppLimits.MAX_MCP_HTTP_TOOL_TIMEOUT_SECONDS) {
                throw IllegalArgumentException(
                    "MCP server #${index + 1} timeout must be between ${AppLimits.MIN_MCP_HTTP_TOOL_TIMEOUT_SECONDS} and ${AppLimits.MAX_MCP_HTTP_TOOL_TIMEOUT_SECONDS} seconds"
                )
            }
            val looksEmpty = url.isBlank() && token.isBlank() && item.serverName.trim().isBlank()
            if (looksEmpty) return@mapIndexedNotNull null
            if (url.isBlank()) {
                throw IllegalArgumentException("MCP server #${index + 1} URL is required")
            }
            validateMcpEndpointUrl(url)
            McpHttpServerConfig(
                id = item.id.ifBlank { "mcp_${index + 1}" },
                serverName = name,
                serverUrl = url,
                authToken = token,
                toolTimeoutSeconds = timeout
            )
        }
    }

    private fun CronJob.toUiCronJob(): UiCronJob {
        return UiCronJob(
            id = id,
            name = name,
            enabled = enabled,
            schedule = when (schedule.kind) {
                "every" -> "every ${schedule.everyMs?.div(1000L) ?: 0L}s"
                "at" -> "at ${schedule.atMs?.let(::formatTimeMs).orEmpty()}"
                "cron" -> schedule.expr ?: "cron"
                else -> schedule.kind
            },
            nextRunAt = state.nextRunAtMs?.let(::formatTimeMs),
            lastStatus = state.lastStatus,
            lastError = state.lastError
        )
    }

    private fun formatTimeMs(value: Long): String {
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(value))
        }.getOrElse { value.toString() }
    }

    private fun normalizeDiscordChannelId(raw: String): String {
        return SessionChannelBindingRules.normalizeDiscordChannelId(raw)
    }

    private fun normalizeDiscordResponseMode(raw: String): String {
        return SessionChannelBindingRules.normalizeDiscordResponseMode(raw)
    }

    private fun normalizeSlackChannelId(raw: String): String {
        return SessionChannelBindingRules.normalizeSlackChannelId(raw)
    }

    private fun normalizeSlackResponseMode(raw: String): String {
        return SessionChannelBindingRules.normalizeSlackResponseMode(raw)
    }

    private fun normalizeFeishuResponseMode(raw: String): String {
        return SessionChannelBindingRules.normalizeFeishuResponseMode(raw)
    }

    private fun normalizeFeishuTargetId(raw: String): String {
        return SessionChannelBindingRules.normalizeFeishuTargetId(raw)
    }

    private fun normalizeWeComTargetId(raw: String): String {
        return SessionChannelBindingRules.normalizeWeComTargetId(raw)
    }

    private fun normalizeEmailAddress(raw: String): String {
        return SessionChannelBindingRules.normalizeEmailAddress(raw)
    }

    private fun parseAllowedUserIds(raw: String): List<String> {
        return SessionChannelBindingRules.parseAllowedIdentifiers(raw)
    }

    private fun isDiscordSnowflake(value: String): Boolean {
        return SessionChannelBindingRules.isDiscordSnowflake(value)
    }

    private fun isSlackChannelId(value: String): Boolean {
        return SessionChannelBindingRules.isSlackChannelId(value)
    }

    private fun isFeishuTargetId(value: String): Boolean {
        return SessionChannelBindingRules.isFeishuTargetId(value)
    }

    private fun isEmailAddress(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches()
    }

    private fun isLocalMcpHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host == "127.0.0.1") return true
        if (host.startsWith("10.")) return true
        if (host.startsWith("192.168.")) return true
        if (host.startsWith("172.")) {
            val second = host.split(".").getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        return false
    }

    private fun runtimeToolArgsPreviewMaxChars(): Int {
        return configStore.getConfig().toolArgsPreviewMaxChars.coerceIn(
            AppLimits.MIN_TOOL_ARGS_PREVIEW_MAX_CHARS,
            AppLimits.MAX_TOOL_ARGS_PREVIEW_MAX_CHARS
        )
    }

    private fun readHeartbeatDoc(): String {
        heartbeatDocFile.parentFile?.mkdirs()
        if (!heartbeatDocFile.exists()) {
            heartbeatDocFile.writeText(
                templateStore.loadTemplate(HeartbeatDoc.FILE_NAME).orEmpty(),
                Charsets.UTF_8
            )
        }
        return runCatching {
            heartbeatDocFile.readText(Charsets.UTF_8)
        }.getOrDefault(templateStore.loadTemplate(HeartbeatDoc.FILE_NAME).orEmpty())
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_MEDIA_ATTACHMENTS_PER_MESSAGE = 4
        private const val MAX_VISION_ATTACHMENT_BYTES = 10L * 1024L * 1024L
        private const val MAX_VISION_IMAGE_SIDE = 2048
        private val VISION_DIRECT_MIME_TYPES = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")
        private const val FEISHU_DISCOVERY_STARTUP_RETRIES = 8
        private const val FEISHU_DISCOVERY_STARTUP_RETRY_DELAY_MS = 350L
        private const val WECOM_DISCOVERY_STARTUP_RETRIES = 8
        private const val WECOM_DISCOVERY_STARTUP_RETRY_DELAY_MS = 350L

        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ChatViewModel(application) as T
                }
            }
        }
    }
}

private data class FeishuDiscoveryResult(
    val snapshots: Map<String, com.lgclaw.channels.FeishuGatewaySnapshot>,
    val candidates: List<UiFeishuChatCandidate>
)

private data class UiMcpServerRuntimeStatus(
    val status: String,
    val usable: Boolean = status.equals("Connected", ignoreCase = true),
    val detail: String = "",
    val toolCount: Int = 0,
    val toolNames: List<String> = emptyList()
)

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class EmailCredentialKey(
    val consentGranted: Boolean,
    val imapHost: String,
    val imapPort: Int,
    val imapUsername: String,
    val imapPassword: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String,
    val smtpPassword: String,
    val fromAddress: String,
    val autoReplyEnabled: Boolean
)



















