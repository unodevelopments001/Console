package com.unodevelopments.cblsshmngr.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

class TerminalBuffer(private val maxLines: Int = 2000) {
    private val lines = mutableListOf(TermLine())
    private var row = 0
    private var column = 0
    private var bold = false
    private var fgIndex: Int? = null
    private var trueColor: Color? = null
    private var state = ParseState.GROUND
    private val csi = StringBuilder()
    private val lock = Any()

    fun write(text: String) {
        if (text.isEmpty()) return
        synchronized(lock) {
            for (ch in text) {
                consume(ch)
            }
        }
    }

    fun snapshot(): AnnotatedString = synchronized(lock) {
        buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                if (index > 0) append('\n')
                append(line.toAnnotated())
            }
        }
    }

    private fun consume(ch: Char) {
        when (state) {
            ParseState.GROUND -> when (ch) {
                '\u001b' -> state = ParseState.ESC
                '\n' -> newline()
                '\r' -> column = 0
                '\b' -> if (column > 0) column--
                '\t' -> column = ((column / 8) + 1) * 8
                '\u0007' -> Unit
                else -> if (ch.code >= 32) put(ch)
            }
            ParseState.ESC -> when (ch) {
                '[' -> {
                    csi.clear()
                    state = ParseState.CSI
                }
                ']' -> {
                    state = ParseState.OSC
                }
                else -> state = ParseState.GROUND
            }
            ParseState.CSI -> when {
                ch.code in 0x20..0x3F -> csi.append(ch)
                ch.code in 0x40..0x7E -> {
                    handleCsi(ch, csi.toString())
                    state = ParseState.GROUND
                }
                else -> state = ParseState.GROUND
            }
            ParseState.OSC -> when (ch) {
                '\u0007' -> state = ParseState.GROUND
                '\u001b' -> state = ParseState.OSC_ESC
                else -> Unit
            }
            ParseState.OSC_ESC -> state = ParseState.GROUND
        }
    }

    private fun handleCsi(command: Char, raw: String) {
        val params = params(raw)
        when (command) {
            'm' -> sgr(params)
            'K' -> eraseLine(params.firstOrNull() ?: 0)
            'J' -> if ((params.firstOrNull() ?: 0) == 2) clear()
            'A' -> moveRow(-(params.firstOrNull() ?: 1).coerceAtLeast(1))
            'B' -> moveRow((params.firstOrNull() ?: 1).coerceAtLeast(1))
            'C' -> column += (params.firstOrNull() ?: 1).coerceAtLeast(1)
            'D' -> column = (column - (params.firstOrNull() ?: 1).coerceAtLeast(1)).coerceAtLeast(0)
            'H', 'f' -> {
                row = ((params.getOrNull(0) ?: 1).coerceAtLeast(1) - 1).coerceAtMost(lines.lastIndex)
                column = (params.getOrNull(1) ?: 1).coerceAtLeast(1) - 1
            }
            'G' -> column = (params.firstOrNull() ?: 1).coerceAtLeast(1) - 1
        }
    }

    private fun sgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetStyle()
            return
        }
        var index = 0
        while (index < params.size) {
            when (val value = params[index]) {
                0 -> resetStyle()
                1 -> bold = true
                22 -> bold = false
                39 -> {
                    fgIndex = null
                    trueColor = null
                }
                in 30..37 -> {
                    fgIndex = value - 30
                    trueColor = null
                }
                in 90..97 -> {
                    fgIndex = value - 90 + 8
                    trueColor = null
                }
                38 -> {
                    if (index + 2 < params.size && params[index + 1] == 5) {
                        trueColor = color256(params[index + 2])
                        fgIndex = null
                        index += 2
                    } else if (index + 4 < params.size && params[index + 1] == 2) {
                        trueColor = Color(params[index + 2], params[index + 3], params[index + 4])
                        fgIndex = null
                        index += 4
                    }
                }
            }
            index++
        }
    }

    private fun put(ch: Char) {
        ensureRow()
        lines[row].set(column, ch, currentColor())
        column++
        if (column >= 500) newline()
    }

    private fun newline() {
        row++
        column = 0
        ensureRow()
        trim()
    }

    private fun moveRow(delta: Int) {
        row = (row + delta).coerceIn(0, lines.lastIndex)
    }

    private fun eraseLine(mode: Int) {
        ensureRow()
        when (mode) {
            1 -> lines[row].eraseBefore(column)
            2 -> lines[row].clear()
            else -> lines[row].eraseFrom(column)
        }
    }

    private fun clear() {
        lines.clear()
        lines.add(TermLine())
        row = 0
        column = 0
    }

    private fun ensureRow() {
        while (lines.size <= row) lines.add(TermLine())
    }

    private fun trim() {
        val overflow = lines.size - maxLines
        if (overflow <= 0) return
        repeat(overflow) { lines.removeAt(0) }
        row = (row - overflow).coerceAtLeast(0)
    }

    private fun resetStyle() {
        bold = false
        fgIndex = null
        trueColor = null
    }

    private fun currentColor(): Color {
        trueColor?.let { return it }
        val index = fgIndex
        if (index == null) return if (bold) Color.White else DEFAULT
        val resolved = if (bold && index in 0..7) index + 8 else index
        return ANSI[resolved.coerceIn(0, ANSI.lastIndex)]
    }

    private fun params(raw: String): List<Int> {
        val cleaned = raw.trimStart('?', '>', '=', '<', '!', ' ')
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(';').map { it.toIntOrNull() ?: 0 }
    }

    private enum class ParseState { GROUND, ESC, CSI, OSC, OSC_ESC }

    private class TermLine {
        private val chars = StringBuilder()
        private val colors = ArrayList<Color>()

        fun set(index: Int, ch: Char, color: Color) {
            while (chars.length < index) {
                chars.append(' ')
                colors.add(DEFAULT)
            }
            if (index == chars.length) {
                chars.append(ch)
                colors.add(color)
            } else {
                chars.setCharAt(index, ch)
                colors[index] = color
            }
        }

        fun eraseFrom(index: Int) {
            if (index >= chars.length) return
            chars.setLength(index)
            while (colors.size > chars.length) colors.removeAt(colors.lastIndex)
        }

        fun eraseBefore(index: Int) {
            val end = minOf(index, chars.length)
            for (cursor in 0 until end) {
                chars.setCharAt(cursor, ' ')
                colors[cursor] = DEFAULT
            }
        }

        fun clear() {
            chars.setLength(0)
            colors.clear()
        }

        fun toAnnotated(): AnnotatedString {
            if (chars.isEmpty()) return AnnotatedString("")
            return buildAnnotatedString {
                var index = 0
                while (index < chars.length) {
                    val color = colors[index]
                    var end = index + 1
                    while (end < chars.length && colors[end] == color) end++
                    withStyle(SpanStyle(color = color)) {
                        append(chars.substring(index, end))
                    }
                    index = end
                }
            }
        }
    }

    private companion object {
        val DEFAULT = Color(0xFFCCCCCC)
        val ANSI = listOf(
            Color(0xFF3A3A3A),
            Color(0xFFCD3131),
            Color(0xFF0DBC79),
            Color(0xFFE5E510),
            Color(0xFF2472C8),
            Color(0xFFBC3FBC),
            Color(0xFF11A8CD),
            Color(0xFFE5E5E5),
            Color(0xFF666666),
            Color(0xFFF14C4C),
            Color(0xFF23D18B),
            Color(0xFFF5F543),
            Color(0xFF3B8EEA),
            Color(0xFFD670D6),
            Color(0xFF29B8DB),
            Color(0xFFFFFFFF),
        )

        fun color256(index: Int): Color {
            if (index < 16) return ANSI[index]
            if (index >= 232) {
                val level = (8 + (index - 232) * 10).coerceIn(0, 255)
                return Color(level, level, level)
            }
            val cube = index - 16
            val red = cube / 36
            val green = (cube % 36) / 6
            val blue = cube % 6
            fun level(value: Int) = if (value == 0) 0 else 55 + value * 40
            return Color(level(red), level(green), level(blue))
        }
    }
}
