package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.flash.FlashRepository
import com.zakodaniumask.manager.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
