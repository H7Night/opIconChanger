package com.opiconchanger.utils

/**
 * 全局路径/包名常量 — App 进程与 Launcher Hook 进程共用。
 * 集中管理，避免各文件散落硬编码路径。
 */
object IconPaths {
    /** 本模块包名 */
    const val APP_PACKAGE = "com.opiconchanger"

    /** 桌面主包名（OPPO/OnePlus 双包名，见 arrays.xml xposed_scope） */
    const val LAUNCHER_PACKAGE = "com.android.launcher"
    const val LAUNCHER_PACKAGE_ALT = "com.oppo.launcher"

    /** OPPO 桌面自定义图标目录（drwxrwxrwx，App 与 Launcher 均直接可读写） */
    const val UX_ICON_DIR = "/data/oplus/uxicons/choose"

    /** Launcher Hook 诊断日志（App 日志 Tab 经 su 读取展示） */
    const val DIAG_FILE = "$UX_ICON_DIR/opicon_hook_diag.txt"

    /**
     * 跨进程请求文件 — 首选路径：位于共享的 UX 图标目录。
     * App 直接写入（文件属主 = App UID），Launcher Hook 校验属主后处理并删除。
     */
    const val REQUEST_FILE = "$UX_ICON_DIR/opicon_request.json"

    /**
     * 跨进程请求文件 — 兜底路径：/data/local/tmp（仅 shell/root 可写，Launcher 可读）。
     * 当 UX 目录被 SELinux 拒绝直接写入时，经 su 写入此路径。
     */
    const val REQUEST_FILE_ROOT = "/data/local/tmp/opicon_request.json"
}
