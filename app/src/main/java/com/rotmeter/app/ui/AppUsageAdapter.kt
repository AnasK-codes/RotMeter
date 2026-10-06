package com.rotmeter.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rotmeter.app.R
import com.rotmeter.app.db.AppUsage
import com.rotmeter.app.databinding.ItemAppUsageBinding
import kotlin.math.roundToInt

class AppUsageAdapter(private val showWeeklyShare: Boolean = false) :
    ListAdapter<AppUsage, AppUsageAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val total = if (showWeeklyShare) currentList.sumOf { it.minutes.toLong() } else 0L
        holder.bind(getItem(position), showWeeklyShare, total)
    }

    // The denominator can change even when an individual app row stays identical.
    override fun onCurrentListChanged(previousList: MutableList<AppUsage>, currentList: MutableList<AppUsage>) {
        super.onCurrentListChanged(previousList, currentList)
        if (showWeeklyShare && itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    class ViewHolder(private val binding: ItemAppUsageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppUsage, showShare: Boolean, total: Long) {
            val context = binding.root.context
            binding.appLabel.text = app.label
            binding.appMinutes.text = context.getString(R.string.app_minutes, app.minutes)
            binding.usageShare.isVisible = showShare
            binding.shareLabel.isVisible = showShare
            if (showShare) {
                val percent = if (total > 0) (app.minutes.toDouble() / total * 100).roundToInt().coerceIn(0, 100) else 0
                val color = ContextCompat.getColor(context, AppVisuals.colorRes(app.rotWeight))
                binding.appMinutes.setTextColor(color)
                binding.usageShare.setIndicatorColor(color)
                binding.usageShare.progress = percent
                binding.shareLabel.text = context.getString(R.string.usage_share_percent, percent)
                binding.usageShare.contentDescription = binding.shareLabel.text
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AppUsage>() {
        override fun areItemsTheSame(oldItem: AppUsage, newItem: AppUsage): Boolean = oldItem.packageName == newItem.packageName
        override fun areContentsTheSame(oldItem: AppUsage, newItem: AppUsage): Boolean = oldItem == newItem
    }
}
