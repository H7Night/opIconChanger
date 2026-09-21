package com.opiconchanger.utils

import android.content.Context
import android.content.Intent

/**
 * 桌面重启工具 — 通过系统 am force-stop 强制停止桌面进程后重新拉起，
 * 使图标修改（应用 / 还原）立即生效。
 */
object RestartUtils {

    /**
     * 重启桌面。自动覆盖 OPPO/OnePlus 双包名，仅处理已安装的桌面包。
     */
    fun restartLauncher(context: Context) {
        try {
            val installed = candidatePackages(context)
            if (installed.isEmpty()) {
                LogUtils.w("未找到桌面包，跳过重启")
                return
            }
            for (pkg in installed) {
                RootExec.exec("am force-stop ${RootExec.shQuote(pkg)}")
            }
            Thread.sleep(500)
            for (pkg in installed) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return
            }
            LogUtils.w("桌面已停止，但未能自动拉起")
        } catch (e: Exception) {
            LogUtils.e("重启桌面失败", e)
        }
    }

    private fun candidatePackages(context: Context): List<String> =
        listOf(IconPaths.LAUNCHER_PACKAGE, IconPaths.LAUNCHER_PACKAGE_ALT)
            .distinct()
            .filter { isInstalled(context, it) }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: Exception) {
        false
    }
}
