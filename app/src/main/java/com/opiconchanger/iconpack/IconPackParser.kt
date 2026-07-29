package com.opiconchanger.iconpack

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.opiconchanger.model.AppFilterEntry
import com.opiconchanger.model.IconEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * Icon Pack 解析引擎 — 参考 GlobalIconPack 的 ResourceOwner 实现
 */
class IconPackParser(private val context: Context) {

    companion object {
        private const val MAX_CACHE_SIZE = 16 * 1024
    }

    private val pm: PackageManager = context.packageManager

    /** LruCache: packName → entries */
    private val iconPackCache = mutableMapOf<String, List<AppFilterEntry>>()

    /** LruCache: "packName:drawableName" → Bitmap */
    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** LruCache: packName → Resources */
    private val resourceCache = object : LruCache<String, Resources>(8) {
        override fun sizeOf(key: String, value: Resources): Int = 1
    }

    /** GIP 方式：通过 pm.getResourcesForApplication 获取 Icon Pack 资源 */
    private fun getResourcesFor(pack: String): Resources? {
        resourceCache.get(pack)?.let { return it }
        return try {
            val res = pm.getResourcesForApplication(pack)
            resourceCache.put(pack, res)
            res
        } catch (e: PackageManager.NameNotFoundException) {
            android.util.Log.w("opIconChanger", "Resources not found for $pack: ${e.message}")
            null
        }
    }

    // ==================== 加载 Icon Pack ====================

    suspend fun loadIconPack(iconPackPackage: String): List<AppFilterEntry> =
        withContext(Dispatchers.IO) {
            iconPackCache[iconPackPackage]?.let { return@withContext it }

            val entries = mutableListOf<AppFilterEntry>()
            try {
                val res = getResourcesFor(iconPackPackage)
                    ?: return@withContext emptyList<AppFilterEntry>().also {
                        iconPackCache[iconPackPackage] = it
                    }

                val appFilterId = res.getIdentifier("appfilter", "xml", iconPackPackage)
                if (appFilterId == 0) {
                    android.util.Log.w("opIconChanger", "appfilter.xml not found in $iconPackPackage")
                    iconPackCache[iconPackPackage] = emptyList()
                    return@withContext emptyList()
                }

                val parser: XmlResourceParser = res.getXml(appFilterId)
                parseAppFilterXml(parser, entries)
                fillAppLabels(entries)
                iconPackCache[iconPackPackage] = entries
                android.util.Log.i("opIconChanger", "Loaded ${entries.size} entries from $iconPackPackage")
            } catch (e: Exception) {
                android.util.Log.e("opIconChanger", "Failed to load $iconPackPackage: ${e.message}", e)
                iconPackCache[iconPackPackage] = emptyList()
            }
            entries
        }

    // ==================== 加载图标 Bitmap ====================

    suspend fun loadIconBitmap(iconPackPackage: String, drawableName: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val cacheKey = "$iconPackPackage:$drawableName"
            bitmapCache.get(cacheKey)?.let { return@withContext it }

            try {
                val res = getResourcesFor(iconPackPackage) ?: return@withContext null
                val id = res.getIdentifier(drawableName, "drawable", iconPackPackage)
                if (id == 0) return@withContext null

                val drawable: Drawable = res.getDrawable(id, null)
                val bitmap = drawableToBitmap(drawable)
                bitmapCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                android.util.Log.w("opIconChanger", "loadIconBitmap failed: $iconPackPackage/$drawableName: ${e.message}")
                null
            }
        }

    // ==================== 搜索 ====================

    suspend fun searchByApp(iconPackPackage: String, query: String): List<IconEntry> =
        withContext(Dispatchers.IO) {
            val entries = iconPackCache[iconPackPackage] ?: loadIconPack(iconPackPackage)
            if (query.isBlank()) return@withContext entries.map { it.toIconEntry(iconPackPackage) }
            val q = query.lowercase()
            entries.filter {
                it.appLabel.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }.map { it.toIconEntry(iconPackPackage) }
        }

    suspend fun searchByDrawableName(iconPackPackage: String, query: String): List<IconEntry> =
        withContext(Dispatchers.IO) {
            val entries = iconPackCache[iconPackPackage] ?: loadIconPack(iconPackPackage)
            if (query.isBlank()) return@withContext emptyList()
            val q = query.lowercase()
            entries.filter { it.drawableName.lowercase().contains(q) }
                .map { it.toIconEntry(iconPackPackage) }
        }

    suspend fun getAllDrawableNames(iconPackPackage: String): List<String> =
        withContext(Dispatchers.IO) {
            (iconPackCache[iconPackPackage] ?: loadIconPack(iconPackPackage))
                .map { it.drawableName }.distinct()
        }

    // ==================== 扫描已安装的 Icon Pack ====================

    suspend fun scanInstalledIconPacks(): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val flags = PackageManager.MATCH_ALL or PackageManager.GET_ACTIVITIES
        val resolveInfoList: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, flags)
        val seenPackages = mutableSetOf<String>()

        for (ri in resolveInfoList) {
            val pkg = ri.activityInfo.packageName
            if (!seenPackages.add(pkg)) continue
            try {
                val res = getResourcesFor(pkg) ?: continue
                val resId = res.getIdentifier("appfilter", "xml", pkg)
                if (resId != 0) result.add(pkg)
            } catch (_: Exception) {}
        }
        android.util.Log.i("opIconChanger", "Found ${result.size} icon packs: $result")
        result.sorted()
    }

    // ==================== 私有方法 ====================

    private fun parseAppFilterXml(parser: XmlResourceParser, entries: MutableList<AppFilterEntry>) {
        var eventType = parser.eventType
        var currentDrawable: String? = null
        var currentComponent: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "item") {
                        currentComponent = parser.getAttributeValue(null, "component")
                        currentDrawable = parser.getAttributeValue(null, "drawable")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && currentDrawable != null && currentComponent != null) {
                        val pkg = extractPackageName(currentComponent!!)
                        entries.add(AppFilterEntry(currentComponent!!, pkg, currentDrawable!!))
                        currentComponent = null; currentDrawable = null
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun extractPackageName(component: String): String =
        component.substringAfter("ComponentInfo{").substringBefore("/").trim()

    private fun fillAppLabels(entries: List<AppFilterEntry>) {
        for (entry in entries) {
            if (entry.packageName.isEmpty()) continue
            try {
                val appInfo: ApplicationInfo = pm.getApplicationInfo(entry.packageName, 0)
                entry.appLabel = pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {}
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 144
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 144
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            drawable.setBounds(0, 0, w, h)
            drawable.draw(Canvas(bmp))
        }
    }

    private fun AppFilterEntry.toIconEntry(iconPackPackage: String) = IconEntry(
        drawableName = drawableName, packageName = packageName,
        appLabel = appLabel, iconPackPackage = iconPackPackage
    )
}
