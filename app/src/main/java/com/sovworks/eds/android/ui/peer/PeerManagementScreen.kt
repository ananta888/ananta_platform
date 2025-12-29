package com.sovworks.eds.android.ui.peer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.trust.TrustedKey
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerManagementScreen(viewModel: PeerViewModel) {
    val peers by viewModel.peers.collectAsState()
    val context = LocalContext.current
    val identity = remember { IdentityManager.loadIdentity(context) }
    val selectedTab = remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Trust Network") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab.value) {
                Tab(
                    selected = selectedTab.value == 0,
                    onClick = { selectedTab.value = 0 },
                    text = { Text("Peers") }
                )
                Tab(
                    selected = selectedTab.value == 1,
                    onClick = { selectedTab.value = 1 },
                    text = { Text("Web of Trust") }
                )
            }

            when (selectedTab.value) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(peers) { peer ->
                            PeerItem(
                                peer = peer,
                                onTrustLevelChange = { level ->
                                    viewModel.updateTrustLevel(peer.fingerprint, level)
                                },
                                onDelete = { viewModel.removePeer(peer.fingerprint) },
                                onAliasChange = { alias -> viewModel.updateAlias(peer.fingerprint, alias) }
                            )
                        }
                    }
                }
                1 -> {
                    TrustGraphSection(
                        peers = peers,
                        selfFingerprint = identity?.getFingerprint(),
                        selfLabel = identity?.id ?: "Me"
                    )
                }
            }
        }
    }
}

private data class TrustGraphNode(
    val id: String,
    val label: String,
    val alias: String?,
    val trustLevel: Int,
    val status: TrustedKey.TrustStatus?,
    val isSelf: Boolean
)

private data class TrustGraphEdge(
    val from: String,
    val to: String,
    val trustLevel: Int
)

@Composable
private fun TrustGraphSection(
    peers: List<TrustedKey>,
    selfFingerprint: String?,
    selfLabel: String
) {
    val (nodes, edges) = remember(peers, selfFingerprint, selfLabel) {
        buildGraphData(peers, selfFingerprint, selfLabel)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Trust-Verbindungen und Empfehlungen",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Knoten = Peers, Linien = Empfehlungen",
            style = MaterialTheme.typography.bodySmall
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            TrustGraphCanvas(nodes = nodes, edges = edges)
        }

        TrustLegend()
    }
}

@Composable
private fun TrustGraphCanvas(
    nodes: List<TrustGraphNode>,
    edges: List<TrustGraphEdge>
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.DKGRAY
            textSize = 28f
        }
    }
    val badgeTextPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 22f
        }
    }

    val trustedColor = Color(0xFF2E7D32)
    val pendingColor = Color(0xFFF9A825)
    val distrustColor = Color(0xFFC62828)
    val neutralColor = MaterialTheme.colorScheme.primary
    val badgeColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        if (nodes.isEmpty()) {
            drawIntoCanvas { canvas ->
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                canvas.nativeCanvas.drawText(
                    "Noch keine Peers vorhanden",
                    size.width / 2f,
                    size.height / 2f,
                    labelPaint
                )
            }
            return@Canvas
        }

        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = min(size.width, size.height)
        val baseRadius = minDim * 0.18f
        val ringSpacing = minDim * 0.14f

        val selfNode = nodes.firstOrNull { it.isSelf }
        val others = nodes.filterNot { it.isSelf }
            .sortedByDescending { it.trustLevel }

        val nodePositions = mutableMapOf<String, Offset>()
        if (selfNode != null) {
            nodePositions[selfNode.id] = center
        }

        val ringSize = 10
        val rings = others.chunked(ringSize)
        rings.forEachIndexed { ringIndex, ringNodes ->
            val radius = baseRadius + ringIndex * ringSpacing
            ringNodes.forEachIndexed { index, node ->
                val angle = (2 * PI * index / ringNodes.size) - (PI / 2)
                val x = center.x + (radius * cos(angle)).toFloat()
                val y = center.y + (radius * sin(angle)).toFloat()
                nodePositions[node.id] = Offset(x, y)
            }
        }

        edges.forEach { edge ->
            val from = nodePositions[edge.from] ?: return@forEach
            val to = nodePositions[edge.to] ?: return@forEach
            val alpha = 0.2f + (edge.trustLevel.coerceIn(1, 5) / 5f) * 0.5f
            drawLine(
                color = Color(0xFF546E7A).copy(alpha = alpha),
                start = from,
                end = to,
                strokeWidth = 3f
            )
        }

        nodes.forEach { node ->
            val position = nodePositions[node.id] ?: return@forEach
            val radius = if (node.isSelf) 18f else 10f + (node.trustLevel * 2f)
            val nodeColor = when (node.status) {
                TrustedKey.TrustStatus.TRUSTED -> trustedColor
                TrustedKey.TrustStatus.DISTRUSTED -> distrustColor
                TrustedKey.TrustStatus.PENDING -> pendingColor
                null -> neutralColor
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(nodeColor, nodeColor.copy(alpha = 0.6f)),
                    center = position,
                    radius = radius * 1.6f
                ),
                radius = radius,
                center = position
            )

            drawIntoCanvas { canvas ->
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                canvas.nativeCanvas.drawText(
                    node.label,
                    position.x,
                    position.y - radius - 8f,
                    labelPaint
                )
            }

            val alias = node.alias
            if (!alias.isNullOrBlank()) {
                val textWidth = badgeTextPaint.measureText(alias)
                val padding = 8f
                val badgeHeight = badgeTextPaint.textSize + 8f
                val top = position.y + radius + 6f
                val left = position.x - (textWidth / 2f) - padding
                val width = textWidth + padding * 2f
                val textY = top + (badgeHeight / 2f) - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f

                drawRoundRect(
                    color = badgeColor.copy(alpha = 0.9f),
                    topLeft = Offset(left, top),
                    size = Size(width, badgeHeight),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                drawIntoCanvas { canvas ->
                    badgeTextPaint.textAlign = android.graphics.Paint.Align.CENTER
                    canvas.nativeCanvas.drawText(
                        alias,
                        position.x,
                        textY,
                        badgeTextPaint
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LegendItem(color = Color(0xFF2E7D32), label = "Trusted")
        LegendItem(color = Color(0xFFF9A825), label = "Pending")
        LegendItem(color = Color(0xFFC62828), label = "Distrusted")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .padding(end = 6.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
    }
}

private fun buildGraphData(
    peers: List<TrustedKey>,
    selfFingerprint: String?,
    selfLabel: String
): Pair<List<TrustGraphNode>, List<TrustGraphEdge>> {
    val nodes = mutableListOf<TrustGraphNode>()
    val edges = mutableListOf<TrustGraphEdge>()

    if (selfFingerprint != null) {
        nodes.add(
            TrustGraphNode(
                id = selfFingerprint,
                label = selfLabel,
                alias = null,
                trustLevel = 5,
                status = null,
                isSelf = true
            )
        )
    }

    peers.forEach { peer ->
        val alias = peer.name?.takeIf { it.isNotBlank() }
        val label = peer.fingerprint.take(6)
        nodes.add(
            TrustGraphNode(
                id = peer.fingerprint,
                label = label,
                alias = alias,
                trustLevel = peer.trustLevel,
                status = peer.status,
                isSelf = false
            )
        )

        peer.recommendations.forEach { rec ->
            edges.add(
                TrustGraphEdge(
                    from = rec.recommenderFingerprint,
                    to = peer.fingerprint,
                    trustLevel = rec.trustLevel
                )
            )
        }
    }

    val nodeIds = nodes.map { it.id }.toSet()
    val filteredEdges = edges.filter { nodeIds.contains(it.from) && nodeIds.contains(it.to) }
    return nodes to filteredEdges
}

@Composable
fun PeerItem(
    peer: TrustedKey,
    onTrustLevelChange: (Int) -> Unit,
    onDelete: () -> Unit,
    onAliasChange: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val showKeyDialog = remember { mutableStateOf(false) }
    val showAliasDialog = remember { mutableStateOf(false) }
    val aliasDraft = remember { mutableStateOf(peer.name ?: "") }
    val aliasIsBlank = aliasDraft.value.trim().isEmpty()
    val alias = peer.name?.takeIf { it.isNotBlank() } ?: "Unknown Peer"
    val peerId = peer.peerId?.takeIf { it.isNotBlank() } ?: "Unknown ID"
    val publicKey = peer.publicKey ?: ""
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = alias, style = MaterialTheme.typography.titleMedium)
                    Text(text = "Peer ID: $peerId", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Public Key: ${publicKey.take(16)}...", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Status: ${peer.status}", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAliasDialog.value = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Alias")
                    }
                    IconButton(onClick = {
                        if (publicKey.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(publicKey))
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Public Key")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Trust: ", style = MaterialTheme.typography.bodyMedium)
                (1..5).forEach { level ->
                    IconButton(onClick = { onTrustLevelChange(level) }) {
                        Icon(
                            imageVector = if (level <= peer.trustLevel) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star $level",
                            tint = if (level <= peer.trustLevel) Color(0xFFFFD700) else Color.Gray
                        )
                    }
                }
            }

            TextButton(onClick = { showKeyDialog.value = true }) {
                Text("Show Public Key")
            }
        }
    }

    if (showKeyDialog.value) {
        AlertDialog(
            onDismissRequest = { showKeyDialog.value = false },
            title = { Text("Public Key") },
            text = {
                Text(text = if (publicKey.isNotBlank()) publicKey else "No public key available")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (publicKey.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(publicKey))
                        }
                        showKeyDialog.value = false
                    }
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog.value = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAliasDialog.value) {
        AlertDialog(
            onDismissRequest = { showAliasDialog.value = false },
            title = { Text("Edit Alias") },
            text = {
                OutlinedTextField(
                    value = aliasDraft.value,
                    onValueChange = { aliasDraft.value = it },
                    label = { Text("Alias") },
                    isError = aliasIsBlank,
                    supportingText = {
                        if (aliasIsBlank) {
                            Text("Alias is required")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = aliasDraft.value.trim()
                    onAliasChange(cleaned)
                    showAliasDialog.value = false
                }, enabled = !aliasIsBlank) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
