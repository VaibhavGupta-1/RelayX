package com.example.relayx.domain.usecase

import com.example.relayx.domain.repository.UserRepository

/**
 * Use case for checking if a specific user code exists.
 */
class CheckUserExistsUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(code: String): Boolean {
        return userRepository.doesUserExist(code)
    }
}
