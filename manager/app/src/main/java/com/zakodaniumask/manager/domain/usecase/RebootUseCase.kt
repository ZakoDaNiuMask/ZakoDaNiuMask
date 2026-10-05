package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.application.ApplicationControlRepository

class RebootUseCase(
    private val repository: ApplicationControlRepository,
) {
    suspend operator fun invoke(reason: String = "") = repository.reboot(reason)
}
