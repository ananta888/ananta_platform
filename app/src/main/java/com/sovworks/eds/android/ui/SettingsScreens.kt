package com.sovworks.eds.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sovworks.eds.settings.Settings
import com.sovworks.eds.android.settings.UserSettings

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun SettingToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun SettingClickItem(
    title: String,
    description: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun GeneralSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { UserSettings.getSettings(context) }
    val scrollState = rememberScrollState()

    var showPreviews by remember { mutableStateOf(settings.showPreviews()) }
    var neverSaveHistory by remember { mutableStateOf(settings.neverSaveHistory()) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        SettingsGroup(title = "Anzeige") {
            SettingToggleItem(
                title = "Vorschaubilder anzeigen",
                description = "Miniaturansichten für Bilder und Videos generieren",
                checked = showPreviews,
                onCheckedChange = {
                    showPreviews = it
                    settings.sharedPreferences.edit().putBoolean("show_previews", it).apply()
                }
            )
        }

        SettingsGroup(title = "Sicherheit") {
            SettingToggleItem(
                title = "Verlauf nie speichern",
                description = "Keine Liste der zuletzt geöffneten Dateien führen",
                checked = neverSaveHistory,
                onCheckedChange = {
                    neverSaveHistory = it
                    settings.sharedPreferences.edit().putBoolean("never_save_history", it).apply()
                }
            )
        }
    }
}

@Composable
fun EncryptionSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { UserSettings.getSettings(context) }
    
    var is2FAEnabled by remember { mutableStateOf(settings.is2FAEnabled) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroup(title = "Verschlüsselung") {
            SettingToggleItem(
                title = "Zwei-Faktor-Authentifizierung (2FA)",
                description = "Zusätzliche Sicherheitsebene für den Zugriff",
                checked = is2FAEnabled,
                onCheckedChange = {
                    is2FAEnabled = it
                    settings.is2FAEnabled = it
                }
            )
        }
    }
}

@Composable
fun ConnectionSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { UserSettings.getSettings(context) }
    
    var signalingMode by remember { mutableStateOf(settings.signalingMode) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroup(title = "Signaling") {
            Text(
                text = "Signaling Modus",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            // Hier könnte ein Dropdown oder RadioButton-Gruppe hin
            listOf("WebSocket", "HTTP", "P2P").forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        signalingMode = mode
                        settings.sharedPreferences.edit().putString("signaling_mode", mode).apply()
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = (signalingMode == mode), onClick = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(mode)
                }
            }
        }
    }
}

@Composable
fun HelpScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Hilfe & Dokumentation", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Willkommen bei der Ananta Platform. Diese App ermöglicht es Ihnen, Dateien sicher zu speichern und dezentral auszutauschen.")
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingClickItem(
            title = "Online Dokumentation",
            description = "Besuchen Sie unsere Website für detaillierte Anleitungen",
            icon = Icons.Default.Help,
            onClick = { /* In einer echten App: URL öffnen */ }
        )
        
        SettingClickItem(
            title = "Häufig gestellte Fragen (FAQ)",
            description = "Antworten auf die meistgestellten Fragen",
            icon = Icons.Default.QuestionAnswer,
            onClick = { /* URL öffnen */ }
        )
    }
}

@Composable
fun VersionHistoryScreen() {
    val history = """
        Version 1.0.0-draft (Aktuell)
        - Einführung der Ananta Platform Integration
        - WebRTC-basiertes Peer-to-Peer Signaling
        - Neues Jetpack Compose Frontend Design
        - Dezentraler Dateiaustausch (Exchange)
        - Trust-Ranking System für Peers
        
        Version 0.9.5 (EDS Lite)
        - Grundlegende Dateimanager-Funktionen
        - Unterstützung für verschlüsselte Container
    """.trimIndent()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Versionshistorie", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(history, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ananta Platform", style = MaterialTheme.typography.headlineLarge)
        Text("Version 1.0.0-draft", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Dezentraler WebRTC-Dateiaustausch", style = MaterialTheme.typography.bodyLarge)
        Text("© 2025 Sovworks", style = MaterialTheme.typography.labelSmall)
    }
}
