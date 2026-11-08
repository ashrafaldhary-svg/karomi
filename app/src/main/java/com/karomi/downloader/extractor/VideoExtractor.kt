package com.karomi.downloader.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * يستخرج صيغ وجودات الفيديو المتاحة من رابط، مع حجم كل ملف تقريبيًا.
 *
 * - الروابط المباشرة لملفات الوسائط (mp4/webm/mp3...) تُدعم بالكامل.
 * - لمنصات مثل YouTube توجد نقطة تكامل جاهزة مع yt-dlp في [extractFromPlatform]:
 *   ادمج مكتبة مثل youtubedl-android ثم حوّل مخرجاتها إلى [FormatOption].
 */
class VideoExtractor(private val client: OkHttpClient) {

    data class FormatOption(
        val label: String,        // "1080p MP4" / "720p MP4" / "480p MP4" / "WEBM" ...
        val extension: String,    // mp4 / webm / mp3 / m4a
        val sizeBytes: Long,      // الحجم التقريبي، -1 إذا كان مجهولًا
        val directUrl: String,
        val kind: Kind
    ) {
        enum class Kind { VIDEO, AUDIO }
    }

    suspend fun extract(url: String): List<FormatOption> = withContext(Dispatchers.IO) {
        when {
            isDirectMediaUrl(url) -> listOf(directFormat(url))
            else -> extractFromPlatform(url)
        }
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".mp4") || path.endsWith(".webm") ||
                path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac")
    }

    /** إنشاء خيار تحميل لرابط مباشر مع قراءة الحجم من ترويسة HEAD */
    private fun directFormat(url: String): FormatOption {
        val ext = url.substringBefore('?').substringAfterLast('.').lowercase()
        val isAudio = ext == "mp3" || ext == "m4a" || ext == "aac"
        val size = runCatching {
            client.newCall(Request.Builder().url(url).head().build()).execute()
                .use { it.header("Content-Length")?.toLongOrNull() ?: -1L }
        }.getOrDefault(-1L)
        val label = if (isAudio) ext.uppercase() else "MP4 (${ext.uppercase()})"
        return FormatOption(
            label = label,
            extension = ext,
            sizeBytes = size,
            directUrl = url,
            kind = if (isAudio) FormatOption.Kind.AUDIO else FormatOption.Kind.VIDEO
        )
    }

    /**
     * نقطة تكامل مع منصات مشاركة الفيديو (YouTube وغيرها).
     *
     * التطبيق للاستخدام الشخصي فقط. للتفعيل الكامل ادمج yt-dlp عبر مكتبة
     * youtubedl-android وأعد قائمة بالجودات:
     *   - 1080p MP4 / 720p MP4 / 480p MP4 / WEBM
     *   - صوت: MP3 128kbps / AAC (M4A)
     */
    private fun extractFromPlatform(url: String): List<FormatOption> {
        // TODO: دمج yt-dlp هنا وإرجاع الجودات الحقيقية مع أحجامها التقريبية
        return emptyList()
    }

    companion object {
        /** الجودات المعروضة للمستخدم عند اختيار صيغة التحميل */
        val VIDEO_QUALITIES = listOf("1080p MP4", "720p MP4", "480p MP4", "WEBM")
        val AUDIO_QUALITIES = listOf("MP3 128kbps", "AAC (M4A)")
    }
}
