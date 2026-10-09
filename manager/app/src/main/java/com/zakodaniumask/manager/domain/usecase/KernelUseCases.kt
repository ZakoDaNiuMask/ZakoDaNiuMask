package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.kernel.KernelRepository
import com.zakodaniumask.manager.data.kernel.SpoofRepository

class GetKernelStatusUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

class GetManagerRuntimeInfoUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke() = repository.getManagerRuntimeInfo()
}

class GetKernelFeatureSettingsUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke() = repository.getFeatureSettings()
}

class SetSuEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSuEnabled(enabled)
}

class SetKernelUmountEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setKernelUmountEnabled(enabled)
}

class ConfigureSuLogUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSuLogEnabled(enabled)
}

class SetSelinuxHideEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSelinuxHideEnabled(enabled)
}

class GetSelinuxModeUseCase(private val repository: SpoofRepository) {
    suspend operator fun invoke() = repository.selinuxMode()
}

class SetSelinuxPermissiveUseCase(private val repository: SpoofRepository) {
    suspend operator fun invoke(permissive: Boolean) = repository.setSelinuxPermissive(permissive)
}

class SetMountHideEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setMountHideEnabled(enabled)
}

class SetSamsungCompatEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSamsungCompatEnabled(enabled)
}

class SetPtctlEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setPtctlEnabled(enabled)
}

class SetUhookEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setUhookEnabled(enabled)
}

class SetDefaultUmountModulesUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setDefaultUmountModules(enabled)
}

class IsLateLoadModeUseCase(private val repository: KernelRepository) {
    operator fun invoke() = repository.isLateLoadMode()
}
