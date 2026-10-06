package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.kpm.KpmRepository
import com.zakodaniumask.manager.domain.model.KpmModule
import com.zakodaniumask.manager.domain.model.KpmStatus

class GetKpmStatusUseCase(private val repository: KpmRepository) {
    suspend operator fun invoke(): KpmStatus = repository.getStatus()
}

class GetKpmModulesUseCase(private val repository: KpmRepository) {
    suspend operator fun invoke(): List<KpmModule> = repository.listModules()
}

class GetKpmModuleInfoUseCase(private val repository: KpmRepository) {
    suspend operator fun invoke(name: String): String = repository.moduleInfo(name)
}

class LoadKpmModuleUseCase(private val repository: KpmRepository) {
    suspend operator fun invoke(path: String, args: String?): Boolean = repository.loadModule(path, args)
}

class UnloadKpmModuleUseCase(private val repository: KpmRepository) {
    suspend operator fun invoke(name: String): Boolean = repository.unloadModule(name)
}

class ControlKpmModuleUseCase(private val repository: KpmRepository) {
    suspend operator fun invoke(name: String, args: String): Int = repository.controlModule(name, args)
}
