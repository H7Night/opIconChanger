# opIconChanger — Agent Guide

## 项目概述

OxygenOS 16 (OPPO/OnePlus) 桌面图标替换工具。通过 LSPosed 模块实现。

**核心痛点**：系统内置图标编辑界面无搜索功能，从 2 万+ 图标中翻找太痛苦。

## 架构（当前）

```
opIconChanger/
├── app/
│   └── src/main/
│       ├── java/com/opiconchanger/
│       │   ├── MainHook.kt                # LSPosed 入口 — Hook Activity.onResume + MorphIconLoader
│       │   ├── ui/
│       │   │   ├── MainActivity.kt        # 主界面：应用列表 + 日志 Tab（终端式日志渲染）
│       │   │   └── IconPickerActivity.kt  # 图标选择器：顶部搜索栏 + 搜索按钮 + 自适应网格
│       │   ├── iconpack/
│       │   │   └── IconPackParser.kt      # Icon Pack 解析引擎（appfilter.xml + 模糊搜索）
│       │   ├── model/
│       │   │   ├── IconEntry.kt           # Icon Pack 条目数据模型
│       │   │   └── IconRequest.kt         # 跨进程请求协议（JSON + 严格字段校验）
│       │   └── utils/
│       │       ├── IconPaths.kt           # 全局路径/包名常量（App 与 Hook 共用）
│       │       ├── RootExec.kt            # 统一 su 执行器（超时/退出码/rootAvailable）
│       │       ├── LogRenderer.kt         # 终端风格日志渲染（行号 + 级别配色 + 关键词高亮）
│       │       ├── LogUtils.kt            # 日志工具
│       │       └── RestartUtils.kt        # 桌面重启工具
│       └── res/
│           ├── layout/                    # activity_main / activity_icon_picker / page_apps / page_log / item_*
│           ├── drawable/                  # bg_icon_cell / bg_terminal_panel / bg_search_bar / bg_dot / bg_tab_indicator
│           └── values/
│               ├── colors.xml             # Material3 青墨色系 + 终端面板配色
│               ├── themes.xml             # Theme.opIconChanger (Material3 Light NoActionBar)
│               ├── strings.xml
│               └── arrays.xml             # xposed_scope 双包名
├── scripts/                               # 构建/安装脚本（见下方）
├── analyze/                               # 反编译产物（OplusLauncher / Lawnicons 源码）
└── keystore/debug.jks                     # release 签名
```

**数据流**：
```
opIconChanger UI → JSON 请求文件(/data/oplus/uxicons/choose/opicon_request.json，App 直写)
  → Launcher.onResume Hook（校验属主 UID + 字段合法性）
  → getResourcesForApplication(iconPackPkg) → getDrawable
  → UxFileUtils.saveEditDrawableToDir() 反射调用
  → /data/oplus/uxicons/choose/<pkg>.{png,cfg}
  → EditedIconLoaderFactory 自动加载 → 桌面渲染
```

**跨进程请求通道安全设计**（2026-08 加固）：
- 请求文件首选 `/data/oplus/uxicons/choose/opicon_request.json`（UX 目录 drwxrwxrwx，App 可直接写入，**文件属主 = App UID**）
- Launcher Hook 侧 `locateOwnRequestFile()` 用 `Os.stat` 校验文件属主 UID == `com.opiconchanger` UID，**非本模块写出的请求一律忽略并删除**
- 兜底 `/data/local/tmp/opicon_request.json`（仅 root/shell 可写，普通应用无法伪造，经 su 写入）
- 所有字段在 `IconRequest.fromJson` 严格校验：`targetPkg`/`iconPackPkg` 匹配包名正则 `^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$`（拒绝 `/ \ ..` 路径穿越），`drawableResName` 仅 `[a-zA-Z0-9_]+`
- 处理串行化（单线程 Executor）+ 按 `path→mtime:length` 去重 + 文件大小上限 8KB，防止重复处理与内存攻击
- 请求文件为 App 直写后由 Launcher 删除（UX 目录可删），不再存在"文件无法删除导致每次 onResume 重复处理"的问题

**Hook 机制**（已在实机验证通过）：
- Hook `android.app.Activity.onResume`（Launcher 进程内触发），轮询读取请求 JSON 文件，有请求则处理
- Hook `MorphIconLoader.loadMorphUxIcon`，绕过系统 1×1 图标限制（`result == null` 时按 .cfg 兜底加载；.cfg 中的包名/资源名同样经过正则校验，防伪造 cfg 加载任意资源）
- 刷新：优先反射触发 `onBroadcastIntent`，失败则发送 `ICON_UPDATED` 广播兜底（Launcher 进程内发送可过签名权限）
- `encase { loadApp() }` 不可靠，直接使用 Xposed 原生 API 挂 `Activity.onResume`
- 日志：`MainHook.onInit` 中 `isDebug = BuildConfig.DEBUG`，生产构建关闭特权进程详细日志

## 反编译分析

### 已反编译
- `analyze/OplusLauncher.apk` (83MB) — OPPO 系统桌面，源码在 `analyze/launcher_decompiled/sources/`
- `analyze/Lawnicons.apk` (17MB) — Icon Pack 示例

### 未反编译
- `com.oplus.uxdesign` — OPPO 主题引擎，`LaunchIconService` 所在应用

### 图标替换的真实路径（反编译确认）

```
UxEditPanelFragment 保存按钮
  → UxFileUtils.saveEditDrawableToDir(drawable, targetPkg, iconPackPkg, drawableResName)
    → 绘制 168×168 ARGB_8888 Bitmap
    → 保存 /data/oplus/uxicons/choose/<pkg>.png
    → 写入 /data/oplus/uxicons/choose/<pkg>.cfg (Properties 格式)
       choose_drawable_res_name=<resName>
       chosse_icon_pack_name=<iconPackPkg>   ← 注意是 "chosse" 不是 "choose"

桌面渲染时：
  EditedIconLoaderFactory.assembleDrawable()
    → UxFileUtils.parseIconChooseResult(pkg) 读取 .cfg
    → 从 icon pack APK 加载 drawable → 构建 AdaptiveIconDrawable
```

**关键发现**：不需要数据库操作，不需要广播。纯文件系统。

### UxFileUtils 关键方法

```java
// 保存图标 (public static)
public static boolean saveEditDrawableToDir(
    Drawable drawable,   // 要保存的图标
    String targetPkg,    // 目标应用包名（生成文件名用）
    String iconPackPkg,  // Icon Pack 包名（写入 cfg）
    String drawableName  // drawable 资源名（写入 cfg）
)

// 路径常量
private static final String UX_CHOOSE_ICON_ROOT_PATH = "/data/oplus/uxicons/choose/";
public static final String CHOOSE_DRAWABLE_RES_NAME = "choose_drawable_res_name";
public static final String CHOOSE_ICON_PACK_NAME = "chosse_icon_pack_name";
```

### Launcher 类路径

- 桌面主 Activity: `com.android.launcher.Launcher` (extends `com.android.launcher3.BaseQuickstepLauncher`)
- 桌面包名（xposed_scope 双包名）: `com.android.launcher` / `com.oppo.launcher`

## 外部可调用接口（反编译分析）

| 接口 | 可行性 | 原因 |
|------|--------|------|
| `OplusFavoritesProvider.call()` | ✅ 读 ❌ 写 | insert/update/delete 返回 null/0 |
| `content://com.android.launcher.settings/desktopappedit` | ❌ | 需 `WRITE_SETTINGS` (signature) |
| `SAVE_CHOOSE_ICON` 广播 | ❌ | 动态注册 receiver，外部无法触发 |
| `am startservice LaunchIconService` | ❌ | 只接收 `PACKAGES` + `icon_theme`，不接收具体 drawableName |
| `UxFileUtils.saveEditDrawableToDir()` | ✅ (Launcher 进程内) | public static，纯文件系统，当前方案 |

## 编译/build

### 方式 1：直接 Gradle

```bash
./gradlew.bat assembleDebug        # debug（无 minify，快，本地开发用）
./gradlew.bat assembleRelease      # release（仅 GitHub Actions 签名构建，本地不构建）
# 输出: app/build/outputs/apk/debug/app-debug.apk
```

**Release 签名与发布（仅 GitHub Actions）**：本地开发只构建 debug，release 只在 GitHub 上通过 tag 触发构建并发布。
- 密钥 `keystore/release.jks`（别名 `opiconchanger`）与密码 `keystore/keystore.pass` 均被 gitignore，备份于 `C:\Users\user\Desktop\opIconChangerKeystore`（含 `.jks` / `.pass` / `.base64`）
- 签名凭据通过环境变量注入：`RELEASE_KEYSTORE_FILE` / `RELEASE_KEYSTORE_PASS` / `RELEASE_KEYSTORE_ALIAS`
- CI：`RELEASE_KEYSTORE_B64` / `RELEASE_KEYSTORE_PASS` / `RELEASE_KEYSTORE_ALIAS` 存于 GitHub Secrets，`release.yml` 解码后注入并创建 GitHub Release
- 本地脚本不带签名；如需本地发布请自行临时注入上述环境变量后 `./gradlew assembleRelease`

### 方式 2：scripts/ 脚本（推荐）

所有脚本自动 `cd` 到项目根，并自动探测 JAVA_HOME（`~/Abandon/Application/scoop/apps/openjdk17`）。

| 脚本 | 平台 | 作用 |
|------|------|------|
| `scripts/build.sh` / `.bat` / `.ps1` | bash / cmd / PowerShell | 构建 debug APK |
| `scripts/buildDebugApk.sh` / `.ps1` | bash / PowerShell | 构建 debug APK（调试用） |
| `scripts/buildAndInstall.sh` / `.ps1` | bash / PowerShell | 构建 debug 并 `adb install -r` 安装；签名不一致时自动卸载旧版重装 |

```bash
bash scripts/buildDebugApk.sh       # 快速构建 debug
powershell -File scripts/build.ps1  # Windows 下构建 release
```

### 方式 3：adb 安装 APK

```bash
# 连接设备（无线调试 / USB 均可）
adb devices

# 安装（-r 覆盖安装保留数据）
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 或直接安装 release
adb install -r app/build/outputs/apk/release/app-release.apk

# 卸载
adb uninstall com.opiconchanger
```

安装后在 LSPosed 管理器中启用模块，并在作用域勾选桌面（`com.android.launcher` / `com.oppo.launcher`），重启桌面生效。

### 完整验证流程（改代码后必跑）

```bash
powershell -File scripts/buildAndInstall.ps1   # 构建 debug + 安装
# 或快速迭代：
./gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.android.launcher && adb shell monkey -p com.opiconchanger 1
# 或点击桌面图标启动，观察 App 日志 Tab
```

## YukiHookAPI 1.3.2 注意事项

- 入口类必须 `object`（单例），不能是 `class`
- KSP 生成 `MainHook_YukiHookXposedInit` 作为实际 Xposed 入口
- `xposed_init` → `com.opiconchanger.MainHook_YukiHookXposedInit`
- `encase { loadApp() }` 在 `IYukiHookXposedInit` 中不可靠，推荐直接使用 Xposed 原生 API

## Android 11+ 包可见性

必须声明 `QUERY_ALL_PACKAGES` 权限 + `<queries>` 块。

## 日志

- 所有日志 tag = `opIconChanger`，`adb logcat -d -s opIconChanger:*`
- App 内「日志」Tab 展示终端风格日志：Launcher 进程诊断文件在 `/data/oplus/uxicons/choose/opicon_hook_diag.txt`（跨进程需 `su -c cat` 读取）
