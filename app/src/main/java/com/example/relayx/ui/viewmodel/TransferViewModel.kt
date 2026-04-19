package com.example.relayx.ui.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.relayx.data.model.Transfer
import com.example.relayx.data.model.TransferStatus
import com.example.relayx.domain.usecase.ObserveTransfersUseCase
import com.example.relayx.domain.usecase.SendFileUseCase
import com.example.relayx.utils.FileUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "RelayXDebug"

/**
 * UI state for the transfer screen and home screen send controls.
 *
 * This is a single source of truth for all transfer-related UI state.
 * The ViewModel ensures this state is always consistent and thread-safe.
 */
data class TransferUiState(
    /** List of transfer records from Firestore (real-time) */
    val transfers: List<Transfer> = emptyList(),
    /** Local download state overrides (transferId → status). UI-only, not persisted. */
    val downloadStates: Map<String, TransferStatus> = emptyMap(),
    /** True while a file send operation is in progress */
    val isSending: Boolean = false,
    /** Upload progress percentage (0–100), exposed via StateFlow */
    val progress: Int = 0,
    /** True momentarily after a successful send (for Snackbar trigger) */
    val sendSuccess: Boolean = false,
    /** Current error message to display, null if no error */
    val error: String? = null,
    /** URI of the file selected via the file picker */
    val selectedFileUri: Uri? = null,
    /** Display name of the selected file */
    val selectedFileName: String = "",
    /** Receiver code typed by the user */
    val receiverCode: String = ""
)

/**
 * ViewModel for handling file transfers (both sending and receiving).
 *
 * Extends AndroidViewModel because it needs Application context for:
 * - ContentResolver (URI → InputStream conversion for sending)
 * - BroadcastReceiver registration (DownloadManager completion tracking)
 *
 * Download state management:
 * - Maintains a downloadStates map: transferId → TransferStatus (DOWNLOADING/DOWNLOADED/FAILED)
 * - These states are UI-only — never persisted to Firestore
 * - Combined with Firestore transfers to produce the effective status shown in UI
 * - Tracks active downloadIds via pendingDownloads map for BroadcastReceiver matching
 */
class TransferViewModel(
    application: Application,
    private val sendFileUseCase: SendFileUseCase,
    private val observeTransfersUseCase: ObserveTransfersUseCase
) : AndroidViewModel(application) {

    // ─── Firestore transfers (real-time from snapshot listener) ──────────
    private val _firestoreTransfers = MutableStateFlow<List<Transfer>>(emptyList())

    // ─── Local download states (UI-only, not persisted) ─────────────────
    private val _downloadStates = MutableStateFlow<Map<String, TransferStatus>>(emptyMap())
    val downloadStates: StateFlow<Map<String, TransferStatus>> = _downloadStates.asStateFlow()

    // ─── Combined UI state ──────────────────────────────────────────────
    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    /**
     * Maps DownloadManager download IDs to transfer IDs.
     * Used by the BroadcastReceiver to update the correct transfer's state.
     */
    private val pendingDownloads = mutableMapOf<Long, String>()

    /**
     * BroadcastReceiver for DownloadManager.ACTION_DOWNLOAD_COMPLETE.
     * Auto-updates download state when a file finishes downloading.
     */
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            Log.d(TAG, "ViewModel: download complete broadcast, downloadId=$downloadId")

            val transferId = pendingDownloads.remove(downloadId)
            if (transferId != null) {
                val success = FileUtils.isDownloadSuccessful(context, downloadId)
                if (success) {
                    Log.d(TAG, "ViewModel: ✅ download SUCCESS for transferId=$transferId")
                    onDownloadComplete(transferId)
                } else {
                    Log.e(TAG, "ViewModel: ❌ download FAILED for transferId=$transferId")
                    onDownloadFailed(transferId)
                }
            }
        }
    }

    private var listenerJob: Job? = null
    private var receiverRegistered = false

    init {
        // Combine Firestore transfers + download states into the unified UI state
        viewModelScope.launch {
            combine(_firestoreTransfers, _downloadStates) { transfers, downloads ->
                transfers to downloads
            }.collect { (transfers, downloads) ->
                _uiState.value = _uiState.value.copy(
                    transfers = transfers,
                    downloadStates = downloads
                )
            }
        }

        // Register the download completion BroadcastReceiver
        registerDownloadReceiver()
    }

    /**
     * Registers the BroadcastReceiver for download completion events.
     */
    private fun registerDownloadReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(downloadReceiver, filter)
        }
        receiverRegistered = true
        Log.d(TAG, "ViewModel: download BroadcastReceiver registered")
    }

    // ─── Firestore Listener ─────────────────────────────────────────────

    /**
     * Starts observing incoming transfers for the given user code.
     */
    fun startListening(userCode: String) {
        Log.d(TAG, "ViewModel: startListening() → userCode=$userCode")
        if (listenerJob?.isActive == true) {
            Log.d(TAG, "ViewModel: cancelling previous listener job")
            listenerJob?.cancel()
        }

        listenerJob = viewModelScope.launch {
            observeTransfersUseCase(userCode)
                .catch { e ->
                    Log.e(TAG, "ViewModel: transfer listener ERROR: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to listen for transfers: ${e.message}"
                    )
                }
                .collect { transfers ->
                    Log.d(TAG, "ViewModel: received ${transfers.size} transfers from listener")
                    _firestoreTransfers.value = transfers
                }
        }
    }

    // ─── Download State Management ──────────────────────────────────────

    /**
     * Initiates a file download and tracks its state.
     *
     * Flow:
     * 1. Sets transferId → DOWNLOADING in downloadStates
     * 2. Calls FileUtils.downloadFile() which returns a downloadId
     * 3. Maps downloadId → transferId in pendingDownloads
     * 4. BroadcastReceiver fires onDownloadComplete/onDownloadFailed when done
     *
     * @param transfer The transfer to download.
     */
    fun startDownload(transfer: Transfer) {
        Log.d(TAG, "ViewModel: startDownload() transferId=${transfer.id}, fileName=${transfer.fileName}")

        // Set DOWNLOADING state immediately for UI feedback
        _downloadStates.value = _downloadStates.value + (transfer.id to TransferStatus.DOWNLOADING)

        val context = getApplication<Application>()
        val downloadId = FileUtils.downloadFile(context, transfer.fileUrl, transfer.fileName)

        if (downloadId == -1L) {
            // Enqueue failed — mark as FAILED immediately
            Log.e(TAG, "ViewModel: download enqueue failed for ${transfer.id}")
            onDownloadFailed(transfer.id)
        } else {
            // Track the download ID so BroadcastReceiver can match it
            pendingDownloads[downloadId] = transfer.id
            Log.d(TAG, "ViewModel: tracking downloadId=$downloadId → transferId=${transfer.id}")
        }
    }

    /**
     * Called when a download completes successfully.
     * Updates the UI-only download state to DOWNLOADED.
     */
    fun onDownloadComplete(transferId: String) {
        Log.d(TAG, "ViewModel: onDownloadComplete() transferId=$transferId")
        _downloadStates.value = _downloadStates.value + (transferId to TransferStatus.DOWNLOADED)
    }

    /**
     * Called when a download fails.
     * Resets the download state so the user sees the original SENT status with retry option.
     */
    fun onDownloadFailed(transferId: String) {
        Log.e(TAG, "ViewModel: onDownloadFailed() transferId=$transferId")
        // Remove from download states — reverts to Firestore status (SENT)
        // so the user can retry
        _downloadStates.value = _downloadStates.value - transferId
        _uiState.value = _uiState.value.copy(
            error = "Download failed. Please try again."
        )
    }

    /**
     * Returns the effective status for a transfer, considering local download overrides.
     * Priority: downloadStates override > Firestore status
     */
    fun getEffectiveStatus(transfer: Transfer): TransferStatus {
        return _downloadStates.value[transfer.id]
            ?: TransferStatus.fromValue(transfer.status)
    }

    // ─── Sender-Side Controls ───────────────────────────────────────────

    fun onReceiverCodeChanged(code: String) {
        val filtered = code.uppercase().filter { it.isLetterOrDigit() }.take(6)
        _uiState.value = _uiState.value.copy(receiverCode = filtered)
    }

    fun onFileSelected(uri: Uri, fileName: String) {
        Log.d(TAG, "ViewModel: onFileSelected()")
        Log.d(TAG, "  File selected: $fileName")
        Log.d(TAG, "  File URI: $uri")
        _uiState.value = _uiState.value.copy(
            selectedFileUri = uri,
            selectedFileName = fileName,
            error = null
        )
    }

    fun sendFile(senderCode: String) {
        val state = _uiState.value
        val fileUri = state.selectedFileUri
        val fileName = state.selectedFileName
        val receiverCode = state.receiverCode

        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "ViewModel: sendFile() called")
        Log.d(TAG, "  Sender code: $senderCode")
        Log.d(TAG, "  Receiver code: $receiverCode")
        Log.d(TAG, "  File name: $fileName")
        Log.d(TAG, "  File URI: $fileUri")

        if (fileUri == null || fileName.isBlank()) {
            Log.e(TAG, "ViewModel: validation failed — no file selected")
            _uiState.value = state.copy(error = "Please select a file first.")
            return
        }
        if (receiverCode.isBlank()) {
            Log.e(TAG, "ViewModel: validation failed — receiver code blank")
            _uiState.value = state.copy(error = "Please enter a receiver code.")
            return
        }
        if (receiverCode.length != 6) {
            Log.e(TAG, "ViewModel: validation failed — receiver code length=${receiverCode.length}")
            _uiState.value = state.copy(error = "Receiver code must be exactly 6 characters.")
            return
        }
        if (senderCode == receiverCode) {
            Log.e(TAG, "ViewModel: validation failed — self-send")
            _uiState.value = state.copy(error = "You cannot send a file to yourself.")
            return
        }

        Log.d(TAG, "ViewModel: validation passed ✅")

        val contentResolver = getApplication<Application>().contentResolver
        Log.d(TAG, "ViewModel: opening InputStream from URI...")

        val inputStream = try {
            contentResolver.openInputStream(fileUri)
        } catch (e: Exception) {
            Log.e(TAG, "ViewModel: openInputStream() FAILED: ${e.message}", e)
            _uiState.value = state.copy(error = "Could not open file: ${e.message}")
            return
        }

        Log.d(TAG, "ViewModel: InputStream opened: ${inputStream != null}")

        if (inputStream == null) {
            Log.e(TAG, "ViewModel: InputStream is NULL — cannot proceed")
            _uiState.value = state.copy(error = "Could not open file stream.")
            return
        }

        try {
            val fileDescriptor = contentResolver.openFileDescriptor(fileUri, "r")
            val fileSize = fileDescriptor?.statSize ?: -1
            Log.d(TAG, "ViewModel: file size = ${if (fileSize > 0) "$fileSize bytes (${fileSize / 1024} KB)" else "unknown"}")
            fileDescriptor?.close()
        } catch (e: Exception) {
            Log.d(TAG, "ViewModel: could not read file size: ${e.message}")
        }

        Log.d(TAG, "ViewModel: launching send coroutine...")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSending = true,
                progress = 0,
                error = null,
                sendSuccess = false
            )

            Log.d(TAG, "ViewModel: calling sendFileUseCase()...")
            val result = sendFileUseCase(
                senderCode = senderCode,
                receiverCode = receiverCode,
                fileName = fileName,
                inputStream = inputStream
            )

            result.fold(
                onSuccess = { transfer ->
                    Log.d(TAG, "ViewModel: ✅ sendFile SUCCESS")
                    Log.d(TAG, "  Transfer ID: ${transfer.id}")
                    Log.d(TAG, "  File URL: ${transfer.fileUrl}")
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        progress = 100,
                        sendSuccess = true,
                        selectedFileUri = null,
                        selectedFileName = "",
                        receiverCode = ""
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "ViewModel: ❌ sendFile FAILED: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        progress = 0,
                        error = e.message ?: "Failed to send file. Please try again."
                    )
                }
            )
            Log.d(TAG, "═══════════════════════════════════════════════")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSendSuccess() {
        _uiState.value = _uiState.value.copy(sendSuccess = false)
    }

    override fun onCleared() {
        Log.d(TAG, "ViewModel: onCleared() — cleaning up")
        super.onCleared()
        listenerJob?.cancel()
        if (receiverRegistered) {
            try {
                getApplication<Application>().unregisterReceiver(downloadReceiver)
                Log.d(TAG, "ViewModel: download BroadcastReceiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "ViewModel: failed to unregister receiver: ${e.message}")
            }
            receiverRegistered = false
        }
    }
}
