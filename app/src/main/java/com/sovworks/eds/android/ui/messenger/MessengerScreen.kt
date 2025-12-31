package com.sovworks.eds.android.ui.messenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovworks.eds.android.trust.TrustStore

@Composable
fun MessengerScreen(
    peerId: String? = null,
    groupId: String? = null,
    relayOnly: Boolean = false,
    viewModel: MessengerViewModel = viewModel()
) {
    var relayOnlyState by remember { mutableStateOf(relayOnly) }

    LaunchedEffect(peerId, groupId, relayOnly) {
        relayOnlyState = relayOnly
        if (groupId != null) {
            viewModel.setChat(groupId, isGroupChat = true)
        } else {
            viewModel.setChat(peerId, isGroupChat = false, relayOnly = relayOnly)
        }
    }

    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val trustStore = remember { TrustStore.getInstance(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Header
        Surface(tonalElevation = 2.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val headerText = when {
                    groupId != null -> "Group: $groupId"
                    peerId != null -> {
                        val trusted = trustStore.getKey(peerId)
                        val display = trusted?.name?.takeIf { it.isNotBlank() }
                            ?: trusted?.peerId?.takeIf { it.isNotBlank() }
                            ?: peerId
                        if (relayOnlyState) {
                            "Chat with $display (Relay)"
                        } else {
                            "Chat with $display"
                        }
                    }
                    else -> "Messenger"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (peerId != null && groupId == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Relay",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = relayOnlyState,
                                onCheckedChange = { enabled ->
                                    relayOnlyState = enabled
                                    viewModel.setChat(
                                        peerId,
                                        isGroupChat = false,
                                        relayOnly = enabled
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }
        }

        // Input Area
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isMe) Alignment.End else Alignment.Start
    val color = if (message.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (message.isMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = color,
            shape = shape
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = if (message.isMe) "Me" else message.senderId.take(8),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.outline
        )
    }
}
