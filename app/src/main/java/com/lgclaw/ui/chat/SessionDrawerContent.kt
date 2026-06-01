package com.lgclaw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.io.File

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
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.5f)
            .widthIn(min = 184.dp, max = 228.dp),
        drawerContainerColor = Color.White,
        drawerContentColor = Color(0xFF171A20)
    ) {
        Box(Modifier.fillMaxSize()) {
            DrawerBackgroundLayer(state = state)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val x = size.width - 1f
                        drawLine(Color(0xFFE7EAF0), Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerHero(onCreateSessionRequest)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        DrawerSessionRow(
                            session = session,
                            selected = session.id == state.currentSessionId,
                            onSelect = { onSelectSession(session.id) },
                            onRename = { onRenameSession(session) },
                            onConfigure = { onConfigureSession(session) },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }
                }
                DrawerActionGrid(
                    onOpenAgents = onOpenAgents,
                    onOpenSkills = onOpenSkills,
                    onOpenTools = onOpenTools,
                    onOpenMemory = onOpenMemory,
                    onOpenTheme = onOpenTheme,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun DrawerHero(onCreateSessionRequest: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF8FAFD),
        border = BorderStroke(1.dp, Color(0xFFE7EAF1)),
        shadowElevation = 1.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, Color(0xFFF1F6FF)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        ),
                        cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                    )
                }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LGClaw",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF171A20)
                )
                Text(
                    text = "会话与能力中心",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7C8798),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.size(30.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF171A20),
                contentColor = Color.White
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onCreateSessionRequest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "新建会话", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DrawerSessionRow(
    session: UiSessionSummary,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        tonalElevation = 0.dp,
        shadowElevation = if (selected) 4.dp else 0.dp,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) Color(0xFFDCE7FF) else Color(0xFFECEFF4)),
        color = if (selected) Color(0xFFF4F8FF) else Color(0xFFFBFCFE)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 9.dp, end = 3.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val secondaryLabel = if (session.isLocal) {
                "本地管理会话"
            } else {
                session.boundChannel.takeIf { it.isNotBlank() }?.let { channelDisplayLabel(it) } ?: "本地"
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (session.isLocal) "本地" else session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = Color(0xFF1A1E27),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) Color(0xFF4E6EA8) else Color(0xFF858E9F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!session.isLocal) {
                MinimalActionIconButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = "重命名会话", tint = Color(0xFF697386))
                }
                MinimalActionIconButton(onClick = onConfigure) {
                    Icon(Icons.Outlined.Settings, contentDescription = "配置会话渠道", tint = Color(0xFF697386))
                }
                MinimalActionIconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除会话", tint = Color(0xFFB4473F))
                }
            }
        }
    }
}

@Composable
private fun DrawerActionGrid(
    onOpenAgents: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DrawerActionTile("智能体", Icons.Rounded.Psychology, onOpenAgents, Modifier.weight(1f))
            DrawerActionTile("技能", Icons.Rounded.Extension, onOpenSkills, Modifier.weight(1f))
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DrawerActionTile("工具", Icons.Rounded.Build, onOpenTools, Modifier.weight(1f))
            DrawerActionTile("记忆", Icons.Rounded.Memory, onOpenMemory, Modifier.weight(1f))
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DrawerActionTile("主题", Icons.Outlined.Settings, onOpenTheme, Modifier.weight(1f))
            DrawerActionTile("设置", Icons.Outlined.Settings, onOpenSettings, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DrawerActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFBFCFE),
        border = BorderStroke(1.dp, Color(0xFFE8ECF3))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF566174))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2430),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            .blur(state.drawerBackgroundBlur.coerceIn(0f, 24f).dp)
            .clip(RoundedCornerShape(0.dp))
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = drawerAdaptiveScrim(
            base = Color.White,
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
    val alpha = (glass * 0.66f + imageOpacity * 0.24f + styleBoost).coerceIn(0.1f, 0.72f)
    return base.copy(alpha = alpha)
}
