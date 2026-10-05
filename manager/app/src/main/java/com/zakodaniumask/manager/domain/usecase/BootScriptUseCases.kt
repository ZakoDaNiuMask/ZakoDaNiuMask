package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.bootscript.BootScriptRepository
import com.zakodaniumask.manager.domain.model.BootScriptSettings

class GetBootScriptUseCase(
    private val repository: BootScriptRepository,
) {
    suspend operator fun invoke(): BootScriptSettings = repository.load()
}

class SetBootScriptUseCase(
    private val repository: BootScriptRepository,
) {
    suspend operator fun invoke(settings: BootScriptSettings): Boolean = repository.save(settings)
}
