package com.pathly

import android.app.Application
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PathlyApplication : Application(), OnMapsSdkInitializedCallback {
    
    override fun onCreate() {
        super.onCreate()
        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, this)
    }
    
    override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
        // Maps SDK initialized successfully
    }
}