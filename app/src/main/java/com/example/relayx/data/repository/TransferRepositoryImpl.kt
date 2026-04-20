package com.example.relayx.data.repository

import android.util.Log
import com.example.relayx.data.model.Transfer
import com.example.relayx.data.model.TransferStatus
import com.example.relayx.data.remote.SupabaseClient
import com.example.relayx.services.FirestoreService
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.util.UUID

private const val TAG = "RelayXDebug"

/**
 * Production implementation of TransferRepository.
 *
 * Coordinates the full file transfer lifecycle:
 * 1. Validates the receiver exists in Firestore
 * 2. Creates an UPLOADING transfer record (immediately visible to receiver via snapshot listener)
 * 3. Reads InputStream bytes and uploads to Supabase Storage
 * 4. Updates Firestore progress at milestones: 10%, 30%, 60%, 100%
 * 5. Marks transfer as SENT when upload completes
 * 6. Marks transfer as FAILED if any step throws
 *
 * This class does NOT depend on android.net.Uri or Context — it takes an
 * InputStream directly, keeping it decoupled from the Android framework.
 * The ViewModel handles URI→InputStream conversion.
 */
class TransferRepositoryImpl(
    private val firestoreService: FirestoreService
) : com.example.relayx.domain.repository.TransferRepository {

    override suspend fun sendFile(
        senderCode: String,
        receiverCode: String,
        fileName: String,
        inputStream: InputStream
    ): Result<Transfer> {
        // Generate transfer ID upfront so we can reference it in error handling
        val transferId = UUID.randomUUID().toString()

        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "TransferRepository.sendFile() START")
        Log.d(TAG, "  transferId: $transferId")
        Log.d(TAG, "  senderCode: $senderCode")
        Log.d(TAG, "  receiverCode: $receiverCode")
        Log.d(TAG, "  fileName: $fileName")
        Log.d(TAG, "  inputStream: ${inputStream.javaClass.simpleName}")

        return try {
            // ── Step 1: Validate receiver exists ─────────────────────────
            Log.d(TAG, "[Step 1] Validating receiver code: $receiverCode")
            val receiverExists = firestoreService.doesUserExist(receiverCode)
            Log.d(TAG, "[Step 1] Receiver exists: $receiverExists")
            if (!receiverExists) {
                Log.e(TAG, "❌ Receiver NOT found: $receiverCode")
                return Result.failure(
                    IllegalArgumentException("Invalid receiver code. No user found with code: $receiverCode")
                )
            }

            // ── Step 2: Create initial transfer record in Firestore ──────
            val transfer = Transfer(
                id = transferId,
                senderCode = senderCode,
                receiverCode = receiverCode,
                fileUrl = "",
                fileName = fileName,
                status = TransferStatus.UPLOADING.value,
                progress = 0,
                timestamp = System.currentTimeMillis()
            )
            Log.d(TAG, "[Step 2] Creating Firestore transfer record...")
            Log.d(TAG, "  Data: $transfer")
            firestoreService.createTransfer(transfer)
            Log.d(TAG, "[Step 2] ✅ Firestore record created")

            // ── Step 3: Update progress → 10% (record created) ───────────
            Log.d(TAG, "[Step 3] Updating progress → 10%")
            firestoreService.updateTransferStatus(
                transferId, TransferStatus.UPLOADING.value, 10
            )

            // ── Step 4/5: Read InputStream & update progress → 30% ──────
            Log.d(TAG, "[Step 4] Preparing upload, updating progress → 30%")
            firestoreService.updateTransferStatus(
                transferId, TransferStatus.UPLOADING.value, 30
            )

            // ── Step 6: Generate unique file name using timestamp ────────
            val timestamp = System.currentTimeMillis()
            val storagePath = "${timestamp}_$fileName"
            Log.d(TAG, "[Step 6] Generated storage path: $storagePath")

            // ── Step 7: Upload to Supabase Storage ──────────────────────
            Log.d(TAG, "[Step 7] Starting Supabase upload...")
            val fileUrlResult = SupabaseClient.uploadFile(storagePath, inputStream, "application/octet-stream")

            if (fileUrlResult.isFailure) {
                val error = fileUrlResult.exceptionOrNull()
                Log.e(TAG, "❌ [Step 7] Supabase upload FAILED: ${error?.message}", error)
                firestoreService.updateTransferStatus(
                    transferId, TransferStatus.FAILED.value, 0
                )
                return Result.failure(error!!)
            }

            val fileUrl = fileUrlResult.getOrThrow()
            Log.d(TAG, "[Step 7] ✅ Supabase upload SUCCESS")
            Log.d(TAG, "  Public URL: $fileUrl")

            // ── Step 8: Update progress → 60% (upload complete) ─────────
            Log.d(TAG, "[Step 8] Updating Firestore progress → 60%")
            firestoreService.updateTransferStatus(
                transferId, TransferStatus.UPLOADING.value, 60
            )

            // ── Step 9: Mark as SENT with file URL and 100% progress ────
            val sentTransfer = transfer.copy(
                fileUrl = fileUrl,
                status = TransferStatus.SENT.value,
                progress = 100
            )
            Log.d(TAG, "[Step 9] Updating Firestore → status=SENT, progress=100")
            Log.d(TAG, "  Saving transfer to Firestore...")
            Log.d(TAG, "  Data: $sentTransfer")
            firestoreService.updateTransfer(sentTransfer)

            // ── Step 10: Update progress → 100% (all done) ──────────────
            Log.d(TAG, "[Step 10] Final status update → SENT, 100%")
            firestoreService.updateTransferStatus(
                transferId, TransferStatus.SENT.value, 100
            )

            Log.d(TAG, "✅ sendFile() COMPLETE — transfer $transferId")
            Log.d(TAG, "═══════════════════════════════════════════════")
            Result.success(sentTransfer)

        } catch (e: Exception) {
            // ── Global error handler: mark FAILED in Firestore ───────────
            Log.e(TAG, "❌ sendFile() EXCEPTION: ${e.javaClass.simpleName}", e)
            Log.e(TAG, "  Message: ${e.message}")
            Log.e(TAG, "  Transfer ID: $transferId")
            try {
                Log.d(TAG, "Marking transfer as FAILED in Firestore...")
                firestoreService.updateTransferStatus(
                    transferId, TransferStatus.FAILED.value, 0
                )
                Log.d(TAG, "Transfer marked as FAILED")
            } catch (firebaseError: Exception) {
                Log.e(TAG, "Could not update Firestore to FAILED: ${firebaseError.message}", firebaseError)
            }
            Log.d(TAG, "═══════════════════════════════════════════════")
            Result.failure(e)
        }
    }

    override fun observeAllTransfersForUser(userCode: String): Flow<List<Transfer>> {
        Log.d(TAG, "observeAllTransfersForUser() → userCode=$userCode")
        return firestoreService.observeAllTransfersForUser(userCode)
    }

    override suspend fun updateTransferStatus(
        transferId: String,
        status: String,
        progress: Int
    ): Result<Unit> {
        Log.d(TAG, "updateTransferStatus() → id=$transferId, status=$status, progress=$progress")
        return try {
            firestoreService.updateTransferStatus(transferId, status, progress)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateTransferStatus() FAILED: ${e.message}", e)
            Result.failure(e)
        }
    }
}
