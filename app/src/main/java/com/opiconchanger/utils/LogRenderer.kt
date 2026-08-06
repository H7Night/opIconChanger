package com.opiconchanger.utils

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import java.util.Locale
import kotlin.math.max

/**
 * 把日志文本渲染成终端风格 Spannable：行号 gutter + 日志级别配色 + 关键词高亮。
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
        val gutterWidth = max(3, lines.size.toString().length)
        val sp = SpannableStringBuilder()
        var matchLines = 0
        val kw = keyword.trim().lowercase(Locale.US)

        lines.forEachIndexed { idx, line ->
            if (idx > 0) sp.append("\n")

            // 行号 gutter
            val lnStart = sp.length
            sp.append(String.format(Locale.US, "%${gutterWidth}d│ ", idx + 1))
            sp.setSpan(
                ForegroundColorSpan(colors.gutter),
                lnStart, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 正文 + 级别配色
            val textStart = sp.length
            sp.append(line)
            sp.setSpan(
                ForegroundColorSpan(lineColor(line, colors)),
                textStart, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 关键词高亮 + 匹配行计数
            if (kw.isNotEmpty()) {
                var matched = false
                val lower = line.lowercase(Locale.US)
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
        val gutter = Color.rgb(0x3B, 0x42, 0x52)
        val text = Color.rgb(0xC8, 0xCD, 0xD8)
        val info = Color.rgb(0x8A, 0x93, 0xA6)
        val warn = Color.rgb(0xE0, 0xA4, 0x58)
        val error = Color.rgb(0xE0, 0x6C, 0x75)
        val success = Color.rgb(0x3E, 0xCF, 0x8E)
        val section = Color.rgb(0x5E, 0xC3, 0xC2)
        val highlightBg = Color.rgb(0x3E, 0x4A, 0x63)
        val highlightFg = Color.WHITE
    }
}
