package com.karomi.downloader.library

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.karomi.downloader.data.AppDatabase
import com.karomi.downloader.data.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * قائمة التشغيل الذكية والنسخ الاحتياطي لقائمة التحميلات.
 */
class PlaylistManager(context: Context) {

    private val dao = AppDatabase.get(context).downloads()
    private val gson = Gson()

    /** قائمة تشغيل ذكية: كل التحميلات المكتملة مرتبة من الأحدث */
    suspend fun smartPlaylist(): List<DownloadEntity> = withContext(Dispatchers.IO) {
        dao.completed()
    }

    /** قائمة تشغيل ذكية للصوتيات فقط */
    suspend fun audioPlaylist(): List<DownloadEntity> =
        smartPlaylist().filter { it.isAudio }

    /** قائمة تشغيل ذكية للفيديوهات فقط */
    suspend fun videoPlaylist(): List<DownloadEntity> =
        smartPlaylist().filter { !it.isAudio }

    /** تصدير نسخة احتياطية JSON لقائمة التحميلات إلى ملف يختاره المستخدم */
    suspend fun exportBackup(context: Context, target: Uri) = withContext(Dispatchers.IO) {
        val all = dao.observeAll().first()
        val json = gson.toJson(all)
        context.contentResolver.openOutputStream(target)?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("تعذر فتح ملف النسخة الاحتياطية")
    }

    /** استعادة قائمة التحميلات من نسخة احتياطية JSON */
    suspend fun importBackup(context: Context, source: Uri): Int = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(source)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("تعذر قراءة ملف النسخة الاحتياطية")

        val type = object : TypeToken<List<DownloadEntity>>() {}.type
        val items: List<DownloadEntity> = gson.fromJson(json, type) ?: emptyList()
        items.forEach { dao.insert(it.copy(id = 0)) }
        items.size
    }
}
