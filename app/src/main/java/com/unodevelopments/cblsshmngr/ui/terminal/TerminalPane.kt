package com.unodevelopments.cblsshmngr.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unodevelopments.cblsshmngr.R
import kotlinx.coroutines.delay

private const val SENTINEL = "\u200B"

@Composable
fun TerminalPane(
    text: androidx.compose.ui.text.AnnotatedString,
    status: String?,
    onSend: (String) -> Unit,
    onResize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var ctrl by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(TextFieldValue(SENTINEL, TextRange(SENTINEL.length))) }
    val scroll = rememberScrollState()
    var stickToBottom by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    var lastCols by remember { mutableIntStateOf(0) }
    var lastRows by remember { mutableIntStateOf(0) }

    LaunchedEffect(scroll.value, scroll.maxValue) {
        stickToBottom = scroll.value >= scroll.maxValue - 48
    }
    LaunchedEffect(text.length) {
        if (stickToBottom) {
            delay(16)
            scroll.scrollTo(scroll.maxValue)
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun sendTyped(raw: String) {
        val normalized = raw.replace("\r\n", "\r").replace('\n', '\r')
        if (normalized.isEmpty()) return
        if (!ctrl) {
            onSend(normalized)
            return
        }
        val out = StringBuilder()
        for (ch in normalized) {
            val code = when (ch) {
                in 'a'..'z' -> ch.code - 96
                in 'A'..'Z' -> ch.code - 64
                else -> null
            }
            if (code != null) out.append(code.toChar()) else out.append(ch)
        }
        ctrl = false
        onSend(out.toString())
    }

    Column(modifier = modifier.background(Color.Black)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val charWidth = with(density) { 8.4.sp.toPx() }.coerceAtLeast(1f)
                    val lineHeight = with(density) { 18.sp.toPx() }.coerceAtLeast(1f)
                    val cols = (size.width / charWidth).toInt()
                    val rows = (size.height / lineHeight).toInt()
                    if (cols != lastCols || rows != lastRows) {
                        lastCols = cols
                        lastRows = rows
                        onResize(cols, rows)
                    }
                }
                .verticalScroll(scroll)
                .padding(8.dp),
        ) {
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.waiting_shell),
                    color = Color(0xFF6E6E6E),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                )
            } else {
                SelectionContainer {
                    Text(
                        text = text,
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
        status?.let { message ->
            Text(
                text = message,
                color = Color(0xFF8A8A8A),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyButton(stringResource(R.string.ctrl), selected = ctrl) { ctrl = !ctrl }
            KeyButton(stringResource(R.string.tab_key)) { onSend("\t") }
            KeyButton(stringResource(R.string.esc_key)) { onSend("\u001b") }
            KeyButton(stringResource(R.string.ctrl_c)) { onSend("\u0003") }
            KeyButton("←") { onSend("\u001b[D") }
            KeyButton("↑") { onSend("\u001b[A") }
            KeyButton("↓") { onSend("\u001b[B") }
            KeyButton("→") { onSend("\u001b[C") }
            IconButton(onClick = {
                focusRequester.requestFocus()
                keyboard?.show()
            }) {
                Icon(
                    Icons.Filled.Keyboard,
                    contentDescription = stringResource(R.string.keyboard),
                    tint = Color(0xFFCCCCCC),
                )
            }
        }
        BasicTextField(
            value = input,
            onValueChange = { next ->
                val raw = next.text
                if (!raw.startsWith(SENTINEL)) {
                    onSend("\u007f")
                } else {
                    sendTyped(raw.removePrefix(SENTINEL))
                }
                input = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
            },
            textStyle = TextStyle(color = Color.Transparent, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .height(1.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Backspace -> {
                            onSend("\u007f")
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            onSend("\r")
                            true
                        }
                        Key.Tab -> {
                            onSend("\t")
                            true
                        }
                        Key.Escape -> {
                            onSend("\u001b")
                            true
                        }
                        Key.DirectionUp -> {
                            onSend("\u001b[A")
                            true
                        }
                        Key.DirectionDown -> {
                            onSend("\u001b[B")
                            true
                        }
                        Key.DirectionLeft -> {
                            onSend("\u001b[D")
                            true
                        }
                        Key.DirectionRight -> {
                            onSend("\u001b[C")
                            true
                        }
                        else -> false
                    }
                },
        )
    }
}

@Composable
private fun KeyButton(label: String, selected: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp),
        colors = if (selected) {
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
        },
    ) {
        Text(label, fontFamily = FontFamily.Monospace)
    }
}
