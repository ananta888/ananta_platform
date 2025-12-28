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
import androidx.compose.ui.unit.sp
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.android.navigation.Screen

@Composable
fun MainMenu(
    navigationViewModel: NavigationViewModel,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Hauptmenü",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        MenuSection(title = "DATEIMANAGER")
        MenuItem(text = "Lokale Dateien", onClick = {
            navigationViewModel.navigateToRoot(Screen.LocalFiles)
            onCloseDrawer()
        })
        MenuItem(text = "Verschlüsselte Container", onClick = {
            navigationViewModel.navigateToRoot(Screen.EncryptedContainers)
            onCloseDrawer()
        })
        MenuItem(text = "Cloud-Speicher", onClick = {
            navigationViewModel.navigateToRoot(Screen.CloudStorage)
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
        MenuItem(text = "Versionshistorie", onClick = {
            navigationViewModel.navigateToRoot(Screen.VersionHistory)
            onCloseDrawer()
        })
        MenuItem(text = "Über die App", onClick = {
            navigationViewModel.navigateToRoot(Screen.About)
            onCloseDrawer()
        })
        MenuItem(text = "Hilfe", onClick = {
            navigationViewModel.navigateToRoot(Screen.Help)
            onCloseDrawer()
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
