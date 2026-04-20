package com.example.relayx.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.relayx.ui.components.UserCodeDisplay
import com.example.relayx.ui.theme.Primary
import com.example.relayx.ui.viewmodel.MainUiState
import com.example.relayx.ui.viewmodel.TransferUiState
import com.example.relayx.utils.FileUtils

/**
 * Main home screen displaying the user's code, file selection, and send controls.
 *
 * UI flow:
 * 1. User sees their 6-character code at the top (share with others)
 * 2. User enters receiver code in the text field
 * 3. User taps "Select File" to pick a file via ActivityResultContracts.GetContent
 * 4. User taps "Send File" to initiate the transfer
 * 5. LinearProgressIndicator shows upload progress
 * 6. Snackbar shows success / error messages
 * 7. History icon navigates to TransferScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainUiState: MainUiState,
    transferUiState: TransferUiState,
    onReceiverCodeChanged: (String) -> Unit,
    onFilesSelected: (List<Pair<Uri, String>>) -> Unit,
    onSendFiles: () -> Unit,
    onClearError: () -> Unit,
    onClearSendSuccess: () -> Unit,
    onNavigateToTransfers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // File picker launcher using ActivityResultContracts (Multiple files)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val files = uris.map { uri ->
                val fileName = FileUtils.getFileName(context, uri)
                Log.d("RelayXDebug", "UI: File selected: $fileName")
                uri to fileName
            }
            onFilesSelected(files)
        }
    }

    // Snackbar host for success and error messages
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success Snackbar
    LaunchedEffect(transferUiState.sendSuccess) {
        if (transferUiState.sendSuccess) {
            snackbarHostState.showSnackbar(
                message = "✅ File sent successfully!",
                duration = SnackbarDuration.Short
            )
            onClearSendSuccess()
        }
    }

    // Show error Snackbar (from either MainViewModel or TransferViewModel)
    val currentError = transferUiState.error ?: mainUiState.error
    LaunchedEffect(currentError) {
        currentError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long,
                actionLabel = "Dismiss"
            )
            onClearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "RelayX",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Transfer count badge on history icon
                    BadgedBox(
                        badge = {
                            if (transferUiState.transfers.isNotEmpty()) {
                                Badge(containerColor = Primary) {
                                    Text("${transferUiState.transfers.size}")
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = onNavigateToTransfers) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "Transfer History"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ─── Loading State ──────────────────────────────────────
            if (mainUiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Initializing...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ─── User Code Display ──────────────────────────────────
            AnimatedVisibility(
                visible = mainUiState.userCode.isNotEmpty(),
                enter = fadeIn() + slideInVertically()
            ) {
                UserCodeDisplay(
                    code = mainUiState.userCode,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (mainUiState.userCode.isNotEmpty()) {
                Text(
                    text = "Share this code so others can send you files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ─── Divider ────────────────────────────────────────────
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 32.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ─── Send Section ───────────────────────────────────────
            Text(
                text = "Send a File",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Receiver code input (disabled during upload)
            OutlinedTextField(
                value = transferUiState.receiverCode,
                onValueChange = onReceiverCodeChanged,
                label = { Text("Receiver Code") },
                placeholder = { Text("e.g. A3B7K2") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    cursorColor = Primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // File selection button (disabled during upload)
            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (transferUiState.selectedFiles.isNotEmpty())
                        "${transferUiState.selectedFiles.size} file(s) selected"
                    else
                        "Select Files",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Send button
            Button(
                onClick = {
                    Log.d("RelayXDebug", "UI: Send button clicked")
                    Log.d("RelayXDebug", "UI: receiverCode=${transferUiState.receiverCode}, filesCount=${transferUiState.selectedFiles.size}")
                    focusManager.clearFocus()
                    onSendFiles()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = transferUiState.receiverCode.length == 6 &&
                        transferUiState.selectedFiles.isNotEmpty() &&
                        mainUiState.userCode.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary
                )
            ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Send File${if (transferUiState.selectedFiles.size > 1) "s" else ""}",
                        fontWeight = FontWeight.SemiBold
                    )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
