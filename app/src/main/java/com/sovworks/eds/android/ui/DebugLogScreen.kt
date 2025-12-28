package com.sovworks.eds.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovworks.eds.android.DebugLogManager

@Composable
fun DebugLogScreen() {
    val logs by DebugLogManager.logs.collectAsState()
    val listState = rememberLazyListState()

    // Automatisch zum Ende scrollen, wenn neue Logs kommen
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { DebugLogManager.clear() }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Logs löschen",
                    tint = Color.White
                )
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    color = getLogColor(log),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

fun getLogColor(log: String): Color {
    return when {
        log.contains(" F/") -> Color.Red
        log.contains(" E/") -> Color(0xFFFF5252)
        log.contains(" W/") -> Color.Yellow
        log.contains(" D/") -> Color.Cyan
        log.contains(" I/") -> Color.Green
        else -> Color.White
    }
}
