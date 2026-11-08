package com.karomi.downloader.extractor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * استخراج المسار الصوتي من ملف فيديو محمّل.
 *
 * - AAC (M4A): مدعوم أصليًا عبر MediaExtractor + MediaMuxer بدون إعادة ترميز.
 * - MP3: يتطلب ترميزًا حقيقيًا — ادمج مكتبة FFmpeg (مثل ffmpeg-kit) في [convertToMp3].
 */
object AudioExtractor {

    /** استخراج الصوت AAC من فيديو وحفظه كملف .m4a. يعيد مسار الملف الناتج. */
    fun extractToM4a(videoPath: String): String {
        val outPath = videoPath.substringBeforeLast('.') + ".m4a"
        val extractor = MediaExtractor()
        extractor.setDataSource(videoPath)

        var audioTrack = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrack = i
                format = f
                break
            }
        }
        check(audioTrack >= 0 && format != null) { "لا يوجد مسار صوتي في الفيديو" }

        extractor.selectTrack(audioTrack)
        val muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerTrack = muxer.addTrack(format)
        muxer.start()

        val buffer = ByteBuffer.allocate(512 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            info.offset = 0
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrack, buffer, info)
            extractor.advance()
        }

        muxer.stop()
        muxer.release()
        extractor.release()
        return outPath
    }

    /**
     * تحويل إلى MP3. يتطلب دمج FFmpeg؛ حتى يتم الدمج يُعاد ملف M4A كما هو
     * (AAC يعمل على جميع مشغّلات Android الحديثة).
     *
     * للدمج: أضف تبعية com.arthenica:ffmpeg-kit-full ثم نفّذ:
     *   FFmpegKit.execute("-i input.m4a -codec:a libmp3lame -b:a 128k output.mp3")
     */
    fun convertToMp3(m4aPath: String): String {
        // TODO: دمج ffmpeg-kit لإنتاج MP3 فعلي بمعدل 128kbps
        return m4aPath
    }

    /** حذف ملف بأمان */
    fun deleteQuietly(path: String) {
        runCatching { File(path).delete() }
    }
}
