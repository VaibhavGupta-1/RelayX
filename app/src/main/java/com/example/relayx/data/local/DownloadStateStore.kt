package com.example.relayx.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private const val TAG = "RelayXDebug"

// DataStore extension on Context specifically for download states
val Context.downloadStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "download_states")

/**
 * Persists active download mappings (downloadId -> transferId) to survive process death.
 */
class DownloadStateStore(private val context: Context) {

    private val PENDING_DOWNLOADS_KEY = stringPreferencesKey("pending_downloads")

    /**
     * Reads the mapping from DataStore as a Flow of Map<Long, String>.
     */
    fun getPendingDownloads(): Flow<Map<Long, String>> {
        return context.downloadStateDataStore.data.map { preferences ->
            val jsonString = preferences[PENDING_DOWNLOADS_KEY] ?: "{}"
            val map = mutableMapOf<Long, String>()
            try {
                val jsonObject = JSONObject(jsonString)
                jsonObject.keys().forEach { key ->
                    map[key.toLong()] = jsonObject.getString(key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "DownloadStateStore: Error parsing pending downloads: ${e.message}")
            }
            map
        }
    }

    /**
     * Adds a new download mapping to DataStore.
     */
    suspend fun savePendingDownload(downloadId: Long, transferId: String) {
        context.downloadStateDataStore.edit { preferences ->
            val jsonString = preferences[PENDING_DOWNLOADS_KEY] ?: "{}"
            try {
                val jsonObject = JSONObject(jsonString)
                jsonObject.put(downloadId.toString(), transferId)
                preferences[PENDING_DOWNLOADS_KEY] = jsonObject.toString()
                Log.d(TAG, "DownloadStateStore: Saved mapping $downloadId -> $transferId")
            } catch (e: Exception) {
                Log.e(TAG, "DownloadStateStore: Error saving pending download: ${e.message}")
            }
        }
    }

    /**
     * Removes a completed or failed download mapping from DataStore.
     */
    suspend fun removePendingDownload(downloadId: Long) {
        context.downloadStateDataStore.edit { preferences ->
            val jsonString = preferences[PENDING_DOWNLOADS_KEY] ?: "{}"
            try {
                val jsonObject = JSONObject(jsonString)
                jsonObject.remove(downloadId.toString())
                preferences[PENDING_DOWNLOADS_KEY] = jsonObject.toString()
                Log.d(TAG, "DownloadStateStore: Removed mapping for $downloadId")
            } catch (e: Exception) {
                Log.e(TAG, "DownloadStateStore: Error removing pending download: ${e.message}")
            }
        }
    }
}
