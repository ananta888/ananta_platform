package com.sovworks.eds.android.filemanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Context
import com.sovworks.eds.android.filemanager.viewmodel.FileListEvent
import com.sovworks.eds.android.filemanager.viewmodel.FileListViewModel
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.android.navigation.Screen

@Composable
fun FileListScreen(
    viewModel: FileListViewModel,
    navigationViewModel: NavigationViewModel,
    context: Context,
    onMenuClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val navigationStack by navigationViewModel.navigationStack.collectAsState()

    Scaffold(
        topBar = {
            BreadcrumbBar(
                screens = navigationStack,
                locations = state.breadcrumbs,
                onMenuClick = onMenuClick,
                onScreenClick = { screen ->
                    navigationViewModel.navigateToRoot(screen)
                },
                onLocationClick = { location ->
                    viewModel.onEvent(FileListEvent.LocationChanged(location), context)
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FileListContent(
                state = state,
                onItemClick = { record ->
                    viewModel.onEvent(FileListEvent.ItemClicked(record), context)
                },
                onItemLongClick = { record ->
                    viewModel.onEvent(FileListEvent.ItemSelected(record), context)
                }
            )
        }
    }
}

@Composable
fun FileListContent(
    state: com.sovworks.eds.android.filemanager.viewmodel.FileListState,
    onItemClick: (com.sovworks.eds.android.filemanager.records.BrowserRecord) -> Unit,
    onItemLongClick: (com.sovworks.eds.android.filemanager.records.BrowserRecord) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (state.error != null) {
            Text(
                text = state.error ?: "Unknown error",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.items) { record ->
                    FileListItem(
                        record = record,
                        isSelected = state.selectedItems.contains(record),
                        onItemClick = { onItemClick(record) },
                        onItemLongClick = { onItemLongClick(record) }
                    )
                }
            }
        }
    }
}
