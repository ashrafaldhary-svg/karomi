package com.karomi.downloader.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.karomi.downloader.databinding.ItemQualityBinding
import com.karomi.downloader.extractor.VideoExtractor
import com.karomi.downloader.util.FormatUtils

class QualityAdapter(
    private val formats: List<VideoExtractor.FormatOption>,
    private val onPick: (VideoExtractor.FormatOption) -> Unit
) : RecyclerView.Adapter<QualityAdapter.Holder>() {

    inner class Holder(val binding: ItemQualityBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemQualityBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = formats.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val format = formats[position]
        holder.binding.qualityLabel.text = format.label
        holder.binding.qualitySize.text = FormatUtils.size(format.sizeBytes)
        holder.binding.root.setOnClickListener { onPick(format) }
    }
}
