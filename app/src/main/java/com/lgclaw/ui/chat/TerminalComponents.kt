@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.lgclaw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun TerminalHeaderChip(
    enabled: Boolean,
    hasPermission: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) Color(0xFFEAF2FF) else Color(0xFFF6F7FA),
        contentColor = Color(0xFF1B1E26),
        border = BorderStroke(1.dp, if (enabled) Color(0xFF3977F6) else Color(0xFFE6EAF1))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Terminal,
                contentDescription = null,
                tint = if (enabled) Color(0xFF3977F6) else Color(0xFF7B8494)
            )
            Text(
                text = if (enabled) "终端：开" else "终端：关",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (!hasPermission) {
                Text(
                    text = "可授权",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD97706),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun TerminalModeBanner(
    state: UiTerminalRuntimeState,
    onRequestOverlayPermission: () -> Unit,
    onCancelTask: () -> Unit
) {
    if (!state.enabled && state.recentOutput.isEmpty() && state.activeCommand.isBlank()) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        contentColor = Color(0xFF1B1E26),
        border = BorderStroke(1.dp, Color(0xFFE6EAF1))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF3977F6))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (state.activeCommand.isNotBlank()) state.activeCommand.take(68) else "终端模式已开启",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = terminalStatusLine(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B8494),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!state.overlayPermissionGranted) {
                MiniTerminalButton(text = "去授权", color = Color(0xFF3977F6), onClick = onRequestOverlayPermission)
            }
            MiniTerminalButton(text = "暂停", color = Color(0xFFB42318), danger = true, onClick = onCancelTask)
        }
    }
}

@Composable
internal fun TerminalMiniOverlay(
    state: UiTerminalRuntimeState,
    onExpand: () -> Unit,
    onCancelTask: () -> Unit,
    onDismissOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    if (state.recentOutput.isEmpty() && state.activeCommand.isBlank() && !state.enabled && !state.installing) return
    val lastLine = remember(state.recentOutput) { state.recentOutput.lastOrNull()?.text.orEmpty() }
    val busy = state.activeJobId.isNotBlank() || state.installing
    Surface(
        modifier = Modifier.widthIn(min = 154.dp, max = 208.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.98f),
        contentColor = Color(0xFF1B1E26),
        border = BorderStroke(1.dp, if (busy) Color(0xFFBFD3FF) else Color(0xFFE6EAF1)),
        shadowElevation = 3.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onExpand, onLongClick = onExpand)
                .padding(start = 9.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Terminal,
                contentDescription = null,
                tint = if (busy) Color(0xFF3977F6) else Color(0xFF7B8494)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = when {
                        state.installing -> "环境准备中"
                        state.activeJobId.isNotBlank() -> "终端运行中"
                        state.lastExitCode != null -> "终端已完成"
                        else -> "终端待命"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.activeCommand.ifBlank { lastLine }.ifBlank { "长按展开" }.take(42),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B8494),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!state.overlayPermissionGranted) {
                IconButton(onClick = onRequestOverlayPermission, modifier = Modifier.padding(0.dp)) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = "授权悬浮窗", tint = Color(0xFFD97706))
                }
            }
            IconButton(onClick = onExpand, modifier = Modifier.padding(0.dp)) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = "展开终端", tint = Color(0xFF3977F6))
            }
            IconButton(onClick = onDismissOverlay, modifier = Modifier.padding(0.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "隐藏终端运行条", tint = Color(0xFF7B8494))
            }
        }
    }
}

@Composable
internal fun TerminalMiniOverlayCompact(
    state: UiTerminalRuntimeState,
    onExpand: () -> Unit,
    onDismissOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    if (state.recentOutput.isEmpty() && state.activeCommand.isBlank() && !state.enabled && !state.installing) return
    val previewText = remember(state.activeCommand, state.recentOutput) {
        val lines = mutableListOf<String>()
        val command = state.activeCommand.trim()
        if (command.isNotBlank()) lines += "$ $command"
        lines += state.recentOutput
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .takeLast(2)
        lines.takeLast(2).joinToString("\n")
    }
    val busy = state.activeJobId.isNotBlank() || state.installing
    Surface(
        modifier = Modifier.widthIn(min = 142.dp, max = 188.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.98f),
        contentColor = Color(0xFF1B1E26),
        border = BorderStroke(1.dp, if (busy) Color(0xFFBFD3FF) else Color(0xFFE6EAF1)),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onExpand, onLongClick = onExpand)
                .padding(start = 5.dp, end = 7.dp, top = 6.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.combinedClickable(onClick = onDismissOverlay, onLongClick = onDismissOverlay),
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFF4F6FA),
                contentColor = Color(0xFF7B8494),
                border = BorderStroke(1.dp, Color(0xFFE7EBF2))
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "隐藏终端浮窗，终端继续静默运行",
                    modifier = Modifier.padding(4.dp),
                    tint = Color(0xFF7B8494)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = if (busy) Color(0xFF3977F6) else Color(0xFF7B8494)
                    )
                    Text(
                        text = when {
                            state.installing -> "环境准备"
                            state.activeJobId.isNotBlank() -> "终端运行"
                            state.lastExitCode != null -> "运行完成"
                            else -> "静默待命"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = previewText.ifBlank { "点按展开，关闭浮窗不停止任务" }.take(120),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF596170),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!state.overlayPermissionGranted) {
                Surface(
                    modifier = Modifier.combinedClickable(
                        onClick = onRequestOverlayPermission,
                        onLongClick = onRequestOverlayPermission
                    ),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFFFF7ED),
                    contentColor = Color(0xFFD97706),
                    border = BorderStroke(1.dp, Color(0xFFFED7AA))
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = "授权悬浮窗",
                        modifier = Modifier.padding(4.dp),
                        tint = Color(0xFFD97706)
                    )
                }
            }
        }
    }
}

@Composable
internal fun TerminalExpandedSheet(
    state: UiTerminalRuntimeState,
    onDismiss: () -> Unit,
    onCancelTask: () -> Unit,
    onClear: () -> Unit,
    onInstallTools: () -> Unit,
    onForceClose: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val outputListState = rememberLazyListState()
        LaunchedEffect(state.recentOutput.size) {
            if (state.recentOutput.isNotEmpty()) {
                outputListState.animateScrollToItem(state.recentOutput.lastIndex)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF3977F6))
                Column(modifier = Modifier.weight(1f)) {
                    Text("终端面板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = terminalStatusLine(state),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7B8494),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.missingExecutables.isNotEmpty()) {
                    MiniTerminalButton(text = "安装工具", color = Color(0xFF3977F6), onClick = onInstallTools)
                }
                MiniTerminalButton(text = "暂停", color = Color(0xFFB42318), danger = true, onClick = onCancelTask)
                MiniTerminalButton(text = "清空", color = Color(0xFF1B1E26), onClick = onClear)
                MiniTerminalButton(text = "关闭", color = Color(0xFFB42318), danger = true, onClick = onForceClose)
            }
            if (state.installing) {
                LinearProgressIndicator(
                    progress = { state.installProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF3977F6),
                    trackColor = Color(0xFFEAF1FF)
                )
                Text(
                    text = state.installMessage.ifBlank { "正在准备内嵌运行环境" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B8494)
                )
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF7F8FA),
                border = BorderStroke(1.dp, Color(0xFFE6EAF1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 500.dp)
            ) {
                if (state.recentOutput.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("暂无终端输出", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7B8494))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(12.dp),
                        state = outputListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.recentOutput) { line ->
                            Text(
                                text = "[${line.stream}] ${line.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1B1E26)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TerminalEnvironmentPanel(
    state: UiTerminalRuntimeState,
    onDetect: () -> Unit,
    onInitialize: () -> Unit,
    onInstallTools: () -> Unit,
    onOpenTerminal: () -> Unit,
    onCancelTask: () -> Unit,
    onClear: () -> Unit
) {
    ModernPanelScaffold(
        title = "运行环境",
        subtitle = "检测本机内嵌 Linux 风格环境、Node.js、Python、Git、SSH 与包管理工具。",
        status = if (state.ready && state.missingExecutables.isEmpty()) "全部就绪" else "需要检查",
        actions = {
            CompactPanelButton("检测", Icons.Rounded.Refresh, onDetect)
            CompactPanelButton("初始化", Icons.Rounded.Build, onInitialize)
        }
    ) {
        item {
            ModernSectionCard(title = "当前状态", subtitle = terminalStatusLine(state)) {
                EnvValueRow("工具链目录", state.toolchainRoot.ifBlank { "尚未初始化" })
                EnvValueRow("Shell", state.shellPath.ifBlank { "未找到" })
                EnvValueRow("工作区", state.activeWorkspace.ifBlank { "当前会话工作区" })
                if (state.installing) {
                    LinearProgressIndicator(
                        progress = { state.installProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = ModernPanelTokens.Accent,
                        trackColor = ModernPanelTokens.AccentSoft
                    )
                    Text(state.installMessage.ifBlank { "正在准备运行环境" }, style = MaterialTheme.typography.bodySmall, color = ModernPanelTokens.Muted)
                }
            }
        }
        item {
            ModernSectionCard(title = "预装能力", subtitle = "缺失项可以直接安装或重新初始化。") {
                val names = listOf("node", "npm", "python", "pip", "uv", "git", "ssh")
                names.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { name ->
                            val installed = state.installedExecutables.any { it.equals(name, ignoreCase = true) }
                            val missing = state.missingExecutables.any { it.equals(name, ignoreCase = true) }
                            ModernListRow(
                                title = name,
                                subtitle = when {
                                    installed -> "已安装，可被 Agent 调度"
                                    missing -> "缺失，建议安装"
                                    else -> "未检测到状态"
                                },
                                selected = installed && !missing,
                                leading = { ModernStatusPill(if (installed && !missing) "可用" else "待修复") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            ModernSectionCard(title = "终端控制", subtitle = "聊天输入始终发给 Agent，终端作为后台工作器由 Agent 或面板调度。") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ModernPrimaryButton(text = "打开终端面板", onClick = onOpenTerminal, modifier = Modifier.weight(1f))
                    ModernSecondaryButton(text = "安装缺失工具", onClick = onInstallTools, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ModernSecondaryButton(text = "暂停当前任务", onClick = onCancelTask, danger = true, modifier = Modifier.weight(1f))
                    ModernSecondaryButton(text = "清空输出", onClick = onClear, modifier = Modifier.weight(1f))
                }
                if (state.lastError.isNotBlank()) {
                    Text("最近提示：${state.lastError}", style = MaterialTheme.typography.bodySmall, color = ModernPanelTokens.Danger)
                }
            }
        }
    }
}

@Composable
private fun CompactPanelButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = ModernPanelTokens.AccentSoft,
        contentColor = ModernPanelTokens.Accent,
        border = BorderStroke(1.dp, ModernPanelTokens.Accent.copy(alpha = 0.24f)),
        modifier = Modifier.combinedClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EnvValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ModernPanelTokens.Muted, modifier = Modifier.widthIn(min = 72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = ModernPanelTokens.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniTerminalButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (danger) Color(0xFFFEEDE8) else Color(0xFFF7F8FA),
        border = BorderStroke(1.dp, if (danger) Color(0xFFF5C2B8) else Color(0xFFE6EAF1)),
        modifier = modifier.combinedClickable(onClick = onClick)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun terminalStatusLine(state: UiTerminalRuntimeState): String {
    return buildString {
        append(state.activeWorkspace.ifBlank { "当前会话工作区" })
        if (state.lastExitCode != null) {
            append(" · 退出码 ")
            append(state.lastExitCode)
        }
        if (state.installing) {
            append(" · ")
            append(state.installMessage.ifBlank { "正在初始化工具链" })
        } else if (state.activeCommand.isNotBlank()) {
            append(" · 正在运行")
        } else if (state.missingExecutables.isNotEmpty()) {
            append(" · 缺少 ")
            append(state.missingExecutables.take(4).joinToString("、"))
        } else if (state.ready) {
            append(" · 环境就绪")
        }
        if (!state.overlayPermissionGranted) {
            append(" · 悬浮窗未授权")
        }
    }
}
