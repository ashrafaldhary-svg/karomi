package com.karomi.downloader.util

import com.karomi.downloader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * فحص التحديثات من الموقع الرسمي فقط (صفحة الإصدارات في مستودع GitHub الرسمي).
 */
class UpdateChecker(private val client: OkHttpClient) {

    data class Result(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val downloadUrl: String
    )

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RELEASES_API).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val json = JSONObject(response.body?.string().orEmpty())
            val latest = json.optString("tag_name").removePrefix("v")
            val url = json.optString("html_url").ifBlank { OFFICIAL_SITE }
            Result(
                hasUpdate = isNewer(latest, BuildConfig.VERSION_NAME),
                latestVersion = latest,
                downloadUrl = url
            )
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split('.').mapNotNull { it.toIntOrNull() }
        val c = current.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    companion object {
        const val OFFICIAL_SITE = "https://github.com/ashrafaldhary-svg/karomi"
        const val RELEASES_API = "https://api.github.com/repos/ashrafaldhary-svg/karomi/releases/latest"
    }
}
