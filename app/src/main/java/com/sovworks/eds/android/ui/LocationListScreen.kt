package com.sovworks.eds.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.DeviceBasedLocation

@Composable
fun LocationListScreen(
    locations: List<Location>,
    onLocationClick: (Location) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(locations) { location ->
            LocationItem(
                location = location,
                onClick = { onLocationClick(location) }
            )
        }
    }
}

@Composable
fun LocationItem(
    location: Location,
    onClick: () -> Unit
) {
    val icon = when (location) {
        is EDSLocation -> Icons.Default.Lock
        is DeviceBasedLocation -> Icons.Default.Storage
        else -> Icons.Default.Folder
    }

    ListItem(
        headlineContent = { Text(location.getTitle() ?: "Unbenannter Speicher") },
        supportingContent = { Text(try { location.getCurrentPath()?.pathString ?: "" } catch (e: Exception) { "" }) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
