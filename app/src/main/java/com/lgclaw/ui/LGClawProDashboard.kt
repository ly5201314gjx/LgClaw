package com.lgclaw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
    val prompts = remember(state.settingsUseChinese, state.settingsCronEnabled, state.settingsHeartbeatEnabled) {
        if (state.settingsUseChinese) {
            listOf(
                LaunchPrompt("安装技能", "扩展 Agent 能力", "请根据我当前要做的事情，推荐适合安装或启用的技能，并说明每个技能的用途。", Icons.Rounded.AutoFixHigh, Color(0xFF5B7CFF)),
                LaunchPrompt("帮我查一下", "快速获取信息", "我需要快速查清一个问题。请先问我关键词、范围和输出格式。", Icons.Rounded.Search, Color(0xFF28C7AE)),
                LaunchPrompt("新建会话", "重新开始任务", "请帮我为一个新任务梳理目标、约束、第一步动作。", Icons.Rounded.Add, Color(0xFF6A8BFF)),
                LaunchPrompt("调整设置", "模型 / 工具 / 主题", "请帮我检查当前 LGClaw 配置是否完整，并列出需要补齐的设置。", Icons.Rounded.Tune, Color(0xFF8B6CFF))
            )
        } else {
            listOf(
                LaunchPrompt("Install skills", "Extend agent ability", "Recommend skills to install or enable for my current work, and explain what each one is for.", Icons.Rounded.AutoFixHigh, Color(0xFF5B7CFF)),
                LaunchPrompt("Look it up", "Get information fast", "I need to clarify a question quickly. Ask me for keywords, scope, and output format first.", Icons.Rounded.Search, Color(0xFF28C7AE)),
                LaunchPrompt("New session", "Start a fresh task", "Help me shape a new task with goal, constraints, and the first useful action.", Icons.Rounded.Add, Color(0xFF6A8BFF)),
                LaunchPrompt("Tune setup", "Model / tools / theme", "Check whether my LGClaw setup is complete and list what still needs configuration.", Icons.Rounded.Tune, Color(0xFF8B6CFF))
            )
        }
    }

    val localTools = listOf(
        LaunchMiniAction(tr("Read files", "读取文件"), Icons.Rounded.Description, Color(0xFF4A8CFF), "请读取我提供的文件，并总结关键内容。"),
        LaunchMiniAction(tr("Search chat", "检索对话"), Icons.Rounded.Search, Color(0xFF6D8CFF), "请帮我从当前对话里找相关信息。"),
        LaunchMiniAction(tr("Tool run", "工具调度"), Icons.Rounded.PlayArrow, Color(0xFF57C5A8), "请根据任务需要自动选择工具并执行。"),
        LaunchMiniAction(tr("Config check", "配置检查"), Icons.Rounded.CheckCircle, Color(0xFF7E8BFF), "请检查模型、工具和渠道配置状态。"),
        LaunchMiniAction(tr("Create plan", "生成计划"), Icons.Rounded.AutoFixHigh, Color(0xFF9A7BFF), "请把我的需求拆成可执行计划。")
    )
    val automationTools = listOf(
        LaunchMiniAction("Cron", Icons.Rounded.Refresh, Color(0xFF7C65F5), "我想创建一个定时任务，请先问我频率、目标和输出渠道。"),
        LaunchMiniAction("Heartbeat", Icons.Rounded.PlayArrow, Color(0xFF8C7AF7), "请帮我设计一个持续跟进的 heartbeat 任务。"),
        LaunchMiniAction(tr("Memory", "记忆"), Icons.Rounded.Description, Color(0xFF8E84FF), "请整理当前会话中适合写入长期记忆的信息。"),
        LaunchMiniAction("MCP", Icons.Rounded.Tune, Color(0xFF6A72E8), "请检查 MCP 配置，并告诉我可以连接哪些工具。")
    )
    val valueItems = listOf(
        LaunchMiniAction(tr("Local first", "本地优先"), Icons.Rounded.CheckCircle, Color(0xFFFFC94D), "请说明当前任务哪些部分适合本地完成。"),
        LaunchMiniAction(tr("Context", "上下文"), Icons.Rounded.Description, Color(0xFFFFC94D), "请帮我压缩并保留当前任务的关键上下文。"),
        LaunchMiniAction(tr("Workflow", "工作流"), Icons.Rounded.AutoFixHigh, Color(0xFFFFC94D), "请把这个重复任务整理成可复用工作流。")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 18.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x226AA8FF), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.04f),
                        radius = size.width * 0.62f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.04f),
                    radius = size.width * 0.62f
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LaunchHeroCard(
                state = state,
                primary = prompts[0],
                secondary = prompts[1],
                onPrimary = { onPromptSelected(prompts[0].prompt) },
                onSecondary = { onPromptSelected(prompts[1].prompt) }
            )
            LaunchFeatureSection(
                icon = Icons.Rounded.Description,
                iconColor = Color(0xFF4A8CFF),
                title = tr("Local tool highlights", "本地工具实测亮点"),
                items = localTools,
                columns = 5,
                onItemClick = { action -> onPromptSelected(action.prompt) }
            )
            LaunchFeatureSection(
                icon = Icons.Rounded.PlayArrow,
                iconColor = Color(0xFF7C65F5),
                title = tr("Automation workspace", "自动化工作区"),
                items = automationTools,
                columns = 4,
                onItemClick = { action -> onPromptSelected(action.prompt) }
            )
            LaunchFeatureSection(
                icon = Icons.Rounded.CheckCircle,
                iconColor = Color(0xFFFFC94D),
                title = tr("Core value", "核心价值"),
                items = valueItems,
                columns = 3,
                onItemClick = { action -> onPromptSelected(action.prompt) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LaunchBottomPill(
                    icon = Icons.Rounded.Add,
                    text = tr("New session", "新建会话"),
                    onClick = onCreateSession,
                    modifier = Modifier.weight(1f)
                )
                LaunchBottomPill(
                    icon = Icons.Outlined.Settings,
                    text = tr("Control room", "控制室"),
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = tr("AI can make mistakes. Check important information.", "内容由 AI 生成，请注意核实信息准确性。"),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9CA3AF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LaunchBottomPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        contentColor = Color(0xFF232733),
        border = BorderStroke(1.dp, Color(0xFFE8ECF4)),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF5F6E86))
            Spacer(modifier = Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun LaunchFeatureSection(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    items: List<LaunchMiniAction>,
    columns: Int,
    onItemClick: (LaunchMiniAction) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.96f),
        contentColor = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFFEFF2F8)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SoftIconBubble(icon = icon, color = iconColor, size = 42.dp, iconSize = 20.dp)
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF111827),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF7A8495))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.take(columns).forEach { item ->
                    LaunchMiniActionCell(
                        action = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LaunchHeroCard(
    state: ChatUiState,
    primary: LaunchPrompt,
    secondary: LaunchPrompt,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.96f),
        contentColor = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFFEFF3FA)),
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 210.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFF8FBFF), Color(0xFFFDFEFF)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x335C9DFF), Color.Transparent),
                        center = Offset(size.width * 0.83f, size.height * 0.06f),
                        radius = size.width * 0.50f
                    ),
                    radius = size.width * 0.50f,
                    center = Offset(size.width * 0.83f, size.height * 0.06f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x22A58BFF), Color.Transparent),
                        center = Offset(size.width * 0.22f, size.height * 0.88f),
                        radius = size.width * 0.48f
                    ),
                    radius = size.width * 0.48f,
                    center = Offset(size.width * 0.22f, size.height * 0.88f)
                )
            }
            MiniMascot(modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 19.dp, end = 18.dp, top = 24.dp, bottom = 17.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Text(
                    text = tr("Hello, I am LGClaw", "你好，我是 LGClaw"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tr("I can help you explore and build", "我可以帮助你 探索 与 创造"),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = Color(0xFF303846),
                    lineHeight = 24.sp
                )
                Text(
                    text = if (state.currentSessionId == AppSession.LOCAL_SESSION_ID) {
                        tr("Local session ready", "本地会话已就绪")
                    } else {
                        state.currentSessionTitle.ifBlank { tr("Current session", "当前会话") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF7B8495),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 210.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LaunchHeroAction(
                        prompt = primary,
                        onClick = onPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LaunchHeroAction(
                        prompt = secondary,
                        onClick = onSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LaunchHeroAction(
    prompt: LaunchPrompt,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 74.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.82f),
        contentColor = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFFEFF3FA)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SoftIconBubble(icon = prompt.icon, color = prompt.color, size = 48.dp, iconSize = 23.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(prompt.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(prompt.subtitle, style = MaterialTheme.typography.labelMedium, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LaunchMiniActionCell(
    action: LaunchMiniAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 74.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFBFCFF),
        contentColor = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFFF1F3F8)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SoftIconBubble(icon = action.icon, color = action.color, size = 40.dp, iconSize = 18.dp)
            Text(
                action.title,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF3B4250),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SoftIconBubble(
    icon: ImageVector,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.08f)),
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                        center = Offset(18f, 10f),
                        radius = 56f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun MiniMascot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(114.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x335C8DFF), Color.Transparent),
                    center = Offset(size.width * 0.55f, size.height * 0.55f),
                    radius = size.minDimension * 0.54f
                ),
                radius = size.minDimension * 0.54f,
                center = Offset(size.width * 0.55f, size.height * 0.55f)
            )
            drawOval(
                color = Color(0x225F8CFF),
                topLeft = Offset(size.width * 0.10f, size.height * 0.55f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.78f, size.height * 0.22f)
            )
        }
        Surface(
            modifier = Modifier.size(70.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFF0F172A),
            contentColor = Color(0xFF69A7FF),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(modifier = Modifier.size(width = 10.dp, height = 24.dp), shape = RoundedCornerShape(999.dp), color = Color(0xFF5DA7FF)) {}
                Spacer(Modifier.width(12.dp))
                Surface(modifier = Modifier.size(width = 10.dp, height = 24.dp), shape = RoundedCornerShape(999.dp), color = Color(0xFF6F7CFF)) {}
            }
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
    val prompt: String,
    val icon: ImageVector,
    val color: Color
)

private data class LaunchMiniAction(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val prompt: String
)

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}
