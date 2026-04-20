package com.example.relayx.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.relayx.data.model.Transfer
import com.example.relayx.data.model.TransferStatus
import com.example.relayx.ui.components.TransferItem
import com.example.relayx.ui.theme.*
import com.example.relayx.ui.viewmodel.DownloadProgress

/**
 * Modern TransferScreen with premium neutral styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    currentUserCode: String,
    transfers: List<Transfer>,
    downloadStates: Map<String, DownloadProgress>,
    onDownload: (Transfer) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Count active (uploading + downloading) transfers for badge
    val activeCount = remember(transfers, downloadStates) {
        transfers.count { transfer ->
            val effective = downloadStates[transfer.id]?.status
                ?: TransferStatus.fromValue(transfer.status)
            effective == TransferStatus.UPLOADING || effective == TransferStatus.DOWNLOADING
        }
    }

    // Modern background color
    // If the system is in dark mode, MaterialTheme maps background differently,
    // so we provide a slight hint if Light mode vs Dark mode using custom colors.
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val modernBackground = if (isDark) Color(0xFF0A0A12) else Color(0xFFF6F7FB)

    Scaffold(
        containerColor = modernBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Transfers",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        // Show active count badge (clean chip style)
                        if (activeCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = StatusUploading,
                                contentColor = TextOnPrimary
                            ) {
                                Text(
                                    text = "$activeCount Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = modernBackground,
                    scrolledContainerColor = modernBackground
                )
            )
        }
    ) { innerPadding ->
        if (transfers.isEmpty()) {
            // ─── Empty State ────────────────────────────────────────
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 48.dp)
                ) {
                    Text(
                        text = "📭",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No transfers yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Files sent to your code will appear here securely and in real-time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // ─── Transfer List ──────────────────────────────────────
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary header
                item(key = "header") {
                    Text(
                        text = "${transfers.size} Transfer${if (transfers.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                items(
                    items = transfers,
                    key = { it.id }
                ) { transfer ->
                    val effectiveProgress = downloadStates[transfer.id]
                        ?: DownloadProgress(TransferStatus.fromValue(transfer.status), 0)

                    TransferItem(
                        transfer = transfer,
                        effectiveProgress = effectiveProgress,
                        currentUserCode = currentUserCode,
                        onDownload = onDownload,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
