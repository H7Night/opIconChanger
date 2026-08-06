package com.opiconchanger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.core.content.ContextCompat
import com.opiconchanger.utils.AppFilter
import com.opiconchanger.utils.AppFilterPredicates
import com.opiconchanger.utils.CustomIconStore
import com.opiconchanger.utils.FilterableApp
import com.opiconchanger.utils.LogRenderer
import com.opiconchanger.utils.LogUtils
import com.opiconchanger.utils.RestartUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.opiconchanger.MainHook
import com.opiconchanger.R
import com.opiconchanger.iconpack.IconPackParser
import com.opiconchanger.model.IconRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
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
    private lateinit var etLogKeyword: EditText
    private lateinit var tvLineCount: TextView
    private lateinit var tvMatchCount: TextView
    private lateinit var rootDot: View
    private lateinit var tvRootStatus: TextView

    private var lastRawLog: String = ""
    private var rootAvailable: Boolean = false

    private var allApps: List<AppEntry> = emptyList()
    private var appAdapter: AppAdapter? = null
    private var pendingApp: AppEntry? = null
    private var iconPacks: List<String> = listOf(DEFAULT_ICON_PACK)
    private var iconPackParser: IconPackParser? = null
    
    private var currentLauncherPackage: String = MainHook.LAUNCHER_PACKAGE
    private var appFilter: AppFilter = AppFilter.ALL
    private var adaptedSet: Set<String> = emptySet()
    private var customizedSet: Set<String> = emptySet()
    private lateinit var spinnerAppFilter: Spinner

    private val iconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        LogUtils.i("iconPickerLauncher: resultCode=${result.resultCode}")
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(this, "取消选择", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val data = result.data
        if (data == null) { Toast.makeText(this, "data为空", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
        val drawable = data.getStringExtra(IconPickerActivity.RESULT_DRAWABLE_NAME)
        val pack = data.getStringExtra(IconPickerActivity.RESULT_ICON_PACK) ?: getSelectedIconPack()
        val app = pendingApp
        if (drawable == null) { Toast.makeText(this, "drawable为空", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
        if (app == null) { Toast.makeText(this, "app为空", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
        pendingApp = null
        Toast.makeText(this, "正在应用: $drawable", Toast.LENGTH_SHORT).show()
        applyIconNew(app, drawable, pack)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val container = findViewById<ViewGroup>(R.id.pageContainer)
        pageApps = layoutInflater.inflate(R.layout.page_apps, container, false)
        pageLog = layoutInflater.inflate(R.layout.page_log, container, false)
        container.apply {
            addView(pageApps); addView(pageLog)
            pageLog.visibility = View.GONE
        }

        val tabApps = findViewById<View>(R.id.tabApps)
        val tabLog = findViewById<View>(R.id.tabLog)
        tabApps.setOnClickListener { showPage(0) }
        tabLog.setOnClickListener { showPage(1) }

        spinnerIconPack = pageApps.findViewById(R.id.spinnerIconPack)
        tvIconCount = pageApps.findViewById(R.id.tvIconCount)
        etSearch = pageApps.findViewById(R.id.etSearch)
        rvApps = pageApps.findViewById(R.id.recyclerView)
        tvEmpty = pageApps.findViewById(R.id.tvEmpty)
        rvApps.layoutManager = LinearLayoutManager(this)

        spinnerAppFilter = pageApps.findViewById(R.id.spinnerAppFilter)
        val filterAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.app_filter_options)
        )
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAppFilter.adapter = filterAdapter
        spinnerAppFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                appFilter = AppFilter.entries.getOrElse(pos) { AppFilter.ALL }
                if (appFilter == AppFilter.UNADAPTED) reloadCustomizedSet()
                else filterApps(etSearch.text?.toString() ?: "")
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterApps(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        tvLog = pageLog.findViewById(R.id.tvLog)
        etLogKeyword = pageLog.findViewById(R.id.etLogKeyword)
        tvLineCount = pageLog.findViewById(R.id.tvLineCount)
        tvMatchCount = pageLog.findViewById(R.id.tvMatchCount)
        rootDot = pageLog.findViewById(R.id.rootDot)
        tvRootStatus = pageLog.findViewById(R.id.tvRootStatus)

        etLogKeyword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderLog() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        pageLog.findViewById<MaterialButton>(R.id.btnRestartLauncher).setOnClickListener { restartLauncher() }
        pageLog.findViewById<MaterialButton>(R.id.btnRefreshLog).setOnClickListener { loadLogs() }
        pageLog.findViewById<MaterialButton>(R.id.btnClearLog).setOnClickListener { clearLogs() }

        detectLauncher()
        loadIconPacks()
        loadApps()
        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        if (appFilter == AppFilter.UNADAPTED) reloadCustomizedSet()
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
            val expected = MainHook.LAUNCHER_PACKAGE
            val match = launcher == expected
            LogUtils.i("══════ 桌面检测 ══════")
            LogUtils.i("  检测到桌面包名: $launcher")
            LogUtils.i("  预期桌面包名:   $expected")
            LogUtils.i("  是否匹配:       $match")
            if (!match) {
                LogUtils.w("  ⚠️ 包名不匹配！请在 LSPosed 管理器中手动勾选 '$launcher'")
            }
        }
    }

    private fun showPage(idx: Int) {
        pageApps.visibility = if (idx == 0) View.VISIBLE else View.GONE
        pageLog.visibility = if (idx == 1) View.VISIBLE else View.GONE
        // 底部 Tab 属于 activity_main，需通过 Activity 根视图查找
        val activeLabel = findViewById<TextView>(if (idx == 0) R.id.tabAppsLabel else R.id.tabLogLabel)
        val inactiveLabel = findViewById<TextView>(if (idx == 0) R.id.tabLogLabel else R.id.tabAppsLabel)
        val activeInd = findViewById<View>(if (idx == 0) R.id.tabAppsIndicator else R.id.tabLogIndicator)
        val inactiveInd = findViewById<View>(if (idx == 0) R.id.tabLogIndicator else R.id.tabAppsIndicator)

        activeLabel.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
        activeLabel.typeface = android.graphics.Typeface.DEFAULT_BOLD
        inactiveLabel.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
        inactiveLabel.typeface = android.graphics.Typeface.DEFAULT
        activeInd.visibility = View.VISIBLE
        inactiveInd.visibility = View.INVISIBLE
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
                        reloadAdaptedSet(iconPacks[pos])
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
                val count = parser.loadIconPack(pack).map { it.drawableName }.distinct().size
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
                val isSystem = ai.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                AppEntry(pkg, pm.getApplicationLabel(ai).toString(), ri.activityInfo.name, pm.getApplicationIcon(ai), isSystem)
            } catch (_: Exception) { null }
        }
    }

    private fun filterApps(query: String) {
        val filtered = allApps.filter {
            AppFilterPredicates.matches(
                FilterableApp(it.pkg, it.isSystem),
                appFilter,
                adaptedSet,
                customizedSet
            )
        }.let { list ->
            if (query.isBlank()) list
            else list.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
        }
        appAdapter?.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvApps.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        if (filtered.isEmpty()) {
            tvEmpty.text = if (appFilter == AppFilter.UNADAPTED)
                getString(R.string.app_list_all_adapted)
            else getString(R.string.app_list_empty)
        }
    }

    private fun reloadAdaptedSet(pack: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val parser = iconPackParser ?: IconPackParser(applicationContext)
            adaptedSet = parser.adaptedPackageSet(pack)
            withContext(Dispatchers.Main) { filterApps(etSearch.text?.toString() ?: "") }
        }
    }

    private fun reloadCustomizedSet(retries: Int = 0) {
        CoroutineScope(Dispatchers.IO).launch {
            customizedSet = CustomIconStore.customizedPackageSet()
            withContext(Dispatchers.Main) { filterApps(etSearch.text?.toString() ?: "") }
        }
        if (retries > 0) {
            CoroutineScope(Dispatchers.Default).launch {
                kotlinx.coroutines.delay(800)
                reloadCustomizedSet(retries - 1)
            }
        }
    }

    private fun onAppClicked(app: AppEntry) {
        pendingApp = app
        iconPickerLauncher.launch(Intent(this, IconPickerActivity::class.java).apply {
            putExtra(IconPickerActivity.EXTRA_PACKAGE_NAME, app.pkg)
            putExtra(IconPickerActivity.EXTRA_COMPONENT, app.component)
            putExtra(IconPickerActivity.EXTRA_ICON_PACK, getSelectedIconPack())
        })
    }

    // ═══════════════════════════════════════════
    //  写入请求文件，由 MainHook 在 Launcher 进程中处理
    // ═══════════════════════════════════════════
    private fun applyIconNew(app: AppEntry, drawable: String, pack: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("icon", drawable))

        CoroutineScope(Dispatchers.IO).launch {
            val request = IconRequest(app.pkg, pack, drawable)
            val json = request.toJson()

            LogUtils.i("═══ 写入请求: ${app.label} → $drawable ═══")

            var success = false

            // 路径 1: filesDir + world-readable（Launcher 作为系统应用可读）
            val localFile = File(filesDir, "opicon_request.json")
            try {
                localFile.writeText(json)
                localFile.setReadable(true, false)
                localFile.setWritable(true, false)
                Runtime.getRuntime().exec(arrayOf("chmod", "666", localFile.absolutePath)).waitFor()
                LogUtils.i("  filesDir 写入成功: ${localFile.absolutePath} (${json.length}B)")
                success = true
            } catch (e: Exception) {
                LogUtils.w("  filesDir 失败: ${e.message}")
            }

            // 路径 2: 用 su 写到 /data/local/tmp/（如果可用）
            try {
                val tmpPath = "/data/local/tmp/opicon_request.json"
                // 先写临时文件，再用 su cp
                val tmpFile = File(filesDir, "opicon_req_tmp.json")
                tmpFile.writeText(json)
                tmpFile.setReadable(true, false)
                val p = Runtime.getRuntime().exec(arrayOf(
                    "su", "-c", "cp ${tmpFile.absolutePath} $tmpPath && chmod 666 $tmpPath"
                ))
                p.waitFor()
                if (p.exitValue() == 0) {
                    LogUtils.i("  su → /data/local/tmp/ 成功")
                    success = true
                } else {
                    LogUtils.w("  su cp 失败 exit=${p.exitValue()}")
                }
                tmpFile.delete()
            } catch (e: Exception) {
                LogUtils.w("  su 路径不可用: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@MainActivity,
                        "${app.label}\n图标: $drawable\n\n请返回桌面以应用图标",
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity,
                        "写入失败\n请检查日志 Tab", Toast.LENGTH_LONG).show()
                }
                if (success && appFilter == AppFilter.UNADAPTED) reloadCustomizedSet(retries = 3)
            }
        }
    }

    private fun loadLogs() {
        tvLog.text = "正在加载日志…"
        CoroutineScope(Dispatchers.IO).launch {
            val sb = StringBuilder()

            // 1. App 自身日志 (filesDir)
            val appDiag = java.io.File(filesDir, "opicon_diag.txt")
            sb.appendLine("═══ App 诊断 (filesDir) ═══")
            sb.appendLine("存在: ${appDiag.exists()}")

            // 2. Launcher 进程诊断 (/data/oplus/uxicons/choose/)
            val hookDiag = java.io.File("/data/oplus/uxicons/choose/opicon_hook_diag.txt")
            sb.appendLine()
            sb.appendLine("═══ Launcher 诊断 ═══")
            sb.appendLine("路径: ${hookDiag.absolutePath}")
            sb.appendLine("存在: ${hookDiag.exists()}")
            if (hookDiag.exists()) {
                sb.appendLine("大小: ${hookDiag.length()}B")
                sb.appendLine("── 内容 ──")
                // 跨进程目录无直接读取权限，走 su
                val diagContent = runCatching { hookDiag.readText() }
                    .getOrElse {
                        runCatching {
                            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat ${hookDiag.absolutePath}"))
                            val out = java.io.BufferedReader(java.io.InputStreamReader(p.inputStream)).readText()
                            p.waitFor()
                            out.ifBlank { "(空文件)" }
                        }.getOrDefault("(无权限读取)")
                    }
                sb.appendLine(diagContent.takeLast(8000))
            } else {
                sb.appendLine("⚠️ Launcher 诊断文件不存在")
                sb.appendLine("   → Launcher 进程的 onResume Hook 未触发")
                sb.appendLine("   → 或 /data/oplus/uxicons/choose/ 不可写")
            }

            // 2. Logcat (作为补充)
            sb.appendLine()
            sb.appendLine("═══ Logcat ═══")
            val logcat = fetchLogcat()
            sb.appendLine(logcat)

            // 3. 当前检测到的桌面信息
            sb.appendLine()
            sb.appendLine("═══ 桌面检测 ═══")
            sb.appendLine("当前识别桌面包名: $currentLauncherPackage")
            sb.appendLine("MainHook 目标包名:  ${MainHook.LAUNCHER_PACKAGE}")
            sb.appendLine("是否匹配:           ${currentLauncherPackage == MainHook.LAUNCHER_PACKAGE}")

            rootAvailable = isRootAvailable()
            withContext(Dispatchers.Main) { showLog(sb.toString()) }
        }
    }

    private fun showLog(raw: String) {
        lastRawLog = raw
        renderLog()
    }

    private fun renderLog() {
        val keyword = etLogKeyword.text?.toString() ?: ""
        val (spanned, stats) = LogRenderer.render(lastRawLog, keyword)
        tvLog.text = spanned
        tvLineCount.text = getString(R.string.log_line_count, stats.lineCount)
        tvMatchCount.text = getString(R.string.log_match_lines, stats.matchLines)

        val (dotColor, statusText) = if (rootAvailable)
            R.color.root_on to getString(R.string.log_root_ready)
        else
            R.color.root_off to getString(R.string.log_root_unavailable)
        rootDot.background.mutate().setTint(ContextCompat.getColor(this, dotColor))
        tvRootStatus.setTextColor(ContextCompat.getColor(this, if (rootAvailable) R.color.root_on else R.color.terminal_text))
        tvRootStatus.text = statusText
    }

    private fun isRootAvailable(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo ok"))
        p.waitFor()
        p.exitValue() == 0
    } catch (_: Exception) { false }

    private fun restartLauncher() {
        tvLog.text = "正在重启桌面…"
        CoroutineScope(Dispatchers.IO).launch {
            RestartUtils.restartLauncher(this@MainActivity)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "桌面已重启", Toast.LENGTH_SHORT).show()
                loadLogs()
            }
        }
    }

    private fun clearLogs() {
        tvLog.text = "正在清空…"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "rm /data/oplus/uxicons/choose/opicon_hook_diag.txt")).waitFor()
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "日志已清空", Toast.LENGTH_SHORT).show()
                loadLogs()
            }
        }
    }

    private fun fetchLogcat(): String {
        // 尝试多种方式读取 logcat
        val commands = listOf(
            arrayOf("logcat", "-d", "-s", "opIconChanger:*", "-t", "100"),   // 无需 root
            arrayOf("su", "-c", "logcat -d -s opIconChanger:* -t 100")       // 需要 root
        )
        for (cmd in commands) {
            try {
                val p = Runtime.getRuntime().exec(cmd)
                val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
                val err = BufferedReader(InputStreamReader(p.errorStream)).readText()
                p.waitFor()
                val exitOk = p.exitValue() == 0
                if (out.isNotBlank()) {
                    val label = if (cmd[0] == "su") "[Root]" else "[直接]"
                    return "$label (exit=${p.exitValue()}):\n$out"
                }
                if (exitOk && err.isBlank()) return "(logcat 无匹配日志)"
                // 继续尝试下一个命令
            } catch (_: Exception) {}
        }
        return "(logcat 读取失败 — 所有方式均不可用)"
    }

    data class AppEntry(val pkg: String, val label: String, val component: String, val icon: Drawable, val isSystem: Boolean)

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
