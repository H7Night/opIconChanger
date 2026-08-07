package com.opiconchanger

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.opiconchanger.model.IconRequest
import com.opiconchanger.utils.IconPaths
import com.opiconchanger.utils.LogUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@InjectYukiHookWithXposed
object MainHook : IYukiHookXposedInit {

    const val LAUNCHER_PACKAGE = IconPaths.LAUNCHER_PACKAGE

    private const val MAX_REQUEST_BYTES = 8 * 1024
    private val hookDiagFile = File(IconPaths.DIAG_FILE)

    // 串行处理请求：单个 worker 线程，避免并发重复处理同一请求文件
    private val requestExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    // 已处理请求的去重：path -> "mtime:length"。Launcher 会删除已处理文件，
    // 若删除失败则据指纹跳过重复处理；用户重新应用同一图标（新 mtime）会正常再处理。
    private val processedRequests: MutableMap<String, String> = HashMap()

    private fun timeStamp() = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun diag(msg: String) {
        Log.i("opIconChanger", msg)
        try { hookDiagFile.appendText("[${timeStamp()}] $msg\n") } catch (_: Exception) {}
    }

    override fun onInit() = configs {
        // 仅 debug 构建开启 YukiHookAPI 详细日志，避免生产环境特权进程日志泄漏
        isDebug = BuildConfig.DEBUG
        @Suppress("DEPRECATION")
        debugTag = "opIconChanger"
    }

    override fun onHook() = encase {
        // 双包名覆盖 OPPO/OnePlus 桌面
        loadApp(IconPaths.LAUNCHER_PACKAGE_ALT, IconPaths.LAUNCHER_PACKAGE) {
            val pid = android.os.Process.myPid()
            diag("onHook PID=$pid PROC=${appInfo.packageName}")

            // Hook Activity.onResume 检测请求文件
            "android.app.Activity".toClass().resolve().apply {
                firstMethod { name = "onResume" }.hook {
                    after {
                        processPendingRequest(instance<Activity>())
                    }
                }
            }

            // Hook MorphIconLoader.loadMorphUxIcon — 绕过 1×1 图标限制
            try {
                var hookCallCount = 0
                "com.oplus.uxicon.ui.morphicon.MorphIconLoader".toClass().resolve().apply {
                    firstMethod { name = "loadMorphUxIcon" }.hook {
                        after {
                            hookCallCount++
                            val cn = args[1] as? android.content.ComponentName
                            val pkg = cn?.packageName ?: "null"
                            // 每 50 次打印一次状态
                            if (hookCallCount % 50 == 1) {
                                diag("🔍 MorphIconLoader called #$hookCallCount result=${result != null} pkg=$pkg")
                            }
                            if (result != null) return@after
                            if (cn == null) return@after
                            val cfgFile = java.io.File(IconPaths.UX_ICON_DIR, "$pkg.cfg")
                            if (!cfgFile.exists()) return@after
                            try {
                                val props = java.util.Properties()
                                props.load(java.io.FileInputStream(cfgFile))
                                val iconPackPkg = props.getProperty("chosse_icon_pack_name") ?: return@after
                                val drawableName = props.getProperty("choose_drawable_res_name") ?: return@after
                                // 安全校验：拒绝非法包名/资源名，防止伪造 .cfg 加载任意资源
                                if (!IconRequest.isValidPackageName(iconPackPkg)) return@after
                                if (!IconRequest.isValidResourceName(drawableName)) return@after
                                val ctx = args[0] as? android.content.Context ?: return@after
                                val res = ctx.packageManager.getResourcesForApplication(iconPackPkg)
                                val id = res.getIdentifier(drawableName, "drawable", iconPackPkg)
                                if (id == 0) return@after
                                val d = res.getDrawable(id, null) ?: return@after
                                diag("🔧 MorphIconLoader 1×1 fallback: $pkg → $drawableName")
                                result = d
                            } catch (_: Exception) {}
                        }
                    }
                }
                diag("✅ MorphIconLoader Hook 注册成功")
            } catch (e: Exception) {
                diag("❌ MorphIconLoader Hook 失败: ${e.message}")
            }
            diag("✅ Hook 注册成功")
        }
    }

    /**
     * 处理挂起请求。安全要点：
     * 1. 只信任本模块自己写出的请求文件（校验属主 UID == com.opiconchanger UID）；
     * 2. 请求文件内容不得超过上限，防止伪造超大文件拖垮 Launcher 进程；
     * 3. 处理串行化 + 去重，避免重复执行 saveEditDrawableToDir / 广播刷新。
     */
    private fun processPendingRequest(context: Activity) {
        val file = locateOwnRequestFile(context) ?: return
        if (file.length() > MAX_REQUEST_BYTES) {
            diag("⚠️ 请求文件超限(${file.length()}B)，忽略并删除")
            file.delete()
            return
        }
        diag("🎯 发现请求文件: ${file.path} (${file.length()}B)")

        requestExecutor.execute {
            try {
                val json = file.readText()
                val key = "${file.lastModified()}:${file.length()}"
                synchronized(processedRequests) {
                    if (processedRequests[file.path] == key) {
                        diag("  请求已处理过，跳过: ${file.path}")
                        return@execute
                    }
                    processedRequests[file.path] = key
                }

                val req = IconRequest.fromJson(json)
                    ?: run { diag("  ❌ JSON 解析/校验失败"); file.delete(); return@execute }

                diag("  解析: target=${req.targetPkg} iconPack=${req.iconPackPkg} drawable=${req.drawableResName}")

                // 1. 加载 IconPack drawable（包存在性已在 fromJson 校验）
                val pm = context.packageManager
                if (!isPackageInstalled(pm, req.iconPackPkg)) {
                    diag("  ❌ iconPack 未安装: ${req.iconPackPkg}")
                    file.delete(); return@execute
                }
                if (!isPackageInstalled(pm, req.targetPkg)) {
                    diag("  ❌ target 未安装: ${req.targetPkg}")
                    file.delete(); return@execute
                }
                val iconRes = pm.getResourcesForApplication(req.iconPackPkg)
                val resId = iconRes.getIdentifier(req.drawableResName, "drawable", req.iconPackPkg)
                if (resId == 0) { diag("  ❌ drawable 不存在: ${req.drawableResName}"); file.delete(); return@execute }
                val drawable = iconRes.getDrawable(resId, null)
                    ?: run { diag("  ❌ getDrawable=null"); file.delete(); return@execute }
                diag("  drawable 已加载 (${drawable.intrinsicWidth}x${drawable.intrinsicHeight})")

                // 2. 反射调用 UxFileUtils
                val uxClassName = "com.oplus.uxicon.ui.util.UxFileUtils"
                var clazz: Class<*>? = null
                for (cl in listOfNotNull(
                    context.classLoader, Activity::class.java.classLoader,
                    ClassLoader.getSystemClassLoader()
                ).distinct()) {
                    try { clazz = cl.loadClass(uxClassName); break } catch (_: Exception) {}
                }
                if (clazz == null) { diag("  ❌ UxFileUtils 类未找到"); file.delete(); return@execute }
                diag("  UxFileUtils 已加载 via ${clazz.classLoader?.javaClass?.simpleName}")

                val method = clazz.getMethod(
                    "saveEditDrawableToDir",
                    android.graphics.drawable.Drawable::class.java,
                    String::class.java, String::class.java, String::class.java
                )
                val ok = method.invoke(
                    null, drawable, req.targetPkg, req.iconPackPkg, req.drawableResName
                ) as? Boolean ?: false

                if (ok) {
                    diag("  ✅ 保存成功!")
                    val png = File(IconPaths.UX_ICON_DIR, "${req.targetPkg}.png")
                    val cfg = File(IconPaths.UX_ICON_DIR, "${req.targetPkg}.cfg")
                    diag("  PNG: ${png.exists()} ${png.length()}B")
                    diag("  CFG: ${cfg.exists()} ${if(cfg.exists())cfg.readText().take(200) else "无"}")
                    triggerIconRefresh(context, req.targetPkg)
                } else {
                    diag("  ❌ saveEditDrawableToDir 返回 false")
                }
            } catch (e: Exception) {
                diag("  ❌ 异常: ${e.javaClass.simpleName}: ${e.message}")
                Log.e("opIconChanger", "processPendingRequest 异常", e)
            } finally {
                file.delete()
            }
        }
    }

    /**
     * 只返回由本模块（com.opiconchanger）写出的请求文件，伪造文件一律忽略。
     * 首选 UX 目录请求文件（App 直接写入，属主 = App UID，须校验属主）；
     * 兜底 /data/local/tmp（仅 root/shell 可写，普通应用无法伪造，免属主校验但内容仍须通过 fromJson）。
     */
    private fun locateOwnRequestFile(context: Context): File? {
        val appUid = getAppUid(context)
        val primary = File(IconPaths.REQUEST_FILE)
        if (primary.exists()) {
            val uid = primary.ownerUid()
            if (appUid != null && uid == appUid) return primary
            diag("⚠️ 忽略伪造请求: ${primary.path} (uid=$uid, expect=$appUid)")
            primary.delete()
            return null
        }
        val fallback = File(IconPaths.REQUEST_FILE_ROOT)
        if (fallback.exists()) {
            diag("⚠️ 使用 root 通道请求文件: ${fallback.path}")
            return fallback
        }
        return null
    }

    private fun getAppUid(context: Context): Int? = try {
        context.packageManager.getApplicationInfo(IconPaths.APP_PACKAGE, 0).uid
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun File.ownerUid(): Int? = try {
        android.system.Os.stat(this.absolutePath).st_uid
    } catch (_: Exception) { null }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun triggerIconRefresh(context: Context, pkg: String) {
        val action = "com.oplus.uxdesign.action.ICON_UPDATED"
        val extra = "android.intent.extra.PACKAGES"
        try {
            val appStateCls = context.classLoader.loadClass("com.android.launcher3.LauncherAppState")
            val appState = appStateCls.getMethod("getInstance", Context::class.java)
                .invoke(null, context)
                ?: run { diag("  ⚠️ LauncherAppState 为空，跳过刷新"); return }
            val model = appStateCls.getMethod("getModel").invoke(appState)
                ?: run { diag("  ⚠️ model 为空，跳过刷新"); return }
            val intent = android.content.Intent(action)
            intent.putStringArrayListExtra(extra, arrayListOf(pkg))
            model.javaClass.getMethod("onBroadcastIntent", android.content.Intent::class.java)
                .invoke(model, intent)
            diag("  ✅ 已触发桌面刷新 (onBroadcastIntent): $pkg")
        } catch (e: Exception) {
            diag("  ❌ 反射刷新失败: ${e.message}")
            try {
                val intent = android.content.Intent(action)
                intent.putStringArrayListExtra(extra, arrayListOf(pkg))
                context.sendBroadcast(intent)
                diag("  ✅ 已发送 ICON_UPDATED 广播兜底: $pkg")
            } catch (e2: Exception) {
                diag("  ❌ 广播兜底失败: ${e2.message}")
            }
        }
    }
}
