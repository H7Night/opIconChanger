package com.opiconchanger.utils

import android.content.Context
import com.opiconchanger.model.IconAction
import com.opiconchanger.model.IconRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object IconRequestWriter {

    const val CHUNK_SIZE = IconRequest.MAX_ITEMS

    suspend fun send(context: Context, request: IconRequest): Boolean = withContext(Dispatchers.IO) {
        val json = request.toJson()
        var success = false

        try {
            val f = File(IconPaths.REQUEST_FILE)
            f.parentFile?.mkdirs()
            f.writeText(json)
            f.setReadable(true, false)
            LogUtils.i("请求文件写入成功: ${f.absolutePath} (${json.length}B)")
            success = true
        } catch (e: Exception) {
            LogUtils.w("请求文件直写失败: ${e.message}")
        }

        try {
            val tmp = File(context.cacheDir, "opicon_req_tmp.json")
            tmp.writeText(json)
            tmp.setReadable(true, false)
            val r = RootExec.exec(
                "cp ${RootExec.shQuote(tmp.absolutePath)} ${RootExec.shQuote(IconPaths.REQUEST_FILE_ROOT)} && " +
                    "chmod 666 ${RootExec.shQuote(IconPaths.REQUEST_FILE_ROOT)}"
            )
            if (r.succeeded) {
                LogUtils.i("su → /data/local/tmp 请求写入成功")
                success = true
            } else {
                LogUtils.w("su 请求写入失败 exit=${r.exitCode}")
            }
            tmp.delete()
        } catch (e: Exception) {
            LogUtils.w("su 请求写入不可用: ${e.message}")
        }

        success
    }

    internal fun chunk(items: List<IconAction>, size: Int = CHUNK_SIZE): List<List<IconAction>> =
        if (items.isEmpty()) emptyList() else items.chunked(size)
}
