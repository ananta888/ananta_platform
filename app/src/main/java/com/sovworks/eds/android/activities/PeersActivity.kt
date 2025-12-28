package com.sovworks.eds.android.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sovworks.eds.android.R
import com.sovworks.eds.android.navigation.Screen
import com.sovworks.eds.android.ui.setSettingsContent

class PeersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSettingsContent(Screen.Peers)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.peers_devices)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No peers found yet.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBackPressed) {
                Text("Back")
            }
        }
    }
}
