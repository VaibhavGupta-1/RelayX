package com.example.relayx.ui.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.relayx.data.model.FileType
import com.example.relayx.data.model.Transfer
import com.example.relayx.data.model.TransferStatus
import com.example.relayx.ui.theme.*
import com.example.relayx.ui.viewmodel.DownloadProgress
import com.example.relayx.utils.FileUtils

private const val TAG = "RelayXDebug"

/**
 * Redesigned clean, modern transfer card.
 */
@Composable
fun TransferItem(
    transfer: Transfer,
    effectiveProgress: DownloadProgress,
    currentUserCode: String,
    onDownload: (Transfer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSender = transfer.senderCode == currentUserCode
    val fileType = remember(transfer.fileName) { FileType.fromFileName(transfer.fileName) }
    val effectiveStatus = effectiveProgress.status
    val statusConfig = remember(effectiveStatus, isSender) { getStatusConfig(effectiveStatus, isSender) }
    val isClickable = effectiveStatus == TransferStatus.SENT || effectiveStatus == TransferStatus.DOWNLOADED

    val targetProgress = if (effectiveStatus == TransferStatus.DOWNLOADING) {
        effectiveProgress.progress / 100f
    } else {
        transfer.progress / 100f
    }

    // Progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 600),
        label = "progress"
    )

    // Pulse animation for UPLOADING
    val isUploading = effectiveStatus == TransferStatus.UPLOADING
    val pulseAlpha = if (isUploading) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        alpha
    } else {
        1f
    }

    // Card background color: mostly surface, very subtle tint on fail
    val cardBackground = if (effectiveStatus == TransferStatus.FAILED) {
        StatusFailed.copy(alpha = 0.04f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isClickable) {
                    Modifier.clickable {
                        Log.d(TAG, "TransferItem: card clicked → opening file: ${transfer.fileUrl}")
                        FileUtils.openFile(context, transfer.fileUrl)
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Header Row ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon (Circular)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(statusConfig.color.copy(alpha = 0.1f))
                        .alpha(pulseAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getDisplayIcon(fileType),
                        contentDescription = "File Type",
                        tint = statusConfig.color,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName.ifBlank { "Unknown file" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isSender) "To: ${transfer.receiverCode} • ${getFileTypeLabel(fileType)}"
                               else "From: ${transfer.senderCode} • ${getFileTypeLabel(fileType)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusConfig.color.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusConfig.color,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // ─── Image Preview ──────────────────────────────────────────
            val showPreview = fileType == FileType.IMAGE &&
                    (effectiveStatus == TransferStatus.SENT || effectiveStatus == TransferStatus.DOWNLOADED) &&
                    transfer.fileUrl.isNotBlank()

            AnimatedVisibility(
                visible = showPreview,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(transfer.fileUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = transfer.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            // ─── Progress & Status Text ─────────────────────────────────
            when (effectiveStatus) {
                TransferStatus.UPLOADING -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isSender) "Uploading..." else "Receiving...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${transfer.progress}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = statusConfig.color
                            )
                        }
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = statusConfig.color,
                            trackColor = statusConfig.color.copy(alpha = 0.1f),
                        )
                    }
                }

                TransferStatus.DOWNLOADING -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = StatusDownloading,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Downloading...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = StatusDownloading
                                )
                            }
                            Text(
                                text = "${effectiveProgress.progress}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusDownloading
                            )
                        }
                        if (effectiveProgress.progress > 0) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = StatusDownloading,
                                trackColor = StatusDownloading.copy(alpha = 0.1f),
                            )
                        }
                    }
                }

                TransferStatus.FAILED -> {
                    Text(
                        text = "❌ Transfer failed",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = StatusFailed
                    )
                }

                TransferStatus.SENT -> {
                    Text(
                        text = if (isSender) "Available for download" else "Ready to download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TransferStatus.DOWNLOADED -> {
                    Text(
                        text = "✅ Saved to Downloads",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = StatusDownloaded
                    )
                }
            }

            // ─── Action Buttons ─────────────────────────────────────────

            AnimatedVisibility(
                visible = effectiveStatus == TransferStatus.SENT || 
                          effectiveStatus == TransferStatus.DOWNLOADED || 
                          (effectiveStatus == TransferStatus.FAILED && transfer.fileUrl.isNotBlank()),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (effectiveStatus == TransferStatus.SENT || effectiveStatus == TransferStatus.FAILED) {
                        // Download Button (Secondary - Outlined)
                        OutlinedButton(
                            onClick = {
                                Log.d(TAG, "TransferItem: Download clicked → ${transfer.fileName}")
                                onDownload(transfer)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (effectiveStatus == TransferStatus.FAILED) StatusFailed else Primary
                            )
                        ) {
                            Icon(
                                imageVector = if (effectiveStatus == TransferStatus.FAILED) Icons.Outlined.Refresh else Icons.Outlined.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (effectiveStatus == TransferStatus.FAILED) "Retry" else "Download",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (effectiveStatus == TransferStatus.SENT || effectiveStatus == TransferStatus.DOWNLOADED) {
                        // Open File Button (Primary - Filled)
                        Button(
                            onClick = {
                                Log.d(TAG, "TransferItem: Open clicked → ${transfer.fileUrl}")
                                FileUtils.openFile(context, transfer.fileUrl)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (effectiveStatus == TransferStatus.DOWNLOADED) StatusDownloaded else Primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = "Open",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open File",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Helper Functions ───────────────────────────────────────────────────

private data class StatusConfig(
    val color: Color,
    val label: String
)

private fun getStatusConfig(status: TransferStatus, isSender: Boolean): StatusConfig {
    return when (status) {
        TransferStatus.UPLOADING -> StatusConfig(StatusUploading, if (isSender) "Uploading" else "Receiving")
        TransferStatus.SENT -> StatusConfig(StatusSent, if (isSender) "Sent" else "Received")
        TransferStatus.DOWNLOADING -> StatusConfig(StatusDownloading, "Downloading")
        TransferStatus.DOWNLOADED -> StatusConfig(StatusDownloaded, "Saved")
        TransferStatus.FAILED -> StatusConfig(StatusFailed, "Failed")
    }
}

private fun getDisplayIcon(fileType: FileType): ImageVector {
    return when (fileType) {
        FileType.IMAGE -> Icons.Outlined.Image
        FileType.VIDEO -> Icons.Outlined.VideoFile
        FileType.PDF -> Icons.Outlined.PictureAsPdf
        FileType.AUDIO -> Icons.Outlined.AudioFile
        FileType.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

private fun getFileTypeLabel(fileType: FileType): String {
    return when (fileType) {
        FileType.IMAGE -> "Image"
        FileType.VIDEO -> "Video"
        FileType.PDF -> "PDF"
        FileType.AUDIO -> "Audio"
        FileType.OTHER -> "File"
    }
}
