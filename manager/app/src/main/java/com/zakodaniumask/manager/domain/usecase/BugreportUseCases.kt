package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.logging.BugreportRepository
import java.io.File

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
