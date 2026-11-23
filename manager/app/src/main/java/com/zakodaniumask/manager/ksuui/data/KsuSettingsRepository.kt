package com.zakodaniumask.manager.ksuui.data

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.ksuui.ManagerUiMode
import com.zakodaniumask.manager.ksuui.ManagerUiModeController
import com.zakodaniumask.manager.ksuui.data.repository.SettingsRepository
import com.zakodaniumask.manager.ui.theme.BackgroundManager
import com.zakodaniumask.manager.ui.theme.BottomBarStyle
import com.zakodaniumask.manager.ui.theme.ThemeConfig

/**
 * Phase 0 settings bridge: the parts used by the ported Home screen and theme are backed by
 * the current manager's settings/theme state; the rest keeps the KernelSU keys with defaults.
 */
class KsuSettingsRepository(
    private val settings: AppSettingsRepository,
    private val themeConfig: ThemeConfig,
    private val backgroundManager: BackgroundManager,
    private val uiModeController: ManagerUiModeController,
) : SettingsRepository {

    override var uiMode: String
        get() = if (uiModeController.mode == ManagerUiMode.KSU_MATERIAL) "material" else "miuix"
        set(value) {
            uiModeController.set(
                if (value == "material") ManagerUiMode.KSU_MATERIAL else ManagerUiMode.KSU_MIUIX,
            )
        }

    override var checkUpdate: Boolean
        get() = settings.getBoolean("ksu_check_update", true)
        set(value) = settings.putBoolean("ksu_check_update", value)

    override var checkModuleUpdate: Boolean
        get() = settings.getBoolean("ksu_check_module_update", true)
        set(value) = settings.putBoolean("ksu_check_module_update", value)

    override var themeMode: Int
        get() {
            val dynamic = themeConfig.useDynamicColor
            val dark = themeConfig.forceDarkMode
            return when {
                themeConfig.amoledBackground && dark != false -> 6
                dark == null -> if (dynamic) 3 else 0
                dark -> if (dynamic) 5 else 2
                else -> if (dynamic) 4 else 1
            }
        }
        set(value) {
            val dynamic = value >= 3
            val dark = when (value) {
                1, 4 -> false
                2, 5, 6 -> true
                else -> null
            }
            themeConfig.useDynamicColor = dynamic
            themeConfig.forceDarkMode = dark
            themeConfig.amoledBackground = value == 6
            settings.putString(
                "theme_mode",
                when (dark) {
                    true -> "dark"
                    false -> "light"
                    null -> "system"
                },
            )
            settings.putBoolean("use_dynamic_color", dynamic)
            settings.putBoolean("amoled_background", value == 6)
        }

    override var miuixMonet: Boolean
        get() = themeConfig.useDynamicColor
        set(value) {
            themeConfig.useDynamicColor = value
            settings.putBoolean("use_dynamic_color", value)
        }

    override var keyColor: Int
        get() = themeConfig.seedColor
        set(value) {
            themeConfig.seedColor = value
            themeConfig.monetCompatSeedColor = value
            settings.putInt("theme_seed_color", value)
        }

    override var colorStyle: String
        get() = themeConfig.dynamicPaletteStyle.name
        set(value) {
            val parsed = PaletteStyle.entries.find { it.name == value } ?: PaletteStyle.TonalSpot
            themeConfig.dynamicPaletteStyle = parsed
            settings.putString("dynamic_palette_style", parsed.name)
        }

    override var colorSpec: String
        get() = themeConfig.dynamicColorSpec.name
        set(value) {
            val parsed = ColorSpec.SpecVersion.entries.find { it.name == value }
                ?: ColorSpec.SpecVersion.SPEC_2021
            themeConfig.dynamicColorSpec = parsed
            settings.putString("dynamic_color_spec", parsed.name)
        }

    override var enablePredictiveBack: Boolean
        get() = settings.getBoolean("ksu_enable_predictive_back", true)
        set(value) = settings.putBoolean("ksu_enable_predictive_back", value)

    override var enableSwipeDismiss: Boolean
        get() = settings.getBoolean("ksu_enable_swipe_dismiss", true)
        set(value) = settings.putBoolean("ksu_enable_swipe_dismiss", value)

    override var pagerInterceptionMode: Int
        get() = settings.getInt("ksu_pager_interception_mode", 0)
        set(value) = settings.putInt("ksu_pager_interception_mode", value)

    override var enableBlur: Boolean
        get() = themeConfig.isEnableBlur
        set(value) = backgroundManager.saveEnableBlur(value)

    override var enableFloatingBottomBar: Boolean
        get() = themeConfig.bottomBarStyle == BottomBarStyle.FLOATING
        set(value) = backgroundManager.saveBottomBarStyle(
            if (value) BottomBarStyle.FLOATING else BottomBarStyle.MATERIAL3_EXPRESSIVE,
        )

    override var enableFloatingBottomBarBlur: Boolean
        get() = settings.getBoolean("ksu_floating_bottom_bar_blur", true)
        set(value) = settings.putBoolean("ksu_floating_bottom_bar_blur", value)

    override var enableNavigationBadge: Boolean
        get() = settings.getBoolean("ksu_enable_navigation_badge", true)
        set(value) = settings.putBoolean("ksu_enable_navigation_badge", value)

    override var navigationRailExpanded: Boolean
        get() = settings.getBoolean("ksu_navigation_rail_expanded", false)
        set(value) = settings.putBoolean("ksu_navigation_rail_expanded", value)

    override var pageScale: Float
        get() = settings.getFloat("ksu_page_scale", 1f)
        set(value) = settings.putFloat("ksu_page_scale", value)

    override var moduleDescriptionMaxLines: Int
        get() = settings.getInt("ksu_module_description_max_lines", 4)
        set(value) = settings.putInt("ksu_module_description_max_lines", value)

    override var enableWebDebugging: Boolean
        get() = settings.getBoolean("ksu_enable_web_debugging", false)
        set(value) = settings.putBoolean("ksu_enable_web_debugging", value)

    override var moduleSortEnabledFirst: Boolean
        get() = settings.getBoolean("ksu_module_sort_enabled_first", false)
        set(value) = settings.putBoolean("ksu_module_sort_enabled_first", value)

    override var moduleSortActionFirst: Boolean
        get() = settings.getBoolean("ksu_module_sort_action_first", false)
        set(value) = settings.putBoolean("ksu_module_sort_action_first", value)

    override var moduleRepoSortOrder: String
        get() = settings.getString("ksu_module_repo_sort_order", "default") ?: "default"
        set(value) = settings.putString("ksu_module_repo_sort_order", value)

    override var superuserShowSystemApps: Boolean
        get() = settings.getBoolean("ksu_superuser_show_system_apps", false)
        set(value) = settings.putBoolean("ksu_superuser_show_system_apps", value)

    override var superuserShowOnlyPrimaryUserApps: Boolean
        get() = settings.getBoolean("ksu_superuser_show_primary_user_apps", false)
        set(value) = settings.putBoolean("ksu_superuser_show_primary_user_apps", value)

    override var superuserSortOption: Int
        get() = settings.getInt("ksu_superuser_sort_option", 0)
        set(value) = settings.putInt("ksu_superuser_sort_option", value)

    override var suLogFilters: Set<String>?
        get() = settings.getStringSet("ksu_su_log_filters", emptySet())
        set(value) = settings.putStringSet("ksu_su_log_filters", value ?: emptySet())

    override var autoJailbreak: Boolean
        get() = settings.getBoolean("ksu_auto_jailbreak", false)
        set(value) = settings.putBoolean("ksu_auto_jailbreak", value)

    override var useSoftReboot: Boolean
        get() = settings.getBoolean("ksu_use_soft_reboot", false)
        set(value) = settings.putBoolean("ksu_use_soft_reboot", value)

    override val intentToken: String
        get() = settings.getString("ksu_intent_token", "") ?: ""

    override suspend fun getSuCompatStatus(): String = "unknown"
    override suspend fun getSuCompatPersistValue(): Long? = null
    override fun isSuEnabled(): Boolean = false
    override fun setSuEnabled(enabled: Boolean): Boolean = false
    override fun setSuCompatModePref(mode: Int) = Unit
    override fun getSuCompatModePref(): Int = 0

    override suspend fun getKernelUmountStatus(): String = "unknown"
    override fun isKernelUmountEnabled(): Boolean = false
    override fun setKernelUmountEnabled(enabled: Boolean): Boolean = false

    override suspend fun getSelinuxHideStatus(): String = "unknown"
    override fun isSelinuxHideEnabled(): Boolean = false
    override fun setSelinuxHideEnabled(enabled: Boolean): Int = 0

    override suspend fun getSulogStatus(): String = "unknown"
    override suspend fun getSulogPersistValue(): Long? = null
    override suspend fun getSulogValue(): Long? = null
    override fun setSulogEnabled(enabled: Boolean): Boolean = false

    override suspend fun getAdbRootStatus(): String = "unknown"
    override suspend fun getAdbRootPersistValue(): Long? = null
    override suspend fun getAdbRootValue(): Long? = null
    override fun setAdbRootEnabled(enabled: Boolean): Boolean = false

    override suspend fun getShellSuAdbOnlyStatus(): String = "unknown"
    override suspend fun getShellSuAdbOnlyPersistValue(): Long? = null
    override suspend fun getShellSuAdbOnlyValue(): Long? = null
    override fun setShellSuAdbOnlyEnabled(enabled: Boolean): Boolean = false

    override fun isDefaultUmountModules(): Boolean = false
    override fun setDefaultUmountModules(enabled: Boolean): Boolean = false

    override fun isLkmMode(): Boolean = runCatching { Natives.isLkmMode }.getOrDefault(false)

    override fun execKsudFeatureSave() = Unit
}
