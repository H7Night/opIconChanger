package com.opiconchanger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.opiconchanger.R
import com.opiconchanger.utils.IconTemplate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TemplateAdapter(
    private var items: List<IconTemplate>,
    private val onClick: (IconTemplate) -> Unit,
    private val onMenu: (IconTemplate, View) -> Unit
) : RecyclerView.Adapter<TemplateAdapter.VH>() {

    fun submitList(new: List<IconTemplate>) {
        items = new
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_template_entry, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvName = v.findViewById<TextView>(R.id.tvTemplateName)
        private val tvMeta = v.findViewById<TextView>(R.id.tvTemplateMeta)
        private val btnMenu = v.findViewById<ImageButton>(R.id.btnTemplateMenu)
        private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(t: IconTemplate) {
            tvName.text = t.name
            tvMeta.text = itemView.context.getString(
                R.string.template_app_count, t.entries.size
            ) + " · " + itemView.context.getString(
                R.string.template_created_at, fmt.format(Date(t.createdAt))
            )
            itemView.setOnClickListener { onClick(t) }
            btnMenu.setOnClickListener { onMenu(t, it) }
        }
    }
}
