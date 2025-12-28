package com.sovworks.eds.android.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.sovworks.eds.android.filemanager.viewmodel.FileListViewModel
import com.sovworks.eds.android.navigation.Screen
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.android.ui.theme.AppTheme
import kotlin.jvm.JvmOverloads

fun ComponentActivity.setAppContent() {
    val fileListViewModel = ViewModelProvider(this)[FileListViewModel::class.java]
    val navigationViewModel = ViewModelProvider(this)[NavigationViewModel::class.java]
    
    setContent {
        AppTheme {
            AppScaffold(
                navigationViewModel = navigationViewModel,
                fileListViewModel = fileListViewModel
            )
        }
    }
}

@JvmOverloads
fun ComponentActivity.setSettingsContent(
    screen: Screen,
    onStartIdentityScanner: (() -> Unit)? = null,
    onStartPairingScanner: (() -> Unit)? = null
) {
    val navigationViewModel = ViewModelProvider(this)[NavigationViewModel::class.java]
    navigationViewModel.navigateToRoot(screen)
    
    // Wir brauchen hier auch ein FileListViewModel, da AppScaffold es erwartet.
    // In einer reinen Settings-Activity ist das suboptimal, aber für die Konsistenz des AppScaffold okay.
    val fileListViewModel = ViewModelProvider(this)[FileListViewModel::class.java]

    setContent {
        AppTheme {
            AppScaffold(
                navigationViewModel = navigationViewModel,
                fileListViewModel = fileListViewModel,
                onStartIdentityScanner = onStartIdentityScanner,
                onStartPairingScanner = onStartPairingScanner
            )
        }
    }
}
