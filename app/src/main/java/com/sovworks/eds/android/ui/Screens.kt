package com.sovworks.eds.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    PlaceholderScreen("Einstellungen")
}

@Composable
fun ProgramSettingsScreen() {
    PlaceholderScreen("Allgemeine Einstellungen")
}

@Composable
fun OpeningOptionsScreen() {
    PlaceholderScreen("Öffnungsoptionen")
}

@Composable
fun EncryptionSettingsScreen() {
    PlaceholderScreen("Verschlüsselungseinstellungen")
}

@Composable
fun VersionHistoryScreen() {
    PlaceholderScreen("Versionshistorie")
}

@Composable
fun AboutScreen() {
    PlaceholderScreen("Über die App")
}

@Composable
fun HelpScreen() {
    PlaceholderScreen("Hilfe")
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
