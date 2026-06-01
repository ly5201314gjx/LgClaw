package com.lgclaw.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object ModernPanelTokens {
    val Page = Color(0xFFF7F8FA)
    val Card = Color.White
    val CardSoft = Color(0xFFFBFCFF)
    val Border = Color(0xFFE6EAF1)
    val BorderStrong = Color(0xFFD8E0EC)
    val Text = Color(0xFF171A20)
    val Muted = Color(0xFF7B8494)
    val Accent = Color(0xFF3977F6)
    val AccentSoft = Color(0xFFEAF1FF)
    val Danger = Color(0xFFD93A3A)
    val DangerSoft = Color(0xFFFFEEEE)
}

@Composable
internal fun ModernPanelScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ModernPanelTokens.Page)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            ModernHeroCard(
                title = title,
                subtitle = subtitle,
                status = status,
                actions = actions
            )
        }
        content()
    }
}

@Composable
internal fun ModernHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    ModernSectionCard(
        modifier = modifier,
        gradient = Brush.linearGradient(
            listOf(Color.White, Color(0xFFF6F9FF), Color.White)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = ModernPanelTokens.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ModernPanelTokens.Muted,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            status?.takeIf { it.isNotBlank() }?.let {
                ModernStatusPill(text = it)
            }
        }
        if (actions != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
internal fun ModernSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    gradient: Brush? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = if (gradient == null) ModernPanelTokens.Card else Color.Transparent,
        contentColor = ModernPanelTokens.Text,
        border = BorderStroke(1.dp, ModernPanelTokens.Border),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .then(if (gradient != null) Modifier.background(gradient) else Modifier)
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!title.isNullOrBlank() || !subtitle.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    title?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ModernPanelTokens.Text
                        )
                    }
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = ModernPanelTokens.Muted
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
internal fun ModernActionTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val accent = if (danger) ModernPanelTokens.Danger else ModernPanelTokens.Accent
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) ModernPanelTokens.AccentSoft else ModernPanelTokens.CardSoft,
        contentColor = ModernPanelTokens.Text,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.42f) else ModernPanelTokens.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                ModernIconDisc(icon = it, selected = selected, danger = danger)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ModernPanelTokens.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ModernPanelTokens.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = accent
            )
        }
    }
}

@Composable
internal fun ModernListRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    leading: (@Composable (() -> Unit))? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) ModernPanelTokens.AccentSoft else ModernPanelTokens.CardSoft,
        contentColor = ModernPanelTokens.Text,
        border = BorderStroke(1.dp, if (selected) ModernPanelTokens.Accent.copy(alpha = 0.36f) else ModernPanelTokens.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ModernPanelTokens.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ModernPanelTokens.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                body?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = ModernPanelTokens.Text.copy(alpha = 0.78f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = it
                )
            }
        }
    }
}

@Composable
internal fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable (() -> Unit))? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, color = ModernPanelTokens.Muted) },
        placeholder = placeholder.takeIf { it.isNotBlank() }?.let {
            { Text(it, color = ModernPanelTokens.Muted.copy(alpha = 0.72f)) }
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ModernPanelTokens.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedBorderColor = ModernPanelTokens.Accent.copy(alpha = 0.72f),
            unfocusedBorderColor = ModernPanelTokens.Border,
            disabledBorderColor = ModernPanelTokens.Border.copy(alpha = 0.62f),
            cursorColor = ModernPanelTokens.Accent,
            focusedTextColor = ModernPanelTokens.Text,
            unfocusedTextColor = ModernPanelTokens.Text
        )
    )
}

@Composable
internal fun ModernSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ModernListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailing = {
            ModernSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}

@Composable
internal fun ModernSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = ModernPanelTokens.Accent,
            checkedBorderColor = ModernPanelTokens.Accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFDDE3EC),
            uncheckedBorderColor = Color(0xFFDDE3EC),
            disabledCheckedTrackColor = ModernPanelTokens.Accent.copy(alpha = 0.42f),
            disabledUncheckedTrackColor = Color(0xFFE8ECF2)
        )
    )
}

@Composable
internal fun ModernSegmentedTabs(
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (key, label) ->
            ModernChip(
                text = label,
                selected = selected == key,
                onClick = { onSelect(key) }
            )
        }
    }
}

@Composable
internal fun ModernChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (danger) ModernPanelTokens.Danger else ModernPanelTokens.Accent
    Surface(
        modifier = modifier
            .heightIn(min = 32.dp)
            .widthIn(max = 168.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = when {
            selected && danger -> ModernPanelTokens.DangerSoft
            selected -> ModernPanelTokens.AccentSoft
            else -> Color.White
        },
        contentColor = if (selected) color else ModernPanelTokens.Text,
        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.46f) else ModernPanelTokens.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp), tint = color)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ModernEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    ModernSectionCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ModernPanelTokens.Text,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = ModernPanelTokens.Muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ModernConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(title, fontWeight = FontWeight.ExtraBold, color = ModernPanelTokens.Text)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = ModernPanelTokens.Muted)
                content?.invoke(this)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (danger) ModernPanelTokens.Danger else ModernPanelTokens.Accent,
                    contentColor = Color.White
                )
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = ModernPanelTokens.Muted)
            }
        }
    )
}

@Composable
internal fun ModernPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) ModernPanelTokens.Danger else ModernPanelTokens.Accent,
            contentColor = Color.White,
            disabledContainerColor = ModernPanelTokens.Border,
            disabledContentColor = ModernPanelTokens.Muted
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ModernSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (danger) ModernPanelTokens.DangerSoft else Color.White,
            contentColor = if (danger) ModernPanelTokens.Danger else ModernPanelTokens.Text,
            disabledContentColor = ModernPanelTokens.Muted
        ),
        border = BorderStroke(1.dp, if (danger) ModernPanelTokens.Danger.copy(alpha = 0.26f) else ModernPanelTokens.Border),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ModernStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = ModernPanelTokens.Accent
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.11f),
        contentColor = accent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ModernIconDisc(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    size: Dp = 32.dp
) {
    val accent = if (danger) ModernPanelTokens.Danger else ModernPanelTokens.Accent
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (selected) accent else ModernPanelTokens.AccentSoft,
        contentColor = if (selected) Color.White else accent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.52f))
        }
    }
}
