package com.hacksyt.ubuntuavf.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

class TerminalBuffer(private val maxLines: Int = 2000) {

    private val lines = mutableListOf<AnnotatedString>()
    private var currentLineBuilder = StringBuilder()
    private val lock = Any()

    fun append(rawText: String) {
        synchronized(lock) {
            val sanitized = stripOrParseAnsi(rawText)
            for (char in sanitized) {
                when (char) {
                    '\n' -> {
                        lines.add(buildAnnotatedLine(currentLineBuilder.toString()))
                        currentLineBuilder.clear()
                        if (lines.size > maxLines) {
                            lines.removeAt(0)
                        }
                    }
                    '\r' -> {
                        // Carriage return - reset current line index if needed
                        currentLineBuilder.clear()
                    }
                    else -> {
                        currentLineBuilder.append(char)
                    }
                }
            }
        }
    }

    fun getLines(): List<AnnotatedString> {
        synchronized(lock) {
            val result = lines.toMutableList()
            if (currentLineBuilder.isNotEmpty()) {
                result.add(buildAnnotatedLine(currentLineBuilder.toString()))
            }
            return result
        }
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
            currentLineBuilder.clear()
        }
    }

    private fun stripOrParseAnsi(input: String): String {
        // Basic ANSI escape sequence stripper for clean console rendering
        return input.replace(Regex("\u001B\\[[;\\d]*[A-Za-z]"), "")
    }

    private fun buildAnnotatedLine(lineText: String): AnnotatedString {
        return buildAnnotatedString {
            if (lineText.contains("error", ignoreCase = true) || lineText.contains("fail", ignoreCase = true)) {
                withStyle(SpanStyle(color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)) {
                    append(lineText)
                }
            } else if (lineText.contains("warning", ignoreCase = true)) {
                withStyle(SpanStyle(color = Color(0xFFFFD700))) {
                    append(lineText)
                }
            } else if (lineText.startsWith("root@") || lineText.startsWith("ubuntu@") || lineText.contains("$") || lineText.contains("#")) {
                withStyle(SpanStyle(color = Color(0xFF69F0AE), fontWeight = FontWeight.SemiBold)) {
                    append(lineText)
                }
            } else {
                withStyle(SpanStyle(color = Color(0xFFE0E0E0))) {
                    append(lineText)
                }
            }
        }
    }
}
