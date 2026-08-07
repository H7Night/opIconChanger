package com.opiconchanger.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.opiconchanger.R
import com.opiconchanger.iconpack.IconPackParser
import com.opiconchanger.model.IconEntry
import androidx.lifecycle.lifecycleScope
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
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var tvResultCount: TextView
    private lateinit var tvEmpty: TextView

    private var iconPack: String = "app.lawnchair.lawnicons"
    private var searchJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_picker)

        iconPack = intent.getStringExtra(EXTRA_ICON_PACK) ?: "app.lawnchair.lawnicons"
        parser = IconPackParser(applicationContext)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, adaptiveSpanCount())
        adapter = IconAdapter(parser) { entry -> onIconSelected(entry) }
        recyclerView.adapter = adapter

        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        tvResultCount = findViewById(R.id.tvResultCount)
        tvEmpty = findViewById(R.id.tvEmpty)
        findViewById<MaterialButton>(R.id.btnSearch).setOnClickListener { performSearch() }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performSearch(); true } else false
        }
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch { kotlinx.coroutines.delay(300); performSearch() }
            }
        })
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
        btnClearSearch.setOnClickListener { etSearch.text?.clear(); performSearch() }

        loadIcons("")
    }

    /** 自适应列数：仿 Lawnicons 按屏宽计算，目标单元格约 68dp（手机上约 5 列） */
    private fun adaptiveSpanCount(): Int {
        val cellDp = 68f
        val widthPx = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density
        return (widthPx / (cellDp * density)).toInt().coerceAtLeast(3)
    }

    private fun performSearch() {
        loadIcons(etSearch.text?.toString()?.trim() ?: "")
    }

    private fun loadIcons(query: String) {
        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = getString(R.string.loading_icons)
        lifecycleScope.launch {
            val merged = parser.searchFuzzy(iconPack, query).distinctBy { it.drawableName }
            adapter.submitList(merged)
            tvResultCount.text = if (query.isBlank()) {
                getString(R.string.icon_total_count, merged.size)
            } else {
                getString(R.string.icon_match_count, merged.size)
            }
            tvEmpty.visibility = if (merged.isEmpty()) View.VISIBLE else View.GONE
            if (merged.isEmpty()) tvEmpty.text = getString(R.string.no_results)
        }
    }

    private fun onIconSelected(entry: IconEntry) {
        android.util.Log.i("opIconChanger", "IconPicker: selected ${entry.drawableName}")
        android.widget.Toast.makeText(this, "已选择: ${entry.drawableName}", android.widget.Toast.LENGTH_SHORT).show()
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
    override fun onViewRecycled(h: VH) { h.cancelLoad() }
    override fun getItemCount() = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val iv = v.findViewById<ImageView>(R.id.ivIcon)
        private var loadJob: kotlinx.coroutines.Job? = null

        fun cancelLoad() { loadJob?.cancel(); loadJob = null }

        fun bind(e: IconEntry) {
            iv.setImageResource(android.R.drawable.ic_menu_gallery)
            iv.setAlpha(0.35f)
            cancelLoad()
            loadJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                val bmp = parser.loadIconBitmap(e.iconPackPackage, e.drawableName)
                if (bmp != null) { iv.setImageBitmap(bmp); iv.setAlpha(1f) }
            }
            iv.setOnClickListener { onItemClick(e) }
        }
    }
}
