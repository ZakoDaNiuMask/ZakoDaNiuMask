package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.update.ManagerUpdateRepository
import com.zakodaniumask.manager.domain.model.ManagerUpdateChannel
import com.zakodaniumask.manager.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? =
        when (channel) {
            ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
            ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
        }
}
