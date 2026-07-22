package com.example

import android.app.Application

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.api.Firebase.ensureFirebaseInitialized(this)
    }

    companion object {
        lateinit var instance: MainApplication
            private set
    }
}

