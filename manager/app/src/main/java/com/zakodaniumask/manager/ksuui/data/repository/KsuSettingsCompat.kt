package com.zakodaniumask.manager.ksuui.data.repository

import com.zakodaniumask.manager.ksuui.KsuServiceLocator

fun isSoftRebootPreferred(): Boolean =
    runCatching { KsuServiceLocator.settingsRepository.useSoftReboot }.getOrDefault(false)
