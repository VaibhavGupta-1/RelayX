package com.example.relayx.di

import android.content.Context
import com.example.relayx.data.repository.TransferRepositoryImpl
import com.example.relayx.data.repository.UserRepositoryImpl
import com.example.relayx.domain.repository.TransferRepository
import com.example.relayx.domain.repository.UserRepository
import com.example.relayx.domain.usecase.GetUserCodeUseCase
import com.example.relayx.domain.usecase.ObserveTransfersUseCase
import com.example.relayx.domain.usecase.SendFileUseCase
import com.example.relayx.services.FirestoreService
import com.example.relayx.services.SupabaseService

/**
 * Manual dependency injection container.
 * Provides singleton instances of services, repositories, and use cases.
 *
 * In a production app you'd typically use Hilt/Dagger, but this keeps
 * the scaffold lightweight while still following proper DI patterns.
 */
class AppContainer(private val context: Context) {

    // ─── Services ───────────────────────────────────────────────────────

    val firestoreService: FirestoreService by lazy {
        FirestoreService()
    }

    val supabaseService: SupabaseService by lazy {
        SupabaseService(context)
    }

    // ─── Repositories ───────────────────────────────────────────────────

    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(context, firestoreService)
    }

    // TransferRepositoryImpl no longer needs Context — it takes InputStream directly
    val transferRepository: TransferRepository by lazy {
        TransferRepositoryImpl(firestoreService)
    }

    // ─── Use Cases ──────────────────────────────────────────────────────

    val getUserCodeUseCase: GetUserCodeUseCase by lazy {
        GetUserCodeUseCase(userRepository)
    }

    val sendFileUseCase: SendFileUseCase by lazy {
        SendFileUseCase(transferRepository)
    }

    val observeTransfersUseCase: ObserveTransfersUseCase by lazy {
        ObserveTransfersUseCase(transferRepository)
    }
}
