package com.sovworks.eds.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.android.navigation.Screen

@Composable
fun MainMenu(
    navigationViewModel: NavigationViewModel,
    onCloseDrawer: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Ananta Platform",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        MenuSection(title = "DATEIMANAGER")
        MenuItem(text = "Lokale Dateien", onClick = {
            navigationViewModel.navigateToRoot(Screen.LocalFiles)
            onCloseDrawer()
        })
        MenuItem(text = "Tresore", onClick = {
            navigationViewModel.navigateToRoot(Screen.Vaults)
            onCloseDrawer()
        })
        MenuItem(text = "Cloud-Speicher", onClick = {
            navigationViewModel.navigateToRoot(Screen.CloudStorage)
            onCloseDrawer()
        })

        Spacer(modifier = Modifier.height(16.dp))
        MenuSection(title = "PLATFORM")
        MenuItem(text = "Exchange", onClick = {
            navigationViewModel.navigateToRoot(Screen.Exchange)
            onCloseDrawer()
        })
        MenuItem(text = "Trust Network", onClick = {
            navigationViewModel.navigateToRoot(Screen.Trust)
            onCloseDrawer()
        })
        MenuItem(text = "Identity Sync", onClick = {
            navigationViewModel.navigateToRoot(Screen.IdentitySync)
            onCloseDrawer()
        })
        MenuItem(text = "Device Pairing", onClick = {
            navigationViewModel.navigateToRoot(Screen.Pairing)
            onCloseDrawer()
        })
        MenuItem(text = "Peers & Devices", onClick = {
            navigationViewModel.navigateToRoot(Screen.Peers)
            onCloseDrawer()
        })

        Spacer(modifier = Modifier.height(16.dp))
        MenuSection(title = "EINSTELLUNGEN")
        MenuItem(text = "Allgemeine Einstellungen", onClick = {
            navigationViewModel.navigateToRoot(Screen.ProgramSettings)
            onCloseDrawer()
        })
        MenuItem(text = "Öffnungsoptionen", onClick = {
            navigationViewModel.navigateToRoot(Screen.OpeningOptions)
            onCloseDrawer()
        })
        MenuItem(text = "Verschlüsselungseinstellungen", onClick = {
            navigationViewModel.navigateToRoot(Screen.EncryptionSettings)
            onCloseDrawer()
        })

        Spacer(modifier = Modifier.height(16.dp))
        MenuSection(title = "EXTRAS")
        MenuItem(text = "File Transfers", onClick = {
            navigationViewModel.navigateToRoot(Screen.FileTransfers)
            onCloseDrawer()
        })
        MenuItem(text = "Connected Devices", onClick = {
            navigationViewModel.navigateToRoot(Screen.ConnectedDevices)
            onCloseDrawer()
        })
        MenuItem(text = "Server Peers", onClick = {
            navigationViewModel.navigateToRoot(Screen.ServerPeers)
            onCloseDrawer()
        })
        MenuItem(text = "Peer Connections", onClick = {
            navigationViewModel.navigateToRoot(Screen.PeerConnections)
            onCloseDrawer()
        })
        MenuItem(text = "Debug Log", onClick = {
            navigationViewModel.navigateToRoot(Screen.DebugLog)
            onCloseDrawer()
        })
        MenuItem(text = "Versionshistorie", onClick = {
            navigationViewModel.navigateToRoot(Screen.VersionHistory)
            onCloseDrawer()
        })
        MenuItem(text = "Hilfe", onClick = {
            navigationViewModel.navigateToRoot(Screen.Help)
            onCloseDrawer()
        })
        MenuItem(text = "Über die App", onClick = {
            navigationViewModel.navigateToRoot(Screen.About)
            onCloseDrawer()
        })

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider()
        MenuItem(text = "Beenden", onClick = {
            onCloseDrawer()
            val lm = com.sovworks.eds.locations.LocationsManagerBase.getLocationsManager(context)
            lm.closeAllLocations(true, true)
            // Beenden der App
            (context as? android.app.Activity)?.finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        })
    }
}

@Composable
fun MenuSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    )
}
