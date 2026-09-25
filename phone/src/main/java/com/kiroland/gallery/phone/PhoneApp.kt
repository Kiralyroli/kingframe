package com.kiroland.gallery.phone

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) {
    /** The default: the dark "Csend" look, same as on the TV. */
    DARK("Sötét"),
    LIGHT("Világos"),
    SYSTEM("Rendszer szerint"),
}

class PhoneApp : Application() {
    lateinit var repo: PhotoRepository
        private set
    lateinit var connection: ConnectionStore
        private set

    /** Outlives activities, so a share being processed survives rotation. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _theme = MutableStateFlow(ThemeMode.DARK)
    val theme: StateFlow<ThemeMode> = _theme

    fun setTheme(mode: ThemeMode) {
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("theme", mode.name).apply()
        _theme.value = mode
    }

    override fun onCreate() {
        super.onCreate()
        repo = PhotoRepository(this)
        connection = ConnectionStore(this)
        _theme.value = getSharedPreferences("ui", MODE_PRIVATE).getString("theme", null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.DARK
        SyncWorker.schedulePeriodic(this)
    }
}

val Context.phoneApp: PhoneApp get() = applicationContext as PhoneApp
