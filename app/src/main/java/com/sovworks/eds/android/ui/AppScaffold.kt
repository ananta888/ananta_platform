package com.sovworks.eds.android.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovworks.eds.android.filemanager.viewmodel.FileListViewModel
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.android.navigation.Screen
import com.sovworks.eds.android.filemanager.ui.BreadcrumbBar
import com.sovworks.eds.android.filemanager.viewmodel.FileListEvent
import com.sovworks.eds.android.locations.ContainerBasedLocation
import com.sovworks.eds.android.locations.activities.CreateLocationActivity
import com.sovworks.eds.android.locations.activities.OpenLocationsActivity
import com.sovworks.eds.android.ui.transfers.FileTransferDashboardScreen
import com.sovworks.eds.android.ui.peer.ConnectedPeersScreen
import com.sovworks.eds.android.ui.peer.PeerConnectionsScreen
import com.sovworks.eds.android.ui.peer.PeerManagementScreen
import com.sovworks.eds.android.ui.peer.ServerPeersScreen
import com.sovworks.eds.android.ui.peer.PeerViewModel
import com.sovworks.eds.android.ui.messenger.MessengerScreen
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.LocationsManagerBase
import kotlinx.coroutines.launch

@Composable
fun AppScaffold(
    navigationViewModel: NavigationViewModel = viewModel(),
    fileListViewModel: FileListViewModel,
    onStartIdentityScanner: (() -> Unit)? = null,
    onStartPairingScanner: (() -> Unit)? = null
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentScreen by navigationViewModel.currentScreen.collectAsState()
    val navigationStack by navigationViewModel.navigationStack.collectAsState()
    val fileListState by fileListViewModel.state.collectAsState()
    val context = LocalContext.current
    val pendingLocation = remember { mutableStateOf<Location?>(null) }
    val openLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val location = pendingLocation.value
        if (result.resultCode == Activity.RESULT_OK && location != null) {
            fileListViewModel.onEvent(FileListEvent.LocationChanged(location), context)
            navigationViewModel.navigateTo(Screen.FileList)
        }
        pendingLocation.value = null
    }
    val showCreateDialog = remember { mutableStateOf(false) }
    val createName = remember { mutableStateOf("") }

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
            },
            floatingActionButton = {
                when (currentScreen) {
                    is Screen.FileList -> {
                        if (fileListState.location != null) {
                            FloatingActionButton(onClick = { showCreateDialog.value = true }) {
                                Text("+")
                            }
                        }
                    }
                    is Screen.Vaults -> {
                        FloatingActionButton(
                            onClick = {
                                val intent = Intent(context, CreateLocationActivity::class.java)
                                intent.putExtra(
                                    CreateLocationActivity.EXTRA_LOCATION_TYPE,
                                    ContainerBasedLocation.URI_SCHEME
                                )
                                context.startActivity(intent)
                            }
                        ) {
                            Text("+")
                        }
                    }
                    else -> Unit
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    is Screen.FileList -> {
                        FileListScreenContent(
                            viewModel = fileListViewModel,
                            context = context
                        )
                    }
                    is Screen.LocalFiles -> {
                        val lm = LocationsManagerBase.getLocationsManager(context)
                        val locations = lm.getLoadedLocations(true).filter { it !is com.sovworks.eds.locations.EDSLocation }
                        LocationListScreen(
                            locations = locations,
                            onLocationClick = { location ->
                                pendingLocation.value = location
                                val intent = Intent(context, OpenLocationsActivity::class.java)
                                LocationsManager.storeLocationsInIntent(intent, listOf(location))
                                openLocationLauncher.launch(intent)
                            }
                        )
                    }
                    is Screen.Vaults -> {
                        val lm = LocationsManagerBase.getLocationsManager(context)
                        val locations = lm.getLoadedEDSLocations(true)
                        LocationListScreen(
                            locations = locations,
                            onLocationClick = { location ->
                                pendingLocation.value = location
                                val intent = Intent(context, OpenLocationsActivity::class.java)
                                LocationsManager.storeLocationsInIntent(intent, listOf(location))
                                openLocationLauncher.launch(intent)
                            }
                        )
                    }
                    is Screen.CloudStorage -> {
                        val lm = LocationsManagerBase.getLocationsManager(context)
                        // Cloud storage often uses DocumentTreeLocation in this app
                        val locations = lm.getLoadedLocations(true).filter { it is com.sovworks.eds.android.locations.DocumentTreeLocation }
                        LocationListScreen(
                            locations = locations,
                            onLocationClick = { location ->
                                pendingLocation.value = location
                                val intent = Intent(context, OpenLocationsActivity::class.java)
                                LocationsManager.storeLocationsInIntent(intent, listOf(location))
                                openLocationLauncher.launch(intent)
                            }
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
                    is Screen.FileTransfers -> FileTransferDashboardScreen()
                    is Screen.ConnectedDevices -> ConnectedPeersScreen()
                    is Screen.ServerPeers -> ServerPeersScreen()
                    is Screen.PeerConnections -> PeerConnectionsScreen()
                    is Screen.IdentitySync -> IdentitySyncScreen(onStartScanner = { onStartIdentityScanner?.invoke() })
                    is Screen.Pairing -> PairingScreen(
                        onStartScanner = { onStartPairingScanner?.invoke() },
                        onOpenIdentitySync = { navigationViewModel.navigateTo(Screen.IdentitySync) }
                    )
                    is Screen.Peers -> PeerManagementScreen(viewModel = viewModel())
                    is Screen.Exchange -> ExchangeScreen()
                    is Screen.Trust -> PeerManagementScreen(viewModel = viewModel())
                    is Screen.Messenger -> {
                        val screen = currentScreen as Screen.Messenger
                        MessengerScreen(peerId = screen.peerId, groupId = screen.groupId)
                    }
                }
            }
            if (showCreateDialog.value && fileListState.location != null) {
                val isEncrypted = fileListState.location?.isEncrypted == true
                AlertDialog(
                    onDismissRequest = {
                        showCreateDialog.value = false
                        createName.value = ""
                    },
                    title = { Text("Create") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = createName.value,
                                onValueChange = { createName.value = it },
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!isEncrypted) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "This location is not encrypted. Create a vault for encrypted files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Row {
                            TextButton(onClick = {
                                fileListViewModel.createEntry(
                                    context,
                                    createName.value,
                                    com.sovworks.eds.android.filemanager.tasks.CreateNewFileBase.FILE_TYPE_FILE
                                )
                                showCreateDialog.value = false
                                createName.value = ""
                            }) {
                                Text("Create File")
                            }
                            TextButton(onClick = {
                                fileListViewModel.createEntry(
                                    context,
                                    createName.value,
                                    com.sovworks.eds.android.filemanager.tasks.CreateNewFileBase.FILE_TYPE_FOLDER
                                )
                                showCreateDialog.value = false
                                createName.value = ""
                            }) {
                                Text("Create Folder")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showCreateDialog.value = false
                            createName.value = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
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
