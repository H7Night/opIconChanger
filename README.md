# opIconChanger

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

为 OxygenOS 16（OPPO / OnePlus）系统桌面提供**可搜索的图标替换工具**，基于 LSPosed 模块实现。

系统内置的图标编辑界面没有搜索功能，从 2 万+ 图标中翻找太痛苦。本模块直接用 LSPosed 注入桌面进程，在 Launcher 进程内反射调用系统自身的图标保存逻辑，实现「搜索 → 挑选 → 立即生效」的完整闭环。

## 功能特性

- 🔍 **模糊搜索图标**：支持按图标名 / 应用名 / 包名搜索，忽略 `_ - .` 等分隔符，支持多关键词（如输入 `tele gram`）
- 📱 **图标选择器**：顶部搜索栏 + 搜索按钮、自适应网格（手机上约 5 列）、底部实时结果计数
- ⚡ **立即生效**：选定图标后无需重启桌面，通过 Launcher 进程内刷新 + 广播兜底即时更新
- 📋 **终端风格日志页**：深色面板 + 行号 gutter + 日志级别配色 + 关键词过滤高亮 + Root 状态指示灯
- 🎨 **Material 3 视觉**：青墨色系主题，轻量引入 Material Components

## 原理

利用反编译分析发现的系统原生接口，纯文件系统操作，无需数据库、无需广播注册：

```
opIconChanger UI → JSON 请求文件 → Launcher.onResume Hook
  → 解析请求 → 从 Icon Pack 加载 Drawable
  → 反射调用 UxFileUtils.saveEditDrawableToDir()
  → 写入 /data/oplus/uxicons/choose/<pkg>.{png,cfg}
  → EditedIconLoaderFactory 自动加载 → 桌面渲染
```

## 目录结构

```
opIconChanger/
├── app/
│   └── src/main/
│       ├── java/com/opiconchanger/
│       │   ├── MainHook.kt                # LSPosed 入口 — Hook Activity.onResume + MorphIconLoader
│       │   ├── ui/
│       │   │   ├── MainActivity.kt        # 主界面：应用列表 + 日志 Tab
│       │   │   └── IconPickerActivity.kt  # 图标选择器
│       │   ├── iconpack/
│       │   │   └── IconPackParser.kt      # Icon Pack 解析引擎（appfilter.xml + 模糊搜索）
│       │   ├── model/                     # 数据模型 + 跨进程请求协议
│       │   └── utils/
│       │       ├── LogRenderer.kt         # 终端风格日志渲染
│       │       ├── LogUtils.kt            # 日志工具
│       │       └── RestartUtils.kt        # 桌面重启工具
│       └── res/
│           ├── layout/                    # 各界面布局
│           ├── drawable/                  # 图标格 / 终端面板 / 搜索栏背景等
│           └── values/                    # colors / themes / strings / arrays(xposed_scope)
├── scripts/                               # 构建 / 安装脚本
├── analyze/                               # 反编译产物（OplusLauncher / Lawnicons 源码）
└── keystore/debug.jks                     # release 签名
```

## 构建

### 方式 1：直接 Gradle

```bash
./gradlew.bat assembleDebug        # debug（无 minify，快）
./gradlew.bat assembleRelease      # release（用 keystore/debug.jks 签名）
# 输出: app/build/outputs/apk/debug/app-debug.apk 或 .../release/app-release.apk
```

### 方式 2：scripts/ 脚本（推荐）

所有脚本自动 `cd` 到项目根，并自动探测 JAVA_HOME。

| 脚本 | 平台 | 作用 |
|------|------|------|
| `scripts/build.sh` / `.bat` / `.ps1` | bash / cmd / PowerShell | 构建 release APK |
| `scripts/buildDebugApk.sh` / `.ps1` | bash / PowerShell | 构建 debug APK（调试用） |
| `scripts/buildAndInstall.sh` / `.ps1` | bash / PowerShell | 构建 release 并 `adb install -r` 安装 |

```bash
bash scripts/buildDebugApk.sh       # 快速构建 debug
powershell -File scripts/build.ps1  # Windows 下构建 release
```

## 安装

```bash
adb devices                          # 连接设备（USB / 无线调试）

# 安装（-r 覆盖安装保留数据）
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 卸载
adb uninstall com.opiconchanger
```

安装后启用模块：

1. 打开 LSPosed 管理器，启用 `opIconChanger` 模块
2. 作用域勾选系统桌面：`com.android.launcher` / `com.oppo.launcher`
3. 重启桌面（或点击 App 内「重启桌面」按钮）生效

## 使用

1. 打开 App，选择图标包（如 Lawnicons `app.lawnchair.lawnicons`）
2. 在应用列表中选择要更换图标的应用，点击「更换图标」
3. 在搜索栏输入图标名（如 `telegram`），点击搜索
4. 点选目标图标，返回桌面即可看到图标已更新

## 技术要点

- **Hook 框架**：YukiHookAPI 1.3.2 + KavaRef，入口为 `object MainHook`（KSP 生成实际 Xposed 入口）
- **图标保存**：反射调用 `UxFileUtils.saveEditDrawableToDir()`（`com.oplus.uxicon.ui.util`），纯文件系统写入 `/data/oplus/uxicons/choose/`
- **配置字段**：`chosse_icon_pack_name`（注意拼写）与 `choose_drawable_res_name`
- **刷新机制**：先尝试 Launcher 进程内反射触发 `onBroadcastIntent`，失败则发送 `ICON_UPDATED` 广播兜底（Launcher 进程内发送可满足签名权限校验）
- **Material 3**：仅引入 `com.google.android.material`，主题 `Theme.opIconChanger`（Light NoActionBar），保持模块体积精简

## 环境要求

- Android 13+（minSdk 33）
- Root + LSPosed（API 101+）
- OxygenOS 16 桌面（`com.android.launcher`）
- 任意 Icon Pack（含 `appfilter.xml`）

## 免责声明

本工具通过系统原生接口修改桌面图标，仅限个人设备调试使用。请勿用于规避任何付费机制或恶意用途。
