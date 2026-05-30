package com.lgclaw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgclaw.config.AppSession

@Composable
internal fun ChatLaunchpad(
    state: ChatUiState,
    onPromptSelected: (String) -> Unit,
    onCreateSession: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val prompts = remember(state.settingsUseChinese) {
        if (state.settingsUseChinese) {
            listOf(
                LaunchPrompt("规划今天", "把目标拆成可执行步骤", "帮我规划今天的三件关键任务，按优先级、预计耗时和下一步动作输出。"),
                LaunchPrompt("检查配置", "Provider / 工具 / 渠道", "请帮我检查当前 LGClaw 配置是否完整，并列出需要补齐的设置。"),
                LaunchPrompt("写自动化", "Cron 或 Heartbeat", "我想创建一个手机端自动化任务，请先问我目标、触发频率和输出渠道。"),
                LaunchPrompt("整理记忆", "沉淀长期信息", "请根据当前会话，整理出应该写入长期记忆的事实和偏好。")
            )
        } else {
            listOf(
                LaunchPrompt("Plan today", "Turn goals into steps", "Help me plan my three key tasks today with priority, estimated time, and next action."),
                LaunchPrompt("Check setup", "Provider / tools / channels", "Check whether my LGClaw setup is complete and list what still needs configuration."),
                LaunchPrompt("Build automation", "Cron or Heartbeat", "I want to create a phone-side automation. Ask me for the goal, cadence, and output channel first."),
                LaunchPrompt("Refine memory", "Capture durable context", "Review this session and suggest facts or preferences that should become long-term memory.")
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LaunchHeroCard(state = state, onOpenSettings = onOpenSettings)
        QuickStatsStrip(state = state)
        Text(
            text = tr("Start fast", "快速开始"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        prompts.forEach { prompt ->
            LaunchPromptRow(prompt = prompt, onClick = { onPromptSelected(prompt.prompt) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCreateSession,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(tr("New session", "新会话"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(tr("Tune app", "调整设置"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LaunchHeroCard(
    state: ChatUiState,
    onOpenSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val highlightCenter = Offset(size.width * 0.88f, size.height * 0.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                        center = highlightCenter,
                        radius = size.width * 0.62f
                    ),
                    radius = size.width * 0.62f,
                    center = highlightCenter
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.08f),
                    radius = size.width * 0.26f,
                    center = Offset(size.width * 0.08f, size.height * 0.92f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = tr("Mobile agent console", "移动智能体控制台"),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = if (state.currentSessionId == AppSession.LOCAL_SESSION_ID) {
                        tr("Ready to run local work", "本地工作已就绪")
                    } else {
                        tr("Working in", "当前会话") + " ${state.currentSessionTitle}"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tr(
                        "Chat, call tools, route messages, schedule jobs, and keep context on this phone.",
                        "聊天、调用工具、接入渠道、安排任务，并把上下文留在这台手机上。"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    lineHeight = 20.sp
                )
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(tr("Open control room", "打开控制室"), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickStatsStrip(state: ChatUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniStat(tr("Sessions", "会话"), state.sessions.size.toString(), Modifier.weight(1f))
        MiniStat(tr("Channels", "渠道"), state.settingsConnectedChannels.size.toString(), Modifier.weight(1f))
        MiniStat(tr("Tokens", "Token"), compactCount(state.settingsTokenTotal), Modifier.weight(1f))
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LaunchPromptRow(prompt: LaunchPrompt, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(7.dp).size(15.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(prompt.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    prompt.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun SettingsStatusOverview(
    state: ChatUiState,
    onNavigate: (SettingsPanelPage) -> Unit,
    onCreateSessionRequest: () -> Unit
) {
    val providerReady = state.settingsApiKey.isNotBlank() && state.settingsModel.isNotBlank()
    val readyChannels = state.settingsConnectedChannels.count {
        it.status.startsWith("Ready", ignoreCase = true) ||
            it.status.startsWith("Experimental", ignoreCase = true)
    }
    val currentProvider = state.settingsProviderCustomName.ifBlank { state.settingsProvider }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr("Control room", "控制室"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        tr("System readiness at a glance", "一眼查看系统就绪状态"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (providerReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (providerReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(
                        imageVector = if (providerReady) Icons.Rounded.CheckCircle else Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
            }

            DashboardStatusRow(
                icon = Icons.Outlined.Settings,
                title = tr("Provider", "提供方"),
                value = if (providerReady) "$currentProvider / ${state.settingsModel}" else tr("Needs API key or model", "需要 API Key 或模型"),
                healthy = providerReady,
                onClick = { onNavigate(SettingsPanelPage.Provider) }
            )
            DashboardStatusRow(
                icon = Icons.Rounded.Refresh,
                title = tr("Channels", "渠道"),
                value = tr("Ready", "就绪") + " $readyChannels / ${state.settingsConnectedChannels.size}",
                healthy = readyChannels > 0,
                onClick = { onNavigate(SettingsPanelPage.Channels) }
            )
            DashboardStatusRow(
                icon = Icons.Rounded.Description,
                title = "Cron / Heartbeat / MCP",
                value = listOf(
                    if (state.settingsCronEnabled) "Cron" else null,
                    if (state.settingsHeartbeatEnabled) "Heartbeat" else null,
                    if (state.settingsMcpEnabled) "MCP" else null
                ).filterNotNull().ifEmpty { listOf(tr("Not enabled", "未启用")) }.joinToString(" · "),
                healthy = state.settingsCronEnabled || state.settingsHeartbeatEnabled || state.settingsMcpEnabled,
                onClick = { onNavigate(SettingsPanelPage.Cron) }
            )
            DashboardStatusRow(
                icon = Icons.Rounded.Add,
                title = tr("Sessions", "会话"),
                value = tr("Total", "总数") + " ${state.sessions.size}",
                healthy = state.sessions.size > 1,
                onClick = onCreateSessionRequest
            )
        }
    }
}

@Composable
private fun DashboardStatusRow(
    icon: ImageVector,
    title: String,
    value: String,
    healthy: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (healthy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (healthy) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(7.dp).size(15.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

private data class LaunchPrompt(
    val title: String,
    val subtitle: String,
    val prompt: String
)

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}
