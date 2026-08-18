package com.tct.bot.managers

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import mobile.LogListener
import java.util.LinkedList
import java.util.regex.Pattern

object LogManager : LogListener {
    
    private const val MAX_LINES = 300
    private val logBuffer = LinkedList<SpannableStringBuilder>()
    var onLogUpdatedCallback: (() -> Unit)? = null

    override fun onLog(message: String?) {
        if (message == null) return
        
        val parsedSpan = parseAnsiToSpannable(message + "\n")
        
        synchronized(logBuffer) {
            logBuffer.add(parsedSpan)
            if (logBuffer.size > MAX_LINES) {
                logBuffer.removeFirst()
            }
        }
        onLogUpdatedCallback?.invoke()
    }

    fun getFullLogText(): SpannableStringBuilder {
        val combined = SpannableStringBuilder()
        synchronized(logBuffer) {
            for (line in logBuffer) {
                combined.append(line)
            }
        }
        Linkify.addLinks(combined, Linkify.WEB_URLS)
        return combined
    }

    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
        onLogUpdatedCallback?.invoke()
    }

    private fun parseAnsiToSpannable(line: String): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        val matcher = Pattern.compile("\u001B\\[([0-9;]+)m").matcher(line)
        var lastEnd = 0
        var currentColor: Int? = null
        var isBold = false
        
        while (matcher.find()) {
            val textBefore = line.substring(lastEnd, matcher.start())
            if (textBefore.isNotEmpty()) {
                appendWithStyle(ssb, textBefore, currentColor, isBold)
            }
            
            val codes = matcher.group(1)?.split(";")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            var i = 0
            while (i < codes.size) {
                when (val code = codes[i]) {
                    0 -> { currentColor = null; isBold = false }
                    1 -> isBold = true
                    38 -> if (i + 4 < codes.size && codes[i+1] == 2) {
                        currentColor = Color.rgb(codes[i+2], codes[i+3], codes[i+4])
                        i += 4
                    }
                    in 30..37 -> currentColor = getBasicAnsiColor(code)
                    in 90..97 -> currentColor = getBrightAnsiColor(code)
                }
                i++
            }
            lastEnd = matcher.end()
        }
        
        val textAfter = line.substring(lastEnd)
        if (textAfter.isNotEmpty()) {
            appendWithStyle(ssb, textAfter, currentColor, isBold)
        }
        return ssb
    }

    private fun appendWithStyle(ssb: SpannableStringBuilder, text: String, color: Int?, bold: Boolean) {
        val startSpan = ssb.length
        ssb.append(text)
        if (color != null) {
            ssb.setSpan(ForegroundColorSpan(color), startSpan, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (bold) {
            ssb.setSpan(StyleSpan(Typeface.BOLD), startSpan, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun getBasicAnsiColor(code: Int): Int = when (code) {
        30 -> Color.BLACK
        31 -> Color.RED
        32 -> Color.GREEN
        33 -> Color.YELLOW
        34 -> Color.BLUE
        35 -> Color.MAGENTA
        36 -> Color.CYAN
        37 -> Color.WHITE
        else -> Color.WHITE
    }
    
    private fun getBrightAnsiColor(code: Int): Int = when (code) {
        90 -> Color.DKGRAY
        91 -> Color.parseColor("#FF5252") // Light Red
        92 -> Color.parseColor("#69F0AE") // Light Green (matches banner success)
        93 -> Color.parseColor("#FFEA00") // Light Yellow (matches banner accent)
        94 -> Color.parseColor("#448AFF") // Light Blue
        95 -> Color.parseColor("#E040FB") // Light Magenta
        96 -> Color.parseColor("#18FFFF") // Light Cyan (matches banner primary)
        97 -> Color.WHITE
        else -> Color.WHITE
    }
}
