package com.example.myspeaker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class LedEffectItem(
    val id: Int,
    val name: String,
    val emoji: String
)

class LedEffectAdapter(
    private val effects: List<LedEffectItem>,
    private val onEffectSelected: (LedEffectItem) -> Unit
) : RecyclerView.Adapter<LedEffectAdapter.ViewHolder>() {

    private var selectedId: Int = 0

    fun setSelectedEffect(effectId: Int) {
        val oldId = selectedId
        selectedId = effectId
        // Only notify changed items for efficiency
        effects.forEachIndexed { index, item ->
            if (item.id == oldId || item.id == effectId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_led_effect, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val effect = effects[position]
        holder.bind(effect, effect.id == selectedId)
    }

    override fun getItemCount() = effects.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEmoji: TextView = itemView.findViewById(R.id.tvEffectEmoji)
        private val tvName: TextView = itemView.findViewById(R.id.tvEffectName)

        fun bind(effect: LedEffectItem, isSelected: Boolean) {
            tvEmoji.text = effect.emoji
            tvName.text = effect.name
            
            // Highlight selected
            itemView.isSelected = isSelected
            itemView.alpha = if (isSelected) 1f else 0.8f
            
            itemView.setOnClickListener {
                onEffectSelected(effect)
            }
        }
    }
}
