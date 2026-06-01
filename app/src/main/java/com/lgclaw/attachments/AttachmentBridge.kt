package com.lgclaw.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.lgclaw.config.AppStoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class AttachmentRef(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val path: String,
    val kind: String,
    val createdAt: Long
)

@Serializable
data class AttachmentPreview(
    val attachmentId: String,
    val kind: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val thumbnailUri: String? = null,
    val previewText: String? = null
)

class AttachmentBridge(
    context: Context
) {
    private val appContext = context.applicationContext
    private val rootDir: File = AppStoragePaths.attachmentsDir(appContext)
    private val indexFile: File = File(rootDir, INDEX_FILE_NAME)

    suspend fun importFromUri(uri: Uri): AttachmentRef = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = queryDisplayName(uri).ifBlank { uri.lastPathSegment.orEmpty() }.ifBlank { "attachment" }
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { guessMimeType(displayName) }
        val target = nextTargetFile(displayName)
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取所选文件")
        val size = querySize(uri).takeIf { it >= 0L } ?: target.length()
        val ref = AttachmentRef(
            id = newAttachmentId(),
            fileName = target.name,
            mimeType = mimeType,
            sizeBytes = if (size > 0L) size else target.length(),
            path = target.absolutePath,
            kind = kindForMime(mimeType, target.name),
            createdAt = System.currentTimeMillis()
        )
        upsert(ref)
        ref
    }

    suspend fun saveBytes(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): AttachmentRef = withContext(Dispatchers.IO) {
        val target = nextTargetFile(fileName.ifBlank { "attachment" })
        FileOutputStream(target).use { it.write(bytes) }
        val finalMime = mimeType.ifBlank { guessMimeType(target.name) }
        val ref = AttachmentRef(
            id = newAttachmentId(),
            fileName = target.name,
            mimeType = finalMime,
            sizeBytes = target.length(),
            path = target.absolutePath,
            kind = kindForMime(finalMime, target.name),
            createdAt = System.currentTimeMillis()
        )
        upsert(ref)
        ref
    }

    suspend fun readText(
        attachmentId: String,
        maxChars: Int = 12_000
    ): String = withContext(Dispatchers.IO) {
        val ref = getRef(attachmentId)
        if (!isTextLike(ref.mimeType, ref.fileName)) {
            throw IllegalArgumentException("unsupported_text_extraction")
        }
        val file = checkedFile(ref)
        val limit = maxChars.takeIf { it > 0 } ?: Int.MAX_VALUE
        file.reader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(DEFAULT_BUFFER_SIZE)
            val out = StringBuilder()
            while (out.length < limit) {
                val wanted = minOf(buffer.size, limit - out.length)
                val count = reader.read(buffer, 0, wanted)
                if (count <= 0) break
                out.append(buffer, 0, count)
            }
            out.toString()
                .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]+"), " ")
                .trim()
        }
    }

    fun getShareUri(attachmentId: String): Uri {
        val ref = getRefSync(attachmentId)
        val file = checkedFile(ref)
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }

    suspend fun generatePreview(attachmentId: String): AttachmentPreview = withContext(Dispatchers.IO) {
        val ref = getRef(attachmentId)
        AttachmentPreview(
            attachmentId = ref.id,
            kind = ref.kind,
            fileName = ref.fileName,
            mimeType = ref.mimeType,
            sizeBytes = ref.sizeBytes,
            thumbnailUri = if (ref.kind == KIND_IMAGE) getShareUri(ref.id).toString() else null,
            previewText = if (isTextLike(ref.mimeType, ref.fileName)) {
                runCatching { readText(ref.id, maxChars = PREVIEW_TEXT_CHARS) }.getOrNull()
            } else {
                null
            }
        )
    }

    suspend fun listRefs(limit: Int = 50): List<AttachmentRef> = withContext(Dispatchers.IO) {
        readIndex().items.sortedByDescending { it.createdAt }.take(limit.coerceIn(1, 500))
    }

    suspend fun getRef(attachmentId: String): AttachmentRef = withContext(Dispatchers.IO) {
        getRefSync(attachmentId)
    }

    private fun getRefSync(attachmentId: String): AttachmentRef {
        val id = attachmentId.trim()
        require(id.isNotBlank()) { "attachment_id_required" }
        return readIndex().items.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("attachment_not_found")
    }

    private fun upsert(ref: AttachmentRef) {
        synchronized(indexLock) {
            val current = readIndexLocked().items.filterNot { it.id == ref.id }
            writeIndexLocked(AttachmentIndex(items = current + ref))
        }
    }

    private fun readIndex(): AttachmentIndex = synchronized(indexLock) { readIndexLocked() }

    private fun readIndexLocked(): AttachmentIndex {
        rootDir.mkdirs()
        if (!indexFile.exists()) return AttachmentIndex()
        return runCatching {
            json.decodeFromString<AttachmentIndex>(indexFile.readText(Charsets.UTF_8))
        }.getOrElse { AttachmentIndex() }
    }

    private fun writeIndexLocked(index: AttachmentIndex) {
        rootDir.mkdirs()
        val tmp = File(rootDir, "$INDEX_FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(index), Charsets.UTF_8)
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun checkedFile(ref: AttachmentRef): File {
        val file = File(ref.path)
        val root = rootDir.canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(root.path + File.separator) || canonical == root) {
            "invalid_attachment_path"
        }
        require(canonical.exists() && canonical.isFile) { "attachment_file_missing" }
        return canonical
    }

    private fun nextTargetFile(originalName: String): File {
        rootDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val safeName = sanitizeFileName(originalName)
        var candidate = File(rootDir, "${stamp}_$safeName")
        var n = 1
        while (candidate.exists()) {
            candidate = File(rootDir, "${stamp}_${n}_$safeName")
            n += 1
        }
        return candidate
    }

    private fun queryDisplayName(uri: Uri): String {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                }.orEmpty()
        }.getOrDefault("")
    }

    private fun querySize(uri: Uri): Long {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[/\\:*?"<>|]"""), "_")
            .replace(Regex("[\\u0000-\\u001F]+"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('.', '_', ' ')
            .take(MAX_SAFE_FILE_NAME_CHARS)
        return cleaned.ifBlank { "attachment" }
    }

    private fun guessMimeType(name: String): String {
        return URLConnection.guessContentTypeFromName(name)
            ?: when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
                "md", "markdown", "log", "kt", "java", "py", "js", "ts", "css", "csv", "json", "xml", "html", "htm" -> "text/plain"
                else -> "application/octet-stream"
            }
    }

    private fun kindForMime(mimeType: String, name: String): String {
        val lower = mimeType.lowercase(Locale.US)
        return when {
            lower.startsWith("image/") -> KIND_IMAGE
            lower.startsWith("video/") -> "video"
            lower.startsWith("audio/") -> "audio"
            isTextLike(mimeType, name) -> "text"
            else -> "document"
        }
    }

    private fun isTextLike(mimeType: String, name: String): Boolean {
        val lowerMime = mimeType.lowercase(Locale.US)
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return lowerMime.startsWith("text/") ||
            lowerMime.contains("json") ||
            lowerMime.contains("xml") ||
            lowerMime.contains("csv") ||
            lowerMime.contains("html") ||
            ext in TEXT_EXTENSIONS
    }

    private fun newAttachmentId(): String =
        "att_" + System.currentTimeMillis().toString(36) + "_" + UUID.randomUUID().toString().replace("-", "").take(8)

    @Serializable
    private data class AttachmentIndex(
        val items: List<AttachmentRef> = emptyList()
    )

    companion object {
        private const val INDEX_FILE_NAME = "index.json"
        private const val KIND_IMAGE = "image"
        private const val PREVIEW_TEXT_CHARS = 1_000
        private const val MAX_SAFE_FILE_NAME_CHARS = 96
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "csv", "json", "xml", "html", "htm", "log",
            "kt", "java", "py", "js", "ts", "tsx", "css", "scss", "yaml", "yml", "toml"
        )
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
        private val indexLock = Any()
    }
}
