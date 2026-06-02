package com.lgclaw.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.lgclaw.config.AppStoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val localUri: String,
    val contentUri: String,
    val createdAt: Long,
    val previewType: String,
    @SerialName("fileName")
    val legacyFileName: String = displayName,
    @SerialName("sizeBytes")
    val legacySizeBytes: Long = size,
    @SerialName("path")
    val legacyPath: String = localUri,
    @SerialName("kind")
    val legacyKind: String = previewType
) {
    val fileName: String get() = displayName
    val sizeBytes: Long get() = size
    val path: String get() = localUri
    val kind: String get() = previewType
}

@Serializable
data class AttachmentPreview(
    val attachmentId: String,
    val previewType: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val contentUri: String,
    val thumbnailUri: String? = null,
    val previewText: String? = null,
    @SerialName("kind")
    val legacyKind: String = previewType,
    @SerialName("fileName")
    val legacyFileName: String = displayName,
    @SerialName("sizeBytes")
    val legacySizeBytes: Long = size
) {
    val kind: String get() = previewType
    val fileName: String get() = displayName
    val sizeBytes: Long get() = size
}

class AttachmentBridge(
    context: Context
) {
    private val appContext = context.applicationContext
    private val rootDir: File = AppStoragePaths.attachmentsDir(appContext)
    private val indexFile: File = File(rootDir, INDEX_FILE_NAME)

    suspend fun importFromUri(
        uri: Uri,
        displayNameOverride: String? = null,
        mimeTypeOverride: String? = null
    ): AttachmentRef = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = displayNameOverride?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(uri).ifBlank { uri.lastPathSegment.orEmpty() }.ifBlank { "attachment" }
        val mimeType = mimeTypeOverride?.takeIf { it.isNotBlank() }
            ?: resolver.getType(uri).orEmpty().ifBlank { guessMimeType(displayName) }
        val target = nextTargetFile(displayName)
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取所选文件")
        val queriedSize = querySize(uri).takeIf { it >= 0L }
        createRef(target, displayName = target.name, mimeType = mimeType, size = queriedSize ?: target.length())
    }

    suspend fun importFromFile(
        sourcePath: String,
        displayNameOverride: String? = null,
        mimeTypeOverride: String? = null
    ): AttachmentRef = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        require(source.exists() && source.isFile) { "source_file_not_found" }
        val displayName = displayNameOverride?.takeIf { it.isNotBlank() } ?: source.name.ifBlank { "attachment" }
        val mimeType = mimeTypeOverride?.takeIf { it.isNotBlank() } ?: guessMimeType(displayName)
        val target = nextTargetFile(displayName)
        source.inputStream().use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        createRef(target, displayName = target.name, mimeType = mimeType, size = target.length())
    }

    suspend fun saveBytes(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): AttachmentRef = withContext(Dispatchers.IO) {
        val target = nextTargetFile(fileName.ifBlank { "attachment" })
        FileOutputStream(target).use { it.write(bytes) }
        val finalMime = mimeType.ifBlank { guessMimeType(target.name) }
        createRef(target, displayName = target.name, mimeType = finalMime, size = target.length())
    }

    suspend fun readText(
        attachmentId: String,
        maxChars: Int = 12_000
    ): String = withContext(Dispatchers.IO) {
        val ref = getRef(attachmentId)
        if (!isTextLike(ref.mimeType, ref.displayName)) {
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
        val contentUri = ref.contentUri.ifBlank { getShareUri(ref.id).toString() }
        AttachmentPreview(
            attachmentId = ref.id,
            previewType = ref.previewType,
            displayName = ref.displayName,
            mimeType = ref.mimeType,
            size = ref.size,
            contentUri = contentUri,
            thumbnailUri = if (ref.previewType == PREVIEW_IMAGE) contentUri else null,
            previewText = if (isTextLike(ref.mimeType, ref.displayName)) {
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

    fun markerFor(ref: AttachmentRef): String {
        val type = when (ref.previewType) {
            PREVIEW_IMAGE -> "image"
            PREVIEW_VIDEO -> "video"
            PREVIEW_AUDIO -> "audio"
            else -> "document"
        }
        return "[LGCLAW_ATTACHMENT:$type|id=${ref.id}|name=${ref.displayName}|mime=${ref.mimeType}|path=${ref.localUri}|size=${ref.size}|uri=${ref.contentUri}|preview=${ref.previewType}]"
    }

    private fun createRef(target: File, displayName: String, mimeType: String, size: Long): AttachmentRef {
        val id = newAttachmentId()
        val previewType = previewTypeForMime(mimeType, target.name)
        val localUri = target.absolutePath
        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            target
        ).toString()
        val ref = AttachmentRef(
            id = id,
            displayName = displayName,
            mimeType = mimeType,
            size = if (size > 0L) size else target.length(),
            localUri = localUri,
            contentUri = contentUri,
            createdAt = System.currentTimeMillis(),
            previewType = previewType
        )
        upsert(ref)
        return ref
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
        val file = File(ref.localUri)
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
                "pdf" -> "application/pdf"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> "application/vnd.ms-excel"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "zip" -> "application/zip"
                else -> "application/octet-stream"
            }
    }

    private fun previewTypeForMime(mimeType: String, name: String): String {
        val lower = mimeType.lowercase(Locale.US)
        return when {
            lower.startsWith("image/") -> PREVIEW_IMAGE
            lower.startsWith("video/") -> PREVIEW_VIDEO
            lower.startsWith("audio/") -> PREVIEW_AUDIO
            isTextLike(mimeType, name) -> PREVIEW_TEXT
            lower.isBlank() || lower == "application/octet-stream" -> "unknown"
            else -> PREVIEW_FILE
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
        private const val PREVIEW_IMAGE = "image"
        private const val PREVIEW_TEXT = "text"
        private const val PREVIEW_FILE = "file"
        private const val PREVIEW_AUDIO = "audio"
        private const val PREVIEW_VIDEO = "video"
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
