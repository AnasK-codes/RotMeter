package com.rotmeter.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.rotmeter.app.R
import com.rotmeter.app.databinding.ItemBadgeBinding
import com.rotmeter.app.util.Gamification

class BadgesAdapter(private val onClick: (Gamification.BadgeState) -> Unit) :
    RecyclerView.Adapter<BadgesAdapter.ViewHolder>() {
    private var badges = emptyList<Gamification.BadgeState>()

    fun submitList(items: List<Gamification.BadgeState>) {
        badges = items.toList()
        notifyDataSetChanged()
    }

    override fun getItemCount() = badges.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemBadgeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(badges[position])

    inner class ViewHolder(private val binding: ItemBadgeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Gamification.BadgeState) {
            val context = binding.root.context
            binding.badgeName.setText(item.badge.nameRes)
            binding.badgeStatus.setText(if (item.unlocked) R.string.badge_unlocked else R.string.badge_locked)
            binding.badgeIcon.setImageResource(if (item.unlocked) R.drawable.ic_badge else R.drawable.ic_lock)
            val color = ContextCompat.getColor(context, if (item.unlocked) R.color.rot_fresh else R.color.rot_text_secondary)
            binding.badgeIcon.setColorFilter(color)
            binding.badgeStatus.setTextColor(color)
            binding.badgeName.setTextColor(ContextCompat.getColor(context,
                if (item.unlocked) R.color.rot_text_primary else R.color.rot_text_secondary))
            binding.root.alpha = if (item.unlocked) 1f else 0.55f
            binding.root.setStrokeColor(ContextCompat.getColor(context,
                if (item.unlocked) R.color.quest_success_stroke else R.color.rot_stroke))
            binding.root.contentDescription = context.getString(R.string.badge_description,
                context.getString(item.badge.nameRes), binding.badgeStatus.text)
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
