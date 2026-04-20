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
import com.example.relayx.data.local.DownloadStateStore
import com.example.relayx.services.UploadWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DownloadProgress(
    val status: TransferStatus,
    val progress: Int = 0 // Download percentage 0-100
)

private const val TAG = "RelayXDebug"

/**
 * UI state for the transfer screen and home screen send controls.
 *
 * This is a single source of truth for all transfer-related UI state.
 * The ViewModel ensures this state is always consistent and thread-safe.
 */
data class TransferUiState(
    /** List of transfer records from Firestore (real-time). Both incoming and outgoing! */
    val transfers: List<Transfer> = emptyList(),
    /** Local download state overrides (transferId → DownloadProgress). UI-only, not persisted. */
    val downloadStates: Map<String, DownloadProgress> = emptyMap(),
    /** List of selected files to send (Pairs of Uri and file name) */
    val selectedFiles: List<Pair<Uri, String>> = emptyList(),
    /** Current error message to display, null if no error */
    val error: String? = null,
    /** Receiver code typed by the user */
    val receiverCode: String = "",
    /** Momentary flag to show success snackbar for enqueueing files */
    val sendSuccess: Boolean = false
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
    private val _downloadStates = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadProgress>> = _downloadStates.asStateFlow()

    // ─── Combined UI state ──────────────────────────────────────────────
    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    /**
     * Maps DownloadManager download IDs to transfer IDs natively using a thread-safe map.
     * Persistence logic syncs this to DataStore.
     */
    private val pendingDownloads = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private val downloadStateStore: DownloadStateStore

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
                viewModelScope.launch {
                    downloadStateStore.removePendingDownload(downloadId)
                }
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
    private var pollerJob: Job? = null
    private var receiverRegistered = false

    init {
        downloadStateStore = (application as com.example.relayx.RelayXApplication).container.downloadStateStore

        // 1. Combine Firestore transfers + local download states
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

        // 2. Restore pending downloads from DataStore
        viewModelScope.launch {
            downloadStateStore.getPendingDownloads().collect { restoredMap ->
                pendingDownloads.putAll(restoredMap)
                Log.d(TAG, "ViewModel: Restored ${pendingDownloads.size} active downloads from memory.")
            }
        }

        // 3. Start download progress poller
        startDownloadPoller()

        // 4. Register the download completion BroadcastReceiver
        registerDownloadReceiver()
    }

    /**
     * Polls active downloads from DownloadManager and emits progress.
     */
    private fun startDownloadPoller() {
        pollerJob = viewModelScope.launch {
            while (isActive) {
                if (pendingDownloads.isNotEmpty()) {
                    val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query()
                    query.setFilterById(*pendingDownloads.keys.toLongArray())
                    
                    val cursor = downloadManager.query(query)
                    val newDownloadStates = _downloadStates.value.toMutableMap()

                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            do {
                                val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val downloadedSOFarIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                val totalSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                
                                if (idIndex != -1 && statusIndex != -1 && downloadedSOFarIndex != -1 && totalSizeIndex != -1) {
                                    val id = cursor.getLong(idIndex)
                                    val status = cursor.getInt(statusIndex)
                                    val downloadedSoFar = cursor.getLong(downloadedSOFarIndex)
                                    val totalSize = cursor.getLong(totalSizeIndex)

                                    val transferId = pendingDownloads[id]
                                    if (transferId != null && status == DownloadManager.STATUS_RUNNING) {
                                        val progress = if (totalSize > 0) ((downloadedSoFar * 100) / totalSize).toInt() else 0
                                        newDownloadStates[transferId] = DownloadProgress(TransferStatus.DOWNLOADING, progress)
                                    }
                                }
                            } while (cursor.moveToNext())
                        }
                        cursor.close()
                    }
                    _downloadStates.value = newDownloadStates
                }
                delay(1000)
            }
        }
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
     * Initiates a file download and tracks its state via DataStore.
     */
    fun startDownload(transfer: Transfer) {
        Log.d(TAG, "ViewModel: startDownload() transferId=${transfer.id}, fileName=${transfer.fileName}")

        // Set DOWNLOADING state immediately for UI feedback
        _downloadStates.value = _downloadStates.value + (transfer.id to DownloadProgress(TransferStatus.DOWNLOADING, 0))

        val context = getApplication<Application>()
        val downloadId = FileUtils.downloadFile(context, transfer.fileUrl, transfer.fileName)

        if (downloadId == -1L) {
            Log.e(TAG, "ViewModel: download enqueue failed for ${transfer.id}")
            onDownloadFailed(transfer.id)
        } else {
            pendingDownloads[downloadId] = transfer.id
            viewModelScope.launch {
                downloadStateStore.savePendingDownload(downloadId, transfer.id)
            }
            Log.d(TAG, "ViewModel: tracking downloadId=$downloadId → transferId=${transfer.id}")
        }
    }

    /**
     * Called when a download completes successfully.
     */
    fun onDownloadComplete(transferId: String) {
        Log.d(TAG, "ViewModel: onDownloadComplete() transferId=$transferId")
        _downloadStates.value = _downloadStates.value + (transferId to DownloadProgress(TransferStatus.DOWNLOADED, 100))
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
    fun getEffectiveDownloadProgress(transfer: Transfer): DownloadProgress {
        return _downloadStates.value[transfer.id]
            ?: DownloadProgress(TransferStatus.fromValue(transfer.status), 0)
    }

    // ─── Sender-Side Controls ───────────────────────────────────────────

    fun onReceiverCodeChanged(code: String) {
        val filtered = code.uppercase().filter { it.isLetterOrDigit() }.take(6)
        _uiState.value = _uiState.value.copy(receiverCode = filtered)
    }

    fun onFilesSelected(files: List<Pair<Uri, String>>) {
        Log.d(TAG, "ViewModel: onFilesSelected() → count=${files.size}")
        _uiState.value = _uiState.value.copy(
            selectedFiles = files,
            error = null
        )
    }

    fun sendFiles(senderCode: String) {
        val state = _uiState.value
        val files = state.selectedFiles
        val receiverCode = state.receiverCode

        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "ViewModel: sendFiles() called")
        Log.d(TAG, "  Sender code: $senderCode")
        Log.d(TAG, "  Receiver code: $receiverCode")
        Log.d(TAG, "  Files selected: ${files.size}")

        if (files.isEmpty()) {
            _uiState.value = state.copy(error = "Please select at least one file.")
            return
        }
        if (receiverCode.isBlank() || receiverCode.length != 6) {
            _uiState.value = state.copy(error = "Receiver code must be exactly 6 characters.")
            return
        }
        if (senderCode == receiverCode) {
            _uiState.value = state.copy(error = "You cannot send files to yourself.")
            return
        }

        val workManager = WorkManager.getInstance(getApplication())

        // Enqueue a WorkManager task for each file
        files.forEach { (uri, fileName) ->
            Log.d(TAG, "ViewModel: Enqueueing UploadWorker for $fileName")
            val workData = workDataOf(
                "fileUri" to uri.toString(),
                "senderCode" to senderCode,
                "receiverCode" to receiverCode,
                "fileName" to fileName
            )
            val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workData)
                .build()

            workManager.enqueue(uploadWorkRequest)
        }

        // Show success, clear inputs
        _uiState.value = _uiState.value.copy(
            sendSuccess = true,
            selectedFiles = emptyList(),
            receiverCode = ""
        )
        Log.d(TAG, "ViewModel: All file uploads enqueued.")
        Log.d(TAG, "═══════════════════════════════════════════════")
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
        pollerJob?.cancel()
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
