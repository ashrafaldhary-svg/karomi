package com.karomi.downloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val quality: String,          // مثال: "1080p MP4" أو "MP3 128kbps"
    val filePath: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: String = STATUS_QUEUED,
    val isAudio: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_PAUSED = "paused"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}
