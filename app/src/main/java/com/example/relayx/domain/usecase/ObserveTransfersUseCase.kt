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
     * @param receiverCode The current user's code to filter incoming transfers.
     * @return A Flow emitting the list of transfers whenever changes occur.
     */
    operator fun invoke(receiverCode: String): Flow<List<Transfer>> {
        return transferRepository.observeIncomingTransfers(receiverCode)
    }
}
