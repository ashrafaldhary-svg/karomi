package com.karomi.downloader.download

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * محمّل متعدد الخيوط: يقسم الملف إلى أجزاء تُحمّل بالتوازي عبر طلبات HTTP Range.
 * يحفظ حالة كل جزء في ملف جانبي (.parts) لدعم الإيقاف المؤقت والاستئناف.
 */
class MultiThreadDownloader(
    private val client: OkHttpClient,
    private val threads: Int = DEFAULT_THREADS
) {

    class PausedException : Exception()

    data class Part(val start: Long, val end: Long, var downloaded: Long = 0L)

    interface Listener {
        fun onProgress(downloaded: Long, total: Long)
        fun onError(error: Exception)
    }

    @Volatile
    var paused = false
        private set

    fun pause() {
        paused = true
    }

    private val gson = Gson()

    /** معلومات الملف البعيد */
    data class RemoteInfo(val length: Long, val supportsRanges: Boolean)

    suspend fun remoteInfo(url: String): RemoteInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { response ->
            val length = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val ranges = response.header("Accept-Ranges")?.equals("bytes", true) == true
            RemoteInfo(length, ranges || length > 0)
        }
    }

    /**
     * تحميل الملف إلى [dest]. يستأنف تلقائيًا إذا وُجد ملف حالة سابق.
     * يرمي [PausedException] عند طلب الإيقاف المؤقت مع حفظ التقدم.
     */
    suspend fun download(url: String, dest: File, listener: Listener) = coroutineScope {
        dest.parentFile?.mkdirs()
        val stateFile = File(dest.absolutePath + PARTS_EXT)

        val info = remoteInfo(url)
        check(info.length > 0) { "تعذر تحديد حجم الملف" }

        val parts = loadOrCreateParts(stateFile, info.length)
        val raf = RandomAccessFile(dest, "rw")
        raf.setLength(info.length)

        val totalDone = java.util.concurrent.atomic.AtomicLong(parts.sumOf { it.downloaded })

        try {
            val jobs = parts.filter { it.downloaded < it.end - it.start + 1 }.map { part ->
                async(Dispatchers.IO) {
                    downloadPart(url, raf, part) { delta ->
                        val done = totalDone.addAndGet(delta)
                        listener.onProgress(done, info.length)
                        if (paused || !isActive) throw PausedException()
                    }
                }
            }
            jobs.forEach { it.await() }
            if (paused) throw PausedException()
            stateFile.delete()
        } catch (e: PausedException) {
            saveParts(stateFile, parts)
            throw e
        } catch (e: Exception) {
            saveParts(stateFile, parts)
            listener.onError(e)
            throw e
        } finally {
            raf.close()
        }
    }

    private fun downloadPart(
        url: String,
        raf: RandomAccessFile,
        part: Part,
        onBytes: (Long) -> Unit
    ) {
        val from = part.start + part.downloaded
        if (from > part.end) return

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$from-${part.end}")
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful || response.code == 206) { "HTTP ${response.code}" }
            val body = response.body ?: error("استجابة فارغة")
            val buffer = ByteArray(64 * 1024)
            var offset = from
            body.byteStream().use { input ->
                while (true) {
                    if (paused) throw PausedException()
                    val read = input.read(buffer)
                    if (read == -1) break
                    synchronized(raf) {
                        raf.seek(offset)
                        raf.write(buffer, 0, read)
                    }
                    offset += read
                    part.downloaded += read
                    onBytes(read.toLong())
                }
            }
        }
    }

    private fun loadOrCreateParts(stateFile: File, length: Long): MutableList<Part> {
        if (stateFile.exists()) {
            runCatching {
                val type = object : TypeToken<List<Part>>() {}.type
                val loaded: List<Part> = gson.fromJson(stateFile.readText(), type)
                if (loaded.isNotEmpty() && loaded.last().end == length - 1) {
                    return loaded.toMutableList()
                }
            }
        }
        // تقسيم الملف إلى أجزاء متساوية بعدد الخيوط
        val chunk = length / threads
        return (0 until threads).map { i ->
            val start = i * chunk
            val end = if (i == threads - 1) length - 1 else (start + chunk - 1)
            Part(start, end)
        }.toMutableList()
    }

    private fun saveParts(stateFile: File, parts: List<Part>) {
        runCatching { stateFile.writeText(gson.toJson(parts)) }
    }

    companion object {
        const val DEFAULT_THREADS = 4   // تحميل متوازٍ يسرّع التنزيل حتى 3 أضعاف
        const val PARTS_EXT = ".parts"
    }
}
