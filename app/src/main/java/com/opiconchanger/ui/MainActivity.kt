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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "opIconChanger"
        private const val DEFAULT_ICON_PACK = "app.lawnchair.lawnicons"
    }

    private lateinit var pageApps: View
    private lateinit var pageLog: View
    private lateinit var spinnerIconPack: Spinner
    private lateinit var tvIconCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var rvApps: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvLog: TextView

    private var allApps: List<AppEntry> = emptyList()
    private var appAdapter: AppAdapter? = null
    private var pendingApp: AppEntry? = null
    private var iconPacks: List<String> = listOf(DEFAULT_ICON_PACK)
    private var iconPackParser: IconPackParser? = null
    
    private var currentLauncherPackage: String = MainHook.LAUNCHER_PACKAGE

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

        pageApps = layoutInflater.inflate(R.layout.page_apps, findViewById(R.id.pageContainer), false)
        pageLog = layoutInflater.inflate(R.layout.page_log, findViewById(R.id.pageContainer), false)
        findViewById<ViewGroup>(R.id.pageContainer).apply {
            addView(pageApps); addView(pageLog)
            pageLog.visibility = View.GONE
        }

        val tabApps = findViewById<TextView>(R.id.tabApps)
        val tabLog = findViewById<TextView>(R.id.tabLog)
        tabApps.setOnClickListener { showPage(0, tabApps, tabLog) }
        tabLog.setOnClickListener { showPage(1, tabLog, tabApps) }

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

        tvLog = pageLog.findViewById(R.id.tvLog)
        pageLog.findViewById<Button>(R.id.btnRefreshLog).setOnClickListener { loadLogs() }

        detectLauncher()
        loadIconPacks()
        loadApps()
        loadLogs()
    }

    private fun detectLauncher() {
        CoroutineScope(Dispatchers.IO).launch {
            val launcher = try {
                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                resolveInfo?.activityInfo?.packageName ?: MainHook.LAUNCHER_PACKAGE
            } catch (e: Exception) {
                MainHook.LAUNCHER_PACKAGE
            }
            currentLauncherPackage = launcher
            Log.i(TAG, "Detected launcher: $currentLauncherPackage")
        }
    }

    private fun showPage(idx: Int, active: TextView, inactive: TextView) {
        pageApps.visibility = if (idx == 0) View.VISIBLE else View.GONE
        pageLog.visibility = if (idx == 1) View.VISIBLE else View.GONE
        active.setTextColor(0xFF000000.toInt())
        inactive.setTextColor(0xFF888888.toInt())
    }

    private fun loadIconPacks() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parser = IconPackParser(applicationContext)
                iconPackParser = parser
                val found = parser.scanInstalledIconPacks()
                iconPacks = if (found.isEmpty()) listOf(DEFAULT_ICON_PACK) else found
            } catch (e: Exception) {
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
                    override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        prefs.edit().putString("selected_icon_pack", iconPacks[pos]).apply()
                        updateIconCount(iconPacks[pos])
                    }
                    override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
                }
            }
        }
    }

    private fun updateIconCount(pack: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parser = iconPackParser ?: IconPackParser(applicationContext)
                val count = parser.loadIconPack(pack).size
                withContext(Dispatchers.Main) { tvIconCount.text = "$count 个图标" }
            } catch (_: Exception) {}
        }
    }

    private fun getSelectedIconPack() = spinnerIconPack.selectedItem?.toString() ?: DEFAULT_ICON_PACK

    private fun loadApps() {
        CoroutineScope(Dispatchers.IO).launch {
            allApps = queryInstalledApps()
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
        val seen = mutableSetOf<String>()
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { ri ->
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
        iconPickerLauncher.launch(Intent(this, IconPickerActivity::class.java).apply {
            putExtra(IconPickerActivity.EXTRA_PACKAGE_NAME, app.pkg)
            putExtra(IconPickerActivity.EXTRA_COMPONENT, app.component)
            putExtra(IconPickerActivity.EXTRA_ICON_PACK, getSelectedIconPack())
        })
    }

    private fun applyIcon(app: AppEntry, drawable: String, pack: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val ok = tryBroadcast(app, drawable, pack)
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(if (ok) "广播已发送" else "广播失败")
                    .setMessage("${app.label}\n图标: $drawable\n\n桌面: $currentLauncherPackage\n\n需要重启桌面以生效")
                    .setPositiveButton("重启桌面") { _, _ -> restartLauncher() }
                    .setNegativeButton("稍后", null).show()
            }
        }
    }

    private fun tryBroadcast(app: AppEntry, drawable: String, pack: String): Boolean {
        val pkg = app.pkg.trim()
        val comp = app.component.trim()
        val dr = drawable.trim()
        val iconPack = pack.trim()
        
        // 尝试构建兼容多种版本的 key
        // 1. 包名|组件名|图标名
        val key1 = "$pkg|$comp|$dr"
        // 2. 包名/组件名
        val key2 = "$pkg/$comp"
        
        val baseCmd = "am broadcast -a com.oplus.uxdesign.action.SAVE_CHOOSE_ICON " +
                "-p $currentLauncherPackage " +
                "--es user_set_name \"$dr\" " +
                "--es use_choose_package \"$iconPack\" " +
                "--es use_choose_item_component \"$comp\" " +
                "--ei user_modify_type 1 " +
                "--ei user_reset_type 0 " +
                "--include-stopped-packages"

        return try {
            // 尝试第一种 Key 格式
            val cmd1 = "$baseCmd --es choose_icon_key \"$key1\""
            Log.i(TAG, "Executing variant 1: $cmd1")
            val p1 = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd1))
            val out1 = BufferedReader(InputStreamReader(p1.inputStream)).readText()
            p1.waitFor()
            
            // 尝试第二种 Key 格式
            val cmd2 = "$baseCmd --es choose_icon_key \"$key2\""
            Log.i(TAG, "Executing variant 2: $cmd2")
            val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd2))
            val out2 = BufferedReader(InputStreamReader(p2.inputStream)).readText()
            p2.waitFor()

            Log.i(TAG, "Variant 1 result: $out1")
            Log.i(TAG, "Variant 2 result: $out2")
            
            out1.contains("result=0") || out2.contains("result=0")
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast failed", e)
            false
        }
    }

    private fun restartLauncher() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 强制停止桌面
                Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $currentLauncherPackage")).waitFor()
                delay(800)
                // 尝试通过 Launcher Intent 拉起桌面
                val launchIntent = packageManager.getLaunchIntentForPackage(currentLauncherPackage)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
                Log.i(TAG, "Launcher $currentLauncherPackage restarted.")
            } catch (e: Exception) {
                Log.e(TAG, "Restart failed", e)
            }
        }
    }

    private fun loadLogs() {
        tvLog.text = "加载中…"
        CoroutineScope(Dispatchers.IO).launch {
            val text = fetchLogcat()
            withContext(Dispatchers.Main) { tvLog.text = text ?: "(无日志)" }
        }
    }

    private fun fetchLogcat(): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -d -s opIconChanger:* -t 150"))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor()
            if (out.isNotBlank()) "[Root Log]\n$out" else "(无日志输出)"
        } catch (e: Exception) { "日志读取失败: ${e.message}" }
    }

    data class AppEntry(val pkg: String, val label: String, val component: String, val icon: Drawable)

    inner class AppAdapter(private var items: List<AppEntry>, val onClick: (AppEntry) -> Unit) : RecyclerView.Adapter<AppAdapter.VH>() {
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
