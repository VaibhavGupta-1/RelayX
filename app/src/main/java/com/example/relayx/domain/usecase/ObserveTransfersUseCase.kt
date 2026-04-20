package com.example.relayx.domain.usecase

import com.example.relayx.data.model.Transfer
import com.example.relayx.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing incoming file transfers in real-time.
 */
class ObserveTransfersUseCase(
    private val transferRepository: TransferRepository
) {

    /**
     * @param userCode The current user's code to filter transfers.
     * @return A Flow emitting the list of all transfers involving the user.
     */
    operator fun invoke(userCode: String): Flow<List<Transfer>> {
        return transferRepository.observeAllTransfersForUser(userCode)
    }
}
