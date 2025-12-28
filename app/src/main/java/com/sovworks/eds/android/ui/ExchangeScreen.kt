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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovworks.eds.android.network.SearchManager
import com.sovworks.eds.android.network.SearchResponse
import com.sovworks.eds.android.network.SharedFile

data class SearchResultItem(
    val peerId: String,
    val file: SharedFile,
    val trustRank: Double?
)

class ExchangeViewModel : ViewModel() {
    var query by mutableStateOf("")
    var minTrustLevel by mutableStateOf(0.0)
    var fileTypesInput by mutableStateOf("")
    var minSizeKbInput by mutableStateOf("")
    var maxSizeKbInput by mutableStateOf("")
    var results = mutableStateListOf<SearchResultItem>()
    var isSearching by mutableStateOf(false)

    private var searchManager: SearchManager? = null

    private val searchListener: (SearchResponse) -> Unit = { response ->
        isSearching = false
        val trustRank = response.trustRank
        results.addAll(response.results.map { SearchResultItem(response.peerId, it, trustRank) })
        results.sortByDescending { it.trustRank ?: 0.0 }
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
            searchManager?.search(
                query = query,
                fileTypes = parseFileTypes(fileTypesInput),
                minSizeBytes = parseSizeKb(minSizeKbInput),
                maxSizeBytes = parseSizeKb(maxSizeKbInput)
            )
        }
    }

    fun setTrustLevel(level: Double) {
        minTrustLevel = level
        searchManager?.setMinTrustLevel(level)
    }

    fun downloadFile(peerId: String, fileName: String) {
        searchManager?.requestFile(peerId, fileName)
    }

    private fun parseFileTypes(input: String): List<String> {
        return input.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseSizeKb(input: String): Long? {
        val value = input.trim().toLongOrNull() ?: return null
        if (value <= 0) return null
        return value * 1024L
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

        OutlinedTextField(
            value = viewModel.fileTypesInput,
            onValueChange = { viewModel.fileTypesInput = it },
            label = { Text("Dateitypen (z.B. pdf,jpg)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = viewModel.minSizeKbInput,
                onValueChange = { viewModel.minSizeKbInput = it },
                label = { Text("Min. Groesse (KB)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = viewModel.maxSizeKbInput,
                onValueChange = { viewModel.maxSizeKbInput = it },
                label = { Text("Max. Groesse (KB)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

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
            items(viewModel.results) { item ->
                val trustDisplay = item.trustRank?.let { " | Trust: ${"%.2f".format(it)}" } ?: ""
                ListItem(
                    headlineContent = { Text(item.file.name) },
                    supportingContent = {
                        Text("Peer: ${item.peerId.take(8)}... | Groesse: ${item.file.size} Bytes$trustDisplay")
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.downloadFile(item.peerId, item.file.name) }) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    }
                )
            }
        }
    }
}
