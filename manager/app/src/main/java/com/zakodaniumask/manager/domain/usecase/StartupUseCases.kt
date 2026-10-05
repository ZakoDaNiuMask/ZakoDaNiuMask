package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
