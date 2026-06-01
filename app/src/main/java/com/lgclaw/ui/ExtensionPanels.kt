package com.lgclaw.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SkillsPanel(
    state: ChatUiState,
    onRefresh: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<UiManagedSkill?>(null) }

    pendingDelete?.let { skill ->
        ModernConfirmDialog(
            title = "删除技能",
            message = if (skill.source == "workspace") "确定删除技能 @${skill.name} 吗？删除后文件会从本地技能目录移除。" else "@${skill.name} 是内置技能，不能删除。可以使用右侧开关关闭它。",
            confirmText = "确认删除",
            dismissText = "取消",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                if (skill.source == "workspace") onDelete(skill.name)
                pendingDelete = null
            },
            danger = true,
            confirmEnabled = skill.source == "workspace"
        )
    }

    ExtensionPanelScaffold(
        title = "技能中心",
        subtitle = "本地 SKILL.md 技能，可通过 @ 指令调用；关闭技能不会从列表消失，长按才会删除。",
        status = "${state.skills.count { it.enabled }}/${state.skills.size} 启用"
    ) {
        item {
            ExtensionEditor(
                name = name,
                description = description,
                body = body,
                nameLabel = "技能名称",
                bodyLabel = "技能指令",
                buttonLabel = "保存技能",
                onNameChange = { name = it },
                onDescriptionChange = { description = it },
                onBodyChange = { body = it },
                onSave = {
                    onCreate(name, description, body)
                    name = ""; description = ""; body = ""
                }
            )
        }
        item {
            ModernSectionCard(title = "@ 指令提示", subtitle = "在对话输入框输入 @技能名，可以把技能说明注入当前任务。停用只会关闭调用，不会从列表里隐藏。") {
                Text("长按自定义技能会弹出删除确认；内置技能只允许停用。", style = MaterialTheme.typography.bodySmall, color = ModernPanelTokens.Muted)
            }
        }
        if (state.skills.isEmpty()) {
            item { EmptyPanelText("暂无技能", "创建后可以在对话里用 @技能名 调用。") }
        }
        items(state.skills, key = { it.name }) { skill ->
            ExtensionRow(
                title = "@${skill.name}",
                subtitle = "${sourceLabel(skill.source)}  ${formatPanelTime(skill.updatedAt)}",
                body = skillDisplayDescription(skill.name, skill.description),
                enabled = skill.enabled,
                onEnabledChange = { onSetEnabled(skill.name, it) },
                onLongClick = { pendingDelete = skill }
            )
        }
    }
}

@Composable
internal fun ToolsPanel(
    state: ChatUiState,
    onRefresh: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    ExtensionPanelScaffold(
        title = "工具中心",
        subtitle = "动态工具会实时注册到 AI 工具目录，下一轮对话即可使用。",
        status = "${state.dynamicTools.count { it.enabled }}/${state.dynamicTools.size} 启用"
    ) {
        item {
            ExtensionEditor(
                name = name,
                description = description,
                body = prompt,
                nameLabel = "工具名称",
                bodyLabel = "工具规划提示词",
                buttonLabel = "保存工具",
                onNameChange = { name = it },
                onDescriptionChange = { description = it },
                onBodyChange = { prompt = it },
                onSave = {
                    onCreate(name, description, prompt)
                    name = ""; description = ""; prompt = ""
                }
            )
        }
        if (state.dynamicTools.isEmpty()) {
            item { EmptyPanelText("暂无动态工具", "你可以让 AI 规划，也可以手动创建。") }
        }
        items(state.dynamicTools, key = { it.name }) { tool ->
            ExtensionRow(
                title = tool.name,
                subtitle = formatPanelTime(tool.updatedAt),
                body = tool.description.ifBlank { "工具提示词：${tool.prompt.take(180)}" },
                enabled = tool.enabled,
                onEnabledChange = { onSetEnabled(tool.name, it) }
            )
        }
    }
}

@Composable
internal fun MemoryPanel(state: ChatUiState, onRefresh: () -> Unit) {
    LaunchedEffect(Unit) { onRefresh() }
    var selectedMemory by remember { mutableStateOf<UiCompressedMemory?>(null) }
    selectedMemory?.let { memory ->
        ModernConfirmDialog(
            title = "压缩记忆详情",
            message = "编号 ${memory.id}，创建于 ${formatPanelTime(memory.createdAt)}。",
            confirmText = "关闭",
            dismissText = "返回",
            onDismiss = { selectedMemory = null },
            onConfirm = { selectedMemory = null },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("编号：${memory.id}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("时间：${formatPanelTime(memory.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("算法：${memory.algorithm}", style = MaterialTheme.typography.bodySmall)
                    Text("消息：${memory.messageCount} 条；原文 ${memory.originalChars} 字符；压缩 ${memory.compressedBytes} 字节", style = MaterialTheme.typography.bodySmall)
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = ModernPanelTokens.CardSoft,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ModernPanelTokens.Border)
                    ) {
                        Text(
                            text = memory.summary,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        )
    }
    ExtensionPanelScaffold(
        title = "记忆中心",
        subtitle = "当前有效上下文 ${"%.1f".format(Locale.US, state.currentConversationK)}K；已压缩记忆会作为摘要继续注入。",
        status = "${state.compressedMemories.size} 条记忆"
    ) {
        item {
            ModernSectionCard(title = "K 值状态", subtitle = "顶部 K 值来自真实消息与压缩记忆统计，主动压缩后会同步刷新。") {
                ModernStatusPill(text = "当前 ${"%.1f".format(Locale.US, state.currentConversationK)}K")
            }
        }
        if (state.compressedMemories.isEmpty()) {
            item { EmptyPanelText("暂无压缩记忆", "达到阈值并触发压缩后会显示在这里。") }
        }
        items(state.compressedMemories, key = { it.id }) { memory ->
            ModernListRow(
                title = "压缩记忆 ${memory.id}",
                subtitle = "${formatPanelTime(memory.createdAt)}  ${memory.algorithm}  ${memory.messageCount} 条",
                body = memory.summary.take(96),
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    ModernStatusPill("${(memory.summary.length / 1000.0).coerceAtLeast(0.1).let { "%.1f".format(Locale.US, it) }}K")
                },
                trailing = {
                    Text("查看", style = MaterialTheme.typography.labelMedium, color = ModernPanelTokens.Accent)
                },
                onClick = { selectedMemory = memory }
            )
        }
    }
}

@Composable
private fun ExtensionPanelScaffold(
    title: String,
    subtitle: String,
    status: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    ModernPanelScaffold(title = title, subtitle = subtitle, status = status, content = content)
}

@Composable
private fun ExtensionEditor(
    name: String,
    description: String,
    body: String,
    nameLabel: String,
    bodyLabel: String,
    buttonLabel: String,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit
) {
    ModernSectionCard(title = "创建", subtitle = "保存后立即写入本地，下一轮对话可用。") {
        ModernTextField(value = name, onValueChange = onNameChange, label = nameLabel, singleLine = true)
        ModernTextField(value = description, onValueChange = onDescriptionChange, label = "描述", maxLines = 2)
        ModernTextField(value = body, onValueChange = onBodyChange, label = bodyLabel, minLines = 3, maxLines = 6)
        ModernPrimaryButton(
            text = buttonLabel,
            onClick = onSave,
            enabled = name.isNotBlank() && body.isNotBlank()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtensionRow(
    title: String,
    subtitle: String,
    body: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    ModernListRow(
        title = title,
        subtitle = subtitle,
        body = body,
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick),
        selected = enabled,
        trailing = {
            ModernSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    )
}

@Composable
private fun EmptyPanelText(title: String, subtitle: String) {
    ModernEmptyState(title = title, subtitle = subtitle)
}

internal fun skillDisplayDescription(name: String, rawDescription: String): String {
    val cleanName = name.trim().lowercase(Locale.US)
    return when (cleanName) {
        "channels" -> "连接 Telegram、Discord、Slack、飞书、邮件、企业微信等外部渠道。"
        "memory" -> "管理本地长期记忆与压缩记忆，帮助 AI 保留重要上下文。"
        "tools" -> "扩展和调用本地工具能力，用于文件、网络、设备和工作流操作。"
        "heartbeat" -> "配置周期性心跳任务，让会话按计划自动继续工作。"
        "agent" -> "定义智能体行为、系统提示词和任务边界。"
        "sessions" -> "管理会话列表、会话发送和跨会话操作。"
        else -> rawDescription.trim().ifBlank { "暂无描述" }.let { description ->
            if (description.any { it in '\u4e00'..'\u9fff' }) description else "本地技能：${description.take(160)}"
        }
    }
}

private fun formatPanelTime(timestamp: Long): String {
    if (timestamp <= 0L) return "内置"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun sourceLabel(source: String): String = when (source.lowercase(Locale.US)) {
    "builtin", "asset" -> "内置"
    "workspace", "local" -> "本地"
    else -> source.ifBlank { "本地" }
}
