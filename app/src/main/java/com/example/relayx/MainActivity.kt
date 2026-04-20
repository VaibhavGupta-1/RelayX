package com.example.relayx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.relayx.di.AppContainer
import com.example.relayx.ui.navigation.Screen
import com.example.relayx.ui.screens.HomeScreen
import com.example.relayx.ui.screens.TransferScreen
import com.example.relayx.ui.theme.RelayXTheme
import com.example.relayx.ui.viewmodel.MainViewModel
import com.example.relayx.ui.viewmodel.MainViewModelFactory
import com.example.relayx.ui.viewmodel.TransferViewModel
import com.example.relayx.ui.viewmodel.TransferViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Retrieve DI container from Application
        appContainer = (application as RelayXApplication).container

        setContent {
            RelayXTheme {
                val navController = rememberNavController()

                // ViewModels are scoped to the Activity (survive recomposition)
                val mainViewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(appContainer.getUserCodeUseCase)
                )
                val transferViewModel: TransferViewModel = viewModel(
                    factory = TransferViewModelFactory(
                        application,
                        appContainer.sendFileUseCase,
                        appContainer.observeTransfersUseCase,
                        appContainer.checkUserExistsUseCase
                    )
                )

                // Collect states as Compose State (triggers recomposition on change)
                val mainUiState by mainViewModel.uiState.collectAsState()
                val transferUiState by transferViewModel.uiState.collectAsState()

                // Start the Firestore real-time listener once user code is available.
                // LaunchedEffect keyed on userCode ensures:
                // - Listener starts when code becomes available
                // - Old listener is cancelled if code changes (edge case)
                // - No duplicate listeners (TransferViewModel handles idempotency too)
                LaunchedEffect(mainUiState.userCode) {
                    if (mainUiState.userCode.isNotEmpty()) {
                        transferViewModel.startListening(mainUiState.userCode)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            mainUiState = mainUiState,
                            transferUiState = transferUiState,
                            onReceiverCodeChanged = transferViewModel::onReceiverCodeChanged,
                            onFilesSelected = transferViewModel::onFilesSelected,
                            onSendFiles = { transferViewModel.sendFiles(mainUiState.userCode) },
                            onClearError = {
                                mainViewModel.clearError()
                                transferViewModel.clearError()
                            },
                            onClearSendSuccess = transferViewModel::clearSendSuccess,
                            onNavigateToTransfers = {
                                navController.navigate(Screen.Transfers.route)
                            }
                        )
                    }

                    composable(Screen.Transfers.route) {
                        TransferScreen(
                            transfers = transferUiState.transfers,
                            downloadStates = transferUiState.downloadStates,
                            currentUserCode = mainUiState.userCode,
                            onDownload = transferViewModel::startDownload,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}