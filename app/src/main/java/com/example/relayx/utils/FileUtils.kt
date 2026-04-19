package com.example.relayx.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast

private const val TAG = "RelayXDebug"

/**
 * Utility functions for file operations: name extraction, opening, and downloading.
 */
object FileUtils {

    /**
     * Extracts the display name of a file from its content URI.
     *
     * @param context The Android context for content resolver access.
     * @param uri The content URI of the file.
     * @return The file name, or "unknown_file" if it cannot be determined.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown_file"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = it.getString(nameIndex) ?: "unknown_file"
                }
            }
        }
        return name
    }

    /**
     * Gets the MIME type of a file from its content URI.
     */
    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    /**
     * Opens a file URL in an external app using Intent.ACTION_VIEW.
     *
     * This launches the system chooser for the appropriate app (browser, PDF viewer, etc.).
     * Handles exceptions gracefully with a Toast message.
     *
     * @param context The Android context.
     * @param url The public URL of the file to open.
     */
    fun openFile(context: Context, url: String) {
        Log.d(TAG, "FileUtils: openFile() url=$url")
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "FileUtils: openFile() ✅ launched intent")
        } catch (e: Exception) {
            Log.e(TAG, "FileUtils: openFile() ❌ failed: ${e.message}", e)
            Toast.makeText(
                context,
                "No app found to open this file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Downloads a file to the device's Downloads directory using Android's DownloadManager.
     *
     * Returns the download ID so callers can track completion via BroadcastReceiver.
     *
     * @param context The Android context.
     * @param url The public URL of the file to download.
     * @param fileName The display name for the downloaded file.
     * @return The DownloadManager download ID, or -1 if enqueue failed.
     */
    fun downloadFile(context: Context, url: String, fileName: String): Long {
        Log.d(TAG, "FileUtils: downloadFile()")
        Log.d(TAG, "  URL: $url")
        Log.d(TAG, "  fileName: $fileName")

        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Determine MIME type from file extension
            val extension = MimeTypeMap.getFileExtensionFromUrl(url)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading via RelayX")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType(mimeType)
                // Allow download over both wifi and mobile data
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )
            }

            val downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "FileUtils: downloadFile() ✅ enqueued, downloadId=$downloadId")

            Toast.makeText(
                context,
                "Downloading \"$fileName\"...",
                Toast.LENGTH_SHORT
            ).show()

            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "FileUtils: downloadFile() ❌ failed: ${e.message}", e)
            Toast.makeText(
                context,
                "Download failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            -1L
        }
    }

    /**
     * Checks whether a download completed successfully via DownloadManager.
     *
     * @param context The Android context.
     * @param downloadId The ID returned by DownloadManager.enqueue().
     * @return true if download STATUS_SUCCESSFUL, false otherwise.
     */
    fun isDownloadSuccessful(context: Context, downloadId: Long): Boolean {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        cursor?.use {
            if (it.moveToFirst()) {
                val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0) {
                    val status = it.getInt(statusIndex)
                    Log.d(TAG, "FileUtils: download $downloadId status=$status")
                    return status == DownloadManager.STATUS_SUCCESSFUL
                }
            }
        }
        return false
    }
}
