package com.opiconchanger.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.StringWriter
import java.util.Properties

/**
 * 直接写入已选图标文件（.png + .cfg），与 Launcher 的 UxFileUtils.saveEditDrawableToDir 相同格式。
 *
 * 意义：Launcher 进程的 Hook 只在桌面 onResume 时处理请求文件，若用户停留在本 App 内，
 * 列表无法感知 .cfg 已存在。由本类在 UI 进程直接落盘，customizedPackageSet() 立即可见，
 * 「无适配应用」列表实时移除该应用。
 */
object IconApplier {
    private const val ICON_SIZE = 168

    /**
     * 从 icon pack 加载 drawable，绘制 168×168 PNG，并写入 <pkg>.cfg。
     * @return 是否成功落盘
     */
    suspend fun applyIcon(
        context: Context,
        targetPkg: String,
        iconPackPkg: String,
        drawableResName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = context.packageManager.getResourcesForApplication(iconPackPkg)
            val id = res.getIdentifier(drawableResName, "drawable", iconPackPkg)
            if (id == 0) {
                LogUtils.w("IconApplier drawable 不存在: $drawableResName")
                return@withContext false
            }
            val drawable = res.getDrawable(id, null) ?: return@withContext false
            val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val d = drawable.constantState?.newDrawable() ?: drawable
            d.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
            d.draw(canvas)
            val cfgText = buildCfgText(iconPackPkg, drawableResName)
            val ok = writeFiles(context, targetPkg, bitmap, cfgText)
            LogUtils.i(if (ok) "IconApplier 直接写入成功: $targetPkg → $drawableResName" else "IconApplier 写入失败: $targetPkg")
            ok
        } catch (e: Exception) {
            LogUtils.w("IconApplier 异常: ${e.message}")
            false
        }
    }

    private fun writeFiles(context: Context, targetPkg: String, bitmap: Bitmap, cfgText: String): Boolean {
        val pngTarget = File(IconPaths.UX_ICON_DIR, "$targetPkg.png")
        val cfgTarget = File(IconPaths.UX_ICON_DIR, "$targetPkg.cfg")
        // 尝试直接写（目录 drwxrwxrwx，但 SELinux 可能拒绝）
        if (writeDirect(pngTarget, cfgTarget, bitmap, cfgText)) return true
        // 兜底：su 写入
        return writeViaSu(context, pngTarget, cfgTarget, bitmap, cfgText)
    }

    private fun writeDirect(pngTarget: File, cfgTarget: File, bitmap: Bitmap, cfgText: String): Boolean = try {
        pngTarget.parentFile?.mkdirs()
        pngTarget.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cfgTarget.writeText(cfgText)
        pngTarget.setReadable(true, false); pngTarget.setWritable(true, false)
        cfgTarget.setReadable(true, false); cfgTarget.setWritable(true, false)
        true
    } catch (e: Exception) {
        LogUtils.d("IconApplier 直接写失败，走 su: ${e.message}")
        false
    }

    private fun writeViaSu(context: Context, pngTarget: File, cfgTarget: File, bitmap: Bitmap, cfgText: String): Boolean = try {
        val tmpDir = File(context.cacheDir, "iconapplier").apply { mkdirs() }
        val tmpPng = File(tmpDir, pngTarget.name)
        val tmpCfg = File(tmpDir, cfgTarget.name)
        tmpPng.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmpCfg.writeText(cfgText)
        val cmd = "mkdir -p ${RootExec.shQuote(IconPaths.UX_ICON_DIR)} && " +
            "cp ${RootExec.shQuote(tmpPng.absolutePath)} ${RootExec.shQuote(pngTarget.absolutePath)} && " +
            "cp ${RootExec.shQuote(tmpCfg.absolutePath)} ${RootExec.shQuote(cfgTarget.absolutePath)} && " +
            "chmod 666 ${RootExec.shQuote(pngTarget.absolutePath)} ${RootExec.shQuote(cfgTarget.absolutePath)}"
        val result = RootExec.exec(cmd)
        tmpPng.delete(); tmpCfg.delete()
        result.succeeded
    } catch (e: Exception) {
        LogUtils.w("IconApplier su 写入失败: ${e.message}")
        false
    }

    /** 生成与 Launcher Properties.store 相同的 .cfg 内容（纯 JVM，可测试）。 */
    internal fun buildCfgText(iconPackPkg: String, drawableResName: String): String {
        val props = Properties().apply {
            setProperty("chosse_icon_pack_name", iconPackPkg)
            setProperty("choose_drawable_res_name", drawableResName)
        }
        val sw = StringWriter()
        props.store(sw, "Icon Configuration")
        return sw.toString()
    }
}
