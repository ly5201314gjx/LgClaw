package com.lgclaw.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Portrait
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lgclaw.agents.AvatarCropSpec
import java.io.File

private data class AvatarPresetUi(
    val key: String,
    val title: String,
    val colors: List<Color>,
    val icon: ImageVector
)

private val AvatarPresets = listOf(
    AvatarPresetUi("lgclaw_soft", "LGClaw", listOf(Color(0xFF3977F6), Color(0xFF89B4FF)), Icons.Rounded.Face),
    AvatarPresetUi("story_muse", "故事", listOf(Color(0xFF9A6BFF), Color(0xFFF0B5FF)), Icons.Rounded.WavingHand),
    AvatarPresetUi("builder_blue", "构建", listOf(Color(0xFF2C8DFF), Color(0xFF67D2FF)), Icons.Rounded.Portrait),
    AvatarPresetUi("glass_mint", "清爽", listOf(Color(0xFF26B8A6), Color(0xFFB3F3E8)), Icons.Rounded.Face),
    AvatarPresetUi("rose_light", "柔光", listOf(Color(0xFFFF7C9C), Color(0xFFFFD2E0)), Icons.Rounded.WavingHand),
    AvatarPresetUi("amber_warm", "暖白", listOf(Color(0xFFF0A31A), Color(0xFFFFDDA0)), Icons.Rounded.Portrait)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AgentAvatar(
    info: UiAvatarInfo,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    fallbackText: String = info.fallbackSymbol.ifBlank { "AI" },
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val file = info.imagePath.takeIf { it.isNotBlank() }?.let(::File)
    val preset = AvatarPresets.firstOrNull { it.key == info.presetKey } ?: AvatarPresets.first()
    Surface(
        modifier = modifier.then(
            if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
            } else {
                Modifier
            }
        ),
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE6EAF1))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.linearGradient(preset.colors)),
            contentAlignment = Alignment.Center
        ) {
            if (file != null && file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Icon(
                        imageVector = preset.icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = fallbackText.take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvatarPickerSheet(
    current: UiAvatarInfo,
    title: String,
    onDismiss: () -> Unit,
    onPickPreset: (String) -> Unit,
    onPickImage: () -> Unit,
    onClear: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AvatarPresets) { preset ->
                    val selected = preset.key == current.presetKey
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) Color(0xFFEAF1FF) else Color.White,
                        border = BorderStroke(1.dp, if (selected) Color(0xFF3977F6) else Color(0xFFE6EAF1))
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(min = 76.dp)
                                .clickable { onPickPreset(preset.key) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AgentAvatar(
                                info = current.copy(presetKey = preset.key, imagePath = ""),
                                modifier = Modifier.size(44.dp),
                                fallbackText = preset.title.take(2)
                            )
                            Text(preset.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE6EAF1)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPickImage() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF3977F6))
                        Text("从相册选择", fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFD),
                    border = BorderStroke(1.dp, Color(0xFFE6EAF1)),
                    modifier = Modifier.clickable { onClear() }
                ) {
                    Text("清空", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvatarCropSheet(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (AvatarCropSpec) -> Unit
) {
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("裁剪头像", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF7F8FA),
                border = BorderStroke(1.dp, Color(0xFFE6EAF1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = sourceUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(190.dp)
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX * 86f
                                translationY = offsetY * 86f
                            }
                            .clip(CircleShape)
                    )
                }
            }
            Text("缩放", style = MaterialTheme.typography.labelMedium)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..3f)
            Text("左右偏移", style = MaterialTheme.typography.labelMedium)
            Slider(value = offsetX, onValueChange = { offsetX = it }, valueRange = -1f..1f)
            Text("上下偏移", style = MaterialTheme.typography.labelMedium)
            Slider(value = offsetY, onValueChange = { offsetY = it }, valueRange = -1f..1f)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE6EAF1)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDismiss() }
                ) {
                    Text("取消", modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF111827),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onConfirm(AvatarCropSpec(zoom = zoom, offsetX = offsetX, offsetY = offsetY)) }
                ) {
                    Text("保存", modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center, color = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun InlineTraceBar(
    traces: List<UiInlineTrace>,
    onClear: (() -> Unit)? = null
) {
    if (traces.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val latest = traces.last()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color(0xFFE6EAF1))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF3977F6),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = latest.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = latest.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B8494),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                onClear?.let {
                    Text(
                        text = "清除",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF3977F6),
                        modifier = Modifier.clickable(onClick = it)
                    )
                }
            }
            if (expanded && traces.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    traces.takeLast(5).reversed().forEach { trace ->
                        Text(
                            text = "• ${trace.title}：${trace.detail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF5F6777),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
