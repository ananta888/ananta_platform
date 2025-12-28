package com.sovworks.eds.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovworks.eds.android.network.SearchManager
import com.sovworks.eds.android.network.SearchResponse
import com.sovworks.eds.android.network.SharedFile

class ExchangeViewModel : ViewModel() {
    var query by mutableStateOf("")
    var minTrustLevel by mutableStateOf(0.0)
    var results = mutableStateListOf<Pair<String, SharedFile>>()
    var isSearching by mutableStateOf(false)

    private var searchManager: SearchManager? = null

    private val searchListener: (SearchResponse) -> Unit = { response ->
        isSearching = false
        results.addAll(response.results.map { response.peerId to it })
    }

    fun init(manager: SearchManager) {
        if (searchManager == null) {
            searchManager = manager
            manager.addSearchListener(searchListener)
        }
    }

    fun performSearch() {
        if (query.isNotBlank()) {
            results.clear()
            isSearching = true
            searchManager?.search(query)
        }
    }

    fun setTrustLevel(level: Double) {
        minTrustLevel = level
        searchManager?.setMinTrustLevel(level)
    }

    fun downloadFile(peerId: String, fileName: String) {
        searchManager?.requestFile(peerId, fileName)
    }

    override fun onCleared() {
        searchManager?.removeSearchListener(searchListener)
        super.onCleared()
    }
}

@Composable
fun ExchangeScreen(
    viewModel: ExchangeViewModel = viewModel()
) {
    val context = LocalContext.current
    val searchManager = remember { SearchManager.getInstance(context) }
    
    LaunchedEffect(Unit) {
        viewModel.init(searchManager)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = viewModel.query,
            onValueChange = { viewModel.query = it },
            label = { Text("Dateien suchen") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { viewModel.performSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = "Suchen")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Minimales Trust Level: ${"%.1f".format(viewModel.minTrustLevel)}")
        Slider(
            value = viewModel.minTrustLevel.toFloat(),
            onValueChange = { viewModel.setTrustLevel(it.toDouble()) },
            valueRange = 0f..1.0f,
            steps = 10
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(viewModel.results) { (peerId, file) ->
                ListItem(
                    headlineContent = { Text(file.name) },
                    supportingContent = { Text("Peer: ${peerId.take(8)}... | Größe: ${file.size} Bytes") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.downloadFile(peerId, file.name) }) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    }
                )
            }
        }
    }
}
