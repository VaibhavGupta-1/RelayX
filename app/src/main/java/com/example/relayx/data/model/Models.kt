package com.example.relayx.data.model

/**
 * Represents a user in the Firestore "users" collection.
 */
data class User(
    val code: String = "",
    val createdAt: Long = 0L
)

/**
 * Represents a file transfer in the Firestore "transfers" collection.
 * Maps 1:1 with the Firestore document structure.
 *
 * Uses Long (epoch millis) for timestamp instead of Firebase Timestamp
 * to keep the model decoupled from Firebase SDK types.
 */
data class Transfer(
    val id: String = "",
    val senderCode: String = "",
    val receiverCode: String = "",
    val fileUrl: String = "",
    val fileName: String = "",
    val status: String = TransferStatus.UPLOADING.value,
    val progress: Int = 0,
    val timestamp: Long = 0L
)

/**
 * Enum representing possible transfer statuses.
 *
 * Firestore-persisted lifecycle (sender side):
 *   UPLOADING → SENT
 *       ↘        ↘
 *      FAILED   FAILED
 *
 * UI-only lifecycle (receiver side):
 *   SENT → DOWNLOADING → DOWNLOADED
 *            ↘
 *           FAILED (download error)
 *
 * DOWNLOADING and DOWNLOADED are managed ONLY in the ViewModel's
 * downloadStates map — they are never written to Firestore.
 */
enum class TransferStatus(val value: String) {
    UPLOADING("UPLOADING"),
    SENT("SENT"),
    DOWNLOADING("DOWNLOADING"),
    DOWNLOADED("DOWNLOADED"),
    FAILED("FAILED");

    companion object {
        /**
         * Safely parses a string to TransferStatus, defaulting to UPLOADING.
         */
        fun fromValue(value: String): TransferStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) }
                ?: UPLOADING
        }
    }
}

/**
 * Enum representing the detected file type based on file extension.
 * Used for selecting the correct icon and enabling preview behavior.
 */
enum class FileType {
    IMAGE,
    VIDEO,
    PDF,
    AUDIO,
    OTHER;

    companion object {
        /**
         * Detects file type from a file name's extension.
         *
         * @param fileName The full file name (e.g., "photo.jpg", "document.pdf").
         * @return The detected FileType.
         */
        fun fromFileName(fileName: String): FileType {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> IMAGE
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> VIDEO
                "pdf" -> PDF
                "mp3", "aac", "wav", "ogg", "flac", "m4a" -> AUDIO
                else -> OTHER
            }
        }
    }
}
