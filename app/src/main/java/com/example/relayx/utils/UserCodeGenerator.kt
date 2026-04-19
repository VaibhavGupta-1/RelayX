package com.example.relayx.utils

/**
 * Generates unique 6-character alphanumeric codes for anonymous user identification.
 * Excludes ambiguous characters: O, 0, I, 1, l to avoid visual confusion.
 */
object UserCodeGenerator {

    private const val CODE_LENGTH = 6

    // Excludes: O, 0, I, 1, l
    private val ALLOWED_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /**
     * Generates a random 6-character code using only unambiguous characters.
     */
    fun generate(): String {
        return (1..CODE_LENGTH)
            .map { ALLOWED_CHARS.random() }
            .joinToString("")
    }
}
