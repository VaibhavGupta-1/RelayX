package com.example.relayx.services

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.relayx.RelayXApplication

private const val TAG = "RelayXDebug"

/**
 * Background worker assigned to handle uploading a file.
 * Resilient against app closure.
 */
class UploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString("fileUri")
        val senderCode = inputData.getString("senderCode")
        val receiverCode = inputData.getString("receiverCode")
        val fileName = inputData.getString("fileName")

        if (fileUriString == null || senderCode == null || receiverCode == null || fileName == null) {
            Log.e(TAG, "UploadWorker: Missing input data")
            return Result.failure()
        }

        Log.d(TAG, "UploadWorker: Starting background upload for $fileName")

        // Retrieve DI container from Application Context
        val appContext = applicationContext as RelayXApplication
        val transferRepository = appContext.container.transferRepository

        val fileUri = Uri.parse(fileUriString)
        val contentResolver = applicationContext.contentResolver

        val inputStream = try {
            contentResolver.openInputStream(fileUri)
        } catch (e: Exception) {
            Log.e(TAG, "UploadWorker: Failed to open InputStream", e)
            return Result.failure()
        }

        if (inputStream == null) {
            Log.e(TAG, "UploadWorker: InputStream is null")
            return Result.failure()
        }

        return try {
            // Using transferRepository.sendFile synchronously
            // The repository orchestrates Firestore updates and uses Supabase streaming under the hood.
            val result = transferRepository.sendFile(senderCode, receiverCode, fileName, inputStream)
            
            if (result.isSuccess) {
                Log.d(TAG, "UploadWorker: Upload SUCCESS")
                Result.success(workDataOf("success" to true))
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "UploadWorker: Upload FAILED - ${error?.message}")
                Result.failure(workDataOf("error" to error?.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "UploadWorker: Exception during upload - ${e.message}", e)
            Result.failure(workDataOf("error" to e.message))
        }
    }
}
