package com.opiconchanger.model

import org.json.JSONObject

/**
 * 跨进程图标请求 — opIconChanger UI 进程 → Launcher 进程
 *
 * UI 侧序列化为 JSON 写入共享文件，MainHook 侧在 Launcher 进程中读取并执行。
 */
data class IconRequest(
    /** 目标应用包名（要替换图标的 App） */
    val targetPkg: String,
    /** Icon Pack 的包名 */
    val iconPackPkg: String,
    /** Icon Pack 中的 drawable 资源名 */
    val drawableResName: String
) {
    companion object {
        // 优先路径：/data/local/tmp（世界可读写）
        const val REQUEST_FILE_PRIMARY = "/data/local/tmp/opicon_request.json"
        // 备用路径：应用自身文件目录（设为 world-readable 后 Launcher 也能读）
        const val REQUEST_FILE_FALLBACK = "/data/data/com.opiconchanger/files/opicon_request.json"

        fun fromJson(json: String): IconRequest? = try {
            val obj = JSONObject(json)
            IconRequest(
                targetPkg = obj.getString("targetPkg"),
                iconPackPkg = obj.getString("iconPackPkg"),
                drawableResName = obj.getString("drawableResName")
            )
        } catch (_: Exception) { null }
    }

    fun toJson(): String = JSONObject().apply {
        put("targetPkg", targetPkg)
        put("iconPackPkg", iconPackPkg)
        put("drawableResName", drawableResName)
    }.toString()
}
