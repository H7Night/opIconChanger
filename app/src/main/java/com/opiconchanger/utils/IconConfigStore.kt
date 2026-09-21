package com.opiconchanger.utils

import com.opiconchanger.model.IconRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

data class IconConfig(val iconPackPkg: String, val drawableResName: String)

object IconConfigStore {

    suspend fun readConfig(pkg: String): IconConfig? = withContext(Dispatchers.IO) {
        if (!IconRequest.isValidPackageName(pkg)) return@withContext null
        val path = "${IconPaths.UX_ICON_DIR}/$pkg.cfg"
        val direct = runCatching { File(path).readText() }.getOrNull()
        val text = direct ?: runCatching {
            RootExec.exec("cat ${RootExec.shQuote(path)}").stdout
        }.getOrNull()
        parseCfg(text ?: return@withContext null)
    }

    suspend fun readConfigs(pkgs: Collection<String>): Map<String, IconConfig> =
        withContext(Dispatchers.IO) {
            pkgs.mapNotNull { pkg -> readConfig(pkg)?.let { pkg to it } }.toMap()
        }

    internal fun parseCfg(text: String): IconConfig? = try {
        val props = Properties()
        props.load(text.reader())
        val pack = props.getProperty("chosse_icon_pack_name")?.trim().orEmpty()
        val res = props.getProperty("choose_drawable_res_name")?.trim().orEmpty()
        if (IconRequest.isValidPackageName(pack) && IconRequest.isValidResourceName(res))
            IconConfig(pack, res)
        else null
    } catch (_: Exception) {
        null
    }
}
