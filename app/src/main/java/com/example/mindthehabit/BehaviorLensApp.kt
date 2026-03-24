package com.example.mindthehabit

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BehaviorLensApp : Application() {
    // App now uses pre-populated database from assets
    // Historical data is bundled with the app
}
