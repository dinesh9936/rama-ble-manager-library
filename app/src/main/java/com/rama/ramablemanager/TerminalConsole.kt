package com.rama.ramablemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TerminalLevel { INFO, SUCCESS, WARNING, ERROR }

data class TerminalEntry(
    val id: Long,
    val timestamp: String,
    val level: TerminalLevel,
    val message: String
)

/** UI-side log buffer; keep one instance per task (scan, connect, etc.). */
class TerminalLog {
    private val entries = mutableStateListOf<TerminalEntry>()
    val lines: List<TerminalEntry> get() = entries
    private var nextId = 0L

    fun add(message: String, level: TerminalLevel = TerminalLevel.INFO) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        entries.add(TerminalEntry(++nextId, time, level, message))
        if (entries.size > 300) entries.removeAt(0)
    }

    fun clear() = entries.clear()
}

@Composable
fun TerminalConsole(
    title: String,
    log: TerminalLog,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val count = log.lines.size
    LaunchedEffect(count) {
        if (count > 0) state.animateScrollToItem(count - 1)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF121820))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color(0xFFB9C9D6), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { log.clear() }) { Text("Clear") }
        }
        HorizontalDivider(color = Color(0xFF33404C))
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(log.lines, key = { it.id }) { entry ->
                val color = when (entry.level) {
                    TerminalLevel.INFO -> Color(0xFFD6E4EC)
                    TerminalLevel.SUCCESS -> Color(0xFF88E0AA)
                    TerminalLevel.WARNING -> Color(0xFFFFCF83)
                    TerminalLevel.ERROR -> Color(0xFFFF9999)
                }
                Text(
                    text = "[${entry.timestamp}] ${entry.level.name}: ${entry.message}",
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
