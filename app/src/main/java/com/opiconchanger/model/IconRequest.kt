package com.opiconchanger.model

import com.opiconchanger.utils.IconPaths
import org.json.JSONObject

/**
 * 跨进程图标请求 — opIconChanger UI 进程 → Launcher 进程
 *
 * UI 侧序列化为 JSON 写入共享文件，MainHook 侧在 Launcher 进程中读取并执行。
 *
 * 安全设计：fromJson 对三个字段做严格校验（见 [PACKAGE_RE]、[RESOURCE_RE]），
 * 拒绝路径穿越（/、\、..）与任意 shell 字符，防止伪造请求被 Launcher 特权进程执行。
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
        /** Android 包名规则：字母/数字/下划线/点，段不能以数字开头，且不含 / \ 与空白 */
        private val PACKAGE_RE = Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$")
        /** drawable 资源名：字母/数字/下划线 */
        private val RESOURCE_RE = Regex("^[a-zA-Z0-9_]+$")
        /** 包名最大长度（Android 单段 255，总长宽松限制防滥用） */
        private const val MAX_PACKAGE_LEN = 255
        /** drawable 名最大长度 */
        private const val MAX_RESOURCE_LEN = 128

        /** 请求文件路径（与 IconPaths 保持一致，Hook 侧与 UI 侧共用） */
        const val REQUEST_FILE = IconPaths.REQUEST_FILE
        const val REQUEST_FILE_ROOT = IconPaths.REQUEST_FILE_ROOT

        /** 包名合法性校验（用于拒绝伪造请求） */
        fun isValidPackageName(pkg: String): Boolean =
            pkg.isNotEmpty() && pkg.length <= MAX_PACKAGE_LEN && PACKAGE_RE.matches(pkg)

        /** drawable 资源名合法性校验 */
        fun isValidResourceName(name: String): Boolean =
            name.isNotEmpty() && name.length <= MAX_RESOURCE_LEN && RESOURCE_RE.matches(name)

        fun fromJson(json: String): IconRequest? {
            return try {
                val obj = JSONObject(json)
                val targetPkg = obj.getString("targetPkg")
                val iconPackPkg = obj.getString("iconPackPkg")
                val drawableResName = obj.getString("drawableResName")
                if (!isValidPackageName(targetPkg)) return null
                if (!isValidPackageName(iconPackPkg)) return null
                if (!isValidResourceName(drawableResName)) return null
                IconRequest(targetPkg, iconPackPkg, drawableResName)
            } catch (_: Exception) { null }
        }
    }

    fun toJson(): String = JSONObject().apply {
        put("targetPkg", targetPkg)
        put("iconPackPkg", iconPackPkg)
        put("drawableResName", drawableResName)
    }.toString()
}
