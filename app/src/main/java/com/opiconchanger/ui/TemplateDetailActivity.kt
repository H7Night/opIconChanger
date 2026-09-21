package com.opiconchanger.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.opiconchanger.R
import com.opiconchanger.iconpack.IconPackParser
import com.opiconchanger.model.IconRequest
import com.opiconchanger.utils.IconApplier
import com.opiconchanger.utils.IconRequestWriter
import com.opiconchanger.utils.RestartUtils
import com.opiconchanger.utils.TemplateEntry
import com.opiconchanger.utils.TemplateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TemplateDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEMPLATE_ID = "template_id"
    }

    data class Row(
        val entry: TemplateEntry,
        val label: String,
        val appIcon: Drawable?,
        val installed: Boolean
    )

    private lateinit var tvTitle: TextView
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var cbSelectAll: CheckBox
    private lateinit var btnApply: MaterialButton
    private lateinit var parser: IconPackParser

    private var templateId: String = ""
    private var templateName: String = ""
    private var rows: List<Row> = emptyList()
    private val selected = mutableSetOf<String>()
    private var adapter: RowAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_template_detail)
        templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID).orEmpty()
        if (templateId.isBlank()) { finish(); return }
        parser = IconPackParser(applicationContext)

        tvTitle = findViewById(R.id.tvDetailTitle)
        rv = findViewById(R.id.rvTemplateApps)
        tvEmpty = findViewById(R.id.tvDetailEmpty)
        cbSelectAll = findViewById(R.id.cbSelectAll)
        btnApply = findViewById(R.id.btnApplyTemplate)
        rv.layoutManager = LinearLayoutManager(this)

        btnApply.setOnClickListener { applySelected() }
        findViewById<View>(R.id.btnDetailMenu).setOnClickListener { showMenu(it) }
        cbSelectAll.setOnCheckedChangeListener(newSelectAllListener())
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val tpl = withContext(Dispatchers.IO) {
                TemplateStore.list(this@TemplateDetailActivity).find { it.id == templateId }
            }
            if (tpl == null) {
                Toast.makeText(this@TemplateDetailActivity, R.string.log_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            templateName = tpl.name
            tvTitle.text = tpl.name
            rows = withContext(Dispatchers.IO) { buildRows(tpl.entries) }
            selected.retainAll(rows.filter { it.installed }.map { it.entry.pkg })
            adapter = RowAdapter(rows, parser, selected) { updateApply() }
            rv.adapter = adapter
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            val allSelected = rows.isNotEmpty() &&
                rows.filter { it.installed }.all { it.entry.pkg in selected }
            cbSelectAll.setOnCheckedChangeListener(null)
            cbSelectAll.isChecked = allSelected
            cbSelectAll.setOnCheckedChangeListener(newSelectAllListener())
            updateApply()
        }
    }

    private fun newSelectAllListener() =
        android.widget.CompoundButton.OnCheckedChangeListener { _, checked ->
            selected.clear()
            if (checked) rows.filter { it.installed }.forEach { selected.add(it.entry.pkg) }
            adapter?.notifyDataSetChanged()
            updateApply()
        }

    private fun buildRows(entries: List<TemplateEntry>): List<Row> = entries.map { e ->
        val installed = isInstalled(e.pkg)
        val label = if (installed) appLabel(e.pkg) else getString(R.string.template_app_uninstalled) + " · " + e.pkg
        Row(e, label, if (installed) appIcon(e.pkg) else null, installed)
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (_: Exception) { false }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun appIcon(pkg: String): Drawable? = try {
        packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) { null }

    private fun updateApply() {
        btnApply.isEnabled = selected.isNotEmpty()
        btnApply.text = if (selected.isEmpty()) getString(R.string.template_apply)
        else getString(R.string.template_apply) + " (${selected.size})"
    }

    private fun applySelected() {
        if (selected.isEmpty()) return
        val chosen = rows.filter { it.installed && it.entry.pkg in selected }.map { it.entry }
        val dialog = showApplying()
        lifecycleScope.launch {
            var ok = 0
            var skip = 0
            try {
                val directFailed = chosen.filterNot {
                    IconApplier.applyIcon(applicationContext, it.pkg, it.iconPackPkg, it.drawableResName)
                }
                val request = IconRequest(
                    com.opiconchanger.model.RequestAction.APPLY,
                    chosen.map {
                        com.opiconchanger.model.IconAction(it.pkg, it.iconPackPkg, it.drawableResName)
                    }
                )
                val sent = if (request.items.isNotEmpty()) {
                    IconRequestWriter.send(applicationContext, request)
                } else false
                skip = if (sent) 0 else directFailed.size
                ok = chosen.size - skip
            } finally {
                dialog.dismiss()
            }
            Toast.makeText(
                this@TemplateDetailActivity,
                getString(R.string.apply_result, ok, skip),
                Toast.LENGTH_LONG
            ).show()
            // 与一键还原一致：调用系统方法重启桌面，让修改立即生效
            withContext(Dispatchers.IO) {
                RestartUtils.restartLauncher(applicationContext)
            }
        }
    }

    private fun showApplying(): AlertDialog {
        val pad = (resources.displayMetrics.density * 20).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(ProgressBar(this@TemplateDetailActivity), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(TextView(this@TemplateDetailActivity).apply {
                text = getString(R.string.template_applying)
                setPadding(pad / 2, 0, 0, 0)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        return AlertDialog.Builder(this)
            .setTitle(R.string.template_apply)
            .setView(row)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.template_rename)
            menu.add(0, 2, 1, R.string.template_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> rename()
                    2 -> delete()
                }
                true
            }
            show()
        }
    }

    private fun rename() {
        TemplateDialogs.promptName(this, getString(R.string.template_rename), templateName) { name ->
            TemplateStore.rename(this, templateId, name)
            templateName = name
            tvTitle.text = name
        }
    }

    private fun delete() {
        TemplateDialogs.confirm(
            this,
            getString(R.string.template_delete),
            getString(R.string.template_delete_confirm, templateName)
        ) {
            TemplateStore.delete(this, templateId)
            finish()
        }
    }

    inner class RowAdapter(
        private val items: List<Row>,
        private val iconParser: IconPackParser,
        private val selectedPkgs: MutableSet<String>,
        private val onToggle: () -> Unit
    ) : RecyclerView.Adapter<RowAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app_entry, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun onViewRecycled(holder: VH) { holder.cancelLoad() }

        override fun getItemCount() = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val cb = v.findViewById<CheckBox>(R.id.cbSelect)
            private val iv = v.findViewById<ImageView>(R.id.ivIcon)
            private val tvLabel = v.findViewById<TextView>(R.id.tvLabel)
            private val tvPkg = v.findViewById<TextView>(R.id.tvPackage)
            private val btn = v.findViewById<View>(R.id.btnChange)
            private var loadJob: kotlinx.coroutines.Job? = null

            fun cancelLoad() { loadJob?.cancel(); loadJob = null }

            fun bind(row: Row) {
                tvLabel.text = row.label
                tvPkg.text = row.entry.pkg
                itemView.alpha = if (row.installed) 1f else 0.4f
                btn.visibility = View.GONE
                cb.isEnabled = row.installed
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = row.entry.pkg in selectedPkgs
                cb.setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPkgs.add(row.entry.pkg) else selectedPkgs.remove(row.entry.pkg)
                    onToggle()
                }
                cancelLoad()
                iv.setImageDrawable(row.appIcon)
                loadJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val bmp = iconParser.loadIconBitmap(row.entry.iconPackPkg, row.entry.drawableResName)
                    if (bmp != null) iv.setImageBitmap(bmp) else iv.setImageDrawable(row.appIcon)
                }
            }
        }
    }
}
