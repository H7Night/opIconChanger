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
import com.opiconchanger.utils.IconApplier
import com.opiconchanger.utils.IconConfigStore
import com.opiconchanger.utils.IconPaths
import com.opiconchanger.utils.IconRequestWriter
import com.opiconchanger.utils.IconTemplate
import com.opiconchanger.utils.LogRenderer
import com.opiconchanger.utils.LogUtils
import com.opiconchanger.utils.RestartUtils
import com.opiconchanger.utils.RootExec
import com.opiconchanger.utils.TemplateEntry
import com.opiconchanger.utils.TemplateStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_ICON_PACK = "app.lawnchair.lawnicons"
    }

    private lateinit var pageApps: View
    private lateinit var pageTemplates: View
    private lateinit var pageLog: View
    private lateinit var rvTemplates: RecyclerView
    private lateinit var tvTemplatesEmpty: TextView
    private var templateAdapter: TemplateAdapter? = null
    private var currentPage: Int = 0
    private val selectedPackages: MutableSet<String> = mutableSetOf()
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
        pageTemplates = layoutInflater.inflate(R.layout.page_templates, container, false)
        pageLog = layoutInflater.inflate(R.layout.page_log, container, false)
        container.apply {
            addView(pageApps); addView(pageTemplates); addView(pageLog)
            pageTemplates.visibility = View.GONE
            pageLog.visibility = View.GONE
        }

        findViewById<View>(R.id.tabApps).setOnClickListener { showPage(0) }
        findViewById<View>(R.id.tabTemplates).setOnClickListener { showPage(1) }
        findViewById<View>(R.id.tabLog).setOnClickListener { showPage(2) }

        spinnerIconPack = pageApps.findViewById(R.id.spinnerIconPack)
        tvIconCount = pageApps.findViewById(R.id.tvIconCount)
        etSearch = pageApps.findViewById(R.id.etSearch)
        rvApps = pageApps.findViewById(R.id.recyclerView)
        tvEmpty = pageApps.findViewById(R.id.tvEmpty)
        rvApps.layoutManager = LinearLayoutManager(this)
        pageApps.findViewById<View>(R.id.btnAppsMenu).setOnClickListener { showAppsMenu(it) }

        rvTemplates = pageTemplates.findViewById(R.id.rvTemplates)
        tvTemplatesEmpty = pageTemplates.findViewById(R.id.tvTemplatesEmpty)
        rvTemplates.layoutManager = LinearLayoutManager(this)

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
                if (appFilter == AppFilter.UNADAPTED || appFilter == AppFilter.CUSTOMIZED) reloadCustomizedSet()
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
        if (appFilter == AppFilter.UNADAPTED || appFilter == AppFilter.CUSTOMIZED) reloadCustomizedSet()
        if (currentPage == 1) loadTemplates()
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
        currentPage = idx
        val pages = listOf(pageApps, pageTemplates, pageLog)
        pages.forEachIndexed { i, v -> v.visibility = if (i == idx) View.VISIBLE else View.GONE }

        val ids = listOf(
            Triple(R.id.tabAppsLabel, R.id.tabAppsIndicator, R.id.tabApps),
            Triple(R.id.tabTemplatesLabel, R.id.tabTemplatesIndicator, R.id.tabTemplates),
            Triple(R.id.tabLogLabel, R.id.tabLogIndicator, R.id.tabLog)
        )
        ids.forEachIndexed { i, (labelId, indId, _) ->
            val label = findViewById<TextView>(labelId)
            val ind = findViewById<View>(indId)
            label.setTextColor(ContextCompat.getColor(this, if (i == idx) R.color.teal_primary else R.color.on_surface_variant))
            label.typeface = if (i == idx) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            ind.visibility = if (i == idx) View.VISIBLE else View.INVISIBLE
        }

        if (idx == 1) loadTemplates()
    }

    private fun loadTemplates() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = TemplateStore.list(this@MainActivity)
            withContext(Dispatchers.Main) {
                val adapter = templateAdapter
                if (adapter == null) {
                    templateAdapter = TemplateAdapter(
                        list,
                        onClick = { openTemplate(it) },
                        onMenu = { tpl, anchor -> showTemplateMenu(tpl, anchor) }
                    )
                    rvTemplates.adapter = templateAdapter
                } else {
                    adapter.submitList(list)
                }
                tvTemplatesEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                rvTemplates.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun openTemplate(t: IconTemplate) {
        startActivity(Intent(this, TemplateDetailActivity::class.java).apply {
            putExtra(TemplateDetailActivity.EXTRA_TEMPLATE_ID, t.id)
        })
    }

    private fun showTemplateMenu(t: IconTemplate, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.template_rename)
            menu.add(0, 2, 1, R.string.template_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> TemplateDialogs.promptName(
                        this@MainActivity, getString(R.string.template_rename), t.name
                    ) { name ->
                        TemplateStore.rename(this@MainActivity, t.id, name)
                        loadTemplates()
                    }
                    2 -> TemplateDialogs.confirm(
                        this@MainActivity,
                        getString(R.string.template_delete),
                        getString(R.string.template_delete_confirm, t.name)
                    ) {
                        TemplateStore.delete(this@MainActivity, t.id)
                        loadTemplates()
                    }
                }
                true
            }
            show()
        }
    }

    private fun showAppsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_apps, popup.menu)
        popup.menu.findItem(R.id.action_set_template).isEnabled = selectedPackages.isNotEmpty()
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_set_template -> { createTemplate(); true }
                R.id.action_restore_all -> { restoreAll(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun createTemplate() {
        val pkgs = selectedPackages.toList()
        if (pkgs.isEmpty()) {
            Toast.makeText(this, R.string.template_no_entries, Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val configs = IconConfigStore.readConfigs(pkgs)
            val entries = pkgs.mapNotNull { p ->
                configs[p]?.let { TemplateEntry(p, it.iconPackPkg, it.drawableResName) }
            }
            withContext(Dispatchers.Main) {
                if (entries.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.template_no_entries, Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                if (entries.size > IconRequest.MAX_ITEMS) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.template_too_many, IconRequest.MAX_ITEMS),
                        Toast.LENGTH_LONG
                    ).show()
                    return@withContext
                }
                val defaultName = getString(R.string.template_default_name, (templateAdapter?.itemCount ?: 0) + 1)
                TemplateDialogs.promptName(
                    this@MainActivity, getString(R.string.menu_set_template), defaultName
                ) { name ->
                    val created = runCatching { TemplateStore.create(this@MainActivity, name, entries) }
                    if (created.isFailure) {
                        Toast.makeText(this@MainActivity, getString(R.string.template_too_many, IconRequest.MAX_ITEMS), Toast.LENGTH_LONG).show()
                        return@promptName
                    }
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.template_saved, entries.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    selectedPackages.clear()
                    filterApps(etSearch.text?.toString() ?: "")
                }
            }
        }
    }

    private fun restoreAll() {
        TemplateDialogs.confirm(
            this,
            getString(R.string.restore_confirm_title),
            getString(R.string.restore_confirm_message)
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val pkgs = CustomIconStore.customizedPackageSet().toList()
                if (pkgs.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, R.string.restore_none, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                var done = 0
                for (p in pkgs) if (IconApplier.deleteIcon(applicationContext, p)) done++
                IconRequestWriter.send(applicationContext, IconRequest.restore(pkgs))
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.restore_done, done),
                        Toast.LENGTH_LONG
                    ).show()
                    selectedPackages.clear()
                    reloadCustomizedSet()
                }
            }
        }
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
        val filtered = filterListForCurrentFilter(allApps).let { list ->
            if (query.isBlank()) list
            else list.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
        }
        appAdapter?.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvApps.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        if (filtered.isEmpty()) {
            tvEmpty.text = when (appFilter) {
                AppFilter.UNADAPTED -> getString(R.string.app_list_all_adapted)
                AppFilter.CUSTOMIZED -> getString(R.string.app_list_no_customized)
                else -> getString(R.string.app_list_empty)
            }
        }
    }

    private fun filterListForCurrentFilter(list: List<AppEntry>): List<AppEntry> =
        list.filter {
            AppFilterPredicates.matches(
                FilterableApp(it.pkg, it.isSystem),
                appFilter,
                adaptedSet,
                customizedSet
            )
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
            LogUtils.i("═══ 写入请求: ${app.label} → $drawable ═══")

            val request = IconRequest.apply(app.pkg, pack, drawable)
            val sent = IconRequestWriter.send(applicationContext, request)
            if (sent) LogUtils.i("  请求文件已写出")
            var success = sent

            // 直接落盘 .png + .cfg，列表立即感知（无需等 Launcher onResume）
            val direct = IconApplier.applyIcon(applicationContext, app.pkg, pack, drawable)
            if (direct) {
                LogUtils.i("✅ 图标已直接落盘: ${app.pkg} → $drawable")
                success = true
            } else {
                LogUtils.w("⚠️ 直接落盘失败，依赖 Launcher Hook 处理请求文件")
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@MainActivity,
                        "${app.label}\n图标: $drawable\n\n已应用，返回桌面刷新图标",
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity,
                        "写入失败\n请检查日志 Tab", Toast.LENGTH_LONG).show()
                }
                // 无论直接落盘成败都刷新列表；直接落盘成功时立即生效
                if (appFilter == AppFilter.UNADAPTED) reloadCustomizedSet(if (direct) 0 else 3)
            }
        }
    }

    private fun loadLogs() {
        tvLog.text = "正在加载日志…"
        CoroutineScope(Dispatchers.IO).launch {
            val sb = StringBuilder()

            // 桌面检测
            sb.appendLine("═══ 桌面检测 ═══")
            sb.appendLine("当前桌面: $currentLauncherPackage")
            sb.appendLine(
                if (currentLauncherPackage == MainHook.LAUNCHER_PACKAGE) "✅ 已匹配"
                else "⚠️ 未匹配，需在 LSPosed 勾选 $currentLauncherPackage"
            )

            // Launcher 进程诊断
            val hookDiag = java.io.File(IconPaths.DIAG_FILE)
            sb.appendLine()
            sb.appendLine("═══ Launcher 诊断 ═══")
            if (hookDiag.exists()) {
                val diagContent = runCatching { hookDiag.readText() }
                    .getOrElse {
                        runCatching {
                            val r = RootExec.exec("cat ${RootExec.shQuote(hookDiag.absolutePath)}")
                            r.stdout.ifBlank { r.stderr.ifBlank { "(空文件)" } }
                        }.getOrDefault("(无权限读取)")
                    }
                sb.appendLine(diagContent.takeLast(8000))
            } else {
                sb.appendLine("⚠️ 诊断文件不存在，Launcher Hook 未触发")
            }

            // Logcat
            sb.appendLine()
            sb.appendLine("═══ Logcat ═══")
            sb.appendLine(fetchLogcat())

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

    private fun isRootAvailable(): Boolean = RootExec.rootAvailable()

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
            RootExec.exec("rm -f ${RootExec.shQuote(IconPaths.DIAG_FILE)}")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "日志已清空", Toast.LENGTH_SHORT).show()
                loadLogs()
            }
        }
    }

    private fun fetchLogcat(): String {
        // 尝试多种方式读取 logcat
        val direct = RootExec.execDirect("logcat", "-d", "-s", "opIconChanger:*", "-t", "100")
        if (direct.stdout.isNotBlank()) return direct.stdout
        if (direct.exitCode == 0 && direct.stderr.isBlank()) return "(logcat 无匹配日志)"
        // 需要 root
        val root = RootExec.exec("logcat -d -s opIconChanger:* -t 100")
        return if (root.stdout.isNotBlank()) root.stdout else "(logcat 读取失败)"
    }

    data class AppEntry(val pkg: String, val label: String, val component: String, val icon: Drawable, val isSystem: Boolean)

    inner class AppAdapter(
        private var items: List<AppEntry>,
        val onClick: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {
        fun submitList(new: List<AppEntry>) { items = new; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app_entry, parent, false))
        override fun onBindViewHolder(h: VH, pos: Int) = h.bind(items[pos])
        override fun getItemCount() = items.size
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val cb = v.findViewById<CheckBox>(R.id.cbSelect)
            private val iv = v.findViewById<ImageView>(R.id.ivIcon)
            private val tvL = v.findViewById<TextView>(R.id.tvLabel)
            private val tvP = v.findViewById<TextView>(R.id.tvPackage)
            private val btn = v.findViewById<Button>(R.id.btnChange)
            fun bind(e: AppEntry) {
                tvL.text = e.label; tvP.text = e.pkg; iv.setImageDrawable(e.icon)
                btn.setOnClickListener { onClick(e) }
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = e.pkg in selectedPackages
                cb.setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPackages.add(e.pkg) else selectedPackages.remove(e.pkg)
                }
                itemView.setOnClickListener { onClick(e) }
            }
        }
    }
}
