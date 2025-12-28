package com.sovworks.eds.android.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    record: BrowserRecord,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    
    ListItem(
        modifier = Modifier
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
            .padding(horizontal = 8.dp),
        headlineContent = {
            Text(text = record.name)
        },
        supportingContent = {
            Row {
                Text(text = dateFormat.format(record.modificationDate))
                Spacer(modifier = Modifier.width(8.dp))
                if (record.isFile) {
                    Text(text = formatSize(record.size))
                }
            }
        },
        leadingContent = {
            val icon = if (record.isDirectory) Icons.Default.Folder else Icons.Default.Description
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            if (isSelected) {
                Checkbox(checked = true, onCheckedChange = null)
            }
        }
    )
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
