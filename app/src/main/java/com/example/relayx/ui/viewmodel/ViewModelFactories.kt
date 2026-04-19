package com.example.relayx.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.relayx.domain.usecase.GetUserCodeUseCase
import com.example.relayx.domain.usecase.ObserveTransfersUseCase
import com.example.relayx.domain.usecase.SendFileUseCase

/**
 * Factory for creating MainViewModel with its dependencies.
 */
class MainViewModelFactory(
    private val getUserCodeUseCase: GetUserCodeUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(getUserCodeUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/**
 * Factory for creating TransferViewModel with its dependencies.
 *
 * TransferViewModel extends AndroidViewModel, so it requires Application
 * in addition to the use cases. Application context is needed to convert
 * content URIs to InputStreams via ContentResolver.
 */
class TransferViewModelFactory(
    private val application: Application,
    private val sendFileUseCase: SendFileUseCase,
    private val observeTransfersUseCase: ObserveTransfersUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransferViewModel::class.java)) {
            return TransferViewModel(application, sendFileUseCase, observeTransfersUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
