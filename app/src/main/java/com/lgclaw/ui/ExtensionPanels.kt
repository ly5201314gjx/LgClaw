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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除技能") },
            text = { Text(if (skill.source == "workspace") "确定删除技能 @${skill.name} 吗？删除后文件会从本地技能目录移除。" else "@${skill.name} 是内置技能，不能删除。可以使用右侧开关关闭它。") },
            confirmButton = {
                Button(
                    onClick = {
                        if (skill.source == "workspace") onDelete(skill.name)
                        pendingDelete = null
                    },
                    enabled = skill.source == "workspace"
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    ExtensionPanelScaffold(title = "技能中心", subtitle = "本地 SKILL.md 技能，可通过 @ 指令调用；关闭技能不会从列表消失，长按才会删除。") {
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
        if (state.skills.isEmpty()) {
            item { EmptyPanelText("暂无技能。创建后可以在对话里用 @技能名 调用。") }
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
    ExtensionPanelScaffold(title = "工具中心", subtitle = "动态工具会实时注册到 AI 工具目录，下一轮对话即可使用。") {
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
            item { EmptyPanelText("暂无动态工具。你可以让 AI 规划，也可以手动创建。") }
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
        AlertDialog(
            onDismissRequest = { selectedMemory = null },
            title = { Text("压缩记忆详情") },
            text = {
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
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = memory.summary,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMemory = null }) { Text("关闭") }
            }
        )
    }
    ExtensionPanelScaffold(
        title = "记忆中心",
        subtitle = "当前有效上下文 ${"%.1f".format(Locale.US, state.currentConversationK)}K；已压缩记忆会作为摘要继续注入。"
    ) {
        if (state.compressedMemories.isEmpty()) {
            item { EmptyPanelText("暂无压缩记忆记录。达到阈值并触发压缩后会显示在这里。") }
        }
        items(state.compressedMemories, key = { it.id }) { memory ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { selectedMemory = memory }),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${(memory.summary.length / 1000.0).coerceAtLeast(0.1).let { "%.1f".format(Locale.US, it) }}K",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("压缩记忆 ${memory.id}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatPanelTime(memory.createdAt)}  ${memory.algorithm}  ${memory.messageCount} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text("查看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ExtensionPanelScaffold(
    title: String,
    subtitle: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
        item { Spacer(Modifier.height(24.dp)) }
    }
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
    Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text(nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = onDescriptionChange, label = { Text("描述") }, singleLine = false, maxLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = onBodyChange, label = { Text(bodyLabel) }, singleLine = false, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
            Button(onClick = onSave, enabled = name.isNotBlank() && body.isNotBlank()) { Text(buttonLabel) }
        }
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
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun EmptyPanelText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
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
