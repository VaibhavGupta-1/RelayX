package com.example.relayx.domain.repository

import com.example.relayx.data.model.Transfer
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Domain-layer contract for transfer operations.
 *
 * This interface decouples the domain layer from Android framework types.
 * The ViewModel handles URI-to-InputStream conversion before calling sendFile.
 */
interface TransferRepository {

    /**
     * Uploads a file to Supabase and creates a transfer record in Firestore.
     *
     * @param senderCode The sender's unique 6-character code.
     * @param receiverCode The receiver's unique 6-character code.
     * @param fileName The display name of the file.
     * @param inputStream The file data as an InputStream. Caller is responsible for providing it;
     *                    the repository will close it via inputStream.use {}.
     * @return Result containing the created Transfer on success.
     */
    suspend fun sendFile(
        senderCode: String,
        receiverCode: String,
        fileName: String,
        inputStream: InputStream
    ): Result<Transfer>

    /**
     * Observes incoming transfers for a given receiver code in real-time.
     * Uses Firestore snapshot listener via callbackFlow.
     */
    fun observeIncomingTransfers(receiverCode: String): Flow<List<Transfer>>

    /**
     * Updates the status and progress of a transfer.
     */
    suspend fun updateTransferStatus(
        transferId: String,
        status: String,
        progress: Int
    ): Result<Unit>
}
