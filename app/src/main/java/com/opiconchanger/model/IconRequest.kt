package com.opiconchanger.model

import com.opiconchanger.utils.IconPaths
import org.json.JSONArray
import org.json.JSONObject

enum class RequestAction { APPLY, RESTORE }

data class IconAction(
    val targetPkg: String,
    val iconPackPkg: String? = null,
    val drawableResName: String? = null
)

data class IconRequest(
    val action: RequestAction,
    val items: List<IconAction>
) {
    companion object {
        private val PACKAGE_RE = Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$")
        private val RESOURCE_RE = Regex("^[a-zA-Z0-9_]+$")
        private const val MAX_PACKAGE_LEN = 255
        private const val MAX_RESOURCE_LEN = 128

        const val MAX_ITEMS = 500

        const val REQUEST_FILE = IconPaths.REQUEST_FILE
        const val REQUEST_FILE_ROOT = IconPaths.REQUEST_FILE_ROOT

        fun isValidPackageName(pkg: String): Boolean =
            pkg.isNotEmpty() && pkg.length <= MAX_PACKAGE_LEN && PACKAGE_RE.matches(pkg)

        fun isValidResourceName(name: String): Boolean =
            name.isNotEmpty() && name.length <= MAX_RESOURCE_LEN && RESOURCE_RE.matches(name)

        fun apply(targetPkg: String, iconPackPkg: String, drawableResName: String): IconRequest =
            IconRequest(RequestAction.APPLY, listOf(IconAction(targetPkg, iconPackPkg, drawableResName)))

        fun restore(pkgs: List<String>): IconRequest =
            IconRequest(RequestAction.RESTORE, pkgs.map { IconAction(it) })

        fun fromJson(json: String): IconRequest? {
            return try {
                val obj = JSONObject(json)
                if (!obj.has("action") && !obj.has("items")) {
                    val targetPkg = obj.getString("targetPkg")
                    val iconPackPkg = obj.getString("iconPackPkg")
                    val drawableResName = obj.getString("drawableResName")
                    if (!isValidPackageName(targetPkg) ||
                        !isValidPackageName(iconPackPkg) ||
                        !isValidResourceName(drawableResName)
                    ) null
                    else IconRequest(RequestAction.APPLY, listOf(IconAction(targetPkg, iconPackPkg, drawableResName)))
                } else {
                    val action = when (obj.getString("action")) {
                        "apply" -> RequestAction.APPLY
                        "restore" -> RequestAction.RESTORE
                        else -> return null
                    }
                    val arr = obj.optJSONArray("items") ?: return null
                    if (arr.length() < 1 || arr.length() > MAX_ITEMS) return null
                    val items = (0 until arr.length()).map { i ->
                        val item = arr.getJSONObject(i)
                        val targetPkg = item.getString("targetPkg")
                        if (!isValidPackageName(targetPkg)) return null
                        when (action) {
                            RequestAction.APPLY -> {
                                val pack = item.getString("iconPackPkg")
                                val res = item.getString("drawableResName")
                                if (!isValidPackageName(pack) || !isValidResourceName(res)) return null
                                IconAction(targetPkg, pack, res)
                            }
                            RequestAction.RESTORE -> IconAction(targetPkg)
                        }
                    }
                    IconRequest(action, items)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (item in items) {
            val o = JSONObject().put("targetPkg", item.targetPkg)
            item.iconPackPkg?.let { o.put("iconPackPkg", it) }
            item.drawableResName?.let { o.put("drawableResName", it) }
            arr.put(o)
        }
        return JSONObject()
            .put("version", 1)
            .put("action", if (action == RequestAction.APPLY) "apply" else "restore")
            .put("items", arr)
            .toString()
    }
}
