package com.opiconchanger.utils

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import java.util.Locale

/**
 * 把日志文本渲染成 Spannable：级别配色 + 关键词高亮。
 * 无行号；行首空白裁剪，折行自动与首字母对齐。
 */
object LogRenderer {

    data class Stats(val lineCount: Int, val matchLines: Int)

    private val SECTION_RE = Regex("═══|══")
    private val ERROR_RE = Regex("ERROR|❌|异常|失败|不可用")
    private val WARN_RE = Regex("WARN|⚠️|警告|不匹配")
    private val SUCCESS_RE = Regex("✅|成功|已发送|保存成功|已触发")
    private val INFO_RE = Regex("INFO")

    fun render(raw: String, keyword: String, colors: Palette = Palette()): Pair<CharSequence, Stats> {
        val lines = raw.trimEnd('\n').split("\n")
        val sp = SpannableStringBuilder()
        var matchLines = 0
        val kw = keyword.trim().lowercase(Locale.US)

        lines.forEachIndexed { idx, line ->
            if (idx > 0) sp.append("\n")

            val text = line.trimStart()
            val textStart = sp.length
            sp.append(text)
            sp.setSpan(
                ForegroundColorSpan(lineColor(text, colors)),
                textStart, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 关键词高亮 + 匹配行计数
            if (kw.isNotEmpty()) {
                var matched = false
                val lower = text.lowercase(Locale.US)
                var from = 0
                while (true) {
                    val at = lower.indexOf(kw, from)
                    if (at < 0) break
                    matched = true
                    val s = textStart + at
                    val e = s + kw.length
                    sp.setSpan(
                        BackgroundColorSpan(colors.highlightBg), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sp.setSpan(
                        ForegroundColorSpan(colors.highlightFg), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    from = at + kw.length
                }
                if (matched) matchLines++
            }
        }
        return sp to Stats(lines.size, matchLines)
    }

    private fun lineColor(line: String, c: Palette): Int = when {
        SECTION_RE.containsMatchIn(line) -> c.section
        ERROR_RE.containsMatchIn(line) -> c.error
        WARN_RE.containsMatchIn(line) -> c.warn
        SUCCESS_RE.containsMatchIn(line) -> c.success
        INFO_RE.containsMatchIn(line) -> c.info
        else -> c.text
    }

    class Palette {
        val text = Color.rgb(0x1F, 0x23, 0x28)
        val info = Color.rgb(0x57, 0x60, 0x6A)
        val warn = Color.rgb(0x9A, 0x67, 0x00)
        val error = Color.rgb(0xCF, 0x22, 0x2E)
        val success = Color.rgb(0x1A, 0x7F, 0x37)
        val section = Color.rgb(0x05, 0x50, 0xAE)
        val highlightBg = Color.rgb(0xFF, 0xF3, 0xBF)
        val highlightFg = Color.rgb(0x1F, 0x23, 0x28)
    }
}
