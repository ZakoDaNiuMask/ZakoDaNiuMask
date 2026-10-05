package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.application.ApplicationControlRepository

class EnsureManagerInstalledUseCase(
    private val repository: ApplicationControlRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.ensureManagerInstalled()
}

