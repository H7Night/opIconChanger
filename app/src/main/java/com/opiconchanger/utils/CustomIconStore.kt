package com.opiconchanger.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CustomIconStore {
    private const val UX_ICON_DIR = "/data/oplus/uxicons/choose"

    /** 已手动更换图标的包名集合（该目录下存在 <pkg>.cfg）。 */
    suspend fun customizedPackageSet(): Set<String> = withContext(Dispatchers.IO) {
        val files = runCatching { File(UX_ICON_DIR).listFiles() }.getOrNull()
        if (files != null) {
            files.filter { it.isFile && it.name.endsWith(".cfg") }
                .map { it.name.removeSuffix(".cfg") }
                .toSet()
        } else {
            suListCfgPackages()
        }
    }

    private fun suListCfgPackages(): Set<String> = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -1 $UX_ICON_DIR/*.cfg 2>/dev/null"))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        parseCustomizedPackages(out)
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
