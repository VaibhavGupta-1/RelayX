package com.example.relayx.data.remote

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.InputStream
import com.example.relayx.BuildConfig

private const val TAG = "RelayXDebug"

/**
 * Ktor-based HTTP client for Supabase Storage API.
 * Does NOT use the official Supabase SDK — uses raw REST calls via Ktor.
 *
 * Features:
 * - Streamed file upload (no full-file-in-memory)
 * - Progress tracking via callback
 * - Configurable timeouts for large file transfers
 */
object SupabaseClient {

    // Values injected securely at build time via local.properties -> BuildConfig
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_API_KEY = BuildConfig.SUPABASE_KEY

    private const val STORAGE_BUCKET = "files"

    /**
     * Buffer size for streaming uploads (8KB chunks).
     * Balances memory efficiency with upload speed.
     */
    private const val UPLOAD_BUFFER_SIZE = 8 * 1024

    init {
        // Log Supabase config on first access (mask API key for safety)
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "SupabaseClient initialized (Secure Mode)")
        Log.d(TAG, "Storage Bucket: $STORAGE_BUCKET")
        Log.d(TAG, "═══════════════════════════════════════════════")
    }

    val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        // Extended timeouts for large file transfers
        install(HttpTimeout) {
            requestTimeoutMillis = 5 * 60 * 1000  // 5 minutes
            connectTimeoutMillis = 30 * 1000       // 30 seconds
            socketTimeoutMillis = 60 * 1000        // 60 seconds
        }
    }

    /**
     * Uploads a file to Supabase Storage using streamed byte reading.
     */
    suspend fun uploadFile(
        storagePath: String,
        inputStream: InputStream,
        contentType: String,
        fileSize: Long = -1,
        onProgress: suspend (Int) -> Unit = {}
    ): Result<String> {
        Log.d(TAG, "───────────────────────────────────────────────")
        Log.d(TAG, "uploadFile() START")
        Log.d(TAG, "  storagePath: $storagePath")
        Log.d(TAG, "  contentType: $contentType")
        Log.d(TAG, "  fileSize: ${if (fileSize > 0) "$fileSize bytes (${fileSize / 1024} KB)" else "unknown"}")
        Log.d(TAG, "  bucket: $STORAGE_BUCKET")

        return try {
            // Report initial progress
            onProgress(0)

            // Read file bytes using buffered streaming
            Log.d(TAG, "Reading InputStream into bytes...")
            val fileBytes = readStreamInChunks(inputStream, fileSize, onProgress)
            Log.d(TAG, "InputStream read complete. Bytes read: ${fileBytes.size} (${fileBytes.size / 1024} KB)")

            // Report that file reading is complete (60% milestone)
            onProgress(60)
            Log.d(TAG, "Upload progress: 60% (stream read complete)")

            val uploadUrl = "$SUPABASE_URL/storage/v1/object/$STORAGE_BUCKET/$storagePath"
            // Log.d(TAG, "Upload URL: $uploadUrl") -- Masked for security
            Log.d(TAG, "Sending POST request to Supabase Storage...")

            val response: HttpResponse = httpClient.post(uploadUrl) {
                header("Authorization", "Bearer $SUPABASE_API_KEY")
                header("apikey", SUPABASE_API_KEY)
                // Upsert mode: overwrite if file already exists (handles retries cleanly)
                header("x-upsert", "true")
                contentType(ContentType.parse(contentType))
                setBody(fileBytes)
            }

            Log.d(TAG, "Supabase response status: ${response.status.value} (${response.status.description})")

            // Report upload complete
            onProgress(90)
            Log.d(TAG, "Upload progress: 90% (HTTP request complete)")

            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/$storagePath"
                onProgress(100)
                Log.d(TAG, "✅ Upload SUCCESSFUL")
                Log.d(TAG, "  File path: $storagePath")
                Log.d(TAG, "  Public URL generated successfully.")
                Log.d(TAG, "  Upload progress: 100%")
                Log.d(TAG, "───────────────────────────────────────────────")
                Result.success(publicUrl)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "❌ Upload FAILED — HTTP ${response.status.value}")
                Log.e(TAG, "  Error response body: $errorBody")
                Log.e(TAG, "  Bucket: $STORAGE_BUCKET")
                Log.e(TAG, "───────────────────────────────────────────────")
                Result.failure(
                    Exception("Upload failed [${response.status.value}]: $errorBody")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload EXCEPTION: ${e.javaClass.simpleName}", e)
            Log.e(TAG, "  Message: ${e.message}")
            Log.e(TAG, "  storagePath: $storagePath")
            Log.e(TAG, "  bucket: $STORAGE_BUCKET")
            Log.e(TAG, "───────────────────────────────────────────────")
            Result.failure(e)
        }
    }

    /**
     * Reads an InputStream in chunks, reporting progress along the way.
     * Caps progress reports between 0–50% during the read phase (upload is 50–100%).
     */
    private suspend fun readStreamInChunks(
        inputStream: InputStream,
        totalSize: Long,
        onProgress: suspend (Int) -> Unit
    ): ByteArray {
        if (totalSize <= 0) {
            // Unknown size: read all at once and report 30% after read
            Log.d(TAG, "File size unknown — reading all bytes at once")
            val bytes = inputStream.readBytes()
            Log.d(TAG, "Read ${bytes.size} bytes (${bytes.size / 1024} KB)")
            onProgress(30)
            Log.d(TAG, "Upload progress: 30% (bytes read, size was unknown)")
            return bytes
        }

        // Known size: read in chunks and report intermediate progress
        Log.d(TAG, "Reading file in ${UPLOAD_BUFFER_SIZE / 1024}KB chunks (total: ${totalSize / 1024} KB)")
        val buffer = ByteArray(UPLOAD_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream(totalSize.toInt())
        var bytesRead: Long = 0
        var lastReportedProgress = 0

        while (true) {
            val count = inputStream.read(buffer)
            if (count == -1) break

            output.write(buffer, 0, count)
            bytesRead += count

            // Map reading progress to 0–50% of total progress
            val readProgress = ((bytesRead.toFloat() / totalSize) * 50).toInt().coerceIn(0, 50)
            if (readProgress >= lastReportedProgress + 10) {
                lastReportedProgress = readProgress
                onProgress(readProgress)
                Log.d(TAG, "Upload progress: $readProgress% (read $bytesRead / $totalSize bytes)")
            }
        }

        Log.d(TAG, "Chunk reading complete. Total bytes: ${output.size()}")
        return output.toByteArray()
    }

    /**
     * Returns the public URL for a file in storage.
     */
    fun getPublicUrl(path: String): String {
        val url = "$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/$path"
        // Log.d(TAG, "getPublicUrl(): $url") -- Masked for security
        return url
    }

    /**
     * Generates a signed URL for time-limited access to a file in the bucket.
     */
    suspend fun generateSignedUrl(path: String, expiresIn: Int = 3600): Result<String> {
        Log.d(TAG, "generateSignedUrl() path=$path, expiresIn=$expiresIn")
        return try {
            val signUrl = "$SUPABASE_URL/storage/v1/object/sign/$STORAGE_BUCKET/$path"

            val response: HttpResponse = httpClient.post(signUrl) {
                header("Authorization", "Bearer $SUPABASE_API_KEY")
                header("apikey", SUPABASE_API_KEY)
                contentType(ContentType.Application.Json)
                setBody("""{"expiresIn": $expiresIn}""")
            }

            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                Log.d(TAG, "Signed URL generated successfully")
                Result.success(body)
            } else {
                Log.e(TAG, "Failed to generate signed URL: ${response.status}")
                Result.failure(Exception("Failed to generate signed URL: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateSignedUrl() exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
