# opIconChanger — Agent Guide

## 项目概述

OxygenOS 16 (OPPO/OnePlus) 桌面图标替换工具。通过 LSPosed 模块实现。

**核心痛点**：系统内置图标编辑界面无搜索功能，从 2 万+ 图标中翻找太痛苦。

## 架构（当前）

```
opIconChanger/
├── MainHook.kt              # LSPosed 入口 — Hook Activity.onResume
├── ui/
│   ├── MainActivity.kt      # 主界面：图标包选择 + 应用列表 + 日志
│   └── IconPickerActivity.kt # 图标选择器（可搜索网格）
├── iconpack/
│   └── IconPackParser.kt    # Icon Pack 解析引擎
└── model/
    ├── IconEntry.kt          # Icon Pack 条目数据模型
    └── IconRequest.kt        # 跨进程请求协议（JSON 文件）
```

**数据流**：
```
opIconChanger UI → JSON 文件 → Launcher.onResume Hook
  → getResourcesForApplication(iconPackPkg) → getDrawable
  → UxFileUtils.saveEditDrawableToDir() 反射调用
  → /data/oplus/uxicons/choose/<pkg>.{png,cfg}
  → EditedIconLoaderFactory 自动加载 → 桌面渲染
```

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
- 桌面包名: `com.android.launcher` (xposed_scope 中使用)

## 尝试过的方案

### ❌ 方案 1：am startservice LaunchIconService
- 通过 `su -c am startservice com.oplus.uxdesign/.icon.service.LaunchIconService` 触发图标下载
- **失败原因**：LaunchIconService 只接收 `PACKAGES` + `icon_theme`，不接收具体 drawableName。下载的是默认图标，不是用户选择的图标。

### ❌ 方案 2：发送 SAVE_CHOOSE_ICON 广播
- 发送 `com.oplus.uxdesign.action.SAVE_CHOOSE_ICON` 广播
- **失败原因**：BroadcastReceiver 是动态注册的（仅在 AppEditActivity 打开时），外部无法触发。

### ❌ 方案 3：am start AppEditActivity + 手动粘贴
- 唤起系统编辑界面，用户手动粘贴图标名搜索
- **失败原因**：仍需手动操作，且 AppEditActivity 启动不稳定。

### ❌ 方案 4：LSPosed + YukiHookAPI encase/loadApp
- 使用 `encase { loadApp(LAUNCHER_PACKAGE) { "com.android.launcher.Launcher".hook { ... } } }`
- **失败原因**：`encase { loadApp() }` 在 `IYukiHookXposedInit` 模式中 PackageParam 上下文未正确传递，Hook 未注册。

### ⚠️ 方案 5（当前）：LSPosed + Xposed 原生 API Hook Activity.onResume
- 直接 `XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", ...)` 
- 在回调中用 `activity.packageName == "com.android.launcher"` 过滤
- 然后反射调用 `UxFileUtils.saveEditDrawableToDir()`
- **当前状态**：编译通过，但实机测试 Hook 未触发，日志无输出
- **可能原因**：LSPosed 模块未激活到正确的 Launcher 包名作用域

### ⚠️ 待验证：桌面包名不匹配
- AndroidManifest 中 `xposed_scope` 配置为 `com.android.launcher`
- OPPO 设备桌面可能使用 `com.oplus.launcher` 等不同包名
- `detectLauncher()` 已实现运行时检测包名，但 xposed_scope 需要编译时确定
- **解决方案**：在 LSPosed 管理器中手动勾选正确的桌面包名

## 外部可调用接口（反编译分析）

| 接口 | 可行性 | 原因 |
|------|--------|------|
| `OplusFavoritesProvider.call()` | ✅ 读 ❌ 写 | insert/update/delete 返回 null/0 |
| `content://com.android.launcher.settings/desktopappedit` | ❌ | 需 `WRITE_SETTINGS` (signature) |
| `SAVE_CHOOSE_ICON` 广播 | ❌ | 动态注册 receiver |
| `UxFileUtils.saveEditDrawableToDir()` | ✅ (Launcher 进程内) | public static，纯文件系统 |

## 编译/build

```bash
./gradlew.bat assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk
```

## YukiHookAPI 1.3.2 注意事项

- 入口类必须 `object`（单例），不能是 `class`
- KSP 生成 `MainHook_YukiHookXposedInit` 作为实际 Xposed 入口
- `xposed_init` → `com.opiconchanger.MainHook_YukiHookXposedInit`
- `encase { loadApp() }` 在 `IYukiHookXposedInit` 中不可靠，推荐直接使用 Xposed 原生 API

## Android 11+ 包可见性

必须声明 `QUERY_ALL_PACKAGES` 权限 + `<queries>` 块。

## 日志

所有日志 tag = `opIconChanger`。App 内日志 Tab 通过 `su -c logcat -d -s opIconChanger:*` 读取。
