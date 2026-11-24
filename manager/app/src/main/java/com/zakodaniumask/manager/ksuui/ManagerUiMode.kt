package com.zakodaniumask.manager.ksuui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.ksuui.ui.UiMode

enum class ManagerUiMode(val value: String, val label: String) {
    ZAKO("zako", "Baka MD3E"),
    KSU_MATERIAL("ksu_material", "KernelSU Material"),
    KSU_MIUIX("ksu_miuix", "KernelSU Miuix"),
    ;

    val isKsu: Boolean
        get() = this != ZAKO

    fun toKsuUiMode(): UiMode = when (this) {
        KSU_MATERIAL -> UiMode.Material
        else -> UiMode.Miuix
    }

    companion object {
        fun fromValue(value: String?): ManagerUiMode =
            entries.find { it.value == value } ?: ZAKO
    }
}

/** Holds the selected manager UI so both the Zako and KernelSU shells can switch at runtime. */
class ManagerUiModeController(
    private val settings: AppSettingsRepository,
) {
    var mode by mutableStateOf(
        ManagerUiMode.fromValue(settings.getString(KEY, ManagerUiMode.ZAKO.value)),
    )
        private set

    fun set(value: ManagerUiMode) {
        mode = value
        settings.putString(KEY, value.value)
    }

    companion object {
        const val KEY = "manager_ui_mode"
    }
}
