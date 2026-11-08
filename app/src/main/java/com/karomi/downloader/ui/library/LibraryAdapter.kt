package com.karomi.downloader.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.karomi.downloader.R
import com.karomi.downloader.data.DownloadEntity
import com.karomi.downloader.databinding.ItemDownloadBinding
import com.karomi.downloader.util.FormatUtils

class LibraryAdapter(
    private val onPlay: (DownloadEntity) -> Unit,
    private val onPauseResume: (DownloadEntity) -> Unit,
    private val onDelete: (DownloadEntity) -> Unit,
    private val onMove: (DownloadEntity) -> Unit
) : RecyclerView.Adapter<LibraryAdapter.Holder>() {

    private val items = mutableListOf<DownloadEntity>()

    fun submit(list: List<DownloadEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class Holder(val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            itemTitle.text = item.title
            itemInfo.text = buildString {
                append(item.quality)
                if (item.totalBytes > 0) append(" • ").append(FormatUtils.size(item.totalBytes))
                append(" • ").append(statusText(root, item.status))
            }

            val showProgress = item.status == DownloadEntity.STATUS_DOWNLOADING
            itemProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
            if (item.totalBytes > 0) {
                itemProgress.progress = (item.downloadedBytes * 100 / item.totalBytes).toInt()
            }

            // زر التشغيل يظهر فقط للمكتملة، وزر الإيقاف/الاستئناف للجارية أو المتوقفة
            playButton.visibility =
                if (item.status == DownloadEntity.STATUS_COMPLETED) View.VISIBLE else View.GONE
            pauseButton.visibility =
                if (item.status == DownloadEntity.STATUS_DOWNLOADING ||
                    item.status == DownloadEntity.STATUS_PAUSED
                ) View.VISIBLE else View.GONE

            pauseButton.setImageResource(
                if (item.status == DownloadEntity.STATUS_DOWNLOADING)
                    R.drawable.ic_pause else R.drawable.ic_play
            )

            playButton.setOnClickListener { onPlay(item) }
            pauseButton.setOnClickListener { onPauseResume(item) }
            deleteButton.setOnClickListener { onDelete(item) }
            moveButton.setOnClickListener { onMove(item) }
        }
    }

    private fun statusText(view: View, status: String): String = when (status) {
        DownloadEntity.STATUS_DOWNLOADING -> view.context.getString(R.string.status_downloading)
        DownloadEntity.STATUS_PAUSED -> view.context.getString(R.string.status_paused)
        DownloadEntity.STATUS_COMPLETED -> view.context.getString(R.string.status_completed)
        DownloadEntity.STATUS_FAILED -> view.context.getString(R.string.status_failed)
        else -> view.context.getString(R.string.status_queued)
    }
}
