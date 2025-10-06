package com.shieldtechhub.shieldkids.features.policy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R

class PolicyStatusAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_INFO = 1
        private const val TYPE_RESTRICTION = 2
    }
    
    private var items: List<PolicyStatusItem> = emptyList()
    
    fun updateItems(newItems: List<PolicyStatusItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PolicyStatusItem.Header -> TYPE_HEADER
            is PolicyStatusItem.Info -> TYPE_INFO
            is PolicyStatusItem.Restriction -> TYPE_RESTRICTION
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_policy_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_INFO -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_policy_info, parent, false)
                InfoViewHolder(view)
            }
            TYPE_RESTRICTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_policy_restriction, parent, false)
                RestrictionViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PolicyStatusItem.Header -> {
                (holder as HeaderViewHolder).bind(item)
            }
            is PolicyStatusItem.Info -> {
                (holder as InfoViewHolder).bind(item)
            }
            is PolicyStatusItem.Restriction -> {
                (holder as RestrictionViewHolder).bind(item)
            }
        }
    }
    
    override fun getItemCount(): Int = items.size
    
    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.tvHeaderTitle)
        
        fun bind(header: PolicyStatusItem.Header) {
            titleTextView.text = header.title
        }
    }
    
    class InfoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val labelTextView: TextView = itemView.findViewById(R.id.tvInfoLabel)
        private val valueTextView: TextView = itemView.findViewById(R.id.tvInfoValue)
        
        fun bind(info: PolicyStatusItem.Info) {
            labelTextView.text = info.label
            valueTextView.text = info.value
        }
    }
    
    class RestrictionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeTextView: TextView = itemView.findViewById(R.id.tvRestrictionType)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tvRestrictionDescription)
        
        fun bind(restriction: PolicyStatusItem.Restriction) {
            typeTextView.text = restriction.type
            descriptionTextView.text = restriction.description
        }
    }
}