package com.sovworks.eds.android.filemanager.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sovworks.eds.locations.Location

import com.sovworks.eds.android.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreadcrumbBar(
    screens: List<Screen>,
    locations: List<Location>,
    onMenuClick: () -> Unit,
    onScreenClick: (Screen) -> Unit,
    onLocationClick: (Location) -> Unit
) {
    TopAppBar(
        title = {
            LazyRow(
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(screens) { index, screen ->
                    TextButton(
                        onClick = { onScreenClick(screen) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (index < screens.size - 1 || locations.isNotEmpty()) {
                        BreadcrumbSeparator()
                    }
                }
                itemsIndexed(locations) { index, location ->
                    TextButton(
                        onClick = { onLocationClick(location) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = location.title?.takeIf { it.isNotEmpty() } ?: "/",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (index < locations.size - 1) {
                        BreadcrumbSeparator()
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
fun BreadcrumbSeparator() {
    Text(
        text = ">",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 2.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
    )
}
