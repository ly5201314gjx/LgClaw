package com.lgclaw.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.DownloadManager
import android.bluetooth.BluetoothAdapter
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.graphics.Typeface
import android.view.WindowManager
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lgclaw.R
import com.lgclaw.attachments.AttachmentBridge
import com.lgclaw.config.AppLimits
import com.lgclaw.config.AppSession
import com.lgclaw.providers.ProviderCatalog
import com.lgclaw.trace.TraceNoteExtractor
import com.lgclaw.tools.AndroidUserActionBridge
import com.lgclaw.tools.AndroidUserActionRequester
import com.lgclaw.tools.hasAllFilesAccess
import com.lgclaw.tools.hasPermission
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isChinese = state.settingsUseChinese
    if (!state.onboardingCompleted) {
        var onboardingStepName by rememberSaveable { mutableStateOf(OnboardingStep.Language.name) }
        val onboardingStep = runCatching { OnboardingStep.valueOf(onboardingStepName) }
            .getOrDefault(OnboardingStep.Language)
        FirstRunOnboardingScreen(
            state = state,
            step = onboardingStep,
            onStepChange = { onboardingStepName = it.name },
            onLanguageSelected = vm::setUiLanguage,
            onProviderChange = vm::onSettingsProviderChanged,
            onProviderCustomNameChange = vm::onSettingsProviderCustomNameChanged,
            onBaseUrlChange = vm::onSettingsBaseUrlChanged,
            onModelChange = vm::onSettingsModelChanged,
            onFetchProviderModels = vm::fetchProviderModels,
            onSetModelEquipped = vm::setModelEquipped,
            onApiKeyChange = vm::onSettingsApiKeyChanged,
            onUserDisplayNameChange = vm::onOnboardingUserDisplayNameChanged,
            onAgentDisplayNameChange = vm::onOnboardingAgentDisplayNameChanged,
            onTestProvider = vm::testProviderSettings,
            onComplete = vm::completeOnboarding
        )
        return
    }
    val settingsSnackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    val hostActivity = context as? ComponentActivity
    var pendingPermissionResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var pendingBluetoothEnableResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var pendingUserConfirmResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var permissionsRefreshNonce by rememberSaveable { mutableStateOf(0) }
    var pendingUserConfirmTitle by remember { mutableStateOf("") }
    var pendingUserConfirmMessage by remember { mutableStateOf("") }
    var pendingUserConfirmLabel by remember {
        mutableStateOf(localizedText("Continue", useChinese = isChinese))
    }
    var pendingUserCancelLabel by remember {
        mutableStateOf(localizedText("Cancel", useChinese = isChinese))
    }
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val grantedAll = result.values.all { it }
        val callback = pendingPermissionResult
        pendingPermissionResult = null
        callback?.invoke(grantedAll)
        permissionsRefreshNonce += 1
    }
    val requestEnableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val enabled = result.resultCode == Activity.RESULT_OK
        pendingBluetoothEnableResult?.invoke(enabled)
        pendingBluetoothEnableResult = null
    }
    var avatarPickerTarget by rememberSaveable { mutableStateOf("") }
    var showAvatarPickerSheet by rememberSaveable { mutableStateOf(false) }
    var pendingAvatarSourceUri by remember { mutableStateOf<Uri?>(null) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.attachFilesToInput(uris)
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        if (uris.isNotEmpty()) vm.attachImagesToInput(uris)
    }
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingAvatarSourceUri = uri
        }
    }
    val chatBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> vm.setChatBackgroundFromUri(uri) }
    val drawerBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> vm.setDrawerBackgroundFromUri(uri) }
    val customFontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> vm.setCustomFontFromUri(uri) }
    val requestOverlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        vm.refreshTerminalOverlayPermission()
    }
    val displayedAssistantText = remember { mutableStateMapOf<Long, String>() }
    val seenMessageIds = remember { mutableStateMapOf<Long, Boolean>() }
    var initializedMessages by rememberSaveable { mutableStateOf(false) }
    var generationAnchorMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var mainSurfaceName by rememberSaveable { mutableStateOf(MainSurface.Chat.name) }
    var revealApiKey by rememberSaveable { mutableStateOf(false) }
    var showChatSearch by rememberSaveable { mutableStateOf(false) }
    var modelMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showCompressionConfirm by rememberSaveable { mutableStateOf(false) }
    var showCompressionCancelConfirm by rememberSaveable { mutableStateOf(false) }
    var showTerminalSheet by rememberSaveable { mutableStateOf(false) }
    var showTerminalMiniOverlay by rememberSaveable { mutableStateOf(true) }
    var terminalMiniOverlaySuppressed by rememberSaveable { mutableStateOf(false) }
    var showHeartbeatEditor by rememberSaveable { mutableStateOf(false) }
    var roleCardMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showQuickRoleCardDialog by rememberSaveable { mutableStateOf(false) }
    var previewImageAttachment by remember { mutableStateOf<UiMediaAttachment?>(null) }
    var quickRoleName by rememberSaveable { mutableStateOf("") }
    var quickRolePersona by rememberSaveable { mutableStateOf("") }
    var messageActionTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedMessageIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var pendingDeleteMessageIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var showCreateSessionDialog by rememberSaveable { mutableStateOf(false) }
    var createSessionName by rememberSaveable { mutableStateOf("") }
    var pendingRenameSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameSessionName by rememberSaveable { mutableStateOf("") }
    var pendingDeleteSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionSettingsSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionSettingsPageName by rememberSaveable { mutableStateOf(SessionSettingsPage.Menu.name) }
    var bindingEnabledDraft by rememberSaveable { mutableStateOf(true) }
    var bindingChannelDraft by rememberSaveable { mutableStateOf("") }
    var bindingChatIdDraft by rememberSaveable { mutableStateOf("") }
    var bindingTelegramBotTokenDraft by rememberSaveable { mutableStateOf("") }
    var bindingTelegramAllowedChatIdDraft by rememberSaveable { mutableStateOf("") }
    var bindingDiscordBotTokenDraft by rememberSaveable { mutableStateOf("") }
    var bindingDiscordResponseModeDraft by rememberSaveable { mutableStateOf("mention") }
    var bindingDiscordAllowedUserIdsDraft by rememberSaveable { mutableStateOf("") }
    var bindingSlackBotTokenDraft by rememberSaveable { mutableStateOf("") }
    var bindingSlackAppTokenDraft by rememberSaveable { mutableStateOf("") }
    var bindingSlackResponseModeDraft by rememberSaveable { mutableStateOf("mention") }
    var bindingSlackAllowedUserIdsDraft by rememberSaveable { mutableStateOf("") }
    var bindingFeishuAppIdDraft by rememberSaveable { mutableStateOf("") }
    var bindingFeishuAppSecretDraft by rememberSaveable { mutableStateOf("") }
    var bindingFeishuEncryptKeyDraft by rememberSaveable { mutableStateOf("") }
    var bindingFeishuVerificationTokenDraft by rememberSaveable { mutableStateOf("") }
    var bindingFeishuResponseModeDraft by rememberSaveable { mutableStateOf("mention") }
    var bindingFeishuAllowedOpenIdsDraft by rememberSaveable { mutableStateOf("") }
    var bindingEmailConsentGrantedDraft by rememberSaveable { mutableStateOf(true) }
    var bindingEmailImapHostDraft by rememberSaveable { mutableStateOf("imap.gmail.com") }
    var bindingEmailImapPortDraft by rememberSaveable { mutableStateOf("993") }
    var bindingEmailImapUsernameDraft by rememberSaveable { mutableStateOf("") }
    var bindingEmailImapPasswordDraft by rememberSaveable { mutableStateOf("") }
    var bindingEmailSmtpHostDraft by rememberSaveable { mutableStateOf("smtp.gmail.com") }
    var bindingEmailSmtpPortDraft by rememberSaveable { mutableStateOf("587") }
    var bindingEmailSmtpUsernameDraft by rememberSaveable { mutableStateOf("") }
    var bindingEmailSmtpPasswordDraft by rememberSaveable { mutableStateOf("") }
    var bindingEmailFromAddressDraft by rememberSaveable { mutableStateOf("") }
    var bindingEmailAutoReplyEnabledDraft by rememberSaveable { mutableStateOf(true) }
    var bindingWeComBotIdDraft by rememberSaveable { mutableStateOf("") }
    var bindingWeComSecretDraft by rememberSaveable { mutableStateOf("") }
    var bindingWeComAllowedUserIdsDraft by rememberSaveable { mutableStateOf("") }
    var bindingChannelMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var bindingDiscordResponseModeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var bindingSlackResponseModeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var closeAfterDetectedBindingSave by rememberSaveable { mutableStateOf(false) }
    var telegramAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var discordAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var slackAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var feishuAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var emailAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var weComAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var settingsPageName by rememberSaveable { mutableStateOf(SettingsPanelPage.Home.name) }
    var visibleHistoryRounds by rememberSaveable { mutableStateOf(HISTORY_ROUNDS_PAGE_SIZE) }
    var hasInitialJumpToBottom by rememberSaveable { mutableStateOf(false) }
    var followLatest by rememberSaveable { mutableStateOf(true) }
    var isLoadingOlderHistory by rememberSaveable { mutableStateOf(false) }
    var olderHistoryLoadingStartedAtMs by rememberSaveable { mutableStateOf(0L) }
    var pendingHistoryRestore by remember { mutableStateOf<HistoryRestoreRequest?>(null) }
    val expandedToolMessages = remember { mutableStateMapOf<Long, Boolean>() }
    var selectedToolGroupStartId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedToolMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var traceCollapsed by rememberSaveable { mutableStateOf(false) }
    var selectedTraceId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewAudioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewAudioRef by rememberSaveable { mutableStateOf<String?>(null) }
    var previewAudioDurationMs by rememberSaveable { mutableStateOf(0) }
    var previewAudioPositionMs by rememberSaveable { mutableStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val mainSurface = runCatching { MainSurface.valueOf(mainSurfaceName) }
        .getOrDefault(MainSurface.Chat)
    val settingsPage = runCatching { SettingsPanelPage.valueOf(settingsPageName) }
        .getOrDefault(SettingsPanelPage.Home)
    val settingsPageTitle = settingsPage.title(isChinese)
    val settingsPageSubtitle = settingsPage.subtitle(isChinese)
    val permissionsDashboard = remember(permissionsRefreshNonce, context.applicationContext) {
        readPermissionsDashboardState(context.applicationContext)
    }
    val sessionSettingsPage = runCatching { SessionSettingsPage.valueOf(sessionSettingsPageName) }
        .getOrDefault(SessionSettingsPage.Menu)
    val dismissSessionSettings = {
        bindingChannelMenuExpanded = false
        bindingDiscordResponseModeMenuExpanded = false
        bindingSlackResponseModeMenuExpanded = false
        vm.clearTelegramChatDiscovery()
        vm.clearFeishuChatDiscovery()
        vm.clearEmailSenderDiscovery()
        vm.clearWeComChatDiscovery()
        closeAfterDetectedBindingSave = false
        telegramAdvancedExpanded = false
        discordAdvancedExpanded = false
        slackAdvancedExpanded = false
        feishuAdvancedExpanded = false
        emailAdvancedExpanded = false
        weComAdvancedExpanded = false
        sessionSettingsSessionId = null
        sessionSettingsPageName = SessionSettingsPage.Menu.name
    }
    val openSessionSettingsForSession: (UiSessionSummary) -> Unit = { session ->
        val draft = vm.getSessionChannelDraft(session.id)
        bindingEnabledDraft = draft.enabled
        bindingChannelDraft = draft.channel
        bindingChatIdDraft = draft.chatId
        bindingTelegramBotTokenDraft = draft.telegramBotToken
        bindingTelegramAllowedChatIdDraft = draft.telegramAllowedChatId
        bindingDiscordBotTokenDraft = draft.discordBotToken
        bindingSlackBotTokenDraft = draft.slackBotToken
        bindingSlackAppTokenDraft = draft.slackAppToken
        bindingFeishuAppIdDraft = draft.feishuAppId
        bindingFeishuAppSecretDraft = draft.feishuAppSecret
        bindingFeishuEncryptKeyDraft = draft.feishuEncryptKey
        bindingFeishuVerificationTokenDraft = draft.feishuVerificationToken
        bindingFeishuResponseModeDraft = draft.feishuResponseMode
        bindingEmailConsentGrantedDraft = true
        bindingEmailImapHostDraft = draft.emailImapHost
        bindingEmailImapPortDraft = draft.emailImapPort
        bindingEmailImapUsernameDraft = draft.emailImapUsername
        bindingEmailImapPasswordDraft = draft.emailImapPassword
        bindingEmailSmtpHostDraft = draft.emailSmtpHost
        bindingEmailSmtpPortDraft = draft.emailSmtpPort
        bindingEmailSmtpUsernameDraft = draft.emailSmtpUsername
        bindingEmailSmtpPasswordDraft = draft.emailSmtpPassword
        bindingEmailFromAddressDraft = draft.emailFromAddress
        bindingEmailAutoReplyEnabledDraft = draft.emailAutoReplyEnabled
        bindingWeComBotIdDraft = draft.wecomBotId
        bindingWeComSecretDraft = draft.wecomSecret
        bindingChannelMenuExpanded = false
        bindingDiscordResponseModeMenuExpanded = false
        bindingSlackResponseModeMenuExpanded = false
        vm.clearTelegramChatDiscovery()
        vm.clearFeishuChatDiscovery()
        vm.clearEmailSenderDiscovery()
        vm.clearWeComChatDiscovery()
        bindingDiscordResponseModeDraft = draft.discordResponseMode
        bindingDiscordAllowedUserIdsDraft = draft.discordAllowedUserIds
        bindingSlackResponseModeDraft = draft.slackResponseMode
        bindingSlackAllowedUserIdsDraft = draft.slackAllowedUserIds
        bindingFeishuAllowedOpenIdsDraft = draft.feishuAllowedOpenIds
        bindingWeComAllowedUserIdsDraft = draft.wecomAllowedUserIds
        sessionSettingsPageName = SessionSettingsPage.Menu.name
        sessionSettingsSessionId = session.id
    }
    val canHandleBack = remember(
        pendingUserConfirmResult,
        showCreateSessionDialog,
        pendingRenameSessionId,
        pendingDeleteSessionId,
        sessionSettingsSessionId,
        sessionSettingsPage,
        showHeartbeatEditor,
        drawerState.currentValue,
        mainSurface,
        settingsPage
    ) {
        pendingUserConfirmResult != null ||
            showCreateSessionDialog ||
            pendingRenameSessionId != null ||
            pendingDeleteSessionId != null ||
            sessionSettingsSessionId != null ||
            showHeartbeatEditor ||
            drawerState.currentValue == DrawerValue.Open ||
            mainSurface != MainSurface.Chat
    }
    BackHandler(enabled = canHandleBack) {
        when {
            pendingUserConfirmResult != null -> {
                val cb = pendingUserConfirmResult
                pendingUserConfirmResult = null
                cb?.invoke(false)
            }
            showCreateSessionDialog -> {
                createSessionName = ""
                showCreateSessionDialog = false
            }
            pendingRenameSessionId != null -> pendingRenameSessionId = null
            pendingDeleteSessionId != null -> pendingDeleteSessionId = null
            sessionSettingsSessionId != null && sessionSettingsPage != SessionSettingsPage.Menu -> {
                bindingChannelMenuExpanded = false
                bindingDiscordResponseModeMenuExpanded = false
                bindingSlackResponseModeMenuExpanded = false
                sessionSettingsPageName = SessionSettingsPage.Menu.name
            }
            sessionSettingsSessionId != null -> dismissSessionSettings()
            showHeartbeatEditor -> showHeartbeatEditor = false
            drawerState.currentValue == DrawerValue.Open -> {
                uiScope.launch { drawerState.close() }
            }
            mainSurface != MainSurface.Chat && settingsPage != SettingsPanelPage.Home -> {
                settingsPageName = SettingsPanelPage.Home.name
            }
            mainSurface != MainSurface.Chat -> {
                uiScope.launch {
                    mainSurfaceName = MainSurface.Chat.name
                    runCatching { drawerState.snapTo(DrawerValue.Open) }
                        .onFailure { drawerState.open() }
                }
            }
        }
    }

    DisposableEffect(hostActivity, state.alwaysOnEnabled, state.alwaysOnKeepScreenAwake) {
        val activity = hostActivity
        if (activity != null) {
            if (state.alwaysOnEnabled && state.alwaysOnKeepScreenAwake) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsRefreshNonce += 1
                vm.refreshTerminalOverlayPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(mainSurface, settingsPage) {
        if (mainSurface != MainSurface.Settings || settingsPage != SettingsPanelPage.AlwaysOn) return@LaunchedEffect
        while (
            mainSurfaceName == MainSurface.Settings.name &&
            settingsPageName == SettingsPanelPage.AlwaysOn.name
        ) {
            vm.refreshAlwaysOnDiagnostics()
            delay(5_000L)
        }
    }

    fun launchRuntimePermissionRequest(
        permissions: Array<String>,
        onResult: (Boolean) -> Unit = {}
    ) {
        val normalized = permissions.map { it.trim() }.filter { it.isNotBlank() }.distinct().toTypedArray()
        if (normalized.isEmpty()) {
            onResult(true)
            return
        }
        if (hostActivity == null) {
            onResult(false)
            return
        }
        if (pendingPermissionResult != null) {
            onResult(false)
            return
        }
        pendingPermissionResult = onResult
        runCatching { requestPermissionsLauncher.launch(normalized) }
            .onFailure {
                pendingPermissionResult = null
                permissionsRefreshNonce += 1
                onResult(false)
            }
    }

    val actionRequester = remember(hostActivity) {
        object : AndroidUserActionRequester {
            override fun requestPermissions(
                permissions: Array<String>,
                onResult: (grantedAll: Boolean) -> Unit
            ) {
                launchRuntimePermissionRequest(permissions, onResult)
            }

            override fun requestEnableBluetooth(onResult: (enabled: Boolean) -> Unit) {
                if (hostActivity == null) {
                    onResult(false)
                    return
                }
                if (pendingBluetoothEnableResult != null) {
                    onResult(false)
                    return
                }
                pendingBluetoothEnableResult = onResult
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                runCatching { requestEnableBluetoothLauncher.launch(intent) }
                    .onFailure {
                        pendingBluetoothEnableResult = null
                        onResult(false)
                    }
            }

            override fun openBluetoothSettings(onResult: (opened: Boolean) -> Unit) {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onSuccess { onResult(true) }
                    .onFailure { onResult(false) }
            }

            override fun requestUserConfirmation(
                title: String,
                message: String,
                confirmLabel: String,
                cancelLabel: String,
                onResult: (confirmed: Boolean) -> Unit
            ) {
                if (pendingUserConfirmResult != null) {
                    onResult(false)
                    return
                }
                pendingUserConfirmTitle = title
                pendingUserConfirmMessage = message
                pendingUserConfirmLabel = confirmLabel.ifBlank {
                    localizedText("Continue", useChinese = isChinese)
                }
                pendingUserCancelLabel = cancelLabel.ifBlank {
                    localizedText("Cancel", useChinese = isChinese)
                }
                pendingUserConfirmResult = onResult
            }
        }
    }

    DisposableEffect(actionRequester) {
        AndroidUserActionBridge.register(actionRequester)
        onDispose {
            AndroidUserActionBridge.unregister(actionRequester)
            pendingPermissionResult?.invoke(false)
            pendingPermissionResult = null
            pendingBluetoothEnableResult?.invoke(false)
            pendingBluetoothEnableResult = null
            pendingUserConfirmResult?.invoke(false)
            pendingUserConfirmResult = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { previewAudioPlayer?.stop() }
            runCatching { previewAudioPlayer?.release() }
            previewAudioPlayer = null
            previewAudioRef = null
            previewAudioDurationMs = 0
            previewAudioPositionMs = 0
        }
    }

    if (pendingUserConfirmResult != null) {
        PendingUserConfirmationDialog(
            title = pendingUserConfirmTitle,
            message = pendingUserConfirmMessage,
            confirmLabel = pendingUserConfirmLabel,
            cancelLabel = pendingUserCancelLabel,
            onConfirm = {
                val cb = pendingUserConfirmResult
                pendingUserConfirmResult = null
                cb?.invoke(true)
            },
            onCancel = {
                val cb = pendingUserConfirmResult
                pendingUserConfirmResult = null
                cb?.invoke(false)
            }
        )
    }

    if (showCreateSessionDialog) {
        CreateSessionDialog(
            sessionName = createSessionName,
            onSessionNameChange = { createSessionName = it },
            onCreate = {
                vm.createSession(createSessionName)
                createSessionName = ""
                showCreateSessionDialog = false
                uiScope.launch { drawerState.close() }
            },
            onDismiss = {
                createSessionName = ""
                showCreateSessionDialog = false
            }
        )
    }

    pendingRenameSessionId?.let { sessionId ->
        val item = state.sessions.firstOrNull { it.id == sessionId && !it.isLocal }
        if (item != null) {
            RenameSessionDialog(
                sessionName = renameSessionName,
                onSessionNameChange = { renameSessionName = it },
                onSave = {
                    vm.renameSession(sessionId, renameSessionName)
                    pendingRenameSessionId = null
                    renameSessionName = ""
                },
                onDismiss = {
                    pendingRenameSessionId = null
                    renameSessionName = ""
                }
            )
        } else {
            pendingRenameSessionId = null
            renameSessionName = ""
        }
    }

    pendingDeleteSessionId?.let { sessionId ->
        val item = state.sessions.firstOrNull { it.id == sessionId }
        if (item != null) {
            DeleteSessionDialog(
                title = uiLabel("Delete Session"),
                message = irreversibleConfirmMessage(
                    prompt = uiLabel("Delete session '%s'?").format(item.title),
                    useChinese = isChinese
                ),
                onDelete = {
                    vm.deleteSession(sessionId)
                    pendingDeleteSessionId = null
                },
                onDismiss = { pendingDeleteSessionId = null }
            )
        } else {
            pendingDeleteSessionId = null
        }
    }

    sessionSettingsSessionId?.let { sessionId ->
        val item = state.sessions.firstOrNull { it.id == sessionId }
        if (item != null) {
            val normalizedChannel = bindingChannelDraft.trim().lowercase()
            val channelLabel = when (normalizedChannel) {
                "telegram" -> uiLabel("Telegram")
                "discord" -> uiLabel("Discord")
                "slack" -> uiLabel("Slack")
                "feishu" -> uiLabel("Feishu")
                "email" -> uiLabel("Email")
                "wecom" -> uiLabel("WeCom")
                else -> uiLabel("None")
            }
            val connected = state.settingsConnectedChannels.firstOrNull { it.sessionId == sessionId }
            val selectedTargetDisplay = when (normalizedChannel) {
                "telegram" -> state.sessionBindingTelegramCandidates
                    .firstOrNull { it.chatId.trim() == bindingChatIdDraft.trim() }
                    ?.let { candidate ->
                        if (candidate.title.isBlank() || candidate.title == candidate.chatId) {
                            candidate.chatId
                        } else {
                            "${candidate.title} · ${candidate.chatId}"
                        }
                    }
                "feishu" -> state.sessionBindingFeishuCandidates
                    .firstOrNull { it.chatId.trim() == bindingChatIdDraft.trim() }
                    ?.let { candidate ->
                        if (candidate.title.isBlank() || candidate.title == candidate.chatId) {
                            candidate.chatId
                        } else {
                            "${candidate.title} · ${candidate.chatId}"
                        }
                    }
                "email" -> state.sessionBindingEmailCandidates
                    .firstOrNull { it.email.trim().equals(bindingChatIdDraft.trim(), ignoreCase = true) }
                    ?.email
                "wecom" -> state.sessionBindingWeComCandidates
                    .firstOrNull { it.chatId.trim() == bindingChatIdDraft.trim() }
                    ?.let { candidate ->
                        if (candidate.title.isBlank() || candidate.title == candidate.chatId) {
                            candidate.chatId
                        } else {
                            "${candidate.title} · ${candidate.chatId}"
                        }
                    }
                else -> null
            }
            val hasPendingDetection = when (normalizedChannel) {
                "feishu" -> bindingFeishuAppIdDraft.isNotBlank() && bindingFeishuAppSecretDraft.isNotBlank() && bindingChatIdDraft.isBlank()
                "email" -> bindingEmailImapHostDraft.isNotBlank() &&
                    bindingEmailImapUsernameDraft.isNotBlank() &&
                    bindingEmailSmtpHostDraft.isNotBlank() &&
                    bindingEmailSmtpUsernameDraft.isNotBlank() &&
                    bindingChatIdDraft.isBlank()
                "wecom" -> bindingWeComBotIdDraft.isNotBlank() && bindingWeComSecretDraft.isNotBlank() && bindingChatIdDraft.isBlank()
                else -> false
            }
            val targetLabel = when {
                bindingChannelDraft.isBlank() -> uiLabel("This session stays local.")
                selectedTargetDisplay != null -> selectedTargetDisplay
                bindingChatIdDraft.isNotBlank() -> bindingChatIdDraft.trim()
                hasPendingDetection -> uiLabel("Waiting for detection")
                else -> tr("Not set", "")
            }
            val activeChannel = item.boundChannel.trim().lowercase()
            val activeChannelLabel = when (activeChannel) {
                "telegram" -> uiLabel("Telegram")
                "discord" -> uiLabel("Discord")
                "slack" -> uiLabel("Slack")
                "feishu" -> uiLabel("Feishu")
                "email" -> uiLabel("Email")
                "wecom" -> uiLabel("WeCom")
                else -> uiLabel("None")
            }
            val activeTargetLabel = when {
                activeChannel.isBlank() -> uiLabel("This session stays local.")
                connected?.chatId?.trim()?.isNotBlank() == true -> connected.chatId.trim()
                item.boundChatId.trim().isNotBlank() -> item.boundChatId.trim()
                else -> tr("Not set", "")
            }
            val activeEnabledLabel = when {
                activeChannel.isBlank() -> tr("Local only", "")
                item.boundEnabled -> tr("On", "")
                else -> tr("Off", "")
            }
            val activeConnectedLabel = when {
                activeChannel.isBlank() -> tr("Local only", "")
                connected?.status?.equals("Connected", ignoreCase = true) == true -> tr("Yes", "")
                else -> tr("No", "")
            }
            val sessionSettingsScrollState = rememberScrollState()
            AlertDialog(
                onDismissRequest = dismissSessionSettings,
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                title = {
                    Text(
                        text = when (sessionSettingsPage) {
                            SessionSettingsPage.Menu -> tr("Session Settings", "")
                            SessionSettingsPage.Configure -> tr("Channels", "")
                            SessionSettingsPage.Diagnostics -> tr("Connection Diagnostics", "")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(sessionSettingsScrollState),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (sessionSettingsPage == SessionSettingsPage.Menu) {
                                SettingsSectionCard(
                                    title = tr("Connection", ""),
                                    subtitle = tr("Current routing and status.", ""),
                                    actions = {
                                        SettingsSectionIconButton(
                                            icon = Icons.Rounded.Refresh,
                                            contentDescription = tr("Refresh connection status", ""),
                                            onClick = vm::refreshSessionConnectionStatus,
                                            containerSize = 30.dp,
                                            iconSize = 12.dp
                                        )
                                    }
                                ) {
                                    SettingsValueRow(tr("Channel", ""), activeChannelLabel.ifBlank { tr("Not selected", "") })
                                    SettingsValueRow(tr("Target", ""), activeTargetLabel)
                                    SettingsValueRow(tr("Enabled", ""), activeEnabledLabel)
                                    SettingsValueRow(tr("Connected", ""), activeConnectedLabel)
                                }
                                SettingsSectionCard(
                                    title = tr("Configure", ""),
                                    subtitle = tr("Channel settings.", ""),
                                    actions = {
                                        SettingsSectionIconButton(
                                            icon = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = tr("Open channel settings", ""),
                                            onClick = { sessionSettingsPageName = SessionSettingsPage.Configure.name },
                                            rotateZ = 90f,
                                            containerSize = 30.dp,
                                            iconSize = 12.dp
                                        )
                                    }
                                ) {
                                    SettingsValueRow(tr("Channel", ""), channelLabel.ifBlank { tr("Not selected", "") })
                                }
                            } else if (sessionSettingsPage == SessionSettingsPage.Diagnostics) {
                                SettingsSectionCard(
                                    title = tr("Connection", ""),
                                    subtitle = tr("Current routing and status.", ""),
                                    actions = {
                                        SettingsSectionIconButton(
                                            icon = Icons.Rounded.Refresh,
                                            contentDescription = tr("Refresh connection status", ""),
                                            onClick = vm::refreshSessionConnectionStatus,
                                            containerSize = 30.dp,
                                            iconSize = 12.dp
                                        )
                                    }
                                ) {
                                    SettingsValueRow(tr("Channel", ""), activeChannelLabel.ifBlank { tr("Not selected", "") })
                                    SettingsValueRow(tr("Target", ""), activeTargetLabel)
                                    SettingsValueRow(tr("Enabled", ""), activeEnabledLabel)
                                    SettingsValueRow(tr("Connected", ""), activeConnectedLabel)
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = uiLabel("Current Route"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when {
                                            bindingChannelDraft.isBlank() -> uiLabel("Not connected")
                                            targetLabel != tr("Not set", "") &&
                                                targetLabel != uiLabel("Waiting for detection") ->
                                                "$channelLabel: $targetLabel"
                                            bindingChannelDraft.equals("feishu", ignoreCase = true) &&
                                                bindingFeishuAppIdDraft.isNotBlank() &&
                                                bindingFeishuAppSecretDraft.isNotBlank() ->
                                                "${uiLabel("Feishu")}: ${uiLabel("Pending detection")}"
                                            bindingChannelDraft.equals("email", ignoreCase = true) &&
                                                bindingEmailConsentGrantedDraft &&
                                                bindingEmailImapHostDraft.isNotBlank() &&
                                                bindingEmailImapUsernameDraft.isNotBlank() &&
                                                bindingEmailImapPasswordDraft.isNotBlank() &&
                                                bindingEmailSmtpHostDraft.isNotBlank() &&
                                                bindingEmailSmtpUsernameDraft.isNotBlank() &&
                                                bindingEmailSmtpPasswordDraft.isNotBlank() ->
                                                "${uiLabel("Email")}: ${uiLabel("Pending detection")}"
                                            bindingChannelDraft.equals("wecom", ignoreCase = true) &&
                                                bindingWeComBotIdDraft.isNotBlank() &&
                                                bindingWeComSecretDraft.isNotBlank() ->
                                                "${uiLabel("WeCom")}: ${uiLabel("Pending detection")}"
                                            else -> uiLabel("Not connected")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                ExposedDropdownMenuBox(
                                    expanded = bindingChannelMenuExpanded,
                                    onExpandedChange = { bindingChannelMenuExpanded = it }
                                ) {
                                    SettingsSelectField(
                                        value = channelLabel,
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth(),
                                        label = "Select Channel",
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bindingChannelMenuExpanded)
                                        }
                                    )
                                    ExposedDropdownMenu(
                                        expanded = bindingChannelMenuExpanded,
                                        onDismissRequest = { bindingChannelMenuExpanded = false },
                                        shape = settingsTextFieldShape(),
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                        border = settingsDropdownMenuBorder()
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "None")
                                            },
                                            onClick = {
                                                closeAfterDetectedBindingSave = false
                                                bindingChannelDraft = ""
                                                bindingChatIdDraft = ""
                                                bindingTelegramBotTokenDraft = ""
                                                bindingTelegramAllowedChatIdDraft = ""
                                                bindingDiscordBotTokenDraft = ""
                                                bindingEnabledDraft = true
                                                bindingDiscordResponseModeDraft = "mention"
                                                bindingDiscordAllowedUserIdsDraft = ""
                                                bindingSlackBotTokenDraft = ""
                                                bindingSlackAppTokenDraft = ""
                                                bindingSlackResponseModeDraft = "mention"
                                                bindingSlackAllowedUserIdsDraft = ""
                                                bindingFeishuAppIdDraft = ""
                                                bindingFeishuAppSecretDraft = ""
                                                bindingFeishuEncryptKeyDraft = ""
                                                bindingFeishuVerificationTokenDraft = ""
                                                bindingFeishuResponseModeDraft = "mention"
                                                bindingFeishuAllowedOpenIdsDraft = ""
                                                bindingEmailConsentGrantedDraft = true
                                                bindingEmailImapHostDraft = "imap.gmail.com"
                                                bindingEmailImapPortDraft = "993"
                                                bindingEmailImapUsernameDraft = ""
                                                bindingEmailImapPasswordDraft = ""
                                                bindingEmailSmtpHostDraft = "smtp.gmail.com"
                                                bindingEmailSmtpPortDraft = "587"
                                                bindingEmailSmtpUsernameDraft = ""
                                                bindingEmailSmtpPasswordDraft = ""
                                                bindingEmailFromAddressDraft = ""
                                                bindingEmailAutoReplyEnabledDraft = true
                                                bindingWeComBotIdDraft = ""
                                                bindingWeComSecretDraft = ""
                                                bindingWeComAllowedUserIdsDraft = ""
                                                bindingSlackResponseModeMenuExpanded = false
                                                bindingChannelMenuExpanded = false
                                                vm.clearTelegramChatDiscovery()
                                                vm.clearFeishuChatDiscovery()
                                                vm.clearEmailSenderDiscovery()
                                                vm.clearWeComChatDiscovery()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "Telegram")
                                            },
                                            onClick = {
                                                closeAfterDetectedBindingSave = false
                                                bindingChannelDraft = "telegram"
                                                bindingDiscordResponseModeMenuExpanded = false
                                                bindingSlackResponseModeMenuExpanded = false
                                                bindingChannelMenuExpanded = false
                                                vm.clearFeishuChatDiscovery()
                                                vm.clearEmailSenderDiscovery()
                                                vm.clearWeComChatDiscovery()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "Discord")
                                            },
                                            onClick = {
                                                closeAfterDetectedBindingSave = false
                                                bindingChannelDraft = "discord"
                                                if (bindingDiscordResponseModeDraft.isBlank()) {
                                                    bindingDiscordResponseModeDraft = "mention"
                                                }
                                                bindingSlackResponseModeMenuExpanded = false
                                                bindingChannelMenuExpanded = false
                                                vm.clearTelegramChatDiscovery()
                                                vm.clearFeishuChatDiscovery()
                                                vm.clearEmailSenderDiscovery()
                                                vm.clearWeComChatDiscovery()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "Slack")
                                            },
                                            onClick = {
                                                closeAfterDetectedBindingSave = false
                                                bindingChannelDraft = "slack"
                                                if (bindingSlackResponseModeDraft.isBlank()) {
                                                    bindingSlackResponseModeDraft = "mention"
                                                }
                                                bindingDiscordResponseModeMenuExpanded = false
                                                bindingChannelMenuExpanded = false
                                                vm.clearTelegramChatDiscovery()
                                                vm.clearFeishuChatDiscovery()
                                                vm.clearEmailSenderDiscovery()
                                                vm.clearWeComChatDiscovery()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "Feishu")
                                            },
                                            onClick = {
                                                closeAfterDetectedBindingSave = false
                                                bindingChannelDraft = "feishu"
                                                if (bindingFeishuResponseModeDraft.isBlank()) {
                                                    bindingFeishuResponseModeDraft = "mention"
                                                }
                                                bindingDiscordResponseModeMenuExpanded = false
                                                bindingSlackResponseModeMenuExpanded = false
                                                bindingChannelMenuExpanded = false
                                                vm.clearTelegramChatDiscovery()
                                                vm.clearFeishuChatDiscovery()
                                                vm.clearEmailSenderDiscovery()
                                                vm.clearWeComChatDiscovery()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "Email")
                                            },
                                            onClick = {
                                                closeAfterDetectedBindingSave = false
                                                bindingChannelDraft = "email"
                                                bindingDiscordResponseModeMenuExpanded = false
                                                bindingSlackResponseModeMenuExpanded = false
                                                bindingChannelMenuExpanded = false
                                                vm.clearTelegramChatDiscovery()
                                                vm.clearFeishuChatDiscovery()
                                                vm.clearEmailSenderDiscovery()
                                                vm.clearWeComChatDiscovery()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "WeCom")
                                            },
                                            onClick = {
                                                closeAfterDetectedBindingSave = false
                                                bindingChannelDraft = "wecom"
                                                bindingDiscordResponseModeMenuExpanded = false
                                                bindingSlackResponseModeMenuExpanded = false
                                                bindingChannelMenuExpanded = false
                                                vm.clearTelegramChatDiscovery()
                                                vm.clearFeishuChatDiscovery()
                                                vm.clearEmailSenderDiscovery()
                                                vm.clearWeComChatDiscovery()
                                            }
                                        )
                                    }
                                }
                                if (sessionSettingsPage == SessionSettingsPage.Configure && normalizedChannel == "telegram") {
                            SessionSetupStepCard(
                                step = 1,
                                text = uiLabel("Open BotFather, send /newbot, then create a bot and copy its HTTP API token.")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SettingsActionButton(
                                        text = uiLabel("BotFather"),
                                        icon = Icons.Rounded.Description,
                                        onClick = { openExternalUrl(context, "https://t.me/BotFather") }
                                    )
                                    SettingsActionButton(
                                        text = uiLabel("Guide"),
                                        icon = Icons.Rounded.Description,
                                        onClick = { openExternalUrl(context, "https://core.telegram.org/bots#6-botfather") }
                                    )
                                }
                                SettingsInfoBlock(
                                    label = uiLabel("Token example"),
                                    value = "123456789:AAExampleBotTokenAbCdEfGhIjKlMnOpQrStUv"
                                )
                                SettingsTextField(
                                    value = bindingTelegramBotTokenDraft,
                                    onValueChange = { bindingTelegramBotTokenDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "Telegram Bot Token",
                                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation()
                                )
                            }
                            SessionSetupStepCard(
                                step = 2,
                                text = uiLabel("Paste the token, then tap Save at the bottom. This starts Telegram polling.")
                            )
                            SessionSetupStepCard(
                                step = 3,
                                text = uiLabel("From the Telegram account you want to bind, send one message to the bot.")
                            )
                            SessionSetupStepCard(
                                step = 4,
                                text = uiLabel("Tap Detect Chats, choose the conversation, then tap Save again to finish binding.")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SettingsActionButton(
                                        text = uiLabel("Detect Chats"),
                                        icon = Icons.Rounded.Refresh,
                                        onClick = {
                                            vm.discoverTelegramChatsForBinding(bindingTelegramBotTokenDraft)
                                        },
                                        enabled = bindingTelegramBotTokenDraft.isNotBlank() && !state.sessionBindingTelegramDiscovering
                                    )
                                    if (state.sessionBindingTelegramDiscovering) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = uiLabel("Detecting..."),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (state.sessionBindingTelegramCandidates.isNotEmpty()) {
                                    state.sessionBindingTelegramCandidates.forEach { candidate ->
                                        val isSelected = bindingChatIdDraft.trim() == candidate.chatId
                                        SessionSetupSelectableItemCard(
                                            selected = isSelected,
                                            title = candidate.title,
                                            subtitle = "${candidate.kind}: ${candidate.chatId}",
                                            onClick = {
                                                bindingChatIdDraft = candidate.chatId
                                                bindingTelegramAllowedChatIdDraft = candidate.chatId
                                                closeAfterDetectedBindingSave = true
                                                vm.showSettingsInfo("Telegram chat selected. Tap Save again to finish binding.")
                                            }
                                        )
                                    }
                                }
                                SessionSetupFeedbackText(
                                    message = state.sessionBindingTelegramInfo,
                                    visible = state.sessionBindingTelegramDiscoveryAttempted,
                                    useChinese = state.settingsUseChinese
                                )
                            }
                            SettingsAdvancedSection(
                                expanded = telegramAdvancedExpanded,
                                onToggle = { telegramAdvancedExpanded = !telegramAdvancedExpanded }
                            ) {
                                SettingsAdvancedOptionCard(
                                    title = "Telegram Chat ID",
                                    description = "Manual target override. Usually filled by Detect Chats."
                                ) {
                                    SettingsTextField(
                                        value = bindingChatIdDraft,
                                        onValueChange = { bindingChatIdDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = "Telegram Chat ID",
                                        placeholder = "Filled automatically after Detect Chats"
                                    )
                                }
                                SettingsAdvancedOptionCard(
                                    title = "Allowed Chat ID",
                                    description = "Restricts replies to one chat. Usually the same as Telegram Chat ID."
                                ) {
                                    SettingsTextField(
                                        value = bindingTelegramAllowedChatIdDraft,
                                        onValueChange = { bindingTelegramAllowedChatIdDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = "Allowed Chat ID",
                                        placeholder = "Usually same as chat ID"
                                    )
                                }
                            }
                                } else if (sessionSettingsPage == SessionSettingsPage.Configure && normalizedChannel == "discord") {
                            SessionSetupStepCard(
                                step = 1,
                                text = uiLabel("Open the Discord Developer Portal, create an application, then open Bot and add a bot.")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SettingsActionButton(
                                        text = uiLabel("Developer Portal"),
                                        icon = Icons.Rounded.Description,
                                        onClick = { openExternalUrl(context, "https://discord.com/developers/applications") }
                                    )
                                }
                                SettingsInfoBlock(
                                    label = uiLabel("Token example"),
                                    value = "MTIzNDU2Nzg5MDEyMzQ1Njc4.GExample.AbcDefGhIjKlMnOpQrStUvWxYz"
                                )
                                SettingsTextField(
                                    value = bindingDiscordBotTokenDraft,
                                    onValueChange = { bindingDiscordBotTokenDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "Discord Bot Token",
                                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation()
                                )
                            }
                            SessionSetupStepCard(
                                step = 2,
                                text = uiLabel("In Bot settings, enable MESSAGE CONTENT INTENT. If you plan to use an allow list, enable SERVER MEMBERS INTENT too.")
                            )
                            SessionSetupStepCard(
                                step = 3,
                                text = uiLabel("Invite the bot to your server from OAuth2 URL Generator. Use scope bot and permissions Send Messages and Read Message History.")
                            )
                            SessionSetupStepCard(
                                step = 4,
                                text = uiLabel("Enable Developer Mode in Discord. Right-click your avatar and Copy User ID if you want an allow list. Right-click the target channel and Copy Channel ID.")
                            ) {
                                SettingsInfoBlock(
                                    label = uiLabel("User ID example"),
                                    value = "123456789012345678"
                                )
                                SettingsTextField(
                                    value = bindingChatIdDraft,
                                    onValueChange = { bindingChatIdDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "Target Channel ID",
                                    placeholder = "Example: 123456789012345678"
                                )
                            }
                            SessionSetupStepCard(
                                step = 5,
                                text = uiLabel("Choose how the bot should respond in this channel.")
                            ) {
                                ExposedDropdownMenuBox(
                                    expanded = bindingDiscordResponseModeMenuExpanded,
                                    onExpandedChange = { bindingDiscordResponseModeMenuExpanded = it }
                                ) {
                                    SettingsSelectField(
                                        value = uiLabel(bindingDiscordResponseModeDraft.ifBlank { "mention" }),
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth(),
                                        label = "Response Mode",
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bindingDiscordResponseModeMenuExpanded)
                                        }
                                    )
                                    ExposedDropdownMenu(
                                        expanded = bindingDiscordResponseModeMenuExpanded,
                                        onDismissRequest = { bindingDiscordResponseModeMenuExpanded = false },
                                        shape = settingsTextFieldShape(),
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                        border = settingsDropdownMenuBorder()
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "mention")
                                            },
                                            onClick = {
                                                bindingDiscordResponseModeDraft = "mention"
                                                bindingDiscordResponseModeMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "open")
                                            },
                                            onClick = {
                                                bindingDiscordResponseModeDraft = "open"
                                                bindingDiscordResponseModeMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                                SettingsInfoBlock(
                                    label = uiLabel("Response modes"),
                                    value = uiLabel("mention: reply only when @mentioned. open: reply to all messages in this channel.")
                                )
                            }
                            SessionSetupStepCard(
                                step = 6,
                                text = uiLabel("After filling the fields, tap Save at the bottom to start the Discord connection.")
                            )
                            SettingsAdvancedSection(
                                expanded = discordAdvancedExpanded,
                                onToggle = { discordAdvancedExpanded = !discordAdvancedExpanded }
                            ) {
                                SettingsAdvancedOptionCard(
                                    title = "Allowed User IDs",
                                    description = "Leave blank to allow anyone in the channel to trigger replies."
                                ) {
                                    SettingsTextField(
                                        value = bindingDiscordAllowedUserIdsDraft,
                                        onValueChange = { bindingDiscordAllowedUserIdsDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4,
                                        label = "Allowed User IDs",
                                        placeholder = "One ID per line or comma-separated"
                                    )
                                }
                            }
                                } else if (sessionSettingsPage == SessionSettingsPage.Configure && normalizedChannel == "slack") {
                            SessionSetupStepCard(
                                step = 1,
                                text = uiLabel("Create a Slack app from scratch, then enable Socket Mode.")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SettingsActionButton(
                                        text = uiLabel("Slack API"),
                                        icon = Icons.Rounded.Description,
                                        onClick = { openExternalUrl(context, "https://api.slack.com/apps") }
                                    )
                                }
                            }
                            SessionSetupStepCard(
                                step = 2,
                                text = uiLabel("Turn on Socket Mode, then create an app-level token with connections:write.")
                            ) {
                                SettingsInfoBlock(
                                    label = uiLabel("App token example"),
                                    value = "Slack app token: starts with xapp, paste your real token here"
                                )
                                SettingsTextField(
                                    value = bindingSlackAppTokenDraft,
                                    onValueChange = { bindingSlackAppTokenDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "Slack App Token (xapp)",
                                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation()
                                )
                            }
                            SessionSetupStepCard(
                                step = 3,
                                text = uiLabel("Add bot scopes chat:write, reactions:write, app_mentions:read, then enable Event Subscriptions for message and app mention events.")
                            )
                            SessionSetupStepCard(
                                step = 4,
                                text = uiLabel("Install the app to your workspace, then copy the bot token.")
                            ) {
                                SettingsInfoBlock(
                                    label = uiLabel("Bot token example"),
                                    value = "Slack bot token: starts with xoxb, paste your real token here"
                                )
                                SettingsTextField(
                                    value = bindingSlackBotTokenDraft,
                                    onValueChange = { bindingSlackBotTokenDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "Slack Bot Token (xoxb)",
                                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation()
                                )
                            }
                            SessionSetupStepCard(
                                step = 5,
                                text = uiLabel("Enter the target conversation ID. Slack channel, group, and DM IDs usually start with C, G, or D.")
                            ) {
                                SettingsTextField(
                                    value = bindingChatIdDraft,
                                    onValueChange = { bindingChatIdDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "Target Channel ID",
                                    placeholder = "Example: C123ABC45 or D123ABC45"
                                )
                            }
                            SessionSetupStepCard(
                                step = 6,
                                text = uiLabel("Choose how the bot should respond in this conversation.")
                            ) {
                                ExposedDropdownMenuBox(
                                    expanded = bindingSlackResponseModeMenuExpanded,
                                    onExpandedChange = { bindingSlackResponseModeMenuExpanded = it }
                                ) {
                                    SettingsSelectField(
                                        value = uiLabel(bindingSlackResponseModeDraft.ifBlank { "mention" }),
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth(),
                                        label = "Response Mode",
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bindingSlackResponseModeMenuExpanded)
                                        }
                                    )
                                    ExposedDropdownMenu(
                                        expanded = bindingSlackResponseModeMenuExpanded,
                                        onDismissRequest = { bindingSlackResponseModeMenuExpanded = false },
                                        shape = settingsTextFieldShape(),
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                        border = settingsDropdownMenuBorder()
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "mention")
                                            },
                                            onClick = {
                                                bindingSlackResponseModeDraft = "mention"
                                                bindingSlackResponseModeMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                SettingsDropdownMenuText(text = "open")
                                            },
                                            onClick = {
                                                bindingSlackResponseModeDraft = "open"
                                                bindingSlackResponseModeMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            SessionSetupStepCard(
                                step = 7,
                                text = uiLabel("After filling the fields, tap Save at the bottom to start the Slack connection.")
                            )
                            SettingsAdvancedSection(
                                expanded = slackAdvancedExpanded,
                                onToggle = { slackAdvancedExpanded = !slackAdvancedExpanded }
                            ) {
                                SettingsAdvancedOptionCard(
                                    title = "Allowed User IDs",
                                    description = "Leave blank to allow anyone in this conversation to trigger replies."
                                ) {
                                    SettingsTextField(
                                        value = bindingSlackAllowedUserIdsDraft,
                                        onValueChange = { bindingSlackAllowedUserIdsDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4,
                                        label = "Allowed User IDs",
                                        placeholder = "One ID per line or comma-separated"
                                    )
                                }
                            }
                                } else if (sessionSettingsPage == SessionSettingsPage.Configure && normalizedChannel == "feishu") {
                            SessionSetupStepCard(
                                step = 1,
                                text = uiLabel("Create a Feishu app in Feishu Open Platform, enable Bot capability, then copy App ID and App Secret.")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SettingsActionButton(
                                        text = uiLabel("Open Platform"),
                                        icon = Icons.Rounded.Description,
                                        onClick = { openExternalUrl(context, "https://open.feishu.cn/") }
                                    )
                                }
                                SettingsTextField(
                                    value = bindingFeishuAppIdDraft,
                                    onValueChange = { bindingFeishuAppIdDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "Feishu App ID"
                                )
                                SettingsTextField(
                                    value = bindingFeishuAppSecretDraft,
                                    onValueChange = { bindingFeishuAppSecretDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "Feishu App Secret",
                                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation()
                                )
                            }
                            SessionSetupStepCard(
                                step = 2,
                                text = uiLabel("After filling App ID and App Secret, tap Save once at the bottom so LGClaw starts Long Connection.")
                            )
                            SessionSetupStepCard(
                                step = 3,
                                text = uiLabel("In Events & Callbacks, select Long Connection, then add im.message.receive_v1.")
                            )
                            SessionSetupStepCard(
                                step = 4,
                                text = uiLabel("In Permission Management, add im:message and im:message.p2p_msg:readonly. If you test with @ in a group, also add im:message.group_at_msg:readonly.")
                            )
                            SessionSetupStepCard(
                                step = 5,
                                text = uiLabel("Publish the app, open it in Feishu, and confirm Long Connection while LGClaw is running.")
                            )
                            SessionSetupStepCard(
                                step = 6,
                                text = uiLabel("In Feishu, send one message that @mentions the bot. Private chats and group chats both require @ to trigger replies.")
                            )
                            SessionSetupStepCard(
                                step = 7,
                                text = uiLabel("Tap Detect Chats, choose the conversation to bind, then tap Save again.")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SettingsActionButton(
                                        text = uiLabel("Detect Chats"),
                                        icon = Icons.Rounded.Refresh,
                                        onClick = {
                                            vm.discoverFeishuChatsForBinding(
                                                appId = bindingFeishuAppIdDraft,
                                                appSecret = bindingFeishuAppSecretDraft,
                                                encryptKey = bindingFeishuEncryptKeyDraft,
                                                verificationToken = bindingFeishuVerificationTokenDraft
                                            )
                                        },
                                        enabled = !state.sessionBindingFeishuDiscovering
                                    )
                                    if (state.sessionBindingFeishuDiscovering) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = uiLabel("Detecting..."),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (state.sessionBindingFeishuCandidates.isNotEmpty()) {
                                    state.sessionBindingFeishuCandidates.forEach { candidate ->
                                        val isSelected = bindingChatIdDraft.trim() == candidate.chatId
                                        SessionSetupSelectableItemCard(
                                            selected = isSelected,
                                            title = candidate.title,
                                            subtitle = "${candidate.kind}: ${candidate.chatId}",
                                            note = candidate.note.takeIf { it.isNotBlank() }?.let {
                                                localizedUiMessage(it, state.settingsUseChinese)
                                            }.orEmpty(),
                                            onClick = {
                                                bindingChatIdDraft = candidate.chatId
                                                bindingFeishuResponseModeDraft = "mention"
                                                closeAfterDetectedBindingSave = true
                                                vm.showSettingsInfo("Feishu chat selected. Tap Save again to finish binding.")
                                            }
                                        )
                                    }
                                }
                                SessionSetupFeedbackText(
                                    message = state.sessionBindingFeishuInfo,
                                    visible = state.sessionBindingFeishuDiscoveryAttempted,
                                    useChinese = state.settingsUseChinese
                                )
                            }
                            SettingsAdvancedSection(
                                expanded = feishuAdvancedExpanded,
                                onToggle = { feishuAdvancedExpanded = !feishuAdvancedExpanded }
                            ) {
                                SettingsAdvancedOptionCard(
                                    title = "Encrypt Key",
                                    description = "Only fill this if your Feishu app requires encrypted events."
                                ) {
                                    SettingsTextField(
                                        value = bindingFeishuEncryptKeyDraft,
                                        onValueChange = { bindingFeishuEncryptKeyDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = "Encrypt Key"
                                    )
                                }
                                SettingsAdvancedOptionCard(
                                    title = "Verification Token",
                                    description = "Only fill this if your Feishu app has a verification token configured."
                                ) {
                                    SettingsTextField(
                                        value = bindingFeishuVerificationTokenDraft,
                                        onValueChange = { bindingFeishuVerificationTokenDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = "Verification Token"
                                    )
                                }
                                SettingsAdvancedOptionCard(
                                    title = "Target ID",
                                    description = "Manual target override. Usually filled automatically after Detect Chats."
                                ) {
                                    SettingsTextField(
                                        value = bindingChatIdDraft,
                                        onValueChange = { bindingChatIdDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = "Target ID",
                                        placeholder = "Private: ou_xxx, Group: oc_xxx"
                                    )
                                }
                                SettingsAdvancedOptionCard(
                                    title = "Allowed Open IDs",
                                    description = "Restricts which senders can trigger replies for this binding."
                                ) {
                                    SettingsTextField(
                                        value = bindingFeishuAllowedOpenIdsDraft,
                                        onValueChange = { bindingFeishuAllowedOpenIdsDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4,
                                        label = "Allowed Open IDs",
                                        placeholder = "One open_id per line, or * to allow all"
                                    )
                                }
                            }
                                } else if (sessionSettingsPage == SessionSettingsPage.Configure && normalizedChannel == "email") {
                            SessionSetupStepCard(
                                step = 1,
                                text = uiLabel("Prepare a mailbox for the bot. IMAP is used to read mail and SMTP is used to send replies.")
                            )
                            SessionSetupStepCard(
                                step = 2,
                                text = uiLabel("Enter IMAP settings for receiving mail.")
                            ) {
                                SettingsTextField(
                                    value = bindingEmailImapHostDraft,
                                    onValueChange = { bindingEmailImapHostDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "IMAP Host"
                                )
                                SettingsTextField(
                                    value = bindingEmailImapPortDraft,
                                    onValueChange = { bindingEmailImapPortDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "IMAP Port",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                SettingsTextField(
                                    value = bindingEmailImapUsernameDraft,
                                    onValueChange = {
                                        bindingEmailImapUsernameDraft = it
                                        if (bindingEmailFromAddressDraft.isBlank()) {
                                            bindingEmailFromAddressDraft = it
                                        }
                                        if (bindingEmailSmtpUsernameDraft.isBlank()) {
                                            bindingEmailSmtpUsernameDraft = it
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "IMAP Username"
                                )
                                SettingsTextField(
                                    value = bindingEmailImapPasswordDraft,
                                    onValueChange = { bindingEmailImapPasswordDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "IMAP Password / App Password",
                                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation()
                                )
                            }
                            SessionSetupStepCard(
                                step = 3,
                                text = uiLabel("Enter SMTP settings for replies.")
                            ) {
                                SettingsTextField(
                                    value = bindingEmailSmtpHostDraft,
                                    onValueChange = { bindingEmailSmtpHostDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "SMTP Host"
                                )
                                SettingsTextField(
                                    value = bindingEmailSmtpPortDraft,
                                    onValueChange = { bindingEmailSmtpPortDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "SMTP Port",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                SettingsTextField(
                                    value = bindingEmailSmtpUsernameDraft,
                                    onValueChange = { bindingEmailSmtpUsernameDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "SMTP Username"
                                )
                                SettingsTextField(
                                    value = bindingEmailSmtpPasswordDraft,
                                    onValueChange = { bindingEmailSmtpPasswordDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "SMTP Password / App Password",
                                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation()
                                )
                                SettingsTextField(
                                    value = bindingEmailFromAddressDraft,
                                    onValueChange = { bindingEmailFromAddressDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "From Address"
                                )
                            }
                            SessionSetupStepCard(
                                step = 4,
                                text = uiLabel("Tap Save once to start mailbox polling, then send one email to this account.")
                            )
                            SessionSetupStepCard(
                                step = 5,
                                text = uiLabel("Tap Detect Senders, choose the sender address to bind, then tap Save again.")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SettingsActionButton(
                                        text = uiLabel("Detect Senders"),
                                        icon = Icons.Rounded.Refresh,
                                        onClick = {
                                            vm.discoverEmailSendersForBinding(
                                                consentGranted = true,
                                                imapHost = bindingEmailImapHostDraft,
                                                imapPort = bindingEmailImapPortDraft,
                                                imapUsername = bindingEmailImapUsernameDraft,
                                                imapPassword = bindingEmailImapPasswordDraft,
                                                smtpHost = bindingEmailSmtpHostDraft,
                                                smtpPort = bindingEmailSmtpPortDraft,
                                                smtpUsername = bindingEmailSmtpUsernameDraft,
                                                smtpPassword = bindingEmailSmtpPasswordDraft,
                                                fromAddress = bindingEmailFromAddressDraft,
                                                autoReplyEnabled = bindingEmailAutoReplyEnabledDraft
                                            )
                                        },
                                        enabled = bindingEmailImapHostDraft.isNotBlank() &&
                                            bindingEmailImapUsernameDraft.isNotBlank() &&
                                            bindingEmailImapPasswordDraft.isNotBlank() &&
                                            !state.sessionBindingEmailDiscovering
                                    )
                                    if (state.sessionBindingEmailDiscovering) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = uiLabel("Detecting..."),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (state.sessionBindingEmailCandidates.isNotEmpty()) {
                                    state.sessionBindingEmailCandidates.forEach { candidate ->
                                        val isSelected = bindingChatIdDraft.trim().equals(candidate.email, ignoreCase = true)
                                        SessionSetupSelectableItemCard(
                                            selected = isSelected,
                                            title = candidate.email,
                                            subtitle = candidate.subject.takeIf { it.isNotBlank() }?.let {
                                                "${tr("Last subject", "")}: $it"
                                            } ?: candidate.email,
                                            note = candidate.note.takeIf { it.isNotBlank() }?.let {
                                                localizedUiMessage(it, state.settingsUseChinese)
                                            }.orEmpty(),
                                            onClick = {
                                                bindingChatIdDraft = candidate.email
                                                closeAfterDetectedBindingSave = true
                                                vm.showSettingsInfo("Email sender selected. Tap Save again to finish binding.")
                                            }
                                        )
                                    }
                                }
                                SessionSetupFeedbackText(
                                    message = state.sessionBindingEmailInfo,
                                    visible = state.sessionBindingEmailDiscoveryAttempted,
                                    useChinese = state.settingsUseChinese
                                )
                            }
                            SettingsAdvancedSection(
                                expanded = emailAdvancedExpanded,
                                onToggle = { emailAdvancedExpanded = !emailAdvancedExpanded }
                            ) {
                                SettingsAdvancedOptionCard(
                                    title = "Auto reply",
                                    description = "Turn this off if you only want detection and manual replies."
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = uiLabel("Auto reply"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        LGClawSwitch(
                                            checked = bindingEmailAutoReplyEnabledDraft,
                                            onCheckedChange = { bindingEmailAutoReplyEnabledDraft = it }
                                        )
                                    }
                                }
                                SettingsAdvancedOptionCard(
                                    title = "Sender Email Address",
                                    description = "Manual sender override. Usually chosen from Detect Senders."
                                ) {
                                    SettingsTextField(
                                        value = bindingChatIdDraft,
                                        onValueChange = { bindingChatIdDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = "Sender Email Address",
                                        placeholder = "someone@example.com"
                                    )
                                }
                            }
                                } else if (sessionSettingsPage == SessionSettingsPage.Configure && normalizedChannel == "wecom") {
                        SessionSetupStepCard(
                            step = 1,
                            text = uiLabel("In WeCom Admin, go to Security & Management > Management Tools, then create an AI Bot.")
                        )
                        SessionSetupStepCard(
                            step = 2,
                            text = uiLabel("Choose Manual Create, then choose API mode with long connection. Copy the Bot ID and Secret.")
                        )
                        SessionSetupStepCard(
                            step = 3,
                            text = uiLabel("Fill WeCom Bot ID and Secret below, then tap Save once to start the long connection.")
                        ) {
                            SettingsTextField(
                                value = bindingWeComBotIdDraft,
                                onValueChange = { bindingWeComBotIdDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                    label = "WeCom Bot ID"
                                )
                                SettingsTextField(
                                    value = bindingWeComSecretDraft,
                                    onValueChange = { bindingWeComSecretDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "WeCom Secret",
                                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation()
                                )
                            }
                            SessionSetupStepCard(
                                step = 4,
                                text = uiLabel("After Save, go to Available Permissions and grant the message permission for the bot.")
                            )
                            SessionSetupStepCard(
                                step = 5,
                                text = uiLabel("Open the bot in WeCom and send one message so the app can detect the conversation.")
                            )
                            SessionSetupStepCard(
                                step = 6,
                                text = uiLabel("Tap Detect Chats, choose the conversation to bind, then tap Save again.")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SettingsActionButton(
                                        text = uiLabel("Detect Chats"),
                                        icon = Icons.Rounded.Refresh,
                                        onClick = {
                                            vm.discoverWeComChatsForBinding(
                                                botId = bindingWeComBotIdDraft,
                                                secret = bindingWeComSecretDraft
                                            )
                                        },
                                        enabled = !state.sessionBindingWeComDiscovering
                                    )
                                    if (state.sessionBindingWeComDiscovering) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = uiLabel("Detecting..."),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (state.sessionBindingWeComCandidates.isNotEmpty()) {
                                    state.sessionBindingWeComCandidates.forEach { candidate ->
                                        val isSelected = bindingChatIdDraft.trim() == candidate.chatId
                                        SessionSetupSelectableItemCard(
                                            selected = isSelected,
                                            title = candidate.title,
                                            subtitle = "${candidate.kind}: ${candidate.chatId}",
                                            note = candidate.note.takeIf { it.isNotBlank() }?.let {
                                                localizedUiMessage(it, state.settingsUseChinese)
                                            }.orEmpty(),
                                            onClick = {
                                                bindingChatIdDraft = candidate.chatId
                                                closeAfterDetectedBindingSave = true
                                                vm.showSettingsInfo("WeCom chat selected. Tap Save again to finish binding.")
                                            }
                                        )
                                    }
                                }
                                SessionSetupFeedbackText(
                                    message = state.sessionBindingWeComInfo,
                                    visible = state.sessionBindingWeComDiscoveryAttempted,
                                    useChinese = state.settingsUseChinese
                                )
                            }
                            SettingsAdvancedSection(
                                expanded = weComAdvancedExpanded,
                                onToggle = { weComAdvancedExpanded = !weComAdvancedExpanded }
                            ) {
                                SettingsAdvancedOptionCard(
                                    title = "Target ID",
                                    description = "Manual target override. Use a detected chatId or a specific userId."
                                ) {
                                    SettingsTextField(
                                        value = bindingChatIdDraft,
                                        onValueChange = { bindingChatIdDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = "Target ID",
                                        placeholder = "userId or detected chatId"
                                    )
                                }
                                SettingsAdvancedOptionCard(
                                    title = "Allowed User IDs",
                                    description = "Restricts which users can trigger replies. Use * to allow all."
                                ) {
                                    SettingsTextField(
                                        value = bindingWeComAllowedUserIdsDraft,
                                        onValueChange = { bindingWeComAllowedUserIdsDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4,
                                        label = "Allowed User IDs",
                                        placeholder = "One user ID per line, or * to allow all"
                                    )
                                }
                            }
                                } else if (sessionSettingsPage == SessionSettingsPage.Configure) {
                                    Text(
                                        text = uiLabel("Select a channel to configure binding for this session."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (
                            sessionSettingsPage == SessionSettingsPage.Configure &&
                            sessionSettingsScrollState.maxValue > 0 &&
                            sessionSettingsScrollState.value < sessionSettingsScrollState.maxValue
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                tonalElevation = 2.dp,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = uiLabel("More settings below"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    when (sessionSettingsPage) {
                        SessionSettingsPage.Configure -> {
                            Button(
                                onClick = {
                                    vm.saveSessionChannelBinding(
                                        sessionId = sessionId,
                                        enabled = bindingEnabledDraft,
                                        channel = bindingChannelDraft,
                                        chatId = bindingChatIdDraft,
                                        targetDisplayName = selectedTargetDisplay ?: bindingChatIdDraft.trim(),
                                        telegramBotToken = bindingTelegramBotTokenDraft,
                                        telegramAllowedChatId = bindingTelegramAllowedChatIdDraft,
                                        discordBotToken = bindingDiscordBotTokenDraft,
                                        discordResponseMode = bindingDiscordResponseModeDraft,
                                        discordAllowedUserIds = bindingDiscordAllowedUserIdsDraft,
                                        slackBotToken = bindingSlackBotTokenDraft,
                                        slackAppToken = bindingSlackAppTokenDraft,
                                        slackResponseMode = bindingSlackResponseModeDraft,
                                        slackAllowedUserIds = bindingSlackAllowedUserIdsDraft,
                                        feishuAppId = bindingFeishuAppIdDraft,
                                        feishuAppSecret = bindingFeishuAppSecretDraft,
                                        feishuEncryptKey = bindingFeishuEncryptKeyDraft,
                                        feishuVerificationToken = bindingFeishuVerificationTokenDraft,
                                        feishuResponseMode = bindingFeishuResponseModeDraft,
                                        feishuAllowedOpenIds = bindingFeishuAllowedOpenIdsDraft,
                                        emailConsentGranted = true,
                                        emailImapHost = bindingEmailImapHostDraft,
                                        emailImapPort = bindingEmailImapPortDraft,
                                        emailImapUsername = bindingEmailImapUsernameDraft,
                                        emailImapPassword = bindingEmailImapPasswordDraft,
                                        emailSmtpHost = bindingEmailSmtpHostDraft,
                                        emailSmtpPort = bindingEmailSmtpPortDraft,
                                        emailSmtpUsername = bindingEmailSmtpUsernameDraft,
                                        emailSmtpPassword = bindingEmailSmtpPasswordDraft,
                                        emailFromAddress = bindingEmailFromAddressDraft,
                                        emailAutoReplyEnabled = bindingEmailAutoReplyEnabledDraft,
                                        wecomBotId = bindingWeComBotIdDraft,
                                        wecomSecret = bindingWeComSecretDraft,
                                        wecomAllowedUserIds = bindingWeComAllowedUserIdsDraft
                                    )
                                    bindingChannelMenuExpanded = false
                                    bindingDiscordResponseModeMenuExpanded = false
                                    bindingSlackResponseModeMenuExpanded = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.56f)
                                )
                            ) {
                                Text(uiLabel("Save"))
                            }
                        }

                        else -> {
                            OutlinedButton(
                                onClick = {
                                    bindingChannelMenuExpanded = false
                                    bindingDiscordResponseModeMenuExpanded = false
                                    bindingSlackResponseModeMenuExpanded = false
                                    vm.clearTelegramChatDiscovery()
                                    vm.clearFeishuChatDiscovery()
                                    vm.clearEmailSenderDiscovery()
                                    vm.clearWeComChatDiscovery()
                                    sessionSettingsSessionId = null
                                    sessionSettingsPageName = SessionSettingsPage.Menu.name
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
                                )
                            ) {
                                Text(uiLabel("Close"))
                            }
                        }
                    }
                },
                dismissButton = {
                    if (sessionSettingsPage != SessionSettingsPage.Menu) {
                        OutlinedButton(
                            onClick = {
                                bindingChannelMenuExpanded = false
                                bindingDiscordResponseModeMenuExpanded = false
                                bindingSlackResponseModeMenuExpanded = false
                                vm.clearTelegramChatDiscovery()
                                vm.clearFeishuChatDiscovery()
                                vm.clearEmailSenderDiscovery()
                                vm.clearWeComChatDiscovery()
                                sessionSettingsPageName = SessionSettingsPage.Menu.name
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
                            )
                        ) {
                            Text(uiLabel("Back"))
                        }
                    }
                }
            )
        } else {
            bindingChannelMenuExpanded = false
            bindingDiscordResponseModeMenuExpanded = false
            bindingSlackResponseModeMenuExpanded = false
            vm.clearTelegramChatDiscovery()
            vm.clearFeishuChatDiscovery()
            vm.clearEmailSenderDiscovery()
            vm.clearWeComChatDiscovery()
            sessionSettingsSessionId = null
            sessionSettingsPageName = SessionSettingsPage.Menu.name
        }
    }

    val userRoundStartIndices = remember(state.messages) {
        state.messages.mapIndexedNotNull { index, message ->
            if (message.role == "user") index else null
        }
    }
    val totalRounds = userRoundStartIndices.size
    val clampedVisibleRounds = when {
        totalRounds <= 0 -> HISTORY_ROUNDS_PAGE_SIZE
        totalRounds <= HISTORY_ROUNDS_PAGE_SIZE -> totalRounds
        else -> visibleHistoryRounds.coerceIn(HISTORY_ROUNDS_PAGE_SIZE, totalRounds)
    }
    val hiddenRounds = if (totalRounds > clampedVisibleRounds) totalRounds - clampedVisibleRounds else 0
    val historyWindowStartIndex = if (hiddenRounds > 0) userRoundStartIndices[hiddenRounds] else 0
    val visibleMessages = if (historyWindowStartIndex > 0) {
        state.messages.subList(historyWindowStartIndex, state.messages.size)
    } else {
        state.messages
    }
    val toolGroupsByStartId = remember(visibleMessages) {
        buildToolMessageGroups(visibleMessages).associateBy { it.startId }
    }
    val compactVisibleMessages = remember(visibleMessages, toolGroupsByStartId) {
        visibleMessages.filter { message -> message.role != "tool" || toolGroupsByStartId.containsKey(message.id) }
    }
    val assistantAvatarInfo = if (state.activeRoleCardId.isNotBlank()) {
        state.currentRoleCardAvatar
    } else {
        state.currentAgentAvatar
    }
    val selectedToolGroup = selectedToolGroupStartId?.let { toolGroupsByStartId[it] }
    val canLoadOlderHistory = hiddenRounds > 0
    val showHistoryStatus = visibleMessages.isNotEmpty()
    val headerItemCount = if (showHistoryStatus) 1 else 0

    LaunchedEffect(state.chatSearchResultIds, state.chatSearchCurrentIndex, visibleMessages.size) {
        val targetId = state.chatSearchResultIds.getOrNull(state.chatSearchCurrentIndex) ?: return@LaunchedEffect
        val visibleIndex = visibleMessages.indexOfFirst { it.id == targetId }
        if (visibleIndex >= 0) {
            listState.animateScrollToItem(index = headerItemCount + visibleIndex)
        } else if (state.messages.any { it.id == targetId }) {
            visibleHistoryRounds = totalRounds.coerceAtLeast(HISTORY_ROUNDS_PAGE_SIZE)
        }
    }

    val hasAssistantOutputAfterAnchor = run {
        val anchor = generationAnchorMessageId
        if (anchor == null) {
            false
        } else {
            state.messages.any { message ->
                if (message.id <= anchor || message.role != "assistant") {
                    false
                } else {
                    (displayedAssistantText[message.id] ?: message.content).isNotBlank()
                }
            }
        }
    }
    val showProcessingBubble = state.isGenerating && !hasAssistantOutputAfterAnchor
    val extraTailItemCount = if (showProcessingBubble) 1 else 0
    val totalItems = visibleMessages.size + headerItemCount + extraTailItemCount
    val tailIndex = if (totalItems <= 0) -1 else totalItems - 1
    val scrollIndicator by remember(
        totalItems,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    ) {
        derivedStateOf {
            val visibleCount = listState.layoutInfo.visibleItemsInfo.size
            val totalCount = listState.layoutInfo.totalItemsCount
            if (totalCount <= 0 || visibleCount <= 0 || totalCount <= visibleCount) {
                null
            } else {
                val maxIndex = (totalCount - visibleCount).coerceAtLeast(1)
                val rawProgress = (listState.firstVisibleItemIndex.toFloat() / maxIndex.toFloat())
                    .coerceIn(0f, 1f)
                ScrollIndicatorUi(
                    thumbFraction = 0.16f,
                    progress = rawProgress
                )
            }
        }
    }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > 0
    var inputBarSurfaceHeightPx by remember { mutableStateOf(0) }
    val tailVisibleGapPx = with(density) { CHAT_TAIL_VISIBLE_GAP.roundToPx() }
    val chatInputBarClearance = with(density) {
        val fallback = CHAT_INPUT_BAR_CLEARANCE.roundToPx()
        val outerVerticalPadding = 8.dp.roundToPx()
        val overlayHeight = maxOf(inputBarSurfaceHeightPx + outerVerticalPadding, fallback)
        val obstructionPx = overlayHeight + if (imeVisible) imeBottomPx else 0
        (obstructionPx.toDp() - 10.dp).coerceAtLeast(52.dp) + CHAT_TAIL_VISIBLE_GAP
    }
    val chatInputBarClearancePx = with(density) { chatInputBarClearance.roundToPx() }
    val isNearTail by remember(
        visibleMessages.size,
        headerItemCount,
        showProcessingBubble,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        tailVisibleGapPx,
        chatInputBarClearancePx
    ) {
        derivedStateOf {
            if (totalItems <= 0) return@derivedStateOf true
            val tailItem = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index == tailIndex }
                ?: return@derivedStateOf false
            val desiredBottom = listState.layoutInfo.viewportEndOffset - chatInputBarClearancePx
            (tailItem.offset + tailItem.size) <= (desiredBottom + 1)
        }
    }
    var programmaticScrolling by remember { mutableStateOf(false) }
    var scrollToLatestAfterSend by remember { mutableStateOf(false) }
    val autoScrollMutex = remember { Mutex() }
    suspend fun moveToLatest(animated: Boolean) {
        if (tailIndex < 0) return
        autoScrollMutex.withLock {
            programmaticScrolling = true
            try {
                val longDistance = abs(listState.firstVisibleItemIndex - tailIndex) > 20
                if (animated && !longDistance) {
                    listState.animateScrollToItem(tailIndex)
                } else {
                    listState.scrollToItem(tailIndex)
                }
                repeat(3) {
                    val tailItem = listState.layoutInfo.visibleItemsInfo
                        .lastOrNull { it.index == tailIndex }
                        ?: return@repeat
                    val desiredBottom = listState.layoutInfo.viewportEndOffset - chatInputBarClearancePx
                    val remaining = (tailItem.offset + tailItem.size) - desiredBottom
                    if (remaining <= 1) return@repeat
                    listState.scrollBy(remaining.toFloat())
                }
            } finally {
                programmaticScrolling = false
            }
        }
    }

    var nearTailBeforeImeOpen by rememberSaveable { mutableStateOf(true) }
    val showScrollToLatestButton = totalItems > 0 && !isNearTail
    val attachmentBridge = remember(context) { AttachmentBridge(context.applicationContext) }
    fun shareAttachment(attachment: UiMediaAttachment) {
        val uri = runCatching {
            attachment.fileId.takeIf { it.isNotBlank() }?.let { attachmentBridge.getShareUri(it) }
        }.getOrNull() ?: toAttachmentUri(attachment.contentUri.ifBlank { attachment.reference })
        uri?.let {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = attachment.mimeType.takeIf { value -> value.isNotBlank() } ?: mediaMimeTypeForKind(attachment.kind)
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "分享附件")) }
        }
    }
    fun openAttachmentWithSystem(attachment: UiMediaAttachment) {
        val uri = runCatching {
            attachment.fileId.takeIf { it.isNotBlank() }?.let { attachmentBridge.getShareUri(it) }
        }.getOrNull() ?: toAttachmentUri(attachment.contentUri.ifBlank { attachment.reference })
        uri?.let {
            val mime = mediaMimeTypeForKind(attachment.kind)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(it, attachment.mimeType.takeIf { value -> value.isNotBlank() } ?: mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "打开附件")) }
        }
    }
    fun saveImageAttachment(attachment: UiMediaAttachment) {
        val source = runCatching {
            attachment.fileId.takeIf { it.isNotBlank() }?.let { attachmentBridge.getShareUri(it) }
        }.getOrNull() ?: toAttachmentUri(attachment.contentUri.ifBlank { attachment.reference })
        if (source == null) {
            Toast.makeText(context, "找不到图片文件", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val fileName = attachment.label.ifBlank { "LGClaw_${System.currentTimeMillis()}.jpg" }
            val resolver = context.contentResolver
            val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, attachment.mimeType.ifBlank { "image/jpeg" })
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LGClaw")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also { uri ->
                    resolver.openInputStream(source)?.use { input ->
                        resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "LGClaw").apply { mkdirs() }
                val out = File(dir, fileName)
                resolver.openInputStream(source)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                Uri.fromFile(out)
            }
            requireNotNull(targetUri) { "save_failed" }
        }.onSuccess {
            Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show()
        }.onFailure { t ->
            Toast.makeText(context, "保存失败：${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        }
    }
    val openAttachment: (UiMediaAttachment) -> Unit = { attachment ->
        if (attachment.kind == UiMediaKind.Image) {
            previewImageAttachment = attachment
        } else {
            openAttachmentWithSystem(attachment)
        }
    }
    val toggleAudioPreview: (UiMediaAttachment) -> Unit = { attachment ->
        val sameRefPlaying = previewAudioRef == attachment.reference &&
            runCatching { previewAudioPlayer?.isPlaying == true }.getOrDefault(false)
        if (sameRefPlaying) {
            runCatching { previewAudioPlayer?.stop() }
            runCatching { previewAudioPlayer?.release() }
            previewAudioPlayer = null
            previewAudioRef = null
            previewAudioDurationMs = 0
            previewAudioPositionMs = 0
        } else {
            runCatching {
                runCatching { previewAudioPlayer?.stop() }
                runCatching { previewAudioPlayer?.release() }
                val player = MediaPlayer()
                val raw = attachment.reference.trim()
                val uri = toAttachmentUri(raw)
                if (uri != null && (
                        raw.startsWith("content://", true) ||
                            raw.startsWith("file://", true) ||
                            raw.startsWith("http://", true) ||
                            raw.startsWith("https://", true)
                        )
                ) {
                    player.setDataSource(context, uri)
                } else {
                    player.setDataSource(raw)
                }
                player.prepare()
                previewAudioDurationMs = runCatching { player.duration }.getOrDefault(0).coerceAtLeast(0)
                previewAudioPositionMs = 0
                player.setOnCompletionListener {
                    runCatching { it.release() }
                    previewAudioPositionMs = previewAudioDurationMs
                    previewAudioPlayer = null
                    previewAudioRef = null
                }
                player.start()
                previewAudioPlayer = player
                previewAudioRef = attachment.reference
            }.onFailure {
                runCatching { previewAudioPlayer?.release() }
                previewAudioPlayer = null
                previewAudioRef = null
                previewAudioDurationMs = 0
                previewAudioPositionMs = 0
            }
        }
    }
    val submitChatMessage: () -> Unit = {
        followLatest = true
        scrollToLatestAfterSend = true
        vm.sendMessage()
        keyboardController?.hide()
        Unit
    }
    val scrollToLatestAction = {
        if (tailIndex >= 0) {
            followLatest = true
            uiScope.launch { moveToLatest(animated = true) }
        }
    }

    LaunchedEffect(previewAudioPlayer, previewAudioRef) {
        val player = previewAudioPlayer ?: return@LaunchedEffect
        while (previewAudioPlayer === player) {
            val duration = runCatching { player.duration }.getOrDefault(0).coerceAtLeast(0)
            val position = runCatching { player.currentPosition }.getOrDefault(0).coerceAtLeast(0)
            previewAudioDurationMs = duration
            previewAudioPositionMs = position.coerceAtMost(duration)
            if (!runCatching { player.isPlaying }.getOrDefault(false)) {
                break
            }
            delay(250)
        }
    }

    LaunchedEffect(
        state.isGenerating,
        state.messages.lastOrNull()?.id,
        state.messages.lastOrNull()?.role
    ) {
        if (!state.isGenerating) {
            generationAnchorMessageId = null
            return@LaunchedEffect
        }
        val lastMessage = state.messages.lastOrNull()
        val latestUserLikeMessageId = state.messages
            .lastOrNull { message ->
                message.role != "assistant" && message.role != "tool"
            }
            ?.id
        when {
            generationAnchorMessageId == null -> {
                generationAnchorMessageId = latestUserLikeMessageId ?: lastMessage?.id
            }
            lastMessage != null && lastMessage.role != "assistant" && lastMessage.role != "tool" -> {
                generationAnchorMessageId = lastMessage.id
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress, isNearTail, programmaticScrolling) {
        if (programmaticScrolling) {
            if (isNearTail) {
                followLatest = true
            }
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress && !isNearTail) {
            followLatest = false
        } else if (isNearTail) {
            followLatest = true
        }
    }
    LaunchedEffect(imeVisible, isNearTail) {
        if (!imeVisible) {
            nearTailBeforeImeOpen = isNearTail
        }
    }
    LaunchedEffect(imeBottomPx, nearTailBeforeImeOpen, tailIndex, chatInputBarClearancePx) {
        if (imeBottomPx > 0 && nearTailBeforeImeOpen && tailIndex >= 0) {
            followLatest = true
            delay(16)
            moveToLatest(animated = false)
        }
    }
    LaunchedEffect(scrollToLatestAfterSend, state.messages.lastOrNull()?.id, tailIndex) {
        if (!scrollToLatestAfterSend || tailIndex < 0) return@LaunchedEffect
        moveToLatest(animated = false)
        scrollToLatestAfterSend = false
    }
    LaunchedEffect(state.messages) {
        if (!initializedMessages) {
            if (state.messages.isEmpty()) {
                return@LaunchedEffect
            }
            state.messages.forEach { message ->
                seenMessageIds[message.id] = true
                if (message.role == "assistant") {
                    displayedAssistantText[message.id] = message.content
                }
            }
            initializedMessages = true
            return@LaunchedEffect
        }

        state.messages.forEach { message ->
            val known = seenMessageIds[message.id] == true
            if (known) {
                if (message.role == "assistant") {
                    val shown = displayedAssistantText[message.id]
                    if (shown != message.content) {
                        displayedAssistantText[message.id] = message.content
                    }
                }
                return@forEach
            }

            seenMessageIds[message.id] = true
            if (message.role != "assistant") return@forEach

            displayedAssistantText[message.id] = message.content
        }

        val validIds = state.messages.asSequence().map { it.id }.toSet()
        seenMessageIds.keys.toList().forEach { id ->
            if (id !in validIds) {
                seenMessageIds.remove(id)
            }
        }
        displayedAssistantText.keys.toList().forEach { id ->
            if (id !in validIds) {
                displayedAssistantText.remove(id)
            }
        }
    }

    LaunchedEffect(state.currentSessionId) {
        // Session switch should snap to latest quickly without expensive animation.
        hasInitialJumpToBottom = false
        followLatest = true
        scrollToLatestAfterSend = false
        pendingHistoryRestore = null
        isLoadingOlderHistory = false
        olderHistoryLoadingStartedAtMs = 0L
        visibleHistoryRounds = HISTORY_ROUNDS_PAGE_SIZE
    }

    LaunchedEffect(
        state.terminalRuntime.activeJobId,
        state.terminalRuntime.activeCommand,
        state.terminalRuntime.installing
    ) {
        if (
            state.terminalRuntime.activeJobId.isNotBlank() ||
            state.terminalRuntime.activeCommand.isNotBlank() ||
            state.terminalRuntime.installing
        ) {
            if (!terminalMiniOverlaySuppressed) {
                showTerminalMiniOverlay = true
            }
        } else {
            terminalMiniOverlaySuppressed = false
        }
    }

    LaunchedEffect(
        pendingHistoryRestore,
        visibleMessages.size,
        headerItemCount
    ) {
        val restore = pendingHistoryRestore ?: return@LaunchedEffect
        val localIndex = visibleMessages.indexOfFirst { it.id == restore.anchorMessageId }
        if (localIndex < 0) {
            val elapsed = System.currentTimeMillis() - olderHistoryLoadingStartedAtMs
            val remain = HISTORY_LOADING_MIN_VISIBLE_MS - elapsed
            if (remain > 0) delay(remain)
            pendingHistoryRestore = null
            isLoadingOlderHistory = false
            olderHistoryLoadingStartedAtMs = 0L
            return@LaunchedEffect
        }
        listState.scrollToItem(
            index = (headerItemCount + localIndex).coerceAtLeast(0),
            scrollOffset = -restore.anchorOffsetFromTop.coerceAtLeast(0)
        )
        val elapsed = System.currentTimeMillis() - olderHistoryLoadingStartedAtMs
        val remain = HISTORY_LOADING_MIN_VISIBLE_MS - elapsed
        if (remain > 0) delay(remain)
        pendingHistoryRestore = null
        isLoadingOlderHistory = false
        olderHistoryLoadingStartedAtMs = 0L
    }

    LaunchedEffect(
        hasInitialJumpToBottom,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        canLoadOlderHistory,
        isLoadingOlderHistory,
        clampedVisibleRounds,
        totalRounds,
        visibleMessages.size,
        visibleMessages.firstOrNull()?.id
    ) {
        if (!hasInitialJumpToBottom) return@LaunchedEffect
        if (!canLoadOlderHistory || isLoadingOlderHistory) return@LaunchedEffect
        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (!atTop) return@LaunchedEffect

        delay(HISTORY_LOAD_TRIGGER_DELAY_MS)
        val stillAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (!stillAtTop || !canLoadOlderHistory || isLoadingOlderHistory) return@LaunchedEffect

        val nextVisibleRounds = (clampedVisibleRounds + HISTORY_ROUNDS_PAGE_SIZE).coerceAtMost(totalRounds)
        if (nextVisibleRounds == clampedVisibleRounds) return@LaunchedEffect

        val firstVisibleInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index >= headerItemCount }
        val anchorMessageId = firstVisibleInfo?.let { info ->
            visibleMessages.getOrNull(info.index - headerItemCount)?.id
        } ?: visibleMessages.firstOrNull()?.id ?: return@LaunchedEffect
        val anchorOffsetFromTop = firstVisibleInfo?.let { info ->
            (info.offset - listState.layoutInfo.viewportStartOffset).coerceAtLeast(0)
        } ?: 0

        isLoadingOlderHistory = true
        olderHistoryLoadingStartedAtMs = System.currentTimeMillis()
        followLatest = false
        pendingHistoryRestore = HistoryRestoreRequest(
            anchorMessageId = anchorMessageId,
            anchorOffsetFromTop = anchorOffsetFromTop
        )
        visibleHistoryRounds = nextVisibleRounds
    }

    LaunchedEffect(
        state.messages.lastOrNull()?.id,
        showProcessingBubble,
        followLatest,
        isNearTail
    ) {
        if (tailIndex < 0) return@LaunchedEffect
        if (!hasInitialJumpToBottom) {
            moveToLatest(animated = false)
            hasInitialJumpToBottom = true
            return@LaunchedEffect
        }
        if (!followLatest) return@LaunchedEffect
        if (isNearTail) return@LaunchedEffect
        moveToLatest(animated = true)
    }

    LaunchedEffect(mainSurface, settingsPage) {
        if (mainSurface != MainSurface.Settings) return@LaunchedEffect
        when (settingsPage) {
            SettingsPanelPage.Cron -> vm.refreshCronJobs()
            SettingsPanelPage.Runtime -> vm.refreshAgentLogs()
            else -> Unit
        }
    }
    LaunchedEffect(state.settingsInfo, mainSurface, sessionSettingsSessionId) {
        val info = state.settingsInfo?.trim().orEmpty()
        val canShowSettingsSnackbar =
            mainSurface != MainSurface.Chat || sessionSettingsSessionId != null
        if (info.isBlank() || !canShowSettingsSnackbar) return@LaunchedEffect
        val isBoundSuccess = info.startsWith("Bound to ", ignoreCase = true)
        if (closeAfterDetectedBindingSave && isBoundSuccess) {
            closeAfterDetectedBindingSave = false
            dismissSessionSettings()
        } else if (closeAfterDetectedBindingSave) {
            closeAfterDetectedBindingSave = false
        }
        settingsSnackbarHostState.currentSnackbarData?.dismiss()
        val isError = info.contains("failed", ignoreCase = true) ||
            info.contains("error", ignoreCase = true)
        val localizedMessage = localizedUiMessage(info, state.settingsUseChinese)
        if (sessionSettingsSessionId != null) {
            Toast.makeText(
                context.applicationContext,
                localizedMessage,
                if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        } else {
            settingsSnackbarHostState.showSnackbar(
                message = localizedMessage,
                withDismissAction = true,
                duration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
        vm.clearSettingsInfo()
    }

    selectedToolGroup?.let { group ->
        ModalBottomSheet(
            onDismissRequest = {
                selectedToolGroupStartId = null
                selectedToolMessageId = null
            },
            containerColor = Color(0xFFF8FAFC),
            contentColor = Color(0xFF111827),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            ToolResultsSheet(
                group = group,
                state = state,
                selectedMessageId = selectedToolMessageId,
                expandedToolMessages = expandedToolMessages,
                currentPreviewAudioRef = previewAudioRef,
                currentPreviewAudioDurationMs = previewAudioDurationMs,
                currentPreviewAudioPositionMs = previewAudioPositionMs,
                onDismiss = {
                    selectedToolGroupStartId = null
                    selectedToolMessageId = null
                },
                onOpenAttachment = openAttachment,
                onToggleAudioPreview = toggleAudioPreview
            )
        }
    }

    selectedTraceId
        ?.let { id -> state.inlineTraces.firstOrNull { it.id == id } }
        ?.let { trace ->
            InlineTraceDetailDialog(
                trace = trace,
                onDismiss = { selectedTraceId = null }
            )
        }

    if (showQuickRoleCardDialog) {
        AlertDialog(
            onDismissRequest = { showQuickRoleCardDialog = false },
            title = { Text("新建角色卡") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quickRoleName, { quickRoleName = it }, label = { Text("角色名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(quickRolePersona, { quickRolePersona = it }, label = { Text("角色设定") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.createRoleCard(
                            quickRoleName,
                            quickRoleName.take(1).ifBlank { "角" },
                            "从对话顶部快速创建",
                            quickRolePersona,
                            "自然、具体、有情绪，不使用AI腔。",
                            "遵守用户设定和基本安全边界。",
                            "当前聊天会话。",
                            ""
                        )
                        quickRoleName = ""
                        quickRolePersona = ""
                        showQuickRoleCardDialog = false
                    },
                    enabled = quickRoleName.isNotBlank() && quickRolePersona.length >= 12
                ) { Text("保存并绑定") }
            },
            dismissButton = { TextButton(onClick = { showQuickRoleCardDialog = false }) { Text("取消") } }
        )
    }

    if (modelMenuExpanded) {
        val providerModelGroups = state.settingsProviderConfigs
            .filter { it.equippedModels.isNotEmpty() || it.model.isNotBlank() }
            .ifEmpty {
                listOf(
                    UiProviderConfig(
                        id = "__current__",
                        providerName = state.settingsProvider,
                        customName = state.settingsProviderCustomName,
                        providerProtocol = state.settingsProviderProtocol,
                        apiKey = state.settingsApiKey,
                        model = state.settingsModel,
                        equippedModels = state.settingsEquippedModels.ifEmpty { listOf(state.settingsModel).filter { it.isNotBlank() } },
                        baseUrl = state.settingsBaseUrl,
                        enabled = true
                    )
                )
            }
        ModalBottomSheet(
            onDismissRequest = { modelMenuExpanded = false },
            containerColor = Color(0xFFF8FAFC),
            contentColor = Color(0xFF111827),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            ModelPickerSheet(
                configs = providerModelGroups,
                currentModel = state.settingsModel,
                onSelect = { config, model ->
                    if (config.id == "__current__") vm.switchActiveModel(model) else vm.switchProviderModel(config.id, model)
                    modelMenuExpanded = false
                },
                onDismiss = { modelMenuExpanded = false }
            )
        }
    }

    if (roleCardMenuExpanded) {
        ModalBottomSheet(
            onDismissRequest = { roleCardMenuExpanded = false },
            containerColor = Color(0xFFF8FAFC),
            contentColor = Color(0xFF111827),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            RoleCardPickerSheet(
                cards = state.roleCards.filter { it.enabled },
                activeRoleCardId = state.activeRoleCardId,
                onCreate = {
                    showQuickRoleCardDialog = true
                    roleCardMenuExpanded = false
                },
                onClear = {
                    vm.bindRoleCardToCurrentSession(null)
                    roleCardMenuExpanded = false
                },
                onSelect = { cardId ->
                    vm.bindRoleCardToCurrentSession(cardId)
                    roleCardMenuExpanded = false
                },
                onDismiss = { roleCardMenuExpanded = false }
            )
        }
    }

    previewImageAttachment?.let { attachment ->
        AttachmentImagePreviewDialog(
            attachment = attachment,
            onDismiss = { previewImageAttachment = null },
            onShare = { shareAttachment(attachment) },
            onOpenExternal = { openAttachmentWithSystem(attachment) },
            onSave = { saveImageAttachment(attachment) }
        )
    }

    if (showAvatarPickerSheet && avatarPickerTarget.isNotBlank()) {
        val currentAvatar = when {
            avatarPickerTarget.startsWith("role:") -> {
                val id = avatarPickerTarget.removePrefix("role:")
                state.roleCards.firstOrNull { it.id == id }?.let {
                    UiAvatarInfo(
                        presetKey = it.avatarPresetKey,
                        imagePath = it.avatarImagePath,
                        cropJson = it.avatarCropJson,
                        fallbackSymbol = it.avatarSymbol.ifBlank { it.name.take(1) }
                    )
                } ?: state.currentRoleCardAvatar
            }
            avatarPickerTarget.startsWith("agent:") -> {
                val id = avatarPickerTarget.removePrefix("agent:")
                state.agentProfiles.firstOrNull { it.id == id }?.let {
                    UiAvatarInfo(
                        presetKey = it.avatarPresetKey,
                        imagePath = it.avatarImagePath,
                        cropJson = it.avatarCropJson,
                        fallbackSymbol = it.name.take(1)
                    )
                } ?: state.currentAgentAvatar
            }
            else -> state.currentAgentAvatar
        }
        AvatarPickerSheet(
            current = currentAvatar,
            title = if (avatarPickerTarget.startsWith("role:")) "角色卡头像" else "智能体头像",
            onDismiss = { showAvatarPickerSheet = false },
            onPickPreset = { key ->
                when {
                    avatarPickerTarget.startsWith("role:") -> vm.setRoleCardAvatar(avatarPickerTarget.removePrefix("role:"), key)
                    avatarPickerTarget.startsWith("agent:") -> vm.setAgentAvatar(avatarPickerTarget.removePrefix("agent:"), key)
                }
                showAvatarPickerSheet = false
            },
            onPickImage = {
                showAvatarPickerSheet = false
                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClear = {
                when {
                    avatarPickerTarget.startsWith("role:") -> vm.setRoleCardAvatar(avatarPickerTarget.removePrefix("role:"), "")
                    avatarPickerTarget.startsWith("agent:") -> vm.setAgentAvatar(avatarPickerTarget.removePrefix("agent:"), "")
                }
                showAvatarPickerSheet = false
            }
        )
    }

    pendingAvatarSourceUri?.let { sourceUri ->
        AvatarCropSheet(
            sourceUri = sourceUri,
            onDismiss = { pendingAvatarSourceUri = null },
            onConfirm = { crop ->
                when {
                    avatarPickerTarget.startsWith("role:") -> vm.saveRoleCardAvatarFromUri(avatarPickerTarget.removePrefix("role:"), sourceUri, crop)
                    avatarPickerTarget.startsWith("agent:") -> vm.saveAgentAvatarFromUri(avatarPickerTarget.removePrefix("agent:"), sourceUri, crop)
                }
                pendingAvatarSourceUri = null
            }
        )
    }

    if (showCompressionConfirm) {
        AlertDialog(
            onDismissRequest = { showCompressionConfirm = false },
            title = { Text("主动压缩当前对话？") },
            text = {
                Text(
                    "当前有效上下文约 ${"%.1f".format(Locale.US, state.currentConversationK)}K。压缩会把较早消息写入本地 GZIP 归档，并生成摘要继续参与后续对话。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompressionConfirm = false
                        vm.startManualCompression()
                    }
                ) { Text("开始压缩") }
            },
            dismissButton = {
                TextButton(onClick = { showCompressionConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showCompressionCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCompressionCancelConfirm = false },
            title = { Text("取消压缩？") },
            text = { Text("取消后会停止当前压缩进程，已经完成写入的压缩记录不会被删除。") },
            confirmButton = {
                Button(
                    onClick = {
                        showCompressionCancelConfirm = false
                        vm.cancelCompression()
                    }
                ) { Text("确认取消") }
            },
            dismissButton = {
                TextButton(onClick = { showCompressionCancelConfirm = false }) { Text("继续压缩") }
            }
        )
    }

    if (pendingDeleteMessageIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteMessageIds = emptyList() },
            title = { Text("删除消息") },
            text = { Text("确定删除选中的 ${pendingDeleteMessageIds.size} 条消息吗？删除后不可恢复。") },
            confirmButton = {
                Button(onClick = {
                    vm.deleteMessages(pendingDeleteMessageIds)
                    selectedMessageIds = emptyList()
                    multiSelectMode = false
                    pendingDeleteMessageIds = emptyList()
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteMessageIds = emptyList() }) { Text("取消") } }
        )
    }

    messageActionTargetId?.let { targetId ->
        val target = state.messages.firstOrNull { it.id == targetId }
        if (target != null) {
            ModalBottomSheet(onDismissRequest = { messageActionTargetId = null }) {
                MessageActionSheet(
                    message = target,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(target.content))
                        vm.showSettingsInfo("消息已复制")
                        messageActionTargetId = null
                    },
                    onEditResend = {
                        vm.onInputChanged(target.content)
                        vm.showSettingsInfo("已回填到输入框，可以修改后重新发送")
                        messageActionTargetId = null
                    },
                    onPolish = {
                        vm.polishMessageToInput(target.content)
                        messageActionTargetId = null
                    },
                    onDelete = {
                        pendingDeleteMessageIds = listOf(target.id)
                        messageActionTargetId = null
                    },
                    onMultiSelect = {
                        multiSelectMode = true
                        selectedMessageIds = (selectedMessageIds + target.id).distinct()
                        vm.showSettingsInfo("已进入多选模式，点击消息可加入或取消选择")
                        messageActionTargetId = null
                    }
                )
            }
        } else {
            messageActionTargetId = null
        }
    }
    if (showHeartbeatEditor) {
        LaunchedEffect(state.settingsHeartbeatDoc) {
            delay(650)
            vm.saveHeartbeatDocument(showSuccessMessage = false, showErrorMessage = false)
        }
        HeartbeatEditorSheet(
            heartbeatDoc = state.settingsHeartbeatDoc,
            saving = state.settingsSaving,
            onHeartbeatDocChange = vm::onSettingsHeartbeatDocChanged,
            onClose = { showHeartbeatEditor = false }
        )
    }
    if (showTerminalSheet) {
        TerminalExpandedSheet(
            state = state.terminalRuntime,
            onDismiss = { showTerminalSheet = false },
            onCancelTask = vm::cancelTerminalTask,
            onClear = vm::clearTerminalOutput,
            onInstallTools = vm::installTerminalDeveloperTools,
            onForceClose = {
                vm.forceCloseTerminal()
                showTerminalSheet = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = mainSurface == MainSurface.Chat,
        drawerContent = {
            SessionDrawerContent(
                state = state,
                onCreateSessionRequest = { showCreateSessionDialog = true },
                onSelectSession = { sessionId ->
                    vm.selectSession(sessionId)
                    mainSurfaceName = MainSurface.Chat.name
                    uiScope.launch { drawerState.close() }
                },
                onRenameSession = { session ->
                    renameSessionName = session.title
                    pendingRenameSessionId = session.id
                },
                onConfigureSession = openSessionSettingsForSession,
                onDeleteSession = { sessionId -> pendingDeleteSessionId = sessionId },
                onOpenSettings = {
                    vm.openSettings()
                    settingsPageName = SettingsPanelPage.Home.name
                    mainSurfaceName = MainSurface.Settings.name
                    uiScope.launch { drawerState.close() }
                },
                onOpenSkills = {
                    vm.refreshExtensionPanels()
                    mainSurfaceName = MainSurface.Skills.name
                    uiScope.launch { drawerState.close() }
                },
                onOpenTools = {
                    vm.refreshExtensionPanels()
                    mainSurfaceName = MainSurface.Tools.name
                    uiScope.launch { drawerState.close() }
                },
                onOpenMemory = {
                    vm.refreshExtensionPanels()
                    mainSurfaceName = MainSurface.Memory.name
                    uiScope.launch { drawerState.close() }
                },
                onOpenAgents = {
                    vm.refreshAgentPanels()
                    mainSurfaceName = MainSurface.Agents.name
                    uiScope.launch { drawerState.close() }
                },
                onOpenTheme = {
                    mainSurfaceName = MainSurface.Theme.name
                    uiScope.launch { drawerState.close() }
                },
                onOpenEnvironment = {
                    vm.detectTerminalEnvironment()
                    mainSurfaceName = MainSurface.Environment.name
                    uiScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                when (mainSurface) {
                    MainSurface.Chat -> Surface(
                        color = Color.White,
                        contentColor = Color(0xFF171A20),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .drawBehind {
                                    val y = size.height - 1f
                                    drawLine(
                                        color = Color(0xFFE7EAF0),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1f
                                    )
                                }
                                .padding(top = 8.dp, bottom = 9.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ModernCircleToolButton(
                                    icon = Icons.Rounded.Menu,
                                    contentDescription = "打开菜单",
                                    onClick = { dismissKeyboard(); uiScope.launch { drawerState.open() } }
                                )
                                Surface(
                                    modifier = Modifier
                                        .weight(1f),
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Transparent,
                                    contentColor = Color(0xFF151923)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = CircleShape,
                                            color = Color(0xFF0B1020)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("◐", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Black)
                                            }
                                        }
                                        Text(
                                            text = state.settingsModel.ifBlank { ProviderCatalog.resolve(state.settingsProvider).title },
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(17.dp), tint = Color(0xFF98A1B2))
                                    }
                                }
                                ModernCircleToolButton(
                                    icon = Icons.Rounded.Search,
                                    contentDescription = "搜索聊天",
                                    onClick = { showChatSearch = !showChatSearch }
                                )
                                ModernCircleToolButton(
                                    icon = Icons.Rounded.Tune,
                                    contentDescription = "设置",
                                    onClick = {
                                        vm.openSettings()
                                        settingsPageName = SettingsPanelPage.Home.name
                                        mainSurfaceName = MainSurface.Settings.name
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TerminalHeaderChip(
                                    enabled = state.terminalRuntime.enabled,
                                    hasPermission = state.terminalRuntime.overlayPermissionGranted,
                                    onClick = {
                                        if (state.terminalRuntime.enabled) {
                                            showTerminalSheet = true
                                        } else {
                                            vm.showSettingsInfo("长按终端按钮可进入终端模式")
                                        }
                                    },
                                    onLongClick = { vm.toggleTerminalMode() },
                                    modifier = Modifier.widthIn(max = 150.dp)
                                )
                                Surface(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .combinedClickable(
                                            onClick = { vm.showSettingsInfo("长按 K 值可主动压缩当前对话") },
                                            onLongClick = { showCompressionConfirm = true }
                                        ),
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White,
                                    contentColor = Color(0xFF1B1E26),
                                    border = BorderStroke(1.dp, Color(0xFFE9EDF5)),
                                    shadowElevation = 6.dp
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${"%.1f".format(Locale.US, state.currentConversationK)}K",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (state.compressionProgress.running) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                        .clickable { showCompressionCancelConfirm = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    contentColor = Color(0xFF1B1E26),
                                    border = BorderStroke(1.dp, Color(0xFFE6EAF1))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = state.compressionProgress.stage.ifBlank { "正在压缩" },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${(state.compressionProgress.progress * 100).roundToInt()}%",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { state.compressionProgress.progress.coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(3.dp)
                                        )
                                        Text(
                                            text = state.compressionProgress.path.ifBlank { "本地消息 -> 摘要 -> GZIP 归档 -> K 值刷新" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val providerModelGroups = state.settingsProviderConfigs
                                    .filter { it.equippedModels.isNotEmpty() || it.model.isNotBlank() }
                                    .ifEmpty {
                                        listOf(
                                            UiProviderConfig(
                                                id = "__current__",
                                                providerName = state.settingsProvider,
                                                customName = state.settingsProviderCustomName,
                                                providerProtocol = state.settingsProviderProtocol,
                                                apiKey = state.settingsApiKey,
                                                model = state.settingsModel,
                                                equippedModels = state.settingsEquippedModels.ifEmpty { listOf(state.settingsModel).filter { it.isNotBlank() } },
                                                baseUrl = state.settingsBaseUrl,
                                                enabled = true
                                            )
                                        )
                                    }
                                Box {
                                    val currentProviderLabel = state.settingsProviderCustomName.ifBlank { ProviderCatalog.resolve(state.settingsProvider).title }
                                    CompactHeaderChip(
                                        label = "$currentProviderLabel / ${state.settingsModel.ifBlank { "模型" }}",
                                        icon = Icons.Rounded.SwapHoriz,
                                        onClick = { modelMenuExpanded = true },
                                        modifier = Modifier.widthIn(max = 220.dp)
                                    )
                                }
                                Box {
                                    CompactHeaderChip(
                                        label = "角色卡：" + state.currentRoleCardName.ifBlank { "未绑定" },
                                        icon = Icons.Rounded.Person,
                                        onClick = { roleCardMenuExpanded = true },
                                        modifier = Modifier.widthIn(max = 190.dp)
                                    )
                                }
                                if (state.currentAgentName.isNotBlank()) {
                                    CompactHeaderChip("智能体：${state.currentAgentName}", Icons.Rounded.Tune, onClick = { mainSurfaceName = MainSurface.Agents.name }, modifier = Modifier.widthIn(max = 190.dp))
                                }
                            }
                        }
                    }

                    MainSurface.Settings, MainSurface.Skills, MainSurface.Tools, MainSurface.Memory, MainSurface.Agents, MainSurface.Theme, MainSurface.Environment -> Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(6.dp)
                    )
                }
            }
        ) { padding ->
            when (mainSurface) {
                MainSurface.Chat -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                ) {
                    if (showChatSearch) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.chatSearchQuery,
                                onValueChange = vm::onChatSearchChanged,
                                modifier = Modifier.weight(1f),
                                label = { Text("\u641c\u7d22\u5386\u53f2\u804a\u5929") },
                                singleLine = true
                            )
                            Text(
                                text = if (state.chatSearchResultIds.isEmpty()) "0/0" else "${state.chatSearchCurrentIndex + 1}/${state.chatSearchResultIds.size}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(onClick = { vm.moveChatSearch(-1) }) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "\u4e0a\u4e00\u4e2a")
                            }
                            IconButton(onClick = { vm.moveChatSearch(1) }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "\u4e0b\u4e00\u4e2a")
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ChatBackgroundLayer(state)
                        val bubbleMaxWidth = rememberResponsiveBubbleMaxWidth()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 3.dp,
                                end = 3.dp,
                                bottom = chatInputBarClearance
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!showHistoryStatus) {
                                item(key = "chat-launchpad") {
                                    ChatLaunchpad(
                                        state = state,
                                        onPromptSelected = vm::onInputChanged,
                                        onCreateSession = { showCreateSessionDialog = true },
                                        onOpenSettings = {
                                            vm.openSettings()
                                            settingsPageName = SettingsPanelPage.Home.name
                                            mainSurfaceName = MainSurface.Settings.name
                                        }
                                    )
                                }
                            }
                            if (showHistoryStatus) {
                                item(key = "history-status") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isLoadingOlderHistory) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .padding(end = 6.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = uiLabel("Loading chat..."),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            val statusText = if (canLoadOlderHistory) {
                                                uiLabel("Chat")
                                            } else {
                                                uiLabel("Beginning of chat")
                                            }
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        items(
                            items = compactVisibleMessages,
                            key = { it.id },
                            contentType = { message ->
                                when {
                                    message.role == "user" -> "user"
                                    message.role == "tool" -> "tool"
                                    message.role == "system" -> "system"
                                    else -> "assistant"
                                }
                            }
                        ) { message ->
                            val isUser = message.role == "user"
                            val isTool = message.role == "tool"
                            val isTrace = message.role == "trace"
                            val isSystem = message.role == "system"
                            val isDarkTheme = isSystemInDarkTheme()
                            val selectedForAction = selectedMessageIds.contains(message.id)
                            val messageActionModifier = Modifier.combinedClickable(
                                onClick = {
                                    if (multiSelectMode) {
                                        selectedMessageIds = if (selectedForAction) selectedMessageIds - message.id else (selectedMessageIds + message.id).distinct()
                                    }
                                },
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    messageActionTargetId = message.id
                                }
                            )
                            val messageExpanded = isTool && expandedToolMessages[message.id] == true
                            val themeTextColor = parseThemeColorOrNull(state.themeTextColorHex)
                            val customTypeface = rememberThemeTypeface(state.themeFontFamily, state.themeCustomFontPath)
                            val themeFontFamily = themeFontFamilyFor(state.themeFontFamily)
                            val messageFontSize = state.themeMessageFontSizeSp.coerceIn(12f, 20f).sp
                            val messageLineHeightMultiplier = state.themeMessageLineHeightMultiplier.coerceIn(1f, 1.7f)
                            val messageLineHeight = (state.themeMessageFontSizeSp.coerceIn(12f, 20f) * messageLineHeightMultiplier).sp
                            val bubbleStyle = UiBubbleStyle.fromKey(state.themeBubbleStyle)
                            val naturalTextColor = themeTextColor ?: if (isDarkTheme) Color.White.copy(alpha = 0.92f) else Color(0xFF171A20)
                            val bubbleColors = when {
                                isUser -> {
                                    val container = themedBubbleContainer(parseThemeColorOrNull(state.themeUserBubbleColorHex) ?: MaterialTheme.colorScheme.primaryContainer, bubbleStyle, state)
                                    val content = if (bubbleStyle == UiBubbleStyle.None) naturalTextColor else themeTextColor ?: readableTextColor(container, isDarkTheme)
                                    ChatBubbleColors(
                                        container = container,
                                        content = content,
                                        header = content.copy(alpha = 0.88f),
                                        time = content.copy(alpha = 0.72f)
                                    )
                                }

                                isTool -> {
                                    val container = themedBubbleContainer(parseThemeColorOrNull(state.themeToolBubbleColorHex) ?: MaterialTheme.colorScheme.secondaryContainer, bubbleStyle, state)
                                    val content = if (bubbleStyle == UiBubbleStyle.None) naturalTextColor else themeTextColor ?: readableTextColor(container, isDarkTheme)
                                    ChatBubbleColors(
                                        container = container,
                                        content = content,
                                        header = content.copy(alpha = 0.88f),
                                        time = content.copy(alpha = 0.72f)
                                    )
                                }

                                isSystem -> {
                                    val container = themedBubbleContainer(MaterialTheme.colorScheme.tertiaryContainer, bubbleStyle, state)
                                    val content = if (bubbleStyle == UiBubbleStyle.None) naturalTextColor else themeTextColor ?: readableTextColor(container, isDarkTheme)
                                    ChatBubbleColors(
                                        container = container,
                                        content = content,
                                        header = content.copy(alpha = 0.88f),
                                        time = content.copy(alpha = 0.72f)
                                    )
                                }

                                else -> {
                                    val assistantBase = parseThemeColorOrNull(state.themeAssistantBubbleColorHex) ?: if (isDarkTheme) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                    val container = themedBubbleContainer(assistantBase, bubbleStyle, state)
                                    val content = if (bubbleStyle == UiBubbleStyle.None) naturalTextColor else themeTextColor ?: readableTextColor(container, isDarkTheme)
                                    ChatBubbleColors(
                                        container = container,
                                        content = content,
                                        header = content.copy(alpha = 0.74f),
                                        time = content.copy(alpha = 0.64f)
                                    )
                                }
                            }
                            val visibleContent = if (message.role == "assistant") {
                                displayedAssistantText[message.id] ?: message.content
                            } else if (isTool && message.isCollapsible) {
                                if (messageExpanded) message.expandedContent.orEmpty() else message.content
                            } else {
                                message.content
                            }
                            val displayContent = if (
                                state.settingsUseChinese &&
                                (message.role == "assistant" || isSystem) &&
                                shouldLocalizeUiMessage(visibleContent)
                            ) {
                                localizedUiMessage(visibleContent, useChinese = true)
                            } else {
                                visibleContent
                            }
                            val pendingPlan = state.pendingPlan
                            val isPendingPlanMessage = pendingPlan != null &&
                                message.role == "assistant" &&
                                message.content.startsWith("计划模式：${pendingPlan.mode.label}") &&
                                message.content.contains(pendingPlan.planText.take(80).trim())
                            if (isUser) {
                                val isLongUserText = displayContent.length > 180 || displayContent.lines().size > 4
                                val userBubbleWidthFactor = if (isLongUserText) 0.68f else 0.82f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = if (isLongUserText) 96.dp else 64.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    ThemedMessageBubble(
                                        colors = bubbleColors,
                                        style = bubbleStyle,
                                        state = state,
                                        modifier = Modifier
                                            .widthIn(max = bubbleMaxWidth * userBubbleWidthFactor)
                                            .then(messageActionModifier)
                                    ) {
                                        CompositionLocalProvider(LocalChatBubbleColors provides bubbleColors) {
                                            Column(
                                                modifier = Modifier,
                                                horizontalAlignment = Alignment.End
                                            ) {
                                                ChatBubbleHeader(
                                                    label = state.userDisplayName.ifBlank { tr("You", "") },
                                                    createdAt = message.createdAt,
                                                    fillWidth = false
                                                )
                                                MarkdownText(
                                                    markdown = displayContent,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = if (isLongUserText) (state.themeMessageFontSizeSp.coerceIn(12f, 20f) - 0.5f).sp else messageFontSize,
                                                        lineHeight = messageLineHeight,
                                                        fontFamily = themeFontFamily
                                                    ),
                                                    inlineCodeBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                                    quoteBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                                                    codeBlockBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                                                    fillMaxWidth = false,
                                                    contentColor = bubbleColors.content,
                                                    lineHeightMultiplier = messageLineHeightMultiplier,
                                                    typeface = customTypeface
                                                )
                                                if (message.attachments.isNotEmpty()) {
                                                    MediaAttachmentList(
                                                        attachments = message.attachments,
                                                        currentPreviewAudioRef = previewAudioRef,
                                                        currentPreviewAudioDurationMs = previewAudioDurationMs,
                                                        currentPreviewAudioPositionMs = previewAudioPositionMs,
                                                        onOpenAttachment = openAttachment,
                                                        onShareAttachment = ::shareAttachment,
                                                        onSaveAttachment = ::saveImageAttachment,
                                                        onToggleAudioPreview = toggleAudioPreview
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (isTrace) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    InlineTraceFlowBar(
                                        traces = message.traceItems,
                                        collapsed = traceCollapsed,
                                        running = message.traceRunning,
                                        onToggleCollapsed = { traceCollapsed = !traceCollapsed },
                                        onTraceLongPress = { selectedTraceId = it.id },
                                        fontSizeSp = state.themeMessageFontSizeSp,
                                        lineHeightMultiplier = state.themeMessageLineHeightMultiplier,
                                        modifier = Modifier
                                            .widthIn(max = bubbleMaxWidth * 1.06f)
                                            .then(messageActionModifier)
                                    )
                                }
                            } else if (isTool) {
                                val toolGroup = toolGroupsByStartId[message.id]
                                if (toolGroup == null) {
                                    Spacer(modifier = Modifier.height(0.dp))
                                } else {
                                    val toolTypography = scaledMessageTypography(state)
                                    ToolGroupDrawerRow(
                                        group = toolGroup,
                                        typography = toolTypography,
                                        modifier = messageActionModifier,
                                        onOpenTool = { toolMessageId ->
                                            selectedToolGroupStartId = toolGroup.startId
                                            selectedToolMessageId = toolMessageId
                                            expandedToolMessages[toolMessageId] = true
                                        }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    ThemedMessageBubble(
                                        colors = bubbleColors,
                                        style = bubbleStyle,
                                        state = state,
                                        modifier = Modifier.widthIn(max = bubbleMaxWidth).then(messageActionModifier)
                                    ) {
                                        CompositionLocalProvider(LocalChatBubbleColors provides bubbleColors) {
                                            Column(
                                                modifier = Modifier
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    if (!isSystem) {
                                                        AgentAvatar(
                                                            info = assistantAvatarInfo,
                                                            fallbackText = state.currentRoleCardName.take(1).ifBlank { state.agentDisplayName.take(2).ifBlank { "AI" } },
                                                            modifier = Modifier.size(32.dp),
                                                            onClick = { mainSurfaceName = MainSurface.Agents.name },
                                                            onLongClick = {
                                                                val target = state.activeRoleCardId.takeIf { it.isNotBlank() }?.let { "role:$it" }
                                                                    ?: state.currentAgentBinding?.agentId?.takeIf { it.isNotBlank() }?.let { "agent:$it" }
                                                                if (target != null) {
                                                                    avatarPickerTarget = target
                                                                    showAvatarPickerSheet = true
                                                                }
                                                            }
                                                        )
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        ChatBubbleHeader(
                                                            label = if (isSystem) {
                                                                tr("System", "")
                                                            } else {
                                                                state.currentRoleCardName.ifBlank { state.agentDisplayName.ifBlank { "LGClaw" } }
                                                            },
                                                            createdAt = message.createdAt,
                                                            fillWidth = true
                                                        )
                                                    }
                                                }
                                                MarkdownText(
                                                    markdown = displayContent,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = messageFontSize, lineHeight = messageLineHeight, fontFamily = themeFontFamily),
                                                    inlineCodeBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                                    quoteBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                                                    codeBlockBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                                                    fillMaxWidth = false,
                                                    contentColor = bubbleColors.content,
                                                    lineHeightMultiplier = messageLineHeightMultiplier,
                                                    typeface = customTypeface
                                                )
                                                if (pendingPlan != null && isPendingPlanMessage) {
                                                    PendingPlanInlineActions(
                                                        isGenerating = state.isGenerating,
                                                        onExecute = vm::executePendingPlan,
                                                        onAdd = vm::addToPendingPlan,
                                                        onCancel = vm::clearPendingPlan
                                                    )
                                                }
                                                if (message.attachments.isNotEmpty()) {
                                                    MediaAttachmentList(
                                                        attachments = message.attachments,
                                                        currentPreviewAudioRef = previewAudioRef,
                                                        currentPreviewAudioDurationMs = previewAudioDurationMs,
                                                        currentPreviewAudioPositionMs = previewAudioPositionMs,
                                                        onOpenAttachment = openAttachment,
                                                        onShareAttachment = ::shareAttachment,
                                                        onSaveAttachment = ::saveImageAttachment,
                                                        onToggleAudioPreview = toggleAudioPreview
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showProcessingBubble) {
                            item(key = "processing-indicator") {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(18.dp),
                                        border = BorderStroke(1.dp, ModernPanelTokens.Border),
                                        modifier = Modifier.widthIn(max = 292.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AgentAvatar(
                                                info = assistantAvatarInfo,
                                                fallbackText = state.currentRoleCardName.take(1).ifBlank { "AI" },
                                                modifier = Modifier.size(30.dp),
                                                onClick = { mainSurfaceName = MainSurface.Agents.name },
                                                onLongClick = {
                                                    val target = state.activeRoleCardId.takeIf { it.isNotBlank() }?.let { "role:$it" }
                                                        ?: state.currentAgentBinding?.agentId?.takeIf { it.isNotBlank() }?.let { "agent:$it" }
                                                    if (target != null) {
                                                        avatarPickerTarget = target
                                                        showAvatarPickerSheet = true
                                                    }
                                                }
                                            )
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .padding(end = 1.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text("正在思考", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = "过程记录会直接显示在本轮消息下方",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ModernPanelTokens.Muted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ChatScrollOverlay(
                        listState = listState,
                        scrollIndicator = scrollIndicator,
                        showScrollToLatestButton = showScrollToLatestButton,
                        chatInputBarClearance = chatInputBarClearance,
                        onScrollToLatest = scrollToLatestAction
                    )
                    if (showTerminalMiniOverlay) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 74.dp, end = 10.dp)
                        ) {
                            TerminalMiniOverlayCompact(
                                state = state.terminalRuntime,
                                onExpand = { showTerminalSheet = true },
                                onDismissOverlay = {
                                    terminalMiniOverlaySuppressed = true
                                    showTerminalMiniOverlay = false
                                },
                                onRequestOverlayPermission = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    requestOverlayPermissionLauncher.launch(intent)
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .imePadding()
                    ) {
                        ChatComposerBar(
                            state = state,
                            onInputHeightChange = { inputBarSurfaceHeightPx = it },
                            onInputChanged = vm::onInputChanged,
                            onSendMessage = submitChatMessage,
                            onStopGeneration = vm::stopGeneration,
                            onPlanModeChange = vm::setPlanModeLevel,
                            onExecutePendingPlan = vm::executePendingPlan,
                            onAddToPendingPlan = vm::addToPendingPlan,
                            onClearPendingPlan = vm::clearPendingPlan,
                            onPickImages = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickAttachments = { attachmentPicker.launch(arrayOf("image/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/*", "application/*")) },
                            onRequestTerminalOverlayPermission = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                requestOverlayPermissionLauncher.launch(intent)
                            },
                            onRemoveAttachment = vm::removePendingAttachment
                        )
                    }
                }
                }

                MainSurface.Skills -> SkillsPanel(state, vm::refreshExtensionPanels, vm::createSkill, vm::setSkillEnabled, vm::deleteSkill)

                MainSurface.Tools -> ToolsPanel(state, vm::refreshExtensionPanels, vm::createDynamicTool, vm::setDynamicToolEnabled)

                MainSurface.Memory -> MemoryPanel(state, vm::refreshExtensionPanels)

                MainSurface.Theme -> ThemePanel(
                    state = state,
                    onPresetApply = vm::applyThemePreset,
                    onTextColorChange = vm::setThemeTextColorHex,
                    onFontFamilyChange = vm::setThemeFontFamily,
                    onBubbleStyleChange = vm::setThemeBubbleStyle,
                    onBubbleTuning = vm::setThemeBubbleTuning,
                    onBubbleColorsChange = vm::setThemeBubbleColors,
                    onTypographyTuning = vm::setThemeTypographyTuning,
                    onPickCustomFont = { customFontPicker.launch("*/*") },
                    onPickChatBackground = { chatBackgroundPicker.launch("image/*") },
                    onClearChatBackground = vm::clearChatBackground,
                    onChatBackgroundTuning = vm::setChatBackgroundTuning,
                    onPickDrawerBackground = { drawerBackgroundPicker.launch("image/*") },
                    onClearDrawerBackground = vm::clearDrawerBackground,
                    onDrawerBackgroundTuning = vm::setDrawerBackgroundTuning,
                    onResetThemeDefaults = vm::resetThemeDefaults,
                    onToggleLanguage = vm::toggleUiLanguage,
                    onToggleThemeMode = vm::toggleUiTheme
                )

                MainSurface.Environment -> TerminalEnvironmentPanel(
                    state = state.terminalRuntime,
                    onDetect = vm::detectTerminalEnvironment,
                    onInitialize = vm::initializeTerminalEnvironment,
                    onInstallTools = vm::installTerminalDeveloperTools,
                    onOpenTerminal = { showTerminalSheet = true },
                    onCancelTask = vm::cancelTerminalTask,
                    onClear = vm::clearTerminalOutput
                )

                MainSurface.Agents -> AgentCenterPanel(
                    state = state,
                    onRefresh = vm::refreshAgentPanels,
                    onCreateAgent = vm::createAgentProfile,
                    onCompleteAndCreateAgent = vm::completeAndCreateAgentProfile,
                    onUpdateAgent = vm::updateAgentProfile,
                    onDuplicateAgent = vm::duplicateAgentProfile,
                    onSetAgentEnabled = vm::setAgentProfileEnabled,
                    onDeleteAgent = vm::deleteAgentProfile,
                    onPreviewAgent = vm::previewAgentProfile,
                    onBindAgent = vm::bindAgentToCurrentSession,
                    onCreateRoleCard = vm::createRoleCard,
                    onBindRoleCard = vm::bindRoleCardToCurrentSession,
                    onDeleteRoleCard = vm::deleteRoleCard
                )
                MainSurface.Settings -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        SettingsContent(
                            state = state,
                            page = settingsPage,
                            permissionsDashboard = permissionsDashboard,
                            onNavigate = { target -> settingsPageName = target.name },
                            onCreateSessionRequest = { showCreateSessionDialog = true },
                            onRequestPermissions = { permissions ->
                                launchRuntimePermissionRequest(permissions)
                            },
                            onRefreshPermissionsStatus = { permissionsRefreshNonce += 1 },
                            revealApiKey = revealApiKey,
                            onRevealToggle = { revealApiKey = !revealApiKey },
                            onStartNewProviderDraft = vm::startNewProviderDraft,
                            onSelectProviderConfig = vm::selectProviderConfigForEditing,
                            onDeleteProviderConfig = vm::deleteProviderConfig,
                            onSetActiveProviderConfig = vm::setActiveProviderConfig,
                            onProviderChange = vm::onSettingsProviderChanged,
                            onProviderCustomNameChange = vm::onSettingsProviderCustomNameChanged,
                            onModelChange = vm::onSettingsModelChanged,
                            onFetchProviderModels = vm::fetchProviderModels,
                            onSetModelEquipped = vm::setModelEquipped,
                            onApiKeyChange = vm::onSettingsApiKeyChanged,
                            onBaseUrlChange = vm::onSettingsBaseUrlChanged,
                            onTestProvider = vm::testProviderSettings,
                            onSaveProviderDraft = vm::saveProviderSettings,
                            onClearProviderTokenStats = vm::clearProviderTokenUsageStats,
                            onMaxRoundsChange = vm::onSettingsMaxRoundsChanged,
                            onToolResultMaxCharsChange = vm::onSettingsToolResultMaxCharsChanged,
                            onMemoryConsolidationWindowChange = vm::onSettingsMemoryConsolidationWindowChanged,
                            onCompressionThresholdKChange = vm::onSettingsCompressionThresholdKChanged,
                            onLlmCallTimeoutSecondsChange = vm::onSettingsLlmCallTimeoutSecondsChanged,
                            onDefaultToolTimeoutSecondsChange = vm::onSettingsDefaultToolTimeoutSecondsChanged,
                            onContextMessagesChange = vm::onSettingsContextMessagesChanged,
                            onAlwaysOnEnabledChange = vm::onAlwaysOnEnabledChanged,
                            onAlwaysOnKeepScreenAwakeChange = vm::onAlwaysOnKeepScreenAwakeChanged,
                            onRefreshAlwaysOnStatus = vm::refreshAlwaysOnDiagnostics,
                            onCronEnabledChange = vm::onSettingsCronEnabledChanged,
                            onCronMinEveryMsChange = vm::onSettingsCronMinEveryMsChanged,
                            onCronMaxJobsChange = vm::onSettingsCronMaxJobsChanged,
                            onRefreshCronJobs = vm::refreshCronJobs,
                            onSetCronJobEnabled = vm::setCronJobEnabled,
                            onRunCronJobNow = vm::runCronJobNow,
                            onRemoveCronJob = vm::removeCronJob,
                            onHeartbeatEnabledChange = vm::onSettingsHeartbeatEnabledChanged,
                            onHeartbeatIntervalSecondsChange = vm::onSettingsHeartbeatIntervalSecondsChanged,
                            onSetSessionChannelEnabled = vm::setSessionChannelEnabled,
                            onMcpEnabledChange = vm::onSettingsMcpEnabledChanged,
                            onAddMcpServer = vm::addSettingsMcpServer,
                            onRemoveMcpServer = vm::removeSettingsMcpServer,
                            onMcpServerNameChange = vm::updateSettingsMcpServerName,
                            onMcpServerUrlChange = vm::updateSettingsMcpServerUrl,
                            onMcpAuthTokenChange = vm::updateSettingsMcpServerAuthToken,
                            onMcpToolTimeoutSecondsChange = vm::updateSettingsMcpServerTimeout,
                            onTriggerHeartbeatNow = vm::triggerHeartbeatNow,
                            onOpenHeartbeatEditor = {
                                vm.loadHeartbeatDocument()
                                showHeartbeatEditor = true
                            },
                            onRefreshCronLogs = vm::refreshCronLogs,
                            onClearCronLogs = vm::clearCronLogs,
                            onRefreshAgentLogs = vm::refreshAgentLogs,
                            onClearAgentLogs = vm::clearAgentLogs,
                            onCheckUpdate = vm::checkAppUpdate,
                            onNotifyUpdateDownloadStarted = vm::notifyAppUpdateDownloadStarted,
                            onNotifyUpdateDownloadFallback = vm::notifyAppUpdateDownloadFallback,
                            onSaveCurrentPage = { target ->
                                when (target) {
                                    SettingsPanelPage.AlwaysOn -> vm.saveAlwaysOnSettings(showSuccessMessage = false, showErrorMessage = false)
                                    SettingsPanelPage.Provider -> vm.saveProviderSettings(showSuccessMessage = false, showErrorMessage = false)
                                    SettingsPanelPage.Runtime -> vm.saveAgentRuntimeSettings(showSuccessMessage = false, showErrorMessage = false)
                                    SettingsPanelPage.Cron -> vm.saveCronSettings(showSuccessMessage = false, showErrorMessage = false)
                                    SettingsPanelPage.Heartbeat -> vm.saveHeartbeatSettings(showSuccessMessage = false, showErrorMessage = false)
                                    SettingsPanelPage.Channels -> vm.saveChannelsSettings(showSuccessMessage = false, showErrorMessage = false)
                                    SettingsPanelPage.Mcp -> vm.saveMcpSettings(showSuccessMessage = false, showErrorMessage = false)
                                    else -> Unit
                                }
                            }
                        )
                    }
                    SnackbarHost(
                        hostState = settingsSnackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, top = 10.dp, bottom = 200.dp),
                        snackbar = { data ->
                            val rawMessage = data.visuals.message.trim()
                            val isError = rawMessage.contains("failed", ignoreCase = true) ||
                                rawMessage.contains("error", ignoreCase = true)
                            val isStructured = rawMessage.contains('\n') ||
                                rawMessage.length > 120 ||
                                Regex("\\w+:\\s+").containsMatchIn(rawMessage)
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isError) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f)
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.96f)
                                    },
                                    contentColor = if (isError) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    tonalElevation = 6.dp,
                                    shadowElevation = 10.dp,
                                    modifier = Modifier
                                        .widthIn(max = 560.dp)
                                        .fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(if (isStructured) 6.dp else 2.dp)
                                        ) {
                                            if (isError || isStructured) {
                                                Text(
                                                    text = if (isError) uiLabel("Error") else uiLabel("Notice"),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            SelectionContainer {
                                                Text(
                                                    text = rawMessage,
                                                    style = if (isStructured) {
                                                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                                    } else {
                                                        MaterialTheme.typography.bodyMedium
                                                    },
                                                    maxLines = if (isStructured) Int.MAX_VALUE else 4,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        MinimalActionIconButton(onClick = { data.dismiss() }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = uiLabel("Close"),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        AppUpdateDialogs(
            context = context,
            state = state,
            useChinese = isChinese,
            onDismissPrompt = vm::dismissAppUpdatePrompt,
            onDismissNotice = vm::dismissAppUpdateNotice,
            onDownloadStarted = vm::notifyAppUpdateDownloadStarted,
            onDownloadFallback = vm::notifyAppUpdateDownloadFallback
        )
    }
}


@Composable
private fun PendingPlanInlineActions(
    isGenerating: Boolean,
    onExecute: () -> Unit,
    onAdd: (String) -> Unit,
    onCancel: () -> Unit
) {
    var addition by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
        Text(
            text = "计划确认",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onExecute,
                enabled = !isGenerating,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("执行", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onCancel,
                enabled = !isGenerating,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("不执行", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
        OutlinedTextField(
            value = addition,
            onValueChange = { addition = it },
            label = { Text("补充要求") },
            placeholder = { Text("例如：更保守一点、先测试、增加某个工具") },
            minLines = 1,
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
        FilledTonalButton(
            onClick = {
                onAdd(addition)
                addition = ""
            },
            enabled = addition.isNotBlank() && !isGenerating,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("添加并重拟计划", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MessageActionSheet(
    message: UiMessage,
    onCopy: () -> Unit,
    onEditResend: () -> Unit,
    onPolish: () -> Unit,
    onDelete: () -> Unit,
    onMultiSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("消息操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message.content.take(120), maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SheetActionRow(Icons.Rounded.ContentCopy, "复制消息", onCopy)
        SheetActionRow(Icons.Outlined.Edit, "编辑重发", onEditResend)
        SheetActionRow(Icons.Rounded.AutoFixHigh, "AI 润色到输入框", onPolish)
        SheetActionRow(Icons.Rounded.SelectAll, "多选消息", onMultiSelect)
        SheetActionRow(Icons.Outlined.DeleteOutline, "删除消息", onDelete, destructive = true)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InlineTraceDetailDialog(
    trace: UiInlineTrace,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("思考源头", fontWeight = FontWeight.ExtraBold, color = ModernPanelTokens.Text)
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(trace.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = ModernPanelTokens.Muted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TraceDetailPill(trace.sourceType.ifBlank { trace.title.ifBlank { "状态" } })
                    TraceDetailPill(trace.sourceName.ifBlank { "当前调度" }, soft = true)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF7F9FC),
                    border = BorderStroke(1.dp, ModernPanelTokens.Border)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("公开说明", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ModernPanelTokens.Text)
                        Text(
                            text = trace.detail.ifBlank { "暂无详情" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModernPanelTokens.Text
                        )
                    }
                }
                if (trace.rawPreview.isNotBlank() && trace.rawPreview != trace.detail) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ModernPanelTokens.Border)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("源头预览", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ModernPanelTokens.Text)
                            Text(
                                text = trace.rawPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = ModernPanelTokens.Muted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            ModernPrimaryButton(text = "知道了", onClick = onDismiss)
        }
    )
}

@Composable
private fun TraceDetailPill(
    text: String,
    soft: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (soft) Color(0xFFF5F7FB) else ModernPanelTokens.AccentSoft,
        contentColor = if (soft) ModernPanelTokens.Muted else ModernPanelTokens.Accent,
        border = if (soft) BorderStroke(1.dp, ModernPanelTokens.Border) else null
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AttachmentImagePreviewDialog(
    attachment: UiMediaAttachment,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attachment.label.ifBlank { "图片预览" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(
                            attachment.mimeType.takeIf { it.isNotBlank() },
                            attachment.sizeBytes.takeIf { it > 0L }?.let(::formatAttachmentSize)
                        ).filterNotNull().joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7B8494),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF6F8FB),
                border = BorderStroke(1.dp, Color(0xFFE6EAF1))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        attachment.contentUri.ifBlank { attachment.reference }
                    ),
                    contentDescription = attachment.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 520.dp)
                        .padding(8.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSave) { Text("保存") }
                TextButton(onClick = onShare) { Text("分享") }
                Button(
                    onClick = onOpenExternal,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                ) {
                    Text("其他应用")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun ModelPickerSheet(
    configs: List<UiProviderConfig>,
    currentModel: String,
    onSelect: (UiProviderConfig, String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .padding(start = 14.dp, end = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SheetHeader(title = "选择模型", subtitle = "按供应商分组，点击模型立即切换", onDismiss = onDismiss)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 410.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(configs, key = { it.id }) { config ->
                val providerLabel = config.customName.ifBlank { ProviderCatalog.resolve(config.providerName).title }
                val models = (config.equippedModels + config.model)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    contentColor = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFFE7ECF5)),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = Color(0xFFEAF1FF), contentColor = Color(0xFF4A6DDF)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(providerLabel.take(1).uppercase(Locale.US), fontWeight = FontWeight.Black)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(providerLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(config.providerProtocol.name.lowercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = Color(0xFF7B8495), maxLines = 1)
                            }
                            if (config.enabled) ModernStatusPill("当前供应商")
                        }
                        models.forEach { model ->
                            val selected = model == currentModel
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(config, model) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) Color(0xFFEFF5FF) else Color(0xFFF8FAFC),
                                contentColor = if (selected) Color(0xFF3156D4) else Color(0xFF202736),
                                border = BorderStroke(1.dp, if (selected) Color(0xFFBBD0FF) else Color(0xFFEAEFF6))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(model, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (selected) {
                                        Text("已选", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3156D4))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleCardPickerSheet(
    cards: List<UiRoleCard>,
    activeRoleCardId: String,
    onCreate: () -> Unit,
    onClear: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .padding(start = 14.dp, end = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SheetHeader(title = "角色卡", subtitle = "选择当前会话的人设与表达方式", onDismiss = onDismiss)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernSecondaryButton(text = "新建角色卡", onClick = onCreate, modifier = Modifier.weight(1f))
            ModernSecondaryButton(text = "解绑", onClick = onClear, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cards, key = { it.id }) { card ->
                val selected = card.id == activeRoleCardId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(card.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) Color(0xFFEFF5FF) else Color.White,
                    contentColor = Color(0xFF111827),
                    border = BorderStroke(1.dp, if (selected) Color(0xFFBBD0FF) else Color(0xFFE7ECF5)),
                    shadowElevation = if (selected) 6.dp else 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(15.dp), color = Color(0xFFF0F4FF), contentColor = Color(0xFF405BD8)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(card.avatarSymbol.ifBlank { card.name.take(1) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(card.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(card.description.ifBlank { card.persona }.ifBlank { "暂无描述" }, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (selected) ModernStatusPill("已绑定")
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111827))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text("关闭", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompactHeaderChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        contentColor = Color(0xFF242934),
        border = BorderStroke(1.dp, Color(0xFFE6EAF1)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0x33FFFFFF), Color.Transparent),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        ),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                }
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF5F6B7D))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF242934),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatBackgroundLayer(state: ChatUiState) {
    val path = state.chatBackgroundPath.trim()
    if (path.isBlank()) return
    val file = File(path)
    if (!file.exists()) return
    Image(
        painter = rememberAsyncImagePainter(file),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = state.chatBackgroundOpacity.coerceIn(0f, 1f) }
            .blur(state.chatBackgroundBlur.coerceIn(0f, 40f).dp)
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = adaptiveBackgroundScrim(
            base = MaterialTheme.colorScheme.background,
            imageOpacity = state.chatBackgroundOpacity,
            glass = state.chatBackgroundGlass,
            bubbleStyle = UiBubbleStyle.fromKey(state.themeBubbleStyle)
        )
    ) {}
}

@Composable
private fun ModernCircleToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(13.dp),
        color = Color.White,
        contentColor = Color(0xFF20242D),
        border = BorderStroke(1.dp, Color(0xFFE7EAF1)),
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x1A8FB8FF), Color.Transparent),
                            center = Offset(size.width * 0.28f, size.height * 0.18f),
                            radius = size.maxDimension * 0.72f
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ThemePanel(
    state: ChatUiState,
    onPresetApply: (String) -> Unit,
    onTextColorChange: (String) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onBubbleStyleChange: (String) -> Unit,
    onBubbleTuning: (Float, Float, Float, Float, Float, Float) -> Unit,
    onBubbleColorsChange: (String, String, String) -> Unit,
    onTypographyTuning: (Float, Float) -> Unit,
    onPickCustomFont: () -> Unit,
    onPickChatBackground: () -> Unit,
    onClearChatBackground: () -> Unit,
    onChatBackgroundTuning: (Float, Float, Float) -> Unit,
    onPickDrawerBackground: () -> Unit,
    onClearDrawerBackground: () -> Unit,
    onDrawerBackgroundTuning: (Float, Float, Float) -> Unit,
    onResetThemeDefaults: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleThemeMode: () -> Unit
    ) {
    val scrollState = rememberScrollState()
    var userColorDraft by remember(state.themeUserBubbleColorHex) { mutableStateOf(state.themeUserBubbleColorHex) }
    var assistantColorDraft by remember(state.themeAssistantBubbleColorHex) { mutableStateOf(state.themeAssistantBubbleColorHex) }
    var toolColorDraft by remember(state.themeToolBubbleColorHex) { mutableStateOf(state.themeToolBubbleColorHex) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ModernPanelTokens.Page)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ModernHeroCard(
            title = "主题中心",
            subtitle = "实时预览、气泡质感、字体排版和背景效果都会即时应用到聊天界面。",
            status = UiBubbleStyle.fromKey(state.themeBubbleStyle).label
        )

        ThemeLivePreview(state = state)

        SettingsSectionCard(title = "界面偏好", subtitle = "语言和明暗模式已收纳到主题中心，首页顶部保持干净") {
            ThemePreferenceToggleRow(
                icon = Icons.Rounded.Translate,
                title = "一键中英文",
                subtitle = if (state.settingsUseChinese) "当前使用中文界面" else "当前使用英文界面",
                checked = state.settingsUseChinese,
                onClick = onToggleLanguage
            )
            ThemePreferenceToggleRow(
                icon = if (state.settingsDarkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                title = "深色模式",
                subtitle = if (state.settingsDarkTheme) "已开启深色显示" else "已使用浅色显示",
                checked = state.settingsDarkTheme,
                onClick = onToggleThemeMode
            )
        }

        SettingsSectionCard(title = "主题预设", subtitle = "一键切换完整视觉方案，背景图片会保留") {
            ThemePresetGrid(selected = state.themePreset, onPresetApply = onPresetApply)
        }

        SettingsSectionCard(title = "消息气泡", subtitle = "毛玻璃和水玻璃会实时影响聊天消息") {
            ThemeChoiceRow(
                title = "气泡质感",
                items = UiBubbleStyle.values().map { it.key to it.label },
                selected = state.themeBubbleStyle,
                onSelect = onBubbleStyleChange
            )
            ThemeSlider("整体透明度", state.themeBubbleOpacity, 0.28f, 1f) {
                onBubbleTuning(it, state.themeBubbleCornerRadius, state.themeBubbleBorderAlpha, state.themeBubbleHighlightAlpha, state.themeBubbleShadowAlpha, state.themeBubbleGlassStrength)
            }
            ThemeSlider("圆角尺寸", state.themeBubbleCornerRadius, 8f, 28f) {
                onBubbleTuning(state.themeBubbleOpacity, it, state.themeBubbleBorderAlpha, state.themeBubbleHighlightAlpha, state.themeBubbleShadowAlpha, state.themeBubbleGlassStrength)
            }
            ThemeSlider("边框强度", state.themeBubbleBorderAlpha, 0f, 1f) {
                onBubbleTuning(state.themeBubbleOpacity, state.themeBubbleCornerRadius, it, state.themeBubbleHighlightAlpha, state.themeBubbleShadowAlpha, state.themeBubbleGlassStrength)
            }
            ThemeSlider("高光折射", state.themeBubbleHighlightAlpha, 0f, 1f) {
                onBubbleTuning(state.themeBubbleOpacity, state.themeBubbleCornerRadius, state.themeBubbleBorderAlpha, it, state.themeBubbleShadowAlpha, state.themeBubbleGlassStrength)
            }
            ThemeSlider("阴影厚度", state.themeBubbleShadowAlpha, 0f, 0.55f) {
                onBubbleTuning(state.themeBubbleOpacity, state.themeBubbleCornerRadius, state.themeBubbleBorderAlpha, state.themeBubbleHighlightAlpha, it, state.themeBubbleGlassStrength)
            }
            ThemeSlider("玻璃强度", state.themeBubbleGlassStrength, 0f, 1f) {
                onBubbleTuning(state.themeBubbleOpacity, state.themeBubbleCornerRadius, state.themeBubbleBorderAlpha, state.themeBubbleHighlightAlpha, state.themeBubbleShadowAlpha, it)
            }
            Text("气泡颜色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            ThemeColorField("我的气泡", userColorDraft) { userColorDraft = it }
            ThemeColorField("AI 气泡", assistantColorDraft) { assistantColorDraft = it }
            ThemeColorField("工具气泡", toolColorDraft) { toolColorDraft = it }
            ModernPrimaryButton(
                text = "应用气泡颜色",
                onClick = { onBubbleColorsChange(userColorDraft, assistantColorDraft, toolColorDraft) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsSectionCard(title = "文字排版", subtitle = "控制聊天消息的字体、字号、行距和文字颜色") {
            ThemeChoiceRow(
                title = "字体类型",
                items = UiFontFamilyChoice.values().map { it.key to it.label },
                selected = state.themeFontFamily,
                onSelect = onFontFamilyChange
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernSecondaryButton(text = "选择字体文件", onClick = onPickCustomFont, modifier = Modifier.weight(1f))
                ModernSecondaryButton(
                    text = "恢复系统字体",
                    onClick = { onFontFamilyChange(UiFontFamilyChoice.System.key) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.themeCustomFontPath.isNotBlank()) {
                Text(
                    text = "当前自定义字体：${File(state.themeCustomFontPath).name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ThemeSlider("消息字号", state.themeMessageFontSizeSp, 12f, 20f) {
                onTypographyTuning(it, state.themeMessageLineHeightMultiplier)
            }
            ThemeSlider("消息行距", state.themeMessageLineHeightMultiplier, 1f, 1.7f) {
                onTypographyTuning(state.themeMessageFontSizeSp, it)
            }
            var colorDraft by remember(state.themeTextColorHex) { mutableStateOf(state.themeTextColorHex) }
            ModernTextField(
                value = colorDraft,
                onValueChange = { colorDraft = it },
                label = "字体颜色",
                placeholder = "#222222，留空跟随主题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernPrimaryButton(text = "应用颜色", onClick = { onTextColorChange(colorDraft) })
                ModernSecondaryButton(text = "跟随主题", onClick = { colorDraft = ""; onTextColorChange("") })
            }
        }

        SettingsSectionCard(title = "聊天背景", subtitle = "支持图片背景、透明度、模糊度与玻璃遮罩") {
            ThemeImagePreview(
                path = state.chatBackgroundPath,
                emptyText = "未设置聊天背景"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernPrimaryButton(text = "选择图片", onClick = onPickChatBackground)
                ModernSecondaryButton(text = "清除", onClick = onClearChatBackground)
            }
            ThemeSlider("透明度", state.chatBackgroundOpacity, 0f, 1f) {
                onChatBackgroundTuning(it, state.chatBackgroundBlur, state.chatBackgroundGlass)
            }
            ThemeSlider("模糊度", state.chatBackgroundBlur, 0f, 40f) {
                onChatBackgroundTuning(state.chatBackgroundOpacity, it, state.chatBackgroundGlass)
            }
            ThemeSlider("毛玻璃遮罩", state.chatBackgroundGlass, 0f, 1f) {
                onChatBackgroundTuning(state.chatBackgroundOpacity, state.chatBackgroundBlur, it)
            }
        }

        SettingsSectionCard(title = "侧边栏背景", subtitle = "图片会自动裁剪为侧边栏比例并保持文字可读") {
            ThemeImagePreview(
                path = state.drawerBackgroundPath,
                emptyText = "未设置侧边栏背景"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernPrimaryButton(text = "选择图片", onClick = onPickDrawerBackground)
                ModernSecondaryButton(text = "清除", onClick = onClearDrawerBackground)
            }
            ThemeSlider("透明度", state.drawerBackgroundOpacity, 0f, 1f) {
                onDrawerBackgroundTuning(it, state.drawerBackgroundBlur, state.drawerBackgroundGlass)
            }
            ThemeSlider("模糊度", state.drawerBackgroundBlur, 0f, 40f) {
                onDrawerBackgroundTuning(state.drawerBackgroundOpacity, it, state.drawerBackgroundGlass)
            }
            ThemeSlider("玻璃遮罩", state.drawerBackgroundGlass, 0f, 1f) {
                onDrawerBackgroundTuning(state.drawerBackgroundOpacity, state.drawerBackgroundBlur, it)
            }
        }

        SettingsSectionCard(title = "恢复默认", subtitle = "恢复文字、气泡、聊天背景和侧边栏背景") {
            ModernSecondaryButton(text = "恢复全部主题默认", onClick = onResetThemeDefaults, modifier = Modifier.fillMaxWidth(), danger = true)
        }
    }
}

@Composable
private fun ThemePreferenceToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (checked) Color(0xFFF4F8FF) else Color.White,
        contentColor = ModernPanelTokens.Text,
        border = BorderStroke(1.dp, if (checked) Color(0xFFD8E6FF) else Color(0xFFE7EAF0)),
        shadowElevation = if (checked) 3.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (checked) Color(0xFF3977F6) else Color(0xFFF5F7FA),
                contentColor = if (checked) Color.White else ModernPanelTokens.Text
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ModernPanelTokens.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (checked) Color(0xFF111827) else Color(0xFFF1F4F8),
                contentColor = if (checked) Color.White else ModernPanelTokens.Muted,
                border = BorderStroke(1.dp, if (checked) Color(0xFF111827) else Color(0xFFE1E6EF))
            ) {
                Text(
                    text = if (checked) "已开" else "未开",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ThemeChoiceRow(
    title: String,
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { (key, label) ->
                ModernChip(text = label, selected = selected == key, onClick = { onSelect(key) })
            }
        }
    }
}

@Composable
private fun ThemePresetGrid(selected: String, onPresetApply: (String) -> Unit) {
    val presets = listOf(
        ThemePresetUi("obsidian_glass", "云白雾面", "干净、柔软、默认推荐", "#E4EEFF", "#FFFFFF"),
        ThemePresetUi("aurora_water", "浅溪水晶", "薄边缘、高透气感", "#DDF5FF", "#FBFDFF"),
        ThemePresetUi("paper_reading", "纸光阅读", "长文舒服、低刺激", "#EEF3FF", "#FFFDF8"),
        ThemePresetUi("plain_flow", "清稿无气泡", "自然贴合、长文沉浸", "#F7F8FA", "#FFFFFF"),
        ThemePresetUi("neon_night", "银蓝浮层", "浅色晶面、细腻秩序", "#EAF4FF", "#FFFFFF"),
        ThemePresetUi("native_clear", "银杏暖白", "温暖、轻快、留白克制", "#FFF0D6", "#FFFFFF")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { preset ->
                    val isSelected = selected == preset.key
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 86.dp)
                            .clickable { onPresetApply(preset.key) },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        contentColor = ModernPanelTokens.Text,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF8FB5FF) else Color(0xFFE6EAF1)),
                        shadowElevation = if (isSelected) 4.dp else 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                ThemeColorDot(preset.userColor)
                                ThemeColorDot(preset.assistantColor)
                                Spacer(modifier = Modifier.weight(1f))
                                if (isSelected) {
                                    ModernStatusPill("当前")
                                }
                            }
                            Text(preset.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = ModernPanelTokens.Muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeColorDot(hex: String) {
    Surface(
        modifier = Modifier.size(18.dp),
        shape = CircleShape,
        color = parseThemeColorOrNull(hex) ?: MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
    ) {}
}

@Composable
private fun ThemeColorField(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeColorDot(value)
        ModernTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = "#BFD8FF",
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeLivePreview(state: ChatUiState) {
    val isDarkTheme = isSystemInDarkTheme()
    val bubbleStyle = UiBubbleStyle.fromKey(state.themeBubbleStyle)
    val fontFamily = themeFontFamilyFor(state.themeFontFamily)
    val typeface = rememberThemeTypeface(state.themeFontFamily, state.themeCustomFontPath)
    val fontSize = state.themeMessageFontSizeSp.coerceIn(12f, 20f).sp
    val lineMultiplier = state.themeMessageLineHeightMultiplier.coerceIn(1f, 1.7f)
    val previewBackdrop = when (state.themePreset) {
        "aurora_water" -> listOf(Color(0xFFE6F7FF), Color(0xFFF8FBFF), Color(0xFFDDEBFF))
        "neon_night" -> listOf(Color(0xFFFFFFFF), Color(0xFFEFF6FF), Color(0xFFEAF7F3))
        "paper_reading" -> listOf(Color(0xFFFFFDF8), Color(0xFFF2EDE2), Color(0xFFFFFFFF))
        "native_clear" -> listOf(Color(0xFFFFF7E8), Color(0xFFFFFFFF), Color(0xFFFFF2D9))
        else -> listOf(Color(0xFFFFFFFF), Color(0xFFEAF3FF), Color(0xFFF7FAFF))
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        previewBackdrop.mapIndexed { index, color ->
                            color.copy(alpha = if (index == 0) 0.34f else 0.24f)
                        }
                    )
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("实时预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PreviewBubble(
                    text = "我想把今天的想法整理成一段更有力量的表达。",
                    alignEnd = true,
                    colors = previewBubbleColors(
                        parseThemeColorOrNull(state.themeUserBubbleColorHex) ?: MaterialTheme.colorScheme.primaryContainer,
                        bubbleStyle,
                        state,
                        isDarkTheme
                    ),
                    style = bubbleStyle,
                    state = state,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeightMultiplier = lineMultiplier,
                    typeface = typeface
                )
                PreviewBubble(
                    text = "可以。我会保留你的语气，把结构收紧，让句子更像一个真实的人在认真说话。",
                    alignEnd = false,
                    colors = previewBubbleColors(
                        parseThemeColorOrNull(state.themeAssistantBubbleColorHex) ?: MaterialTheme.colorScheme.surface,
                        bubbleStyle,
                        state,
                        isDarkTheme
                    ),
                    style = bubbleStyle,
                    state = state,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeightMultiplier = lineMultiplier,
                    typeface = typeface
                )
                ToolPreviewDrawer()
            }
        }
    }
}

@Composable
private fun ToolPreviewDrawer() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.88f),
        contentColor = ModernPanelTokens.Text,
        border = BorderStroke(1.dp, ModernPanelTokens.Border),
        shadowElevation = 1.dp,
        modifier = Modifier.widthIn(max = 292.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModernStatusPill("2 个工具")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("工具抽屉", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("识别图片与读取 PDF，点击查看详情", style = MaterialTheme.typography.labelSmall, color = ModernPanelTokens.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PreviewBubble(
    text: String,
    alignEnd: Boolean,
    colors: ChatBubbleColors,
    style: UiBubbleStyle,
    state: ChatUiState,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeightMultiplier: Float,
    typeface: Typeface?
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        ThemedMessageBubble(
            colors = colors,
            style = style,
            state = state,
            modifier = Modifier.widthIn(max = 292.dp)
        ) {
            MarkdownText(
                markdown = text,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * lineHeightMultiplier).sp,
                    fontFamily = fontFamily
                ),
                inlineCodeBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                quoteBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                codeBlockBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                contentColor = colors.content,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = typeface
            )
        }
    }
}

private data class ThemePresetUi(
    val key: String,
    val title: String,
    val subtitle: String,
    val userColor: String,
    val assistantColor: String
)

@Composable
private fun ThemeImagePreview(path: String, emptyText: String) {
    val file = path.trim().takeIf { it.isNotBlank() }?.let { File(it) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        if (file != null && file.exists()) {
            Image(
                painter = rememberAsyncImagePainter(file),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class ToolMessageGroup(
    val startId: Long,
    val messages: List<UiMessage>
) {
    val createdAt: Long = messages.firstOrNull()?.createdAt ?: 0L
    val status: String = messages
        .map { it.content.lowercase(Locale.US) }
        .firstOrNull { "error" in it || "failed" in it || "pending" in it }
        ?.let { if ("pending" in it) "pending" else "error" }
        ?: "ok"
    val summary: String = messages
        .asSequence()
        .mapNotNull { TraceNoteExtractor.extractAssistantNote(it.expandedContent ?: it.content) }
        .firstOrNull()
        ?: messages
            .asSequence()
            .map { TraceNoteExtractor.cleanTraceText(it.content, 72) }
            .firstOrNull { it.isNotBlank() }
        ?: "工具已返回结果"
    val attachmentCount: Int = messages.sumOf { it.attachments.size }
}

private data class ScaledMessageTypography(
    val bodySize: TextUnit,
    val bodyLineHeight: TextUnit,
    val pillTitleSize: TextUnit,
    val badgeSize: TextUnit,
    val terminalSize: TextUnit,
    val terminalLineHeight: TextUnit,
    val lineHeightMultiplier: Float,
    val fontFamily: FontFamily,
    val typeface: Typeface?
)

@Composable
private fun scaledMessageTypography(state: ChatUiState): ScaledMessageTypography {
    val base = state.themeMessageFontSizeSp.coerceIn(12f, 20f)
    val lineMultiplier = state.themeMessageLineHeightMultiplier.coerceIn(1f, 1.7f)
    return ScaledMessageTypography(
        bodySize = base.sp,
        bodyLineHeight = (base * lineMultiplier).sp,
        pillTitleSize = (base - 1f).coerceIn(11f, 18f).sp,
        badgeSize = (base - 3f).coerceIn(9f, 14f).sp,
        terminalSize = (base - 1f).coerceIn(11f, 17f).sp,
        terminalLineHeight = ((base - 1f).coerceIn(11f, 17f) * 1.35f).sp,
        lineHeightMultiplier = lineMultiplier,
        fontFamily = themeFontFamilyFor(state.themeFontFamily),
        typeface = rememberThemeTypeface(state.themeFontFamily, state.themeCustomFontPath)
    )
}

private fun buildToolMessageGroups(messages: List<UiMessage>): List<ToolMessageGroup> {
    val groups = mutableListOf<ToolMessageGroup>()
    var buffer = mutableListOf<UiMessage>()
    fun flush() {
        if (buffer.isNotEmpty()) {
            groups += ToolMessageGroup(startId = buffer.first().id, messages = buffer.toList())
            buffer = mutableListOf()
        }
    }
    messages.forEach { message ->
        if (message.role == "tool") {
            buffer += message
        } else {
            flush()
        }
    }
    flush()
    return groups
}

@Composable
private fun ToolGroupDrawerRow(
    group: ToolMessageGroup,
    typography: ScaledMessageTypography,
    modifier: Modifier = Modifier,
    onOpenTool: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 2.dp, top = 3.dp, bottom = 3.dp)
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        group.messages.forEachIndexed { index, message ->
            val status = toolStatusFromMessage(message).let { if (it == "ok" && group.status != "ok") group.status else it }
            val statusColor = toolStatusColor(status)
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { onOpenTool(message.id) }
                ),
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFF8FAFC).copy(alpha = 0.96f),
                contentColor = Color(0xFF263241),
                border = BorderStroke(1.dp, Color(0xFFE4EAF2)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = toolShortName(message, index),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = typography.pillTitleSize),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF263241),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "[${toolStatusShort(status)}]",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = typography.badgeSize),
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        maxLines = 1
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { onOpenTool(group.messages.firstOrNull()?.id ?: group.startId) }
            ),
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFEFF4F2),
            contentColor = Color(0xFF607268),
            border = BorderStroke(1.dp, Color(0xFFDDE8E4))
        ) {
            Text(
                text = "长按查看",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = typography.badgeSize),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ToolInlineDrawer(
    group: ToolMessageGroup,
    state: ChatUiState,
    typography: ScaledMessageTypography,
    expandedToolMessages: MutableMap<Long, Boolean>,
    currentPreviewAudioRef: String?,
    currentPreviewAudioDurationMs: Int,
    currentPreviewAudioPositionMs: Int,
    onOpenFullDetail: () -> Unit,
    onOpenAttachment: (UiMediaAttachment) -> Unit,
    onToggleAudioPreview: (UiMediaAttachment) -> Unit
) {
    val notes = group.messages.mapNotNull { message ->
        TraceNoteExtractor.extractAssistantNoteFull(message.expandedContent ?: message.content)
            ?.takeIf { it.isNotBlank() }
    }.distinct()
    val fallbackSummary = group.messages
        .asSequence()
        .map { TraceNoteExtractor.cleanTraceText(it.expandedContent ?: it.content, 420) }
        .firstOrNull { it.isNotBlank() }
        ?: "已完成工具调度"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolNoteBlock(
            title = toolCompletionTitle(group),
            body = notes.joinToString("\n\n").ifBlank { fallbackSummary },
            typography = typography
        )
        group.messages.forEachIndexed { index, message ->
            val cardExpanded = expandedToolMessages[message.id] == true
            ToolTerminalPreviewCard(
                message = message,
                index = index,
                expanded = cardExpanded,
                typography = typography,
                onToggleExpanded = { expandedToolMessages[message.id] = !cardExpanded },
                onOpenFullDetail = onOpenFullDetail
            )
            if (message.attachments.isNotEmpty()) {
                MediaAttachmentList(
                    attachments = message.attachments,
                    currentPreviewAudioRef = currentPreviewAudioRef,
                    currentPreviewAudioDurationMs = currentPreviewAudioDurationMs,
                    currentPreviewAudioPositionMs = currentPreviewAudioPositionMs,
                    onOpenAttachment = onOpenAttachment,
                    onToggleAudioPreview = onToggleAudioPreview
                )
            }
        }
    }
}

@Composable
private fun ToolNoteBlock(
    title: String,
    body: String,
    typography: ScaledMessageTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .width(2.dp)
                .heightIn(min = 42.dp)
                .background(Color(0xFFC9D6D0), RoundedCornerShape(999.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = typography.pillTitleSize),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF50645B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = typography.bodySize,
                    lineHeight = typography.bodyLineHeight,
                    fontFamily = typography.fontFamily
                ),
                color = Color(0xFF24312B)
            )
        }
    }
}

@Composable
private fun ToolTerminalPreviewCard(
    message: UiMessage,
    index: Int,
    expanded: Boolean,
    typography: ScaledMessageTypography,
    onToggleExpanded: () -> Unit,
    onOpenFullDetail: () -> Unit
) {
    val fullContent = message.expandedContent?.takeIf { it.isNotBlank() } ?: message.content
    val preview = TraceNoteExtractor.rawPreview(fullContent).ifBlank { "已完成工具调度" }
    val body = if (expanded) preview else preview.take(520).trim()
    val status = toolStatusFromMessage(message)
    val statusColor = toolStatusColor(status)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF131A18).copy(alpha = 0.96f),
        contentColor = Color(0xFFE8F0ED),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TerminalDot(Color(0xFFFF6B5E))
                    TerminalDot(Color(0xFFFFC857))
                    TerminalDot(Color(0xFF51D18A))
                }
                Text(
                    text = toolTitle(message, index),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = typography.pillTitleSize),
                    color = Color(0xFFE7EFEB),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ToolBadge("工具", Color(0xFF88B7A4), typography.badgeSize)
                ToolBadge(toolStatusText(status), statusColor, typography.badgeSize)
            }
            Text(
                text = body + if (!expanded && preview.length > body.length) "\n..." else "",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = typography.terminalSize,
                    lineHeight = typography.terminalLineHeight,
                    fontFamily = FontFamily.Monospace
                ),
                color = Color(0xFFD8E2DE),
                maxLines = if (expanded) 36 else 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (expanded) 360.dp else 156.dp)
                    .verticalScroll(rememberScrollState())
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleExpanded, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(
                        text = if (expanded) "收起原始片段" else "展开原始片段",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = typography.badgeSize),
                        color = Color(0xFFB9C9C2)
                    )
                }
                TextButton(onClick = onOpenFullDetail, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(
                        text = "查看完整输出",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = typography.badgeSize),
                        color = Color(0xFFD9E9E2)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalDot(color: Color) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun ToolBadge(text: String, color: Color, fontSize: TextUnit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun toolCompletionTitle(group: ToolMessageGroup): String {
    val elapsed = ((group.messages.lastOrNull()?.createdAt ?: group.createdAt) - group.createdAt)
        .coerceAtLeast(0L) / 1000L
    return if (elapsed > 0) "思考完成（用时${elapsed}秒）" else "思考完成"
}

private fun toolTitle(message: UiMessage, index: Int): String {
    val cleaned = TraceNoteExtractor.cleanTraceText(message.content, 90)
    return cleaned.substringBefore("\n").substringBefore("·").ifBlank { "工具 ${index + 1}" }
}

private fun toolShortName(message: UiMessage, index: Int): String {
    val full = message.expandedContent?.takeIf { it.isNotBlank() } ?: message.content
    val text = "$full\n${message.content}".lowercase(Locale.US)
    val known = listOf(
        "terminal_exec",
        "attachment_send",
        "attachment_save_base64",
        "attachment_import_path",
        "attachment_import_uri",
        "attachment_preview",
        "attachment_read_text",
        "message",
        "web_search"
    ).firstOrNull { it in text }
    if (known != null) return known
    val title = toolTitle(message, index)
        .replace("使用工具", "")
        .replace("工具结果", "")
        .trim(' ', '：', ':', '·')
    return title.substringBefore(" ").substringBefore("\n").take(28).ifBlank { "tool_${index + 1}" }
}

private fun toolStatusFromMessage(message: UiMessage): String {
    val text = "${message.content}\n${message.expandedContent.orEmpty()}".lowercase(Locale.US)
    return when {
        "pending" in text || "running" in text || "调度中" in text -> "pending"
        "error" in text || "failed" in text || "exception" in text || "失败" in text -> "error"
        else -> "ok"
    }
}

private fun toolStatusText(status: String): String = when (status) {
    "error" -> "失败"
    "pending" -> "调度中"
    else -> "成功"
}

private fun toolStatusShort(status: String): String = when (status) {
    "error" -> "error"
    "pending" -> "running"
    else -> "ok"
}

private fun toolStatusColor(status: String): Color = when (status) {
    "error" -> Color(0xFFD14343)
    "pending" -> Color(0xFFE19B31)
    else -> Color(0xFF39A979)
}

@Composable
private fun ToolResultsSheet(
    group: ToolMessageGroup,
    state: ChatUiState,
    selectedMessageId: Long?,
    expandedToolMessages: MutableMap<Long, Boolean>,
    currentPreviewAudioRef: String?,
    currentPreviewAudioDurationMs: Int,
    currentPreviewAudioPositionMs: Int,
    onDismiss: () -> Unit,
    onOpenAttachment: (UiMediaAttachment) -> Unit,
    onToggleAudioPreview: (UiMediaAttachment) -> Unit
) {
    val customTypeface = rememberThemeTypeface(state.themeFontFamily, state.themeCustomFontPath)
    val themeFontFamily = themeFontFamilyFor(state.themeFontFamily)
    val messageFontSize = state.themeMessageFontSizeSp.coerceIn(12f, 20f).sp
    val lineMultiplier = state.themeMessageLineHeightMultiplier.coerceIn(1f, 1.7f)
    val lineHeight = (state.themeMessageFontSizeSp.coerceIn(12f, 20f) * lineMultiplier).sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(start = 14.dp, end = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("工具详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111827))
                Text(
                    "${group.messages.size} 个工具结果" + if (group.attachmentCount > 0) " · ${group.attachmentCount} 个附件" else " · 长按工具名打开",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("关闭", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(group.messages, key = { it.id }) { message ->
                val expanded = expandedToolMessages[message.id] == true || message.id == selectedMessageId
                val fullContent = message.expandedContent?.takeIf { it.isNotBlank() } ?: message.content
                val note = TraceNoteExtractor.extractAssistantNoteFull(fullContent)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = if (message.id == selectedMessageId) Color.White else Color(0xFFFCFDFF),
                    contentColor = Color(0xFF111827),
                    border = BorderStroke(1.dp, if (message.id == selectedMessageId) Color(0xFFC9D8FF) else Color(0xFFE8EDF5)),
                    shadowElevation = if (message.id == selectedMessageId) 7.dp else 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                toolShortName(message, group.messages.indexOf(message)),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            ToolBadge(toolStatusText(toolStatusFromMessage(message)), toolStatusColor(toolStatusFromMessage(message)), (state.themeMessageFontSizeSp - 3f).coerceIn(9f, 14f).sp)
                            TextButton(
                                onClick = { expandedToolMessages[message.id] = !expandedToolMessages.getOrDefault(message.id, false) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!note.isNullOrBlank()) {
                            ToolNoteBlock(
                                title = "note / think",
                                body = note,
                                typography = scaledMessageTypography(state)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF111A18),
                            contentColor = Color(0xFFE8F0ED),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        )
                        {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = if (expanded) 340.dp else 130.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            ) {
                                MarkdownText(
                                    markdown = if (expanded) fullContent else TraceNoteExtractor.rawPreview(fullContent),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = if (expanded) messageFontSize else (state.themeMessageFontSizeSp - 1f).coerceIn(11f, 17f).sp,
                                        lineHeight = lineHeight,
                                        fontFamily = if (expanded) themeFontFamily else FontFamily.Monospace
                                    ),
                                    inlineCodeBackground = Color.White.copy(alpha = 0.10f),
                                    quoteBackground = Color.White.copy(alpha = 0.08f),
                                    codeBlockBackground = Color.Black.copy(alpha = 0.24f),
                                    contentColor = Color(0xFFE8F0ED),
                                    lineHeightMultiplier = lineMultiplier,
                                    typeface = customTypeface
                                )
                            }
                        }
                        if (message.attachments.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF6F8FB),
                                border = BorderStroke(1.dp, Color(0xFFE6EAF1))
                            ) {
                                Box(Modifier.padding(8.dp)) {
                                    MediaAttachmentList(
                                        attachments = message.attachments,
                                        currentPreviewAudioRef = currentPreviewAudioRef,
                                        currentPreviewAudioDurationMs = currentPreviewAudioDurationMs,
                                        currentPreviewAudioPositionMs = currentPreviewAudioPositionMs,
                                        onOpenAttachment = onOpenAttachment,
                                        onToggleAudioPreview = onToggleAudioPreview
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemedMessageBubble(
    colors: ChatBubbleColors,
    style: UiBubbleStyle,
    state: ChatUiState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (style == UiBubbleStyle.None) {
        Box(
            modifier = modifier
                .background(Color.Transparent)
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            content()
        }
        return
    }
    val radius = state.themeBubbleCornerRadius.coerceIn(14f, 24f).dp
    val shape = RoundedCornerShape(radius)
    val shadowAlpha = when (style) {
        UiBubbleStyle.Native -> state.themeBubbleShadowAlpha.coerceIn(0.02f, 0.12f)
        UiBubbleStyle.Frosted -> state.themeBubbleShadowAlpha.coerceIn(0.03f, 0.14f)
        UiBubbleStyle.Water -> state.themeBubbleShadowAlpha.coerceIn(0.04f, 0.16f)
        UiBubbleStyle.None -> 0f
    }
    val elevation = (shadowAlpha * 14f).dp
    Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(themedBubbleBrush(colors.container, style, state))
            .drawBehind { drawBubbleGlass(style, state, colors.container) }
            .border(themedBubbleBorder(state, style), shape)
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            content()
        }
    }
}

@Composable
private fun themedBubbleBrush(base: Color, style: UiBubbleStyle, state: ChatUiState): Brush {
    return when (style) {
        UiBubbleStyle.Native -> Brush.linearGradient(listOf(base, base))
        UiBubbleStyle.None -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
        UiBubbleStyle.Frosted -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.18f * state.themeBubbleGlassStrength.coerceIn(0f, 1f)),
                base.copy(alpha = (base.alpha + 0.04f).coerceAtMost(0.96f)),
                base.copy(alpha = (base.alpha * 0.96f).coerceIn(0.52f, 0.94f))
            ),
            start = Offset.Zero,
            end = Offset(180f, 220f)
        )
        UiBubbleStyle.Water -> Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f * state.themeBubbleGlassStrength.coerceIn(0f, 1f)),
                base.copy(alpha = (base.alpha + 0.05f).coerceAtMost(0.88f)),
                base.copy(alpha = (base.alpha * 0.86f).coerceIn(0.44f, 0.82f)),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.025f * state.themeBubbleGlassStrength.coerceIn(0f, 1f))
            ),
            start = Offset.Zero,
            end = Offset(260f, 220f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBubbleGlass(
    style: UiBubbleStyle,
    state: ChatUiState,
    base: Color
) {
    val highlight = state.themeBubbleHighlightAlpha.coerceIn(0f, 1f)
    if (style == UiBubbleStyle.Native || style == UiBubbleStyle.None || highlight <= 0.01f) return
    val corner = state.themeBubbleCornerRadius.dp.toPx()
    val strokeWidth = if (style == UiBubbleStyle.Water) 1.35.dp.toPx() else 1.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (style == UiBubbleStyle.Water) 0.42f * highlight else 0.24f * highlight),
                Color.White.copy(alpha = 0.06f * highlight),
                Color(0xFFB9C8D8).copy(alpha = if (style == UiBubbleStyle.Water) 0.12f * highlight else 0.06f * highlight)
            )
        ),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = strokeWidth)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (style == UiBubbleStyle.Water) 0.18f * highlight else 0.12f * highlight),
                Color.Transparent
            )
        ),
        size = Size(size.width, size.height * 0.42f),
        cornerRadius = CornerRadius(corner, corner)
    )
    if (style == UiBubbleStyle.Water) {
        val inset = 2.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.20f * highlight),
            topLeft = Offset(inset, inset),
            size = Size(max(0f, size.width - inset * 2), max(0f, size.height - inset * 2)),
            cornerRadius = CornerRadius(max(0f, corner - inset), max(0f, corner - inset)),
            style = Stroke(width = 0.8.dp.toPx())
        )
        drawLine(
            color = Color.White.copy(alpha = 0.18f * highlight),
            start = Offset(size.width * 0.14f, 1.4.dp.toPx()),
            end = Offset(size.width * 0.86f, 1.4.dp.toPx()),
            strokeWidth = 0.8.dp.toPx()
        )
    }
}

private fun themedBubbleBorder(state: ChatUiState, style: UiBubbleStyle): BorderStroke {
    val alpha = when (style) {
        UiBubbleStyle.Native -> state.themeBubbleBorderAlpha.coerceIn(0.16f, 0.42f)
        UiBubbleStyle.Frosted -> state.themeBubbleBorderAlpha.coerceIn(0.18f, 0.52f)
        UiBubbleStyle.Water -> state.themeBubbleBorderAlpha.coerceIn(0.24f, 0.58f)
        UiBubbleStyle.None -> 0f
    }
    val edge = when (style) {
        UiBubbleStyle.Native -> Color(0xFFDDE5F0)
        UiBubbleStyle.Frosted -> Color(0xFFDDE7F3)
        UiBubbleStyle.Water -> Color(0xFFC7D6E7)
        UiBubbleStyle.None -> Color.Transparent
    }
    return BorderStroke(1.dp, edge.copy(alpha = alpha))
}

private fun previewBubbleColors(base: Color, style: UiBubbleStyle, state: ChatUiState, isDarkTheme: Boolean): ChatBubbleColors {
    val container = themedBubbleContainer(base, style, state)
    val content = readableTextColor(container, isDarkTheme)
    return ChatBubbleColors(
        container = container,
        content = content,
        header = content.copy(alpha = 0.78f),
        time = content.copy(alpha = 0.62f)
    )
}

@Composable
private fun ThemeSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("%.2f".format(Locale.US, value.coerceIn(min, max)), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max
        )
    }
}

private fun parseThemeColorOrNull(raw: String): Color? {
    val clean = raw.trim().removePrefix("#")
    if (!clean.matches(Regex("[A-Fa-f0-9]{6}"))) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$clean")) }.getOrNull()
}

@Composable
private fun rememberThemeTypeface(key: String, customPath: String): Typeface? {
    val context = LocalContext.current
    return remember(key, customPath) {
        when (UiFontFamilyChoice.fromKey(key)) {
            UiFontFamilyChoice.Sans -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            UiFontFamilyChoice.Serif -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            UiFontFamilyChoice.Mono -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            UiFontFamilyChoice.Custom -> customPath.trim()
                .takeIf { it.isNotBlank() && File(it).exists() }
                ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
                ?: runCatching {
                    androidx.core.content.res.ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_regular)
                }.getOrNull()
            UiFontFamilyChoice.System -> null
        }
    }
}

private fun themeFontFamilyFor(key: String): FontFamily {
    return when (UiFontFamilyChoice.fromKey(key)) {
        UiFontFamilyChoice.Sans -> FontFamily.SansSerif
        UiFontFamilyChoice.Serif -> FontFamily.Serif
        UiFontFamilyChoice.Mono -> FontFamily.Monospace
        UiFontFamilyChoice.Custom -> FontFamily.Default
        UiFontFamilyChoice.System -> FontFamily.Default
    }
}

private fun themedBubbleContainer(base: Color, style: UiBubbleStyle, state: ChatUiState): Color {
    val opacity = state.themeBubbleOpacity.coerceIn(0.28f, 1f)
    val glass = state.themeBubbleGlassStrength.coerceIn(0f, 1f)
    return when (style) {
        UiBubbleStyle.Native -> base.copy(alpha = opacity.coerceIn(0.72f, 1f))
        UiBubbleStyle.Frosted -> base.copy(alpha = (opacity * (0.72f + glass * 0.18f)).coerceIn(0.38f, 0.92f))
        UiBubbleStyle.Water -> base.copy(alpha = (opacity * (0.55f + glass * 0.16f)).coerceIn(0.30f, 0.82f))
        UiBubbleStyle.None -> Color.Transparent
    }
}

private fun readableTextColor(background: Color, isDarkTheme: Boolean): Color {
    val luminance = background.luminanceEstimate()
    return if (background.alpha < 0.5f) {
        if (isDarkTheme) Color.White.copy(alpha = 0.94f) else Color(0xFF111827)
    } else if (luminance > 0.58f) {
        Color(0xFF111827)
    } else {
        Color.White.copy(alpha = 0.94f)
    }
}

private fun adaptiveBackgroundScrim(base: Color, imageOpacity: Float, glass: Float, bubbleStyle: UiBubbleStyle): Color {
    val styleBoost = when (bubbleStyle) {
        UiBubbleStyle.Native -> 0.02f
        UiBubbleStyle.Frosted -> 0.08f
        UiBubbleStyle.Water -> 0.12f
        UiBubbleStyle.None -> 0.02f
    }
    val alpha = (glass * 0.62f + imageOpacity * 0.22f + styleBoost).coerceIn(0.08f, 0.72f)
    return base.copy(alpha = alpha)
}

private fun Color.luminanceEstimate(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue).coerceIn(0f, 1f)
}

@Composable
private fun rememberResponsiveBubbleMaxWidth(): Dp {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        minOf((configuration.screenWidthDp.dp * 0.82f), 360.dp)
    }
}

private enum class MainSurface {
    Chat,
    Settings,
    Skills,
    Tools,
    Memory,
    Agents,
    Theme,
    Environment,
}



























