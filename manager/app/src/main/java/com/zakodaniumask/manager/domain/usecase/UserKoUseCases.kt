// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.userko.UserKoRepository
import com.zakodaniumask.manager.domain.model.UserKoOperationResult
import com.zakodaniumask.manager.domain.model.UserKoState

class GetUserKoStateUseCase(private val repository: UserKoRepository) {
    suspend operator fun invoke(): UserKoState = repository.load()
}

class ImportUserKoUseCase(private val repository: UserKoRepository) {
    suspend operator fun invoke(uri: String, displayName: String): UserKoOperationResult =
        repository.import(uri, displayName)
}

class LoadUserKoUseCase(private val repository: UserKoRepository) {
    suspend operator fun invoke(id: String): UserKoOperationResult = repository.loadModule(id)
}

class UnloadUserKoUseCase(private val repository: UserKoRepository) {
    suspend operator fun invoke(id: String): UserKoOperationResult = repository.unloadModule(id)
}

class DeleteUserKoUseCase(private val repository: UserKoRepository) {
    suspend operator fun invoke(id: String): UserKoOperationResult = repository.delete(id)
}

class SetUserKoAutoLoadUseCase(private val repository: UserKoRepository) {
    suspend operator fun invoke(id: String, enabled: Boolean): UserKoOperationResult =
        repository.setAutoLoad(id, enabled)
}

class SetUserKoStageUseCase(private val repository: UserKoRepository) {
    suspend operator fun invoke(stage: String): UserKoOperationResult = repository.setStage(stage)
}
