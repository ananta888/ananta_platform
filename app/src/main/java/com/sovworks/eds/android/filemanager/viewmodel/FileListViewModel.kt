package com.sovworks.eds.android.filemanager.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.DirectorySettings
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.filemanager.tasks.LoadDirSettingsObservable
import com.sovworks.eds.android.filemanager.tasks.ReadDir
import com.sovworks.eds.locations.Location
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Maybe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Stack

class FileListViewModel : ViewModel() {

    private val _state = MutableStateFlow(FileListState())
    val state: StateFlow<FileListState> = _state.asStateFlow()

    private val disposables = CompositeDisposable()
    private val navigationHistory = Stack<Location>()

    fun onEvent(event: FileListEvent, context: Context) {
        when (event) {
            is FileListEvent.LocationChanged -> loadLocation(event.location, context)
            is FileListEvent.ItemClicked -> handleItemClicked(event.item, context)
            is FileListEvent.ItemSelected -> handleItemSelected(event.item)
            is FileListEvent.ToggleSelectionMode -> {
                _state.update { it.copy(selectionMode = !it.selectionMode, selectedItems = emptySet()) }
            }
            is FileListEvent.ClearSelection -> {
                _state.update { it.copy(selectedItems = emptySet()) }
            }
            is FileListEvent.NavigateBack -> {
                if (navigationHistory.isNotEmpty()) {
                    loadLocation(navigationHistory.pop(), context, addToHistory = false)
                }
            }
            is FileListEvent.Refresh -> {
                _state.value.location?.let { loadLocation(it, context, addToHistory = false) }
            }
        }
    }

    private fun loadLocation(location: Location, context: Context, addToHistory: Boolean = true) {
        disposables.clear()
        
        if (addToHistory && _state.value.location != null) {
            navigationHistory.push(_state.value.location!!.copy())
        }

        _state.update { it.copy(isLoading = true, location = location, error = null, items = emptyList()) }
        updateBreadcrumbs(location)

        val settingsMaybe: Maybe<DirectorySettings> = LoadDirSettingsObservable.create(location)
        
        val disposable = settingsMaybe
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapObservable { settings: DirectorySettings ->
                ReadDir.createObservable(context, location, emptyList(), settings, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
            }
            .subscribe(
                { record: BrowserRecord ->
                    _state.update { it.copy(items = it.items + record) }
                },
                { error: Throwable ->
                    Logger.log(error)
                    _state.update { it.copy(isLoading = false, error = error.message) }
                },
                {
                    _state.update { it.copy(isLoading = false, items = sortItems(state.value.items)) }
                }
            )
        disposables.add(disposable)
    }

    fun canNavigateBack(): Boolean = navigationHistory.isNotEmpty()

    private fun sortItems(items: List<BrowserRecord>): List<BrowserRecord> {
        return items.sortedWith(compareBy<BrowserRecord> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun handleItemClicked(item: BrowserRecord, context: Context) {
        if (_state.value.selectionMode) {
            handleItemSelected(item)
        } else {
            try {
                if (item.isDirectory) {
                    val newLocation = _state.value.location?.copy()
                    if (newLocation != null) {
                        newLocation.setCurrentPath(item.path)
                        loadLocation(newLocation, context)
                    }
                } else {
                    (context as? FileManagerActivity)?.let {
                        item.setHostActivity(it)
                        item.open()
                    }
                }
            } catch (e: Exception) {
                Logger.log(e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun handleItemSelected(item: BrowserRecord) {
        _state.update {
            val newSelection = if (it.selectedItems.contains(item)) {
                it.selectedItems - item
            } else {
                it.selectedItems + item
            }
            it.copy(selectedItems = newSelection)
        }
    }

    private fun updateBreadcrumbs(location: Location) {
        val crumbs = mutableListOf<Location>()
        try {
            var currentPath = location.getCurrentPath()
            while (currentPath != null) {
                val loc = location.copy()
                loc.setCurrentPath(currentPath)
                crumbs.add(0, loc)
                currentPath = currentPath.getParentPath()
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
        _state.update { it.copy(breadcrumbs = crumbs) }
    }

    override fun onCleared() {
        disposables.clear()
        super.onCleared()
    }
}
