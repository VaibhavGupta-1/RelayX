package com.example.relayx

import android.app.Application
import com.example.relayx.di.AppContainer
import com.google.firebase.FirebaseApp

class RelayXApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        container = AppContainer(this)
    }
}
