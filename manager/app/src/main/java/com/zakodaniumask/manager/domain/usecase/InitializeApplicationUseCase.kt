package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.startup.ApplicationInitializationRepository
import com.zakodaniumask.manager.data.startup.StartupRepository

class InitializeApplicationUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val startupRepository: StartupRepository,
    private val initializationRepository: ApplicationInitializationRepository,
) {
    suspend operator fun invoke() {
        runCatching {
            settingsRepository.preload()
            initializationRepository.initialize()
        }.onSuccess {
            startupRepository.markReady()
        }.onFailure { error ->
            startupRepository.markFailed(error)
        }
    }
}
