package com.lgclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgclaw.ui.theme.ChatSurface
import coil.compose.AsyncImage
import java.io.File

@Composable
internal fun ChatComposerBar(
    state: ChatUiState,
    onInputHeightChange: (Int) -> Unit,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onPlanModeChange: (UiPlanModeLevel) -> Unit,
    onExecutePendingPlan: () -> Unit,
    onAddToPendingPlan: (String) -> Unit,
    onClearPendingPlan: () -> Unit,
    onPickImages: () -> Unit,
    onPickAttachments: () -> Unit,
    onRequestTerminalOverlayPermission: () -> Unit,
    onRemoveAttachment: (String) -> Unit
) {
    val baseTextSp = state.themeMessageFontSizeSp.coerceIn(12f, 20f)
    val lineMultiplier = state.themeMessageLineHeightMultiplier.coerceIn(1f, 1.7f)
    val bodyTextSize = baseTextSp.sp
    val bodyLineHeight = (baseTextSp * lineMultiplier).sp
    val compactTextSize = (baseTextSp - 2f).coerceIn(10f, 16f).sp
    val tinyTextSize = (baseTextSp - 3f).coerceIn(9f, 14f).sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onInputHeightChange(it.height) }
    ) {
        PlanModeStrip(
            state = state,
            compactTextSize = compactTextSize,
            tinyTextSize = tinyTextSize,
            onPlanModeChange = onPlanModeChange
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            ModeSelectorPill(
                state = state,
                onPlanModeChange = onPlanModeChange
            )
        }


        if (state.input.contains("@")) {
            CommandSuggestionStrip(
                state = state,
                compactTextSize = compactTextSize,
                onPick = { command ->
                    val base = state.input.substringBeforeLast("@", state.input)
                    onInputChanged(base + "@" + command + " ")
                }
            )
        }
        PendingAttachmentStrip(
            attachments = state.pendingAttachments,
            compactTextSize = compactTextSize,
            tinyTextSize = tinyTextSize,
            onRemove = onRemoveAttachment
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                color = Color.White,
                contentColor = Color(0xFF1F2430),
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.2.dp, Color(0xFF8DB6F7).copy(alpha = 0.72f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 62.dp)
                        .drawBehind {
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFAFBFF), Color(0xFFF7F9FF)),
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height)
                                ),
                                cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x226FA8FF), Color.Transparent),
                                    center = Offset(size.width * 0.88f, size.height * 0.85f),
                                    radius = size.maxDimension * 0.36f
                                )
                            )
                        }
                        .padding(start = 9.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ComposerToolKey(
                        icon = Icons.Rounded.AttachFile,
                        contentDescription = "上传文件",
                        accent = Color(0xFF111827),
                        onClick = onPickAttachments
                    )
                    ComposerToolKey(
                        icon = Icons.Rounded.Image,
                        contentDescription = "上传图片",
                        accent = Color(0xFF4D6FD8),
                        onClick = onPickImages
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (state.input.isBlank()) {
                            Text(
                                text = "输入消息，Agent 会按需调用终端、技能和工具",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyTextSize, lineHeight = bodyLineHeight),
                                color = Color(0xFFA3AAB8),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = state.input,
                            onValueChange = onInputChanged,
                            singleLine = false,
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (!state.isGenerating && state.input.isNotBlank()) onSendMessage()
                                }
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = bodyTextSize,
                                lineHeight = bodyLineHeight,
                                color = Color(0xFF1F2430)
                            ),
                            cursorBrush = SolidColor(Color(0xFF3977F6)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val isStopState = state.isGenerating
                    val canSend = (state.input.isNotBlank() || state.pendingAttachments.isNotEmpty()) && !state.isGenerating
                    Surface(
                        color = if (isStopState) {
                            Color(0xFFFFE8E5)
                        } else if (canSend) {
                            Color(0xFF5F7CFF)
                        } else {
                            Color(0xFFF1F3F8)
                        },
                        shape = CircleShape,
                        tonalElevation = 0.dp,
                        shadowElevation = if (canSend || isStopState) 10.dp else 2.dp
                    ) {
                        Box(
                            modifier = Modifier.drawBehind {
                                if (canSend && !isStopState) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(Color(0xFF74B8FF), Color(0xFF6D62FF)),
                                            center = Offset(size.width * 0.30f, size.height * 0.25f),
                                            radius = size.maxDimension * 0.78f
                                        )
                                    )
                                }
                            }
                        ) {
                            IconButton(
                                onClick = { if (state.isGenerating) onStopGeneration() else onSendMessage() },
                                enabled = state.isGenerating || state.input.isNotBlank() || state.pendingAttachments.isNotEmpty(),
                                modifier = Modifier.size(38.dp)
                            ) {
                                if (isStopState) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                color = Color(0xFFCC3528),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "发送给 Agent",
                                        tint = if (canSend) Color.White else Color(0xFF9AA3B2),
                                        modifier = Modifier.size(21.dp)
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
private fun ComposerToolKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.82f),
        contentColor = accent,
        border = BorderStroke(1.dp, Color(0xFFE9EDF5)),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun PendingAttachmentStrip(
    attachments: List<UiPendingAttachment>,
    compactTextSize: TextUnit,
    tinyTextSize: TextUnit,
    onRemove: (String) -> Unit
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color(0xFFE6EAF1))
            ) {
                Row(
                    modifier = Modifier.padding(start = 7.dp, end = 3.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (attachment.kind == UiMediaKind.Image) {
                        AsyncImage(
                            model = File(attachment.path),
                            contentDescription = "图片缩略图",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Column(modifier = Modifier.widthIn(max = 120.dp)) {
                        Text(
                            text = attachment.fileLabel(),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = compactTextSize),
                            color = Color(0xFF20242D),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${attachment.id} · ${attachment.mimeType.substringAfterLast('/').uppercase(java.util.Locale.US)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = tinyTextSize),
                            color = Color(0xFF7B8494),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemove(attachment.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "移除附件", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private fun UiPendingAttachment.fileLabel(): String {
    return name.substringAfter('_', name).take(36).ifBlank { if (kind == UiMediaKind.Image) "图片" else "文件" }
}

@Composable
private fun PlanModeStrip(
    state: ChatUiState,
    compactTextSize: TextUnit,
    tinyTextSize: TextUnit,
    onPlanModeChange: (UiPlanModeLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (state.planModeLevel == UiPlanModeLevel.Off) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Text(
                    text = if (state.planModeLevel == UiPlanModeLevel.Off) "计划：关闭" else "计划：${state.planModeLevel.label}",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = compactTextSize),
                    color = if (state.planModeLevel == UiPlanModeLevel.Off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                UiPlanModeLevel.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = compactTextSize)) },
                        onClick = { expanded = false; onPlanModeChange(mode) }
                    )
                }
            }
        }
        if (state.isPlanning) {
            Text("生成计划中...", style = MaterialTheme.typography.labelMedium.copy(fontSize = tinyTextSize), color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

@Composable
private fun CommandSuggestionStrip(
    state: ChatUiState,
    compactTextSize: TextUnit,
    onPick: (String) -> Unit
) {
    val commands = (state.skills.filter { it.enabled }.map { it.name } + state.dynamicTools.filter { it.enabled }.map { it.name })
        .distinct()
        .take(16)
    if (commands.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        commands.forEach { command ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .clickable { onPick(command) }
            ) {
                Text(
                    text = "@$command",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = compactTextSize),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@Composable
private fun ModeSelectorPill(
    state: ChatUiState,
    onPlanModeChange: (UiPlanModeLevel) -> Unit
) {
    val v = ChatSurface.visuals
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = when (state.planModeLevel) {
        UiPlanModeLevel.Off -> "机关模式"
        UiPlanModeLevel.Quick -> "机关模式 · 速"
        UiPlanModeLevel.Standard -> "机关模式 · 标准"
        UiPlanModeLevel.Deep -> "机关模式 · 深"
        UiPlanModeLevel.Codex -> "机关模式 · Codex"
    }
    val accent = if (state.planModeLevel == UiPlanModeLevel.Off) v.muted else v.accent
    val bg = if (state.planModeLevel == UiPlanModeLevel.Off) v.hairline.copy(alpha = 0.55f) else v.accentSoft
    Box {
        Surface(
            modifier = Modifier
                .heightIn(min = 28.dp)
                .clickable { expanded = true }
                .padding(horizontal = 2.dp),
            shape = RoundedCornerShape(v.rPill),
            color = bg,
            contentColor = accent,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = accent
                )
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = (v.fontMicro.value + 0.5f).sp
                    ),
                    fontWeight = FontWeight.Medium,
                    color = accent
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = accent
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(
                UiPlanModeLevel.Off to "关闭",
                UiPlanModeLevel.Quick to "速",
                UiPlanModeLevel.Standard to "标准",
                UiPlanModeLevel.Deep to "深",
                UiPlanModeLevel.Codex to "Codex"
            ).forEach { (level, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "机关模式  " + label,
                            color = if (level == state.planModeLevel) v.accent else v.ink
                        )
                    },
                    onClick = {
                        onPlanModeChange(level)
                        expanded = false
                    }
                )
            }
        }
    }
}
