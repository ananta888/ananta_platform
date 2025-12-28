package com.sovworks.eds.android.filemanager.viewmodel

import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.locations.Location

data class FileListState(
    val location: Location? = null,
    val items: List<BrowserRecord> = emptyList(),
    val isLoading: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedItems: Set<BrowserRecord> = emptySet(),
    val error: String? = null,
    val breadcrumbs: List<Location> = emptyList()
)

sealed class FileListEvent {
    data class LocationChanged(val location: Location) : FileListEvent()
    data class ItemClicked(val item: BrowserRecord) : FileListEvent()
    data class ItemSelected(val item: BrowserRecord) : FileListEvent()
    data object ToggleSelectionMode : FileListEvent()
    data object ClearSelection : FileListEvent()
    data object NavigateBack : FileListEvent()
    data object Refresh : FileListEvent()
}
