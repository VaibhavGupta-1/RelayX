package com.example.relayx.services

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.relayx.data.remote.SupabaseClient

private const val TAG = "RelayXDebug"

/**
 * Android-aware wrapper around SupabaseClient.
 *
 * Handles URI-to-bytes conversion using ContentResolver, then delegates
 * to SupabaseClient for the actual upload. Useful for standalone file
 * uploads outside the core transfer flow.
 */
class SupabaseService(private val context: Context) {

    /**
     * Uploads a file from a content URI to Supabase Storage.
     */
    suspend fun uploadFile(fileUri: Uri, storagePath: String? = null): Result<String> {
        Log.d(TAG, "SupabaseService: uploadFile() START")
        // Log.d(TAG, "  File URI: $fileUri") -- Masked for security
        return try {
            val contentResolver = context.contentResolver
            val fileName = getFileName(fileUri) ?: "unknown_file"
            val path = storagePath ?: "${System.currentTimeMillis()}_$fileName"

            Log.d(TAG, "  File name: $fileName")
            Log.d(TAG, "  Storage path: $path")
            Log.d(TAG, "  Bucket: transfers")

            // Read file bytes from content URI
            val inputStream = contentResolver.openInputStream(fileUri)
            Log.d(TAG, "  InputStream opened: ${inputStream != null}")

            if (inputStream == null) {
                Log.e(TAG, "SupabaseService: InputStream is NULL — aborting")
                return Result.failure(Exception("Could not open file stream"))
            }

            // Log file size if possible
            try {
                val fd = contentResolver.openFileDescriptor(fileUri, "r")
                val size = fd?.statSize ?: -1
                Log.d(TAG, "  File size: ${if (size > 0) "$size bytes (${size / 1024} KB)" else "unknown"}")
                fd?.close()
            } catch (e: Exception) {
                Log.d(TAG, "  File size: unknown (${e.message})")
            }

            inputStream.use { stream ->
                Log.d(TAG, "SupabaseService: delegating to SupabaseClient.uploadFile()...")
                SupabaseClient.uploadFile(path, stream, "application/octet-stream")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SupabaseService: uploadFile() FAILED: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Returns the public URL for a file in storage.
     */
    fun getPublicUrl(path: String): String {
        return SupabaseClient.getPublicUrl(path)
    }

    /**
     * Extracts the display name from a content URI.
     */
    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = it.getString(nameIndex)
                }
            }
        }
        Log.d(TAG, "SupabaseService: getFileName() → $name")
        return name
    }
}
