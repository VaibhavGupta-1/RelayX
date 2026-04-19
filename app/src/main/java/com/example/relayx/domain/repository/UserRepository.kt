package com.example.relayx.domain.repository

/**
 * Domain-layer contract for user identity operations.
 */
interface UserRepository {

    /**
     * Gets the existing user code or creates a new one.
     * Handles code generation, collision checking, local persistence, and Firestore registration.
     */
    suspend fun getOrCreateUserCode(): String

    /**
     * Returns the locally saved user code, or null if not yet generated.
     */
    suspend fun getCurrentUserCode(): String?
}
