package com.shieldtechhub.shieldkids.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R

data class TopAppItem(
    val appName: String,
    val packageName: String,
    val totalTime: String,
    val category: String
)

class TopAppsAdapter(private val apps: List<TopAppItem>) : RecyclerView.Adapter<TopAppsAdapter.TopAppViewHolder>() {

    class TopAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appName: TextView = itemView.findViewById(R.id.tvAppName)
        val appTime: TextView = itemView.findViewById(R.id.tvAppTime)
        val appCategory: TextView = itemView.findViewById(R.id.tvAppCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_app, parent, false)
        return TopAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopAppViewHolder, position: Int) {
        val app = apps[position]
        holder.appName.text = app.appName
        holder.appTime.text = app.totalTime
        holder.appCategory.text = app.category
    }

    override fun getItemCount(): Int = apps.size
}