package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.flash.FlashRepository

class CheckFlashModuleMountUseCase(private val repository: FlashRepository) {
    suspend operator fun invoke(uri: String) = repository.moduleNeedsMount(uri)
}
