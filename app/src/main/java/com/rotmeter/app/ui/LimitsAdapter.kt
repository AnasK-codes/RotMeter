package com.rotmeter.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rotmeter.app.R
import com.rotmeter.app.db.AppInfo
import com.rotmeter.app.blocking.BlockingPreferences
import com.rotmeter.app.databinding.ItemAppLimitBinding
import kotlin.math.roundToInt

class LimitsAdapter(
    private val onSelectionChanged: ((AppInfo, Boolean) -> Unit)? = null,
    private val onClick: (AppInfo) -> Unit
) :
    ListAdapter<AppInfo, LimitsAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemAppLimitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), onClick, onSelectionChanged)

    class ViewHolder(private val binding: ItemAppLimitBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo, onClick: (AppInfo) -> Unit, onSelectionChanged: ((AppInfo, Boolean) -> Unit)?) {
            val context = binding.root.context
            AppVisuals.showInitial(binding.appInitial, app.label, app.rotWeight)
            binding.appLabel.text = app.label
            binding.appCategory.text = app.categoryName
            val limit = app.dailyLimitMin
            binding.appLimit.text = limit?.let { context.getString(R.string.limit_pill_minutes, it) }
                ?: context.getString(R.string.no_limit)
            binding.todayUsage.text = limit?.let { context.getString(R.string.today_limit_usage, app.todayMinutes, it) }
                ?: context.getString(R.string.today_usage_minutes, app.todayMinutes)
            val fraction = if (limit != null && limit > 0) app.todayMinutes.toDouble() / limit else 0.0
            val color = ContextCompat.getColor(context, when {
                limit == null -> R.color.rot_text_secondary
                fraction < 0.7 -> R.color.rot_fresh
                fraction <= 1.0 -> R.color.rot_mild
                else -> R.color.rot_error_text
            })
            binding.usageProgress.setIndicatorColor(color)
            binding.usageProgress.progress = (fraction * 100).coerceIn(0.0, 100.0).roundToInt()
            binding.usageProgress.contentDescription = binding.todayUsage.text
            val selected = BlockingPreferences.isSelected(context, app.packageName)
            binding.blockApp.setOnCheckedChangeListener(null)
            binding.blockApp.isChecked = selected
            binding.blockApp.isEnabled = limit != null || selected
            binding.blockApp.setText(if (limit == null) R.string.block_needs_limit else R.string.block_selected_app)
            binding.blockApp.setOnCheckedChangeListener { _, checked ->
                onSelectionChanged?.invoke(app, checked)
                binding.blockApp.isEnabled = limit != null || checked
            }
            binding.root.setOnClickListener { onClick(app) }
        }
    }
    private object Diff : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean = oldItem.packageName == newItem.packageName
        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean = oldItem == newItem
    }
}
