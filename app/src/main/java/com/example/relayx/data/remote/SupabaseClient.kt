package com.example.relayx.data.remote

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.InputStream
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
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

        return try {
            onProgress(0)

            val uploadUrl = "$SUPABASE_URL/storage/v1/object/$STORAGE_BUCKET/$storagePath"
            Log.d(TAG, "Sending POST request to Supabase Storage with streaming...")

            val response: HttpResponse = httpClient.post(uploadUrl) {
                header("Authorization", "Bearer $SUPABASE_API_KEY")
                header("apikey", SUPABASE_API_KEY)
                header("x-upsert", "true")

                // Provide streamed outgoing content
                setBody(object : OutgoingContent.WriteChannelContent() {
                    override val contentLength: Long? = if (fileSize > 0) fileSize else null
                    override val contentType: ContentType = ContentType.parse(contentType)

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        val buffer = ByteArray(UPLOAD_BUFFER_SIZE)
                        var bytesSent: Long = 0
                        var lastReportedProgress = 0

                        inputStream.use { stream ->
                            while (true) {
                                val count = stream.read(buffer)
                                if (count == -1) break
                                channel.writeFully(buffer, 0, count)
                                bytesSent += count

                                val len = contentLength
                                if (len != null && len > 0) {
                                    val progress = ((bytesSent.toFloat() / len) * 100).toInt().coerceIn(0, 100)
                                    // Throttle progress updates to every 5%
                                    if (progress >= lastReportedProgress + 5 || progress == 100) {
                                        lastReportedProgress = progress
                                        // Dispatch to onProgress callback
                                        onProgress(progress)
                                    }
                                }
                            }
                        }
                    }
                })
            }

            Log.d(TAG, "Supabase response status: ${response.status.value} (${response.status.description})")

            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/$storagePath"
                onProgress(100)
                Log.d(TAG, "✅ Upload SUCCESSFUL")
                Result.success(publicUrl)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "❌ Upload FAILED — HTTP ${response.status.value}")
                Result.failure(Exception("Upload failed [${response.status.value}]: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload EXCEPTION: ${e.javaClass.simpleName}", e)
            Result.failure(e)
        }
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
