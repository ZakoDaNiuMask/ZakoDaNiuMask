package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.flash.FlashRepository

class ObserveKernelFlashUseCase(private val repository: FlashRepository) {
    operator fun invoke() = repository.kernelFlashSession
}

class StartKernelFlashUseCase(private val repository: FlashRepository) {
    operator fun invoke(
        uri: String,
        selectedSlot: String?,
        skipKsud: Boolean = false,
        kpmPatchEnabled: Boolean = false,
        kpmUndoPatch: Boolean = false,
    ) = repository.startKernelFlash(uri, selectedSlot, skipKsud, kpmPatchEnabled, kpmUndoPatch)
}
