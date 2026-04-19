package com.example.relayx.services

import android.util.Log
import com.example.relayx.data.model.Transfer
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "RelayXDebug"

/**
 * Service class for all Firestore operations.
 * Manages "users" and "transfers" collections.
 *
 * All suspend functions use kotlinx.coroutines.tasks.await() to bridge
 * Firebase's Task API into coroutine-land. Real-time listeners use
 * callbackFlow for clean Flow integration with proper cleanup.
 */
class FirestoreService {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val transfersCollection = db.collection("transfers")

    // ─── User Operations ────────────────────────────────────────────────

    /**
     * Registers a new user in Firestore with the given code.
     * Uses the code as the document ID for O(1) lookups.
     * Uses FieldValue.serverTimestamp() for consistent server-side timing.
     */
    suspend fun registerUser(code: String) {
        Log.d(TAG, "Firestore: registerUser() code=$code")
        val user = hashMapOf(
            "code" to code,
            "createdAt" to FieldValue.serverTimestamp()
        )
        usersCollection.document(code).set(user).await()
        Log.d(TAG, "Firestore: registerUser() ✅ success")
    }

    /**
     * Checks if a user with the given code exists in Firestore.
     * Uses a direct document get (not a query) for efficiency.
     */
    suspend fun doesUserExist(code: String): Boolean {
        Log.d(TAG, "Firestore: doesUserExist() code=$code")
        val document = usersCollection.document(code).get().await()
        val exists = document.exists()
        Log.d(TAG, "Firestore: doesUserExist() → $exists")
        return exists
    }

    // ─── Transfer Operations ────────────────────────────────────────────

    /**
     * Creates a new transfer document in Firestore.
     * Uses FieldValue.serverTimestamp() for the timestamp field.
     * The transfer ID is used as the document ID for direct lookups.
     */
    suspend fun createTransfer(transfer: Transfer) {
        Log.d(TAG, "Firestore: createTransfer() id=${transfer.id}")
        Log.d(TAG, "  Saving transfer to Firestore...")
        Log.d(TAG, "  Data: $transfer")
        val data = hashMapOf(
            "id" to transfer.id,
            "senderCode" to transfer.senderCode,
            "receiverCode" to transfer.receiverCode,
            "fileUrl" to transfer.fileUrl,
            "fileName" to transfer.fileName,
            "status" to transfer.status,
            "progress" to transfer.progress,
            "timestamp" to FieldValue.serverTimestamp()
        )
        try {
            transfersCollection.document(transfer.id).set(data).await()
            Log.d(TAG, "Firestore: createTransfer() ✅ success")
        } catch (e: Exception) {
            Log.e(TAG, "Firestore: createTransfer() ❌ FAILED: ${e.message}", e)
            throw e
        }
    }

    /**
     * Updates an existing transfer document with all fields.
     * Preserves the original timestamp (does NOT overwrite with serverTimestamp).
     */
    suspend fun updateTransfer(transfer: Transfer) {
        Log.d(TAG, "Firestore: updateTransfer() id=${transfer.id}, status=${transfer.status}, progress=${transfer.progress}")
        val data = hashMapOf<String, Any>(
            "id" to transfer.id,
            "senderCode" to transfer.senderCode,
            "receiverCode" to transfer.receiverCode,
            "fileUrl" to transfer.fileUrl,
            "fileName" to transfer.fileName,
            "status" to transfer.status,
            "progress" to transfer.progress
        )
        try {
            transfersCollection.document(transfer.id).update(data).await()
            Log.d(TAG, "Firestore: updateTransfer() ✅ success")
        } catch (e: Exception) {
            Log.e(TAG, "Firestore: updateTransfer() ❌ FAILED: ${e.message}", e)
            throw e
        }
    }

    /**
     * Atomically updates only the status and progress fields of a transfer.
     * Uses update() for field-level writes (cheaper than full document set).
     */
    suspend fun updateTransferStatus(transferId: String, status: String, progress: Int) {
        Log.d(TAG, "Firestore: updateTransferStatus() id=$transferId, status=$status, progress=$progress")
        try {
            transfersCollection.document(transferId).update(
                mapOf(
                    "status" to status,
                    "progress" to progress
                )
            ).await()
            Log.d(TAG, "Firestore: updateTransferStatus() ✅ success")
        } catch (e: Exception) {
            Log.e(TAG, "Firestore: updateTransferStatus() ❌ FAILED: ${e.message}", e)
            throw e
        }
    }

    // ─── Real-Time Listeners ────────────────────────────────────────────

    /**
     * Observes transfers where receiverCode matches the given code.
     * Uses Firestore snapshot listener for real-time updates across devices.
     */
    fun observeTransfersForReceiver(receiverCode: String): Flow<List<Transfer>> = callbackFlow {
        Log.d(TAG, "Firestore: observeTransfersForReceiver() started → receiverCode=$receiverCode")
        val listenerRegistration: ListenerRegistration = transfersCollection
            .whereEqualTo("receiverCode", receiverCode)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore: snapshot listener ERROR: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                val transfers = snapshot?.documents?.mapNotNull { doc ->
                    documentToTransfer(doc)
                } ?: emptyList()

                Log.d(TAG, "Firestore: snapshot update → ${transfers.size} transfers received")
                trySend(transfers)
            }

        // Clean up listener when the Flow collector cancels
        awaitClose {
            Log.d(TAG, "Firestore: snapshot listener REMOVED for receiverCode=$receiverCode")
            listenerRegistration.remove()
        }
    }

    /**
     * Observes transfers sent by the given user code.
     * Same real-time behavior as observeTransfersForReceiver but for outgoing transfers.
     */
    fun observeTransfersForSender(senderCode: String): Flow<List<Transfer>> = callbackFlow {
        Log.d(TAG, "Firestore: observeTransfersForSender() started → senderCode=$senderCode")
        val listenerRegistration: ListenerRegistration = transfersCollection
            .whereEqualTo("senderCode", senderCode)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore: sender listener ERROR: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                val transfers = snapshot?.documents?.mapNotNull { doc ->
                    documentToTransfer(doc)
                } ?: emptyList()

                Log.d(TAG, "Firestore: sender snapshot → ${transfers.size} transfers")
                trySend(transfers)
            }

        awaitClose {
            Log.d(TAG, "Firestore: sender listener REMOVED for senderCode=$senderCode")
            listenerRegistration.remove()
        }
    }

    // ─── Private Helpers ────────────────────────────────────────────────

    /**
     * Safely parses a Firestore document into a Transfer object.
     */
    private fun documentToTransfer(doc: com.google.firebase.firestore.DocumentSnapshot): Transfer? {
        return try {
            // Convert Firestore Timestamp → Long (epoch millis)
            val firestoreTimestamp = doc.getTimestamp("timestamp")
            val epochMillis = firestoreTimestamp?.toDate()?.time ?: 0L

            Transfer(
                id = doc.getString("id") ?: doc.id,
                senderCode = doc.getString("senderCode") ?: "",
                receiverCode = doc.getString("receiverCode") ?: "",
                fileUrl = doc.getString("fileUrl") ?: "",
                fileName = doc.getString("fileName") ?: "",
                status = doc.getString("status") ?: "",
                progress = (doc.getLong("progress") ?: 0).toInt(),
                timestamp = epochMillis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firestore: documentToTransfer() parse error for doc ${doc.id}: ${e.message}")
            null
        }
    }
}
