# opIconChanger

自定义图标选择器 LSPosed 模块 — 为 OxygenOS 16 桌面提供可搜索的图标替换工具。

## 目标

- **设备**: OnePlus 15, OxygenOS 16.0.8 (CPH2747)
- **Launcher**: com.android.launcher (OplusLauncher v16.6.8)
- **Root**: Suki Ultura v4.1.3
- **LSPosed**: Vector v2.0, API 101
- **Icon Pack**: Lawnicons (app.lawnchair.lawnicons)

## 功能

| 功能 | 状态 |
|------|------|
| 拦截桌面"更改图标"入口 | ✅ |
| 自定义图标选择器（搜索框 + 网格） | ✅ |
| App 名/包名搜索 | ✅ |
| Icon Pack Drawable 名搜索 | ✅ |
| 异步加载 + LruCache 缓存 | ✅ |
| 选择后自动应用（广播回传） | ✅ (策略 A) |
| 文件保存方式回退 | 🔲 (策略 B 预留) |

## 架构

```
opIconChanger/
├── app/src/main/java/com/opiconchanger/
│   ├── MainHook.kt              # LSPosed 入口 (object 单例)
│   ├── hook/LauncherHook.kt     # Hook 核心逻辑 (LegacyCreator DSL)
│   ├── ui/IconPickerActivity.kt # 自定义图标选择器 UI
│   ├── iconpack/IconPackParser.kt # Icon Pack 解析引擎
│   └── model/IconEntry.kt       # 数据模型
├── app/src/main/res/            # 布局 & 字符串 & scope 配置
├── keystore/debug.jks           # 测试签名 (storepass: android)
├── build.bat                    # 一键构建脚本
└── analyze/                     # 反编译产物 (OplusLauncher + Lawnicons)
```

## Hook 流程

```
长按桌面图标 → 编辑 → 点击"更改图标"
    │
    └─ UxEditPanelFragment.jumpToChangeIconPanel()
         │  ← [Hook 拦截，阻止原始调用]
         │
         └─ 启动 IconPickerActivity (可搜索)
              │
              用户选择图标 → setResult + finish
              │
              └─ AppEditActivity.onActivityResult ← [Hook 拦截]
                   │
                   └─ 发送 SAVE_CHOOSE_ICON 广播 → Launcher 更新图标
```

## 构建

### 脚本一览

| 脚本 | 功能 | Windows | Linux/macOS |
|------|------|---------|-------------|
| Build Release APK | 签名 release 构建 | `.\build.ps1` | `./build.sh` |
| Build Debug APK | 快速 debug 构建（无签名） | `.\buildDebugApk.ps1` | `./buildDebugApk.sh` |
| Build & Install | 构建 release 并 `adb install` | `.\buildAndInstall.ps1` | `./buildAndInstall.sh` |

### 手动构建

```powershell
# Release（含签名）
.\gradlew assembleRelease

# Debug（快速迭代）
.\gradlew assembleDebug
```

输出:
- Release: `app/build/outputs/apk/release/app-release.apk`
- Debug:   `app/build/outputs/apk/debug/app-debug.apk`

## 安装 & 调试

```powershell
# 安装
adb install app\build\outputs\apk\release\app-release.apk

# 在 LSPosed 管理器中:
#   1. 启用 opIconChanger 模块
#   2. 作用域勾选 "系统桌面" (com.android.launcher)
#   3. 重启桌面或软重启

# 查看日志
adb logcat -s opIconChanger:V
```

## 关键技术决策

| 决策 | 选择 | 原因 |
|------|------|------|
| Hook 框架 | YukiHookAPI 1.3.2 + KavaRef 1.0.3 | 专为 LSPosed 优化，Kotlin DSL |
| 拦截点 | jumpToChangeIconPanel() | 唯一可拦截的入口，无 startActivity |
| 回传方式 | SAVE_CHOOSE_ICON 广播 | Launcher 原生机制，无需修改内部状态 |
| Icon Pack 解析 | appfilter.xml | 最精确的 Component→Drawable 映射 |
| 签名 | debug.jks (android/android) | 仅用于测试，发布时更换 |

## 注意事项

1. **字段名混淆**: `mOriginPackageName` / `mOriginComponentName` 等字段可能在 Launcher 更新后变化，报错时检查 `adb logcat -s opIconChanger`
2. **compileSdk 36**: 需要 SDK Platform 36，可通过 SDK Manager 安装
3. **广播权限**: 模块在 Launcher 进程内运行，`sendBroadcast` 无需额外权限
4. **Icon Pack 资源读取**: `createPackageContext` 在系统级进程中使用时需要 `CONTEXT_IGNORE_SECURITY` 标志
5. **Gradle 代理**: `gradle.properties` 已配置 `127.0.0.1:7890`，无需代理时删除对应行
6. **KavaRef**: 依赖已添加 (`kavaref-core:1.0.3` + `kavaref-extension:1.0.3`)，后续可将遗留 `inject` API 迁移到 `asResolver().field {}.get()` 等 KavaRef 反射 API
