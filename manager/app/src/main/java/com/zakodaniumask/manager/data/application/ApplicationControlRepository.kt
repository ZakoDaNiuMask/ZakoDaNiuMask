package com.zakodaniumask.manager.data.application

import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApplicationControlRepository(
    private val ksuCliRepository: KsuCliRepository,
    private val settings: AppSettingsRepository,
) {
    suspend fun ensureManagerInstalled(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val isManager = runCatching { Natives.isManager }.getOrDefault(false)
            val featured = Natives.isFullFeatured() ||
                (isManager && settings.getBoolean("ignore_uapi", false))
            if (featured && ksuCliRepository.rootAvailable()) {
                ksuCliRepository.install()
            }
        }
    }

    suspend fun reboot(reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { ksuCliRepository.reboot(reason) }
    }
}
