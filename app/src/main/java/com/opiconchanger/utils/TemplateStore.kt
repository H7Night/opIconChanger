package com.opiconchanger.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TemplateEntry(val pkg: String, val iconPackPkg: String, val drawableResName: String)

data class IconTemplate(
    val id: String,
    val name: String,
    val createdAt: Long,
    val entries: List<TemplateEntry>
)

object TemplateStore {
    private const val FILE_NAME = "templates.json"
    private const val VERSION = 1

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun list(context: Context): List<IconTemplate> =
        runCatching { decode(file(context).readText()) }.getOrDefault(emptyList())

    fun create(context: Context, name: String, entries: List<TemplateEntry>): IconTemplate {
        val template = IconTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
            entries = entries
        )
        file(context).writeText(encode(list(context) + template))
        return template
    }

    fun rename(context: Context, id: String, newName: String): Boolean {
        val all = list(context).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx < 0) return false
        all[idx] = all[idx].copy(name = newName.trim())
        file(context).writeText(encode(all))
        return true
    }

    fun delete(context: Context, id: String): Boolean {
        val all = list(context)
        val remaining = all.filterNot { it.id == id }
        if (remaining.size == all.size) return false
        file(context).writeText(encode(remaining))
        return true
    }

    internal fun encode(items: List<IconTemplate>): String {
        val templates = JSONArray()
        for (t in items) {
            val entries = JSONArray()
            for (e in t.entries) {
                entries.put(
                    JSONObject()
                        .put("pkg", e.pkg)
                        .put("iconPackPkg", e.iconPackPkg)
                        .put("drawableResName", e.drawableResName)
                )
            }
            templates.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("createdAt", t.createdAt)
                    .put("entries", entries)
            )
        }
        return JSONObject().put("version", VERSION).put("templates", templates).toString()
    }

    internal fun decode(json: String): List<IconTemplate> = try {
        val templates = JSONObject(json).optJSONArray("templates") ?: JSONArray()
        (0 until templates.length()).mapNotNull { i ->
            val o = templates.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", "")
            val name = o.optString("name", "")
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val arr = o.optJSONArray("entries") ?: JSONArray()
            val entries = (0 until arr.length()).mapNotNull { j ->
                val e = arr.optJSONObject(j) ?: return@mapNotNull null
                val pkg = e.optString("pkg", "")
                if (pkg.isBlank()) return@mapNotNull null
                TemplateEntry(pkg, e.optString("iconPackPkg", ""), e.optString("drawableResName", ""))
            }
            IconTemplate(id, name, o.optLong("createdAt", 0L), entries)
        }
    } catch (_: Exception) {
        emptyList()
    }
}
