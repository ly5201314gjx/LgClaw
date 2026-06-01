@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.lgclaw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
                    text = "需授权",
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
            Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = null,
                tint = Color(0xFF3977F6)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (state.activeCommand.isNotBlank()) state.activeCommand.take(68) else "终端模式已开启",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(state.activeWorkspace.ifBlank { "当前会话工作区" })
                        if (state.lastExitCode != null) {
                            append(" · 退出码 ")
                            append(state.lastExitCode)
                        }
                        if (state.installing) {
                            append(" · ")
                            append(state.installMessage.ifBlank { "正在初始化工具链" })
                        } else if (state.overlayPermissionGranted) {
                            append(" · 悬浮窗已授权")
                        }
                    },
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
    onRequestOverlayPermission: () -> Unit
) {
    if (state.recentOutput.isEmpty() && state.activeCommand.isBlank() && !state.enabled) return
    val preview = remember(state.recentOutput) { state.recentOutput.takeLast(4) }
    Surface(
        modifier = Modifier.widthIn(max = 260.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.96f),
        contentColor = Color(0xFF1B1E26),
        border = BorderStroke(1.dp, Color(0xFFE6EAF1))
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(onClick = onExpand, onLongClick = onExpand)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF3977F6))
                Text(
                    text = if (state.activeCommand.isNotBlank()) state.activeCommand.take(24) else "终端运行中",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancelTask, modifier = Modifier.padding(0.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "暂停终端")
                }
            }
            if (!state.overlayPermissionGranted) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFD97706))
                    Text("悬浮窗未授权", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD97706))
                    Text(
                        text = "去授权",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF3977F6),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.combinedClickable(onClick = onRequestOverlayPermission)
                    )
                }
            }
            if (preview.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF7F8FA))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    preview.forEach { line ->
                        Text(
                            text = "[${line.stream}] ${line.text}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4A5568),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text("长按可展开终端面板", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7B8494))
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
                        text = state.activeWorkspace.ifBlank { "当前会话工作区" },
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
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF7F8FA),
                border = BorderStroke(1.dp, Color(0xFFE6EAF1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 460.dp)
            ) {
                if (state.recentOutput.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("暂无终端输出", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7B8494))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(12.dp),
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
