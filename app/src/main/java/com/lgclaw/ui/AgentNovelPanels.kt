package com.lgclaw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import com.lgclaw.agents.AgentRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AgentCenterPanel(
    state: ChatUiState,
    onRefresh: () -> Unit,
    onCreateAgent: (String, String, String, String) -> Unit,
    onCompleteAndCreateAgent: (String, String, String, String) -> Unit,
    onUpdateAgent: (String, String, String, String, String, String, String, Boolean) -> Unit,
    onDuplicateAgent: (String) -> Unit,
    onSetAgentEnabled: (String, Boolean) -> Unit,
    onDeleteAgent: (String) -> Unit,
    onPreviewAgent: (String, String) -> Unit,
    onBindAgent: (String?, String?) -> Unit,
    onCreateRoleCard: (String, String, String, String, String, String, String, String) -> Unit,
    onBindRoleCard: (String?) -> Unit,
    onDeleteRoleCard: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("自定义") }
    var description by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var roleName by remember { mutableStateOf("") }
    var roleAvatar by remember { mutableStateOf("角") }
    var roleDescription by remember { mutableStateOf("") }
    var rolePersona by remember { mutableStateOf("") }
    var roleStyle by remember { mutableStateOf("") }
    var roleBoundaries by remember { mutableStateOf("") }
    var roleScenario by remember { mutableStateOf("") }
    var roleExamples by remember { mutableStateOf("") }
    var pendingDeleteRole by remember { mutableStateOf<UiRoleCard?>(null) }
    var pendingDeleteAgent by remember { mutableStateOf<UiAgentProfile?>(null) }
    var expandedAgentId by remember { mutableStateOf<String?>(null) }

    pendingDeleteRole?.let { card ->
        ConfirmDeleteDialog("删除角色卡", "确定删除“${card.name}”吗？如果当前会话正在使用它，会自动解除绑定。", { pendingDeleteRole = null }) {
            onDeleteRoleCard(card.id); pendingDeleteRole = null
        }
    }
    pendingDeleteAgent?.let { profile ->
        ConfirmDeleteDialog("删除智能体", "确定删除“${profile.name}”吗？相关会话会自动解除绑定。内置智能体不可删除。", { pendingDeleteAgent = null }) {
            onDeleteAgent(profile.id); pendingDeleteAgent = null; expandedAgentId = null
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ModernPanelTokens.Page).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernHeroCard(
                title = "智能体中心",
                subtitle = "管理会话智能体、能力配置和角色卡。绑定后，每轮对话都会读取对应设定。",
                status = if (state.currentAgentBinding?.agentId != null) "已绑定" else "普通对话"
            )
        }
        item {
            ModernSectionCard(title = "当前会话", subtitle = "这里的状态会和聊天顶部同步，绑定或解绑都会强提示。") {
                Text("当前会话", fontWeight = FontWeight.SemiBold)
                Text("智能体：" + state.currentAgentName.ifBlank { "未绑定" }, color = ModernPanelTokens.Muted)
                Text("角色卡：" + state.currentRoleCardName.ifBlank { "未绑定" }, color = ModernPanelTokens.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.agentProfiles.filter { it.enabled }.forEach { profile ->
                        ModernChip(text = profile.name, selected = state.currentAgentBinding?.agentId == profile.id, onClick = { onBindAgent(profile.id, state.activeNovelProjectId.ifBlank { null }) })
                    }
                    ModernChip(text = "无智能体", selected = state.currentAgentBinding?.agentId == null, onClick = { onBindAgent(null, null) })
                }
            }
        }
        item {
            ModernSectionCard(title = "角色卡", subtitle = "角色卡会在每轮对话前注入，用于稳定角色扮演、语气和边界。") {
                Text("角色卡", fontWeight = FontWeight.SemiBold)
                if (state.roleCards.isEmpty()) {
                    Text("还没有角色卡。可以在下方创建一个有性格、有边界、有场景的角色。", color = ModernPanelTokens.Muted)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.roleCards.filter { it.enabled }.forEach { card ->
                            ModernChip(text = "${card.avatarSymbol} ${card.name}", selected = state.activeRoleCardId == card.id, onClick = { onBindRoleCard(card.id) })
                        }
                        ModernChip(text = "无角色卡", selected = state.activeRoleCardId.isBlank(), onClick = { onBindRoleCard(null) })
                    }
                }
            }
        }
        item {
            ModernSectionCard(title = "新建角色卡", subtitle = "可以先写核心人设，再逐步补充语气、关系和边界。") {
                Text("新建角色卡", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernTextField(roleAvatar, { roleAvatar = it.take(4) }, label = "标识", singleLine = true, modifier = Modifier.width(92.dp))
                    ModernTextField(roleName, { roleName = it }, label = "名称", singleLine = true, modifier = Modifier.weight(1f))
                }
                ModernTextField(roleDescription, { roleDescription = it }, label = "简介", minLines = 2, maxLines = 3)
                ModernTextField(rolePersona, { rolePersona = it }, label = "角色设定", minLines = 4, maxLines = 8)
                ModernTextField(roleStyle, { roleStyle = it }, label = "说话风格", minLines = 2, maxLines = 5)
                ModernTextField(roleScenario, { roleScenario = it }, label = "当前场景与关系", minLines = 2, maxLines = 5)
                ModernTextField(roleBoundaries, { roleBoundaries = it }, label = "边界与禁区", minLines = 2, maxLines = 5)
                ModernTextField(roleExamples, { roleExamples = it }, label = "示例对话", minLines = 2, maxLines = 6)
                ModernPrimaryButton(text = "保存并绑定角色卡", onClick = {
                    onCreateRoleCard(roleName, roleAvatar, roleDescription, rolePersona, roleStyle, roleBoundaries, roleScenario, roleExamples)
                    roleName = ""; roleAvatar = "角"; roleDescription = ""; rolePersona = ""; roleStyle = ""; roleBoundaries = ""; roleScenario = ""; roleExamples = ""
                }, enabled = roleName.isNotBlank() && rolePersona.length >= 12)
            }
        }
        items(state.roleCards, key = { it.id }) { card ->
            ModernSectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(card.avatarSymbol, style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(card.name, fontWeight = FontWeight.SemiBold)
                        Text(card.description.ifBlank { "暂无简介" }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = ModernPanelTokens.Muted)
                    }
                    ModernSecondaryButton(text = "删除", onClick = { pendingDeleteRole = card }, danger = true)
                }
                Text(card.persona, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModernSecondaryButton(text = "绑定到当前会话", onClick = { onBindRoleCard(card.id) })
                    if (state.activeRoleCardId == card.id) ModernStatusPill("使用中")
                }
            }
        }
        item {
            ModernSectionCard(title = "创建运行时智能体", subtitle = "可以只填一部分，再让 AI 补全完整设定。") {
                Text("创建运行时智能体", fontWeight = FontWeight.SemiBold)
                ModernTextField(name, { name = it }, label = "名称", singleLine = true)
                ModernTextField(type, { type = it }, label = "类型", singleLine = true)
                ModernTextField(description, { description = it }, label = "描述", minLines = 2, maxLines = 3)
                ModernTextField(prompt, { prompt = it }, label = "系统提示词", minLines = 5, maxLines = 10)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernPrimaryButton(text = "保存智能体", onClick = { onCreateAgent(name, type, description, prompt); name = ""; type = "自定义"; description = ""; prompt = "" }, enabled = name.isNotBlank() || type.isNotBlank() || description.isNotBlank() || prompt.isNotBlank())
                    ModernSecondaryButton(text = "AI 补全并保存", onClick = { onCompleteAndCreateAgent(name, type, description, prompt) }, enabled = name.isNotBlank() || type.isNotBlank() || description.isNotBlank() || prompt.isNotBlank())
                }
            }
        }
        item { ModernSectionCard { Text("智能体列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) } }
        items(state.agentProfiles, key = { it.id }) { profile ->
            AgentProfileCard(
                profile = profile,
                expanded = expandedAgentId == profile.id,
                isBound = state.currentAgentBinding?.agentId == profile.id,
                onToggleExpanded = { expandedAgentId = if (expandedAgentId == profile.id) null else profile.id },
                onBind = { onBindAgent(profile.id, state.activeNovelProjectId.ifBlank { null }) },
                onUnbind = { onBindAgent(null, null) },
                onUpdate = onUpdateAgent,
                onDuplicate = { onDuplicateAgent(profile.id) },
                onSetEnabled = { onSetAgentEnabled(profile.id, it) },
                onDelete = { pendingDeleteAgent = profile },
                onPreview = { onPreviewAgent(profile.id, it) }
            )
        }
    }
}

@Composable
private fun AgentProfileCard(
    profile: UiAgentProfile,
    expanded: Boolean,
    isBound: Boolean,
    onToggleExpanded: () -> Unit,
    onBind: () -> Unit,
    onUnbind: () -> Unit,
    onUpdate: (String, String, String, String, String, String, String, Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onPreview: (String) -> Unit
) {
    val isBuiltin = AgentRepository.isBuiltin(profile.id)
    var editName by remember(profile.id, profile.updatedAt) { mutableStateOf(profile.name) }
    var editType by remember(profile.id, profile.updatedAt) { mutableStateOf(profile.type) }
    var editDescription by remember(profile.id, profile.updatedAt) { mutableStateOf(profile.description) }
    var editPrompt by remember(profile.id, profile.updatedAt) { mutableStateOf(profile.systemPrompt) }
    var editSkills by remember(profile.id, profile.updatedAt) { mutableStateOf(profile.defaultSkills.joinToString("，")) }
    var editTools by remember(profile.id, profile.updatedAt) { mutableStateOf(profile.dynamicTools.joinToString("，")) }
    var testMessage by remember(profile.id) { mutableStateOf("") }

    ModernSectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    ModernStatusPill(agentTypeLabel(profile.type))
                }
                Text(profile.description.ifBlank { "暂无描述" }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = ModernPanelTokens.Muted)
                Text("编号：${profile.id}  更新：${formatAgentTime(profile.updatedAt)}", style = MaterialTheme.typography.bodySmall)
            }
            ModernSecondaryButton(text = if (expanded) "收起" else "详情", onClick = onToggleExpanded)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernPrimaryButton(text = if (isBound) "已绑定" else "强提示绑定", onClick = onBind, enabled = profile.enabled)
            if (isBound) ModernSecondaryButton(text = "取消绑定", onClick = onUnbind, danger = true)
            ModernSecondaryButton(text = if (profile.enabled) "停用" else "启用", onClick = { onSetEnabled(!profile.enabled) })
            ModernSecondaryButton(text = if (isBuiltin) "复制后编辑" else "复制", onClick = onDuplicate)
        }
        if (isBound) Text("【当前会话正在使用】后续每轮对话都会读取此智能体。", color = ModernPanelTokens.Accent, style = MaterialTheme.typography.bodySmall)
        if (!profile.enabled) Text("此智能体已停用，不会出现在绑定选择中。", color = ModernPanelTokens.Danger, style = MaterialTheme.typography.bodySmall)
        if (expanded) {
            Text("详细介绍", fontWeight = FontWeight.SemiBold)
            Text(profile.systemPrompt, maxLines = 8, overflow = TextOverflow.Ellipsis, color = ModernPanelTokens.Muted)
            Text("能力配置", fontWeight = FontWeight.SemiBold)
            Text("默认技能：${profile.defaultSkills.ifEmpty { listOf("无") }.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
            Text("动态工具：${profile.dynamicTools.ifEmpty { listOf("无") }.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
            ModernTextField(testMessage, { testMessage = it }, label = "测试消息", minLines = 2, maxLines = 4)
            ModernSecondaryButton(text = "预览运行时上下文", onClick = { onPreview(testMessage) }, modifier = Modifier.fillMaxWidth())
            Text("手动修改", fontWeight = FontWeight.SemiBold)
            if (isBuiltin) {
                Text("内置智能体不可直接改写，可以先复制为自定义智能体再编辑。", color = ModernPanelTokens.Muted)
            } else {
                ModernTextField(editName, { editName = it }, label = "名称", singleLine = true)
                ModernTextField(editType, { editType = it }, label = "类型", singleLine = true)
                ModernTextField(editDescription, { editDescription = it }, label = "描述", minLines = 2, maxLines = 4)
                ModernTextField(editPrompt, { editPrompt = it }, label = "系统提示词", minLines = 5, maxLines = 12)
                ModernTextField(editSkills, { editSkills = it }, label = "默认技能，用逗号分隔", minLines = 1, maxLines = 3)
                ModernTextField(editTools, { editTools = it }, label = "动态工具，用逗号分隔", minLines = 1, maxLines = 3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernPrimaryButton(text = "保存修改", onClick = { onUpdate(profile.id, editName, editType, editDescription, editPrompt, editSkills, editTools, profile.enabled) })
                    ModernSecondaryButton(text = "删除", onClick = onDelete, danger = true)
                }
            }
        }
    }
}
@Composable
internal fun NovelWorkspacePanel(
    state: ChatUiState,
    onRefresh: () -> Unit,
    onCreateProject: (String, String, String, String) -> Unit,
    onUpdateProject: (String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onSaveChapter: (String, String, String, String) -> Unit,
    onDeleteChapter: (String) -> Unit,
    onSaveCharacter: (String, String, String, String, String, String, String) -> Unit,
    onDeleteCharacter: (String) -> Unit,
    onSaveWorldNote: (String, String, String, String) -> Unit,
    onDeleteWorldNote: (String) -> Unit,
    onAnalyzeRelations: (String) -> Unit,
    onSummarizeProject: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    var selectedTab by remember { mutableStateOf("项目") }
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var styleGuide by remember { mutableStateOf("") }
    var premise by remember { mutableStateOf("") }
    var chapterIndex by remember { mutableStateOf("1") }
    var chapterTitle by remember { mutableStateOf("") }
    var chapterContent by remember { mutableStateOf("") }
    var characterName by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var arc by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var worldCategory by remember { mutableStateOf("世界观") }
    var worldTitle by remember { mutableStateOf("") }
    var worldContent by remember { mutableStateOf("") }
    var pendingDeleteProject by remember { mutableStateOf<UiNovelProject?>(null) }
    var pendingDeleteChapter by remember { mutableStateOf<UiNovelChapter?>(null) }
    var pendingDeleteCharacter by remember { mutableStateOf<UiNovelCharacter?>(null) }
    var pendingDeleteWorldNote by remember { mutableStateOf<UiNovelWorldNote?>(null) }
    val activeProject = state.novelProjects.firstOrNull { it.id == state.activeNovelProjectId }

    pendingDeleteProject?.let { project ->
        ConfirmDeleteDialog(
            title = "删除小说项目",
            text = "将删除《${project.title}》以及它的章节、人物、关系图谱、世界观笔记和摘要历史。",
            onDismiss = { pendingDeleteProject = null },
            onConfirm = { onDeleteProject(project.id); pendingDeleteProject = null }
        )
    }
    pendingDeleteChapter?.let { chapter ->
        ConfirmDeleteDialog("删除章节", "确定删除“${chapter.title}”吗？", { pendingDeleteChapter = null }) { onDeleteChapter(chapter.id); pendingDeleteChapter = null }
    }
    pendingDeleteCharacter?.let { character ->
        ConfirmDeleteDialog("删除人物", "确定删除“${character.name}”及相关关系边吗？", { pendingDeleteCharacter = null }) { onDeleteCharacter(character.id); pendingDeleteCharacter = null }
    }
    pendingDeleteWorldNote?.let { note ->
        ConfirmDeleteDialog("删除世界观笔记", "确定删除“${note.title}”吗？", { pendingDeleteWorldNote = null }) { onDeleteWorldNote(note.id); pendingDeleteWorldNote = null }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PanelHeader("小说工作台", activeProject?.title ?: "创建或选择一个本地小说项目") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("项目", "章节", "人物", "关系图谱", "摘要历史", "世界观", "AI 动作").forEach { tab ->
                    FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab) })
                }
            }
        }
        item {
            PanelCard {
                Text("多本小说", fontWeight = FontWeight.SemiBold)
                if (state.novelProjects.isEmpty()) {
                    Text("暂无小说项目，可以在下方创建第一本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.novelProjects.forEach { project ->
                            FilterChip(
                                selected = project.id == state.activeNovelProjectId,
                                onClick = { onSelectProject(project.id) },
                                label = { Text(project.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }
        when (selectedTab) {
            "项目" -> {
                item {
                    PanelCard {
                        Text("新建小说", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(title, { title = it }, label = { Text("项目标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(genre, { genre = it }, label = { Text("类型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(styleGuide, { styleGuide = it }, label = { Text("风格设定") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(premise, { premise = it }, label = { Text("故事简介") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = { onCreateProject(title, genre, styleGuide, premise); title = ""; genre = ""; styleGuide = ""; premise = "" },
                            enabled = title.isNotBlank()
                        ) { Text("创建项目") }
                    }
                }
                items(state.novelProjects, key = { it.id }) { project ->
                    PanelCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(project.title, fontWeight = FontWeight.SemiBold)
                                Text(project.genre.ifBlank { "未设置类型" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { onSelectProject(project.id) }) { Text("打开") }
                            TextButton(onClick = { pendingDeleteProject = project }) { Text("删除") }
                        }
                        Text(project.premise.ifBlank { "暂无简介" }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (project.id == state.activeNovelProjectId) {
                            ElevatedButton(onClick = { onUpdateProject(project.id, project.title, project.genre, project.styleGuide, project.premise) }) { Text("保存当前设定") }
                        }
                    }
                }
            }
            "章节" -> ifActiveProject(activeProject) { project ->
                item {
                    PanelCard {
                        Text("章节编辑", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(chapterIndex, { chapterIndex = it }, label = { Text("序号") }, singleLine = true, modifier = Modifier.width(88.dp))
                            OutlinedTextField(chapterTitle, { chapterTitle = it }, label = { Text("章节标题") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(chapterContent, { chapterContent = it }, label = { Text("章节正文") }, minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { onSaveChapter(project.id, chapterIndex, chapterTitle, chapterContent); chapterContent = "" }, enabled = chapterContent.isNotBlank()) { Text("保存章节并生成本地摘要") }
                    }
                }
                items(state.novelChapters, key = { it.id }) { chapter ->
                    PanelCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("第 ${chapter.chapterIndex} 章 ${chapter.title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { pendingDeleteChapter = chapter }) { Text("删除") }
                        }
                        Text(chapter.summary.ifBlank { chapter.content.take(180) }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            "人物" -> ifActiveProject(activeProject) { project ->
                item {
                    PanelCard {
                        Text("人物档案", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(characterName, { characterName = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(aliases, { aliases = it }, label = { Text("别名，用逗号分隔") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(goal, { goal = it }, label = { Text("目标") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(secret, { secret = it }, label = { Text("秘密") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(arc, { arc = it }, label = { Text("人物弧光") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(notes, { notes = it }, label = { Text("备注") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { onSaveCharacter(project.id, characterName, aliases, goal, secret, arc, notes); characterName = ""; aliases = ""; goal = ""; secret = ""; arc = ""; notes = "" }, enabled = characterName.isNotBlank()) { Text("保存人物") }
                    }
                }
                items(state.novelCharacters, key = { it.id }) { character ->
                    PanelCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(character.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { pendingDeleteCharacter = character }) { Text("删除") }
                        }
                        Text("目标：${character.goal.ifBlank { "未设置" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("弧光：${character.arc.ifBlank { "未设置" }}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            "关系图谱" -> ifActiveProject(activeProject) { project ->
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { onAnalyzeRelations(project.id) }) { Text("重新分析关系") } } }
                items(state.novelRelations) { relation ->
                    PanelCard {
                        Text("${relation.fromName} - ${relation.toName}", fontWeight = FontWeight.SemiBold)
                        Text("${relation.label}  权重 ${"%.1f".format(Locale.US, relation.weight)}")
                        relation.evidence.take(2).forEach { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            "摘要历史" -> ifActiveProject(activeProject) { project ->
                item { Button(onClick = { onSummarizeProject(project.id) }) { Text("生成历史摘要") } }
                items(state.novelAnalyses, key = { it.id }) { analysis ->
                    PanelCard {
                        Text("${analysisKindLabel(analysis.kind)} · ${formatAgentTime(analysis.createdAt)}", fontWeight = FontWeight.SemiBold)
                        Text(analysis.summary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            "世界观" -> ifActiveProject(activeProject) { project ->
                item {
                    PanelCard {
                        Text("世界观笔记", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(worldCategory, { worldCategory = it }, label = { Text("分类") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(worldTitle, { worldTitle = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(worldContent, { worldContent = it }, label = { Text("内容") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { onSaveWorldNote(project.id, worldCategory, worldTitle, worldContent); worldTitle = ""; worldContent = "" }, enabled = worldContent.isNotBlank()) { Text("保存世界观笔记") }
                    }
                }
                items(state.novelWorldNotes, key = { it.id }) { note ->
                    PanelCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("[${note.category}] ${note.title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { pendingDeleteWorldNote = note }) { Text("删除") }
                        }
                        Text(note.content, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            "AI 动作" -> ifActiveProject(activeProject) { project ->
                item {
                    PanelCard {
                        Text("AI 动作", fontWeight = FontWeight.SemiBold)
                        Text("当前项目：${project.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { onSummarizeProject(project.id) }) { Text("总结历史章节") }
                            OutlinedButton(onClick = { onAnalyzeRelations(project.id) }) { Text("分析人物关系") }
                        }
                    }
                }
                item { NovelStats(state) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ifActiveProject(
    activeProject: UiNovelProject?,
    content: androidx.compose.foundation.lazy.LazyListScope.(UiNovelProject) -> Unit
) {
    if (activeProject == null) {
        item { Text("请先创建或选择一本小说。", modifier = Modifier.padding(16.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        content(activeProject)
    }
}

@Composable
internal fun ConfirmDeleteDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ModernConfirmDialog(
        title = title,
        message = text,
        confirmText = "确认删除",
        dismissText = "取消",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        danger = true
    )
}

@Composable
private fun NovelStats(state: ChatUiState) {
    PanelCard {
        Text("项目数据", fontWeight = FontWeight.SemiBold)
        Text("章节 ${state.novelChapters.size} 个，人物 ${state.novelCharacters.size} 个，关系边 ${state.novelRelations.size} 条，世界观 ${state.novelWorldNotes.size} 条，分析 ${state.novelAnalyses.size} 条")
        state.novelChapters.takeLast(5).forEach { Text("第 ${it.chapterIndex} 章 ${it.title}：${it.summary.take(160)}", maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
internal fun PanelHeader(title: String, subtitle: String) {
    ModernHeroCard(title = title, subtitle = subtitle)
}

@Composable
internal fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    ModernSectionCard(content = content)
}

private fun formatAgentTime(timestamp: Long): String {
    if (timestamp <= 0L) return "内置"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun agentTypeLabel(type: String): String = when (type.lowercase(Locale.US)) {
    "builtin" -> "内置"
    "novel" -> "小说"
    "builder", "developer" -> "开发"
    "custom" -> "自定义"
    else -> type.ifBlank { "自定义" }
}

private fun analysisKindLabel(kind: String): String = when (kind.lowercase(Locale.US)) {
    "summary" -> "摘要"
    "relations" -> "关系图谱"
    "continuity" -> "一致性检查"
    else -> kind.ifBlank { "分析" }
}





