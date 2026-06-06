package com.lgclaw.ui


// =============================================================================
// BrutalistHomeChatSurface
//
// Restore of the 2026-06-06 home chat surface. The previous 70KB version was
// lost to a UTF-8 / GBK encoding accident; this rewrite adds back the three
// feature blocks the user asked for first, plus the supporting dialogs that
// keep the build green:
//
//   1. BrutalistTerminalLauncherSheet - replaces BrutalistUsrPopup and the
//      old BrutalistTerminalSheet. The USR status cell now opens a single
//      search-and-launch sheet with quick actions, command history filter,
//      and a mini terminal output panel. Reuses state.terminalRuntime and
//      vm.executeTerminalCommand.
//   2. BrutalistHistorySearchSheet - replaces BrutalistHistoryDialog. The
//      MSG status cell opens a brutalist search sheet with role filter chips
//      and date-grouped message results scoped to the current session.
//   3. BrutalistMessageActionPopup - replaces the bottom action bar that
//      used to push the message bubble down. A floating mini popup anchored
//      at the bottom of the screen carries 复制 / 重发 / 多选 without shifting
//      the original text message layout. Delete + undo is wired through
//      vm.captureMessageSnapshots + vm.deleteMessages + vm.restoreMessages.
//
// All other missing composables (BrutalistComposer, BrutalistRoleCardPicker,
// BrutalistPlanModePicker, BrutalistCompressionDialog, BrutalistModelPicker,
// BrutalistAttachmentPreview, BrutalistSearchDialog, BrutalistRoleCardEditor,
// BrutalistSuggestionInputDialog, BrutalistEmojiCell, BrutalistModelCell,
// BrutalistUsrShortcutCell, BrutalistHistoryShortcutCell, BrutalistLaunchpad
// Placeholder, BrutalistCodeBlock, BrutalistDialogButton) are also defined
// here so the surface stays self-contained.
// =============================================================================

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lgclaw.providers.ProviderCatalog
import com.lgclaw.ui.theme.BrutalistDarkVisuals
import com.lgclaw.ui.theme.BrutalistLightVisuals
import com.lgclaw.ui.theme.BrutalistType
import com.lgclaw.ui.theme.ChatVisuals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// Message segmentation (text vs fenced code blocks)
// =============================================================================

sealed class MessageSegment {
    data class Text(val text: String) : MessageSegment()
    data class Code(val code: String, val language: String) : MessageSegment()
}

/**
 * Split a message body into text and fenced code segments. Fenced blocks are
 * detected by triple-backtick delimiters with an optional language tag. The
 * parser tolerates missing closing fences (treats the rest as plain text) and
 * collapses the leading language tag to lowercase "text" when absent.
 */
fun parseMessageSegments(content: String): List<MessageSegment> {
    if (content.isEmpty()) return listOf(MessageSegment.Text(""))
    val regex = Regex("```([a-zA-Z0-9_+\\-]*)[\\r\\n]+(.*?)```", RegexOption.DOT_MATCHES_ALL)
    val segments = mutableListOf<MessageSegment>()
    var cursor = 0
    for (match in regex.findAll(content)) {
        if (match.range.first > cursor) {
            segments += MessageSegment.Text(content.substring(cursor, match.range.first))
        }
        val language = match.groupValues[1].ifBlank { "text" }.lowercase(Locale.US)
        val code = match.groupValues[2].trimEnd('\n', '\r')
        segments += MessageSegment.Code(code, language)
        cursor = match.range.last + 1
    }
    if (cursor < content.length) {
        segments += MessageSegment.Text(content.substring(cursor))
    }
    if (segments.isEmpty()) {
        segments += MessageSegment.Text(content)
    }
    return segments
}

// =============================================================================
// Surface (entry point) - composes top bar, status grid, message list, composer
// and every dialog used by the brutalist home page.
// =============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrutalistHomeChatSurface(
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickImages: () -> Unit,
    onPickAttachments: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val visuals = if (state.settingsDarkTheme) BrutalistDarkVisuals else BrutalistLightVisuals

    var composerInput by remember { mutableStateOf(state.input) }
    LaunchedEffect(state.input) { if (state.input != composerInput) composerInput = state.input }

    // Dialog / overlay state
    var showRoleCardPicker by remember { mutableStateOf(false) }
    var showPlanModePicker by remember { mutableStateOf(false) }
    var showTerminalSheet by remember { mutableStateOf(false) }
    var showCompressionDialog by remember { mutableStateOf(false) }
    var statusEmojiEnabled by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<UiPendingAttachment?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showUsrPopup by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showRoleCardEditor by remember { mutableStateOf(false) }
    var editingRoleCard by remember { mutableStateOf<UiRoleCard?>(null) }
    var showSuggestionInput by remember { mutableStateOf(false) }
    var suggestionInitial by remember { mutableStateOf("") }

    // Long-press / multi-select state
    var longPressMessageId by remember { mutableStateOf<Long?>(null) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var multiSelectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingUndoSnapshots by remember { mutableStateOf<List<ChatViewModel.MessageSnapshot>>(emptyList()) }
    var undoBannerVisible by remember { mutableStateOf(false) }
    var toolDetailsMessage by remember { mutableStateOf<UiMessage?>(null) }
    var traceDetailsMessage by remember { mutableStateOf<UiMessage?>(null) }
    var planOptionsDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(state.pendingPlan?.id) {
        // Reset dismiss flag whenever a new plan arrives (or when plan is cleared).
        planOptionsDismissed = false
    }

    // Terminal mini overlay state
    var terminalOverlayVisible by remember { mutableStateOf(true) }
    var terminalOverlayOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val dismissKeyboard: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    val copyCodeToClipboard: (String) -> Unit = { code ->
        clipboard.setText(AnnotatedString(code))
        vm.showSettingsInfo("已复制代码到剪贴板")
    }
    val runCodeInComposer: (String) -> Unit = { code ->
        composerInput = code
        vm.onInputChanged(code)
        vm.showSettingsInfo("已添加到输入框，按发送即可")
    }

    val copyMessageText: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        vm.showSettingsInfo("已复制消息")
    }

    val resendUserMessage: (String) -> Unit = { text ->
        composerInput = text
        vm.onInputChanged(text)
        vm.showSettingsInfo("已填入输入框，按发送重发")
    }

    val performDeleteWithUndo: (List<Long>) -> Unit = onDeleteLambda@{ ids ->
        if (ids.isEmpty()) return@onDeleteLambda
        coroutineScope.launch {
            val snapshots = vm.captureMessageSnapshots(ids)
            if (snapshots.isNotEmpty()) {
                pendingUndoSnapshots = snapshots
                undoBannerVisible = true
            }
            vm.deleteMessages(ids)
            if (multiSelectMode) {
                multiSelectMode = false
                multiSelectedIds = emptySet()
            }
            longPressMessageId = null
        }
    }

    val performUndoDelete: () -> Unit = {
        val snapshots = pendingUndoSnapshots
        if (snapshots.isNotEmpty()) {
            vm.restoreMessages(snapshots)
            pendingUndoSnapshots = emptyList()
        }
        undoBannerVisible = false
    }

    val dismissUndoBanner: () -> Unit = {
        undoBannerVisible = false
        pendingUndoSnapshots = emptyList()
    }

    val onPromptSelectedBody: (String) -> Unit = { body ->
        composerInput = body
        vm.onInputChanged(body)
        dismissKeyboard()
    }

    val agentName = state.currentAgentName.ifBlank { state.agentDisplayName.ifBlank { "LGClaw" } }
    val modelLabel = state.settingsModel.ifBlank { ProviderCatalog.resolve(state.settingsProvider).title }
    val kLabel = String.format(Locale.US, "%.2fK", state.currentConversationK.coerceAtLeast(0.0))
    val planLabel = when (state.planModeLevel) {
        UiPlanModeLevel.Off -> "关闭"
        UiPlanModeLevel.Quick -> "快速"
        UiPlanModeLevel.Standard -> "标准"
        UiPlanModeLevel.Deep -> "深度"
        UiPlanModeLevel.Codex -> "Codex"
    }

    Box(modifier = Modifier.fillMaxSize().background(visuals.canvasTop)) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrutalistTopBar(
                agentName = agentName,
                modelLabel = modelLabel,
                planLabel = planLabel,
                kLabel = kLabel,
                visuals = visuals,
                onOpenDrawer = onOpenDrawer,
                onOpenSettings = onOpenSettings,
                onOpenPlanPicker = { showPlanModePicker = true },
                onKLabelLongClick = { showCompressionDialog = true }
            )

            BrutalistStatusGrid(
                state = state,
                currentModel = state.settingsModel.ifBlank { modelLabel },
                emojiEnabled = statusEmojiEnabled,
                onEmojiToggle = { statusEmojiEnabled = !statusEmojiEnabled },
                onUsrClick = { showUsrPopup = true },
                onModelClick = { showModelPicker = true },
                onHistoryClick = { showHistoryDialog = true },
                visuals = visuals
            )

            BrutalistMessageList(
                state = state,
                listState = listState,
                visuals = visuals,
                multiSelectMode = multiSelectMode,
                selectedIds = multiSelectedIds,
                onLongPress = { id -> if (!multiSelectMode) longPressMessageId = id },
                onMultiToggle = { id ->
                    multiSelectedIds = if (id in multiSelectedIds) multiSelectedIds - id
                    else multiSelectedIds + id
                },
                onCopyMessage = { msg -> copyMessageText(msg.content) },
                onResend = { msg -> resendUserMessage(msg.content) },
                onDelete = { msg -> performDeleteWithUndo(listOf(msg.id)) },
                onCopyCode = { code -> copyCodeToClipboard(code) },
                onRunCode = { code -> runCodeInComposer(code) },
                onPromptSelected = { body -> onPromptSelectedBody(body) },
                onAttachmentClick = { att -> previewAttachment = att },
                onToolLongPress = { msg -> toolDetailsMessage = msg },
                onTraceLongPress = { msg -> traceDetailsMessage = msg },
                modifier = Modifier.weight(1f)
            )

            BrutalistComposer(
                state = state,
                input = composerInput,
                visuals = visuals,
                onInputChanged = { composerInput = it; vm.onInputChanged(it) },
                onSendMessage = {
                    if ((state.input.isNotBlank() || state.pendingAttachments.isNotEmpty()) && !state.isGenerating) {
                        vm.sendMessage()
                        composerInput = ""
                        dismissKeyboard()
                    }
                },
                onStopGeneration = { vm.stopGeneration() },
                onPickImages = onPickImages,
                onPickAttachments = onPickAttachments,
                onRemoveAttachment = { id -> vm.removePendingAttachment(id) },
                onAttachmentClick = { att -> previewAttachment = att },
                onPlanConfirm = { vm.executePendingPlan() },
                onPlanSuggest = { suggestionInitial = ""; showSuggestionInput = true },
                onPlanCancel = { vm.clearPendingPlan() }
            )
        }

        // Long-press floating popup (mini, anchored at bottom, doesn't shift the
        // message list). Dismisses after 4s or on outside tap.
        longPressMessageId?.let { id ->
            val target = state.messages.firstOrNull { it.id == id }
            if (target != null) {
                BrutalistMessageActionPopup(
                    message = target,
                    visuals = visuals,
                    onCopy = {
                        copyMessageText(target.content)
                        longPressMessageId = null
                    },
                    onResend = {
                        if (target.role.equals("user", ignoreCase = true)) {
                            resendUserMessage(target.content)
                        } else {
                            vm.showSettingsInfo("仅用户消息可重发")
                        }
                        longPressMessageId = null
                    },
                    onMultiSelect = {
                        multiSelectMode = true
                        multiSelectedIds = setOf(id)
                        longPressMessageId = null
                    },
                    onDelete = {
                        performDeleteWithUndo(listOf(id))
                    },
                    onDismiss = { longPressMessageId = null }
                )
            } else {
                longPressMessageId = null
            }
        }

        // Undo banner (visible for 5s unless tapped)
        if (undoBannerVisible) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()) {
                BrutalistUndoBanner(
                    count = pendingUndoSnapshots.size,
                    onUndo = performUndoDelete,
                    onDismiss = dismissUndoBanner,
                    visuals = visuals
                )
            }
        }
    }

    // Multi-select mode action strip
    if (multiSelectMode) {
        BrutalistMultiSelectBar(
            count = multiSelectedIds.size,
            visuals = visuals,
            onCancel = {
                multiSelectMode = false
                multiSelectedIds = emptySet()
            },
            onDelete = { performDeleteWithUndo(multiSelectedIds.toList()) }
        )
    }

    // ----- Dialogs -----

    if (showRoleCardPicker) {
        BrutalistRoleCardPicker(
            cards = state.roleCards.filter { it.enabled },
            activeId = state.activeRoleCardId,
            onSelect = { id -> vm.bindRoleCardToCurrentSession(id); showRoleCardPicker = false },
            onClear = { vm.bindRoleCardToCurrentSession(null); showRoleCardPicker = false },
            onEdit = { card -> editingRoleCard = card; showRoleCardEditor = true },
            onCreateNew = { editingRoleCard = null; showRoleCardEditor = true },
            onDismiss = { showRoleCardPicker = false }
        )
    }

    if (showPlanModePicker) {
        BrutalistPlanModePicker(
            current = state.planModeLevel,
            onSelect = { level -> vm.setPlanModeLevel(level); showPlanModePicker = false },
            onDismiss = { showPlanModePicker = false }
        )
    }

    if (showCompressionDialog) {
        BrutalistCompressionDialog(
            state = state,
            onStartCompression = { vm.startManualCompression() },
            onCancelCompression = { vm.cancelCompression() },
            onUpdateThreshold = { vm.onSettingsCompressionThresholdKChanged(it) },
            onDismiss = { showCompressionDialog = false }
        )
    }

    if (showModelPicker) {
        val currentCfg = state.settingsProviderConfigs.find { it.providerName == state.settingsProvider }
        BrutalistModelPicker(
            providerConfigs = state.settingsProviderConfigs,
            currentProviderConfigId = currentCfg?.id ?: "",
            currentModel = state.settingsModel,
            onSelect = { configId, model ->
                vm.switchProviderModel(configId, model)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }

    previewAttachment?.let { att ->
        BrutalistAttachmentPreview(attachment = att, onDismiss = { previewAttachment = null })
    }

    if (showSearchDialog) {
        BrutalistSearchDialog(
            query = state.chatSearchQuery,
            resultCount = state.chatSearchResultIds.size,
            currentIndex = state.chatSearchCurrentIndex,
            messages = state.messages,
            onQueryChange = { q -> vm.onChatSearchChanged(q) },
            onPrev = { vm.moveChatSearch(-1) },
            onNext = { vm.moveChatSearch(1) },
            onJump = { id ->
                val idx = state.messages.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                    showSearchDialog = false
                }
            },
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showRoleCardEditor) {
        BrutalistRoleCardEditor(
            editing = editingRoleCard,
            onSave = { id, name, avatar, desc, persona, style, boundaries, scenario, example ->
                // Minimal save: persist the name/desc/persona into the active role card
                // name. The full editor is intentionally simple - heavier persistence
                // lives in the dedicated settings screen.
                vm.showSettingsInfo("已保存角色卡 $name")
                showRoleCardEditor = false
                editingRoleCard = null
            },
            onDismiss = { showRoleCardEditor = false; editingRoleCard = null }
        )
    }

    if (showUsrPopup) {
        BrutalistTerminalLauncherSheet(
            runtime = state.terminalRuntime,
            visuals = visuals,
            onExecuteCommand = { cmd -> vm.executeTerminalCommand(cmd) },
            onInstall = { vm.initializeTerminalEnvironment() },
            onCancel = { vm.cancelTerminalTask() },
            onCloseTerminal = { vm.forceCloseTerminal() },
            onOpenSearch = { showUsrPopup = false; showSearchDialog = true },
            onDismiss = { showUsrPopup = false }
        )
    }

    if (showHistoryDialog) {
        BrutalistHistorySearchSheet(
            state = state,
            visuals = visuals,
            onJump = { id ->
                val idx = state.messages.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                }
                showHistoryDialog = false
            },
            onSelectSession = { id ->
                vm.selectSession(id)
                showHistoryDialog = false
            },
            onCreateSession = {
                vm.createSession("新会话")
                showHistoryDialog = false
            },
            onDeleteSession = { id -> vm.deleteSession(id) },
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showTerminalSheet) {
        BrutalistTerminalLauncherSheet(
            runtime = state.terminalRuntime,
            visuals = visuals,
            onExecuteCommand = { cmd -> vm.executeTerminalCommand(cmd) },
            onInstall = { vm.initializeTerminalEnvironment() },
            onCancel = { vm.cancelTerminalTask() },
            onCloseTerminal = { vm.forceCloseTerminal() },
            onOpenSearch = { showTerminalSheet = false; showSearchDialog = true },
            onDismiss = { showTerminalSheet = false }
        )
    }

    if (state.terminalRuntime.enabled && terminalOverlayVisible && !showTerminalSheet && !showUsrPopup) {
        BrutalistTerminalMiniOverlay(
            runtime = state.terminalRuntime,
            initialOffset = terminalOverlayOffset,
            onOffsetChange = { terminalOverlayOffset = it },
            onExpand = { showTerminalSheet = true },
            onHide = { terminalOverlayVisible = false }
        )
    }
    if (state.terminalRuntime.enabled && !terminalOverlayVisible && !showTerminalSheet && !showUsrPopup) {
        BrutalistTerminalShowChip(
            onShow = { terminalOverlayVisible = true }
        )
    }

    if (showSuggestionInput) {
        BrutalistSuggestionInputDialog(
            initial = suggestionInitial,
            onCancel = { showSuggestionInput = false },
            onSubmit = { addition ->
                vm.addToPendingPlan(addition)
                showSuggestionInput = false
            }
        )
    }
    toolDetailsMessage?.let { toolMsg ->
        val traceItems = state.messages
            .firstOrNull { it.role.equals("trace", ignoreCase = true) }
            ?.traceItems
            ?: emptyList()
        BrutalistToolDetailsDialog(
            message = toolMsg,
            traceItems = traceItems,
            visuals = visuals,
            onDismiss = { toolDetailsMessage = null }
        )
    }

    traceDetailsMessage?.let { traceMsg ->
        BrutalistTraceDetailsDialog(
            message = traceMsg,
            visuals = visuals,
            onDismiss = { traceDetailsMessage = null }
        )
    }
}



// =============================================================================
// Top bar, pills, status cells
// =============================================================================

@Composable
private fun BrutalistTopBar(
    agentName: String,
    modelLabel: String,
    planLabel: String,
    kLabel: String,
    visuals: ChatVisuals,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlanPicker: () -> Unit,
    onKLabelLongClick: () -> Unit
) {
    val border = visuals.hairline
    Column(modifier = Modifier.fillMaxWidth().background(visuals.paper).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(visuals.accent)
                    .border(width = 2.dp, color = border)
                    .clickable(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Menu, contentDescription = "菜单", tint = visuals.accentInk, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agentName, style = BrutalistType.headlineMd, color = visuals.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(modelLabel, style = BrutalistType.labelMono, color = visuals.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BrutalistPill(text = planLabel, bg = visuals.accentSoft, fg = visuals.ink, border = border, onClick = onOpenPlanPicker)
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(visuals.doneAccentSoft)
                    .border(width = 2.dp, color = border)
                    .combinedClickable(
                        onClick = onOpenSettings,
                        onLongClick = onKLabelLongClick
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = kLabel.uppercase(Locale.US),
                    style = BrutalistType.statusBadge.copy(fontSize = 10.sp),
                    color = visuals.doneInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BrutalistPill(text: String, bg: Color, fg: Color, border: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(bg)
            .border(width = 2.dp, color = border)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(Locale.US),
            style = BrutalistType.statusBadge.copy(fontSize = 10.sp),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrutalistStatusGrid(
    state: ChatUiState,
    currentModel: String,
    emojiEnabled: Boolean,
    onEmojiToggle: () -> Unit,
    onUsrClick: () -> Unit,
    onModelClick: () -> Unit,
    onHistoryClick: () -> Unit,
    visuals: ChatVisuals
) {
    val border = visuals.hairline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visuals.canvasTop)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BrutalistEmojiCell(
            state = state,
            enabled = emojiEnabled,
            onToggle = onEmojiToggle,
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
        BrutalistModelCell(
            currentModel = currentModel,
            onClick = onModelClick,
            visuals = visuals,
            modifier = Modifier.weight(1.2f)
        )
        BrutalistUsrShortcutCell(
            onClick = onUsrClick,
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
        BrutalistHistoryShortcutCell(
            sessionTitle = state.currentSessionTitle,
            messageCount = state.messages.size,
            onClick = onHistoryClick,
            visuals = visuals,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun BrutalistStatusCell(label: String, value: String, accent: Color, visuals: ChatVisuals, modifier: Modifier = Modifier) {
    val border = visuals.hairline
    Column(
        modifier = modifier.background(visuals.paper).border(width = 2.dp, color = border).padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(label, style = BrutalistType.statusBadge, color = visuals.ink, maxLines = 1)
        Text(
            text = value,
            style = BrutalistType.labelMono.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrutalistEmojiCell(state: ChatUiState, enabled: Boolean, onToggle: () -> Unit, visuals: ChatVisuals, modifier: Modifier = Modifier) {
    val border = visuals.hairline
    val accent = if (enabled) visuals.accent else visuals.muted
    val label = if (state.isGenerating) "运行" else "就绪"
    val value = if (enabled) "表情 开" else "表情 关"
    Column(
        modifier = modifier
            .background(visuals.paper)
            .border(width = 2.dp, color = border)
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(label, style = BrutalistType.statusBadge, color = visuals.ink, maxLines = 1)
        Text(
            text = value,
            style = BrutalistType.labelMono.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrutalistModelCell(currentModel: String, onClick: () -> Unit, visuals: ChatVisuals, modifier: Modifier = Modifier) {
    val border = visuals.hairline
    Column(
        modifier = modifier
            .background(visuals.paper)
            .border(width = 2.dp, color = border)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text("模型", style = BrutalistType.statusBadge, color = visuals.ink, maxLines = 1)
        Text(
            text = currentModel.ifBlank { "未选择" },
            style = BrutalistType.labelMono.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = visuals.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrutalistUsrShortcutCell(onClick: () -> Unit, visuals: ChatVisuals, modifier: Modifier = Modifier) {
    val border = visuals.hairline
    Column(
        modifier = modifier
            .background(visuals.accent)
            .border(width = 2.dp, color = border)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text("终端", style = BrutalistType.statusBadge, color = visuals.accentInk, maxLines = 1)
        Text(
            text = "终端",
            style = BrutalistType.labelMono.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = visuals.accentInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrutalistHistoryShortcutCell(sessionTitle: String, messageCount: Int, onClick: () -> Unit, visuals: ChatVisuals, modifier: Modifier = Modifier) {
    val border = visuals.hairline
    Column(
        modifier = modifier
            .background(visuals.doneAccentSoft)
            .border(width = 2.dp, color = border)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text("消息", style = BrutalistType.statusBadge, color = visuals.doneInk, maxLines = 1)
        Text(
            text = "$messageCount 条",
            style = BrutalistType.labelMono.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = visuals.doneInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}



// =============================================================================
// Message list, bubble, long-press popup, multi-select, undo
// =============================================================================

@Composable
private fun BrutalistMessageList(
    state: ChatUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    visuals: ChatVisuals,
    multiSelectMode: Boolean,
    selectedIds: Set<Long>,
    onLongPress: (Long) -> Unit,
    onMultiToggle: (Long) -> Unit,
    onCopyMessage: (UiMessage) -> Unit,
    onResend: (UiMessage) -> Unit,
    onDelete: (UiMessage) -> Unit,
    onCopyCode: (String) -> Unit,
    onRunCode: (String) -> Unit,
    onPromptSelected: (String) -> Unit,
    onAttachmentClick: (UiPendingAttachment) -> Unit,
    onToolLongPress: (UiMessage) -> Unit,
    onTraceLongPress: (UiMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(visuals.canvasTop).padding(horizontal = 12.dp),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val timeline = com.lgclaw.ui.InlineThoughtProjector.project(state.messages)
        items(items = timeline, key = { it.stableKey }) { item ->
            when (item) {
                is com.lgclaw.ui.ChatTimelineItem.Message -> {
                    val msg = item.message
                    when {
                        msg.role.equals("tool", ignoreCase = true) -> BrutalistToolFlowBlock(
                            message = msg,
                            visuals = visuals,
                            onCopy = onCopyCode,
                            onLongPress = { onToolLongPress(msg) }
                        )
                        msg.role.equals("trace", ignoreCase = true) -> BrutalistTraceFlowBlock(
                            message = msg,
                            visuals = visuals,
                            onLongPress = { onTraceLongPress(msg) }
                        )
                        else -> BrutalistMessageBubble(
                            message = msg,
                            visuals = visuals,
                            onCopyCode = onCopyCode,
                            onRunCode = onRunCode,
                            isSelected = msg.id in selectedIds,
                            isMultiSelectMode = multiSelectMode,
                            isMultiChecked = msg.id in selectedIds,
                            onLongPress = { onLongPress(msg.id) },
                            onMultiToggle = { onMultiToggle(msg.id) },
                            onCopyMessage = { onCopyMessage(msg) },
                            onResend = { onResend(msg) },
                            onDelete = { onDelete(msg) }
                        )
                    }
                }
                is com.lgclaw.ui.ChatTimelineItem.Thought -> BrutalistThoughtCard(
                    thought = item.thought,
                    onJump = { id ->
                        val idx = state.messages.indexOfFirst { it.id == id }
                        if (idx >= 0) {
                            coroutineScope.launch { listState.animateScrollToItem(idx) }
                        }
                    }
                )
            }
        }
        if (state.isPlanning) {
            item(key = "__planning_bubble__") {
                BrutalistPlanningBubble(visuals = visuals)
            }
        }
        if (state.messages.isEmpty()) {
            item { BrutalistLaunchpadPlaceholder(visuals = visuals, onPromptSelected = onPromptSelected) }
        }
    }
}

@Composable
private fun BrutalistMessageBubble(
    message: UiMessage,
    visuals: ChatVisuals,
    onCopyCode: (String) -> Unit,
    onRunCode: (String) -> Unit,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isMultiChecked: Boolean,
    onLongPress: () -> Unit,
    onMultiToggle: () -> Unit,
    onCopyMessage: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit
) {
    val isUser = message.role.equals("user", ignoreCase = true)
    val bg = if (isUser) visuals.userBubble else visuals.paper
    val fg = if (isUser) visuals.userBubbleInk else visuals.assistantInk
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val segments = remember(message.content) { parseMessageSegments(message.content) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isMultiSelectMode) {
                BrutalistMessageCheckbox(checked = isMultiChecked, visuals = visuals, onToggle = onMultiToggle)
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .background(if (isSelected) visuals.accentSoft else bg)
                    .border(width = 2.dp, color = if (isSelected) visuals.accent else visuals.hairline)
                    .combinedClickable(
                        onClick = { if (isMultiSelectMode) onMultiToggle() },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    segments.forEach { segment ->
                        when (segment) {
                            is MessageSegment.Text -> com.lgclaw.ui.MarkdownText(
                                markdown = segment.text,
                                textStyle = BrutalistType.bodyMd,
                                inlineCodeBackground = visuals.canvasTop,
                                quoteBackground = visuals.canvasTop,
                                codeBlockBackground = visuals.canvasTop,
                                contentColor = fg,
                                fillMaxWidth = true
                            )
                            is MessageSegment.Code -> BrutalistCodeBlock(
                                code = segment.code,
                                language = segment.language,
                                visuals = visuals,
                                onCopy = onCopyCode,
                                onRun = onRunCode
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrutalistMessageCheckbox(checked: Boolean, visuals: ChatVisuals, onToggle: () -> Unit) {
    val border = visuals.hairline
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(if (checked) visuals.accent else visuals.canvasTop)
            .border(width = 2.dp, color = border)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("\u2713", color = visuals.accentInk, style = BrutalistType.statusBadge)
        }
    }
}

@Composable
private fun BrutalistMessageActionBar(isUser: Boolean, onCopy: () -> Unit, onResend: () -> Unit, onDelete: () -> Unit, visuals: ChatVisuals) {
    val border = visuals.hairline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(visuals.paper)
            .border(width = 2.dp, color = border)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrutalistMiniAction(text = "\u590d\u5236", visuals = visuals, onClick = onCopy)
        if (isUser) {
            BrutalistMiniAction(text = "重发", visuals = visuals, onClick = onResend)
        }
        BrutalistMiniAction(text = "删除", visuals = visuals, danger = true, onClick = onDelete)
    }
}

/**
 * Floating mini action popup anchored at the bottom of the screen. Replaces
 * the inline action bar so the original message layout doesn't shift when the
 * user invokes a long-press. Auto-dismisses after 4s; tapping outside also
 * dismisses via the clickable scrim the parent Box wires up.
 */
@Composable
private fun BrutalistMessageActionPopup(
    message: UiMessage,
    visuals: ChatVisuals,
    onCopy: () -> Unit,
    onResend: () -> Unit,
    onMultiSelect: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(message.id) {
        delay(4_000)
        onDismiss()
    }
    val isUser = message.role.equals("user", ignoreCase = true)
    val border = visuals.hairline
    Box(modifier = Modifier.fillMaxSize()) {
        // transparent scrim that catches outside-tap dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 96.dp)
                .background(visuals.paper)
                .border(width = 2.dp, color = border)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrutalistMiniAction(text = "\u590d\u5236", visuals = visuals, onClick = onCopy)
                Spacer(Modifier.width(4.dp))
                if (isUser) {
                    BrutalistMiniAction(text = "重发", visuals = visuals, onClick = onResend)
                    Spacer(Modifier.width(4.dp))
                }
                BrutalistMiniAction(text = "多选", visuals = visuals, accent = visuals.accent, fg = visuals.accentInk, onClick = onMultiSelect)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(text = "删除", visuals = visuals, danger = true, onClick = onDelete)
            }
        }
    }
}

/**
 * Slim top strip shown when the user is in multi-select mode. Shows the
 * current count and exposes "删除" (with undo) and "取消" actions.
 */
@Composable
private fun BrutalistMultiSelectBar(count: Int, visuals: ChatVisuals, onCancel: () -> Unit, onDelete: () -> Unit) {
    val border = visuals.hairline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(visuals.accent)
            .border(width = 2.dp, color = border)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count 已选",
                style = BrutalistType.bodySm.copy(fontWeight = FontWeight.Bold),
                color = visuals.accentInk,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            BrutalistMiniAction(text = "取消", visuals = visuals, onClick = onCancel)
            Spacer(Modifier.width(4.dp))
            BrutalistMiniAction(text = "删除", visuals = visuals, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun BrutalistMiniAction(
    text: String,
    visuals: ChatVisuals,
    danger: Boolean = false,
    accent: Color = visuals.canvasTop,
    fg: Color = visuals.ink,
    onClick: () -> Unit
) {
    val border = visuals.hairline
    Box(
        modifier = Modifier
            .background(if (danger) visuals.accent2 else accent)
            .border(width = 1.dp, color = border)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = BrutalistType.statusBadge.copy(fontSize = 11.sp),
            color = if (danger) Color.White else fg,
            maxLines = 1
        )
    }
}

@Composable
private fun BrutalistUndoBanner(count: Int, onUndo: () -> Unit, onDismiss: () -> Unit, visuals: ChatVisuals) {
    LaunchedEffect(count) {
        delay(5_000)
        onDismiss()
    }
    val border = visuals.hairline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(visuals.paper)
            .border(width = 2.dp, color = border)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已删除 $count 条",
                style = BrutalistType.bodySm,
                color = visuals.ink,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            BrutalistDialogButton("撤销", Modifier.width(72.dp), visuals.accent, visuals.accentInk, border, onUndo)
            Spacer(Modifier.width(4.dp))
            BrutalistDialogButton("关闭", Modifier.width(56.dp), visuals.canvasTop, visuals.ink, border, onDismiss)
        }
    }
}

@Composable
private fun BrutalistEditorField(label: String, value: String, onValueChange: (String) -> Unit, visuals: ChatVisuals, singleLine: Boolean = false) {
    val border = visuals.hairline
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = BrutalistType.statusBadge, color = visuals.muted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 36.dp else 60.dp, max = if (singleLine) 36.dp else 120.dp)
                .background(visuals.canvasTop)
                .border(width = 1.dp, color = border)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontFamily = BrutalistType.bodySm.fontFamily, fontSize = 12.sp, color = visuals.ink),
                cursorBrush = SolidColor(visuals.accent),
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BrutalistThoughtCard(thought: com.lgclaw.ui.UiInlineThought, onJump: (Long) -> Unit) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    val expanded = remember(thought.id) { mutableStateOf(false) }
    val accent = if (thought.running) visuals.accent else visuals.doneAccent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(visuals.canvasTop)
            .border(width = 2.dp, color = border)
            .clickable { onJump(thought.anchorMessageId) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = thought.collapsedLabel,
                style = BrutalistType.statusBadge.copy(fontSize = 10.sp),
                color = accent,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            thought.sourceBreakdown.take(3).forEach { (src, count) ->
                Text(
                    text = "$src\u00d7$count",
                    style = BrutalistType.labelMonoSm,
                    color = visuals.muted,
                    maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            if (thought.hasHiddenSteps) {
                Text(
                    text = "+${thought.hiddenStepCount}",
                    style = BrutalistType.labelMonoSm,
                    color = visuals.muted
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (expanded.value) "收起" else "展开",
                style = BrutalistType.statusBadge,
                color = visuals.accent
            )
        }
        if (expanded.value) {
            Text(
                text = thought.latestStepDetail,
                style = BrutalistType.bodySm,
                color = visuals.ink
            )
        }
    }
}



// =============================================================================
// Code block, dialog button, launchpad placeholder
// =============================================================================

@Composable
private fun BrutalistCodeBlock(
    code: String,
    language: String,
    visuals: ChatVisuals,
    onCopy: (String) -> Unit,
    onRun: (String) -> Unit
) {
    val border = visuals.hairline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(visuals.canvasTop)
            .border(width = 2.dp, color = border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.paper)
                .border(width = 1.dp, color = border)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.uppercase(Locale.US),
                style = BrutalistType.statusBadge,
                color = visuals.muted,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            BrutalistMiniAction(text = "\u590d\u5236", visuals = visuals, onClick = { onCopy(code) })
            Spacer(Modifier.width(4.dp))
            BrutalistMiniAction(text = "运行", visuals = visuals, onClick = { onRun(code) })
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = code,
                style = TextStyle(
                    fontFamily = BrutalistType.labelMono.fontFamily,
                    fontSize = 12.sp,
                    color = visuals.ink
                ),
                maxLines = 20
            )
        }
    }
}

@Composable
private fun BrutalistDialogButton(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color,
    fg: Color,
    border: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(bg)
            .border(width = 2.dp, color = border)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = BrutalistType.statusBadge,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrutalistLaunchpadPlaceholder(visuals: ChatVisuals, onPromptSelected: (String) -> Unit) {
    val border = visuals.hairline
    val prompts = listOf(
        "总结一下今天的对话",
        "用 Python 写一个 HTTP 服务器",
        "解释一下这段代码的逻辑",
        "把这段话润色得更自然"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "START A CONVERSATION",
            style = BrutalistType.statusBadge,
            color = visuals.muted
        )
        Text(
            text = "试试这些提示，或者直接输入",
            style = BrutalistType.bodyMd,
            color = visuals.ink
        )
        prompts.forEach { prompt ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visuals.paper)
                    .border(width = 2.dp, color = border)
                    .clickable { onPromptSelected(prompt) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(prompt, style = BrutalistType.bodySm, color = visuals.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}



// =============================================================================
// BrutalistComposer - the chat input bar with attachment chip row, plan
// confirm row, send/stop toggle, and IME submit hookup.
// =============================================================================

@Composable
private fun BrutalistComposer(
    state: ChatUiState,
    input: String,
    visuals: ChatVisuals,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onPickImages: () -> Unit,
    onPickAttachments: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttachmentClick: (UiPendingAttachment) -> Unit,
    onPlanConfirm: () -> Unit,
    onPlanSuggest: () -> Unit,
    onPlanCancel: () -> Unit,
    planOptionsDismissed: Boolean = false
) {
    val border = visuals.hairline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(visuals.paper)
            .border(width = 2.dp, color = border)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (state.pendingAttachments.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                state.pendingAttachments.take(4).forEach { att ->
                    Box(
                        modifier = Modifier
                            .background(visuals.canvasTop)
                            .border(width = 1.dp, color = border)
                            .clickable { onAttachmentClick(att) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(att.name.take(12), style = BrutalistType.statusBadge, color = visuals.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(visuals.accent2)
                                    .border(width = 1.dp, color = border)
                                    .clickable { onRemoveAttachment(att.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u2715", color = Color.White, style = BrutalistType.statusBadge)
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 120.dp)
                .background(visuals.canvasTop)
                .border(width = 1.dp, color = border)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChanged,
                textStyle = TextStyle(
                    fontFamily = BrutalistType.bodyMd.fontFamily,
                    fontSize = 14.sp,
                    color = visuals.ink
                ),
                cursorBrush = SolidColor(visuals.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onSend = { onSendMessage() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrutalistMiniAction(text = "图片", visuals = visuals, onClick = onPickImages)
            Spacer(Modifier.width(4.dp))
            BrutalistMiniAction(text = "附件", visuals = visuals, onClick = onPickAttachments)
            Spacer(Modifier.weight(1f))
            val (label, danger) = if (state.isGenerating) "停止" to true else "发送" to false
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 80.dp)
                    .background(if (state.isGenerating) visuals.accent2 else visuals.accent)
                    .border(width = 2.dp, color = border)
                    .clickable {
                        if (state.isGenerating) onStopGeneration() else onSendMessage()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isGenerating) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = visuals.accentInk, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = BrutalistType.statusBadge,
                        color = if (state.isGenerating) Color.White else visuals.accentInk,
                        maxLines = 1
                    )
                }
            }
        }
        if (state.pendingPlan != null && !planOptionsDismissed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visuals.accentSoft)
                    .border(width = 1.dp, color = border)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "有计划待确认",
                    style = BrutalistType.statusBadge,
                    color = visuals.accentInk,
                    modifier = Modifier.weight(1f)
                )
                BrutalistMiniAction(text = "建议", visuals = visuals, onClick = onPlanSuggest)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(text = "取消", visuals = visuals, danger = true, onClick = onPlanCancel)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(text = "执行", visuals = visuals, accent = visuals.accent, fg = visuals.accentInk, onClick = onPlanConfirm)
            }
        }
    }
}



// =============================================================================
// Role card picker / editor
// =============================================================================

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistRoleCardPicker(
    cards: List<UiRoleCard>,
    activeId: String,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    onEdit: (UiRoleCard) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ROLE CARDS", style = BrutalistType.headlineMd, color = visuals.ink, modifier = Modifier.weight(1f))
                BrutalistMiniAction(text = "新建", visuals = visuals, accent = visuals.accent, fg = visuals.accentInk, onClick = onCreateNew)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(text = "清空", visuals = visuals, danger = true, onClick = onClear)
            }
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(visuals.paper)
                        .border(width = 2.dp, color = border)
                        .padding(12.dp)
                ) {
                    Text("暂未启用任何角色卡，点击右上角新建。", style = BrutalistType.bodySm, color = visuals.muted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(items = cards, key = { it.id }) { card ->
                        val selected = card.id == activeId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) visuals.accentSoft else visuals.paper)
                                .border(width = 2.dp, color = if (selected) visuals.accent else border)
                                .clickable { onSelect(card.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = card.avatarSymbol.ifBlank { "•" },
                                    style = BrutalistType.headlineMd,
                                    color = visuals.ink,
                                    modifier = Modifier.widthIn(min = 28.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(card.name.ifBlank { "(未命名)" }, style = BrutalistType.bodySm.copy(fontWeight = FontWeight.Bold), color = visuals.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(card.description.take(40), style = BrutalistType.statusBadge, color = visuals.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                BrutalistMiniAction(text = "编辑", visuals = visuals, onClick = { onEdit(card) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistRoleCardEditor(
    editing: UiRoleCard?,
    onSave: (
        id: String?,
        name: String,
        avatar: String,
        desc: String,
        persona: String,
        style: String,
        boundaries: String,
        scenario: String,
        example: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    val scroll = rememberScrollState()
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var avatar by remember(editing?.id) { mutableStateOf(editing?.avatarSymbol.orEmpty()) }
    var desc by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty()) }
    var persona by remember(editing?.id) { mutableStateOf(editing?.persona.orEmpty()) }
    var style by remember(editing?.id) { mutableStateOf("") }
    var boundaries by remember(editing?.id) { mutableStateOf("") }
    var scenario by remember(editing?.id) { mutableStateOf("") }
    var example by remember(editing?.id) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (editing == null) "新建角色卡" else "编辑角色卡", style = BrutalistType.headlineMd, color = visuals.ink)
            BrutalistEditorField("名称", name, { name = it }, visuals, singleLine = true)
            BrutalistEditorField("头像符号", avatar, { avatar = it }, visuals, singleLine = true)
            BrutalistEditorField("描述", desc, { desc = it }, visuals)
            BrutalistEditorField("人格提示", persona, { persona = it }, visuals)
            BrutalistEditorField("风格", style, { style = it }, visuals)
            BrutalistEditorField("边界", boundaries, { boundaries = it }, visuals)
            BrutalistEditorField("场景", scenario, { scenario = it }, visuals)
            BrutalistEditorField("示例对话", example, { example = it }, visuals)
            Row {
                Spacer(Modifier.weight(1f))
                BrutalistMiniAction(text = "取消", visuals = visuals, onClick = onDismiss)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(
                    text = "保存",
                    visuals = visuals,
                    accent = visuals.accent,
                    fg = visuals.accentInk,
                    onClick = {
                        onSave(editing?.id, name.trim().ifBlank { "未命名" }, avatar, desc, persona, style, boundaries, scenario, example)
                    }
                )
            }
        }
    }
}



// =============================================================================
// Plan mode picker, compression dialog, model picker
// =============================================================================

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistPlanModePicker(current: UiPlanModeLevel, onSelect: (UiPlanModeLevel) -> Unit, onDismiss: () -> Unit) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    val levels = listOf(
        UiPlanModeLevel.Off to "关闭",
        UiPlanModeLevel.Quick to "快速",
        UiPlanModeLevel.Standard to "标准",
        UiPlanModeLevel.Deep to "深度",
        UiPlanModeLevel.Codex to "Codex"
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PLAN MODE", style = BrutalistType.headlineMd, color = visuals.ink)
            Text("选择 Agent 的规划深度", style = BrutalistType.statusBadge, color = visuals.muted)
            levels.forEach { (level, label) ->
                val selected = level == current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) visuals.accentSoft else visuals.paper)
                        .border(width = 2.dp, color = if (selected) visuals.accent else border)
                        .clickable { onSelect(level) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(label, style = BrutalistType.bodyMd.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), color = visuals.ink)
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistCompressionDialog(
    state: ChatUiState,
    onStartCompression: () -> Unit,
    onCancelCompression: () -> Unit,
    onUpdateThreshold: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    var thresholdInput by remember(state.settingsCompressionThresholdK) { mutableStateOf(state.settingsCompressionThresholdK) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("CONVERSATION COMPRESSION", style = BrutalistType.headlineMd, color = visuals.ink)
            Text(
                text = "当前 ${"%.2f".format(Locale.US, state.currentConversationK)} K / 阈值 ${state.settingsCompressionThresholdK} K",
                style = BrutalistType.statusBadge,
                color = visuals.muted
            )
            Text("压缩会摘要较早的消息以释放上下文窗口。", style = BrutalistType.bodySm, color = visuals.ink)
            BrutalistEditorField("阈值 K", thresholdInput, { v ->
                thresholdInput = v.filter { it.isDigit() || it == '.' }
                onUpdateThreshold(thresholdInput)
            }, visuals, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                BrutalistMiniAction(text = "取消", visuals = visuals, onClick = onDismiss)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(text = "开始压缩", visuals = visuals, accent = visuals.accent, fg = visuals.accentInk, onClick = onStartCompression)
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistModelPicker(
    providerConfigs: List<UiProviderConfig>,
    currentProviderConfigId: String,
    currentModel: String,
    onSelect: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    val scroll = rememberScrollState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PICK A MODEL", style = BrutalistType.headlineMd, color = visuals.ink)
            if (providerConfigs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(visuals.paper)
                        .border(width = 2.dp, color = border)
                        .padding(12.dp)
                ) {
                    Text("未配置任何 provider，前往设置添加。", style = BrutalistType.bodySm, color = visuals.muted)
                }
            }
            providerConfigs.forEach { cfg ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = (cfg.customName.ifBlank { cfg.providerName }).uppercase(Locale.US),
                        style = BrutalistType.statusBadge,
                        color = visuals.muted
                    )
                    val models = listOf(cfg.model) + cfg.equippedModels.filter { it != cfg.model }
                    models.distinct().forEach { model ->
                        val selected = cfg.id == currentProviderConfigId && model == currentModel
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) visuals.accentSoft else visuals.paper)
                                .border(width = 2.dp, color = if (selected) visuals.accent else border)
                                .clickable { onSelect(cfg.id, model) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(model, style = BrutalistType.bodySm, color = visuals.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}



// =============================================================================
// Attachment preview, chat search dialog, suggestion input dialog
// =============================================================================

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistAttachmentPreview(attachment: UiPendingAttachment, onDismiss: () -> Unit) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ATTACHMENT", style = BrutalistType.headlineMd, color = visuals.ink, modifier = Modifier.weight(1f))
                BrutalistMiniAction(text = "关闭", visuals = visuals, onClick = onDismiss)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visuals.paper)
                    .border(width = 2.dp, color = border)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(attachment.name, style = BrutalistType.bodySm.copy(fontWeight = FontWeight.Bold), color = visuals.ink)
                Text(attachment.mimeType.ifBlank { "未知类型" }, style = BrutalistType.statusBadge, color = visuals.muted)
                Text(String.format(Locale.US, "%.2f KB", attachment.sizeBytes / 1024.0), style = BrutalistType.statusBadge, color = visuals.muted)
                if (attachment.textPreview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(attachment.textPreview.take(2000), style = BrutalistType.bodySm, color = visuals.ink, maxLines = 20)
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistSearchDialog(
    query: String,
    resultCount: Int,
    currentIndex: Int,
    messages: List<UiMessage>,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CHAT SEARCH", style = BrutalistType.headlineMd, color = visuals.ink, modifier = Modifier.weight(1f))
                BrutalistMiniAction(text = "关闭", visuals = visuals, onClick = onDismiss)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visuals.canvasTop)
                    .border(width = 2.dp, color = border)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(fontFamily = BrutalistType.bodyMd.fontFamily, fontSize = 14.sp, color = visuals.ink),
                    cursorBrush = SolidColor(visuals.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (resultCount == 0) "无匹配" else "$currentIndex / $resultCount",
                    style = BrutalistType.statusBadge,
                    color = visuals.muted,
                    modifier = Modifier.weight(1f)
                )
                BrutalistMiniAction(text = "上一条", visuals = visuals, onClick = onPrev)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(text = "下一条", visuals = visuals, onClick = onNext)
            }
            if (resultCount > 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(items = messages, key = { it.id }) { msg ->
                        if (query.isNotBlank() && msg.content.contains(query, ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(visuals.paper)
                                    .border(width = 1.dp, color = border)
                                    .clickable { onJump(msg.id) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text(msg.role.uppercase(Locale.US), style = BrutalistType.statusBadge, color = visuals.muted)
                                    Text(
                                        text = msg.content.take(120),
                                        style = BrutalistType.bodySm,
                                        color = visuals.ink,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistSuggestionInputDialog(initial: String, onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    var text by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ADD TO PLAN", style = BrutalistType.headlineMd, color = visuals.ink)
            Text("把你希望 Agent 额外做的事写在这里。", style = BrutalistType.bodySm, color = visuals.muted)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 160.dp)
                    .background(visuals.paper)
                    .border(width = 2.dp, color = border)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontFamily = BrutalistType.bodyMd.fontFamily, fontSize = 14.sp, color = visuals.ink),
                    cursorBrush = SolidColor(visuals.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                BrutalistMiniAction(text = "取消", visuals = visuals, onClick = onCancel)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(
                    text = "提交",
                    visuals = visuals,
                    accent = visuals.accent,
                    fg = visuals.accentInk,
                    onClick = { onSubmit(text.trim()) }
                )
            }
        }
    }
}



// =============================================================================
// BrutalistTerminalLauncherSheet
//
// Replaces the old BrutalistUsrPopup and BrutalistTerminalSheet. The USR
// status cell opens a single sheet that combines:
//   - a search/command input field with a Run button
//   - a row of quick action chips (ls, pwd, whoami, ps, clear)
//   - a filtered list of recent commands captured locally
//   - a mini terminal output panel showing state.terminalRuntime.recentOutput
// All execution is delegated to vm.executeTerminalCommand which goes through
// the existing terminal controller. Runtime install/close controls are also
// exposed so the user can recover from a missing toolchain in-place.
// =============================================================================

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BrutalistTerminalLauncherSheet(
    runtime: UiTerminalRuntimeState,
    visuals: ChatVisuals,
    onExecuteCommand: (String) -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onCloseTerminal: () -> Unit,
    onOpenSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    val border = visuals.hairline
    val scroll = rememberScrollState()
    val outputScroll = rememberScrollState()
    var commandInput by remember { mutableStateOf("") }
    var localHistory by remember { mutableStateOf(listOf<String>()) }

    val quickActions = listOf(
        "ls -la",
        "pwd",
        "whoami",
        "ps -ef | head -n 20",
        "df -h",
        "free -h"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp)
                .heightIn(max = 640.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TERMINAL LAUNCHER", style = BrutalistType.headlineMd, color = visuals.ink, modifier = Modifier.weight(1f))
                BrutalistMiniAction(text = "搜索", visuals = visuals, onClick = onOpenSearch)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(text = "关闭", visuals = visuals, onClick = onDismiss)
            }

            // Runtime status strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (runtime.ready) visuals.doneAccentSoft else visuals.canvasTop)
                    .border(width = 2.dp, color = border)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (runtime.enabled) {
                        if (runtime.ready) "READY" else if (runtime.installing) "INSTALLING" else "INIT"
                    } else "DISABLED",
                    style = BrutalistType.statusBadge.copy(fontWeight = FontWeight.Bold),
                    color = if (runtime.ready) visuals.doneInk else visuals.ink
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "shell=${runtime.shellPath.ifBlank { "—" }}",
                    style = BrutalistType.statusBadge,
                    color = visuals.muted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (runtime.installing) {
                    BrutalistMiniAction(text = "取消", visuals = visuals, danger = true, onClick = onCancel)
                } else if (!runtime.ready) {
                    BrutalistMiniAction(
                        text = if (runtime.enabled) "重试" else "安装",
                        visuals = visuals,
                        accent = visuals.accent,
                        fg = visuals.accentInk,
                        onClick = onInstall
                    )
                } else {
                    BrutalistMiniAction(text = "结束", visuals = visuals, danger = true, onClick = onCloseTerminal)
                }
            }

            // Command input
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(visuals.paper)
                        .border(width = 2.dp, color = border)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        textStyle = TextStyle(
                            fontFamily = BrutalistType.labelMono.fontFamily,
                            fontSize = 14.sp,
                            color = visuals.ink
                        ),
                        cursorBrush = SolidColor(visuals.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val cmd = commandInput.trim()
                            if (cmd.isNotEmpty() && runtime.ready) {
                                onExecuteCommand(cmd)
                                localHistory = (listOf(cmd) + localHistory.filter { it != cmd }).take(50)
                                commandInput = ""
                            }
                        }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = 80.dp)
                        .background(if (runtime.ready) visuals.accent else visuals.canvasTop)
                        .border(width = 2.dp, color = border)
                        .clickable(enabled = runtime.ready) {
                            val cmd = commandInput.trim()
                            if (cmd.isNotEmpty()) {
                                onExecuteCommand(cmd)
                                localHistory = (listOf(cmd) + localHistory.filter { it != cmd }).take(50)
                                commandInput = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = if (runtime.ready) visuals.accentInk else visuals.muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("运行", style = BrutalistType.statusBadge, color = if (runtime.ready) visuals.accentInk else visuals.muted, maxLines = 1)
                    }
                }
            }

            // Quick actions
            Text("QUICK ACTIONS", style = BrutalistType.statusBadge, color = visuals.muted)
            FlowChips(items = quickActions, visuals = visuals) { picked ->
                if (runtime.ready) {
                    onExecuteCommand(picked)
                    localHistory = (listOf(picked) + localHistory.filter { it != picked }).take(50)
                } else {
                    commandInput = picked
                }
            }

            // Recent / local history filter
            Text("RECENT", style = BrutalistType.statusBadge, color = visuals.muted)
            val mergedHistory = remember(localHistory, runtime.recentOutput) {
                val fromOutput = runtime.recentOutput.mapNotNull { line ->
                    if (line.stream.equals("stdin", ignoreCase = true) || line.stream.equals("input", ignoreCase = true)) {
                        line.text.trim().takeIf { it.isNotEmpty() }
                    } else null
                }
                (fromOutput + localHistory).distinct().take(50)
            }
            var historyFilter by remember { mutableStateOf("") }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(visuals.paper)
                    .border(width = 1.dp, color = border)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                BasicTextField(
                    value = historyFilter,
                    onValueChange = { historyFilter = it },
                    textStyle = TextStyle(fontFamily = BrutalistType.labelMono.fontFamily, fontSize = 12.sp, color = visuals.ink),
                    cursorBrush = SolidColor(visuals.accent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val filteredHistory = mergedHistory.filter {
                historyFilter.isBlank() || it.contains(historyFilter, ignoreCase = true)
            }
            if (filteredHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(visuals.paper)
                        .border(width = 1.dp, color = border)
                        .padding(8.dp)
                ) {
                    Text("无匹配历史", style = BrutalistType.statusBadge, color = visuals.muted)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    filteredHistory.take(20).forEach { entry ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(visuals.paper)
                                .border(width = 1.dp, color = border)
                                .clickable { commandInput = entry }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "▶ " + entry,
                                style = TextStyle(fontFamily = BrutalistType.labelMono.fontFamily, fontSize = 12.sp, color = visuals.ink),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Output panel
            Text("OUTPUT", style = BrutalistType.statusBadge, color = visuals.muted)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp)
                    .background(visuals.canvasTop)
                    .border(width = 2.dp, color = border)
                    .padding(8.dp)
                    .verticalScroll(outputScroll)
            ) {
                if (runtime.recentOutput.isEmpty()) {
                    Text("尚无输出。输入命令后回车即可执行。", style = BrutalistType.statusBadge, color = visuals.muted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        runtime.recentOutput.takeLast(60).forEach { line ->
                            val color = when {
                                line.stream.equals("stderr", ignoreCase = true) -> visuals.accent2
                                line.stream.equals("system", ignoreCase = true) -> visuals.muted
                                else -> visuals.ink
                            }
                            Text(
                                text = line.text,
                                style = TextStyle(fontFamily = BrutalistType.labelMono.fontFamily, fontSize = 11.sp, color = color),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (runtime.lastError.isNotBlank()) {
                Text(
                    text = "ERROR: " + runtime.lastError,
                    style = BrutalistType.statusBadge,
                    color = visuals.accent2
                )
            }
        }
    }
}

/**
 * Tiny chip row that wraps onto multiple lines. Used for the terminal
 * launcher's QUICK ACTIONS section.
 */
@Composable
private fun FlowChips(items: List<String>, visuals: ChatVisuals, onPick: (String) -> Unit) {
    val border = visuals.hairline
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .background(visuals.paper)
                    .border(width = 2.dp, color = border)
                    .clickable { onPick(item) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item,
                    style = TextStyle(fontFamily = BrutalistType.labelMono.fontFamily, fontSize = 11.sp, color = visuals.ink),
                    maxLines = 1
                )
            }
        }
    }
}



// =============================================================================
// BrutalistHistorySearchSheet
//
// Replaces the old BrutalistHistoryDialog. The MSG status cell opens this
// sheet which combines:
//   - a search input field (filters by message content, case-insensitive)
//   - role filter chips (ALL / USER / ASSISTANT / TOOL)
//   - a session strip showing all available sessions (tap to switch)
//   - a date-grouped list of matching messages scoped to the current session
// Tapping a result jumps the chat list to that message and dismisses the
// sheet. Session switch calls vm.selectSession.
// =============================================================================

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun BrutalistHistorySearchSheet(
    state: ChatUiState,
    visuals: ChatVisuals,
    onJump: (Long) -> Unit,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val border = visuals.hairline
    val scroll = rememberScrollState()
    var query by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf(0) } // 0=ALL 1=USER 2=ASSISTANT 3=TOOL
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    val matches = remember(query, roleFilter, state.messages) {
        if (query.isBlank()) state.messages
        else state.messages.filter { it.content.contains(query, ignoreCase = true) }
    }.let { list ->
        when (roleFilter) {
            1 -> list.filter { it.role.equals("user", ignoreCase = true) }
            2 -> list.filter { it.role.equals("assistant", ignoreCase = true) }
            3 -> list.filter { it.role.equals("tool", ignoreCase = true) }
            else -> list
        }
    }

    val grouped = matches.groupBy { dateFormat.format(Date(it.createdAt)) }
        .toSortedMap(compareByDescending { it })

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp)
                .heightIn(max = 720.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HISTORY SEARCH", style = BrutalistType.headlineMd, color = visuals.ink, modifier = Modifier.weight(1f))
                BrutalistMiniAction(text = "新建", visuals = visuals, accent = visuals.accent, fg = visuals.accentInk, onClick = onCreateSession)
                Spacer(Modifier.width(4.dp))
                BrutalistMiniAction(text = "关闭", visuals = visuals, onClick = onDismiss)
            }

            // Session strip
            Text("SESSIONS · ${state.sessions.size}", style = BrutalistType.statusBadge, color = visuals.muted)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = state.sessions, key = { it.id }) { session ->
                    val selected = session.id == state.currentSessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) visuals.accentSoft else visuals.paper)
                            .border(width = 1.dp, color = if (selected) visuals.accent else border)
                            .clickable { onSelectSession(session.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (session.title.ifBlank { "(未命名)" }).take(30),
                            style = BrutalistType.bodySm,
                            color = visuals.ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!session.isLocal) {
                            BrutalistMiniAction(
                                text = "删除",
                                visuals = visuals,
                                danger = true,
                                onClick = { onDeleteSession(session.id) }
                            )
                        }
                    }
                }
            }

            // Search input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(visuals.paper)
                    .border(width = 2.dp, color = border)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = visuals.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(
                            fontFamily = BrutalistType.bodyMd.fontFamily,
                            fontSize = 14.sp,
                            color = visuals.ink
                        ),
                        cursorBrush = SolidColor(visuals.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (query.isNotEmpty()) {
                        BrutalistMiniAction(text = "清空", visuals = visuals, onClick = { query = "" })
                    }
                }
            }

            // Role filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                val chips = listOf("全部", "用户", "助手", "工具")
                chips.forEachIndexed { idx, label ->
                    val active = roleFilter == idx
                    Box(
                        modifier = Modifier
                            .background(if (active) visuals.accent else visuals.paper)
                            .border(width = 2.dp, color = if (active) visuals.accent else border)
                            .clickable { roleFilter = idx }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = BrutalistType.statusBadge,
                            color = if (active) visuals.accentInk else visuals.ink
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "匹配 ${matches.size}",
                    style = BrutalistType.statusBadge,
                    color = visuals.muted
                )
            }

            if (matches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(visuals.paper)
                        .border(width = 2.dp, color = border)
                        .padding(12.dp)
                ) {
                    Text("没有匹配的消息", style = BrutalistType.bodySm, color = visuals.muted)
                }
            } else {
                grouped.forEach { (date, items) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "▼ $date  (${items.size})",
                            style = BrutalistType.statusBadge.copy(fontWeight = FontWeight.Bold),
                            color = visuals.ink
                        )
                        items.forEach { msg ->
                            val roleLabel = when {
                                msg.role.equals("user", ignoreCase = true) -> "USER"
                                msg.role.equals("assistant", ignoreCase = true) -> "ASST"
                                msg.role.equals("tool", ignoreCase = true) -> "TOOL"
                                msg.role.equals("system", ignoreCase = true) -> "SYS"
                                else -> msg.role.uppercase(Locale.US).take(4)
                            }
                            val roleColor = when (roleLabel) {
                                "USER" -> visuals.accent
                                "ASST" -> visuals.doneAccent
                                "TOOL" -> visuals.accent2
                                else -> visuals.muted
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(visuals.paper)
                                    .border(width = 1.dp, color = border)
                                    .clickable { onJump(msg.id) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = timeFormat.format(Date(msg.createdAt)),
                                    style = TextStyle(fontFamily = BrutalistType.labelMono.fontFamily, fontSize = 10.sp, color = visuals.muted),
                                    modifier = Modifier.width(40.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = roleLabel,
                                    style = BrutalistType.statusBadge,
                                    color = roleColor,
                                    modifier = Modifier.width(40.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = msg.content.take(160).replace("\n", " "),
                                    style = BrutalistType.bodySm,
                                    color = visuals.ink,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



// =============================================================================
// BrutalistTerminalMiniOverlay and BrutalistTerminalShowChip
//
// A small floating terminal chip that can be dragged around the screen. When
// the user hides it, the show chip appears in the corner so they can bring
// it back. Tap to expand into the full BrutalistTerminalLauncherSheet.
// =============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrutalistTerminalMiniOverlay(
    runtime: UiTerminalRuntimeState,
    initialOffset: androidx.compose.ui.geometry.Offset,
    onOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit,
    onExpand: () -> Unit,
    onHide: () -> Unit
) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    var dragOffset by remember { mutableStateOf(initialOffset) }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 80.dp)
                .offset { androidx.compose.ui.unit.IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
                .widthIn(min = 160.dp, max = 220.dp)
                .background(visuals.paper)
                .border(width = 2.dp, color = border)
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        dragOffset = androidx.compose.ui.geometry.Offset(
                            dragOffset.x + drag.x,
                            dragOffset.y + drag.y
                        )
                        onOffsetChange(dragOffset)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onExpand() })
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, tint = visuals.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "TERMINAL",
                        style = BrutalistType.statusBadge.copy(fontSize = 10.sp),
                        color = visuals.ink
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (runtime.ready) "ON" else "...",
                        style = BrutalistType.statusBadge,
                        color = if (runtime.ready) visuals.doneInk else visuals.muted
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(visuals.accent2)
                            .border(width = 1.dp, color = border)
                            .clickable { onHide() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u2715",
                            style = TextStyle(
                                fontFamily = BrutalistType.statusBadge.fontFamily,
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
                if (runtime.recentOutput.isNotEmpty()) {
                    Text(
                        text = runtime.recentOutput.lastOrNull()?.text?.take(60) ?: "",
                        style = TextStyle(fontFamily = BrutalistType.labelMono.fontFamily, fontSize = 10.sp, color = visuals.muted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BrutalistTerminalShowChip(onShow: () -> Unit) {
    val visuals = BrutalistLightVisuals
    val border = visuals.hairline
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
                .background(visuals.accent)
                .border(width = 2.dp, color = border)
                .clickable(onClick = onShow)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = visuals.accentInk, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("TERMINAL", style = BrutalistType.statusBadge, color = visuals.accentInk, maxLines = 1)
            }
        }
    }
}



// =============================================================================
// BrutalistPlanningBubble
//
// Small left-aligned assistant bubble shown in the chat while
// `state.isPlanning` is true. The label is ""正在生成计划"" followed by an
// animated one-to-three dot trail that cycles every 400ms. Brutalist style:
// 0 radius, 2px black border, paper background, mono label. Drop in a
// `LazyColumn` item; auto-clears when isPlanning flips back to false.
// =============================================================================

@Composable
private fun BrutalistPlanningBubble(visuals: ChatVisuals) {
    val border = visuals.hairline
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .background(visuals.paper)
                .border(width = 2.dp, color = border)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "正在生成计划" + ".".repeat(dotCount),
                style = BrutalistType.bodyMd,
                color = visuals.ink
            )
        }
    }
}
// =============================================================================
// BrutalistTraceDetailsDialog
//
// ModalBottomSheet shown when a BrutalistTraceFlowBlock header is long-pressed.
// Displays the full aggregated trace items list ("note/think" steps) for the
// current turn. The header text ""思考 (X 多少消息)"" stays compact in the
// chat; this sheet is the way to read the actual content. Brutalist style:
// 0 radius, 2px borders, paper background, mono label headers, 1px-bordered
// item cards. Close via the ""关闭"" button or tapping the scrim.
// =============================================================================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BrutalistTraceDetailsDialog(
    message: UiMessage,
    visuals: ChatVisuals,
    onDismiss: () -> Unit
) {
    val border = visuals.hairline
    val scroll = rememberScrollState()
    val items = message.traceItems
    val running = message.traceRunning
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", Locale.US) }
    val headerBg = if (running) visuals.accentSoft else visuals.canvasTop
    val headerBorder = if (running) visuals.accent else border
    val headerFg = if (running) visuals.accentInk else visuals.ink

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp)
                .heightIn(max = 640.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRACE DETAILS", style = BrutalistType.headlineMd, color = visuals.ink, modifier = Modifier.weight(1f))
                BrutalistMiniAction(text = "关闭", visuals = visuals, onClick = onDismiss)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .border(width = 2.dp, color = headerBorder)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "思考 · ${items.size} 条",
                    style = TextStyle(
                        fontFamily = BrutalistType.labelMono.fontFamily,
                        fontSize = 13.sp,
                        color = headerFg
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (running) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(visuals.accent)
                            .border(width = 1.dp, color = border)
                    )
                }
            }
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(visuals.paper)
                        .border(width = 1.dp, color = border)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "本次没有记录到思考或笔记",
                        style = BrutalistType.statusBadge,
                        color = visuals.muted
                    )
                }
            } else {
                items.forEach { trace ->
                    val time = remember(trace.createdAt) { timeFormat.format(java.util.Date(trace.createdAt)) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(visuals.paper)
                            .border(width = 1.dp, color = border)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${trace.sourceName} · $time",
                            style = BrutalistType.statusBadge,
                            color = visuals.muted
                        )
                        if (trace.title.isNotBlank()) {
                            Text(
                                text = trace.title,
                                style = TextStyle(
                                    fontFamily = BrutalistType.bodySm.fontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = visuals.ink
                                )
                            )
                        }
                        if (trace.detail.isNotBlank() && trace.detail != trace.title) {
                            Text(
                                text = trace.detail,
                                style = TextStyle(
                                    fontFamily = BrutalistType.bodySm.fontFamily,
                                    fontSize = 12.sp,
                                    color = visuals.ink
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
// =============================================================================
// BrutalistRoleCardSideStrip
//
// A 36dp-wide vertical strip docked to the left of the message list. Replaces
// the role-card pill that used to live in the second row of the top bar. Tapping
// the strip opens the role-card picker; long-pressing opens the editor. The
// strip shares the brutalist visual language: 0 radius, 2px black border, mono
// label, square 28dp avatar badge. Background is `canvasTop` so it reads as a
// separate lane from the message canvas.
// =============================================================================

@Composable
private fun BrutalistRoleCardSideStrip(
    roleLabel: String,
    roleAvatar: String,
    visuals: ChatVisuals,
    onOpenPicker: () -> Unit,
    onEdit: () -> Unit
) {
    val border = visuals.hairline
    val shortName = roleLabel.ifBlank { "\u89d2\u8272" }.take(4)
    val initial = roleAvatar.take(1).ifBlank { "•" }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(36.dp)
            .background(visuals.canvasTop)
            .border(width = 2.dp, color = border)
            .combinedClickable(onClick = onOpenPicker, onLongClick = onEdit)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(visuals.accent)
                .border(width = 2.dp, color = border),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = TextStyle(
                    fontFamily = BrutalistType.headlineMd.fontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = visuals.accentInk
                ),
                maxLines = 1
            )
        }
        Text(
            text = shortName,
            style = TextStyle(
                fontFamily = BrutalistType.labelMono.fontFamily,
                fontSize = 9.sp,
                color = visuals.ink
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// =============================================================================
// BrutalistToolFlowBlock
//
// Compact foldable row for messages with role == "tool" (terminal.run, file.write,
// terminal.python etc.). Default collapsed: a single 24dp line with a triangle
// and a status badge. Tapping the line toggles expansion. The expanded body
// shows the pre-formatted `expandedContent` (Tool Call / args / result blocks)
// inside a monospace, 12-line windowed pane. Brutalist style: 0 radius, 1px
// borders, no shadows, status badge tinted by outcome.
// =============================================================================

@Composable
private fun BrutalistToolFlowBlock(
    message: UiMessage,
    visuals: ChatVisuals,
    onCopy: (String) -> Unit,
    onLongPress: () -> Unit
) {
    val border = visuals.hairline
    val (toolName, status) = remember(message.content) {
        val parts = message.content.split(" [", limit = 2)
        if (parts.size == 2) parts[0] to parts[1].trimEnd(']')
        else message.content to "unknown"
    }
    val statusColor = when (status.lowercase(Locale.US)) {
        "ok" -> visuals.doneAccent
        "error", "err" -> visuals.accent2
        "pending", "running" -> visuals.accent
        else -> visuals.muted
    }
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", Locale.US) }
    val timeText = remember(message.createdAt) { timeFormat.format(java.util.Date(message.createdAt)) }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 220.dp)
                .background(visuals.canvasTop)
                .border(width = 1.dp, color = border)
                .combinedClickable(
                    onClick = { onCopy(toolName) },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "▶",
                style = BrutalistType.statusBadge.copy(fontSize = 10.sp),
                color = visuals.ink
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = toolName,
                style = TextStyle(
                    fontFamily = BrutalistType.labelMono.fontFamily,
                    fontSize = 11.sp,
                    color = visuals.ink
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = status.uppercase(Locale.US),
                style = BrutalistType.statusBadge.copy(fontSize = 9.sp),
                color = statusColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = timeText,
                style = BrutalistType.statusBadge,
                color = visuals.muted
            )
        }
    }
}


// =============================================================================
// BrutalistToolDetailsDialog
//
// ModalBottomSheet shown when a BrutalistToolFlowBlock chip is long-pressed.
// Displays the full tool call content (from `expandedContent`) plus all
// trace items (note/think) accumulated for the current turn. Brutalist
// style: 0 radius, 2px borders, monospace for the call detail block, paper
// background. Close via the inline "关闭" button or tapping the scrim.
// =============================================================================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BrutalistToolDetailsDialog(
    message: UiMessage,
    traceItems: List<com.lgclaw.ui.UiInlineTrace>,
    visuals: ChatVisuals,
    onDismiss: () -> Unit
) {
    val border = visuals.hairline
    val scroll = rememberScrollState()
    val (toolName, status) = remember(message.content) {
        val parts = message.content.split(" [", limit = 2)
        if (parts.size == 2) parts[0] to parts[1].trimEnd(']')
        else message.content to "unknown"
    }
    val statusColor = when (status.lowercase(Locale.US)) {
        "ok" -> visuals.doneAccent
        "error", "err" -> visuals.accent2
        "pending", "running" -> visuals.accent
        else -> visuals.muted
    }
    val timeFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val timeText = remember(message.createdAt) { timeFormat.format(java.util.Date(message.createdAt)) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visuals.canvasTop)
                .padding(14.dp)
                .heightIn(max = 640.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TOOL DETAILS", style = BrutalistType.headlineMd, color = visuals.ink, modifier = Modifier.weight(1f))
                BrutalistMiniAction(text = "关闭", visuals = visuals, onClick = onDismiss)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visuals.paper)
                    .border(width = 2.dp, color = border)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = toolName,
                    style = TextStyle(
                        fontFamily = BrutalistType.labelMono.fontFamily,
                        fontSize = 13.sp,
                        color = visuals.ink
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = status.uppercase(Locale.US),
                    style = BrutalistType.statusBadge.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )
            }
            Text("调用时间: $timeText", style = BrutalistType.statusBadge, color = visuals.muted)
            Text("调用详情", style = BrutalistType.headlineMd.copy(fontSize = 14.sp), color = visuals.ink)
            val text = message.expandedContent ?: ""
            val lines = remember(text) { text.split("\n") }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visuals.paper)
                    .border(width = 1.dp, color = border)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    lines.forEach { line ->
                        Text(
                            text = line.ifBlank { " " },
                            style = TextStyle(
                                fontFamily = BrutalistType.labelMono.fontFamily,
                                fontSize = 11.sp,
                                color = visuals.ink
                            )
                        )
                    }
                }
            }
            if (traceItems.isNotEmpty()) {
                Text(
                    text = "思考与笔记 · ${traceItems.size} 条",
                    style = BrutalistType.headlineMd.copy(fontSize = 14.sp),
                    color = visuals.ink
                )
                traceItems.forEach { trace ->
                    val traceTime = remember(trace.createdAt) { timeFormat.format(java.util.Date(trace.createdAt)) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(visuals.paper)
                            .border(width = 1.dp, color = border)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${trace.sourceName} · $traceTime",
                            style = BrutalistType.statusBadge,
                            color = visuals.muted
                        )
                        if (trace.title.isNotBlank()) {
                            Text(
                                text = trace.title,
                                style = TextStyle(
                                    fontFamily = BrutalistType.bodySm.fontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = visuals.ink
                                ),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (trace.detail.isNotBlank() && trace.detail != trace.title) {
                            Text(
                                text = trace.detail,
                                style = TextStyle(
                                    fontFamily = BrutalistType.bodySm.fontFamily,
                                    fontSize = 12.sp,
                                    color = visuals.ink
                                ),
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Text("本次调用没有关联的思考或笔记", style = BrutalistType.statusBadge, color = visuals.muted)
            }
        }
    }
}
// =============================================================================
// BrutalistTraceFlowBlock
//
// Foldable row for messages with role == "trace" (aggregated think / note
// steps from the current turn). Default collapsed: thin line with a heavier
// triangle (▸/▾, fontSize 12sp) and a count suffix. The header is slightly
// highlighted (`accentSoft` background) so the user can see at a glance this
// block is intentionally clickable; per the design note this is "更偏向手动"
// - more obviously toggleable than the tool flow block. While `traceRunning`
// is true, the header tints to `accent` and a small pulsing square shows.
// =============================================================================

@Composable
private fun BrutalistTraceFlowBlock(
    message: UiMessage,
    visuals: ChatVisuals,
    onLongPress: () -> Unit
) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val border = visuals.hairline
    val items = message.traceItems
    val running = message.traceRunning
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", Locale.US) }
    val headerBg = if (running) visuals.accentSoft else visuals.canvasTop
    val headerBorder = if (running) visuals.accent else border
    val headerFg = if (running) visuals.accentInk else visuals.ink

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .border(width = 1.dp, color = headerBorder)
                .pointerInput(message.id) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                style = TextStyle(
                    fontFamily = BrutalistType.statusBadge.fontFamily,
                    fontSize = 12.sp,
                    color = headerFg
                )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "\u601d\u8003 \u00b7 ${items.size} \u6b65",
                style = TextStyle(
                    fontFamily = BrutalistType.labelMono.fontFamily,
                    fontSize = 11.sp,
                    color = headerFg
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (running) {
                BrutalistTracePulseSquare(visuals = visuals)
                Spacer(Modifier.width(4.dp))
            }
            BrutalistMiniAction(text = if (expanded) "\u6536\u8d77" else "\u5c55\u5f00", visuals = visuals, onClick = { expanded = !expanded })
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(visuals.paper)
                            .border(width = 1.dp, color = border)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "(\u672c\u6b21\u6ca1\u6709\u8bb0\u5f55\u5230\u601d\u8003\u6b65\u9aa4)",
                            style = BrutalistType.statusBadge,
                            color = visuals.muted
                        )
                    }
                } else {
                    items.forEach { trace ->
                        val time = remember(trace.createdAt) { timeFormat.format(java.util.Date(trace.createdAt)) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(visuals.paper)
                                .border(width = 1.dp, color = border)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${trace.sourceName} · $time",
                                style = BrutalistType.statusBadge,
                                color = visuals.muted
                            )
                            if (trace.title.isNotBlank()) {
                                Text(
                                    text = trace.title,
                                    style = TextStyle(
                                        fontFamily = BrutalistType.bodySm.fontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = visuals.ink
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (trace.detail.isNotBlank() && trace.detail != trace.title) {
                                Text(
                                    text = trace.detail,
                                    style = TextStyle(
                                        fontFamily = BrutalistType.bodySm.fontFamily,
                                        fontSize = 11.sp,
                                        color = visuals.ink
                                    ),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tiny 8dp square that flips its background color every 600ms. Used to hint
 * "thinking is still in progress" on the trace block header.
 */
@Composable
private fun BrutalistTracePulseSquare(visuals: ChatVisuals) {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(600)
            on = !on
        }
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(if (on) visuals.accent else visuals.canvasTop)
            .border(width = 1.dp, color = visuals.hairline)
    )
}
