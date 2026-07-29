package com.opiconchanger.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.SearchView
import com.opiconchanger.R
import com.opiconchanger.iconpack.IconPackParser
import com.opiconchanger.model.IconEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IconPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "target_package_name"
        const val EXTRA_COMPONENT = "target_component"
        const val EXTRA_ICON_PACK = "icon_pack_package"
        const val RESULT_DRAWABLE_NAME = "selected_drawable_name"
        const val RESULT_ICON_PACK = "selected_icon_pack"
    }

    private lateinit var parser: IconPackParser
    private lateinit var adapter: IconAdapter
    private var searchJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_picker)

        val iconPack = intent.getStringExtra(EXTRA_ICON_PACK) ?: "app.lawnchair.lawnicons"
        parser = IconPackParser(applicationContext)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 6)
        adapter = IconAdapter(parser) { entry -> onIconSelected(entry) }
        recyclerView.adapter = adapter

        val tvEmpty = findViewById<android.widget.TextView>(R.id.tvEmpty)
        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { performSearch(iconPack, query ?: ""); return true }
            override fun onQueryTextChange(newText: String?): Boolean {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch { delay(300); performSearch(iconPack, newText ?: "") }
                return true
            }
        })

        loadIcons(iconPack, "")
    }

    private fun performSearch(pack: String, query: String) { loadIcons(pack, query) }

    private fun loadIcons(pack: String, query: String) {
        val tvEmpty = findViewById<android.widget.TextView>(R.id.tvEmpty)
        lifecycleScope.launch {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = getString(R.string.loading_icons)
            val byApp = parser.searchByApp(pack, query)
            val byName = parser.searchByDrawableName(pack, query)
            val merged = (byApp + byName).distinctBy { "${it.iconPackPackage}:${it.drawableName}" }
            adapter.submitList(merged)
            tvEmpty.visibility = if (merged.isEmpty()) View.VISIBLE else View.GONE
            if (merged.isEmpty()) tvEmpty.text = getString(R.string.no_results)
        }
    }

    private fun onIconSelected(entry: IconEntry) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(RESULT_DRAWABLE_NAME, entry.drawableName)
            putExtra(RESULT_ICON_PACK, entry.iconPackPackage)
        })
        finish()
    }
}

// ==================== Adapter ====================

class IconAdapter(
    private val parser: IconPackParser,
    private val onItemClick: (IconEntry) -> Unit
) : RecyclerView.Adapter<IconAdapter.VH>() {

    private var items: List<IconEntry> = emptyList()

    fun submitList(new: List<IconEntry>) { items = new; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_icon_entry, parent, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(items[pos])
    override fun getItemCount() = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val iv = v.findViewById<ImageView>(R.id.ivIcon)

        fun bind(e: IconEntry) {
            iv.setImageResource(android.R.drawable.ic_menu_gallery)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                val bmp = parser.loadIconBitmap(e.iconPackPackage, e.drawableName)
                if (bmp != null) iv.setImageBitmap(bmp)
            }
            iv.setOnClickListener { onItemClick(e) }
        }
    }
}
