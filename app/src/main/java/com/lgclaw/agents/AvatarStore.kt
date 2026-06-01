package com.lgclaw.agents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.lgclaw.config.AppStoragePaths
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

@Serializable
data class AvatarCropSpec(
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

object AvatarStore {
    private const val AVATAR_SIZE = 512
    private val json = Json { ignoreUnknownKeys = true }

    fun cropSpecJson(spec: AvatarCropSpec): String = json.encodeToString(spec)

    fun saveCroppedAvatar(
        context: Context,
        ownerKind: String,
        ownerId: String,
        sourceUri: Uri,
        crop: AvatarCropSpec
    ): File {
        val bitmap = context.contentResolver.openInputStream(sourceUri).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalArgumentException("无法读取头像图片")
        val cropped = cropSquare(bitmap, crop)
        val output = Bitmap.createScaledBitmap(cropped, AVATAR_SIZE, AVATAR_SIZE, true)
        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()

        val targetDir = File(AppStoragePaths.avatarsDir(context), sanitize(ownerKind)).apply { mkdirs() }
        val file = File(targetDir, "${sanitize(ownerId)}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { outputStream ->
            check(output.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)) { "头像保存失败" }
        }
        output.recycle()
        return file
    }

    private fun cropSquare(source: Bitmap, crop: AvatarCropSpec): Bitmap {
        val width = source.width
        val height = source.height
        val safeZoom = crop.zoom.coerceIn(1f, 3f)
        val side = (minOf(width, height) / safeZoom).toInt().coerceAtLeast(32)
        val travelX = (width - side).coerceAtLeast(0) / 2f
        val travelY = (height - side).coerceAtLeast(0) / 2f
        val centerX = width / 2f + crop.offsetX.coerceIn(-1f, 1f) * travelX
        val centerY = height / 2f + crop.offsetY.coerceIn(-1f, 1f) * travelY
        val left = (centerX - side / 2f).toInt().coerceIn(0, (width - side).coerceAtLeast(0))
        val top = (centerY - side / 2f).toInt().coerceIn(0, (height - side).coerceAtLeast(0))
        return Bitmap.createBitmap(source, left, top, side.coerceAtMost(width), side.coerceAtMost(height))
    }

    private fun sanitize(raw: String): String =
        raw.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "avatar" }
            .take(80)
}
