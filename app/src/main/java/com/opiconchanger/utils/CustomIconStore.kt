package com.opiconchanger.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CustomIconStore {
    /** 已手动更换图标的包名集合（该目录下存在 <pkg>.cfg）。 */
    suspend fun customizedPackageSet(): Set<String> = withContext(Dispatchers.IO) {
        val files = runCatching { File(IconPaths.UX_ICON_DIR).listFiles() }.getOrNull()
        val result = if (files != null) {
            files.filter { it.isFile && it.name.endsWith(".cfg") }
                .map { it.name.removeSuffix(".cfg") }
                .toSet()
        } else {
            suListCfgPackages()
        }
        LogUtils.d("customizedPackageSet: ${result.size} packages (direct=${files != null})")
        result
    }

    private fun suListCfgPackages(): Set<String> = try {
        // UX_ICON_DIR 为内部常量，无需 shell 引用；保留 glob 展开
        val r = RootExec.exec("ls -1 ${IconPaths.UX_ICON_DIR}/*.cfg 2>/dev/null")
        parseCustomizedPackages(r.stdout)
    } catch (e: Exception) {
        LogUtils.w("CustomIconStore su ls 失败: ${e.message}")
        emptySet()
    }
}

internal fun parseCustomizedPackages(lines: String): Set<String> =
    lines.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.substringAfterLast('/').removeSuffix(".cfg") }
        .toSet()
