package com.elephenman.lifetrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LifeTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hilt auto-injects, no manual setup needed
    }
}
