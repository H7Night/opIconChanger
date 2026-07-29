package com.opiconchanger.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.opiconchanger.MainHook
import com.opiconchanger.R
import com.opiconchanger.iconpack.IconPackParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "opIconChanger"
        private const val DEFAULT_ICON_PACK = "app.lawnchair.lawnicons"
    }

    // === Pages ===
    private lateinit var pageApps: View
    private lateinit var pageLog: View

    // === Apps page ===
    private lateinit var spinnerIconPack: Spinner
    private lateinit var tvIconCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var rvApps: RecyclerView
    private lateinit var tvEmpty: TextView
    private var allApps: List<AppEntry> = emptyList()
    private var appAdapter: AppAdapter? = null
    private var pendingApp: AppEntry? = null
    private var iconPacks: List<String> = listOf(DEFAULT_ICON_PACK)
    private var iconPackParser: IconPackParser? = null

    // === Log page ===
    private lateinit var tvLog: TextView

    private val iconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val drawable = data.getStringExtra(IconPickerActivity.RESULT_DRAWABLE_NAME) ?: return@registerForActivityResult
        val pack = data.getStringExtra(IconPickerActivity.RESULT_ICON_PACK) ?: getSelectedIconPack()
        val app = pendingApp ?: return@registerForActivityResult
        pendingApp = null
        applyIcon(app, drawable, pack)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pages
        pageApps = layoutInflater.inflate(R.layout.page_apps, findViewById(R.id.pageContainer), false)
        pageLog = layoutInflater.inflate(R.layout.page_log, findViewById(R.id.pageContainer), false)
        findViewById<ViewGroup>(R.id.pageContainer).apply {
            addView(pageApps)
            addView(pageLog)
            pageLog.visibility = View.GONE
        }

        // Bottom nav
        val tabApps = findViewById<TextView>(R.id.tabApps)
        val tabLog = findViewById<TextView>(R.id.tabLog)
        tabApps.setOnClickListener { showPage(0, tabApps, tabLog) }
        tabLog.setOnClickListener { showPage(1, tabLog, tabApps) }

        // Apps page
        spinnerIconPack = pageApps.findViewById(R.id.spinnerIconPack)
        tvIconCount = pageApps.findViewById(R.id.tvIconCount)
        etSearch = pageApps.findViewById(R.id.etSearch)
        rvApps = pageApps.findViewById(R.id.recyclerView)
        tvEmpty = pageApps.findViewById(R.id.tvEmpty)
        rvApps.layoutManager = LinearLayoutManager(this)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterApps(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Log page
        tvLog = pageLog.findViewById(R.id.tvLog)
        pageLog.findViewById<Button>(R.id.btnRefreshLog).setOnClickListener { loadLogs() }

        loadIconPacks()
        loadApps()
        loadLogs()
    }

    private fun showPage(idx: Int, active: TextView, inactive: TextView) {
        pageApps.visibility = if (idx == 0) View.VISIBLE else View.GONE
        pageLog.visibility = if (idx == 1) View.VISIBLE else View.GONE
        active.setTextColor(0xFF000000.toInt())
        inactive.setTextColor(0xFF888888.toInt())
    }

    // ==================== Icon Pack ====================

    private fun loadIconPacks() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parser = IconPackParser(applicationContext)
                iconPackParser = parser
                val found = parser.scanInstalledIconPacks()
                iconPacks = if (found.isEmpty()) listOf(DEFAULT_ICON_PACK) else found
                Log.i(TAG, "Icon packs found: $iconPacks")
            } catch (e: Exception) {
                Log.e(TAG, "scanInstalledIconPacks failed", e)
                iconPacks = listOf(DEFAULT_ICON_PACK)
            }
            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, iconPacks)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerIconPack.adapter = adapter
                val prefs = getSharedPreferences("opiconchanger_prefs", MODE_PRIVATE)
                val saved = prefs.getString("selected_icon_pack", DEFAULT_ICON_PACK) ?: DEFAULT_ICON_PACK
                val idx = iconPacks.indexOf(saved).takeIf { it >= 0 } ?: 0
                spinnerIconPack.setSelection(idx)
                spinnerIconPack.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        prefs.edit().putString("selected_icon_pack", iconPacks[pos]).apply()
                        updateIconCount(iconPacks[pos])
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
                updateIconCount(iconPacks[idx])
            }
        }
    }

    private fun updateIconCount(pack: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parser = iconPackParser ?: IconPackParser(applicationContext)
                val count = parser.loadIconPack(pack).size
                Log.i(TAG, "Icon pack $pack: $count icons loaded")
                withContext(Dispatchers.Main) { tvIconCount.text = "$count 个图标" }
            } catch (e: Exception) {
                Log.e(TAG, "loadIconPack failed for $pack", e)
            }
        }
    }

    private fun getSelectedIconPack() = spinnerIconPack.selectedItem?.toString() ?: DEFAULT_ICON_PACK

    // ==================== App List ====================

    private fun loadApps() {
        CoroutineScope(Dispatchers.IO).launch {
            allApps = queryInstalledApps()
            Log.i(TAG, "Apps loaded: ${allApps.size}")
            withContext(Dispatchers.Main) {
                appAdapter = AppAdapter(allApps) { onAppClicked(it) }
                rvApps.adapter = appAdapter
                filterApps("")
            }
        }
    }

    private fun queryInstalledApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val flags = PackageManager.MATCH_ALL or PackageManager.GET_ACTIVITIES
        val seen = mutableSetOf<String>()
        return pm.queryIntentActivities(intent, flags).mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (!seen.add(pkg)) return@mapNotNull null
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                AppEntry(pkg, pm.getApplicationLabel(ai).toString(), ri.activityInfo.name, pm.getApplicationIcon(ai))
            } catch (_: Exception) { null }
        }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) allApps
        else allApps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
        appAdapter?.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvApps.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun onAppClicked(app: AppEntry) {
        pendingApp = app
        Log.i(TAG, "Opening icon picker for ${app.pkg} with pack ${getSelectedIconPack()}")
        iconPickerLauncher.launch(Intent(this, IconPickerActivity::class.java).apply {
            putExtra(IconPickerActivity.EXTRA_PACKAGE_NAME, app.pkg)
            putExtra(IconPickerActivity.EXTRA_COMPONENT, app.component)
            putExtra(IconPickerActivity.EXTRA_ICON_PACK, getSelectedIconPack())
        })
    }

    // ==================== Apply Icon ====================

    private fun applyIcon(app: AppEntry, drawable: String, pack: String) {
        Log.i(TAG, "applyIcon: ${app.pkg} -> $drawable (pack=$pack)")
        CoroutineScope(Dispatchers.IO).launch {
            val ok = tryBroadcast(app, drawable, pack)
            Log.i(TAG, "tryBroadcast result: $ok")
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(if (ok) "广播已发送" else "广播失败")
                    .setMessage("${app.label}\n图标: $drawable\n\n${if (ok) "需要重启桌面以生效" else "请检查 root 权限"} ")
                    .setPositiveButton("重启桌面") { _, _ ->
                        Log.i(TAG, "User clicked restart launcher")
                        restartLauncher()
                    }
                    .setNegativeButton("稍后", null)
                    .show()
            }
        }
    }

    private fun tryBroadcast(app: AppEntry, drawable: String, pack: String): Boolean {
        val cmd = buildString {
            append("am broadcast -p ${MainHook.LAUNCHER_PACKAGE} ")
            append("-a com.oplus.uxdesign.action.SAVE_CHOOSE_ICON ")
            append("--es user_set_name \"$drawable\" ")
            append("--es use_choose_package \"$pack\" ")
            append("--es use_choose_item_component \"${app.component}\" ")
            append("--ei user_modify_type 1 --ei user_reset_type 0 ")
            append("--es choose_icon_key \"${app.pkg}|${app.component}|$drawable\" ")
            append("--include-stopped-packages")
        }
        Log.i(TAG, "Broadcast cmd: $cmd")
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val err = BufferedReader(InputStreamReader(p.errorStream)).readText()
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor()
            Log.i(TAG, "Broadcast exit=${p.exitValue()} out=$out err=$err")
            err.isBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast failed", e)
            false
        }
    }

    private fun restartLauncher() {
        Log.i(TAG, "restartLauncher: executing am force-stop...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop ${MainHook.LAUNCHER_PACKAGE}"))
                val err = BufferedReader(InputStreamReader(p.errorStream)).readText()
                p.waitFor()
                Log.i(TAG, "restartLauncher: exit=${p.exitValue()} err=$err")
            } catch (e: Exception) {
                Log.e(TAG, "restartLauncher failed", e)
            }
        }
    }

    // ==================== Log ====================

    private fun loadLogs() {
        tvLog.text = "加载中…"
        CoroutineScope(Dispatchers.IO).launch {
            val text = fetchLogcat()
            withContext(Dispatchers.Main) { tvLog.text = text ?: "(无日志)" }
        }
    }

    private fun fetchLogcat(): String? {
        // try no-root first
        try {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-s", "opIconChanger:*", "-t", "200"))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor()
            if (out.isNotBlank()) return "[无 root 模式]\n$out"
        } catch (_: Exception) {}

        // try root
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -d -s opIconChanger:* -t 200"))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            val err = BufferedReader(InputStreamReader(p.errorStream)).readText()
            p.waitFor()
            return buildString {
                if (err.isNotBlank()) append("[stderr]\n$err\n\n")
                if (out.isNotBlank()) append("[root 模式]\n$out") else append("(root 模式无输出)")
            }
        } catch (e: Exception) {
            return "日志读取失败: ${e.message}"
        }
    }

    // ==================== Types ====================

    data class AppEntry(val pkg: String, val label: String, val component: String, val icon: Drawable)

    inner class AppAdapter(
        private var items: List<AppEntry>,
        private val onClick: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {
        fun submitList(new: List<AppEntry>) { items = new; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app_entry, parent, false))
        override fun onBindViewHolder(h: VH, pos: Int) = h.bind(items[pos])
        override fun getItemCount() = items.size
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val iv = v.findViewById<ImageView>(R.id.ivIcon)
            private val tvL = v.findViewById<TextView>(R.id.tvLabel)
            private val tvP = v.findViewById<TextView>(R.id.tvPackage)
            private val btn = v.findViewById<Button>(R.id.btnChange)
            fun bind(e: AppEntry) {
                tvL.text = e.label; tvP.text = e.pkg; iv.setImageDrawable(e.icon)
                btn.setOnClickListener { onClick(e) }
            }
        }
    }
}
