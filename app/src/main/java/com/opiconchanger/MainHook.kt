package com.opiconchanger

import android.app.Activity
import android.content.Context
import android.util.Log
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.opiconchanger.model.IconRequest
import com.opiconchanger.utils.LogUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@InjectYukiHookWithXposed
object MainHook : IYukiHookXposedInit {

    const val LAUNCHER_PACKAGE = "com.android.launcher"

    private const val UX_ICON_DIR = "/data/oplus/uxicons/choose"
    private val hookDiagFile = File(UX_ICON_DIR, "opicon_hook_diag.txt")

    private val requestPaths = arrayOf(
        IconRequest.REQUEST_FILE_PRIMARY,
        IconRequest.REQUEST_FILE_FALLBACK,
        "/data/data/com.opiconchanger/files/opicon_request.json"
    )

    private fun timeStamp() = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun diag(msg: String) {
        Log.i("opIconChanger", msg)
        try { hookDiagFile.appendText("[${timeStamp()}] $msg\n") } catch (_: Exception) {}
    }

    override fun onInit() = configs {
        isDebug = true
        @Suppress("DEPRECATION")
        debugTag = "opIconChanger"
    }

    override fun onHook() = encase {
        // 双包名覆盖 OPPO/OnePlus 桌面
        loadApp("com.oppo.launcher", "com.android.launcher") {
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
                            val cfgFile = java.io.File("/data/oplus/uxicons/choose/$pkg.cfg")
                            if (!cfgFile.exists()) return@after
                            try {
                                val props = java.util.Properties()
                                props.load(java.io.FileInputStream(cfgFile))
                                val iconPackPkg = props.getProperty("chosse_icon_pack_name") ?: return@after
                                val drawableName = props.getProperty("choose_drawable_res_name") ?: return@after
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

    private fun processPendingRequest(context: Activity) {
        var found: File? = null
        for (path in requestPaths) {
            val f = File(path)
            if (f.exists()) { found = f; break }
        }
        if (found == null) return
        val file = found
        diag("🎯 发现请求文件: ${file.path} (${file.length()}B)")

        Thread {
            try {
                val json = file.readText()
                val req = IconRequest.fromJson(json)
                    ?: run { diag("  ❌ JSON 解析失败"); file.delete(); return@Thread }

                diag("  解析: target=${req.targetPkg} iconPack=${req.iconPackPkg} drawable=${req.drawableResName}")

                // 1. 加载 IconPack drawable
                val pm = context.packageManager
                val iconRes = pm.getResourcesForApplication(req.iconPackPkg)
                val resId = iconRes.getIdentifier(req.drawableResName, "drawable", req.iconPackPkg)
                if (resId == 0) { diag("  ❌ drawable 不存在: ${req.drawableResName}"); file.delete(); return@Thread }
                val drawable = iconRes.getDrawable(resId, null)
                    ?: run { diag("  ❌ getDrawable=null"); file.delete(); return@Thread }
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
                if (clazz == null) { diag("  ❌ UxFileUtils 类未找到"); file.delete(); return@Thread }
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
                    val png = File(UX_ICON_DIR, "${req.targetPkg}.png")
                    val cfg = File(UX_ICON_DIR, "${req.targetPkg}.cfg")
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
        }.start()
    }

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
