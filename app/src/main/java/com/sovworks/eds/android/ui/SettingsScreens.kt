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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sovworks.eds.settings.Settings
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import com.sovworks.eds.android.network.WebRtcService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

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
    val scope = rememberCoroutineScope()

    var signalingMode by remember { mutableStateOf(settings.signalingMode) }
    var signalingUrl by remember { mutableStateOf(settings.signalingServerUrl) }
    var testResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var testInProgress by remember { mutableStateOf(false) }
    var publicVisibility by remember {
        mutableStateOf(settings.signalingPublicVisibility == UserSettingsCommon.SIGNALING_VISIBILITY_PUBLIC)
    }
    var autoConnectPublic by remember { mutableStateOf(settings.isAutoConnectPublicPeers) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroup(title = "Signaling") {
            Text(
                text = "Signaling Modus",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            val modes = listOf(
                UserSettingsCommon.SIGNALING_MODE_WEBSOCKET to "WebSocket",
                UserSettingsCommon.SIGNALING_MODE_HTTP to "HTTP",
                UserSettingsCommon.SIGNALING_MODE_LOCAL to "P2P (Local)"
            )
            modes.forEach { (modeKey, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        signalingMode = modeKey
                        settings.sharedPreferences.edit()
                            .putString(UserSettingsCommon.SIGNALING_MODE, modeKey)
                            .apply()
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = (signalingMode == modeKey), onClick = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(label)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = signalingUrl,
                onValueChange = {
                    signalingUrl = it
                    val cleaned = it.trim()
                    val editor = settings.sharedPreferences.edit()
                    if (cleaned.isEmpty()) {
                        editor.remove(UserSettingsCommon.SIGNALING_SERVER_URL)
                    } else {
                        editor.putString(UserSettingsCommon.SIGNALING_SERVER_URL, cleaned)
                    }
                    editor.apply()
                },
                label = { Text("Signaling-Server URL") },
                isError = signalingUrl.isNotBlank() && !isValidSignalingUrl(signalingUrl),
                supportingText = {
                    val trimmed = signalingUrl.trim()
                    when {
                        trimmed.isEmpty() -> Text("Mehrere URLs mit ; trennen")
                        !isValidSignalingUrl(trimmed) ->
                            Text("Ungueltige URL. Erlaubt: ws/wss/http/https")
                        else -> Text("Mehrere URLs mit ; trennen")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        val url = signalingUrl.trim()
                        testResults = emptyList()
                        testInProgress = true
                        scope.launch {
                            val result = testSignalingUrls(url)
                            testResults = result
                            testInProgress = false
                        }
                    },
                    enabled = signalingUrl.isNotBlank() &&
                        isValidSignalingUrl(signalingUrl) &&
                        !testInProgress
                ) {
                    Text(if (testInProgress) "Teste..." else "Verbindung testen")
                }
                if (testInProgress) {
                    Text(
                        text = "Teste...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (testResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    testResults.forEach { (url, status) ->
                        Text(
                            text = "$url: $status",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == "OK") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                    val failed = testResults.filter { it.second != "OK" }.map { it.first }
                    if (failed.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                testResults = emptyList()
                                testInProgress = true
                                scope.launch {
                                    val result = testSignalingUrls(failed)
                                    testResults = result
                                    testInProgress = false
                                }
                            },
                            enabled = !testInProgress
                        ) {
                            Text("Fehlgeschlagene erneut testen")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingToggleItem(
                title = "Public Key sichtbar",
                description = "Erlaubt dem Signaling-Server, deinen Public Key als oeffentlich zu listen",
                checked = publicVisibility,
                onCheckedChange = { enabled ->
                    publicVisibility = enabled
                    val value = if (enabled) {
                        UserSettingsCommon.SIGNALING_VISIBILITY_PUBLIC
                    } else {
                        UserSettingsCommon.SIGNALING_VISIBILITY_PRIVATE
                    }
                    settings.sharedPreferences.edit()
                        .putString(UserSettingsCommon.SIGNALING_PUBLIC_VISIBILITY, value)
                        .apply()
                    scope.launch(Dispatchers.IO) {
                        WebRtcService.initialize(context.applicationContext, settings)
                    }
                }
            )
            SettingToggleItem(
                title = "Auto-Connect Public Peers",
                description = "Verbindet automatisch mit oeffentlichen Peers",
                checked = autoConnectPublic,
                onCheckedChange = { enabled ->
                    autoConnectPublic = enabled
                    settings.sharedPreferences.edit()
                        .putBoolean(UserSettingsCommon.SIGNALING_AUTO_CONNECT_PUBLIC, enabled)
                        .apply()
                }
            )
        }
    }
}

private fun isValidSignalingUrl(url: String): Boolean {
    val entries = parseSignalingUrls(url)
    if (entries.isEmpty()) return false
    return entries.all { entry ->
        try {
            val uri = URI(entry)
            val scheme = uri.scheme?.lowercase()
            val allowed = setOf("ws", "wss", "http", "https")
            scheme in allowed && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}

private fun parseSignalingUrls(urls: String): List<String> {
    return urls.split(";").map { it.trim() }.filter { it.isNotEmpty() }
}

private suspend fun testSignalingUrls(urls: String): List<Pair<String, String>> {
    val entries = parseSignalingUrls(urls)
    return testSignalingUrls(entries)
}

private suspend fun testSignalingUrls(entries: List<String>): List<Pair<String, String>> {
    return withContext(Dispatchers.IO) {
        entries.map { entry ->
            async { entry to testSignalingUrl(entry) }
        }.awaitAll()
    }
}

private suspend fun testSignalingUrl(url: String): String {
    return try {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme == "ws" || scheme == "wss") {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val result = kotlinx.coroutines.CompletableDeferred<String>()
            val ws = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    result.complete("OK")
                    webSocket.close(1000, "ok")
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    result.complete("Fehler")
                }
            })
            val status = withTimeoutOrNull(5000) { result.await() } ?: "Timeout"
            ws.cancel()
            status
        } else {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val response = client.newCall(Request.Builder().url(url).head().build()).execute()
            if (response.isSuccessful) "OK" else "Fehler"
        }
    } catch (_: Exception) {
        "Fehler"
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
