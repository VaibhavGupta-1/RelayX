package com.example.relayx

import android.app.Application
import com.google.firebase.FirebaseApp

class RelayXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
