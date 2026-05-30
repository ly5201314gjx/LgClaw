package com.lgclaw.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lgclaw.R
import com.lgclaw.providers.ProviderCatalog

internal enum class OnboardingStep {
    Language,
    Provider,
    Identity
}

@Composable
internal fun FirstRunOnboardingScreen(
    state: ChatUiState,
    step: OnboardingStep,
    onStepChange: (OnboardingStep) -> Unit,
    onLanguageSelected: (Boolean) -> Unit,
    onProviderChange: (String) -> Unit,
    onProviderCustomNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onFetchProviderModels: () -> Unit,
    onSetModelEquipped: (String, Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onUserDisplayNameChange: (String) -> Unit,
    onAgentDisplayNameChange: (String) -> Unit,
    onTestProvider: () -> Unit,
    onComplete: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (!state.settingsUseChinese) onLanguageSelected(true)
    }

    val providerOptions = remember { ProviderCatalog.all() }
    val stepOrder = remember { OnboardingStep.entries.toList() }
    val stepIndex = stepOrder.indexOf(step).coerceAtLeast(0)
    val previousStep = stepOrder.getOrNull(stepIndex - 1)
    val nextStep = stepOrder.getOrNull(stepIndex + 1)
    val canMoveNext = when (step) {
        OnboardingStep.Language -> true
        OnboardingStep.Provider -> state.settingsBaseUrl.isNotBlank() &&
            state.settingsModel.isNotBlank() &&
            state.settingsApiKey.isNotBlank()
        OnboardingStep.Identity -> state.onboardingUserDisplayName.trim().isNotBlank() &&
            state.onboardingAgentDisplayName.trim().isNotBlank()
    }

    BackHandler(enabled = previousStep != null) {
        previousStep?.let(onStepChange)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OnboardingHeroCard(
                title = "LGClaw",
                subtitle = "本地 AI 助手，支持模型供应商、工具、技能、记忆与智能体。"
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingStepChip(Modifier.weight(1f), "语言", step == OnboardingStep.Language)
                OnboardingStepChip(Modifier.weight(1f), "供应商", step == OnboardingStep.Provider)
                OnboardingStepChip(Modifier.weight(1f), "身份", step == OnboardingStep.Identity)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    OnboardingStep.Language -> {
                        SettingsSectionCard(
                            title = "界面语言",
                            subtitle = "应用界面统一使用中文。"
                        ) {
                            OnboardingChoiceCard(
                                title = "中文",
                                subtitle = "技能、工具、记忆、设置和聊天界面均使用中文显示。",
                                selected = true,
                                onClick = { onLanguageSelected(true) }
                            )
                        }
                    }

                    OnboardingStep.Provider -> {
                        val selectedProvider = ProviderCatalog.resolve(state.settingsProvider)
                        val modelCandidates = (state.settingsDiscoveredModels + state.settingsEquippedModels + ProviderCatalog.suggestedModels(selectedProvider.id) + state.settingsModel)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()

                        SettingsSectionCard(
                            title = "模型供应商",
                            subtitle = "填写接口地址与 API 密钥后，可立即获取模型并勾选启用。"
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                providerOptions.forEach { option ->
                                    FilterChip(
                                        selected = selectedProvider.id == option.id,
                                        onClick = { onProviderChange(option.id) },
                                        label = { Text(providerDisplayTitle(option.id), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = state.settingsBaseUrl,
                                onValueChange = onBaseUrlChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("接口地址") },
                                singleLine = true,
                                shape = settingsTextFieldShape(),
                                colors = settingsTextFieldColors()
                            )
                            if (selectedProvider.id == "custom") {
                                OutlinedTextField(
                                    value = state.settingsProviderCustomName,
                                    onValueChange = onProviderCustomNameChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("自定义名称") },
                                    singleLine = true,
                                    shape = settingsTextFieldShape(),
                                    colors = settingsTextFieldColors()
                                )
                            }
                            ProviderModelField(
                                providerId = selectedProvider.id,
                                value = state.settingsModel,
                                onValueChange = onModelChange,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SettingsActionButton(
                                    text = if (state.settingsModelFetching) "获取中..." else "获取模型",
                                    icon = Icons.Rounded.Refresh,
                                    onClick = onFetchProviderModels,
                                    enabled = !state.settingsModelFetching && state.settingsBaseUrl.isNotBlank()
                                )
                                Text(
                                    text = "可同时装备多个模型，聊天页可快速切换。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (modelCandidates.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    modelCandidates.forEach { model ->
                                        val selected = state.settingsEquippedModels.contains(model)
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSetModelEquipped(model, !selected) },
                                            label = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = state.settingsApiKey,
                                onValueChange = onApiKeyChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API 密钥") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = settingsTextFieldShape(),
                                colors = settingsTextFieldColors()
                            )
                            Button(
                                onClick = onTestProvider,
                                enabled = !state.settingsProviderTesting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.settingsProviderTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(8.dp))
                                    Text("测试中...")
                                } else {
                                    Text("测试接口")
                                }
                            }
                        }
                    }

                    OnboardingStep.Identity -> {
                        SettingsSectionCard(
                            title = "身份名称",
                            subtitle = "设置聊天中显示的称呼，之后也可以在设置里修改。"
                        ) {
                            OutlinedTextField(
                                value = state.onboardingUserDisplayName,
                                onValueChange = onUserDisplayNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("AI 怎么称呼你？") },
                                placeholder = { Text("你") },
                                singleLine = true,
                                shape = settingsTextFieldShape(),
                                colors = settingsTextFieldColors()
                            )
                            OutlinedTextField(
                                value = state.onboardingAgentDisplayName,
                                onValueChange = onAgentDisplayNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("AI 的名称") },
                                placeholder = { Text("LGClaw") },
                                singleLine = true,
                                shape = settingsTextFieldShape(),
                                colors = settingsTextFieldColors()
                            )
                        }
                    }
                }

                state.settingsInfo?.takeIf { it.isNotBlank() }?.let { info ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
                    ) {
                        Text(
                            text = localizedUiMessage(info, true),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingNavIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    filled = false,
                    enabled = previousStep != null,
                    onClick = { previousStep?.let(onStepChange) }
                )
                OnboardingNavIconButton(
                    icon = if (nextStep == null) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = if (nextStep == null) "开始聊天" else "下一步",
                    filled = true,
                    enabled = canMoveNext && !state.settingsSaving,
                    loading = state.settingsSaving,
                    onClick = {
                        if (nextStep != null) onStepChange(nextStep) else onComplete()
                    }
                )
            }
        }
    }
}

@Composable
internal fun OnboardingHeroCard(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.lgclaw_mark),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
internal fun OnboardingNavIconButton(
    icon: ImageVector,
    contentDescription: String,
    filled: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)),
        tonalElevation = if (filled) 4.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(imageVector = icon, contentDescription = contentDescription)
            }
        }
    }
}

@Composable
internal fun OnboardingStepChip(
    modifier: Modifier = Modifier,
    title: String,
    active: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
internal fun OnboardingChoiceCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 108.dp)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}