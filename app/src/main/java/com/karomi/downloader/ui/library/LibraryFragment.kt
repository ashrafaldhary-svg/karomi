package com.karomi.downloader.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.karomi.downloader.KaromiApp
import com.karomi.downloader.R
import com.karomi.downloader.data.DownloadEntity
import com.karomi.downloader.databinding.FragmentLibraryBinding
import com.karomi.downloader.download.DownloadService
import com.karomi.downloader.ui.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * مكتبة التحميلات: قائمة تشغيل ذكية (الكل / فيديو / صوت)، تشغيل مباشر،
 * إيقاف مؤقت واستئناف، حذف ونقل الملفات.
 */
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val dao by lazy { (requireActivity().application as KaromiApp).database.downloads() }
    private lateinit var adapter: LibraryAdapter
    private var allItems: List<DownloadEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = LibraryAdapter(
            onPlay = { play(it) },
            onPauseResume = { pauseOrResume(it) },
            onDelete = { confirmDelete(it) },
            onMove = { moveFile(it) }
        )
        binding.libraryList.layoutManager = LinearLayoutManager(requireContext())
        binding.libraryList.adapter = adapter

        binding.filterChips.setOnCheckedStateChangeListener { _, _ -> applyFilter() }

        // مراقبة قاعدة البيانات لتحديث القائمة تلقائيًا (قائمة تشغيل ذكية)
        viewLifecycleOwner.lifecycleScope.launch {
            dao.observeAll().collect { items ->
                allItems = items
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val filtered = when (binding.filterChips.checkedChipId) {
            R.id.chip_video -> allItems.filter { !it.isAudio }
            R.id.chip_audio -> allItems.filter { it.isAudio }
            else -> allItems
        }
        adapter.submit(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /** تشغيل مباشر داخل التطبيق عبر ExoPlayer */
    private fun play(item: DownloadEntity) {
        if (!File(item.filePath).exists()) {
            Toast.makeText(requireContext(), R.string.file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(requireContext(), PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PATH, item.filePath)
                .putExtra(PlayerActivity.EXTRA_TITLE, item.title)
        )
    }

    private fun pauseOrResume(item: DownloadEntity) {
        when (item.status) {
            DownloadEntity.STATUS_DOWNLOADING ->
                DownloadService.pause(requireContext(), item.id)

            DownloadEntity.STATUS_PAUSED ->
                DownloadService.start(
                    requireContext(), item.id, item.url, item.filePath, item.title
                )

            else -> Unit
        }
    }

    private fun confirmDelete(item: DownloadEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, item.title))
            .setPositiveButton(R.string.delete) { _, _ -> delete(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun delete(item: DownloadEntity) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            File(item.filePath).delete()
            File(item.filePath + ".parts").delete()
            dao.delete(item)
        }
    }

    /** نقل الملف إلى مجلد مخصص (Videos أو Music داخل مجلد التطبيق) */
    private fun moveFile(item: DownloadEntity) {
        val folders = arrayOf("Videos", "Music", "Downloads")
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.move_to)
            .setItems(folders) { _, which ->
                viewLifecycleOwner.lifecycleScope(Dispatchers.IO) {
                    val target = File(requireContext().getExternalFilesDir(null), folders[which])
                    target.mkdirs()
                    val dest = File(target, File(item.filePath).name)
                    val ok = File(item.filePath).renameTo(dest)
                    if (ok) dao.update(item.copy(filePath = dest.absolutePath))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            if (ok) R.string.move_done else R.string.move_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
