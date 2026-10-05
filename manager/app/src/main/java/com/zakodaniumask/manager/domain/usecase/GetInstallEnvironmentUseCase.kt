package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.flash.FlashRepository
import com.zakodaniumask.manager.domain.model.InstallEnvironment

class GetInstallEnvironmentUseCase(
    private val repository: FlashRepository,
) {
    fun cached(): InstallEnvironment? = repository.installEnvironment.value

    suspend operator fun invoke(forceRefresh: Boolean = false) =
        repository.getInstallEnvironment(forceRefresh)
}
