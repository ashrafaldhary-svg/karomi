package com.karomi.downloader.ui.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.karomi.downloader.KaromiApp
import com.karomi.downloader.R
import com.karomi.downloader.data.DownloadEntity
import com.karomi.downloader.databinding.DialogQualityBinding
import com.karomi.downloader.databinding.FragmentHomeBinding
import com.karomi.downloader.download.DownloadService
import com.karomi.downloader.extractor.VideoExtractor
import com.karomi.downloader.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val client = OkHttpClient()
    private val extractor by lazy { VideoExtractor(client) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.urlInput.setText(arguments?.getString(ARG_URL).orEmpty())

        binding.pasteButton.setOnClickListener { pasteFromClipboard() }
        binding.fetchButton.setOnClickListener { fetchFormats() }

        // لصق تلقائي إذا كانت الحافظة تحتوي رابطًا عند فتح الشاشة
        autoPasteClipboardUrl()
    }

    private fun autoPasteClipboardUrl() {
        if (binding.urlInput.text?.isNotBlank() == true) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return
        if (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        binding.urlInput.setText(url)
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            toast(getString(R.string.clipboard_empty))
        } else {
            val url = Regex("https?://\\S+").find(text)?.value
            if (url != null) binding.urlInput.setText(url)
            else toast(getString(R.string.no_url_in_clipboard))
        }
    }

    private fun fetchFormats() {
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (!url.startsWith("http")) {
            toast(getString(R.string.invalid_url))
            return
        }

        binding.fetchProgress.visibility = View.VISIBLE
        binding.fetchButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val formats = extractor.extract(url)
                if (formats.isEmpty()) {
                    // رابط منصة غير مباشر: اعرض الجودات القياسية للاختيار
                    showStandardQualitySheet(url)
                } else {
                    showFormatsSheet(url, formats)
                }
            } catch (e: Exception) {
                toast(getString(R.string.fetch_failed, e.message))
            } finally {
                binding.fetchProgress.visibility = View.GONE
                binding.fetchButton.isEnabled = true
            }
        }
    }

    /** عرض الجودات المستخرجة مع الحجم التقريبي لكل جودة */
    private fun showFormatsSheet(url: String, formats: List<VideoExtractor.FormatOption>) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = DialogQualityBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.qualityList.layoutManager = LinearLayoutManager(requireContext())
        sheetBinding.qualityList.adapter = QualityAdapter(formats) { format ->
            dialog.dismiss()
            startDownload(url, format.label, format.extension,
                format.kind == VideoExtractor.FormatOption.Kind.AUDIO,
                toMp3 = format.extension == "mp3",
                toM4a = format.extension == "m4a")
        }
        dialog.show()
    }

    /** عرض الجودات القياسية عندما يكون الرابط لصفحة منصة (يتطلب دمج yt-dlp) */
    private fun showStandardQualitySheet(url: String) {
        val labels = VideoExtractor.VIDEO_QUALITIES + VideoExtractor.AUDIO_QUALITIES
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.choose_quality)
            .setItems(labels.toTypedArray()) { _, which ->
                val label = labels[which]
                val isAudio = label.startsWith("MP3") || label.startsWith("AAC")
                val ext = when {
                    label.contains("MP3") -> "mp3"
                    label.contains("AAC") -> "m4a"
                    label.contains("WEBM") -> "webm"
                    else -> "mp4"
                }
                startDownload(url, label, ext, isAudio,
                    toMp3 = ext == "mp3", toM4a = ext == "m4a")
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startDownload(
        url: String, qualityLabel: String, ext: String, isAudio: Boolean,
        toMp3: Boolean, toM4a: Boolean
    ) {
        val context = requireContext()
        val title = url.substringAfterLast('/').substringBefore('?').ifBlank { "karomi_media" }
        val dir = File(
            context.getExternalFilesDir(null),
            if (isAudio) "Music" else "Videos"
        ).apply { mkdirs() }
        val dest = File(dir, "${FormatUtils.safeFileName(title)}.$ext")

        lifecycleScope.launch {
            val dao = (context.applicationContext as KaromiApp).database.downloads()
            val id = withContext(Dispatchers.IO) {
                dao.insert(
                    DownloadEntity(
                        url = url,
                        title = title,
                        quality = qualityLabel,
                        filePath = dest.absolutePath,
                        isAudio = isAudio
                    )
                )
            }
            DownloadService.start(context, id, url, dest.absolutePath, title, toMp3, toM4a)
            toast(getString(R.string.download_started))
        }
    }

    private fun toast(message: String) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_URL = "url"

        fun newInstance(url: String) = HomeFragment().apply {
            arguments = bundleOf(ARG_URL to url)
        }
    }
}
