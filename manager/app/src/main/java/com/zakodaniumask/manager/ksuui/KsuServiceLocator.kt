package com.zakodaniumask.manager.ksuui

import android.app.Application
import com.zakodaniumask.manager.ksuui.data.repository.SettingsRepository

/**
 * Minimal bridge for ported KernelSU UI code that uses the no-argument
 * `SettingsRepositoryImpl()` pattern. Initialized once from the Application after Koin starts.
 */
object KsuServiceLocator {
    lateinit var settingsRepository: SettingsRepository
        private set

    fun init(app: Application, settings: SettingsRepository) {
        ksuApp = app
        settingsRepository = settings
    }
}
