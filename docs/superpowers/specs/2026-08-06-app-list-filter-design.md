# App 列表筛选 (全部 / 系统应用 / 用户应用 / 无适配应用) — 设计文档

日期: 2026-08-06
分支: `main`（基于当前 UI refactor 之后的状态）

## 目标

在「应用」Tab 的应用列表上增加筛选能力，帮助用户快速找到需要替换图标的应用，尤其是**无适配应用**：当前 icon pack 没有为其提供图标、且用户尚未手动换过图标、桌面仍显示原始图标的应用。

## 决策记录

| 决策点 | 结论 |
|--------|------|
| 无适配判定 | **按包名匹配**：包名不在当前 icon pack 的 `appfilter.xml` 包名集合中 |
| 已手动换图标的处理 | **不算无适配**：`/data/oplus/uxicons/choose/<pkg>.cfg` 存在即视为已自定义，从无适配中排除 |
| 筛选 UI | **下拉筛选框**（Spinner），放在图标包下拉下方一行 |
| 选项 | 全部(默认) / 系统应用 / 用户应用 / 无适配，共 4 项，无计数 |
| 与搜索的关系 | 搜索文本 与 筛选条件 为 AND 组合 |
| 系统应用判定 | `ApplicationInfo.FLAG_SYSTEM` 或 `FLAG_UPDATED_SYSTEM_APP` |
| 用户应用判定 | 非系统应用 |

## 无适配应用的完整定义

```
无适配应用 := 包名 ∉ iconPack 的 appfilter 包名集合
             AND /data/oplus/uxicons/choose/<pkg>.cfg 不存在
```

- 若 icon pack 无 `appfilter.xml`（或加载失败）→ 包名集合为空 → 所有应用都判为“无包适配”，再叠加已自定义排除。
- `opicon_hook_diag.txt` 等非 `.cfg` 文件不计入已自定义集合。

## 架构与改动点

全部遵循现有代码模式，改动小且边界清晰。

### 1. `AppEntry` 增加系统标志 (`MainActivity.kt`)

- `data class AppEntry(..., val isSystem: Boolean)`。
- `queryInstalledApps()` 中从 `pm.getApplicationInfo(pkg, 0)` 计算：
  `ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0`。

### 2. `IconPackParser` 增加适配包名集合

- `fun adaptedPackageSet(pack: String): Set<String>`
- 实现：`loadIconPack(pack).map { it.packageName }.toSet()`（复用已有缓存，首次后无解析开销）。

### 3. 已自定义图标集合 (`utils/CustomIconStore.kt`，新增)

- `suspend fun customizedPackageSet(): Set<String>`：列出 `/data/oplus/uxicons/choose/*.cfg`，对每个文件名做 `.removeSuffix(".cfg")`（包名含点，不能按通用扩展名剥离）得包名集合。
- 读取方式沿用日志页模式：先直接 `File(dir).listFiles()`，失败则 `su -c "ls .../choose/*.cfg"` 兜底；两者都失败则返回空集合并记录一条日志（安全方向：宁可多报无适配，不隐藏应用）。

### 4. 筛选状态与组合过滤 (`MainActivity.kt`)

- `enum class AppFilter { ALL, SYSTEM, USER, UNADAPTED }`，字段 `appFilter: AppFilter = ALL`。
- `filterApps(query)` 改为先按 `appFilter` 过滤，再叠加搜索文本（AND）：
  ```
  ALL      → base
  SYSTEM   → base.filter { it.isSystem }
  USER     → base.filter { !it.isSystem }
  UNADAPTED→ base.filter { it.pkg !in adaptedSet && it.pkg !in customizedSet }
  ```
- `adaptedSet` 缓存，随图标包 spinner 变化异步重载并重新过滤。
- `customizedSet` 缓存，在选中「无适配」时异步重载并重新过滤；`applyIconNew` 成功后也触发一次重载。

### 5. UI (`page_apps.xml`)

- 图标包行下方新增一行：`筛选` label + 4 项 `Spinner`，选项来自 `strings.xml` 中的 `string-array app_filter_options`（全部/系统应用/用户应用/无适配）。样式与现有图标包 spinner 一致。
- 搜索框保持在筛选行下方。

## 数据流

```
图标包 spinner 变化 → 异步加载 adaptedSet(新 pack) → 重新 filterApps
筛选 spinner 变化   → 若为无适配：异步加载 customizedSet → 重新 filterApps
搜索输入变化        → filterApps（搜索 AND 筛选）
filterApps → appAdapter.submitList(filtered) → tvEmpty / RecyclerView 切换
```

## 空状态

- 筛选为「无适配」且结果为空 → 显示「当前图标包已适配全部应用」（新增字符串）。
- 其他筛选结果为空 → 沿用现有「未找到匹配应用」。

## 边界与错误处理

- icon pack 加载失败/无 appfilter → 包名集合为空（前述语义）。
- 无 root 且无法直接读取该目录 → `customizedSet` 为空，仅记日志；无适配可能多报，但不会漏报需处理的应用。
- `.cfg` 文件名 = 包名，仅匹配 `.cfg` 后缀。
- 桌面写 `.cfg` 是异步的：应用后不能立即认为文件已存在，故每次进入无适配筛选都重载 `customizedSet`。

## 测试与验证（项目无测试框架，走实机）

1. `powershell -File scripts/buildAndInstall.ps1` 构建安装。
2. 选 Lawnicons：
   - 已知被 Lawnicons 适配的应用（如部分 Google/知名应用）不出现在「无适配」。
   - 已知未被适配的应用出现在「无适配」。
   - 对某无适配应用手动换图标后，重新进入「无适配」，该应用消失。
3. 「系统应用 / 用户应用」分别核对已知系统应用（如 com.android.settings）与用户应用。
4. 搜索文本 + 筛选组合生效。
5. 「无适配」为空时显示「当前图标包已适配全部应用」。
