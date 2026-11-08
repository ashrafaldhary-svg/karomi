package com.karomi.downloader.util

import java.util.Locale

object FormatUtils {

    /** تنسيق حجم الملف بصيغة مقروءة: 12.4 م.ب */
    fun size(bytes: Long): String {
        if (bytes < 0) return "؟"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(Locale.US, "%.1f ج.ب", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f م.ب", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f ك.ب", bytes / kb)
            else -> "$bytes بايت"
        }
    }

    /** اسم ملف آمن من عنوان الفيديو */
    fun safeFileName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(80)
            .ifBlank { "karomi_video" }
}
