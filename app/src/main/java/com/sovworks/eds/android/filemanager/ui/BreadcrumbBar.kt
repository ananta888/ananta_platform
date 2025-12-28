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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreadcrumbBar(
    breadcrumbs: List<Location>,
    onMenuClick: () -> Unit,
    onBreadcrumbClick: (Location) -> Unit
) {
    TopAppBar(
        title = {
            LazyRow(
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(breadcrumbs) { index, location ->
                    TextButton(
                        onClick = { onBreadcrumbClick(location) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = location.title?.takeIf { it.isNotEmpty() } ?: "/",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (index < breadcrumbs.size - 1) {
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
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
