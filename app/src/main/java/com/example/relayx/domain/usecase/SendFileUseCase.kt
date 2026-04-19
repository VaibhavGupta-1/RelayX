package com.example.relayx.domain.usecase

import com.example.relayx.data.model.Transfer
import com.example.relayx.domain.repository.TransferRepository
import java.io.InputStream

/**
 * Use case for sending a file to another user.
 * Encapsulates the business logic of validating inputs and delegating to the repository.
 *
 * Validation rules:
 * - receiverCode must not be blank
 * - receiverCode must be exactly 6 characters
 * - senderCode must not equal receiverCode (no self-sends)
 * - fileName must not be blank
 */
class SendFileUseCase(
    private val transferRepository: TransferRepository
) {

    /**
     * @param senderCode The current user's code.
     * @param receiverCode The target user's code.
     * @param fileName The display name of the file.
     * @param inputStream The file data stream. Will be consumed and closed by the repository.
     * @return Result containing the created Transfer on success.
     */
    suspend operator fun invoke(
        senderCode: String,
        receiverCode: String,
        fileName: String,
        inputStream: InputStream
    ): Result<Transfer> {
        // Validate inputs
        if (receiverCode.isBlank()) {
            return Result.failure(Exception("Receiver code cannot be empty."))
        }
        if (receiverCode.length != 6) {
            return Result.failure(Exception("Receiver code must be exactly 6 characters."))
        }
        if (senderCode == receiverCode) {
            return Result.failure(Exception("You cannot send a file to yourself."))
        }
        if (fileName.isBlank()) {
            return Result.failure(Exception("No file selected."))
        }

        return transferRepository.sendFile(senderCode, receiverCode, fileName, inputStream)
    }
}
