package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

internal data class ChatTextPart(val text: String, val language: String? = null)

/** Only explicit fenced blocks are code: never guess or execute a user's script. */
internal fun splitChatCode(text: String): List<ChatTextPart> {
    val fence = Regex("```([A-Za-z0-9_+#.-]*)[ \\t]*\\r?\\n([\\s\\S]*?)```")
    val parts = mutableListOf<ChatTextPart>()
    var offset = 0
    for (match in fence.findAll(text)) {
        if (match.range.first > offset) parts.add(ChatTextPart(text.substring(offset, match.range.first)))
        parts.add(ChatTextPart(match.groupValues[2].removeSuffix("\n"), match.groupValues[1]))
        offset = match.range.last + 1
    }
    if (offset < text.length) parts.add(ChatTextPart(text.substring(offset)))
    return parts
}

@Composable
internal fun ChatCodeBlock(code: String, language: String) {
    val context = LocalContext.current
    val highlighted = remember(code) {
        buildAnnotatedString {
            append(code)
            // Lightweight lexical hints, not a language parser; no network or evaluation.
            val tokens = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b(?:fun|val|var|if|else|return|class|import|def|function|const|let|for|while|true|false|null|None)\\b|\\b\\d+\\b")
            for (match in tokens.findAll(code)) {
                val color = when {
                    match.value.startsWith("\"") || match.value.startsWith("'") -> Color(0xFFA7D7A9)
                    match.value.first().isDigit() -> Color(0xFFF3CE88)
                    else -> Color(0xFFB6C9FF)
                }
                addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF16181D), MaterialTheme.shapes.small).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(language.ifBlank { "code" }, color = Color(0xFFCDD2DB), style = MaterialTheme.typography.labelSmall)
            Row {
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                        ?.setPrimaryClip(ClipData.newPlainText("Code", code))
                }) { Text(xyNow("Salin", "Copy"), color = Color.White) }
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, code) }
                    runCatching { context.startActivity(Intent.createChooser(share, xyNow("Bagikan kode", "Share code"))) }
                }) { Text(xyNow("Bagikan", "Share"), color = Color.White) }
            }
        }
        SelectionContainer {
            Text(highlighted, color = Color(0xFFE7EAF0), fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()).padding(4.dp))
        }
    }
}
