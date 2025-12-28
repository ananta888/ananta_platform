package com.sovworks.eds.android.navigation

import androidx.lifecycle.ViewModel
import com.sovworks.eds.settings.GlobalConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class Screen(val title: String) {
    object FileList : Screen("Dateimanager")
    object LocalFiles : Screen("Lokale Dateien")
    object EncryptedContainers : Screen("Verschlüsselte Container")
    object CloudStorage : Screen("Cloud-Speicher")
    object Settings : Screen("Einstellungen")
    object ProgramSettings : Screen("Allgemeine Einstellungen")
    object OpeningOptions : Screen("Öffnungsoptionen")
    object EncryptionSettings : Screen("Verschlüsselungseinstellungen")
    object VersionHistory : Screen("Versionshistorie")
    object About : Screen("Über die App")
    object Help : Screen("Hilfe")
    object DebugLog : Screen("Debug Log")
    object FileTransfers : Screen("File Transfers")
    object PeerConnections : Screen("Peer Connections")
    data class Messenger(val peerId: String? = null, val groupId: String? = null) : Screen("Messenger")
}

class NavigationViewModel : ViewModel() {
    private val startScreen = Screen.DebugLog
    
    private val _currentScreen = MutableStateFlow<Screen>(startScreen)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _navigationStack = MutableStateFlow<List<Screen>>(listOf(startScreen))
    val navigationStack: StateFlow<List<Screen>> = _navigationStack.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        _navigationStack.value = _navigationStack.value + screen
    }

    fun navigateBack() {
        if (_navigationStack.value.size > 1) {
            val newStack = _navigationStack.value.dropLast(1)
            _navigationStack.value = newStack
            _currentScreen.value = newStack.last()
        }
    }

    fun navigateToRoot(screen: Screen) {
        _navigationStack.value = listOf(screen)
        _currentScreen.value = screen
    }
}
