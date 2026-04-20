package com.example.relayx.data.repository

import android.content.Context
import com.example.relayx.services.FirestoreService
import com.example.relayx.utils.UserCodeGenerator
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore extension on Context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "relayx_prefs")

/**
 * Implementation of UserRepository that manages anonymous user identity.
 * Generates a 6-char unique code, checks Firestore for collisions,
 * persists locally via DataStore, and registers in Firestore.
 */
class UserRepositoryImpl(
    private val context: Context,
    private val firestoreService: FirestoreService
) : com.example.relayx.domain.repository.UserRepository {

    companion object {
        private val KEY_USER_CODE = stringPreferencesKey("user_code")
    }

    override suspend fun getOrCreateUserCode(): String {
        // Check if we already have a code saved locally
        val savedCode = getSavedCode()
        if (savedCode != null) {
            return savedCode
        }

        // Generate a new unique code with collision avoidance
        val newCode = generateUniqueCode()

        // Save locally
        saveCode(newCode)

        // Register in Firestore
        firestoreService.registerUser(newCode)

        return newCode
    }

    override suspend fun getCurrentUserCode(): String? {
        return getSavedCode()
    }

    override suspend fun doesUserExist(code: String): Boolean {
        return firestoreService.doesUserExist(code)
    }

    private suspend fun getSavedCode(): String? {
        return context.dataStore.data
            .map { preferences -> preferences[KEY_USER_CODE] }
            .first()
    }

    private suspend fun saveCode(code: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_CODE] = code
        }
    }

    /**
     * Generates a unique 6-char code and checks Firestore for collisions.
     * Retries up to 10 times before throwing.
     */
    private suspend fun generateUniqueCode(): String {
        repeat(10) {
            val code = UserCodeGenerator.generate()
            val exists = firestoreService.doesUserExist(code)
            if (!exists) return code
        }
        throw Exception("Failed to generate a unique user code after 10 attempts.")
    }
}
