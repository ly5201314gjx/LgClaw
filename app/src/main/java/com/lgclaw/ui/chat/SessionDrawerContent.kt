package com.lgclaw.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.io.File

/**
 * Session drawer UI extracted from the chat route to keep the route entry focused on orchestration.
 */
@Composable
internal fun SessionDrawerContent(
    state: ChatUiState,
    onCreateSessionRequest: () -> Unit,
    onSelectSession: (String) -> Unit,
    onRenameSession: (UiSessionSummary) -> Unit,
    onConfigureSession: (UiSessionSummary) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenTheme: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.52f).widthIn(min = 188.dp, max = 236.dp),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.fillMaxSize()) {
            DrawerBackgroundLayer(state = state)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("会话", ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCreateSessionRequest,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = tr("新建会话", ""),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    val selected = session.id == state.currentSessionId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSession(session.id) },
                        tonalElevation = if (selected) 2.dp else 0.dp,
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 7.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val secondaryLabel = if (session.isLocal) {
                                tr("本地管理会话", "")
                            } else {
                                session.boundChannel
                                    .takeIf { it.isNotBlank() }
                                    ?.let { channelDisplayLabel(it) }
                                    ?: tr("本地", "本地")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (session.isLocal) tr("LOCAL", "") else session.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                                Text(
                                    text = secondaryLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!session.isLocal) {
                                MinimalActionIconButton(onClick = { onRenameSession(session) }) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = uiLabel("重命名会话")
                                    )
                                }
                                MinimalActionIconButton(onClick = { onConfigureSession(session) }) {
                                    Icon(
                                        Icons.Outlined.Settings,
                                        contentDescription = uiLabel("配置会话渠道")
                                    )
                                }
                                MinimalActionIconButton(onClick = { onDeleteSession(session.id) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = uiLabel("删除会话")
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            DrawerActionRow("智能体", Icons.Rounded.Psychology, onOpenAgents)
            DrawerActionRow("技能", Icons.Rounded.Extension, onOpenSkills)
            DrawerActionRow("工具", Icons.Rounded.Build, onOpenTools)
            DrawerActionRow("记忆", Icons.Rounded.Memory, onOpenMemory)
            DrawerActionRow("主题", Icons.Outlined.Settings, onOpenTheme)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings),
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("设置", ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun DrawerBackgroundLayer(state: ChatUiState) {
    val path = state.drawerBackgroundPath.trim()
    if (path.isBlank()) return
    val file = File(path)
    if (!file.exists()) return
    Image(
        painter = rememberAsyncImagePainter(file),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = state.drawerBackgroundOpacity.coerceIn(0f, 1f) }
            .blur(state.drawerBackgroundBlur.coerceIn(0f, 40f).dp)
            .clip(RoundedCornerShape(0.dp))
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = drawerAdaptiveScrim(
            base = MaterialTheme.colorScheme.background,
            imageOpacity = state.drawerBackgroundOpacity,
            glass = state.drawerBackgroundGlass,
            bubbleStyle = UiBubbleStyle.fromKey(state.themeBubbleStyle)
        )
    ) {}
}

private fun drawerAdaptiveScrim(base: Color, imageOpacity: Float, glass: Float, bubbleStyle: UiBubbleStyle): Color {
    val styleBoost = when (bubbleStyle) {
        UiBubbleStyle.Native -> 0.02f
        UiBubbleStyle.Frosted -> 0.08f
        UiBubbleStyle.Water -> 0.12f
    }
    val alpha = (glass * 0.66f + imageOpacity * 0.24f + styleBoost).coerceIn(0.1f, 0.76f)
    return base.copy(alpha = alpha)
}

@Composable
private fun DrawerActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}



