package com.opiconchanger.utils

import android.content.Context

/**
 * 作用域重启工具 — 重启桌面进程使 Hook 生效
 */
object RestartUtils {

    /**
     * 重启桌面
     */
    fun restartLauncher(context: Context) {
        try {
            RootExec.exec("am force-stop ${RootExec.shQuote(IconPaths.LAUNCHER_PACKAGE)}")
            Thread.sleep(500)
            context.packageManager.getLaunchIntentForPackage(IconPaths.LAUNCHER_PACKAGE)?.let {
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        } catch (e: Exception) {
            LogUtils.e("重启桌面失败", e)
        }
    }
}
