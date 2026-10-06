package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.plugin.OnlinePluginRepository
import com.zakodaniumask.manager.data.plugin.PluginRepository
import com.zakodaniumask.manager.domain.model.OnlinePlugin
import com.zakodaniumask.manager.domain.model.PluginInfo

class GetPluginsUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(): Result<List<PluginInfo>> = repository.list()
}

class InstallPluginUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(zipPath: String): Boolean = repository.install(zipPath)
}

class UninstallPluginUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(id: String): Boolean = repository.uninstall(id)
}

class SetPluginEnabledUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(id: String, enabled: Boolean): Boolean =
        repository.setEnabled(id, enabled)
}

class RunPluginCallbackUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(id: String, function: String): Pair<Boolean, String> =
        repository.runCallback(id, function)
}

class RunPluginActionUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(id: String): Pair<Boolean, String> = repository.runAction(id)
}

class GetPluginConfigUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(id: String, key: String): String = repository.getConfig(id, key)
}

class SetPluginConfigUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(id: String, key: String, value: String): Boolean =
        repository.setConfig(id, key, value)
}

class GetPluginLogUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(id: String): String = repository.getLog(id)
}

class ClearPluginLogUseCase(private val repository: PluginRepository) {
    suspend operator fun invoke(id: String): Boolean = repository.clearLog(id)
}

class GetOnlinePluginsUseCase(private val repository: OnlinePluginRepository) {
    suspend operator fun invoke(language: String): Result<List<OnlinePlugin>> =
        repository.fetch(language)
}

class DownloadOnlinePluginUseCase(private val repository: OnlinePluginRepository) {
    suspend operator fun invoke(url: String, destPath: String): Result<String> =
        repository.download(url, destPath)
}
