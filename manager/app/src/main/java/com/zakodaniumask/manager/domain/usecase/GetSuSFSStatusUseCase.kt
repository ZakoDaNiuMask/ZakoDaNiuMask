package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

