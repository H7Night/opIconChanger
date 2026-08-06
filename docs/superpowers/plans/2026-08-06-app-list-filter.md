# App 列表筛选 (全部 / 系统应用 / 用户应用 / 无适配应用) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a filter dropdown to the 应用 tab that can show all / system / user / unadapted apps, where "unadapted" = package has no icon in the current icon pack's appfilter.xml AND has no manually applied icon (`/data/oplus/uxicons/choose/<pkg>.cfg` missing).

**Architecture:** Three small additions following existing patterns: (1) pure, JVM-testable filter predicate in `utils/AppFilter.kt`, (2) `IconPackParser.adaptedPackageSet()` reusing the existing appfilter cache, plus `utils/CustomIconStore.kt` that lists customized packages from the filesystem (direct read with `su` fallback), (3) `MainActivity` filter state + combined search/filter, and a filter `Spinner` row in `page_apps.xml`.

**Tech Stack:** Kotlin, AndroidX (AppCompatActivity, RecyclerView, Spinner), Material Components, kotlinx-coroutines, JUnit4 (new, test-only).

**Spec:** `docs/superpowers/specs/2026-08-06-app-list-filter-design.md`

## Global Constraints

- `minSdk 33`, `compileSdk 37`, `targetSdk 36`; Kotlin `2.1.20` (stdlib forced in `build.gradle.kts` `configurations.all`).
- Only new dependency: `testImplementation("junit:junit:4.13.2")`. No other dependency changes.
- All log calls via `LogUtils` (tag `opIconChanger`), never `android.util.Log` directly.
- All user-visible strings go in `app/src/main/res/values/strings.xml`; no hardcoded Chinese in code.
- Spinner styling follows the existing icon-pack spinner pattern (`android.R.layout.simple_spinner_item`).
- The `AppFilter` enum declaration order MUST stay `ALL, SYSTEM, USER, UNADAPTED` to match the string-array order (全部/系统应用/用户应用/无适配).
- Unit tests run with `./gradlew.bat testDebugUnitTest`. Pure logic files (`AppFilter.kt`, the parse helper in `CustomIconStore.kt`) must NOT reference Android framework classes so they stay JVM-testable.
- On-device verification uses `powershell -File scripts/buildAndInstall.ps1` from the project root.

---

### Task 1: Pure filter predicate + JUnit test harness

**Files:**
- Modify: `app/build.gradle.kts` (dependencies block, after the Material line)
- Create: `app/src/main/java/com/opiconchanger/utils/AppFilter.kt`
- Create: `app/src/test/java/com/opiconchanger/utils/AppFilterPredicatesTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class AppFilter { ALL, SYSTEM, USER, UNADAPTED }`; `data class FilterableApp(val pkg: String, val isSystem: Boolean)`; `object AppFilterPredicates { fun matches(app: FilterableApp, filter: AppFilter, adaptedPackages: Set<String>, customizedPackages: Set<String>): Boolean }`. Task 3 uses these.

- [ ] **Step 1: Add JUnit test dependency**

In `app/build.gradle.kts`, inside the `dependencies { }` block (after the Material Components line), add:

```kotlin
    // Unit tests (pure JVM logic only)
    testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/opiconchanger/utils/AppFilterPredicatesTest.kt`:

```kotlin
package com.opiconchanger.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterPredicatesTest {
    private val userApp = FilterableApp(pkg = "com.example.app", isSystem = false)
    private val systemApp = FilterableApp(pkg = "com.android.settings", isSystem = true)

    @Test
    fun allAlwaysMatches() {
        assertTrue(AppFilterPredicates.matches(userApp, AppFilter.ALL, emptySet(), emptySet()))
        assertTrue(AppFilterPredicates.matches(systemApp, AppFilter.ALL, emptySet(), emptySet()))
    }

    @Test
    fun systemMatchesOnlySystemApps() {
        assertTrue(AppFilterPredicates.matches(systemApp, AppFilter.SYSTEM, emptySet(), emptySet()))
        assertFalse(AppFilterPredicates.matches(userApp, AppFilter.SYSTEM, emptySet(), emptySet()))
    }

    @Test
    fun userMatchesOnlyNonSystemApps() {
        assertTrue(AppFilterPredicates.matches(userApp, AppFilter.USER, emptySet(), emptySet()))
        assertFalse(AppFilterPredicates.matches(systemApp, AppFilter.USER, emptySet(), emptySet()))
    }

    @Test
    fun unadaptedExcludesAdaptedPackage() {
        val adapted = setOf("com.example.app")
        assertFalse(AppFilterPredicates.matches(userApp, AppFilter.UNADAPTED, adapted, emptySet()))
    }

    @Test
    fun unadaptedExcludesCustomizedPackage() {
        val customized = setOf("com.example.app")
        assertFalse(AppFilterPredicates.matches(userApp, AppFilter.UNADAPTED, emptySet(), customized))
    }

    @Test
    fun unadaptedMatchesWhenNeitherAdaptedNorCustomized() {
        assertTrue(AppFilterPredicates.matches(userApp, AppFilter.UNADAPTED, emptySet(), emptySet()))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.opiconchanger.utils.AppFilterPredicatesTest"`
Expected: FAIL with "unresolved reference: AppFilter" (nothing implemented yet).

- [ ] **Step 4: Write minimal implementation**

Create `app/src/main/java/com/opiconchanger/utils/AppFilter.kt` (pure Kotlin, NO Android imports):

```kotlin
package com.opiconchanger.utils

enum class AppFilter { ALL, SYSTEM, USER, UNADAPTED }

data class FilterableApp(val pkg: String, val isSystem: Boolean)

object AppFilterPredicates {
    fun matches(
        app: FilterableApp,
        filter: AppFilter,
        adaptedPackages: Set<String>,
        customizedPackages: Set<String>
    ): Boolean = when (filter) {
        AppFilter.ALL -> true
        AppFilter.SYSTEM -> app.isSystem
        AppFilter.USER -> !app.isSystem
        AppFilter.UNADAPTED ->
            app.pkg !in adaptedPackages && app.pkg !in customizedPackages
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.opiconchanger.utils.AppFilterPredicatesTest"`
Expected: BUILD SUCCESSFUL, 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/opiconchanger/utils/AppFilter.kt app/src/test/java/com/opiconchanger/utils/AppFilterPredicatesTest.kt
git commit -m "feat: add pure app filter predicate (all/system/user/unadapted) with tests"
```

---

### Task 2: Adapted package set + customized-icon detection

**Files:**
- Modify: `app/src/main/java/com/opiconchanger/iconpack/IconPackParser.kt` (add method after `loadIconPack`, ~line 88)
- Create: `app/src/main/java/com/opiconchanger/utils/CustomIconStore.kt`
- Create: `app/src/test/java/com/opiconchanger/utils/CustomIconStoreTest.kt`

**Interfaces:**
- Consumes: `AppFilter`/`FilterableApp` not needed here; reuses `IconPackParser.loadIconPack(pack): List<AppFilterEntry>` (suspend, cached in `iconPackCache`); `LogUtils.w`.
- Produces: `suspend fun IconPackParser.adaptedPackageSet(iconPackPackage: String): Set<String>`; `object CustomIconStore { suspend fun customizedPackageSet(): Set<String> }`; `internal fun parseCustomizedPackages(lines: String): Set<String>`. Task 3 consumes all three.

- [ ] **Step 1: Write the failing test for the parser**

Create `app/src/test/java/com/opiconchanger/utils/CustomIconStoreTest.kt`:

```kotlin
package com.opiconchanger.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIconStoreTest {

    @Test
    fun parseExtractsPackageNamesFromLsOutput() {
        val input = "/data/oplus/uxicons/choose/com.foo.cfg\n/data/oplus/uxicons/choose/com.bar.cfg\n"
        assertEquals(setOf("com.foo", "com.bar"), parseCustomizedPackages(input))
    }

    @Test
    fun parseHandlesBlankAndEmptyInput() {
        assertEquals(emptySet<String>(), parseCustomizedPackages(""))
        assertEquals(setOf("com.foo"), parseCustomizedPackages("com.foo.cfg"))
    }

    @Test
    fun parseKeepsDotsInsidePackageName() {
        val result = parseCustomizedPackages("/data/oplus/uxicons/choose/com.android.settings.cfg\n")
        assertTrue("com.android.settings" in result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.opiconchanger.utils.CustomIconStoreTest"`
Expected: FAIL with "unresolved reference: parseCustomizedPackages".

- [ ] **Step 3: Implement `IconPackParser.adaptedPackageSet`**

In `app/src/main/java/com/opiconchanger/iconpack/IconPackParser.kt`, add this method directly after the `loadIconPack` function (it reuses the parser's cache, so it is cheap on repeat calls):

```kotlin
    /** 当前 icon pack 适配（appfilter 中出现）的包名集合 */
    suspend fun adaptedPackageSet(iconPackPackage: String): Set<String> =
        iconPackCache[iconPackPackage]?.map { it.packageName }?.toSet()
            ?: loadIconPack(iconPackPackage).map { it.packageName }.toSet()
```

- [ ] **Step 4: Implement `CustomIconStore`**

Create `app/src/main/java/com/opiconchanger/utils/CustomIconStore.kt`:

```kotlin
package com.opiconchanger.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CustomIconStore {
    private const val UX_ICON_DIR = "/data/oplus/uxicons/choose"

    /** 已手动更换图标的包名集合（该目录下存在 <pkg>.cfg）。 */
    suspend fun customizedPackageSet(): Set<String> = withContext(Dispatchers.IO) {
        val files = runCatching { File(UX_ICON_DIR).listFiles() }.getOrNull()
        if (files != null) {
            files.filter { it.isFile && it.name.endsWith(".cfg") }
                .map { it.name.removeSuffix(".cfg") }
                .toSet()
        } else {
            suListCfgPackages()
        }
    }

    private fun suListCfgPackages(): Set<String> = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -1 $UX_ICON_DIR/*.cfg 2>/dev/null"))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        parseCustomizedPackages(out)
    } catch (e: Exception) {
        LogUtils.w("CustomIconStore su ls 失败: ${e.message}")
        emptySet()
    }
}

internal fun parseCustomizedPackages(lines: String): Set<String> =
    lines.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.substringAfterLast('/').removeSuffix(".cfg") }
        .toSet()
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (Task 1's 6 + Task 2's 3).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/opiconchanger/iconpack/IconPackParser.kt app/src/main/java/com/opiconchanger/utils/CustomIconStore.kt app/src/test/java/com/opiconchanger/utils/CustomIconStoreTest.kt
git commit -m "feat: expose icon pack adapted package set and customized icon detection"
```

---

### Task 3: App list UI integration (filter dropdown + combined filtering)

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (add 5 strings + 1 string-array)
- Modify: `app/src/main/res/layout/page_apps.xml` (insert filter row between icon-pack row and search box)
- Modify: `app/src/main/java/com/opiconchanger/ui/MainActivity.kt` (AppEntry.isSystem, queryInstalledApps, filter state, filterApps, spinner wiring, reload helpers, applyIconNew refresh)

**Interfaces:**
- Consumes: `AppFilter`, `FilterableApp`, `AppFilterPredicates.matches` (Task 1); `IconPackParser.adaptedPackageSet`, `CustomIconStore.customizedPackageSet` (Task 2).
- Produces: the working 应用-tab filter UI. No new public API.

- [ ] **Step 1: Add strings**

In `app/src/main/res/values/strings.xml`, add inside `<resources>`:

```xml
    <!-- App 列表筛选 -->
    <string name="app_filter_all">全部</string>
    <string name="app_filter_system">系统应用</string>
    <string name="app_filter_user">用户应用</string>
    <string name="app_filter_unadapted">无适配应用</string>
    <string name="app_list_all_adapted">当前图标包已适配全部应用</string>

    <string-array name="app_filter_options">
        <item>@string/app_filter_all</item>
        <item>@string/app_filter_system</item>
        <item>@string/app_filter_user</item>
        <item>@string/app_filter_unadapted</item>
    </string-array>
```

- [ ] **Step 2: Add the filter dropdown row to the layout**

In `app/src/main/res/layout/page_apps.xml`, insert this block between the icon-pack row (ends at `</LinearLayout>` after `tvIconCount`, ~line 39) and the `<EditText android:id="@+id/etSearch" .../>`:

```xml
    <!-- 筛选：全部 / 系统应用 / 用户应用 / 无适配 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:paddingTop="0dp"
        android:paddingBottom="4dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="筛选"
            android:textAppearance="?android:attr/textAppearanceMedium"
            android:layout_marginEnd="8dp" />

        <Spinner
            android:id="@+id/spinnerAppFilter"
            android:layout_width="0dp"
            android:layout_height="40dp"
            android:layout_weight="1" />
    </LinearLayout>
```

- [ ] **Step 3: Update `MainActivity.kt` — imports and `AppEntry`**

Add import: `import android.content.pm.ApplicationInfo`.

Change the `AppEntry` data class (bottom of file) to add `isSystem`:

```kotlin
    data class AppEntry(val pkg: String, val label: String, val component: String, val icon: Drawable, val isSystem: Boolean)
```

Add filter state fields near the other `private var` fields (after `private var currentLauncherPackage: String = MainHook.LAUNCHER_PACKAGE`):

```kotlin
    private var appFilter: AppFilter = AppFilter.ALL
    private var adaptedSet: Set<String> = emptySet()
    private var customizedSet: Set<String> = emptySet()
    private lateinit var spinnerAppFilter: Spinner
```

- [ ] **Step 4: Compute `isSystem` in `queryInstalledApps`**

In `queryInstalledApps()` (MainActivity.kt ~line 238), replace the `AppEntry(...)` construction:

```kotlin
                val ai = pm.getApplicationInfo(pkg, 0)
                val isSystem = ai.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                AppEntry(pkg, pm.getApplicationLabel(ai).toString(), ri.activityInfo.name, pm.getApplicationIcon(ai), isSystem)
```

- [ ] **Step 5: Rewrite `filterApps` to combine search AND filter**

Replace `filterApps(query: String)` (MainActivity.kt ~line 252) with:

```kotlin
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
```

- [ ] **Step 6: Wire the filter spinner in `onCreate`**

Inside `onCreate`, after the existing `spinnerIconPack = pageApps.findViewById(R.id.spinnerIconPack)` block, add:

```kotlin
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
```

- [ ] **Step 7: Add reload helpers + icon-pack reload hook**

Add these private methods near `filterApps`:

```kotlin
    private fun reloadAdaptedSet(pack: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val parser = iconPackParser ?: IconPackParser(applicationContext)
            adaptedSet = parser.adaptedPackageSet(pack)
            withContext(Dispatchers.Main) { filterApps(etSearch.text?.toString() ?: "") }
        }
    }

    private fun reloadCustomizedSet() {
        CoroutineScope(Dispatchers.IO).launch {
            customizedSet = CustomIconStore.customizedPackageSet()
            withContext(Dispatchers.Main) { filterApps(etSearch.text?.toString() ?: "") }
        }
    }
```

In `loadIconPacks()`, inside `onItemSelected` (currently `prefs.edit()... ; updateIconCount(iconPacks[pos])`), add the adapted-set reload:

```kotlin
                    override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        prefs.edit().putString("selected_icon_pack", iconPacks[pos]).apply()
                        updateIconCount(iconPacks[pos])
                        reloadAdaptedSet(iconPacks[pos])
                    }
```

- [ ] **Step 8: Refresh customized set after a successful apply**

In `applyIconNew`, inside the final `withContext(Dispatchers.Main) { ... }` block, after the success/failure Toast branch, add:

```kotlin
                if (success && appFilter == AppFilter.UNADAPTED) reloadCustomizedSet()
```

- [ ] **Step 9: Compile check**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/layout/page_apps.xml app/src/main/java/com/opiconchanger/ui/MainActivity.kt
git commit -m "feat: app list filter dropdown (all/system/user/unadapted) with search AND filter"
```

---

### Task 4: Build, install and on-device verification

**Files:** none (verification only).

**Interfaces:** Consumes the fully wired feature from Task 3.

- [ ] **Step 1: Build and install release**

Run: `powershell -File scripts/buildAndInstall.ps1`
Expected: BUILD SUCCESSFUL, `Performing Streamed Install` / `Success` (script auto-handles debug→release signature conflicts).

- [ ] **Step 2: Wake device if locked**

If the device is PIN-locked, ask the user to unlock it, then:
```bash
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -n com.opiconchanger/.ui.MainActivity
```

- [ ] **Step 3: Verify filter behavior manually**

Checklist against Lawnicons (`app.lawnchair.lawnicons`):
1. 「筛选」dropdown shows 全部 / 系统应用 / 用户应用 / 无适配, default 全部 showing every app.
2. 系统应用: a known system app (e.g. `com.android.settings`) present; a known user-installed app absent.
3. 用户应用: the inverse of (2).
4. 无适配: a known-adapted app (Lawnicons covers many popular apps) absent; a known-unadapted app present.
5. Manually apply an icon to an unadapted app, return to 应用 tab, re-select 无适配 → that app is now absent (it has a `.cfg`).
6. Search text + 无适配 combine (e.g. search a package prefix while 无适配 selected).
7. With 无适配 selected and zero results, text shows 「当前图标包已适配全部应用」.

- [ ] **Step 4: Check crash log**

Run: `adb shell logcat -d -s AndroidRuntime:E`
Expected: no FATAL EXCEPTION entries for `com.opiconchanger`.

- [ ] **Step 5: Commit any verification-driven fixes (if needed)**

If the checklist surfaced a bug, fix it in the relevant Task 3 file and commit with a message describing the fix. Otherwise no commit needed.

---

## Self-Review Notes

- **Spec coverage:** 无适配定义 (package-level + customized exclusion) → Tasks 1+2+3; system/user flags → Task 3 step 3-4; filter dropdown UI → Task 3 step 2; search AND filter → Task 3 step 5; adapted set reload on icon-pack switch → Task 3 step 7; customized reload on 无适配 select + after apply → Task 3 steps 6-8; empty state → Task 3 step 5 + Task 4 checklist 7; su fallback + `.cfg`-only matching → Task 2 step 4.
- **Type consistency:** `adaptedPackageSet` / `customizedPackageSet` / `parseCustomizedPackages` / `AppFilterPredicates.matches` names are identical across the tasks that define and consume them. `AppFilter` enum order is pinned in Global Constraints and used positionally in Task 3 step 6.
- **No placeholders:** every step contains concrete code or an exact command.
