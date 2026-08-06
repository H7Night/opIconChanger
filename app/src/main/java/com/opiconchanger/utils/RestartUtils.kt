package com.opiconchanger.utils

import android.content.Context

/**
 * 作用域重启工具 — 重启桌面进程使 Hook 生效
 */
object RestartUtils {

    private const val LAUNCHER_PKG = "com.android.launcher"

    /**
     * 重启桌面
     */
    fun restartLauncher(context: Context) {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $LAUNCHER_PKG")).waitFor()
            Thread.sleep(500)
            context.packageManager.getLaunchIntentForPackage(LAUNCHER_PKG)?.let {
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        } catch (e: Exception) {
            LogUtils.e("重启桌面失败", e)
        }
    }
}
