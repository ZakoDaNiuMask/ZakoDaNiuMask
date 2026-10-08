package com.zakodaniumask.manager.data.module

import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.domain.model.ModuleCustomOrderStore
import com.zakodaniumask.manager.domain.model.ModulePreferences
import com.zakodaniumask.manager.domain.model.ModuleSortGroup
import com.zakodaniumask.manager.domain.model.ModuleSortPriorityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ModulePreferencesRepository(
    private val settings: AppSettingsRepository,
) {
    private val mutablePreferences = MutableStateFlow(readPreferences())
    val preferences: StateFlow<ModulePreferences> = mutablePreferences.asStateFlow()

    fun reload() {
        mutablePreferences.value = readPreferences()
    }

    fun setSortGroups(groups: Set<ModuleSortGroup>) {
        mutablePreferences.update { it.copy(sortGroups = groups) }
        settings.putString(ModuleSortPriorityStore.Key, ModuleSortPriorityStore.encode(groups))
    }

    fun setCustomOrder(order: List<String>) {
        mutablePreferences.update { it.copy(sortCustomOrder = order) }
        if (order.isEmpty()) {
            settings.putString(ModuleCustomOrderStore.Key, "")
        } else {
            settings.putString(ModuleCustomOrderStore.Key, ModuleCustomOrderStore.encode(order))
        }
    }

    fun setShowMoreInfo(enabled: Boolean) {
        mutablePreferences.update { it.copy(showMoreModuleInfo = enabled) }
        settings.putBoolean(PREF_SHOW_MORE_INFO, enabled)
    }

    private fun readPreferences() = ModulePreferences(
        sortGroups = ModuleSortPriorityStore.decode(
            settings.getString(ModuleSortPriorityStore.Key, "").ifBlank { null }
        ),
        sortCustomOrder = ModuleCustomOrderStore.decode(
            settings.getString(ModuleCustomOrderStore.Key, "")
        ),
        showMoreModuleInfo = settings.getBoolean(PREF_SHOW_MORE_INFO, false),
    )

    private companion object {
        const val PREF_SHOW_MORE_INFO = "show_more_module_info"
    }
}
