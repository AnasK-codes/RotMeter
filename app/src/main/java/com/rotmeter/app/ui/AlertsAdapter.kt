package com.rotmeter.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rotmeter.app.db.AlertItem
import com.rotmeter.app.util.RelativeTime
import com.rotmeter.app.databinding.ItemAlertBinding

class AlertsAdapter : ListAdapter<AlertItem, AlertsAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onCurrentListChanged(previousList: MutableList<AlertItem>, currentList: MutableList<AlertItem>) {
        super.onCurrentListChanged(previousList, currentList)
        // A refresh can advance relative times while all stored alert fields stay identical.
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    class ViewHolder(private val binding: ItemAlertBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.shapeAppearanceModel = binding.root.shapeAppearanceModel.toBuilder()
                .setBottomLeftCornerSize(4f * binding.root.resources.displayMetrics.density).build()
        }

        fun bind(alert: AlertItem) {
            binding.message.text = alert.message
            binding.appLabel.text = alert.label
            binding.createdAt.text = RelativeTime.format(alert.createdAt)
            binding.createdAt.contentDescription = alert.createdAt
        }
    }

    private object Diff : DiffUtil.ItemCallback<AlertItem>() {
        override fun areItemsTheSame(oldItem: AlertItem, newItem: AlertItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AlertItem, newItem: AlertItem): Boolean = oldItem == newItem
    }
}
