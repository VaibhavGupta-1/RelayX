package com.example.relayx.domain.usecase

import com.example.relayx.domain.repository.UserRepository

/**
 * Use case for initializing or retrieving the anonymous user identity.
 */
class GetUserCodeUseCase(
    private val userRepository: UserRepository
) {

    /**
     * Returns the existing user code or generates a new one.
     */
    suspend operator fun invoke(): String {
        return userRepository.getOrCreateUserCode()
    }
}
