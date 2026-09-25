package com.kiroland.gallery.tv

import android.app.Application
import android.content.Context

class TvApp : Application() {
    lateinit var store: PhotoStore
        private set
    lateinit var prefs: TvPrefs
        private set
    lateinit var weather: WeatherService
        private set

    override fun onCreate() {
        super.onCreate()
        store = PhotoStore(this)
        prefs = TvPrefs(this)
        weather = WeatherService(this, prefs)
    }
}

val Context.tvApp: TvApp get() = applicationContext as TvApp
