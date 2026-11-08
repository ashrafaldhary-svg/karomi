package com.karomi.downloader.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.karomi.downloader.databinding.ActivityPlayerBinding
import java.io.File

/** مشغّل داخلي لتشغيل الفيديوهات والصوتيات المحمّلة مباشرة من المكتبة */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        supportActionBar?.title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        if (path.isBlank() || !File(path).exists()) {
            finish()
            return
        }

        val exoPlayer = ExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        player = exoPlayer
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
    }
}
