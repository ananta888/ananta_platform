package com.sovworks.eds.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovworks.eds.android.filemanager.ui.FileListScreen
import com.sovworks.eds.android.filemanager.viewmodel.FileListViewModel
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.android.navigation.Screen
import com.sovworks.eds.android.filemanager.ui.BreadcrumbBar
import com.sovworks.eds.android.filemanager.viewmodel.FileListEvent
import kotlinx.coroutines.launch

@Composable
fun AppScaffold(
    navigationViewModel: NavigationViewModel = viewModel(),
    fileListViewModel: FileListViewModel
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentScreen by navigationViewModel.currentScreen.collectAsState()
    val navigationStack by navigationViewModel.navigationStack.collectAsState()
    val fileListState by fileListViewModel.state.collectAsState()
    val context = LocalContext.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                MainMenu(
                    navigationViewModel = navigationViewModel,
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                BreadcrumbBar(
                    screens = navigationStack,
                    locations = fileListState.breadcrumbs,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onScreenClick = { screen ->
                        navigationViewModel.navigateToRoot(screen)
                    },
                    onLocationClick = { location ->
                        fileListViewModel.onEvent(FileListEvent.LocationChanged(location), context)
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    is Screen.FileList, Screen.LocalFiles, Screen.EncryptedContainers, Screen.CloudStorage -> {
                        FileListScreenContent(
                            viewModel = fileListViewModel,
                            context = context
                        )
                    }
                    is Screen.Settings -> SettingsScreen()
                    is Screen.ProgramSettings -> ProgramSettingsScreen()
                    is Screen.OpeningOptions -> OpeningOptionsScreen()
                    is Screen.EncryptionSettings -> EncryptionSettingsScreen()
                    is Screen.VersionHistory -> VersionHistoryScreen()
                    is Screen.About -> AboutScreen()
                    is Screen.Help -> HelpScreen()
                    is Screen.DebugLog -> DebugLogScreen()
                }
            }
        }
    }
}

@Composable
fun FileListScreenContent(
    viewModel: FileListViewModel,
    context: android.content.Context
) {
    val state by viewModel.state.collectAsState()
    com.sovworks.eds.android.filemanager.ui.FileListContent(
        state = state,
        onItemClick = { record: com.sovworks.eds.android.filemanager.records.BrowserRecord ->
            viewModel.onEvent(FileListEvent.ItemClicked(record), context)
        },
        onItemLongClick = { record: com.sovworks.eds.android.filemanager.records.BrowserRecord ->
            viewModel.onEvent(FileListEvent.ItemSelected(record), context)
        }
    )
}
